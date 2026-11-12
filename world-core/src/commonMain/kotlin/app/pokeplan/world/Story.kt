package app.pokeplan.world

/**
 * The story foundation (docs/pokelife/MODULES/WORLD.md): chapters that each introduce a mechanic or a meaning, and the Guardian
 * at the end. A chapter opens when its condition is met and is never lost: missing a day does not undo story progress. The
 * Rescue Team and Explorers chapters here are title cards (their scenes are not written); the Mew opening and the Kanto, Johto, Hoenn and
 * Sinnoh episodes are authored in `story/`.
 */
data class Chapter(val number: Int, val title: String, val summary: String, val unlocks: String, val needsRank: ExplorerRank, val needsDungeons: Int)

object Story {
    /** The Guardian candidates (docs/pokelife/MODULES/WORLD.md): the first is the strongest PMD reference. Its mastery bonus is illustrative. */
    const val GUARDIAN = "Primal Dialga"

    /** The species the Guardian gives (Dialga) and its form: Primal Dialga, a form of its own in the Dex (not the Dialga of Sinnoh, docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md). */
    const val GUARDIAN_SPECIES = 483
    const val GUARDIAN_FORM = "dialga-primal"
    const val GUARDIAN_BONUS = "Temporal Mastery: every expedition climbs 10% faster"

    /** The chapters of [layer]'s story. */
    fun chaptersOf(layer: WorldLayer): List<Chapter> = when (layer) {
        WorldLayer.RESCUE -> RescueStory.chapters
        WorldLayer.EXPLORERS -> ExplorersStory.chapters
        WorldLayer.KANTO -> KantoStory.chapters
        WorldLayer.JOHTO -> JohtoStory.chapters
        WorldLayer.HOENN -> HoennStory.chapters
        WorldLayer.SINNOH -> SinnohStory.chapters
    }

    /** What the Guardian of [layer] gives once its story is complete. */
    fun guardianBonus(layer: WorldLayer): String = when (layer) {
        WorldLayer.RESCUE -> RescueStory.GUARDIAN_BONUS
        WorldLayer.EXPLORERS -> GUARDIAN_BONUS
        WorldLayer.KANTO -> KantoStory.GUARDIAN_BONUS
        WorldLayer.JOHTO -> JohtoStory.GUARDIAN_BONUS
        WorldLayer.HOENN -> HoennStory.GUARDIAN_BONUS
        WorldLayer.SINNOH -> SinnohStory.GUARDIAN_BONUS
    }

    /** How many different dungeons of [layer] have been cleared (repeating one counts once, so a cheap dungeon cannot be farmed for chapters). */
    fun dungeonsCleared(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Int = Dungeons.of(layer).count { (data.clears[it.id] ?: 0) > 0 }

    /** How many chapters of [layer]'s story are open for [data]: each needs its rank and enough different dungeons of the layer cleared. */
    fun opened(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Int =
        chaptersOf(layer).count { data.rank >= it.needsRank && dungeonsCleared(data, layer) >= it.needsDungeons }

    /** The layer's story is complete when its Guardian has been faced. */
    fun complete(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Boolean = data.layerDone(layer)

    /** The next chapter of [layer] that is not open yet, or null at the end. */
    fun next(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Chapter? = chaptersOf(layer).getOrNull(opened(data, layer))
}
