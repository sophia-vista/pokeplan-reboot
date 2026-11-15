package app.pokeplan

import app.pokeplan.platform.fitSides
import org.junit.Test
import kotlin.test.assertEquals

class FitSidesTest {
    @Test fun smallPictureIsLeftAlone() = assertEquals(300 to 200, fitSides(300, 200, 600))
    @Test fun wideKeepsAspect() = assertEquals(600 to 300, fitSides(1200, 600, 600))
    @Test fun tallKeepsAspect() = assertEquals(240 to 480, fitSides(600, 1200, 480))
    @Test fun neverBelowOnePixel() = assertEquals(240 to 1, fitSides(100000, 400, 240))
}
