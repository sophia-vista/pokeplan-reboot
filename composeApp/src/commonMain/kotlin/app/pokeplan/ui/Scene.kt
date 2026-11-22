package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import app.pokeplan.ui.screens.LocalReduceMotion
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
import app.pokeplan.ui.theme.Pixel
import kotlin.math.max

private val scenes = BitmapCache(CacheBudget.SCENES)

@Composable
internal fun rememberFadeIn(ready: Boolean): State<Float> {
    val calm = LocalReduceMotion.current
    val fade = remember { Animatable(if (ready || calm) 1f else 0f) }
    LaunchedEffect(ready) {
        if (ready) { if (calm) fade.snapTo(1f) else fade.animateTo(1f, tween(150)) }
    }
    return fade.asState()
}

internal fun isGroundMap(name: String): Boolean = app.pokeplan.ui.launcher.FriendAreas.isArea(name) || name.startsWith("map:")

@Composable
internal fun rememberScene(name: String): ImageBitmap? {
    val image by produceState(scenes[name], name) {
        value = scenes[name]
        if (value == null) {
            val path = when {
                app.pokeplan.ui.launcher.FriendAreas.isArea(name) -> app.pokeplan.ui.launcher.FriendAreas.file(name)
                name.startsWith("map:") -> "files/maps/${name.removePrefix("map:")}.png"
                else -> "files/scenes/$name.png"
            }
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            scenes[name] = value
        }
    }
    return image
}

@Composable
fun SceneImage(name: String, modifier: Modifier = Modifier, darkness: Float = 0f, crisp: Boolean = false) {
    val image = rememberScene(name)
    val fade by rememberFadeIn(image != null)
    Canvas(modifier) {
        val scene = image ?: return@Canvas drawRect(Color(0xFF8FCB7F))
        val cover = max(size.width / scene.width, size.height / scene.height)
        if (crisp) {
            val whole = kotlin.math.ceil(cover).toInt().coerceAtLeast(1)
            val srcW = kotlin.math.ceil(size.width / whole).toInt().coerceAtMost(scene.width)
            val srcH = kotlin.math.ceil(size.height / whole).toInt().coerceAtMost(scene.height)
            drawImage(
                scene,
                srcOffset = IntOffset((scene.width - srcW) / 2, scene.height - srcH), srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(0, size.height.toInt() - srcH * whole), dstSize = IntSize(srcW * whole, srcH * whole), alpha = fade, filterQuality = FilterQuality.None,
            )
        } else {
            val srcW = (size.width / cover).toInt().coerceAtMost(scene.width)
            val srcH = (size.height / cover).toInt().coerceAtMost(scene.height)
            drawImage(
                scene,
                srcOffset = IntOffset((scene.width - srcW) / 2, scene.height - srcH), srcSize = IntSize(srcW, srcH),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = fade, filterQuality = FilterQuality.Medium,
            )
        }
        if (darkness > 0f) drawRect(Pixel.Night.copy(alpha = darkness.coerceIn(0f, 1f)), Offset.Zero, Size(size.width, size.height))
    }
}
