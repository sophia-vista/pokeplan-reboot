package app.pokeplan.shared.model

import kotlin.random.Random

/** Percent chance of each rarity; the four values always add up to 100. */
data class RarityRates(val common: Int, val uncommon: Int, val rare: Int, val superRare: Int) {
    init {
        require(common + uncommon + rare + superRare == 100) { "rates must add up to 100" }
    }

    fun roll(rng: Random): Rarity {
        val n = rng.nextInt(100)
        return when {
            n < common -> Rarity.COMMON
            n < common + uncommon -> Rarity.UNCOMMON
            n < common + uncommon + rare -> Rarity.RARE
            else -> Rarity.SUPER_RARE
        }
    }
}

/** An egg hatched by a finished focus session. Longer sessions, and Deep Focus, shift the odds toward rarer species. */
object Egg {
    private val tables = listOf(
        RarityRates(60, 30, 9, 1),
        RarityRates(45, 35, 15, 5),
        RarityRates(20, 45, 25, 10),
        RarityRates(5, 35, 40, 20),
        RarityRates(0, 30, 45, 25),
        RarityRates(0, 10, 50, 40),
        RarityRates(0, 0, 30, 70),
    )

    /** Session length bands (minutes) that each step up one table: 5, 20, 40, 60, 120, 240. */
    private val bandStarts = listOf(5, 20, 40, 60, 120, 240)

    fun rates(duration: FocusDuration, deepFocus: Boolean): RarityRates {
        require(duration.isLongEnough) { "focus sessions are at least ${FocusDuration.MIN_MINUTES} minutes" }
        val minutes = duration.minutesRoundedUp
        val band = bandStarts.indexOfLast { minutes >= it }
        // Deep Focus is worth one band.
        return tables[if (deepFocus) band + 1 else band]
    }
}
