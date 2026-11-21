package app.pokeplan.core.dex

import kotlin.random.Random

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

    private val bandStarts = listOf(5, 20, 40, 60, 120, 240)

    const val MIN_MINUTES = 5

    fun rates(minutes: Int, deepFocus: Boolean): RarityRates {
        require(minutes >= MIN_MINUTES) { "an egg needs at least $MIN_MINUTES minutes of effort" }
        val band = bandStarts.indexOfLast { minutes >= it }
        return tables[if (deepFocus) band + 1 else band]
    }
}
