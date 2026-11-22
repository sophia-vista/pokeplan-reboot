package app.pokeplan.world

enum class ExplorerRank(val label: String, val points: Int, val teamSlots: Int) {
    NORMAL("Normal", 0, 2),
    BRONZE("Bronze", 300, 2),
    SILVER("Silver", 1_500, 3),
    GOLD("Gold", 6_000, 3),
    PLATINUM("Platinum", 20_000, 4),
    DIAMOND("Diamond", 60_000, 4),
    ;

    val badge: String get() = "pmd2-" + listOf(197, 198, 199, 200, 201, 211)[ordinal.coerceAtMost(5)]

    companion object {
        fun at(points: Int): ExplorerRank = entries.last { points >= it.points }
    }

    fun next(): ExplorerRank? = entries.getOrNull(ordinal + 1)

    fun nextRankOpens(): List<String> {
        val next = next() ?: return emptyList()
        return buildList {
            if (next.teamSlots > teamSlots) add("${next.teamSlots} expedition teams (now $teamSlots)")
            val places = Dungeons.all.filter { it.needs == next }
            if (places.isNotEmpty()) add("the dungeons ${places.joinToString(", ") { it.name }}")
        }
    }
}

object PmdArt {
    const val POKE_COIN = "pmd2-072"

    fun treasureBox(tier: Int): String = when (tier) { 1 -> "pmd2-065"; 2 -> "pmd2-067"; else -> "pmd2-066" }
}
