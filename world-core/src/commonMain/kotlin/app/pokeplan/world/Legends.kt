package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType

/**
 * A legendary quest of a layer (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, "World regions"): clear [dungeonId] [clears] times (and, for the end of the story,
 * once its layer's Guardian has been faced) and [speciesId] joins the collection, as a Guardian of that layer. Not eggs and not finds:
 * the Pokémon is given once. [type] is the type whose explorers it makes faster in the layer's dungeons that favour it, if any.
 */
data class LegendQuest(
    val id: String,
    val speciesId: Int,
    val name: String,
    val layer: WorldLayer,
    val dungeonId: String,
    val clears: Int,
    val afterGuardian: Boolean,
    val blurb: String,
    /** The types whose dungeons of the layer (those that favour one) its Pokémon makes [Legends.FASTER_PERCENT] faster, if any. */
    val types: Set<PokemonType> = emptySet(),
    /** More chance of a find on a floor of the layer, in permille, and of a treasure box, in percent. */
    val findPermille: Int = 0,
    val treasurePercent: Int = 0,
)

/** What a legendary of a layer gives, in words, from the same numbers the simulation uses: where it applies and how much. */
fun LegendQuest.bonusText(): String {
    val parts = buildList {
        if (types.isNotEmpty()) add("${types.joinToString(" and ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }}-favouring dungeons climb ${Legends.FASTER_PERCENT}% faster")
        if (findPermille > 0) add("${findPermille / 10.0}% more chance of a wild Pokémon on a floor")
        if (treasurePercent > 0) add("${treasurePercent}% more chance of a treasure box")
    }
    return if (parts.isEmpty()) "No bonus." else parts.joinToString("; ") + " in ${layer.label}."
}

object Legends {
    /** How much faster a floor is in a dungeon of the layer that favours a given legendary's type: percent, for each one. *(tune)* */
    const val FASTER_PERCENT = 10

    val all: List<LegendQuest> = listOf(
        LegendQuest("kanto-zapdos", 145, "Zapdos", WorldLayer.KANTO, "power-plant", 3, false, "Storms gather where the Power Plant hums. Clear it three times.", setOf(PokemonType.ELECTRIC)),
        LegendQuest("kanto-articuno", 144, "Articuno", WorldLayer.KANTO, "seafoam-islands", 3, false, "Something cold watches from the deepest Seafoam cave. Clear it three times.", setOf(PokemonType.ICE)),
        LegendQuest("kanto-moltres", 146, "Moltres", WorldLayer.KANTO, "mt-ember", 3, false, "A fire bird nests at the top of Mt. Ember. Clear it three times.", setOf(PokemonType.FIRE)),
        LegendQuest("kanto-mewtwo", 150, "Mewtwo", WorldLayer.KANTO, "cerulean-cave", 1, true, "Kanto's Guardian waits on the last floor of Cerulean Cave.", findPermille = 10),
        LegendQuest("kanto-mew", 151, "Mew", WorldLayer.KANTO, "cerulean-cave", 3, true, "Where Mewtwo came from. Clear Cerulean Cave three times once its Guardian has been faced.", treasurePercent = 3),
        LegendQuest("johto-lugia", 249, "Lugia", WorldLayer.JOHTO, "whirl-islands", 3, false, "The guardian of the sea sleeps in the deepest Whirl Islands cave. Clear it three times.", setOf(PokemonType.WATER, PokemonType.FLYING)),
        LegendQuest("johto-ho-oh", 250, "Ho-Oh", WorldLayer.JOHTO, "tin-tower", 1, true, "Johto's Guardian waits on the top floor of the Tin Tower.", treasurePercent = 3, findPermille = 5),
        LegendQuest("hoenn-groudon", 383, "Groudon", WorldLayer.HOENN, "mt-chimney", 3, false, "The land's ancient Pokémon stirs under Mt. Chimney. Clear it three times.", setOf(PokemonType.GROUND, PokemonType.FIRE)),
        LegendQuest("hoenn-kyogre", 382, "Kyogre", WorldLayer.HOENN, "seafloor-cavern", 3, false, "The sea's ancient Pokémon sleeps beneath the Seafloor Cavern. Clear it three times.", setOf(PokemonType.WATER)),
        LegendQuest("sinnoh-dialga", 483, "Dialga", WorldLayer.SINNOH, "mt-coronet", 3, false, "Time itself is thin at the summit of Mt. Coronet. Clear it three times.", setOf(PokemonType.STEEL)),
        LegendQuest("sinnoh-palkia", 484, "Palkia", WorldLayer.SINNOH, "spear-pillar", 3, false, "Space folds around the Spear Pillar. Clear it three times.", setOf(PokemonType.WATER)),
        LegendQuest("sinnoh-giratina", 487, "Giratina", WorldLayer.SINNOH, "distortion-world", 1, true, "Sinnoh's Guardian waits in the Distortion World.", setOf(PokemonType.GHOST), treasurePercent = 3),
        LegendQuest("hoenn-rayquaza", 384, "Rayquaza", WorldLayer.HOENN, "sky-pillar", 1, true, "Hoenn's Guardian waits at the top of the Sky Pillar.", setOf(PokemonType.FLYING, PokemonType.DRAGON), findPermille = 5),
    )

    /** The species World gives through its own quests: Plan has no quest for them (a decision of 1 November 2026: Plan keeps the rest until a region or module gives them). */
    val coveredSpecies: Set<Int> by lazy { all.map { it.speciesId }.toSet() }

    fun of(layer: WorldLayer): List<LegendQuest> = all.filter { it.layer == layer }

    fun byId(id: String): LegendQuest? = all.firstOrNull { it.id == id }

    /** How much of [quest] is done: clears of its dungeon, up to what it needs. */
    fun progress(data: WorldData, quest: LegendQuest): Int = minOf(data.clears[quest.dungeonId] ?: 0, quest.clears)

    fun isMet(data: WorldData, quest: LegendQuest): Boolean =
        progress(data, quest) >= quest.clears && (!quest.afterGuardian || data.layerDone(quest.layer))

    /** The quests whose conditions are met and whose Pokémon has not been given yet. */
    fun ready(data: WorldData): List<LegendQuest> = all.filter { it.id !in data.legends && isMet(data, it) }
}
