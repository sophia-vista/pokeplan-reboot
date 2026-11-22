package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun distancesBearingsAndText() {
        val d = Geo.distanceMeters(14.5995, 120.9842, 35.6762, 139.6503)
        assertTrue(d in 2_950_000.0..3_100_000.0, "$d")
        val b = Geo.bearingDegrees(14.5995, 120.9842, 35.6762, 139.6503)
        assertTrue(b in 30.0..60.0, "$b")
        assertEquals(0.0, Geo.distanceMeters(1.0, 2.0, 1.0, 2.0), 1e-6)
        assertEquals(180.0, Geo.bearingDegrees(10.0, 0.0, 5.0, 0.0), 0.01)
        assertEquals(90.0, Geo.bearingDegrees(0.0, 0.0, 0.0, 10.0), 0.01)
        assertEquals("850 m", Geo.distanceText(850.0)); assertEquals("1.2 km", Geo.distanceText(1_240.0)); assertEquals("250 km", Geo.distanceText(250_000.0))
        assertEquals("14.5995° N, 120.9842° E", Geo.coordinates(14.5995, 120.9842)); assertEquals("33.8688° S, 151.2093° E", Geo.coordinates(-33.8688, 151.2093))
        assertEquals("geo:14.59950,120.98420", Geo.mapLink(14.5995, 120.9842)); assertEquals("geo:-33.86880,-70.00000", Geo.mapLink(-33.8688, -70.0))
        assertTrue(Geo.valid(90.0, 180.0)); assertTrue(!Geo.valid(91.0, 0.0)); assertTrue(!Geo.valid(Double.NaN, 0.0))
    }
}

class SoundMathTest {
    private fun sine(hz: Double, rate: Int, n: Int, amp: Double = 0.5) = ShortArray(n) { (amp * 32767 * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    @Test
    fun levelsAreBelowFullScaleAndSilenceIsFloored() {
        assertEquals(-120f, SoundMath.dbFs(ShortArray(100)))
        assertEquals(-120f, SoundMath.dbFs(ShortArray(0)))
        val full = ShortArray(1000) { if (it % 2 == 0) 32767 else -32768 }
        assertTrue(abs(SoundMath.dbFs(full)) < 0.1f, "a full-scale square wave is about 0 dB")
        assertEquals(-3f, SoundMath.dbFs(sine(1000.0, 16_000, 16_000, 1.0)), 0.2f)
        assertEquals(70f, SoundMath.estimate(-20f, 90f)); assertEquals(0f, SoundMath.estimate(-120f, 90f)); assertEquals(130f, SoundMath.estimate(0f, 200f))
        assertEquals("Normal talk", SoundMath.describe(50f)); assertTrue(SoundMath.describe(110f).contains("ears"))
    }

    @Test
    fun theSpectrumFindsThePitch() {
        val rate = 16_000
        val s = SoundMath.spectrum(sine(1000.0, rate, 4096), 1024)
        val hz = SoundMath.dominantHz(s, rate)!!
        assertTrue(abs(hz - 1000f) < 20f, "$hz")
        val a4 = SoundMath.dominantHz(SoundMath.spectrum(sine(440.0, rate, 4096), 1024), rate)!!
        assertTrue(abs(a4 - 440f) < 15f, "$a4"); assertEquals("A4", SoundMath.noteName(a4))
        assertEquals("C4", SoundMath.noteName(261.6f)); assertEquals("E2", SoundMath.noteName(82.4f))
        assertNull(SoundMath.dominantHz(SoundMath.spectrum(ShortArray(2048), 1024), rate), "silence has no pitch")
        val re = DoubleArray(8).also { it[0] = 1.0 }; val im = DoubleArray(8)
        SoundMath.fft(re, im); assertTrue(re.all { abs(it - 1.0) < 1e-9 })
    }
}

class NetworkCheckTest {
    @Test
    fun theTimesBecomeAVerdict() {
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(null, null, null))).startsWith("No answer"))
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(40L, 60L, 55L))).startsWith("Fast"))
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(200L, 220L, 180L))).startsWith("Good"))
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(500L, 600L, 450L))).startsWith("Slow"))
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(2000L, 1500L, 1800L))).startsWith("Very slow"))
        assertTrue(NetworkCheck.verdict(NetworkCheck.Result(listOf(50L, null, 60L))).startsWith("Unreliable"))
        val r = NetworkCheck.Result(listOf(100L, 160L, 120L)); assertEquals(120L, r.median); assertEquals(60L, r.jitter); assertNull(NetworkCheck.Result(listOf(null)).median)
        assertNotNull(NetworkInfo("Wi-Fi", false, true)); assertTrue(!NetworkInfo("No connection", false, false).online)
    }
}
