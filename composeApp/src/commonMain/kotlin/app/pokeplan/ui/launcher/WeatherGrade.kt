package app.pokeplan.ui.launcher

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import app.pokeplan.ui.BitmapCache
import app.pokeplan.ui.CacheBudget
import app.pokeplan.ui.orNull
import pokeplan.composeapp.generated.resources.Res

/**
 * The colour grading of the dungeon's weather (pret/pmd-red `src/weather.c`, `src/bg_palette_buffer.c`, data/dungeon/colvec.inc, extracted to `assets/weather/ramps.tsv`
 * by `tools/weather/extract_ramps.py`): every colour of the dungeon's tile palettes goes through a table of 256 entries for each of red, green and blue that belongs to the
 * weather. Sandstorm lifts red and green and crushes blue (a yellow-brown light), rain sinks the reds and greens and lifts the blues, cloudy cools everything and darkens the
 * blacks, hail and snow cool the picture, fog lifts the blacks and takes the whites down (a flat grey). Where the town is made of a dungeon's tiles it is graded the same way.
 */
internal class WeatherRamp(val red: IntArray, val green: IntArray, val blue: IntArray) {
    /**
     * [argb] as the game would show it: a colour of 5 bits a channel is `v * 8 + 7` in the game's palettes (which is what the ramp is indexed by) and `v * 255 / 31` in our
     * tile pictures; the result is `ramp[...] * 31 / 256` as 5 bits again (the brightness is at its full 31), shown as 8. The alpha stays.
     */
    fun grade(argb: Int): Int {
        val a = argb ushr 24
        if (a == 0) return argb
        return (a shl 24) or (channel((argb shr 16) and 0xFF, red) shl 16) or (channel((argb shr 8) and 0xFF, green) shl 8) or channel(argb and 0xFF, blue)
    }

    private fun channel(value: Int, table: IntArray): Int {
        val five = (value * 31 + 127) / 255
        val out = (table[five * 8 + 7] * 31 / 256) and 0x1F
        return out * 255 / 31
    }

    companion object {
        /** The ramps of [text] (`NAME<TAB>r|g|b<TAB>256 numbers` a line, see `ramps.tsv`) by weather name. */
        fun parse(text: String): Map<String, WeatherRamp> {
            val channels = mutableMapOf<String, MutableMap<String, IntArray>>()
            for (line in text.lineSequence()) {
                val f = line.split('\t')
                if (f.size != 3) continue
                val values = f[2].split(',').map { it.trim().toInt() }
                require(values.size == 256) { "a ramp has 256 entries" }
                channels.getOrPut(f[0]) { mutableMapOf() }[f[1]] = values.toIntArray()
            }
            return channels.mapValues { (_, c) -> WeatherRamp(c.getValue("r"), c.getValue("g"), c.getValue("b")) }
        }
    }
}

/** The game's weather the town's [Weather] is graded like, or null for one the game's ramps do not cover (a clear sky is graded by nothing). */
internal fun Weather.rampName(): String? = when (this) {
    Weather.RAIN -> "RAIN"
    Weather.SNOW -> "SNOW"
    Weather.BLIZZARD -> "HAIL"
    Weather.SANDSTORM -> "SANDSTORM"
    Weather.CLOUDS -> "CLOUDY"
    else -> null
}

private var ramps: Map<String, WeatherRamp>? = null
private val graded = BitmapCache(CacheBudget.TILES)

/** The game's ramps, read once. */
internal suspend fun weatherRamps(): Map<String, WeatherRamp> =
    ramps ?: (orNull { WeatherRamp.parse(Res.readBytes("files/weather/ramps.tsv").decodeToString()) } ?: emptyMap()).also { ramps = it }

/** [atlas] (named [name]) graded for [weather], or itself when the weather has no ramp; made once for each pair. */
internal suspend fun gradedFor(name: String, atlas: ImageBitmap, weather: Weather): ImageBitmap {
    val ramp = weather.rampName()?.let { weatherRamps()[it] } ?: return atlas
    val key = "$name|${weather.rampName()}"
    if (key in graded) return graded[key] ?: atlas
    return atlas.graded(ramp).also { graded[key] = it }
}

/**
 * A copy of this picture with every colour sent through [ramp]. Every pixel of one colour is drawn with one call (a tileset has a few dozen colours), so it needs no
 * pixel buffer to write: it reads the pixels and draws points.
 */
internal fun ImageBitmap.graded(ramp: WeatherRamp): ImageBitmap {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    readPixels(pixels, 0, 0, w, h)
    val byColor = HashMap<Int, ArrayList<Float>>()
    for (i in pixels.indices) {
        val c = pixels[i]
        if (c ushr 24 == 0) continue
        val list = byColor.getOrPut(c) { ArrayList() }
        list.add(i % w + 0.5f)
        list.add(i / w + 0.5f)
    }
    val out = ImageBitmap(w, h)
    val canvas = Canvas(out)
    val paint = Paint().apply { strokeWidth = 1f; strokeCap = StrokeCap.Square; isAntiAlias = false }
    for ((c, points) in byColor) {
        paint.color = Color(ramp.grade(c))
        canvas.drawRawPoints(PointMode.Points, points.toFloatArray(), paint)
    }
    return out
}
