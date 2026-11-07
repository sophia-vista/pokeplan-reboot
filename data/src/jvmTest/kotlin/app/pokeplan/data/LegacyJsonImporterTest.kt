package app.pokeplan.data

import app.pokeplan.app.AppData
import app.pokeplan.care.CareData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The five committed 0.123.0 saves, imported into a real SQLite file and compared with what the app's own model reads from them. */
class LegacyJsonImporterTest {
    // The app's own settings (SavedDocument): the class discriminator is "kind".
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "kind" }
    private fun fixture(name: String) = javaClass.getResource("/fixtures/0.123.0/$name")!!.readText()
    private val now = 1_800_000_000_000L

    private fun importInto(store: DataStore, main: String, care: String? = null) =
        LegacyJsonImporter(store).import(fixture(main), care?.let { fixture(it) }, now)

    @Test
    fun theHeavySaveImportsWithEveryCountMatchingTheTypedModel() {
        val saveText = fixture("main-heavy-70-days.json")
        val app = json.decodeFromString<AppData>(saveText)
        val care = json.decodeFromString<CareData>(fixture("care-week.json"))
        val store = JvmDatabase.open()
        val report = importInto(store, "main-heavy-70-days.json", "care-week.json")

        assertFalse(report.alreadyImported)
        assertEquals(app.player.pokemon.size.toLong(), store.pokemon.count())
        assertEquals((app.player.coins + app.world.poke).toLong(), store.economy.poke())
        assertEquals(app.player.inventory.counts.mapValues { it.value.toLong() } + app.world.items.mapValues { it.value.toLong() }.filter { it.key !in app.player.inventory.counts }, store.economy.bag())
        assertEquals(app.player.caught.map { it.toLong() }.sorted(), store.pokemon.dex(DexKind.CAUGHT_SPECIES))
        assertEquals(app.shinySpecies.map { it.toLong() }.sorted(), store.pokemon.dex(DexKind.SHINY_SPECIES))
        assertEquals(app.seenSpecies.map { it.toLong() }.sorted(), store.pokemon.dex(DexKind.SEEN_SPECIES))
        assertEquals(app.player.eggMeter.toLong(), store.pokemon.progress()!!.eggMeter)
        assertEquals(app.player.trainerPoints.toLong(), store.pokemon.progress()!!.trainerPoints)
        assertEquals(app.player.partyOf("plan").map { it.id }.toSet(), store.pokemon.party(ModuleIds.PLAN).toSet())
        assertEquals(app.player.partyOf("plan").size, store.pokemon.party(ModuleIds.PLAN).size)
        assertEquals(emptyList(), store.integrityIssues())
        assertEquals(emptyList(), store.economy.reconcile())
        // Care's whole save is in the sidecar, field by field.
        val careFields = (Json.parseToJsonElement(fixture("care-week.json")) as kotlinx.serialization.json.JsonObject).keys - "cloud"
        assertEquals(careFields, store.db.dataQueries.sidecarOf("care").executeAsList().map { it.path }.toSet())
        assertEquals(care.routines.size, (Json.parseToJsonElement(store.db.dataQueries.sidecarJson("care", "routines").executeAsOne()) as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun everyIndividualPokemonSurvivesFieldForField() {
        val app = json.decodeFromString<AppData>(fixture("main-heavy-70-days.json"))
        val store = JvmDatabase.open()
        importInto(store, "main-heavy-70-days.json")
        for (p in app.player.pokemon) {
            val r = store.pokemon.get(p.id) ?: error("${p.id} was lost")
            assertEquals(p.speciesId, r.speciesId)
            assertEquals(p.formIdentifier, r.formIdentifier)
            assertEquals(p.nickname, r.nickname)
            assertEquals(p.nature.name, r.nature)
            assertEquals(p.ability, r.ability)
            assertEquals(p.gender?.name, r.gender)
            assertEquals(listOf(p.ivs.hp, p.ivs.attack, p.ivs.defense, p.ivs.spAttack, p.ivs.spDefense, p.ivs.speed), listOf(r.ivs.hp, r.ivs.attack, r.ivs.defense, r.ivs.spAttack, r.ivs.spDefense, r.ivs.speed))
            assertEquals(p.level, r.level)
            assertEquals(p.friendship, r.friendship)
            assertEquals(p.isShiny, r.isShiny)
            assertEquals(p.caughtAtMillis, r.caughtAtMillis)
            assertEquals(p.personality, r.personality)
            assertEquals(p.id in app.favorites, r.isFavorite)
            assertEquals(app.origins[p.id]?.how?.name, r.originHow)
        }
    }

    @Test
    fun everyOtherFieldLandsInTheSidecarAndNothingIsDropped() {
        val saveText = fixture("main-typical-week.json")
        val store = JvmDatabase.open()
        importInto(store, "main-typical-week.json", "care-week.json")
        val q = store.db.dataQueries
        val source = Json.parseToJsonElement(saveText) as kotlinx.serialization.json.JsonObject
        val mapped = setOf("schema", "player", "favorites", "origins", "shinySpecies", "caughtForms", "shinyForms", "seenSpecies", "account")
        val expected = source.keys.filter { it !in mapped }.toSet()
        val stored = q.sidecarOf("save").executeAsList().map { it.path }.filter { !it.startsWith("player.") }.toSet()
        assertEquals(expected, stored)
        // The tasks come back exactly as the app wrote them.
        val tasks = q.sidecarJson("save", "tasks").executeAsOne()
        assertEquals(source["tasks"].toString(), tasks)
        // World keeps its fields except the two moved into the shared Poké balance and Bag.
        val world = Json.parseToJsonElement(q.sidecarJson("save", "world").executeAsOne()) as kotlinx.serialization.json.JsonObject
        assertFalse("poke" in world || "items" in world)
        assertTrue(q.sidecarOf("care").executeAsList().isNotEmpty())
    }

    @Test
    fun theOpeningBalanceIsOneLedgerEventWithBothSourcesRecorded() {
        val store = JvmDatabase.open()
        val report = importInto(store, "main-typical-week.json")
        val ledger = store.economy.ledgerOf(LegacyJsonImporter.openingEventId(report.migrationId))
        assertTrue(ledger.isNotEmpty())
        val metadata = store.db.dataQueries.eventById(LegacyJsonImporter.openingEventId(report.migrationId)).executeAsOne().metadata_json!!
        assertTrue("legacyCoins" in metadata && "legacyWorldPoke" in metadata)
        // Nothing was paid by the engine: the only event is the opening balance.
        assertEquals(1L, store.db.dataQueries.eventCount().executeAsOne())
    }

    @Test
    fun importingTheSameFilesAgainWritesNothingAndTheBalanceIsNotAddedTwice() {
        val store = JvmDatabase.open()
        val first = importInto(store, "main-heavy-70-days.json", "care-week.json")
        val poke = store.economy.poke()
        val ledger = store.db.dataQueries.ledgerCount().executeAsOne()
        val second = importInto(store, "main-heavy-70-days.json", "care-week.json")
        assertTrue(second.alreadyImported)
        assertEquals(first.migrationId, second.migrationId)
        assertEquals(first.counts, second.counts)
        assertEquals(poke, store.economy.poke())
        assertEquals(ledger, store.db.dataQueries.ledgerCount().executeAsOne())
    }

    @Test
    fun aDifferentSaveIsRefusedInsteadOfMergedOverTheFirst() {
        val store = JvmDatabase.open()
        importInto(store, "main-heavy-70-days.json")
        assertFailsWith<ImportRejectedException> { importInto(store, "main-typical-week.json") }
    }

    @Test
    fun aBrokenFileLeavesTheDatabaseEmpty() {
        val store = JvmDatabase.open()
        assertFailsWith<ImportFailedException> { LegacyJsonImporter(store).import("{not json", null, now) }
        val broken = fixture("main-typical-week.json").replaceFirst("\"level\":", "\"level\":900,\"x\":")
        assertFailsWith<ImportFailedException> { LegacyJsonImporter(store).import(broken, null, now) }
        assertEquals(0L, store.pokemon.count())
        assertEquals(0L, store.economy.poke())
        assertEquals(0L, store.db.dataQueries.eventCount().executeAsOne())
        assertEquals(emptyList(), store.db.dataQueries.sidecarOf("save").executeAsList())
    }

    @Test
    fun anEmptyNewSaveAndARunningFocusSaveBothImport() {
        for (name in listOf("main-empty.json", "main-focus-running-and-pending.json")) {
            val store = JvmDatabase.open()
            val report = importInto(store, name)
            assertEquals(emptyList(), store.integrityIssues(), name)
            assertTrue(report.counts.getValue("sidecarRows") > 10, name)
        }
        // The focus session and the unpaid completion are kept for PLAN to read back.
        val store = JvmDatabase.open()
        importInto(store, "main-focus-running-and-pending.json")
        assertTrue("focus" in store.db.dataQueries.sidecarOf("save").executeAsList().map { it.path })
    }

    @Test
    fun theLeadComesFromTheOldBuddyAndTheOldCareLead() {
        val text = fixture("main-heavy-70-days.json")
        val source = Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val ids = ((source["player"] as kotlinx.serialization.json.JsonObject)["pokemon"] as kotlinx.serialization.json.JsonArray)
            .map { it as kotlinx.serialization.json.JsonObject }.filter { (it["inParty"] as kotlinx.serialization.json.JsonPrimitive).content == "true" }
            .map { (it["id"] as kotlinx.serialization.json.JsonPrimitive).content }
        val chosen = ids.last()
        val edited = text.replace("\"buddyId\":null", "\"buddyId\":\"$chosen\"").replace("\"buddyId\": null", "\"buddyId\": \"$chosen\"")
        val store = JvmDatabase.open()
        LegacyJsonImporter(store).import(edited, null, now)
        // One place only (7 November 2026): the old buddy is the buddy and no longer in Plan's party, which the import reports.
        assertEquals(chosen, store.pokemon.buddy())
        assertEquals(PokemonPlace.Buddy, store.pokemon.placeOf(chosen))
        assertEquals(ids.size - 1, store.pokemon.party(ModuleIds.PLAN).size)
        assertTrue(chosen !in store.pokemon.party(ModuleIds.PLAN))
        assertTrue(store.db.dataQueries.warningsOf(LegacyJsonImporter(store).import(edited, null, now).migrationId).executeAsList().any { it.code == "place_conflict" })
    }
}
