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

internal class WeatherRamp(val red: IntArray, val green: IntArray, val blue: IntArray) {
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

internal suspend fun weatherRamps(): Map<String, WeatherRamp> =
    ramps ?: (orNull { WeatherRamp.parse(Res.readBytes("files/weather/ramps.tsv").decodeToString()) } ?: emptyMap()).also { ramps = it }

internal suspend fun gradedFor(name: String, atlas: ImageBitmap, weather: Weather): ImageBitmap {
    val ramp = weather.rampName()?.let { weatherRamps()[it] } ?: return atlas
    val key = "$name|${weather.rampName()}"
    if (key in graded) return graded[key] ?: atlas
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { atlas.graded(ramp) }.also { graded[key] = it }
}

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
