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
 * anchored at the bottom, where the ground is. With [darkness] it is dimmed with a cool tint. With [crisp] it is scaled by a
 * whole number (the next one up that covers the box) and drawn without smoothing, so its pixels stay square and sharp.
 */
@Composable
fun SceneImage(name: String, modifier: Modifier = Modifier, darkness: Float = 0f, crisp: Boolean = false) {
    val image by produceState(scenes[name], name) {
        if (value == null) {
            value = orNull { Res.readBytes("files/scenes/$name.png").decodeToImageBitmap() }
            scenes[name] = value
        }
    }
    Canvas(modifier) {
        val scene = image ?: return@Canvas drawRect(Color(0xFF8FCB7F))
        // Cover: the largest scale that leaves no gap, then show the bottom, left-centred part of the picture.
        val cover = max(size.width / scene.width, size.height / scene.height)
        if (crisp) {
            val whole = kotlin.math.ceil(cover).toInt().coerceAtLeast(1)
            val srcW = kotlin.math.ceil(size.width / whole).toInt().coerceAtMost(scene.width)
            val srcH = kotlin.math.ceil(size.height / whole).toInt().coerceAtMost(scene.height)
            drawImage(
                scene,
                srcOffset = IntOffset((scene.width - srcW) / 2, scene.height - srcH), srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(0, size.height.toInt() - srcH * whole), dstSize = IntSize(srcW * whole, srcH * whole), filterQuality = FilterQuality.None,
            )
        } else {
            val srcW = (size.width / cover).toInt().coerceAtMost(scene.width)
            val srcH = (size.height / cover).toInt().coerceAtMost(scene.height)
            drawImage(
                scene,
                srcOffset = IntOffset((scene.width - srcW) / 2, scene.height - srcH), srcSize = IntSize(srcW, srcH),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium,
            )
        }
        if (darkness > 0f) drawRect(Color(0xFF101C3C).copy(alpha = darkness.coerceIn(0f, 1f)), Offset.Zero, Size(size.width, size.height))
    }
}
