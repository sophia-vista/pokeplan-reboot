package app.pokeplan.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.pokeplan.app.storage.MemoryStorage
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Android entry point builds the save, the stores and the Dex tables off the UI thread while the startup screen is
 * up (`App(asyncModel = true)`), so its pixels keep moving instead of the first real frame freezing the last drawn one.
 * This renders that entry the way the app opens: the startup screen first, then the app itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
class StartupScreenTest {
    private val paper = 0xF7F5F0 // Pixel.Paper in the light palette, the startup screen's whole background

    private fun corner(bytes: ByteArray): Int =
        ImageIO.read(ByteArrayInputStream(bytes)).getRGB(4, 4) and 0xFFFFFF

    @Test
    fun theStartupScreenTurnsIntoTheApp() {
        val storage = MemoryStorage()
        val scene = ImageComposeScene(width = 540, height = 1080, density = Density(1.5f)) {
            App(storage, asyncModel = true)
        }
        var nanos = 0L
        var turnedAt = -1
        for (frame in 0 until 120) {
            val bytes = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
            if (frame == 0) assertTrue(corner(bytes) == paper, "the first frame was not the startup screen")
            if (corner(bytes) != paper) { turnedAt = frame; break }
            nanos += 50_000_000
            Thread.sleep(5)
        }
        scene.close()
        assertTrue(turnedAt > 0, "the startup screen never turned into the app (the model never finished off the UI thread)")
    }
}
