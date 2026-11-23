package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sound meter's processing is plain Kotlin: these run it off the UI, on the JVM. */
class SoundMeterTest {
    private fun sine(hz: Double, rate: Int = 16_000, n: Int = 1024, amp: Double = 0.5) = ShortArray(n) { (amp * 32767 * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    @Test
    fun silenceReadsQuietAndHasNoPitch() {
        val meter = SoundMeter()
        val r = assertNotNull(meter.onFrame(ShortArray(1024), 16_000, 0))
        assertEquals(-120f, r.dbFs)
        assertEquals(-120f, r.low)
        assertEquals(-120f, r.high)
        assertEquals(0, r.samples)
        assertNull(r.pitchHz)
        assertTrue(r.bands.size == 24)
    }

    @Test
    fun oneToneGivesItsPitchAndBars() {
        val meter = SoundMeter()
        val r = assertNotNull(meter.onFrame(sine(1000.0), 16_000, 0))
        assertTrue(abs(r.pitchHz!! - 1000f) < 20f, "${r.pitchHz}")
        assertTrue(r.bands.max() > 0f)
        assertTrue(r.dbFs in -12f..-2f, "${r.dbFs}")
    }

    @Test
    fun theScreenUpdatesAtTenHertzAndOnlyWhenTheValueChanges() {
        val meter = SoundMeter()
        val quiet = sine(1000.0, amp = 0.05)
        val loud = sine(1000.0, amp = 0.5)
        val first = assertNotNull(meter.onFrame(loud, 16_000, 0), "the first reading is always drawn")
        assertNull(meter.onFrame(loud, 16_000, 50), "too soon after a draw")
        assertNull(meter.onFrame(loud, 16_000, 100), "a redraw slot with the same level writes no new state")
        val changed = assertNotNull(meter.onFrame(quiet, 16_000, 200), "a changed level at a redraw slot is drawn")
        assertTrue(changed.dbFs < first.dbFs, "the quiet frame reads quieter than the loud one: ${changed.dbFs} vs ${first.dbFs}")
        assertNull(meter.onFrame(quiet, 16_000, 260), "still throttled")
    }

    @Test
    fun theQuietestLoudestAndAverageFollowTheFrames() {
        val meter = SoundMeter()
        meter.onFrame(sine(1000.0, amp = 0.05), 16_000, 0)
        val louder = assertNotNull(meter.onFrame(sine(1000.0, amp = 0.5), 16_000, 200))
        assertTrue(louder.high > louder.low, "the loudest is above the quietest")
        assertEquals(2, louder.samples)
        meter.resetExtremes()
        val again = assertNotNull(meter.onFrame(sine(1000.0, amp = 0.5), 16_000, 400))
        assertEquals(1, again.samples, "the reset starts the quietest and loudest again")
        assertEquals(again.low, again.high)
    }

    @Test
    fun resetForgetsTheOldRoom() {
        val meter = SoundMeter()
        meter.onFrame(sine(1000.0), 16_000, 0)
        meter.reset()
        val r = assertNotNull(meter.onFrame(ShortArray(1024), 16_000, 10))
        assertEquals(-120f, r.dbFs)
        assertEquals(0, r.samples)
    }
}
