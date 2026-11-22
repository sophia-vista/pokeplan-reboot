package app.pokeplan.ui.launcher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherRampTest {
    private val ramps = WeatherRamp.parse(File("../assets/weather/ramps.tsv").readText())

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun allEightWeathersAreThere() {
        assertEquals(setOf("CLEAR", "SUNNY", "SANDSTORM", "CLOUDY", "RAIN", "HAIL", "FOG", "SNOW"), ramps.keys)
    }

    @Test
    fun aClearSkyLeavesAColourWithinAThirtySecondOfItself() {
        val clear = ramps.getValue("CLEAR")
        for (v in listOf(0, 40, 90, 140, 200, 255)) {
            val out = blue(clear.grade(rgb(v, v, v)))
            assertTrue(kotlin.math.abs(out - v) <= 16, "$v became $out")
        }
        assertEquals(0, clear.grade(0), "a transparent pixel stays as it is")
    }

    @Test
    fun sandstormIsYellowBrownRainIsBlueAndFogLiftsTheBlacks() {
        val grey = rgb(120, 120, 120)
        val sand = ramps.getValue("SANDSTORM").grade(grey)
        assertTrue(red(sand) > blue(sand) + 40 && green(sand) > blue(sand) + 30, "sand light is warm")
        val rain = ramps.getValue("RAIN").grade(grey)
        assertTrue(blue(rain) > red(rain) + 20, "rain light is blue")
        val black = rgb(0, 0, 0)
        val fog = ramps.getValue("FOG").grade(black)
        assertTrue(red(fog) > 60 && red(fog) == green(fog) && green(fog) == blue(fog), "fog's blacks are a flat grey")
        val sun = ramps.getValue("SUNNY").grade(black)
        assertTrue(red(sun) in 1..30, "sunny lifts the blacks a little")
    }
}
