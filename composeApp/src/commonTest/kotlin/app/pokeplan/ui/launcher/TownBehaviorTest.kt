package app.pokeplan.ui.launcher

import app.pokeplan.core.dex.PokemonType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TownBehaviorTest {
    @Test
    fun theGuardianMostlyStandsAndNeverWalksFar() {
        val random = Random(1)
        val beats = List(2000) { TownBehavior.next(random, guardian = true, facingLeft = false) }
        val roams = beats.filterIsInstance<Beat.Roam>()
        assertTrue(roams.size < 400)
        assertTrue(roams.all { it.legs == 1 })
        assertTrue(beats.filterIsInstance<Beat.Stand>().all { it.millis >= 7_000 })
        val anchor = androidx.compose.ui.geometry.Offset(1f, 0.5f)
        var at = anchor
        var heading = 0f
        repeat(500) { val leg = TownBehavior.leg(random, at, heading, anchor, 2, guardian = true); at = leg.to; heading = leg.heading; assertTrue(kotlin.math.abs(at.x - 1f) <= 0.12f) }
    }

    @Test
    fun aTurnIsAlwaysAChangeOfDirection() {
        val random = Random(2)
        repeat(500) { assertEquals(true, (TownBehavior.next(random, false, facingLeft = false) as? Beat.Turn)?.left ?: true) }
    }

    @Test
    fun restingTheyShiftAStepAndRestlessTheyWalkTwoToFourLegs() {
        val random = Random(3)
        val resting = List(2000) { TownBehavior.next(random, false, true, restless = false) }
        assertTrue(resting.any { it is Beat.Stand } && resting.any { it is Beat.Turn } && resting.any { it == Beat.Graze } && resting.any { it == Beat.Hop })
        assertTrue(resting.filterIsInstance<Beat.Roam>().all { it.legs == 1 && it.short })
        val restless = List(2000) { TownBehavior.next(random, false, true, restless = true) }
        assertEquals(setOf(2, 3, 4), restless.filterIsInstance<Beat.Roam>().map { it.legs }.toSet())
        assertTrue(restless.filterIsInstance<Beat.Roam>().size > resting.filterIsInstance<Beat.Roam>().size * 2)
    }

    /** The share of beats spent in a restless spell at a liveliness, over a long run. */
    private fun restlessShare(lively: Float): Float {
        val random = Random(9)
        var restless = false
        var count = 0
        repeat(20_000) { restless = TownBehavior.spell(random, restless, lively); if (restless) count++ }
        return count / 20_000f
    }

    @Test
    fun theSliderMovesTheTownFromSleepyToRestless() {
        val sleepy = restlessShare(TownBehavior.liveliness(1))
        val calm = restlessShare(TownBehavior.liveliness(TownBehavior.ACTIVITY_DEFAULT))
        val restless = restlessShare(TownBehavior.liveliness(10))
        assertTrue(sleepy < 0.22f && calm in 0.25f..0.45f && restless > 0.6f, "$sleepy $calm $restless")
        // Rests are longer in a sleepy town.
        val r = Random(10)
        val slow = List(2000) { TownBehavior.next(r, false, false, lively = 0f) }.filterIsInstance<Beat.Stand>().map { it.millis }.average()
        val quick = List(2000) { TownBehavior.next(r, false, false, lively = 1f) }.filterIsInstance<Beat.Stand>().map { it.millis }.average()
        assertTrue(slow > quick * 2)
        assertEquals("Calm", TownBehavior.activityLabel(TownBehavior.ACTIVITY_DEFAULT))
    }

    @Test
    fun aWalkKeepsToItsWayAndStaysNearItsCorner() {
        val random = Random(11)
        val anchor = androidx.compose.ui.geometry.Offset(1.5f, 0.5f)
        var at = anchor
        var heading = 0f
        var turns = 0f
        var far = 0
        repeat(3000) {
            val leg = TownBehavior.leg(random, at, heading, anchor, 3, guardian = false)
            var d = kotlin.math.abs(leg.heading - heading) % (2f * kotlin.math.PI.toFloat())
            if (d > kotlin.math.PI) d = 2f * kotlin.math.PI.toFloat() - d
            turns += d
            at = leg.to
            heading = leg.heading
            assertTrue(at.y in 0f..1f && at.x in -0.2f..3.2f)
            if (kotlin.math.abs(at.x - anchor.x) > 0.6f) far++
        }
        // Mostly gentle turns (a ruler-straight walk is 0, a fresh random way each leg about 1.57 on average).
        assertTrue(turns / 3000 < 1.0f, "${turns / 3000}")
        // Mostly near its corner, now and then off across the town.
        assertTrue(far in 1..900, "$far")
    }

    @Test
    fun aStrollCurvesGentlyAndEndsWhereItWasGoing() {
        val random = Random(12)
        val from = androidx.compose.ui.geometry.Offset(0.2f, 0.5f)
        val to = androidx.compose.ui.geometry.Offset(0.6f, 0.5f)
        repeat(50) {
            val way = TownBehavior.path(random, from, to)
            assertEquals(to, way.last())
            // Never doubles back, and never strays far from the line.
            assertTrue(way.zipWithNext().all { (a, b) -> b.x >= a.x - 1e-4f })
            assertTrue(way.all { kotlin.math.abs(it.y - 0.5f) * TownBehavior.DEPTH < 0.05f })
        }
        assertTrue(TownBehavior.crowded(androidx.compose.ui.geometry.Offset(0.5f, 0.5f), listOf(androidx.compose.ui.geometry.Offset(0.53f, 0.55f))))
        assertTrue(!TownBehavior.crowded(androidx.compose.ui.geometry.Offset(0.5f, 0.5f), listOf(androidx.compose.ui.geometry.Offset(0.8f, 0.5f))))
    }

    @Test
    fun tappedOverAndOverItIsFlustered() {
        val random = Random(4)
        assertEquals(TapReaction.FLUSTERED, TownBehavior.tapReaction(random, 3))
        assertTrue(List(200) { TownBehavior.tapReaction(random, 1) }.none { it == TapReaction.FLUSTERED })
    }

    @Test
    fun bedtimesAreStaggeredAndWithinRange() {
        val times = (1..60).map { TownBehavior.bedtime("mon$it") }
        assertTrue(times.all { it in 0.4f..0.65f })
        assertTrue(times.toSet().size > 20)
    }

    @Test
    fun weatherFeelingsFollowTypes() {
        assertEquals(Feeling.ENJOYS, TownBehavior.feeling(listOf(PokemonType.WATER), Weather.RAIN))
        assertEquals(Feeling.DISLIKES, TownBehavior.feeling(listOf(PokemonType.FIRE), Weather.RAIN))
        assertEquals(Feeling.ENJOYS, TownBehavior.feeling(listOf(PokemonType.ICE, PokemonType.FLYING), Weather.SNOW))
        assertNull(TownBehavior.feeling(listOf(PokemonType.NORMAL), Weather.RAIN))
        assertNull(TownBehavior.feeling(listOf(PokemonType.WATER), Weather.CLEAR))
    }

    @Test
    fun theWayInAndOutIsOffTheEdgeOfHome() {
        val random = Random(5)
        repeat(50) {
            val e = TownBehavior.entrance(random, home = 1)
            assertTrue(e.x < 1f || e.x > 2f)
            assertTrue(TownBehavior.exit(androidx.compose.ui.geometry.Offset(1.2f, 0.4f), 1).x < 1f)
            assertTrue(TownBehavior.exit(androidx.compose.ui.geometry.Offset(1.8f, 0.4f), 1).x > 2f)
        }
    }

    @Test
    fun paceAndAnchorAreStable() {
        assertEquals(TownBehavior.pace("a"), TownBehavior.pace("a"))
        assertTrue(TownBehavior.pace("zeta") in 0.8f..1.25f)
        assertNotEquals(TownBehavior.anchor("a", 2, 0, false), TownBehavior.anchor("b", 2, 0, false))
        assertTrue(TownBehavior.anchor("x", 2, 1, buddy = true).x in 1.3f..1.7f)
    }
}
