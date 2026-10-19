package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * The page behind every screen: a soft pixel-dithered gradient whose colours follow the time of day (peach at dawn, mint in
 * the morning, warm cream in the afternoon, rose at dusk, blue-grey at night). The tones stay light so the dark text on it
 * is always readable; only the mood changes. In dark mode the same hues are pulled down to deep navy so the light text is readable. Drawn once as a tiny bitmap of coarse "pixels" and scaled up without smoothing.
 */
@Composable
fun DayBackground(hour: Int, minute: Int, modifier: Modifier = Modifier, dark: Boolean = false) {
    val (top, bottom) = DayPalette.at(hour + minute / 60f, dark)
    val cell = with(LocalDensity.current) { 4.dp.toPx() }
    Canvas(modifier.fillMaxSize()) {
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val bitmap = dithered(cols, rows, top, bottom)
        drawImage(bitmap, dstSize = IntSize((cols * cell).toInt(), (rows * cell).toInt()), filterQuality = FilterQuality.None)
    }
}

private val bayer = arrayOf(intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6), intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5))

private val cache = HashMap<String, ImageBitmap>()

/** A [cols] x [rows] picture that dithers from [top] to [bottom] with an ordered (Bayer) pattern. */
private fun dithered(cols: Int, rows: Int, top: Color, bottom: Color): ImageBitmap {
    // Colours move slowly through the day, so round them to keep a handful of pictures instead of one per minute.
    val key = "$cols:$rows:${top.toArgb8()}:${bottom.toArgb8()}"
    cache[key]?.let { return it }
    if (cache.size > 6) cache.clear()
    val bitmap = ImageBitmap(cols, rows)
    val canvas = ComposeCanvas(bitmap)
    val paintTop = Paint().apply { color = top; isAntiAlias = false }
    val paintBottom = Paint().apply { color = bottom; isAntiAlias = false }
    // A faint grain: now and then a "pixel" a shade darker, in the same hue, so even the flat areas have texture.
    val grainTop = Paint().apply { color = shade(top); isAntiAlias = false }
    val grainBottom = Paint().apply { color = shade(bottom); isAntiAlias = false }
    for (y in 0 until rows) {
        val t = y / rows.toFloat()
        for (x in 0 until cols) {
            val threshold = (bayer[y % 4][x % 4] + 0.5f) / 16f
            val lower = t > threshold * 0.9f + 0.05f
            val grain = ((x * 73856093) xor (y * 19349663)).let { (it ushr 3) % 11 == 0 }
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, if (lower) (if (grain) grainBottom else paintBottom) else (if (grain) grainTop else paintTop))
        }
    }
    cache[key] = bitmap
    return bitmap
}

private fun shade(c: Color) = Color(c.red * 0.955f, c.green * 0.955f, c.blue * 0.955f)

private fun Color.toArgb8(): Int = ((red * 31).toInt() shl 10) or ((green * 31).toInt() shl 5) or (blue * 31).toInt()

/** The page colours through the day: (top, bottom) at each key hour, blended between them. Always light. */
internal object DayPalette {
    private data class Key(val hour: Float, val top: Color, val bottom: Color)

    private val keys = listOf(
        Key(0f, Color(0xFFD3D9EE), Color(0xFFE8EBF6)),   // night
        Key(5f, Color(0xFFD3D9EE), Color(0xFFE8EBF6)),
        Key(7f, Color(0xFFF5D8C8), Color(0xFFF7EEE4)),   // dawn
        Key(10f, Color(0xFFDCEFE6), Color(0xFFF3F5EE)),  // morning
        Key(14f, Color(0xFFF6EED6), Color(0xFFF6F3EA)),  // afternoon
        Key(18f, Color(0xFFF2E0CE), Color(0xFFF5EFE6)),
        Key(20f, Color(0xFFF0CFD2), Color(0xFFF3EAE6)),  // dusk
        Key(22f, Color(0xFFD3D9EE), Color(0xFFE8EBF6)),
        Key(24f, Color(0xFFD3D9EE), Color(0xFFE8EBF6)),
    )

    private val night = Color(0xFF12131D)

    fun at(hour: Float, dark: Boolean = false): Pair<Color, Color> {
        val (top, bottom) = lightAt(hour)
        // Keep a trace of the hour's hue so the day still shows, at about an eighth of the light strength.
        return if (dark) lerp(night, top, 0.12f) to lerp(night, bottom, 0.1f) else top to bottom
    }

    private fun lightAt(hour: Float): Pair<Color, Color> {
        val h = hour.coerceIn(0f, 24f)
        val i = keys.indexOfLast { it.hour <= h }.coerceAtMost(keys.size - 2)
        val a = keys[i]
        val b = keys[i + 1]
        val t = if (b.hour == a.hour) 0f else floor(((h - a.hour) / (b.hour - a.hour)) * 20f) / 20f
        return lerp(a.top, b.top, t) to lerp(a.bottom, b.bottom, t)
    }

    private fun lerp(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
}
