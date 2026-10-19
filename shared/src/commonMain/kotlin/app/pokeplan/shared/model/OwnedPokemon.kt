package app.pokeplan.shared.model

import app.pokeplan.shared.data.Pokedex
import kotlin.random.Random

/** A Pokémon in the player's collection. Changes return a new instance. */
data class OwnedPokemon(
    val id: String,
    val species: Species,
    val nickname: String,
    val nature: String,
    val level: Int = 1,
    val fedCandy: Int = 0,
    val inParty: Boolean,
) {
    val canFeed: Boolean get() = level < MAX_LEVEL

    val canEvolve: Boolean
        get() = species.evolvesTo != null && species.evolveLevel != null && level >= species.evolveLevel

    /** Every [CANDIES_PER_LEVEL] Rare Candies fed raise the level by one, up to [MAX_LEVEL]. */
    fun feedRareCandy(): OwnedPokemon {
        check(canFeed) { "$nickname is already level $MAX_LEVEL" }
        val fed = fedCandy + 1
        return copy(fedCandy = fed, level = if (fed % CANDIES_PER_LEVEL == 0) level + 1 else level)
    }

    /** Evolves with a Super Candy. A nickname left at the species name follows the evolution. */
    fun evolve(): OwnedPokemon {
        check(canEvolve) { "$nickname cannot evolve yet" }
        val next = checkNotNull(Pokedex.species(species.evolvesTo!!)) { "unknown evolution ${species.evolvesTo}" }
        return copy(species = next, nickname = if (nickname == species.name) next.name else nickname)
    }

    companion object {
        const val MAX_LEVEL = 100
        const val CANDIES_PER_LEVEL = 10
        const val PARTY_SIZE = 6

        /** A freshly hatched Pokémon joins the party if there is room, otherwise it goes to the PC. */
        fun hatched(id: String, species: Species, partySize: Int, rng: Random = Random) = OwnedPokemon(
            id = id,
            species = species,
            nickname = species.name,
            nature = Nature.random(rng),
            inParty = partySize < PARTY_SIZE,
        )
    }
}
