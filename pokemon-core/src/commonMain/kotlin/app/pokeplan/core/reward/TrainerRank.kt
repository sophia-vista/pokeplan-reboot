package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region

enum class Feature(val step: Int, val label: String) {
    TASKS(0, "Tasks and habits"), FOCUS(0, "Focus and eggs"), BUDDY(0, "Your buddy"), PARTY(0, "Party and PC"),
    POKEDEX(0, "Pokédex"), RARE_CANDY(0, "Exp. Candy"),
    EVOLUTION(1, "Evolution with Super Candy"), BAG(1, "The Bag"), GOALS(1, "Goals"), REGION_PICKER(1, "Choosing a hatching region"),
    MINI_QUESTS(1, "Daily mini-quests"),
    HELD_ITEMS(2, "Held items"), PERKS(2, "Party perks"), GROWTH_SCREEN(2, "Growth trends"), COINS(2, "Poké"),
    TRAINING_ITEMS(3, "Nature Mints, Bottle Caps, Ability Capsules and Tera Shards"), STATS_DETAIL(3, "Stats and the IV judge"),
    GUARDIANS(4, "Guardians"), LEGENDARY_QUESTS(4, "Legendary quests"),
    ;

    companion object {
        fun at(rank: Int): List<Feature> = entries.filter { it.step == rank }
    }
}

object TrainerRank {
    val names: List<String> = listOf(
        "Rookie", "Apprentice", "Trainer", "Ace", "Veteran", "Expert", "Elite", "Champion", "Master", "Legend",
    )

    val thresholds: List<Int> = listOf(0, 50, 150, 325, 650, 1_150, 1_900, 3_000, 5_000, 9_000)

    fun pointsForAchievement(tier: Int): Int = tier * 2

    fun pointsForEffort(minutes: Int): Int = minutes / 5

    fun rankFor(points: Int): Int = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)

    fun nameFor(points: Int): String = names[rankFor(points)]

    fun pointsToNext(points: Int): Int? = thresholds.getOrNull(rankFor(points) + 1)?.let { it - points }

    fun unlockedRegions(points: Int): List<Region> = Region.entries.filter { isUnlocked(it, points) }

    fun isUnlocked(region: Region, points: Int): Boolean = region.released && rankToUnlock(region) <= rankFor(points)

    fun rankToUnlock(region: Region): Int = if (region.isModule) 0 else Region.games.indexOf(region) + 1

    fun isVisible(feature: Feature, points: Int): Boolean = feature.step <= rankFor(points)
}
