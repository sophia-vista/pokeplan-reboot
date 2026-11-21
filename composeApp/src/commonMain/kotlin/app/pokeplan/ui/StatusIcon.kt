package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

enum class StatusKind(val file: String, val frames: Int, val start: Int = 0, val millis: Long = 110, val cell: Int = 16) {
    ALERT("alert", 9), QUESTION("question", 10), CROSS("cross", 9), SLEEP("sleep", 9, start = 3, millis = 220),
    ZAP("zap", 2, millis = 140, cell = 24), SWEAT("sweat", 2, millis = 300), TWINKLE("twinkle", 3, millis = 160, cell = 24),
    SKULL("skull", 8, millis = 140),
    FLAME("flame", 7), SHIELD("shield", 13), FIST("fist", 8, millis = 130), ICE("ice", 6, millis = 160, cell = 32),
}

private val strips = mutableMapOf<StatusKind, ImageBitmap?>()

@Composable
fun StatusIcon(kind: StatusKind, pixel: Int, modifier: Modifier = Modifier, loop: Boolean = false, animate: Boolean = true) {
    val strip = rememberCachedImage(strips, kind) { orNull { Res.readBytes("files/status/${kind.file}.png").decodeToImageBitmap() } }
    var frame by remember(kind) { mutableIntStateOf(if (loop) kind.start else 0) }
    // A one-shot icon holds its last frame and stops; a looping one also stops while the app is out of sight.
    val foreground = rememberInForeground()
    if (animate) LaunchedEffect(kind, loop, foreground) {
        if (!foreground) return@LaunchedEffect
        frame = if (loop) kind.start else 0
        while (true) {
            if (!loop && frame >= kind.frames - 1) break
            delay(kind.millis)
            frame = if (loop) (if (frame + 1 >= kind.frames) kind.start else frame + 1) else (frame + 1).coerceAtMost(kind.frames - 1)
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
