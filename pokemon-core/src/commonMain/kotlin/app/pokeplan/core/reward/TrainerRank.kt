package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region

/**
 * A feature that appears as the trainer rank rises, so day one stays simple. See "ADHD-friendly design" in docs/design.md.
 * Only features that exist are listed; the early ranks come within days, so nothing waits long.
 */
enum class Feature(val step: Int, val label: String) {
    TASKS(0, "Tasks and habits"), FOCUS(0, "Focus and eggs"), BUDDY(0, "Your buddy"), PARTY(0, "Party and PC"),
    POKEDEX(0, "Pokédex"), RARE_CANDY(0, "Rare Candy"), WEEKLY_REVIEW(0, "Weekly review"),
    EVOLUTION(1, "Evolution with Super Candy"), BAG(1, "The Bag"), GOALS(1, "Goals"), REGION_PICKER(1, "Choosing a hatching region"),
    HELD_ITEMS(2, "Held items"), PERKS(2, "Party perks"), GROWTH_SCREEN(2, "Growth trends"), COINS(2, "Coins"),
    TRAINING_ITEMS(3, "Nature Mints, Bottle Caps, Ability Capsules and Tera Shards"), STATS_DETAIL(3, "Stats and the IV judge"),
    GUARDIANS(4, "Guardians"), LEGENDARY_QUESTS(4, "Legendary quests"),
    ;

    companion object {
        /** What opens exactly at [rank]. */
        fun at(rank: Int): List<Feature> = entries.filter { it.step == rank }
    }
}

/**
 * The trainer rank, shared by every app: it is earned from rewards in any of them. Ranks unlock regions one by one and
 * reveal features. Names and thresholds are *(tune)*.
 */
object TrainerRank {
    /** Rank names, from the first. */
    val names: List<String> = listOf(
        "Rookie", "Apprentice", "Trainer", "Ace", "Veteran", "Expert", "Elite", "Champion", "Master", "Legend",
    )

    /**
     * Points needed to reach each rank: quick at first (a typical player reaches Apprentice on day 2 and Ace in under two
     * weeks, so the reveal never makes anyone wait), then slower, so the later regions keep coming for months. *(tune)*
     */
    val thresholds: List<Int> = listOf(0, 50, 150, 325, 650, 1_150, 1_900, 3_000, 5_000, 9_000)

    /** Points for finished work of [tier] (1 to 5). */
    fun pointsForAchievement(tier: Int): Int = tier * 2

    /** Points for [minutes] of effort: one per 5 minutes. */
    fun pointsForEffort(minutes: Int): Int = minutes / 5

    /** The rank index (0 is the first rank) for [points]. */
    fun rankFor(points: Int): Int = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)

    fun nameFor(points: Int): String = names[rankFor(points)]

    /** Points still needed for the next rank, or null at the top. */
    fun pointsToNext(points: Int): Int? = thresholds.getOrNull(rankFor(points) + 1)?.let { it - points }

    /** The Home Region is always open; the others open in order, one per rank. */
    fun unlockedRegions(points: Int): List<Region> = Region.entries.take(rankFor(points) + 1)

    fun isUnlocked(region: Region, points: Int): Boolean = region.ordinal <= rankFor(points)

    /** Rank needed to open [region]. */
    fun rankToUnlock(region: Region): Int = region.ordinal

    fun isVisible(feature: Feature, points: Int): Boolean = feature.step <= rankFor(points)
}
