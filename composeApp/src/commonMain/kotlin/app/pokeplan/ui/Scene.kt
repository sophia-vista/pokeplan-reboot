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

private val scenes = mutableMapOf<String, ImageBitmap?>()

/** 0 until [ready], then fades to 1 over 150 ms (at once under Reduce motion), so a picture arriving late does not pop in. */
@Composable
internal fun rememberFadeIn(ready: Boolean): State<Float> {
    val calm = LocalReduceMotion.current
    val fade = remember { Animatable(if (ready || calm) 1f else 0f) }
    LaunchedEffect(ready) {
        if (ready) { if (calm) fade.snapTo(1f) else fade.animateTo(1f, tween(150)) }
    }
    return fade.asState()
}

/** True for a background that is a ground map of the Mystery Dungeon games or a Friend Area (pixel art with a grid of its own), not a stock scene. */
internal fun isGroundMap(name: String): Boolean = app.pokeplan.ui.launcher.FriendAreas.isArea(name) || name.startsWith("map:")

/** The picture of a scene, a ground map or a Friend Area by its name (null while it is read or if it is missing), kept once read. */
@Composable
internal fun rememberScene(name: String): ImageBitmap? {
    val image by produceState(scenes[name], name) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = scenes[name]
        if (value == null) {
            // A Friend Area ("fa-..."), a ground map of the Mystery Dungeon games the story uses ("map:..."), else a stock scene.
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

/**
 * A real Pokémon scene (assets/scenes, "meadow", "mountain"...) filling the box like a photo: scaled to cover it and
 * anchored at the bottom, where the ground is. With [darkness] it is dimmed with a cool tint. With [crisp] it is scaled by a
 * whole number (the next one up that covers the box) and drawn without smoothing, so its pixels stay square and sharp.
 */
@Composable
fun SceneImage(name: String, modifier: Modifier = Modifier, darkness: Float = 0f, crisp: Boolean = false) {
    val image = rememberScene(name)
    val fade by rememberFadeIn(image != null)
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
