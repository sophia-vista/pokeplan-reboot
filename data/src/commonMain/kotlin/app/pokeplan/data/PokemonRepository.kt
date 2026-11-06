package app.pokeplan.data


/** Owned Pokémon, parties, the global buddy, Guardians, Dex history and progress: the shared Pokémon side. */
class PokemonRepository internal constructor(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun add(p: OwnedPokemonRecord) = store.transaction {
        q.insertPokemon(
            p.id, p.speciesId.toLong(), p.formIdentifier, p.nickname, p.nature, p.ability, p.gender,
            p.ivs.hp.toLong(), p.ivs.attack.toLong(), p.ivs.defense.toLong(), p.ivs.spAttack.toLong(), p.ivs.spDefense.toLong(), p.ivs.speed.toLong(),
            p.evs.hp.toLong(), p.evs.attack.toLong(), p.evs.defense.toLong(), p.evs.spAttack.toLong(), p.evs.spDefense.toLong(), p.evs.speed.toLong(),
            p.level.toLong(), p.candyProgress.toLong(), p.totalCandies.toLong(), p.friendship.toLong(), if (p.isShiny) 1 else 0,
            p.heldItem, p.teraType, p.cosmeticForm, p.personality.toLong(), p.caughtAtMillis,
            if (p.isFavorite) 1 else 0, p.originHow, p.originRegion, p.extraJson,
        )
    }

    fun get(id: String): OwnedPokemonRecord? = q.pokemonById(id).executeAsOneOrNull()?.toRecord()

    fun all(): List<OwnedPokemonRecord> = q.allPokemon().executeAsList().map { it.toRecord() }

    fun count(): Long = q.pokemonCount().executeAsOne()

    /** Releases a Pokémon. Its party slots, buddy and Guardian entries go with it; its Dex history stays. */
    fun release(id: String) = store.transaction { q.releasePokemon(id) }

    // Parties.

    /** Replaces [moduleId]'s party with [ownedIds] (the lead first). A party never fills itself and never holds one Pokémon twice. */
    fun setParty(moduleId: String, ownedIds: List<String>) {
        require(moduleId in ModuleIds.WITH_PARTY) { "$moduleId has no party" }
        require(ownedIds.size <= ModuleIds.PARTY_CAPACITY) { "a party holds at most ${ModuleIds.PARTY_CAPACITY} (the lead and five members)" }
        require(ownedIds.toSet().size == ownedIds.size) { "a Pokémon can be in a party only once" }
        store.transaction {
            q.clearParty(moduleId)
            ownedIds.forEachIndexed { i, id -> q.insertPartySlot(moduleId, (i + 1).toLong(), id) }
        }
    }

    /** The party's Pokémon ids in slot order; the first is the lead. */
    fun party(moduleId: String): List<String> = q.partyOf(moduleId).executeAsList().map { it.owned_id }

    fun lead(moduleId: String): String? = party(moduleId).firstOrNull()

    // Buddy and Guardians.

    fun setBuddy(id: String?) = store.transaction { if (id == null) q.clearBuddy() else q.setBuddy(id) }

    fun buddy(): String? = q.buddy().executeAsOneOrNull()

    fun setGuardians(ids: List<String>) = store.transaction {
        q.clearGuardians()
        ids.forEachIndexed { i, id -> q.insertGuardian((i + 1).toLong(), id) }
    }

    fun guardians(): List<String> = q.guardians().executeAsList().map { it.owned_id }

    // Dex history and progress.

    fun addDex(kind: DexKind, ref: Long) = store.transaction { q.addDex(kind.key, ref) }

    fun dex(kind: DexKind): List<Long> = q.dexOf(kind.key).executeAsList()

    fun setProgress(progress: PlayerProgress) = store.transaction { q.setProgress(progress.eggMeter, progress.trainerPoints) }

    fun progress(): PlayerProgress? = q.progress().executeAsOneOrNull()?.let { PlayerProgress(it.egg_meter, it.trainer_points) }

    private fun Owned_pokemon.toRecord() = OwnedPokemonRecord(
        id = id, speciesId = species_id.toInt(), formIdentifier = form_identifier, nickname = nickname, nature = nature,
        ability = ability, gender = gender,
        ivs = Spread(iv_hp.toInt(), iv_attack.toInt(), iv_defense.toInt(), iv_sp_attack.toInt(), iv_sp_defense.toInt(), iv_speed.toInt()),
        evs = Spread(ev_hp.toInt(), ev_attack.toInt(), ev_defense.toInt(), ev_sp_attack.toInt(), ev_sp_defense.toInt(), ev_speed.toInt()),
        level = level.toInt(), candyProgress = candy_progress.toInt(), totalCandies = total_candies.toInt(), friendship = friendship.toInt(),
        isShiny = is_shiny == 1L, heldItem = held_item, teraType = tera_type, cosmeticForm = cosmetic_form,
        personality = personality.toInt(), caughtAtMillis = caught_at_millis, isFavorite = is_favorite == 1L,
        originHow = origin_how, originRegion = origin_region, extraJson = extra_json,
    )
}
