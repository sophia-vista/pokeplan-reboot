package app.pokeplan.world

/**
 * The story foundation (docs/world.md, section 15): chapters that each introduce a mechanic or a meaning, and the Guardian
 * at the end. A chapter opens when its condition is met and is never lost: missing a day does not undo story progress. Only
 * the first chapters are written; the rest are data to fill when their mechanics exist.
 */
data class Chapter(val number: Int, val title: String, val summary: String, val unlocks: String, val needsRank: ExplorerRank, val needsDungeons: Int)

object Story {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "A New Explorer", "You form an exploration team and begin to explore.", "Basic expeditions", ExplorerRank.NORMAL, 0),
        Chapter(2, "Requests and Rescues", "Pokémon in need of help come to your door.", "The mission board", ExplorerRank.BRONZE, 1),
        Chapter(3, "Into the Unknown", "Deeper dungeons and a second team.", "More teams", ExplorerRank.SILVER, 3),
        Chapter(4, "Strange Discoveries", "The dungeons behave strangely.", "Research and treasure", ExplorerRank.GOLD, 4),
        Chapter(5, "Ancient Secrets", "The source of the disturbances.", "Special expeditions", ExplorerRank.PLATINUM, 5),
        Chapter(6, "The Great Disturbance", "You face the Guardian.", "Story completion", ExplorerRank.DIAMOND, 7),
    )

    /** The Guardian candidates (docs/world.md): the first is the strongest PMD reference. Its mastery bonus is illustrative. */
    const val GUARDIAN = "Primal Dialga"

    /** The species the Guardian gives (Dialga; there is no Primal form in the Dex). */
    const val GUARDIAN_SPECIES = 483
    const val GUARDIAN_BONUS = "Temporal Mastery: every expedition climbs 10% faster"

    /** How many chapters are open for [data]: each needs its rank and enough different dungeons cleared (repeating one counts once, so a cheap dungeon cannot be farmed for chapters). */
    fun opened(data: WorldData): Int = chapters.count { data.rank >= it.needsRank && data.clears.count { c -> c.value > 0 } >= it.needsDungeons }

    /** The story is complete when the Guardian has been faced. */
    fun complete(data: WorldData): Boolean = data.guardianDone

    /** The next chapter that is not open yet, or null at the end. */
    fun next(data: WorldData): Chapter? = chapters.getOrNull(opened(data))
}
