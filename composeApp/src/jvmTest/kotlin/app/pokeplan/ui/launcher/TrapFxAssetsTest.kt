package app.pokeplan.ui.launcher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The trap effects in the code ([TrapFx]) are the ones tools/sprites/extract_sky_effects.py cut, frame for frame. */
class TrapFxAssetsTest {
    private val tiles = listOf("../assets/tiles", "assets/tiles").map(::File).first { it.isDirectory }

    @Test
    fun theTableMatchesTheExtractedEffects() {
        val rows = File(tiles, "trap-fx.tsv").readLines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
        assertEquals(TrapFx.entries.map { it.name.lowercase() }, rows.map { it[0] })
        for (r in rows) {
            val fx = TrapFx.valueOf(r[0].uppercase())
            assertEquals(listOf(r[4], r[5], r[6], r[7]).map(String::toInt), listOf(fx.width, fx.height, fx.originX, fx.originY))
            assertEquals(r[8].split(',').map(String::toInt), fx.ticks)
            val strip = javax.imageio.ImageIO.read(File(tiles, fx.file + ".png"))
            assertEquals(fx.width * fx.ticks.size, strip.width)
            assertEquals(fx.height, strip.height)
        }
    }
}
