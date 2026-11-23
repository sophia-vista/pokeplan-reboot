package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.theme.LoadingDots
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The loading pixels must actually move: a tween shrinks to nothing when the phone's animations are turned off and the
 * indicator froze on its first dot. These render the real composable at known frame times and read the dot colours back.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LoadingDotsTest {
    private val lit = listOf(0x66, 0x6B, 0x7E) // Pixel.InkSoft in the light palette

    /** Which of the three pixels is the solid one at the given frame times. */
    private fun litDots(calm: Boolean, frameTimesMs: List<Long>): List<List<Boolean>> {
        val scene = ImageComposeScene(width = 40, height = 12, density = Density(1f)) {
            CompositionLocalProvider(LocalReduceMotion provides calm) {
                PixelTheme(false) {
                    Box(Modifier.size(40.dp, 12.dp).background(Pixel.Paper)) { LoadingDots() }
                }
            }
        }
        val shots = mutableListOf<List<Boolean>>()
        for (ms in frameTimesMs) {
            val image = scene.render(ms * 1_000_000L)
            val bytes = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
            val img = ImageIO.read(ByteArrayInputStream(bytes))
            shots += listOf(2, 12, 22).map { x -> isLit(img.getRGB(x, 2)) }
        }
        scene.close()
        return shots
    }

    private fun isLit(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return kotlin.math.abs(r - lit[0]) + kotlin.math.abs(g - lit[1]) + kotlin.math.abs(b - lit[2]) < 60
    }

    @Test
    fun thePixelsAdvanceAcrossTheFrames() {
        // Frames at 0, 16, 32 ms start the clock; the samples below land in three different steps of the loop.
        val frames = litDots(calm = false, frameTimesMs = listOf(0, 16, 32, 332, 632, 932))
        val phases = frames.drop(3).map { shot -> shot.indexOfFirst { it } }
        assertEquals(3, phases.toSet().size, "the loading pixels stayed on one dot across the loop: $phases")
    }

    @Test
    fun thePixelsHoldStillUnderReduceMotion() {
        val frames = litDots(calm = true, frameTimesMs = listOf(0, 16, 32, 332, 632))
        frames.forEach { shot -> assertTrue(shot.all { it }, "Reduce motion must hold all three pixels solid, got $shot") }
    }

    /** A phone with animations turned off: the platform motion scale is 0, so nothing may loop. */
    private class StillPhone(override val scaleFactor: Float) : MotionDurationScale

    @Test
    fun thePixelsHoldOnAPhoneWithAnimationsOff() {
        val scene = ImageComposeScene(40, 12, Density(1f), Dispatchers.Unconfined + StillPhone(0f)) {
            PixelTheme(false) {
                Box(Modifier.size(40.dp, 12.dp).background(Pixel.Paper)) { LoadingDots() }
            }
        }
        val shots = listOf(0L, 16L, 32L, 332L, 632L).map { ms ->
            val bytes = scene.render(ms * 1_000_000L).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
            val img = ImageIO.read(ByteArrayInputStream(bytes))
            listOf(2, 12, 22).map { x -> isLit(img.getRGB(x, 2)) }
        }
        scene.close()
        shots.forEach { shot -> assertTrue(shot.all { it }, "animations off must hold all three pixels solid, got $shot") }
    }
}
