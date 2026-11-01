package app.pokeplan.ui.gear

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensorMathTest {
    private fun near(a: Float?, b: Float, tol: Float = 0.5f) = assertTrue(a != null && abs(SensorMath.delta(a, b)) <= tol, "$a vs $b")

    @Test fun headingFromGravityAndTheMagneticField() {
        val flat = floatArrayOf(0f, 0f, 9.8f)
        near(SensorMath.heading(flat, floatArrayOf(0f, 20f, -40f)), 0f)      // north is straight ahead
        near(SensorMath.heading(flat, floatArrayOf(-20f, 0f, -40f)), 90f)    // north is to the left: facing east
        near(SensorMath.heading(flat, floatArrayOf(0f, -20f, -40f)), 180f)   // facing south
        near(SensorMath.heading(flat, floatArrayOf(20f, 0f, -40f)), 270f)    // facing west
        assertNull(SensorMath.heading(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 20f, 0f)), "no gravity")
        assertNull(SensorMath.heading(flat, floatArrayOf(0f, 0f, -40f)), "a field straight down gives no direction")
    }

    @Test fun anglesWrapAroundAndSmoothTheShortWay() {
        assertEquals(350f, SensorMath.normalize(-10f)); assertEquals(10f, SensorMath.normalize(370f))
        assertEquals(20f, SensorMath.delta(350f, 10f)); assertEquals(-20f, SensorMath.delta(10f, 350f))
        val s = SensorMath.smooth(350f, 10f, 0.5f)
        near(s, 0f); assertEquals(5f, SensorMath.smooth(null, 5f))
        assertEquals("N", SensorMath.cardinal(0f)); assertEquals("N", SensorMath.cardinal(359f)); assertEquals("NE", SensorMath.cardinal(45f)); assertEquals("WSW", SensorMath.cardinal(250f))
    }

    @Test fun tiltAndLevel() {
        assertTrue(SensorMath.isLevel(SensorMath.tilt(floatArrayOf(0f, 0f, 9.81f))!!))
        val up = SensorMath.tilt(floatArrayOf(0f, 4.9f, 8.49f))!!
        assertTrue(abs(up.pitch - 30f) < 0.5f && abs(up.roll) < 0.5f, up.toString())
        assertTrue(abs(up.fromFlat - 30f) < 0.5f)
        assertNull(SensorMath.tilt(floatArrayOf(0f, 0f, 0f)), "free fall gives no tilt")
        assertEquals("3.4°", SensorMath.degreesText(3.44f)); assertEquals("-0.5°", SensorMath.degreesText(-0.5f)); assertEquals("0.0°", SensorMath.degreesText(-0.01f))
    }

    @Test fun pressureToHeight() {
        assertEquals(0f, SensorMath.altitudeMeters(1013.25f)!!, 0.5f)
        val h = SensorMath.altitudeMeters(899f)!!
        assertTrue(h in 950f..1050f, "about 1 km up: $h")
        assertTrue(SensorMath.altitudeMeters(1013.25f, 1023.25f)!! > 70f, "a higher sea-level reference puts the same reading higher")
        assertNull(SensorMath.altitudeMeters(20f)); assertNull(SensorMath.altitudeMeters(1000f, 500f))
        assertEquals("1013.3 hPa", SensorMath.pressureText(1013.25f)); assertNotNull(SensorMath.pointOnCircle(90f, 10f))
    }
}
