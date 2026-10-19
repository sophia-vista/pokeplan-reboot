package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.max

private val scenes = mutableMapOf<String, ImageBitmap?>()

/**
 * A real Pokémon scene (assets/scenes, "meadow", "mountain"...) filling the box like a photo: scaled to cover it and
 * anchored at the bottom, where the ground is. With [night] it is dimmed with a cool tint.
 */
@Composable
fun SceneImage(name: String, modifier: Modifier = Modifier, night: Boolean = false) {
    val image by produceState(scenes[name], name) {
        if (value == null) {
            value = try {
                Res.readBytes("files/scenes/$name.png").decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            scenes[name] = value
        }
    }
    Canvas(modifier) {
        val scene = image ?: return@Canvas drawRect(Color(0xFF8FCB7F))
        // Cover: the largest scale that leaves no gap, then show the bottom, left-centred part of the picture.
        val scale = max(size.width / scene.width, size.height / scene.height)
        val srcW = (size.width / scale).toInt().coerceAtMost(scene.width)
        val srcH = (size.height / scale).toInt().coerceAtMost(scene.height)
        drawImage(
            scene,
            srcOffset = IntOffset((scene.width - srcW) / 2, scene.height - srcH), srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium,
        )
        if (night) drawRect(Color(0x99101C3C), Offset.Zero, Size(size.width, size.height))
    }
}
