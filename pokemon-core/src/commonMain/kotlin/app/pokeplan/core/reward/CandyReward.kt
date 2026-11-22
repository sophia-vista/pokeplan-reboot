package app.pokeplan.core.reward

import kotlin.random.Random

enum class CandyType { RARE, SUPER }

data class CandyReward(val type: CandyType, val amount: Int)

class CandyRates(val superAbove: Int, val rare: IntRange, val superCandy: IntRange) {
    init {
        require(!rare.isEmpty() && !superCandy.isEmpty()) { "candy ranges must not be empty" }
    }

    fun roll(rng: Random, superBonusPoints: Int = 0): List<CandyReward> {
        val rareCandies = CandyReward(CandyType.RARE, rng.nextInt(rare.first, rare.last + 1))
        if (rng.nextInt(1, 101) <= superAbove - superBonusPoints.coerceAtLeast(0)) return listOf(rareCandies)
        return listOf(rareCandies, CandyReward(CandyType.SUPER, rng.nextInt(superCandy.first, superCandy.last + 1)))
    }
}

object CandyRewards {
    const val MIN_PRIORITY = 1
    const val MAX_PRIORITY = 5

    private val tables = listOf(
        CandyRates(superAbove = 90, rare = 6..10, superCandy = 1..2),
        CandyRates(superAbove = 85, rare = 8..20, superCandy = 2..3),
        CandyRates(superAbove = 80, rare = 10..26, superCandy = 3..4),
        CandyRates(superAbove = 70, rare = 12..34, superCandy = 4..5),
        CandyRates(superAbove = 60, rare = 14..40, superCandy = 6..7),
    )

    fun rates(priority: Int): CandyRates {
        require(priority in MIN_PRIORITY..MAX_PRIORITY) { "priority must be $MIN_PRIORITY..$MAX_PRIORITY" }
        return tables[priority - 1]
    }

    fun reward(priority: Int, rng: Random = Random): List<CandyReward> = rates(priority).roll(rng)
}
