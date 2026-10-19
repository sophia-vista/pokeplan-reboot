package app.pokeplan.shared.model

/**
 * Static species data. [rarity] is only set for species that can hatch from an egg;
 * evolved forms have none. [evolveLevel] and [evolvesTo] are set together or not at all.
 */
data class Species(
    val name: String,
    val dexNum: Int,
    val rarity: Rarity?,
    val types: List<PokemonType>,
    val evolveLevel: Int? = null,
    val evolvesTo: String? = null,
)
