package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A save that cannot be kept in the shared tables as it is (a Pokémon in two places, a buddy that is not owned...). Nothing was written. */
class SaveNotRepresentableException(message: String) : IllegalStateException(message)

/**
 * The player's side of the save in the shared tables (schema version 6, REP-014; docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` sections
 * 4, 6 and 11 and `ARCHITECTURE/DATA_AND_MIGRATIONS.md` sections 7.3, 8 and 12). The same recipe as [PlanTables]: the app still reads and
 * writes one `AppData` document and [LiveDocuments] splits it, so the rules stay pure and tested, but the tables are the only authority
 * for what they hold and nothing is saved twice.
 *
 * What lives here ([PART_FIELDS]): the collection (`owned_pokemon`, in the order the Pokémon were owned), where each one is
 * (`pokemon_place`, **one row at most per Pokémon**: buddy, a module's party with the lead in slot 1, or a Guardian slot; none is the PC),
 * the Dex history (`dex_history`), the Egg meter and trainer points (`player_progress`), the Bag (`bag_stack`) and the Poké balance
 * (`currency_balance`) with the append-only ledger behind them, and a few single values (`pokemon_state`). A WORLD team and a GEAR helper
 * are the other two places; their records stay in the `save` row and [placesOutside] checks them against the rows here, so a save that has
 * a Pokémon in two places is refused before anything is written.
 *
 * **The ledger is the only writer of the balance and the Bag.** The save's `coins` and `inventory` are not stored as such: a write turns
 * the change since the last write into one ledger event in the same transaction (`EconomyRepository.settle`, idempotent, append-only) and
 * a read puts the balances back from the tables. The migration pays nothing: the balances it finds become one opening-balance event.
 *
 * It works on the JSON, not on the model, so `:data` stays independent of `pokemon-core`: a field of a Pokémon it has never heard of is
 * kept in `extra_json`, and a field of the player it has never heard of in `pokemon_state`.
 */
class PokemonTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    /** Whether the player's side lives here (set once by [activate]); before that it is inside the `save` row. */
    fun isActive(): Boolean = q.meta(META_POKEMON_TABLES).executeAsOneOrNull() == ACTIVE

    /** Takes the player's fields out of [document] (the save without PLAN's part): `first` is for these tables, `second` the rest. */
    fun split(document: JsonObject): Pair<JsonObject, JsonObject> =
        JsonObject(document.filterKeys { it in PART_FIELDS }) to JsonObject(document.filterKeys { it !in PART_FIELDS })

    /**
     * Saves the player's part of a save: only the rows that changed are touched, and the balance and the Bag move by one ledger event.
     * [rest] is the other part of the same save (for the one-place check). Call it inside a transaction.
     */
    fun write(part: JsonObject, rest: JsonObject, nowMillis: Long, opening: Boolean = false) {
        val player = part["player"] as? JsonObject ?: JsonObject(emptyMap())
        val owned = (player["pokemon"] as? JsonArray).orEmpty().map { it as? JsonObject ?: throw SaveNotRepresentableException("a Pokémon is not an object") }
        val ids = owned.map { it.text("id") ?: throw SaveNotRepresentableException("a Pokémon has no id") }
        if (ids.toSet().size != ids.size) throw SaveNotRepresentableException("two Pokémon share an id")
        val idSet = ids.toSet()

        val buddy = player.text("buddyId")
        val guardians = player.textList("guardians")
        if (buddy != null && buddy !in idSet) throw SaveNotRepresentableException("the buddy is not an owned Pokémon")
        guardians.firstOrNull { it !in idSet }?.let { throw SaveNotRepresentableException("a Guardian is not an owned Pokémon") }

        // Where each one is. A Pokémon can be named by only one place, here or on a World team or as a helper.
        val places = linkedMapOf<String, Place>()
        fun put(id: String, place: Place, what: String) { if (places.put(id, place) != null) throw SaveNotRepresentableException("$id is in two places ($what)") }
        buddy?.let { put(it, Place(KIND_BUDDY, "", 0), "the buddy") }
        guardians.forEachIndexed { i, id -> put(id, Place(KIND_GUARDIAN, "", i + 1), "a Guardian") }
        val byParty = linkedMapOf<String, MutableList<String>>()
        for ((index, o) in owned.withIndex()) if (o.bool("inParty") == true) byParty.getOrPut(o.text("partyId") ?: "") { mutableListOf() } += ids[index]
        for ((party, members) in byParty) {
            if (party.isEmpty()) throw SaveNotRepresentableException("a Pokémon is in a party that has no id")
            val chosen = when (party) { "plan" -> player.text("planLeadId"); "care" -> player.text("careLeadId"); "heal" -> player.text("healLeadId"); "mint" -> player.text("mintLeadId"); "home" -> player.text("homeLeadId"); else -> null }
            val ordered = chosen?.takeIf { it in members }?.let { lead -> listOf(lead) + members.filter { it != lead } } ?: members
            ordered.forEachIndexed { i, id -> put(id, Place(KIND_PARTY, party, i + 1), "the $party party") }
        }
        val outside = placesOutside(rest)
        outside.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { throw SaveNotRepresentableException("$it is in two places (World teams and Pokégear helpers)") }
        outside.firstOrNull { it in places }?.let { throw SaveNotRepresentableException("$it is in two places (${places.getValue(it).kind} and a World team or a Pokégear helper)") }

        // The collection.
        val favorites = part.textList("favorites").toSet()
        val origins = part["origins"] as? JsonObject ?: JsonObject(emptyMap())
        val have = store.pokemon.all().associateBy { it.id }
        val positions = positionsFor(ids, have)
        val wanted = owned.mapIndexed { i, o -> record(o, positions[i], ids[i] in favorites, origins[ids[i]] as? JsonObject) }
        for (gone in have.keys - idSet) q.releasePokemon(gone)
        for (r in wanted) {
            val old = have[r.id]
            when {
                old == null -> insert(r)
                old != r -> update(r)
            }
        }
        writePlaces(places)

        // The Dex history, the Egg meter and trainer points, and the single values.
        writeDex(DexKind.CAUGHT_SPECIES, player.longSet("caught"))
        writeDex(DexKind.SHINY_SPECIES, part.longSet("shinySpecies"))
        writeDex(DexKind.CAUGHT_FORM, part.longSet("caughtForms"))
        writeDex(DexKind.SHINY_FORM, part.longSet("shinyForms"))
        writeDex(DexKind.SEEN_SPECIES, part.longSet("seenSpecies"))
        val progress = PlayerProgress(player.long("eggMeter") ?: 0, player.long("trainerPoints") ?: 0)
        if (store.pokemon.progress() != progress) store.pokemon.setProgress(progress)
        rows.diffState("pokemon_state", state(player, favorites - idSet, origins.filterKeys { it !in idSet }))

        // The balance and the Bag: one ledger event for what changed.
        val coins = player.long("coins") ?: 0
        val counts = ((player["inventory"] as? JsonObject)?.get("counts") as? JsonObject).orEmpty().mapValues { (_, v) -> (v as? JsonPrimitive)?.longOrNull ?: throw SaveNotRepresentableException("a Bag count is not a number") }
        // What a module reward really was is settled under its own event id first; whatever is still different after that is the net change below.
        if (!opening) settleNotes(rest["ledgerNotes"], nowMillis)
        settleChange(coins, counts, nowMillis, opening, rest["pokeMigration"])
    }

    /**
     * Settles the save's ledger notes (`AppData.ledgerNotes`): each is a module reward with its own event id (`home:chore:<id>:<day>`, source module and opaque
     * reference), so the ledger says what really happened instead of only "net change of one save" (DATA_AND_MIGRATIONS.md section 7.3.0). The event id is the
     * idempotency key: a note already settled is skipped, so a replay, a restore or a second write pays nothing twice. A note that cannot be applied (it would take
     * a balance below zero, or is not a number) is left to the net change that follows, which always brings the tables to the document.
     */
    private fun settleNotes(notes: JsonElement?, nowMillis: Long) {
        for (element in (notes as? JsonArray).orEmpty()) {
            val note = element as? JsonObject ?: continue
            val id = note.text("eventId") ?: continue
            if (store.economy.hasEvent(id)) continue
            val poke = note.long("poke") ?: 0L
            val items = (note["items"] as? JsonObject).orEmpty().mapNotNull { (item, n) -> (n as? JsonPrimitive)?.longOrNull?.let { item to it } }.toMap()
            if (poke == 0L && items.isEmpty()) continue
            try {
                store.economy.settle(
                    RewardEventInput(
                        eventId = id, sourceModule = note.text("module") ?: "APP", sourceRecordId = note.text("ref") ?: id,
                        eventKind = note.text("kind") ?: "reward", policyVersion = LIVE_POLICY, occurredAt = note.long("atMillis") ?: nowMillis,
                        poke = poke, items = items,
                    ),
                )
            } catch (e: InsufficientFundsException) {
                // Left to the net change; nothing was applied (the repository checks before it writes).
            }
        }
    }

    /** The player's fields as stored, to be put back into the save with the rest. Empty until the first save has been written. */
    fun read(): JsonObject {
        val progress = store.pokemon.progress() ?: return JsonObject(emptyMap())
        val records = store.pokemon.all()
        val places = q.allPlaces().executeAsList().associateBy { it.owned_id }
        val state = rows.state("pokemon_state").mapValues { JSON.parseToJsonElement(it.value) }
        val leftover = state["player"] as? JsonObject ?: JsonObject(emptyMap())
        val buddy = places.values.firstOrNull { it.kind == KIND_BUDDY }?.owned_id
        val guardians = places.values.filter { it.kind == KIND_GUARDIAN }.sortedBy { it.slot }.map { it.owned_id }
        val bag = store.economy.bag()
        val inventory = JsonObject(
            mapOf("counts" to JsonObject(bag.mapValues { JsonPrimitive(it.value) })) + ((leftover["inventory"] as? JsonObject) ?: JsonObject(emptyMap())),
        )
        val player = JsonObject(
            leftover.filterKeys { it != "inventory" } + mapOf(
                "pokemon" to JsonArray(records.map { json(it, places[it.id]) }),
                "buddyId" to (buddy?.let { JsonPrimitive(it) } ?: JsonNull),
                "inventory" to inventory,
                "coins" to JsonPrimitive(store.economy.poke()),
                "eggMeter" to JsonPrimitive(progress.eggMeter),
                "trainerPoints" to JsonPrimitive(progress.trainerPoints),
                "caught" to numbers(DexKind.CAUGHT_SPECIES),
                "guardians" to JsonArray(guardians.map { JsonPrimitive(it) }),
            ),
        )
        val favoriteIds = records.filter { it.isFavorite }.map { it.id } + ((state["favorites"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
        val originEntries = records.mapNotNull { r -> origin(r)?.let { r.id to it } }.toMap() + ((state["origins"] as? JsonObject) ?: JsonObject(emptyMap()))
        return JsonObject(
            mapOf(
                "player" to player,
                "favorites" to JsonArray(favoriteIds.map { JsonPrimitive(it) }),
                "origins" to JsonObject(originEntries),
                "shinySpecies" to numbers(DexKind.SHINY_SPECIES),
                "caughtForms" to numbers(DexKind.CAUGHT_FORM),
                "shinyForms" to numbers(DexKind.SHINY_FORM),
                "seenSpecies" to numbers(DexKind.SEEN_SPECIES),
            ),
        )
    }

    /**
     * Marks the player's side as living here, after [write] has put [part] into the (empty) tables with the balances as one opening-balance
     * event. The caller reads the save back and compares it before the transaction ends. Refuses tables that already hold something: they
     * hold a legacy import, not this save.
     */
    fun begin(part: JsonObject, rest: JsonObject, nowMillis: Long) {
        check(store.pokemon.count() == 0L && q.eventCount().executeAsOne() == 0L && q.ledgerCount().executeAsOne() == 0L) {
            "the Pokémon and ledger tables are not empty; they hold something that is not this save"
        }
        write(part, rest, nowMillis, opening = true)
        mark(nowMillis)
    }

    /** Fresh install, no save yet: the tables are the store from the start. */
    fun beginEmpty(nowMillis: Long) = mark(nowMillis)

    private fun mark(nowMillis: Long) {
        q.setMeta(META_POKEMON_TABLES, ACTIVE)
        q.setMeta(META_POKEMON_TABLES_AT, nowMillis.toString())
    }

    /** Erases the whole player side (the Pokémon, their places, the Dex, the single values), in one transaction. The ledger stays: it is history. */
    fun clear() {
        store.transaction {
            for (id in store.pokemon.all().map { it.id }) q.releasePokemon(id)
            for (kind in DexKind.entries) writeDex(kind, emptySet())
            store.driver.execute(null, "DELETE FROM pokemon_state", 0)
            store.driver.execute(null, "DELETE FROM player_progress", 0)
        }
    }

    // --- the one-place rule --------------------------------------------------------------------------------------------

    private class Place(val kind: String, val ref: String, val slot: Int)

    private fun writePlaces(wanted: Map<String, Place>) {
        val have = q.allPlaces().executeAsList().associateBy { it.owned_id }
        fun same(id: String): Boolean { val h = have[id] ?: return false; val w = wanted[id] ?: return false; return h.kind == w.kind && h.ref == w.ref && h.slot == w.slot.toLong() }
        // Everything that moved goes first, so a slot another Pokémon takes over is free when it is written.
        for (id in have.keys) if (id in wanted && !same(id) || id !in wanted) q.removePlace(id)
        for ((id, p) in wanted) if (!same(id)) q.insertPlace(id, p.kind, p.ref, p.slot.toLong())
    }

    /** The Pokémon ids named by a World team or a Pokégear helper (the other two places), read from the save's other part. */
    internal fun placesOutside(rest: JsonObject): List<String> {
        val teams = ((rest["world"] as? JsonObject)?.get("teams") as? JsonArray).orEmpty()
            .flatMap { team -> ((team as? JsonObject)?.get("members") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
        val helpers = (rest["gearHelpers"] as? JsonObject).orEmpty().values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return teams + helpers
    }

    // --- one Pokémon as a row and back ---------------------------------------------------------------------------------

    private fun record(o: JsonObject, position: Int, favorite: Boolean, origin: JsonObject?): OwnedPokemonRecord {
        val id = o.text("id")!!
        fun req(key: String) = o.text(key) ?: throw SaveNotRepresentableException("$id has no $key")
        // Fields without a column are kept whole; so is the party id of a Pokémon that is not in a party (it means nothing, but it must come back).
        val extra = o.filterKeys { it !in KNOWN_POKEMON_FIELDS } + (if (o.bool("inParty") == true) emptyMap() else o["partyId"]?.let { mapOf("partyId" to it) } ?: emptyMap())
        return OwnedPokemonRecord(
            id = id,
            speciesId = o.long("speciesId")?.toInt() ?: throw SaveNotRepresentableException("$id has no speciesId"),
            formIdentifier = req("formIdentifier"),
            nickname = o.text("nickname") ?: "",
            nature = req("nature"),
            ability = o.text("ability") ?: "",
            gender = o.text("gender"),
            ivs = spread(o["ivs"] as? JsonObject ?: throw SaveNotRepresentableException("$id has no ivs")),
            evs = (o["evs"] as? JsonObject)?.let { spread(it) } ?: Spread.ZERO,
            level = o.long("level")?.toInt() ?: 1,
            candyProgress = o.long("candyProgress")?.toInt() ?: 0,
            totalCandies = o.long("totalCandies")?.toInt() ?: 0,
            friendship = o.long("friendship")?.toInt() ?: 70,
            isShiny = o.bool("isShiny") ?: false,
            heldItem = o.text("heldItem"),
            teraType = o.text("teraType"),
            cosmeticForm = o.text("cosmeticForm"),
            personality = o.long("personality")?.toInt() ?: 0,
            caughtAtMillis = o.long("caughtAtMillis") ?: throw SaveNotRepresentableException("$id has no caughtAtMillis"),
            isFavorite = favorite,
            originHow = origin?.text("how"),
            originRegion = origin?.text("region"),
            extraJson = extra.takeIf { it.isNotEmpty() }?.let { JSON.encodeToString(JsonObject.serializer(), JsonObject(it)) },
            position = position,
            originJson = origin?.let { JSON.encodeToString(JsonObject.serializer(), it) },
        )
    }

    private fun json(r: OwnedPokemonRecord, place: Pokemon_place?): JsonObject {
        val extra = r.extraJson?.let { JSON.parseToJsonElement(it) as JsonObject } ?: JsonObject(emptyMap())
        val inParty = place?.kind == KIND_PARTY
        fun nullable(s: String?): JsonElement = s?.let { JsonPrimitive(it) } ?: JsonNull
        return JsonObject(
            extra + buildMap {
                put("id", JsonPrimitive(r.id)); put("speciesId", JsonPrimitive(r.speciesId)); put("formIdentifier", JsonPrimitive(r.formIdentifier))
                put("nickname", JsonPrimitive(r.nickname)); put("nature", JsonPrimitive(r.nature)); put("ability", JsonPrimitive(r.ability))
                put("gender", nullable(r.gender)); put("ivs", spread(r.ivs)); put("evs", spread(r.evs))
                put("level", JsonPrimitive(r.level)); put("candyProgress", JsonPrimitive(r.candyProgress)); put("totalCandies", JsonPrimitive(r.totalCandies))
                put("friendship", JsonPrimitive(r.friendship)); put("isShiny", JsonPrimitive(r.isShiny))
                put("heldItem", nullable(r.heldItem)); put("teraType", nullable(r.teraType)); put("cosmeticForm", nullable(r.cosmeticForm))
                put("personality", JsonPrimitive(r.personality)); put("inParty", JsonPrimitive(inParty)); put("caughtAtMillis", JsonPrimitive(r.caughtAtMillis))
                if (inParty) put("partyId", JsonPrimitive(place!!.ref))
            },
        )
    }

    private fun origin(r: OwnedPokemonRecord): JsonObject? = when {
        r.originJson != null -> JSON.parseToJsonElement(r.originJson) as JsonObject
        // A row an older importer wrote has only the two lookup columns.
        r.originHow != null -> buildJsonObject { put("how", r.originHow); put("region", r.originRegion) }
        else -> null
    }

    private fun spread(o: JsonObject) = Spread(
        o.long("hp")?.toInt() ?: 0, o.long("attack")?.toInt() ?: 0, o.long("defense")?.toInt() ?: 0,
        o.long("spAttack")?.toInt() ?: 0, o.long("spDefense")?.toInt() ?: 0, o.long("speed")?.toInt() ?: 0,
    )

    private fun spread(s: Spread) = buildJsonObject {
        put("hp", s.hp); put("attack", s.attack); put("defense", s.defense); put("spAttack", s.spAttack); put("spDefense", s.spDefense); put("speed", s.speed)
    }

    /**
     * Where each Pokémon sits in the collection's list. Positions are kept as they are so that a change touches only its own row; they are
     * renumbered from 1 only when an insert in the middle or a reorder makes the old ones disagree with the list.
     */
    private fun positionsFor(ids: List<String>, have: Map<String, OwnedPokemonRecord>): List<Int> {
        val known = ids.mapNotNull { have[it]?.position }
        val inOrder = known.zipWithNext().all { (a, b) -> a < b }
        val lastKnown = ids.indexOfLast { it in have }
        val newOnlyAtTheEnd = ids.withIndex().none { (i, id) -> id !in have && i < lastKnown }
        if (!inOrder || !newOnlyAtTheEnd || known.any { it <= 0 }) return ids.indices.map { it + 1 }
        var next = (have.values.maxOfOrNull { it.position } ?: 0) + 1
        return ids.map { have[it]?.position ?: next++ }
    }

    private fun insert(p: OwnedPokemonRecord) = store.pokemon.add(p)

    private fun update(p: OwnedPokemonRecord) {
        q.updatePokemon(
            p.speciesId.toLong(), p.formIdentifier, p.nickname, p.nature, p.ability, p.gender,
            p.ivs.hp.toLong(), p.ivs.attack.toLong(), p.ivs.defense.toLong(), p.ivs.spAttack.toLong(), p.ivs.spDefense.toLong(), p.ivs.speed.toLong(),
            p.evs.hp.toLong(), p.evs.attack.toLong(), p.evs.defense.toLong(), p.evs.spAttack.toLong(), p.evs.spDefense.toLong(), p.evs.speed.toLong(),
            p.level.toLong(), p.candyProgress.toLong(), p.totalCandies.toLong(), p.friendship.toLong(), if (p.isShiny) 1 else 0,
            p.heldItem, p.teraType, p.cosmeticForm, p.personality.toLong(), p.caughtAtMillis,
            if (p.isFavorite) 1 else 0, p.originHow, p.originRegion, p.extraJson, p.position.toLong(), p.originJson, p.id,
        )
    }

    // --- Dex, single values -------------------------------------------------------------------------------------------

    private fun writeDex(kind: DexKind, wanted: Set<Long>) {
        val have = store.pokemon.dex(kind).toSet()
        for (gone in have - wanted) store.driver.execute(null, "DELETE FROM dex_history WHERE kind = ? AND ref = ?", 2) { bindString(0, kind.key); bindLong(1, gone) }
        for (ref in wanted - have) store.pokemon.addDex(kind, ref)
    }

    private fun numbers(kind: DexKind) = JsonArray(store.pokemon.dex(kind).map { JsonPrimitive(it) })

    /** The player's fields that have no table (the leads, the roles version, anything a newer build adds), and the favourites and origins of Pokémon that are gone. */
    private fun state(player: JsonObject, strayFavorites: Set<String>, strayOrigins: Map<String, JsonElement>): Map<String, String> = buildMap {
        val leftover = player.filterKeys { it !in HANDLED_PLAYER_FIELDS } +
            ((player["inventory"] as? JsonObject)?.filterKeys { it != "counts" }?.takeIf { it.isNotEmpty() }?.let { mapOf("inventory" to JsonObject(it)) } ?: emptyMap())
        if (leftover.isNotEmpty()) put("player", JSON.encodeToString(JsonObject.serializer(), JsonObject(leftover)))
        if (strayFavorites.isNotEmpty()) put("favorites", JSON.encodeToString(JsonArray.serializer(), JsonArray(strayFavorites.sorted().map { JsonPrimitive(it) })))
        if (strayOrigins.isNotEmpty()) put("origins", JSON.encodeToString(JsonObject.serializer(), JsonObject(strayOrigins)))
    }

    // --- the ledger -----------------------------------------------------------------------------------------------------

    /**
     * Brings the balance and the Bag to [coins] and [counts] with one ledger event (nothing when they already agree). The event is a
     * net change of one save, named by a running number, so a retry that finds the balances already right pays nothing. [opening] is the
     * migration's: the balances found become the opening balance, with the two legacy source values kept as audit metadata.
     */
    private fun settleChange(coins: Long, counts: Map<String, Long>, nowMillis: Long, opening: Boolean, pokeMigration: JsonElement?) {
        val dPoke = coins - store.economy.poke()
        val bag = store.economy.bag()
        val dItems = (counts.keys + bag.keys).associateWith { (counts[it] ?: 0L) - (bag[it] ?: 0L) }.filterValues { it != 0L }
        if (dPoke == 0L && dItems.isEmpty()) return
        val event = if (opening) {
            RewardEventInput(
                eventId = OPENING_EVENT, sourceModule = "MIGRATION", sourceRecordId = "live-save", eventKind = "opening-balance", policyVersion = LIVE_POLICY,
                occurredAt = nowMillis, poke = dPoke, items = dItems,
                metadataJson = """{"saveSchema":1,"pokeMigration":${pokeMigration?.toString() ?: "null"}}""",
            )
        } else {
            val n = (q.meta(META_LEDGER_SEQ).executeAsOneOrNull()?.toLongOrNull() ?: 0L) + 1
            q.setMeta(META_LEDGER_SEQ, n.toString())
            RewardEventInput(
                eventId = "save:$n", sourceModule = "APP", sourceRecordId = "save", eventKind = "net-change", policyVersion = LIVE_POLICY,
                occurredAt = nowMillis, poke = dPoke, items = dItems,
            )
        }
        store.economy.settle(event)
    }

    // --- JSON helpers --------------------------------------------------------------------------------------------------

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
    private fun JsonObject.textList(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun JsonObject.longSet(key: String): Set<Long> = (this[key] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.longOrNull ?: throw SaveNotRepresentableException("$key holds something that is not a number") }.toSet()

    companion object {
        const val META_POKEMON_TABLES = "pokemon_store"
        const val META_POKEMON_TABLES_AT = "pokemon_store_since"
        const val ACTIVE = "tables"
        private const val META_LEDGER_SEQ = "ledger_seq"
        private const val LIVE_POLICY = "live-save"
        const val OPENING_EVENT = "live:save:opening-balance"

        private const val KIND_BUDDY = PokemonRepository.KIND_BUDDY
        private const val KIND_PARTY = PokemonRepository.KIND_PARTY
        private const val KIND_GUARDIAN = PokemonRepository.KIND_GUARDIAN

        private val JSON = Json { encodeDefaults = true }

        /** The save's top-level fields that belong here; the rest of the save is not the player's. */
        val PART_FIELDS = setOf("player", "favorites", "origins", "shinySpecies", "caughtForms", "shinyForms", "seenSpecies")

        private val SET_FIELDS = setOf("favorites", "shinySpecies", "caughtForms", "shinyForms", "seenSpecies")

        /** The fields of the player that have a table; every other field (the leads, the roles version, a newer build's) goes to `pokemon_state`. */
        private val HANDLED_PLAYER_FIELDS = setOf("pokemon", "buddyId", "inventory", "coins", "eggMeter", "trainerPoints", "caught", "guardians")

        /** The fields of a Pokémon that have a column (`inParty` and `partyId` are its place). */
        private val KNOWN_POKEMON_FIELDS = setOf(
            "id", "speciesId", "formIdentifier", "nickname", "nature", "ability", "gender", "ivs", "evs", "level", "candyProgress", "totalCandies",
            "friendship", "isShiny", "heldItem", "teraType", "cosmeticForm", "personality", "inParty", "caughtAtMillis", "partyId",
        )

        /**
         * The save with what a read back cannot tell apart left out: a set is a set (its order is not kept), and a Bag entry of zero is not
         * a stack. Used to check that a save came back equal.
         */
        internal fun canonical(document: JsonObject): JsonObject {
            fun sorted(e: JsonElement?): JsonElement? = (e as? JsonArray)?.let { a -> JsonArray(a.sortedBy { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.distinct()) } ?: e
            val player = (document["player"] as? JsonObject)?.let { p ->
                JsonObject(
                    p.mapValues { (key, value) ->
                        when {
                            key == "caught" -> sorted(value)!!
                            key == "inventory" && value is JsonObject ->
                                JsonObject(value.mapValues { (k, v) -> if (k == "counts" && v is JsonObject) JsonObject(v.filterValues { (it as? JsonPrimitive)?.longOrNull != 0L }) else v })
                            else -> value
                        }
                    },
                )
            }
            return JsonObject(document.mapValues { (key, value) -> if (key == "player" && player != null) player else if (key in SET_FIELDS) sorted(value)!! else value })
        }
    }
}
