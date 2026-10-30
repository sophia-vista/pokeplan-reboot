package app.pokeplan.core

import app.pokeplan.core.reward.CandyRewards
import app.pokeplan.core.reward.CandyType
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
                for (r in CandyRewards.reward(p, rng)) {
                    val range = if (r.type == CandyType.SUPER) rates.superCandy else rates.rare
                    assertTrue(r.amount in range, "priority $p: $r")
                }
            }
        }
    }

    @Test
    fun everyTaskPaysRareCandiesAndSuperCandiesComeOnTop() {
        val rng = Random(7)
        for (p in CandyRewards.MIN_PRIORITY..CandyRewards.MAX_PRIORITY) {
            repeat(2_000) {
                val paid = CandyRewards.reward(p, rng)
                assertEquals(CandyType.RARE, paid.first().type, "priority $p: $paid")
                assertTrue(paid.size == 1 || (paid.size == 2 && paid[1].type == CandyType.SUPER), "priority $p: $paid")
            }
        }
    }

    @Test
    fun superCandyBonusGrowsWithPriority() {
        val shares = (1..5).map { p ->
            val rng = Random(11)
            List(10_000) { CandyRewards.reward(p, rng) }.count { paid -> paid.any { it.type == CandyType.SUPER } } / 100.0
        }
        assertEquals(shares.sorted(), shares)
        // 10%, 15%, 20%, 30%, 40%, the Super Candy shares of the original tables.
        listOf(10, 15, 20, 30, 40).zip(shares).forEach { (want, got) -> assertTrue(got in want - 2.0..want + 2.0, "$got vs $want") }
    }

    @Test
    fun theSuperCandyPerkRaisesTheBonusChance() {
        val rates = CandyRewards.rates(1)
        val plain = Random(4).let { rng -> List(10_000) { rates.roll(rng) }.count { it.size == 2 } }
        val perked = Random(4).let { rng -> List(10_000) { rates.roll(rng, superBonusPoints = 10) }.count { it.size == 2 } }
        assertTrue(perked > plain * 1.5, "plain $plain, perked $perked")
    }

    @Test
    fun topPriorityStillPaysARange() {
        // The original table had min 7 above max 6 here, which always paid 7.
        val rng = Random(5)
        val amounts = List(5_000) { CandyRewards.reward(5, rng) }.flatten().filter { it.type == CandyType.SUPER }.map { it.amount }.toSet()
        assertEquals(setOf(6, 7), amounts)
    }

    @Test
    fun priorityOutsideOneToFiveIsRejected() {
        assertFailsWith<IllegalArgumentException> { CandyRewards.reward(0) }
        assertFailsWith<IllegalArgumentException> { CandyRewards.reward(6) }
    }
}
