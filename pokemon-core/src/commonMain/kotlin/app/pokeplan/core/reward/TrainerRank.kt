package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region

/** A feature that appears as the trainer rank rises, so day one stays simple. See "ADHD-friendly design" in docs/design.md. */
enum class Feature(val step: Int) {
    TASKS(0), HABITS(0), FOCUS(0), BUDDY(0), RARE_CANDY(0), EGGS(0), WEEKLY_REVIEW(0),
    SUPER_CANDY(1), EVOLUTION(1), POKEDEX(1), GOALS(1), MINI_QUESTS(1), REGION_PICKER(1),
    ITEMS(2), BAG(2), SHOP(2), COINS(2), GROWTH_SCREEN(2), LURES(2), SPOTLIGHT_DAYS(2),
    TRAINING_PLANS(3), STATS_DETAIL(3), IV_JUDGE(3), TERA(3), BOND_STYLES(3), MOOD_CHECK_IN(3),
    LEGENDARY_QUESTS(4),
    CONTESTS(5), PERFECT_DEX(5), HALL_OF_FAME(5),
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

    /** Points needed to reach each rank, aimed at a new region every 3 to 4 weeks for a typical player. */
    val thresholds: List<Int> = listOf(0, 625, 1_375, 2_250, 3_250, 4_375, 5_625, 7_125, 8_750, 10_625)

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
