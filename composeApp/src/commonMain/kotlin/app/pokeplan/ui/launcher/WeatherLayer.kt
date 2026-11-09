package app.pokeplan.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.pokeplan.ui.orNull
import app.pokeplan.ui.CacheBudget
import app.pokeplan.ui.BitmapCache
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.ceil

private val sheets = BitmapCache(CacheBudget.SMALL)

@Composable
private fun weatherSheet(name: String): ImageBitmap? {
    val sheet by produceState(sheets[name], name) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = sheets[name]
        if (value == null) {
            value = orNull { Res.readBytes("files/weather/$name.png").decodeToImageBitmap() }
            sheets[name] = value
        }
    }
    return sheet
}

/**
 * Weather over the town, drawn from the real Mystery Dungeon effects (assets/weather, cut by tools/sprites/cut_weather.py)
 * at the town's whole-number [pixel] scale, without smoothing: rain streaks and splashes, snow and blizzard tiles, scrolling
 * sand and the shadows of drifting clouds. It never takes a touch. With [calm] (Reduce motion, or the launcher out of
 * sight) it shows one still frame.
 */
@Composable
internal fun WeatherLayer(weather: Weather, pixel: Int, dungeon: Boolean, calm: Boolean, modifier: Modifier = Modifier) {
    if (weather == Weather.CLEAR) return
    val rain = weatherSheet("rain")
    val snow = weatherSheet(if (dungeon) "snow-dungeon" else "snow")
    val blizzard = weatherSheet("blizzard")
    val sand1 = weatherSheet("sand-1")
    val sand2 = weatherSheet("sand-2")
    val clouds = weatherSheet("clouds")
    var now by remember { mutableLongStateOf(0L) }
    val foreground = app.pokeplan.ui.rememberInForeground()
    if (!calm) LaunchedEffect(weather, foreground) { while (foreground) withFrameMillis { now = it } }
    Canvas(modifier) {
        val t = if (calm) 0L else now
        when (weather) {
            Weather.RAIN -> rain?.let { tile(it, pixel, 240, 160, (t / 70 % 61).toInt(), 5) }
            Weather.SNOW -> snow?.let { if (dungeon) tile(it, pixel, 32, 32, (t / 220 % 16).toInt(), 4) else tile(it, pixel, 64, 32, (t / 220 % 16).toInt(), 4) }
            Weather.BLIZZARD -> {
                drawRect(Color.White.copy(alpha = 0.1f), Offset.Zero, size)
                blizzard?.let { tile(it, pixel, 32, 32, (t / 80 % 16).toInt(), 4) }
            }
            Weather.SANDSTORM -> {
                drawRect(Color(0xFFC89040).copy(alpha = 0.16f), Offset.Zero, size)
                sand1?.let { scrolling(it, pixel, t, 18f, 0.4f) }
                sand2?.let { scrolling(it, pixel, t, 9f, 0.3f) }
            }
            Weather.CLOUDS -> clouds?.let { shadows(it, pixel, t) }
            Weather.CLEAR, Weather.LIVE -> Unit
        }
    }
}

/** Frame [index] of a [cols]-wide sheet of [w] x [h] frames, repeated to cover the canvas. */
private fun DrawScope.tile(image: ImageBitmap, pixel: Int, w: Int, h: Int, index: Int, cols: Int) {
    val src = IntOffset(index % cols * w, index / cols * h)
    val tw = w * pixel
    val th = h * pixel
    for (y in 0 until ceil(size.height / th).toInt()) for (x in 0 until ceil(size.width / tw).toInt()) {
        drawImage(image, src, IntSize(w, h), IntOffset(x * tw, y * th), IntSize(tw, th), filterQuality = FilterQuality.None)
    }
}

/** A texture sliding sideways at [speed] art pixels a second and repeated to cover the canvas. */
private fun DrawScope.scrolling(image: ImageBitmap, pixel: Int, t: Long, speed: Float, alpha: Float) {
    val tw = image.width * pixel
    val th = image.height * pixel
    val shift = ((t / 1000f * speed * pixel) % tw).toInt() / pixel * pixel
    for (y in 0 until ceil(size.height / th).toInt()) for (x in -1 until ceil(size.width / tw).toInt()) {
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(x * tw + shift, y * th), IntSize(tw, th), alpha = alpha, filterQuality = FilterQuality.None)
    }
}

/** The cloud shadows, faint and slow, drifting across and wrapping round. */
private fun DrawScope.shadows(image: ImageBitmap, pixel: Int, t: Long) {
    val tw = image.width * pixel
    val th = image.height * pixel
    val shift = ((t / 1000f * 6f * pixel) % tw).toInt() / pixel * pixel
    for (y in 0 until ceil(size.height / th).toInt()) for (x in -1 until ceil(size.width / tw).toInt()) {
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(x * tw + shift, y * th), IntSize(tw, th), alpha = 0.16f, filterQuality = FilterQuality.None)
    }
}
