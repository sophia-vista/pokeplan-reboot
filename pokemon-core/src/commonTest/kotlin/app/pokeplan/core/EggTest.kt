package app.pokeplan.core

import app.pokeplan.core.model.Egg
import app.pokeplan.core.model.Rarity
import app.pokeplan.core.model.RarityRates
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EggTest {
    @Test
    fun bandsFollowTheOriginalTables() {
        val expected = mapOf(
            5 to RarityRates(60, 30, 9, 1),
            19 to RarityRates(60, 30, 9, 1),
            20 to RarityRates(45, 35, 15, 5),
            40 to RarityRates(20, 45, 25, 10),
            60 to RarityRates(5, 35, 40, 20),
            120 to RarityRates(0, 30, 45, 25),
            239 to RarityRates(0, 30, 45, 25),
            240 to RarityRates(0, 10, 50, 40),
            600 to RarityRates(0, 10, 50, 40),
        )
        for ((m, rates) in expected) assertEquals(rates, Egg.rates(m, deepFocus = false), "$m min")
    }

    @Test
    fun deepFocusMovesUpOneBand() {
        assertEquals(RarityRates(45, 35, 15, 5), Egg.rates(5, deepFocus = true))
        assertEquals(RarityRates(0, 10, 50, 40), Egg.rates(120, deepFocus = true))
        assertEquals(RarityRates(0, 0, 30, 70), Egg.rates(240, deepFocus = true))
    }

    @Test
    fun effortUnderFiveMinutesDoesNotHatch() {
        assertFailsWith<IllegalArgumentException> { Egg.rates(4, false) }
    }

    @Test
    fun rollMatchesTheRatesOverManyEggs() {
        val rng = Random(42)
        val rates = RarityRates(45, 35, 15, 5)
        val n = 20_000
        val counts = List(n) { rates.roll(rng) }.groupingBy { it }.eachCount()
        for ((rarity, pct) in listOf(Rarity.COMMON to 45, Rarity.UNCOMMON to 35, Rarity.RARE to 15, Rarity.SUPER_RARE to 5)) {
            val got = counts.getValue(rarity) * 100.0 / n
            assertTrue(got in pct - 1.5..pct + 1.5, "$rarity: $got% vs $pct%")
        }
    }

    @Test
    fun zeroRateRaritiesNeverRoll() {
        val rng = Random(7)
        repeat(5_000) { assertTrue(RarityRates(0, 0, 30, 70).roll(rng) in listOf(Rarity.RARE, Rarity.SUPER_RARE)) }
    }

    @Test
    fun ratesMustAddUpTo100() {
        assertFailsWith<IllegalArgumentException> { RarityRates(50, 30, 10, 5) }
    }
}
