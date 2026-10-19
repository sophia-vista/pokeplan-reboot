package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.min

/** One animated sprite: [frames] side by side in a sheet of [width] x [height] frames, each shown for the matching entry of [times] (ms). */
private class Animation(val frames: Int, val width: Int, val height: Int, val times: IntArray, val hasShiny: Boolean)

private var animations: Map<Int, Animation>? = null
private val sheets = mutableMapOf<String, ImageBitmap?>()

/** assets/animated/animated.tsv: `id, frames, width, height, comma separated frame times, shiny`. Read once. */
private suspend fun animations(): Map<Int, Animation> {
    animations?.let { return it }
    val parsed = try {
        Res.readBytes("files/animated/animated.tsv").decodeToString().lines().filter { it.isNotBlank() }.associate { line ->
            val c = line.split('\t')
            c[0].toInt() to Animation(c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray(), c[5] == "1")
        }
    } catch (e: Exception) {
        emptyMap()
    }
    animations = parsed
    return parsed
}

/**
 * A Pokémon with its real Black/White animation, drawn at a whole-number scale so the pixels stay sharp, standing on the
 * bottom edge of the [size] box ([ground] up from it). Species without an animation (above Gen 5, and alternate forms)
 * show the still [Sprite]. With [animate] off it shows the first frame, for "Reduce motion".
 */
@Composable
fun AnimatedSprite(
    formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 144.dp,
    animate: Boolean = true, ground: Dp = 12.dp,
) {
    val animation by produceState<Animation?>(null, formId) { value = animations()[formId] }
    val a = animation
    val name = if (a != null && shiny && a.hasShiny) "$formId-shiny" else "$formId"
    val sheet by produceState(sheets[name], name, a != null) {
        if (value == null && a != null) {
            value = try {
                Res.readBytes("files/animated/$name.png").decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            sheets[name] = value
        }
    }
    val image = sheet
    if (a == null || image == null) {
        Sprite(formId, shiny, description, modifier, size)
        return
    }
    var frame by remember(formId) { mutableIntStateOf(0) }
    LaunchedEffect(a, animate) {
        if (!animate) {
            frame = 0
            return@LaunchedEffect
        }
        var i = 0
        while (true) {
            frame = i
            delay(a.times[i].toLong())
            i = (i + 1) % a.frames
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val box = min(this.size.width, this.size.height - ground.toPx())
        val scale = min(box / a.width, box / a.height).toInt().coerceAtLeast(1)
        val w = a.width * scale
        val h = a.height * scale
        drawImage(
            image,
            srcOffset = IntOffset(frame * a.width, 0), srcSize = IntSize(a.width, a.height),
            dstOffset = IntOffset(((this.size.width - w) / 2).toInt(), (this.size.height - ground.toPx() - h).toInt()), dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
}
