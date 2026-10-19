package app.pokeplan.shared.model

import kotlin.random.Random

enum class CandyType { RARE, SUPER }

data class CandyReward(val type: CandyType, val amount: Int)

/**
 * Candies earned for finishing a task. Higher [priority] (1..5) pays more, as in the original app.
 * A roll above [superAbove] (out of 100) pays Super Candies, otherwise Rare Candies.
 */
class CandyRates(val superAbove: Int, val rare: IntRange, val superCandy: IntRange) {
    init {
        require(!rare.isEmpty() && !superCandy.isEmpty()) { "candy ranges must not be empty" }
    }

    fun roll(rng: Random): CandyReward =
        if (rng.nextInt(1, 101) > superAbove) CandyReward(CandyType.SUPER, rng.nextInt(superCandy.first, superCandy.last + 1))
        else CandyReward(CandyType.RARE, rng.nextInt(rare.first, rare.last + 1))
}

object CandyRewards {
    const val MIN_PRIORITY = 1
    const val MAX_PRIORITY = 5

    private val tables = listOf(
        CandyRates(superAbove = 90, rare = 3..5, superCandy = 1..2),
        CandyRates(superAbove = 85, rare = 4..10, superCandy = 2..3),
        CandyRates(superAbove = 80, rare = 5..13, superCandy = 3..4),
        CandyRates(superAbove = 70, rare = 6..17, superCandy = 4..5),
        // The original had this super range as 7..6 (min above max), which always paid 7.
        CandyRates(superAbove = 60, rare = 7..20, superCandy = 6..7),
    )

    fun rates(priority: Int): CandyRates {
        require(priority in MIN_PRIORITY..MAX_PRIORITY) { "priority must be $MIN_PRIORITY..$MAX_PRIORITY" }
        return tables[priority - 1]
    }

    fun reward(priority: Int, rng: Random = Random): CandyReward = rates(priority).roll(rng)
}
