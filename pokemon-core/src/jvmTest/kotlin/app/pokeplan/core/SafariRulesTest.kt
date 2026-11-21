package app.pokeplan.core

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.reward.SafariRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafariRulesTest {
    @Test
    fun slotsGrowWithPeopleAndStopAtTheCap() {
        assertEquals(SafariRules.BASE_SLOTS, SafariRules.slots(0))
        assertEquals(SafariRules.BASE_SLOTS + 1, SafariRules.slots(SafariRules.PEOPLE_PER_SLOT))
        assertEquals(SafariRules.slots(SafariRules.PEOPLE_CAP), SafariRules.slots(10_000))
        assertTrue(SafariRules.slots(10_000) <= SafariRules.MAX_SLOTS)
    }

    @Test
    fun aPoolIsStableDistinctAndFromTheLinkRegion() {
        val region = Dex.regionDex(Region.LINK).map { it.id }.toSet()
        for (ref in listOf("a", "b", "deadbeef", "0", "ffffffffffffffff")) {
            val pool = SafariRules.pool(ref, SafariRules.PEOPLE_CAP)
            assertEquals(pool, SafariRules.pool(ref, SafariRules.PEOPLE_CAP))
            assertEquals(pool.size, pool.map { it.speciesId }.toSet().size)
            assertTrue(pool.all { it.speciesId in region })
            assertEquals(pool.take(SafariRules.BASE_SLOTS), SafariRules.pool(ref, 0))
        }
    }

    @Test
    fun differentKeysGiveDifferentSafaris() {
        val pools = (1..30).map { SafariRules.pool("key$it", 0).map { o -> o.speciesId } }.toSet()
        assertTrue(pools.size > 15)
    }
}
