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
        repeat(200) { val t = TownBehavior.target(random, anchor, 2, guardian = true); assertTrue(kotlin.math.abs(t.x - 1f) <= 0.12f) }
    }

    @Test
    fun aTurnIsAlwaysAChangeOfDirection() {
        val random = Random(2)
        repeat(500) { assertEquals(true, (TownBehavior.next(random, false, facingLeft = false) as? Beat.Turn)?.left ?: true) }
    }

    @Test
    fun othersTryEveryKindOfBeatAndRoamOneToThreeLegs() {
        val random = Random(3)
        val beats = List(2000) { TownBehavior.next(random, false, true) }
        assertTrue(beats.any { it is Beat.Stand } && beats.any { it is Beat.Turn } && beats.any { it == Beat.Graze } && beats.any { it == Beat.Hop })
        assertEquals(setOf(1, 2, 3), beats.filterIsInstance<Beat.Roam>().map { it.legs }.toSet())
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
