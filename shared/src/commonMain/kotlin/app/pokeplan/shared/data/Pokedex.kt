package app.pokeplan.shared.data

import app.pokeplan.shared.model.Rarity
import app.pokeplan.shared.model.Species

object Pokedex {
    val all: List<Species> = speciesData

    private val byName: Map<String, Species> = all.associateBy { it.name }
    private val byDexNum: Map<Int, Species> = all.associateBy { it.dexNum }
    private val byRarity: Map<Rarity, List<Species>> = all.filter { it.rarity != null }.groupBy { it.rarity!! }

    fun species(name: String): Species? = byName[name]

    fun species(dexNum: Int): Species? = byDexNum[dexNum]

    /** Species that can hatch from an egg of the given rarity. */
    fun hatchable(rarity: Rarity): List<Species> = byRarity[rarity].orEmpty()

    fun entry(dexNum: Int): String = pokedexEntries[dexNum - 1]
}
