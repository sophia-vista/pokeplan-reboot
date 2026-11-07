package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.app.GameStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The player's side of the save in the shared Pokémon, Bag and ledger tables (REP-014, schema 6): the 0.123.0 fixtures move in and read back
 * equal, a write changes only the rows that changed, the ledger is the only writer of the balance, a Pokémon is in one place only, a move that
 * cannot be proved leaves the save row as the live save, and a restore goes through the same split.
 */
class PokemonTablesTest {
    private val fixtures = listOf("main-empty.json", "main-typical-week.json", "main-heavy-70-days.json", "main-focus-running-and-pending.json")
    private fun fixture(name: String) = javaClass.getResource("/fixtures/0.123.0/$name")!!.readText()
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private val normalize: (String, Long) -> String = GameStore::normalizeDocument

    /** A store with [doc] as the save, PLAN already in its tables, ready for the Pokémon move. */
    private fun withSave(doc: String, normalizer: ((String, Long) -> String)? = normalize): DataStore {
        val s = JvmDatabase.open()
        s.live.normalizer = normalizer
        s.live.activate(doc, null, 5)
        s.live.movePlanToTables(6)
        return s
    }

    private fun moved(name: String): DataStore = withSave(fixture(name)).also { it.live.movePokemonToTables(7) }

    private fun scalar(s: DataStore, sql: String): Long =
        s.driver.executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val watched = listOf("owned_pokemon", "pokemon_place", "bag_stack", "currency_balance", "reward_event", "reward_ledger_entry", "dex_history", "player_progress", "pokemon_state")

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in watched) for (op in listOf("INSERT", "UPDATE", "DELETE"))
            s.driver.execute(null, "CREATE TRIGGER log_${op}_$t AFTER $op ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
    }

    private fun writes(s: DataStore, table: String? = null) = scalar(s, "SELECT count(*) FROM write_log" + (table?.let { " WHERE t = '$it'" } ?: ""))
    private fun clearLog(s: DataStore) = s.driver.execute(null, "DELETE FROM write_log", 0)

    private fun player(doc: JsonObject) = doc["player"] as JsonObject
    private fun pokemon(doc: JsonObject) = (player(doc)["pokemon"] as JsonArray).map { it as JsonObject }
    private fun text(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun withPlayer(doc: JsonObject, change: (JsonObject) -> JsonObject) = JsonObject(doc + ("player" to change(player(doc))))
    private fun JsonObject.with(vararg pairs: Pair<String, JsonElement>) = JsonObject(this + pairs)
    private fun save(s: DataStore) = obj(s.live.read("save")!!)
    private fun same(expected: JsonObject, actual: JsonObject, label: String) =
        assertEquals(LiveDocuments.canonical(expected), LiveDocuments.canonical(actual), label)

    /** A 0.123.0 save whose buddy is also in Plan's party, as the old app allowed: two places, and no Poké merge record. */
    private fun oldSaveWithABuddyInTheParty(): String {
        val doc = obj(fixture("main-heavy-70-days.json"))
        val inParty = pokemon(doc).first { (it["inParty"] as JsonPrimitive).content == "true" }
        return Json.encodeToString(JsonObject.serializer(), withPlayer(doc) { it.with("buddyId" to JsonPrimitive(text(inParty, "id")!!)) })
    }

    private fun write(s: DataStore, doc: JsonObject, now: Long = 100) = s.live.write("save", Json.encodeToString(JsonObject.serializer(), doc), now)

    // --- the move ------------------------------------------------------------------------------------------------------

    @Test
    fun theSaveFixturesMoveIntoTablesAndReadBackEqual() {
        for (name in fixtures) {
            val s = moved(name)
            assertTrue(s.pokemonTables.isActive(), name)
            val expected = obj(normalize(fixture(name), 7))
            same(expected, save(s), name)
            same(expected, obj(s.live.snapshot().getValue("save")), "$name snapshot")
            // The old save is kept, whole, and the row holds neither PLAN's nor the player's.
            assertEquals(fixture(name), s.live.read("aside:save:plan:6"))
            assertNotNull(s.live.read("aside:save:pokemon:7"), name)
            val row = obj(s.driver.executeQuery(null, "SELECT json FROM live_document WHERE name = 'save'", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value!!)
            assertTrue(row.keys.none { it in PokemonTables.PART_FIELDS || it in PlanTables.PLAN_FIELDS }, "$name: the row holds only the rest")
            // Counts: every Pokémon is a row, the Bag and the balance are the save's.
            assertEquals(pokemon(expected).size.toLong(), s.pokemon.count(), name)
            assertEquals((player(expected)["coins"] as JsonPrimitive).content.toLong(), s.economy.poke(), name)
            assertEquals(
                ((player(expected)["inventory"] as JsonObject)["counts"] as JsonObject).mapValues { (it.value as JsonPrimitive).content.toLong() }.filterValues { it > 0 },
                s.economy.bag(), name,
            )
        }
    }

    @Test
    fun theMovePaysNothingAndTheLedgerStartsWithOneOpeningBalance() {
        val s = moved("main-heavy-70-days.json")
        val coins = s.economy.poke()
        assertTrue(coins > 0 || s.economy.bag().isNotEmpty(), "the fixture has something to carry over")
        assertEquals(1L, scalar(s, "SELECT count(*) FROM reward_event"))
        val event = s.db.dataQueries.eventById(PokemonTables.OPENING_EVENT).executeAsOne()
        assertEquals("MIGRATION", event.source_module)
        assertEquals("opening-balance", event.event_kind)
        assertTrue(event.metadata_json!!.contains("saveSchema"))
        // The two legacy source values stay in the audit metadata (the Poké merge's record).
        assertTrue(event.metadata_json!!.contains("legacyCoins"), event.metadata_json)
        assertEquals(emptyList(), s.economy.reconcile())
        assertEquals(0L, scalar(s, "SELECT count(*) FROM outbox_event"), "nothing is published to anyone")
        // Moving again does nothing.
        s.live.movePokemonToTables(99)
        assertEquals(1L, scalar(s, "SELECT count(*) FROM reward_event"))
        assertNull(s.live.read("aside:save:pokemon:99"))
    }

    @Test
    fun aFreshInstallStartsOnTheTables() {
        val s = JvmDatabase.open()
        s.live.activate(null, null, 1)
        s.live.movePlanToTables(2)
        s.live.movePokemonToTables(3)
        assertTrue(s.pokemonTables.isActive())
        assertNull(s.live.read("save"))
    }

    @Test
    fun aMoveThatCannotBeProvedLeavesTheSaveRowAsTheLiveSave() {
        // Without the app's own check, a 0.123.0 save has a buddy that is also in Plan's party: two places. Nothing may be written.
        val s = withSave(oldSaveWithABuddyInTheParty(), normalizer = null)
        val before = s.live.read("save")
        assertFailsWith<SaveNotRepresentableException> { s.live.movePokemonToTables(7) }
        assertFalse(s.pokemonTables.isActive())
        assertEquals(0L, s.pokemon.count())
        assertEquals(0L, scalar(s, "SELECT count(*) FROM reward_event"))
        assertEquals(before, s.live.read("save"))
        assertNull(s.live.read("aside:save:pokemon:7"), "the aside copy is part of the rolled-back transaction")
        // The app keeps working on the row.
        s.live.write("save", before!!, 8)
        assertEquals(before, s.live.read("save"))
    }

    @Test
    fun aReadBackThatDiffersIsNotAccepted() {
        // A normalizer that hands back a save different from the one it reads cannot be proved equal to it.
        val s = withSave(fixture("main-typical-week.json"), normalizer = { text, _ ->
            val o = obj(text)
            Json.encodeToString(JsonObject.serializer(), o.with("player" to player(o).with("origins" to JsonPrimitive("x"))).with("seenSpecies" to JsonPrimitive("not a list")))
        })
        assertFailsWith<Exception> { s.live.movePokemonToTables(7) }
        assertFalse(s.pokemonTables.isActive())
        assertEquals(0L, s.pokemon.count())
    }

    // --- writes ---------------------------------------------------------------------------------------------------------

    @Test
    fun aWriteChangesOnlyTheRowsThatChanged() {
        val s = moved("main-heavy-70-days.json")
        val doc = save(s)
        countWrites(s)

        write(s, doc)
        assertEquals(0L, writes(s), "the same save again writes nothing")

        // One Pokémon levels up: one row.
        val first = pokemon(doc).first()
        val levelled = withPlayer(doc) { p ->
            p.with("pokemon" to JsonArray(pokemon(doc).map { if (it === first) it.with("level" to JsonPrimitive((text(it, "level")!!.toInt() % 99) + 1), "candyProgress" to JsonPrimitive(0)) else it }))
        }
        write(s, levelled)
        assertEquals(1L, writes(s), "one Pokémon changed")
        assertEquals(1L, writes(s, "owned_pokemon"))
        assertEquals((text(pokemon(save(s)).first(), "level")), text(pokemon(levelled).first(), "level"))
        clearLog(s)

        // Five Poké earned: one balance row, one event, one ledger entry, and nothing else.
        val coins = (player(doc)["coins"] as JsonPrimitive).content.toInt()
        write(s, withPlayer(levelled) { it.with("coins" to JsonPrimitive(coins + 5)) })
        assertEquals(3L, writes(s))
        assertEquals(1L, writes(s, "currency_balance"))
        assertEquals(1L, writes(s, "reward_event"))
        assertEquals(1L, writes(s, "reward_ledger_entry"))
        assertEquals((coins + 5).toLong(), s.economy.poke())
        clearLog(s)

        // Two Rare Candies in the Bag: one stack, one event, one entry.
        val counts = (player(doc)["inventory"] as JsonObject)["counts"] as JsonObject
        val candy = ((counts["rare-candy"] as? JsonPrimitive)?.content?.toInt() ?: 0)
        write(s, withPlayer(levelled) { p ->
            p.with("coins" to JsonPrimitive(coins + 5), "inventory" to JsonObject(mapOf("counts" to JsonObject(counts + ("rare-candy" to JsonPrimitive(candy + 2))))))
        })
        assertEquals(3L, writes(s))
        assertEquals(1L, writes(s, "bag_stack"))
        assertEquals((candy + 2).toLong(), s.economy.quantity("rare-candy"))
        assertEquals(emptyList(), s.economy.reconcile())
    }

    @Test
    fun theLedgerIsTheOnlyWriterOfTheBalanceAndTheBag() {
        val s = moved("main-typical-week.json")
        val doc = save(s)
        val coins = (player(doc)["coins"] as JsonPrimitive).content.toInt()
        write(s, withPlayer(doc) { it.with("coins" to JsonPrimitive(coins + 40)) }, 200)
        write(s, withPlayer(doc) { it.with("coins" to JsonPrimitive(coins + 10)) }, 201) // 30 spent
        assertEquals((coins + 10).toLong(), s.economy.poke())
        // The balance is the sum of the ledger, entry by entry, and each change is an event of its own that is never edited or removed.
        assertEquals(emptyList(), s.economy.reconcile())
        assertEquals(coins.toLong() + 10, scalar(s, "SELECT sum(delta) FROM reward_ledger_entry WHERE asset = 'poke'"))
        assertEquals(if (coins > 0 || s.economy.bag().isNotEmpty()) 3L else 2L, scalar(s, "SELECT count(*) FROM reward_event"), "the opening balance and one event per change")
        assertFailsWith<Exception> { s.driver.execute(null, "UPDATE reward_ledger_entry SET delta = delta + 1", 0) }
        assertFailsWith<Exception> { s.driver.execute(null, "DELETE FROM reward_ledger_entry", 0) }
        // The balance cannot go below zero, even if a save said so: nothing is written.
        assertFailsWith<Exception> { write(s, withPlayer(doc) { it.with("coins" to JsonPrimitive(-5)) }, 202) }
        assertEquals((coins + 10).toLong(), s.economy.poke())
        // The save never carries a second wallet: World's old balance is not stored.
        assertFalse(obj(s.live.read("save")!!).let { (it["world"] as? JsonObject)?.containsKey("poke") ?: false })
    }

    @Test
    fun aWriteThatFailsHalfwayLeavesEveryTableAsItWas() {
        val s = moved("main-heavy-70-days.json")
        val doc = save(s)
        val first = pokemon(doc).first()
        // A Pokémon levels up, a stack is added and the balance goes below zero in the same save: the ledger refuses it, and nothing else may stay.
        val broken = withPlayer(doc) { p ->
            p.with("coins" to JsonPrimitive(-1), "pokemon" to JsonArray(pokemon(doc).map { if (it === first) it.with("friendship" to JsonPrimitive(1)) else it }))
        }
        val rows = scalar(s, "SELECT count(*) FROM owned_pokemon")
        val events = scalar(s, "SELECT count(*) FROM reward_event")
        assertFailsWith<Exception> { write(s, broken) }
        same(doc, save(s), "unchanged")
        assertEquals(rows, scalar(s, "SELECT count(*) FROM owned_pokemon"))
        assertEquals(events, scalar(s, "SELECT count(*) FROM reward_event"))
        assertEquals(emptyList(), s.economy.reconcile())
        assertEquals(emptyList(), s.integrityIssues())
    }

    @Test
    fun theMigrationFromSchemaFiveCarriesTheCollectionOverAndKeepsOnePlaceEach() {
        val dir = java.nio.file.Files.createTempDirectory("pokelife-migration").toFile()
        try {
            val file = java.io.File(dir, "old.db")
            java.io.File("src/commonMain/sqldelight/databases/5.db").copyTo(file)
            java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
                val st = c.createStatement()
                st.execute("PRAGMA user_version = 5")
                for (id in listOf("a", "b", "c", "d")) st.execute(
                    "INSERT INTO owned_pokemon(id, species_id, form_identifier, nickname, nature, ability, iv_hp, iv_attack, iv_defense, iv_sp_attack, iv_sp_defense, iv_speed, " +
                        "ev_hp, ev_attack, ev_defense, ev_sp_attack, ev_sp_defense, ev_speed, level, candy_progress, total_candies, friendship, is_shiny, personality, caught_at_millis) " +
                        "VALUES ('$id', 25, 'pikachu', '', 'HARDY', 'static', 1,1,1,1,1,1, 0,0,0,0,0,0, 5, 0, 0, 70, 0, 0, 1)",
                )
                // The old tables allowed a Pokémon in several places: a in Plan's and Care's party and the buddy, b also a Guardian.
                st.execute("INSERT INTO module_party(module_id, slot, owned_id) VALUES ('PLAN', 1, 'a'), ('PLAN', 2, 'c'), ('CARE', 1, 'a'), ('CARE', 2, 'd')")
                st.execute("INSERT INTO buddy_selection(id, owned_id) VALUES (1, 'a')")
                st.execute("INSERT INTO guardian_slot(slot, owned_id) VALUES (1, 'b')")
            }
            val s = JvmDatabase.open(file.path)
            assertEquals(10, DataStore.SCHEMA_VERSION) // Heal made it 7 (0.136.0), Mint's tables 8 (0.141.0), Home's tables 9, Link's tables 10
            assertEquals(4L, s.pokemon.count())
            assertEquals(listOf("a", "b", "c", "d"), s.pokemon.all().map { it.id }, "the order they were owned")
            // The first place by the app's priority wins (Guardian, buddy, party); the others are closed up behind it.
            assertEquals(PokemonPlace.Buddy, s.pokemon.placeOf("a"))
            assertEquals(PokemonPlace.Guardian, s.pokemon.placeOf("b"))
            assertEquals(listOf("c"), s.pokemon.party(ModuleIds.PLAN))
            assertEquals(listOf("d"), s.pokemon.party(ModuleIds.CARE))
            assertEquals(s.pokemon.places().size.toLong(), scalar(s, "SELECT count(*) FROM pokemon_place"))
            assertEquals(emptyList(), s.integrityIssues())
            s.close()
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun everythingTheSaveHoldsAboutThePlayerComesBack() {
        val s = moved("main-heavy-70-days.json")
        val doc = save(s)
        val mon = pokemon(doc).first()
        val id = text(mon, "id")!!
        val extra = withPlayer(doc) { p ->
            p.with(
                "newPlayerField" to JsonPrimitive("kept"),
                "pokemon" to JsonArray(pokemon(doc).map { if (it === mon) it.with("aFieldFromANewerBuild" to JsonObject(mapOf("a" to JsonPrimitive(1)))) else it }),
            )
        }.with(
            "favorites" to JsonArray(listOf(JsonPrimitive(id), JsonPrimitive("gone-pokemon"))),
            "origins" to JsonObject(
                mapOf(
                    id to JsonObject(mapOf("how" to JsonPrimitive("QUEST"), "region" to JsonNull, "visitor" to JsonObject(mapOf("n" to JsonPrimitive(2))))),
                    "gone-pokemon" to JsonObject(mapOf("how" to JsonPrimitive("STARTER"), "region" to JsonNull)),
                ),
            ),
            "shinySpecies" to JsonArray(listOf(JsonPrimitive(25), JsonPrimitive(1))),
            "newTopLevelField" to JsonPrimitive("stays in the row"),
        )
        write(s, extra)
        same(extra, save(s), "all the player's fields")
        assertTrue(Json.parseToJsonElement(s.live.read("save")!!).toString().contains("aFieldFromANewerBuild"))
    }

    @Test
    fun theOrderOfTheCollectionAndOfAPartyIsKept() {
        val s = moved("main-heavy-70-days.json")
        val doc = save(s)
        val list = pokemon(doc)
        // A Pokémon released from the middle, a new one added at the end, then one inserted in the middle: the list comes back in order each time.
        val without = withPlayer(doc) { it.with("pokemon" to JsonArray(list.filterIndexed { i, _ -> i != 1 })) }
        write(s, without)
        assertEquals(pokemon(without).map { text(it, "id") }, pokemon(save(s)).map { text(it, "id") })
        val extraMon = list[1].with("id" to JsonPrimitive("added-at-the-end"), "inParty" to JsonPrimitive(false))
        val appended = withPlayer(without) { it.with("pokemon" to JsonArray(pokemon(without) + extraMon)) }
        write(s, appended)
        assertEquals(pokemon(appended).map { text(it, "id") }, pokemon(save(s)).map { text(it, "id") })
        val inserted = withPlayer(appended) { it.with("pokemon" to JsonArray(listOf(extraMon.with("id" to JsonPrimitive("added-in-front"))) + pokemon(appended))) }
        write(s, inserted)
        assertEquals(pokemon(inserted).map { text(it, "id") }, pokemon(save(s)).map { text(it, "id") })
        // A party reads back in the order the app keeps it, lead first.
        val party = pokemon(inserted).filter { (it["inParty"] as JsonPrimitive).content == "true" && text(it, "partyId") == "plan" }.map { text(it, "id")!! }
        val lead = player(inserted).let { text(it, "planLeadId") } ?: party.first()
        assertEquals(listOf(lead) + party.filter { it != lead }, s.pokemon.party(ModuleIds.PLAN))
    }

    // --- one place -------------------------------------------------------------------------------------------------------

    @Test
    fun aPokemonHasOnePlaceAndTheTableCannotHoldTwo() {
        val s = moved("main-heavy-70-days.json")
        val places = s.pokemon.places()
        assertTrue(places.isNotEmpty())
        val id = places.keys.first()
        // Directly in the table: a second row for the same Pokémon, two buddies, or two in one slot are refused by the database itself.
        assertFailsWith<Exception> { s.driver.execute(null, "INSERT INTO pokemon_place(owned_id, kind, ref, slot) VALUES ('$id', 'guardian', '', 99)", 0) }
        val other = s.pokemon.all().map { it.id }.first { it !in places }
        assertFailsWith<Exception>("two buddies") {
            if (places.values.none { it == PokemonPlace.Buddy }) s.pokemon.setBuddy(id)
            s.driver.execute(null, "INSERT INTO pokemon_place(owned_id, kind, ref, slot) VALUES ('$other', 'buddy', '', 0)", 0)
        }
        // Through the repository every assignment takes the Pokémon from its old place.
        s.pokemon.setParty(ModuleIds.CARE, listOf(id))
        s.pokemon.setBuddy(id)
        assertEquals(PokemonPlace.Buddy, s.pokemon.placeOf(id))
        assertEquals(emptyList(), s.pokemon.party(ModuleIds.CARE))
        s.pokemon.setGuardians(listOf(id))
        assertEquals(PokemonPlace.Guardian, s.pokemon.placeOf(id))
        assertNull(s.pokemon.buddy())
        // Every place in the table names a different Pokémon.
        assertEquals(s.pokemon.places().size.toLong(), scalar(s, "SELECT count(*) FROM pokemon_place"))
    }

    @Test
    fun aSaveThatPutsAPokemonInTwoPlacesIsRefusedWholeBeforeAnythingIsWritten() {
        val s = moved("main-heavy-70-days.json")
        val doc = save(s)
        val inParty = pokemon(doc).first { (it["inParty"] as JsonPrimitive).content == "true" }
        val id = text(inParty, "id")!!
        val before = s.live.read("save")
        val coins = (player(doc)["coins"] as JsonPrimitive).content.toInt()

        // Buddy and party at once (an extra change in the same save, a payout, must not sneak through either).
        val buddyAndParty = withPlayer(doc) { it.with("buddyId" to JsonPrimitive(id), "coins" to JsonPrimitive(coins + 50)) }
        assertFailsWith<SaveNotRepresentableException> { write(s, buddyAndParty) }
        // Guardian and buddy.
        val guardianAndBuddy = withPlayer(doc) { it.with("buddyId" to JsonPrimitive(id), "guardians" to JsonArray(listOf(JsonPrimitive(id))), "pokemon" to JsonArray(pokemon(doc).map { if (text(it, "id") == id) it.with("inParty" to JsonPrimitive(false)) else it })) }
        assertFailsWith<SaveNotRepresentableException> { write(s, guardianAndBuddy) }
        // On a World team and in a party; and a Pokégear helper and in a party (the other two places live in the save row).
        val world = (doc["world"] as? JsonObject) ?: JsonObject(emptyMap())
        val team = JsonObject(mapOf("id" to JsonPrimitive(1), "members" to JsonArray(listOf(JsonPrimitive(id)))))
        assertFailsWith<SaveNotRepresentableException> { write(s, doc.with("world" to world.with("teams" to JsonArray(listOf(team))))) }
        assertFailsWith<SaveNotRepresentableException> { write(s, doc.with("gearHelpers" to JsonObject(mapOf("Calculator" to JsonPrimitive(id))))) }
        // Two teams, or a team and a helper, with the same Pokémon.
        val other = text(pokemon(doc).first { text(it, "id") != id && (it["inParty"] as JsonPrimitive).content == "false" }, "id")!!
        assertFailsWith<SaveNotRepresentableException> {
            write(s, doc.with("world" to world.with("teams" to JsonArray(listOf(team.with("members" to JsonArray(listOf(JsonPrimitive(other)))), team.with("id" to JsonPrimitive(2), "members" to JsonArray(listOf(JsonPrimitive(other)))))))))
        }

        // Nothing was written: not the Pokémon, not the balance, not the ledger.
        assertEquals(before, s.live.read("save"))
        assertEquals(coins.toLong(), s.economy.poke())
        assertEquals(emptyList(), s.economy.reconcile())
        // One place each, in the save row's company, is accepted: a Pokémon on a team that is not anywhere else.
        write(s, doc.with("world" to world.with("teams" to JsonArray(listOf(team.with("members" to JsonArray(listOf(JsonPrimitive(other)))))))))
        assertNull(s.pokemon.placeOf(other), "a World team is not a row here: it is a place the save row keeps")
    }

    @Test
    fun aDanglingBuddyOrGuardianIsRefused() {
        val s = moved("main-typical-week.json")
        val doc = save(s)
        assertFailsWith<SaveNotRepresentableException> { write(s, withPlayer(doc) { it.with("buddyId" to JsonPrimitive("nobody")) }) }
        assertFailsWith<SaveNotRepresentableException> { write(s, withPlayer(doc) { it.with("guardians" to JsonArray(listOf(JsonPrimitive("nobody")))) }) }
        same(doc, save(s), "unchanged")
    }

    // --- restore and the daily backup -------------------------------------------------------------------------------------

    @Test
    fun aBackupSnapshotRestoresThroughTheSameSplit() {
        val a = moved("main-heavy-70-days.json")
        val doc = save(a)
        write(a, withPlayer(doc) { it.with("coins" to JsonPrimitive((player(doc)["coins"] as JsonPrimitive).content.toInt() + 7)) }, 300)
        val snapshot = a.live.snapshot()

        val b = withSave(fixture("main-empty.json"))
        b.live.movePokemonToTables(7)
        b.live.replaceAll(snapshot, 400)
        same(obj(snapshot.getValue("save")), save(b), "restored")
        assertEquals(a.economy.poke(), b.economy.poke())
        assertEquals(a.pokemon.count(), b.pokemon.count())
        assertEquals(a.pokemon.places(), b.pokemon.places())
        assertEquals(emptyList(), b.economy.reconcile())
        // What the restore replaced is kept aside, whole.
        assertNotNull(b.live.read("aside:save:400"))
        same(obj(snapshot.getValue("save")), obj(b.live.snapshot().getValue("save")), "the snapshot after a restore")
    }

    @Test
    fun aBackupFromBeforeTheTablesIsCheckedByTheAppFirst() {
        // A 0.123.0 save has a buddy that is also in a party and no Poké merge record; restoring it must not fail or lose the World balance.
        val s = moved("main-empty.json")
        val old = oldSaveWithABuddyInTheParty()
        s.live.replaceAll(mapOf("save" to old), 500)
        same(obj(normalize(old, 500)), save(s), "the old save, as the app holds it")
        assertEquals(emptyList(), s.economy.reconcile())
        // And without the app's check a save the tables cannot represent is refused, with the current save kept.
        val t = moved("main-empty.json").also { it.live.normalizer = null }
        val before = t.live.read("save")
        assertFailsWith<SaveNotRepresentableException> { t.live.replaceAll(mapOf("save" to old), 501) }
        assertEquals(before, t.live.read("save"))
    }

    @Test
    fun theDocumentTheDeviceCheckUsesMovesInWithoutTheAppsCheck() {
        // The same save the Android DB check builds (DeviceSelfCheck): PLAN's tables already active, no normalizer.
        fun mon(id: String, inParty: Boolean, level: Int) =
            "{\"id\":\"$id\",\"speciesId\":25,\"formIdentifier\":\"pikachu\",\"nickname\":\"\",\"nature\":\"HARDY\",\"ability\":\"static\",\"gender\":null," +
                "\"ivs\":{\"hp\":1,\"attack\":2,\"defense\":3,\"spAttack\":4,\"spDefense\":5,\"speed\":6},\"evs\":{\"hp\":0,\"attack\":0,\"defense\":0,\"spAttack\":0,\"spDefense\":0,\"speed\":0}," +
                "\"level\":$level,\"candyProgress\":0,\"totalCandies\":0,\"friendship\":70,\"isShiny\":false,\"heldItem\":null,\"teraType\":null,\"cosmeticForm\":null," +
                "\"personality\":7,\"inParty\":$inParty,\"caughtAtMillis\":1,\"partyId\":\"plan\"}"
        fun save(coins: Int, level: Int) =
            "{\"player\":{\"pokemon\":[${mon("p1", false, level)},${mon("p2", true, 3)}],\"buddyId\":\"p1\",\"inventory\":{\"counts\":{\"rare-candy\":3}},\"coins\":$coins," +
                "\"eggMeter\":0,\"trainerPoints\":0,\"caught\":[25],\"guardians\":[],\"careLeadId\":null,\"planLeadId\":\"p2\",\"rolesVersion\":1}," +
                "\"favorites\":[\"p2\"],\"origins\":{},\"shinySpecies\":[],\"caughtForms\":[],\"shinyForms\":[],\"seenSpecies\":[25]}"
        val doc = "{\"player\":{\"coins\":1},\"tasks\":[{\"id\":\"t\",\"title\":\"T\"}],\"activeDays\":[\"2026-11-01\"],\"focus\":null}"
        val s = withSave(doc, normalizer = null)
        s.live.write("save", save(10, 5), 12)
        s.live.movePokemonToTables(13)
        assertTrue(s.pokemonTables.isActive())
        assertEquals(10L, s.economy.poke())
        assertEquals(mapOf("rare-candy" to 3L), s.economy.bag())
        s.live.write("save", save(15, 6), 14)
        assertEquals(15L, s.economy.poke())
        assertEquals(emptyList(), s.economy.reconcile())
        assertEquals(PokemonPlace.Buddy, s.pokemon.placeOf("p1"))
        assertEquals(PokemonPlace.Party("PLAN"), s.pokemon.placeOf("p2"))
        val back = s.live.read("save")!!
        assertTrue("\"level\":6" in back && "\"coins\":15" in back)
        assertEquals(back, s.live.snapshot()["save"])
    }

    // --- the schema --------------------------------------------------------------------------------------------------------

    @Test
    fun theTablesAreAtSchemaSixWithOnePlaceTable() {
        val s = JvmDatabase.open()
        assertEquals(10, DataStore.SCHEMA_VERSION) // Heal made it 7 (0.136.0), Mint's tables 8 (0.141.0), Home's tables 9, Link's tables 10
        val tables = mutableSetOf<String>()
        s.driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { c ->
            while (c.next().value) tables += c.getString(0)!!
            QueryResult.Unit
        }, 0)
        assertTrue("pokemon_place" in tables && "pokemon_state" in tables)
        assertTrue(tables.none { it in setOf("module_party", "buddy_selection", "guardian_slot") }, "the three place tables are one")
    }
}
