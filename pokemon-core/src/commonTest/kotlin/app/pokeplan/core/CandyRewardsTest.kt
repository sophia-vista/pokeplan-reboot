package app.pokeplan.core

import app.pokeplan.core.model.CandyRewards
import app.pokeplan.core.model.CandyType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CandyRewardsTest {
    @Test
    fun amountsStayInsideThePriorityRange() {
        val rng = Random(3)
        for (p in CandyRewards.MIN_PRIORITY..CandyRewards.MAX_PRIORITY) {
            val rates = CandyRewards.rates(p)
            repeat(2_000) {
                val r = CandyRewards.reward(p, rng)
                val range = if (r.type == CandyType.SUPER) rates.superCandy else rates.rare
                assertTrue(r.amount in range, "priority $p: $r")
            }
        }
    }

    @Test
    fun superCandyShareGrowsWithPriority() {
        val shares = (1..5).map { p ->
            val rng = Random(11)
            List(10_000) { CandyRewards.reward(p, rng) }.count { it.type == CandyType.SUPER } / 100.0
        }
        assertEquals(shares.sorted(), shares)
        // 10%, 15%, 20%, 30%, 40% in the original tables.
        listOf(10, 15, 20, 30, 40).zip(shares).forEach { (want, got) -> assertTrue(got in want - 2.0..want + 2.0, "$got vs $want") }
    }

    @Test
    fun topPriorityStillPaysARange() {
        // The original table had min 7 above max 6 here, which always paid 7.
        val rng = Random(5)
        val amounts = List(5_000) { CandyRewards.reward(5, rng) }.filter { it.type == CandyType.SUPER }.map { it.amount }.toSet()
        assertEquals(setOf(6, 7), amounts)
    }

    @Test
    fun priorityOutsideOneToFiveIsRejected() {
        assertFailsWith<IllegalArgumentException> { CandyRewards.reward(0) }
        assertFailsWith<IllegalArgumentException> { CandyRewards.reward(6) }
    }
}
