package app.pokeplan.data


class PokemonRepository internal constructor(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun add(p: OwnedPokemonRecord) = store.transaction {
        val position = if (p.position > 0) p.position else q.nextPokemonPosition().executeAsOne().toInt()
        q.insertPokemon(
            p.id, p.speciesId.toLong(), p.formIdentifier, p.nickname, p.nature, p.ability, p.gender,
            p.ivs.hp.toLong(), p.ivs.attack.toLong(), p.ivs.defense.toLong(), p.ivs.spAttack.toLong(), p.ivs.spDefense.toLong(), p.ivs.speed.toLong(),
            p.evs.hp.toLong(), p.evs.attack.toLong(), p.evs.defense.toLong(), p.evs.spAttack.toLong(), p.evs.spDefense.toLong(), p.evs.speed.toLong(),
            p.level.toLong(), p.candyProgress.toLong(), p.totalCandies.toLong(), p.friendship.toLong(), if (p.isShiny) 1 else 0,
            p.heldItem, p.teraType, p.cosmeticForm, p.personality.toLong(), p.caughtAtMillis,
            if (p.isFavorite) 1 else 0, p.originHow, p.originRegion, p.extraJson, position.toLong(), p.originJson,
        )
    }

    fun get(id: String): OwnedPokemonRecord? = q.pokemonById(id).executeAsOneOrNull()?.toRecord()

    fun all(): List<OwnedPokemonRecord> = q.allPokemon().executeAsList().map { it.toRecord() }

    fun count(): Long = q.pokemonCount().executeAsOne()

    fun release(id: String) = store.transaction {
        q.releasePokemon(id)
        compact()
    }

    fun placeOf(id: String): PokemonPlace? = q.placeOf(id).executeAsOneOrNull()?.let { place(it.kind, it.ref) }

    fun setParty(moduleId: String, ownedIds: List<String>) {
        require(moduleId in ModuleIds.WITH_PARTY) { "$moduleId has no party" }
        require(ownedIds.size <= ModuleIds.PARTY_CAPACITY) { "a party holds at most ${ModuleIds.PARTY_CAPACITY} (the lead and five members)" }
        require(ownedIds.toSet().size == ownedIds.size) { "a Pokémon can be in a party only once" }
        store.transaction {
            q.clearPlaces(KIND_PARTY, moduleId.lowercase())
            ownedIds.forEachIndexed { i, id ->
                q.removePlace(id)
                q.insertPlace(id, KIND_PARTY, moduleId.lowercase(), (i + 1).toLong())
            }
            compact()
        }
    }

    fun party(moduleId: String): List<String> = q.partyOf(moduleId.lowercase()).executeAsList().map { it.owned_id }

    fun lead(moduleId: String): String? = party(moduleId).firstOrNull()

    fun setBuddy(id: String?) = store.transaction {
        q.clearPlaces(KIND_BUDDY, "")
        if (id != null) {
            q.removePlace(id)
            q.insertPlace(id, KIND_BUDDY, "", 0)
        }
        compact()
    }

    fun buddy(): String? = q.buddy().executeAsOneOrNull()

    fun setGuardians(ids: List<String>) = store.transaction {
        require(ids.toSet().size == ids.size) { "a Pokémon can be a Guardian only once" }
        q.clearPlaces(KIND_GUARDIAN, "")
        ids.forEachIndexed { i, id ->
            q.removePlace(id)
            q.insertPlace(id, KIND_GUARDIAN, "", (i + 1).toLong())
        }
        compact()
    }

    fun guardians(): List<String> = q.guardians().executeAsList().map { it.owned_id }

    fun places(): Map<String, PokemonPlace> = q.allPlaces().executeAsList().associate { it.owned_id to place(it.kind, it.ref) }

    private fun compact() {
        var last: Pair<String, String>? = null
        var next = 0L
        for (row in q.allPlaces().executeAsList()) {
            if (row.kind == KIND_BUDDY) continue
            val group = row.kind to row.ref
            if (group != last) { last = group; next = 0 }
            next++
            if (row.slot != next) q.moveSlot(next, row.owned_id)
        }
    }

    private fun place(kind: String, ref: String): PokemonPlace = when (kind) {
        KIND_BUDDY -> PokemonPlace.Buddy
        KIND_GUARDIAN -> PokemonPlace.Guardian
        else -> PokemonPlace.Party(ref.uppercase())
    }

    fun addDex(kind: DexKind, ref: Long) = store.transaction { q.addDex(kind.key, ref) }

    fun dex(kind: DexKind): List<Long> = q.dexOf(kind.key).executeAsList()

    fun setProgress(progress: PlayerProgress) = store.transaction { q.setProgress(progress.eggMeter, progress.trainerPoints) }

    fun progress(): PlayerProgress? = q.progress().executeAsOneOrNull()?.let { PlayerProgress(it.egg_meter, it.trainer_points) }

    internal fun Owned_pokemon.toRecord() = OwnedPokemonRecord(
        id = id, speciesId = species_id.toInt(), formIdentifier = form_identifier, nickname = nickname, nature = nature,
        ability = ability, gender = gender,
        ivs = Spread(iv_hp.toInt(), iv_attack.toInt(), iv_defense.toInt(), iv_sp_attack.toInt(), iv_sp_defense.toInt(), iv_speed.toInt()),
        evs = Spread(ev_hp.toInt(), ev_attack.toInt(), ev_defense.toInt(), ev_sp_attack.toInt(), ev_sp_defense.toInt(), ev_speed.toInt()),
        level = level.toInt(), candyProgress = candy_progress.toInt(), totalCandies = total_candies.toInt(), friendship = friendship.toInt(),
        isShiny = is_shiny == 1L, heldItem = held_item, teraType = tera_type, cosmeticForm = cosmetic_form,
        personality = personality.toInt(), caughtAtMillis = caught_at_millis, isFavorite = is_favorite == 1L,
        originHow = origin_how, originRegion = origin_region, extraJson = extra_json,
        position = position.toInt(), originJson = origin_json,
    )

    companion object {
        internal const val KIND_BUDDY = "buddy"
        internal const val KIND_PARTY = "party"
        internal const val KIND_GUARDIAN = "guardian"
    }
}
