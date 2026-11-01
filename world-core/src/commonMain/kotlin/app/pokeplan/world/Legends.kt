package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType

/**
 * A legendary quest of a layer (docs/ecosystem.md, "World regions"): clear [dungeonId] [clears] times (and, for the end of the story,
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
    val type: PokemonType? = null,
)

object Legends {
    /** How much faster a floor is in a Kanto dungeon that favours a granted bird's type: percent, for each bird. *(tune)* */
    const val BIRD_FASTER_PERCENT = 10

    /** Mewtwo's Psychic Mastery: more chance of a find in Kanto, in permille of a floor. Mew's: more chance of a treasure box, in percent. *(tune)* */
    const val MEWTWO_FIND_PERMILLE = 10
    const val MEW_TREASURE_PERCENT = 3

    val all: List<LegendQuest> = listOf(
        LegendQuest("kanto-zapdos", 145, "Zapdos", WorldLayer.KANTO, "power-plant", 3, false, "Storms gather where the Power Plant hums. Clear it three times.", PokemonType.ELECTRIC),
        LegendQuest("kanto-articuno", 144, "Articuno", WorldLayer.KANTO, "seafoam-islands", 3, false, "Something cold watches from the deepest Seafoam cave. Clear it three times.", PokemonType.ICE),
        LegendQuest("kanto-moltres", 146, "Moltres", WorldLayer.KANTO, "mt-ember", 3, false, "A fire bird nests at the top of Mt. Ember. Clear it three times.", PokemonType.FIRE),
        LegendQuest("kanto-mewtwo", 150, "Mewtwo", WorldLayer.KANTO, "cerulean-cave", 1, true, "The Guardian of Kanto waits at the end of Cerulean Cave."),
        LegendQuest("kanto-mew", 151, "Mew", WorldLayer.KANTO, "cerulean-cave", 3, true, "Where Mewtwo came from. Clear Cerulean Cave three times once its Guardian has been faced."),
    )

    fun of(layer: WorldLayer): List<LegendQuest> = all.filter { it.layer == layer }

    fun byId(id: String): LegendQuest? = all.firstOrNull { it.id == id }

    /** How much of [quest] is done: clears of its dungeon, up to what it needs. */
    fun progress(data: WorldData, quest: LegendQuest): Int = minOf(data.clears[quest.dungeonId] ?: 0, quest.clears)

    fun isMet(data: WorldData, quest: LegendQuest): Boolean =
        progress(data, quest) >= quest.clears && (!quest.afterGuardian || data.layerDone(quest.layer))

    /** The quests whose conditions are met and whose Pokémon has not been given yet. */
    fun ready(data: WorldData): List<LegendQuest> = all.filter { it.id !in data.legends && isMet(data, it) }
}
