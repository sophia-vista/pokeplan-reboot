package app.pokeplan.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.pokeplan.app.MemoryStorage
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws the app offscreen and writes PNGs to composeApp/build/screenshots, so screens can be looked at without a device.
 * The assertion only checks that something was drawn.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private fun render(name: String, storage: MemoryStorage, steps: (ImageComposeScene) -> Unit = {}) {
        val scene = ImageComposeScene(width = 540, height = 1080, density = Density(1.5f)) { App(storage, focusTab = true) }
        var nanos = 0L
        // Sprites load asynchronously, so draw a few frames with a short wait between them.
        repeat(40) {
            scene.render(nanos)
            nanos += 50_000_000
            Thread.sleep(25)
        }
        steps(scene)
        repeat(40) {
            scene.render(nanos)
            nanos += 50_000_000
            Thread.sleep(25)
        }
        val image = scene.render(nanos)
        val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        val out = File("build/screenshots/$name.png")
        out.parentFile.mkdirs()
        out.writeBytes(bytes)
        assertTrue(bytes.size > 5_000, "$name drew almost nothing")
        scene.close()
    }

    @Test
    fun onboarding() = render("onboarding", MemoryStorage())

    @Test
    fun home() {
        val storage = MemoryStorage()
        app.pokeplan.app.GameStore(storage, app.pokeplan.app.Clock { System.currentTimeMillis() }).start("Sophia", 25)
        render("home", storage)
    }
}
