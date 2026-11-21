package app.pokeplan.ui.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightTest {
    @Test
    fun brightnessScalesTheColourAndNeverGoesBlackOrPastFull() {
        assertEquals(0xFFFFFFFFL, LightColors.byId("white").argb(1f))
        assertEquals(0xFFFFFFFFL, LightColors.byId("white").argb(5f), "past full is full")
        val dim = LightColors.byId("white").argb(0f)
        assertTrue((dim and 0xFF) in 12..13, "never fully dark: ${dim.toString(16)}")
        assertEquals(0xFF000000L or (127L shl 16) or (15L shl 8) or 10L, LightColors.byId("red").argb(0.5f))
        assertEquals("white", LightColors.byId("nope").id, "an unknown colour falls back to white")
        assertEquals(LightColors.all.size, LightColors.all.map { it.id }.toSet().size)
    }

    @Test
    fun sosIsThreeShortThreeLongThreeShortAndAlwaysEndsInARest() {
        val p = Sos.pattern(200)
        val on = p.filter { it.first }.map { it.second }
        assertEquals(listOf(200L, 200, 200, 600, 600, 600, 200, 200, 200), on)
        assertTrue(!p.last().first && p.last().second == 1400L, "a long rest before it repeats")
        assertTrue(p.zipWithNext().none { (a, b) -> a.first && b.first }, "lights never run together")
        assertTrue(Sos.pattern().minOf { it.second } >= 200, "never faster than one change per 200 ms")
        assertEquals(5 * 60_000L, Sos.MAX_MS)
    }
}
