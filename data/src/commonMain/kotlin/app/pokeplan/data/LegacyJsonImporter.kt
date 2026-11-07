package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long

/** The import could not finish. Nothing was written: the whole import is one transaction. */
class ImportFailedException(val migrationId: String, message: String, cause: Throwable? = null) :
    IllegalStateException("Import $migrationId failed: $message", cause)

/** A different save was already imported into this database, or the database already holds data. */
class ImportRejectedException(message: String) : IllegalStateException(message)

data class ImportWarning(val code: String, val detail: String)

data class ImportReport(
    val migrationId: String,
    /** True when this exact pair of files had been imported before; nothing was written this time. */
    val alreadyImported: Boolean,
    val counts: Map<String, Long>,
    val warnings: List<ImportWarning>,
)

/**
 * The one-time import of a 0.123.0 `save.json` and `care.json` (DATA_AND_MIGRATIONS.md section 12).
 *
 * It takes the two files **as text** and never touches them. It reads them as JSON, not into the live model, so
 * a field it does not know is kept rather than dropped: every top-level field with no table yet goes to the legacy
 * sidecar as raw JSON, and an unknown field of a Pokémon goes to its `extra_json`. Nothing is paid: the reward log
 * is kept for display, balances are written once as an opening-balance ledger entry, and the engine never runs.
 *
 * The import is all or nothing and idempotent: its identity is a hash of the two inputs, the same inputs import
 * once, and a different save is refused instead of being merged over the first.
 */
class LegacyJsonImporter(private val store: DataStore) {
    private val json = Json { ignoreUnknownKeys = true }

    fun import(saveJson: String, careJson: String?, nowMillis: Long): ImportReport {
        val checksum = Sha256.hex(saveJson + "\u0000" + (careJson ?: ""))
        val migrationId = "legacy-json-${checksum.take(16)}"
        store.db.dataQueries.migrationById(migrationId).executeAsOneOrNull()?.let {
            return ImportReport(migrationId, true, readCounts(it.summary_json), store.db.dataQueries.warningsOf(migrationId).executeAsList().map { w -> ImportWarning(w.code, w.detail) })
        }
        val done = store.db.dataQueries.meta(META_IMPORTED).executeAsOneOrNull()
        if (done != null) throw ImportRejectedException("a different save was already imported ($done)")
        if (store.pokemon.count() > 0 || store.economy.hasEvent(openingEventId(migrationId))) throw ImportRejectedException("the database already holds data")

        val save = parse(saveJson, "save.json", migrationId)
        val care = careJson?.let { parse(it, "care.json", migrationId) }
        val schema = (save["schema"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        if (schema != SUPPORTED_SAVE_SCHEMA) throw ImportFailedException(migrationId, "save schema $schema is not supported (only $SUPPORTED_SAVE_SCHEMA)")

        val warnings = mutableListOf<ImportWarning>()
        val counts = linkedMapOf<String, Long>()
        try {
            store.transaction {
                importAll(save, care, migrationId, checksum, nowMillis, warnings, counts)
            }
        } catch (e: ImportFailedException) {
            throw e
        } catch (e: Exception) {
            throw ImportFailedException(migrationId, e.message ?: e.toString(), e)
        }
        return ImportReport(migrationId, false, counts, warnings)
    }

    private fun parse(text: String, name: String, migrationId: String): JsonObject =
        try {
            json.parseToJsonElement(text) as? JsonObject ?: throw ImportFailedException(migrationId, "$name is not a JSON object")
        } catch (e: ImportFailedException) {
            throw e
        } catch (e: Exception) {
            throw ImportFailedException(migrationId, "$name cannot be read: ${e.message}", e)
        }

    private fun importAll(
        save: JsonObject, care: JsonObject?, migrationId: String, checksum: String, nowMillis: Long,
        warnings: MutableList<ImportWarning>, counts: MutableMap<String, Long>,
    ) {
        val q = store.db.dataQueries
        val player = (save["player"] as? JsonObject) ?: JsonObject(emptyMap())
        val favorites = save.stringSet("favorites")
        val origins = (save["origins"] as? JsonObject) ?: JsonObject(emptyMap())

        // 1. Pokémon, in the order they were owned.
        val records = (player["pokemon"] as? JsonArray).orEmpty().map { toRecord(it as JsonObject, favorites, origins) }
        val ids = records.map { it.id }
        check(ids.toSet().size == ids.size) { "two Pokémon share an id" }
        records.forEach { store.pokemon.add(it) }
        val owned = ids.toSet()
        favorites.filter { it !in owned }.forEach { warnings += ImportWarning("favorite_unknown_pokemon", it) }
        origins.keys.filter { it !in owned }.forEach { warnings += ImportWarning("origin_unknown_pokemon", it) }

        // 2. Parties: one per module, in the order the Pokémon were listed; the buddy / Care lead goes first (REP-009). A Pokémon is in one place
        // only (owner decision, 7 November 2026), so the buddy, then a Guardian, take it from a party they were also in, as the app's Places.resolve does.
        val buddyId = player.string("buddyId")?.also { if (it !in owned) warnings += ImportWarning("buddy_unknown_pokemon", it) }?.takeIf { it in owned }
        val careLead = player.string("careLeadId")
        val byModule = linkedMapOf<String, MutableList<String>>()
        for (raw in (player["pokemon"] as JsonArray)) {
            raw as JsonObject
            if (raw.bool("inParty") != true) continue
            val legacyId = raw.string("partyId") ?: "plan"
            val module = legacyId.uppercase()
            if (module !in ModuleIds.WITH_PARTY) warnings += ImportWarning("unknown_party_id", "$legacyId on ${raw.string("id")}")
            byModule.getOrPut(module) { mutableListOf() } += raw.string("id")!!
        }
        for ((module, members) in byModule) {
            val lead = when (module) {
                ModuleIds.PLAN -> buddyId
                ModuleIds.CARE -> careLead
                else -> null
            }
            val ordered = if (lead != null && lead in members) listOf(lead) + members.filter { it != lead } else members.toList()
            if (module == ModuleIds.CARE && careLead != null && careLead !in members) warnings += ImportWarning("care_lead_not_in_party", careLead)
            val kept = ordered.take(ModuleIds.PARTY_CAPACITY)
            ordered.drop(ModuleIds.PARTY_CAPACITY).forEach { warnings += ImportWarning("party_over_capacity", "$module: $it stays owned but left the party") }
            if (module in ModuleIds.WITH_PARTY) {
                store.pokemon.setParty(module, kept)
            } else warnings += ImportWarning("party_dropped", "$module: ${kept.size} members have no module to belong to")
        }

        // 3. The global buddy starts as the old PLAN buddy; Guardians keep their slots.
        buddyId?.let { store.pokemon.setBuddy(it) }
        val guardians = player.stringList("guardians").filter { g -> (g in owned).also { if (!it) warnings += ImportWarning("guardian_unknown_pokemon", g) } }
        store.pokemon.setGuardians(guardians)
        val placed = store.pokemon.places()
        for ((module, members) in byModule) for (id in members) {
            val now = placed[id]
            if (module in ModuleIds.WITH_PARTY && now != PokemonPlace.Party(module) && id in owned) warnings += ImportWarning("place_conflict", "$id left the ${module.lowercase()} party for ${now?.let { it::class.simpleName } ?: "the PC"} (one place only)")
        }
        val partySlots = q.partySlotCount().executeAsOne()

        // 4. Dex history.
        val dexSources = mapOf(
            DexKind.CAUGHT_SPECIES to player.longSet("caught"),
            DexKind.SHINY_SPECIES to save.longSet("shinySpecies"),
            DexKind.CAUGHT_FORM to save.longSet("caughtForms"),
            DexKind.SHINY_FORM to save.longSet("shinyForms"),
            DexKind.SEEN_SPECIES to save.longSet("seenSpecies"),
        )
        dexSources.forEach { (kind, refs) -> refs.forEach { store.pokemon.addDex(kind, it) } }

        // 5. Progress, Poké and the Bag. Both legacy balances and both inventories become one opening-balance event.
        store.pokemon.setProgress(PlayerProgress(player.long("eggMeter") ?: 0, player.long("trainerPoints") ?: 0))
        val world = (save["world"] as? JsonObject) ?: JsonObject(emptyMap())
        val coins = player.long("coins") ?: 0
        val worldPoke = world.long("poke") ?: 0
        val bag = mutableMapOf<String, Long>()
        ((player["inventory"] as? JsonObject)?.get("counts") as? JsonObject)?.forEach { (item, n) -> bag.merge(item, (n as JsonPrimitive).long, Long::plus) }
        val worldItems = (world["items"] as? JsonObject)?.mapValues { (it.value as JsonPrimitive).long } ?: emptyMap()
        worldItems.forEach { (item, n) -> bag.merge(item, n, Long::plus) }
        val openingMetadata = """{"legacyCoins":$coins,"legacyWorldPoke":$worldPoke,"legacyWorldItems":${JsonObject(worldItems.mapValues { JsonPrimitive(it.value) })},"saveSchema":$SUPPORTED_SAVE_SCHEMA,"migrationId":${jsonString(migrationId)}}"""
        store.economy.settle(
            RewardEventInput(
                eventId = openingEventId(migrationId), sourceModule = "MIGRATION", sourceRecordId = migrationId, eventKind = "opening-balance",
                policyVersion = LEGACY_POLICY, occurredAt = nowMillis, poke = coins + worldPoke,
                items = bag.filterValues { it > 0 }, metadataJson = openingMetadata,
            ),
        )

        // 6. Everything else goes to the sidecar, untouched; the cloud account and sync state are not imported.
        var sidecarRows = 0L
        fun sidecar(source: String, path: String, value: JsonElement) { q.insertSidecar(source, path, value.toString()); sidecarRows++ }
        val playerHandled = setOf("pokemon", "buddyId", "inventory", "coins", "eggMeter", "trainerPoints", "caught", "guardians", "careLeadId")
        val saveHandled = setOf("schema", "player", "favorites", "origins", "shinySpecies", "caughtForms", "shinyForms", "seenSpecies")
        for ((key, value) in save) {
            when {
                key in saveHandled -> {}
                key == "account" -> if (value !is JsonNull) warnings += ImportWarning("cloud_account_not_imported", "the signed-in account and its session are not carried over")
                key == "world" -> sidecar("save", "world", JsonObject(world - "poke" - "items"))
                else -> sidecar("save", key, value)
            }
        }
        for ((key, value) in player) if (key !in playerHandled) sidecar("save", "player.$key", value)
        care?.forEach { (key, value) ->
            if (key == "cloud") { if (value !is JsonNull) warnings += ImportWarning("cloud_account_not_imported", "Care's cloud copy state is not carried over") }
            else sidecar("care", key, value)
        }
        q.setMeta("legacy_save_schema", SUPPORTED_SAVE_SCHEMA.toString())

        // 7. Verify against the source before committing; any mismatch throws and rolls everything back.
        counts["pokemon"] = records.size.toLong()
        counts["partySlots"] = partySlots
        counts["guardians"] = guardians.size.toLong()
        counts["dexEntries"] = dexSources.values.sumOf { it.size }.toLong()
        counts["bagStacks"] = bag.count { it.value > 0 }.toLong()
        counts["poke"] = coins + worldPoke
        counts["sidecarRows"] = sidecarRows
        counts["rewardLogEntries"] = (save["rewardLog"] as? JsonArray)?.size?.toLong() ?: 0
        verify(counts, records.size, dexSources, bag, coins + worldPoke)

        val summary = JsonObject(counts.mapValues { JsonPrimitive(it.value) }).toString()
        q.insertMigration(migrationId, "legacy_json_import", checksum, nowMillis, summary)
        q.setMeta(META_IMPORTED, migrationId)
        warnings.forEach { q.insertWarning(migrationId, it.code, it.detail) }
    }

    private fun verify(counts: Map<String, Long>, expectedPokemon: Int, dex: Map<DexKind, Set<Long>>, bag: Map<String, Long>, expectedPoke: Long) {
        check(store.pokemon.count() == expectedPokemon.toLong()) { "Pokémon count: wrote ${store.pokemon.count()}, source has $expectedPokemon" }
        check(store.economy.poke() == expectedPoke) { "Poké: wrote ${store.economy.poke()}, source has $expectedPoke" }
        check(store.economy.bag() == bag.filterValues { it > 0 }) { "the Bag differs from the source" }
        for ((kind, refs) in dex) check(store.pokemon.dex(kind).toSet() == refs) { "Dex ${kind.key} differs from the source" }
        check(store.db.dataQueries.partySlotCount().executeAsOne() == counts["partySlots"]) { "party slots differ" }
        check(store.pokemon.places().size.toLong() == store.db.dataQueries.placeCount().executeAsOne()) { "a Pokémon is in two places" }
        val problems = store.economy.reconcile() + store.integrityIssues()
        check(problems.isEmpty()) { problems.joinToString("; ") }
    }

    private fun toRecord(o: JsonObject, favorites: Set<String>, origins: JsonObject): OwnedPokemonRecord {
        val id = o.string("id") ?: error("a Pokémon has no id")
        fun req(key: String) = o.string(key) ?: error("$id has no $key")
        val known = setOf(
            "id", "speciesId", "formIdentifier", "nickname", "nature", "ability", "gender", "ivs", "evs", "level", "candyProgress",
            "totalCandies", "friendship", "isShiny", "heldItem", "teraType", "cosmeticForm", "personality", "inParty", "caughtAtMillis", "partyId",
        )
        val extra = o.filterKeys { it !in known }
        val origin = origins[id] as? JsonObject
        return OwnedPokemonRecord(
            id = id,
            speciesId = o.long("speciesId")?.toInt() ?: error("$id has no speciesId"),
            formIdentifier = req("formIdentifier"),
            nickname = o.string("nickname") ?: "",
            nature = req("nature"),
            ability = o.string("ability") ?: "",
            gender = o.string("gender"),
            ivs = spread(o["ivs"] as? JsonObject ?: error("$id has no ivs")),
            evs = (o["evs"] as? JsonObject)?.let { spread(it) } ?: Spread.ZERO,
            level = o.long("level")?.toInt() ?: 1,
            candyProgress = o.long("candyProgress")?.toInt() ?: 0,
            totalCandies = o.long("totalCandies")?.toInt() ?: 0,
            friendship = o.long("friendship")?.toInt() ?: 70,
            isShiny = o.bool("isShiny") ?: false,
            heldItem = o.string("heldItem"),
            teraType = o.string("teraType"),
            cosmeticForm = o.string("cosmeticForm"),
            personality = o.long("personality")?.toInt() ?: 0,
            caughtAtMillis = o.long("caughtAtMillis") ?: error("$id has no caughtAtMillis"),
            isFavorite = id in favorites,
            originHow = origin?.string("how"),
            originRegion = origin?.string("region"),
            originJson = origin?.toString(),
            extraJson = extra.takeIf { it.isNotEmpty() }?.let { JsonObject(it).toString() },
        )
    }

    private fun spread(o: JsonObject) = Spread(
        o.long("hp")?.toInt() ?: 0, o.long("attack")?.toInt() ?: 0, o.long("defense")?.toInt() ?: 0,
        o.long("spAttack")?.toInt() ?: 0, o.long("spDefense")?.toInt() ?: 0, o.long("speed")?.toInt() ?: 0,
    )

    private fun readCounts(summary: String): Map<String, Long> =
        (json.parseToJsonElement(summary) as JsonObject).mapValues { (it.value as JsonPrimitive).long }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.boolean
    private fun JsonObject.stringList(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun JsonObject.stringSet(key: String): Set<String> = stringList(key).toSet()
    private fun JsonObject.longSet(key: String): Set<Long> = (this[key] as? JsonArray).orEmpty().map { (it as JsonPrimitive).long }.toSet()

    companion object {
        const val SUPPORTED_SAVE_SCHEMA = 1
        const val LEGACY_POLICY = "legacy-0.123.0"
        private const val META_IMPORTED = "legacy_import_migration_id"

        fun openingEventId(migrationId: String) = "legacy:$migrationId:opening-balance"
    }
}
