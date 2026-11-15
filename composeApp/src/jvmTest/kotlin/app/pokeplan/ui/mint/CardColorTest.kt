package app.pokeplan.ui.mint

import kotlin.test.Test
import kotlin.test.assertEquals

class CardColorTest {
    @Test
    fun hsvRoundTripKeepsEveryColourWithinOneStep() {
        val samples = listOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xA98CF0, 0x6E45C2, 0x123456, 0xFEDCBA, 0x808080)
        for (rgb in samples) {
            val (h, s, v) = rgbToHsv(rgb)
            val back = hsvToRgb(h, s, v)
            for (shift in listOf(16, 8, 0)) assertEquals((rgb shr shift and 255).toDouble(), (back shr shift and 255).toDouble(), 1.0, "channel $shift of ${hexOf(rgb)}")
        }
        assertEquals(0xFF0000, hsvToRgb(0f, 1f, 1f))
        assertEquals(0x00FF00, hsvToRgb(120f, 1f, 1f))
        assertEquals("#00FF7F".length, hexOf(0x00FF7F).length)
    }
}
