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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res

/**
 * The Mystery Dungeon status icons (assets/status, cut by tools/sprites/cut_status.py), each a strip of 16 px frames:
 * the "!" over a startled Pokémon, the "?" of a curious or confused one, the red X of a hit or a quarrel, the Zz of sleep.
 * [loop] is the frames repeated from the first; [start] is where a looping one begins (the Zz grows before it settles).
 */
enum class StatusKind(val file: String, val frames: Int, val start: Int = 0, val millis: Long = 110, val cell: Int = 16) {
    ALERT("alert", 9), QUESTION("question", 10), CROSS("cross", 9), SLEEP("sleep", 9, start = 3, millis = 220), SKULL("skull", 8),
    /** The yellow flash of a shock, the blue drop of a nervous one and a white sparkle for something nice (24 px, 16 px and 24 px frames). */
    ZAP("zap", 2, millis = 140, cell = 24), SWEAT("sweat", 2, millis = 300), TWINKLE("twinkle", 3, millis = 160, cell = 24),
}

private val strips = mutableMapOf<StatusKind, ImageBitmap?>()

/**
 * One status icon at a whole-number [pixel] scale (screen pixels per art pixel), without smoothing. It plays its frames
 * once and holds the last, or with [loop] cycles them; with [animate] off it shows one still frame (Reduce motion).
 */
@Composable
fun StatusIcon(kind: StatusKind, pixel: Int, modifier: Modifier = Modifier, loop: Boolean = false, animate: Boolean = true) {
    val strip by produceState(strips[kind], kind) {
        if (value == null) {
            value = orNull { Res.readBytes("files/status/${kind.file}.png").decodeToImageBitmap() }
            strips[kind] = value
        }
    }
    var frame by remember(kind) { mutableIntStateOf(if (loop) kind.start else 0) }
    if (animate) LaunchedEffect(kind, loop) {
        while (true) {
            delay(kind.millis)
            frame = if (loop) (if (frame + 1 >= kind.frames) kind.start else frame + 1) else minOf(frame + 1, kind.frames - 1)
        }
    }
    val cell = kind.cell
    val dp = pixel * cell / LocalDensity.current.density
    Canvas(modifier.size(dp.dp)) {
        val image = strip ?: return@Canvas
        val shown = if (animate) frame else if (loop) kind.frames - 1 else 0
        drawImage(image, srcOffset = IntOffset(shown * cell, 0), srcSize = IntSize(cell, cell), dstSize = IntSize(cell * pixel, cell * pixel), filterQuality = FilterQuality.None)
    }
}
