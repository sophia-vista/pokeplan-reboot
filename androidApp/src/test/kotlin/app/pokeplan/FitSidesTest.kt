package app.pokeplan

import app.pokeplan.platform.fitSides
import app.pokeplan.platform.isKeepableImage
import org.junit.Test
import kotlin.test.assertEquals

class FitSidesTest {
    @Test fun smallPictureIsLeftAlone() = assertEquals(300 to 200, fitSides(300, 200, 600))
    @Test fun wideKeepsAspect() = assertEquals(600 to 300, fitSides(1200, 600, 600))
    @Test fun tallKeepsAspect() = assertEquals(240 to 480, fitSides(600, 1200, 480))
    @Test fun neverBelowOnePixel() = assertEquals(240 to 1, fitSides(100000, 400, 240))
    @Test fun pngJpegAndWebpAreKept() {
        assertEquals(true, isKeepableImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertEquals(true, isKeepableImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(true, isKeepableImage("RIFF1234WEBPVP8 ".toByteArray()))
    }

    @Test fun otherKindsAreRe_encoded() {
        assertEquals(false, isKeepableImage("GIF89a".toByteArray()))
        assertEquals(false, isKeepableImage("RIFF1234WAVEfmt ".toByteArray()))
        assertEquals(false, isKeepableImage(byteArrayOf()))
    }
}
