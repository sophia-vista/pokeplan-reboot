package app.pokeplan.world

/**
 * The exploration team's rank, the module's own progression (docs/world.md, section 9), separate from any Pokémon's level.
 * The names follow Explorers of Sky's Explorer Rank. [points] is the Exploration XP that reaches it *(tune)*; rank opens
 * dungeons ([DungeonDef.needs]) and expedition teams ([teamSlots]).
 */
enum class ExplorerRank(val label: String, val points: Int, val teamSlots: Int) {
    NORMAL("Normal", 0, 2),
    BRONZE("Bronze", 300, 2),
    SILVER("Silver", 1_500, 3),
    GOLD("Gold", 6_000, 3),
    PLATINUM("Platinum", 20_000, 4),
    DIAMOND("Diamond", 60_000, 4),
    ;

    companion object {
        /** The highest rank [points] reaches. */
        fun at(points: Int): ExplorerRank = entries.last { points >= it.points }
    }

    /** The next rank, or null at the top. */
    fun next(): ExplorerRank? = entries.getOrNull(ordinal + 1)

    /** What reaching the next rank opens, as phrases to follow "opens": more teams, and the dungeons (of every layer) that ask for it. Empty at the top rank. */
    fun nextRankOpens(): List<String> {
        val next = next() ?: return emptyList()
        return buildList {
            if (next.teamSlots > teamSlots) add("${next.teamSlots} expedition teams (now $teamSlots)")
            val places = Dungeons.all.filter { it.needs == next }
            if (places.isNotEmpty()) add("the dungeons ${places.joinToString(", ") { it.name }}")
        }
    }
}
