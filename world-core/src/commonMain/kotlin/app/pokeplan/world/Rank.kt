package app.pokeplan.world

/**
 * The exploration team's rank, the module's own progression (docs/pokelife/MODULES/WORLD.md), separate from any Pokémon's level.
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

    /**
     * The Mystery Dungeon rank badge for this rank, an item id of the PMD2 item art (assets/pmd-items), in the order the sheet has them
     * (the winged emblems 197 to 201 and 211): Normal, Bronze, Silver, Gold, Platinum, Diamond. The pictures are the games' own.
     */
    val badge: String get() = "pmd2-" + listOf(197, 198, 199, 200, 201, 211)[ordinal.coerceAtMost(5)]

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

/**
 * Where World uses the Mystery Dungeon item art (assets/pmd-items, drawn by `ItemIcon`): the Poké coin and the treasure boxes by tier
 * (a red Wonder Box for a plain one, the blue for silver, the striped gold one for gold). Item ids of the PMD2 sheet, in one place.
 */
object PmdArt {
    const val POKE_COIN = "pmd2-072"

    fun treasureBox(tier: Int): String = when (tier) { 1 -> "pmd2-065"; 2 -> "pmd2-067"; else -> "pmd2-066" }
}
