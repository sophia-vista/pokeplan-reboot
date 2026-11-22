package app.pokeplan.ui.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GbaWeatherTest {
    @Test
    fun snowAndBlizzardFramesLastAsLongAsTheGamesDurationsSay() {
        assertEquals(5, GbaWeather.SNOW_TICKS)
        assertEquals(2, GbaWeather.BLIZZARD_TICKS)
        val snowFrameMillis = GbaWeather.SNOW_TICKS * GbaWeather.TICK_MILLIS
        assertTrue(snowFrameMillis in 83.0..84.5, "about 83.7 ms, not the 220 ms it was drawn at")
        assertEquals(0, GbaWeather.frame(0, GbaWeather.SNOW_TICKS))
        assertEquals(0, GbaWeather.frame(80, GbaWeather.SNOW_TICKS))
        assertEquals(1, GbaWeather.frame(85, GbaWeather.SNOW_TICKS))
        assertEquals(1, GbaWeather.frame(34, GbaWeather.BLIZZARD_TICKS))
        assertEquals(0, GbaWeather.frame((16 * GbaWeather.SNOW_TICKS * GbaWeather.TICK_MILLIS).toLong() + 1, GbaWeather.SNOW_TICKS))
    }

    @Test
    fun rainFramesLastTwoTicksAndLoopOverTheSixtyOneDrawnFrames() {
        // Effect 16 is efbg004: every one of its 63 frame headers says 2 ticks, and the sheet holds the 61 frames that have art.
        assertEquals(2, GbaWeather.RAIN_TICKS)
        assertEquals(61, GbaWeather.RAIN_FRAMES)
        val frameMillis = GbaWeather.RAIN_TICKS * GbaWeather.TICK_MILLIS
        assertTrue(frameMillis in 33.0..34.0, "about 33.5 ms, not the 70 ms it was drawn at")
        assertEquals(0, GbaWeather.rainFrame(0))
        assertEquals(0, GbaWeather.rainFrame(33))
        assertEquals(1, GbaWeather.rainFrame(34))
        assertEquals(1, GbaWeather.rainFrame(66))
        assertEquals(0, GbaWeather.rainFrame((61 * 2 * GbaWeather.TICK_MILLIS).toLong() + 1))
    }

    @Test
    fun theCloudsDriftRightAHalfPixelAndUpAQuarterPixelATick() {
        assertEquals(0 to 0, GbaWeather.cloudShift(0))
        assertEquals(0 to 0, GbaWeather.cloudShift(1))
        assertEquals(1 to 0, GbaWeather.cloudShift(2))
        assertEquals(2 to -1, GbaWeather.cloudShift(4))
        assertEquals(30 to -15, GbaWeather.cloudShift(60), "30 pixels right and 15 up in a second")
        assertEquals(1, GbaWeather.fastTextureShift(2))
        assertEquals(1, GbaWeather.slowTextureShift(4))
        assertEquals(0, GbaWeather.slowTextureShift(3))
    }

    @Test
    fun aTextureLayerIsBlendedInAtAQuarter() {
        assertEquals(0.25f, GbaWeather.TEXTURE_ALPHA)
    }

    @Test
    fun theTownsWeathersAreGradedLikeTheGamesOnes() {
        assertEquals("RAIN", Weather.RAIN.rampName())
        assertEquals("SNOW", Weather.SNOW.rampName())
        assertEquals("HAIL", Weather.BLIZZARD.rampName())
        assertEquals("SANDSTORM", Weather.SANDSTORM.rampName())
        assertEquals("CLOUDY", Weather.CLOUDS.rampName())
        assertEquals(null, Weather.CLEAR.rampName())
    }
}
