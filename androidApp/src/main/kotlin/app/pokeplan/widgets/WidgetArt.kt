package app.pokeplan.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * Bitmaps for the widgets, all made from the app's real assets (sprites, items, box wallpapers). Widgets cannot use the
 * Compose renderer, so the tiling and the rounded corners are done here with a Canvas. Nothing here draws pixel art;
 * the only shape drawn in code is the small shadow under the buddy, which is UI chrome.
 */
object WidgetArt {
    private val cache = LinkedHashMap<String, Bitmap>()

    private const val RESOURCES = "composeResources/pokeplan.composeapp.generated.resources/files"

    private fun asset(context: Context, path: String): Bitmap? = try {
        context.assets.open("$RESOURCES/$path").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private fun remember(key: String, make: () -> Bitmap?): Bitmap? {
        cache[key]?.let { return it }
        val bitmap = make() ?: return null
        // Widget bitmaps are small; keep the few that are asked for again and again.
        if (cache.size > 24) cache.remove(cache.keys.first())
        cache[key] = bitmap
        return bitmap
    }

    /**
     * A box wallpaper tiled and mirrored (like the app's PixelBackground) to [width] x [height] pixels, with its two
     * pixel frame trimmed, scaled by a whole number so the pixels stay sharp, and rounded corners. [roundBottom] false
     * rounds only the top (for a header strip).
     */
    fun wallpaper(context: Context, name: String, width: Int, height: Int, radius: Float, density: Float, roundBottom: Boolean = true): Bitmap? =
        remember("wall:$name:$width:$height:$radius:$roundBottom:$density") {
            val source = asset(context, "backgrounds/$name.png") ?: return@remember null
            val inner = Bitmap.createBitmap(source, FRAME, FRAME, source.width - 2 * FRAME, source.height - 2 * FRAME)
            val step = (3 * density).roundToInt().coerceAtLeast(1)
            val tileW = inner.width * step
            val tileH = inner.height * step
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.clipPath(roundedPath(width.toFloat(), height.toFloat(), radius, roundBottom))
            val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
            var row = 0
            var y = 0
            while (y < height) {
                var column = 0
                var x = 0
                while (x < width) {
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    canvas.scale(if (column % 2 == 0) 1f else -1f, if (row % 2 == 0) 1f else -1f, tileW / 2f, tileH / 2f)
                    canvas.drawBitmap(inner, null, RectF(0f, 0f, tileW.toFloat(), tileH.toFloat()), paint)
                    canvas.restore()
                    x += tileW
                    column++
                }
                y += tileH
                row++
            }
            out
        }

    private fun roundedPath(width: Float, height: Float, radius: Float, roundBottom: Boolean) = Path().apply {
        val bottom = if (roundBottom) radius else 0f
        addRoundRect(RectF(0f, 0f, width, height), floatArrayOf(radius, radius, radius, radius, bottom, bottom, bottom, bottom), Path.Direction.CW)
    }

    /** A real scene (assets/scenes) filling [width] x [height] like a photo, bottom-anchored, rounded, dimmed at night. */
    fun scene(context: Context, name: String, darkness: Float, width: Int, height: Int, radius: Float, roundBottom: Boolean = true): Bitmap? {
        // The tint is quantized so a slowly changing dusk does not build a new picture every minute.
        val tint = (darkness.coerceIn(0f, 1f) * 20).toInt()
        return remember("scene:$name:$tint:$width:$height:$radius:$roundBottom") {
            val source = asset(context, "scenes/$name.png") ?: return@remember null
            val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
            val srcW = (width / scale).toInt().coerceAtMost(source.width)
            val srcH = (height / scale).toInt().coerceAtMost(source.height)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.clipPath(roundedPath(width.toFloat(), height.toFloat(), radius, roundBottom))
            canvas.drawBitmap(
                source, android.graphics.Rect((source.width - srcW) / 2, source.height - srcH, (source.width - srcW) / 2 + srcW, source.height),
                RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { isFilterBitmap = true },
            )
            if (tint > 0) canvas.drawColor(Color.argb((tint / 20f * 255).toInt(), 0x10, 0x1C, 0x3C))
            out
        }
    }

    private const val CANVAS = 192

    /** One square picture of the buddy standing on a soft shadow, bottom-centred; [sprite] is enlarged by [scale] without smoothing. */
    private fun standing(sprite: Bitmap, scale: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(sprite, sprite.width * scale, sprite.height * scale, false)
        val out = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // A small shadow of a few flat rows, so it matches the pixels of the sprite.
        val shadow = Paint().apply { color = Color.argb(70, 0, 0, 0); isAntiAlias = false }
        val rows = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
        val rowHeight = 4f
        rows.forEachIndexed { i, share ->
            val width = CANVAS * 0.34f * share
            val top = CANVAS - 6f - rowHeight * (rows.size - i)
            canvas.drawRect(CANVAS / 2f - width / 2, top, CANVAS / 2f + width / 2, top + rowHeight, shadow)
        }
        canvas.drawBitmap(scaled, (CANVAS - scaled.width) / 2f, CANVAS - scaled.height - 12f, Paint().apply { isFilterBitmap = false })
        return out
    }

    /** A still picture of the buddy: the first frame of its Mystery Dungeon style sprite, else its regular sprite. */
    fun still(context: Context, formId: Int, shiny: Boolean): Bitmap? =
        remember("still:$formId:$shiny") {
            pmdFrames(context, formId, shiny)?.first()?.let { return@remember it }
            val sprite = asset(context, "sprites/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: return@remember null
            standing(withoutEmptyRowsBelow(sprite), 2)
        }

    private fun pmdFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? =
        animation(context, formId)?.takeIf { it.dir == "pmd" }?.let { idleFrames(context, formId, shiny) }

    /** The regular sprites have empty rows under the feet; without them the buddy stands on its shadow, as the animated frames do. */
    private fun withoutEmptyRowsBelow(sprite: Bitmap): Bitmap {
        val row = IntArray(sprite.width)
        var bottom = sprite.height
        while (bottom > 1) {
            sprite.getPixels(row, 0, sprite.width, 0, bottom - 1, sprite.width, 1)
            if (row.any { it ushr 24 != 0 }) break
            bottom--
        }
        return if (bottom == sprite.height) sprite else Bitmap.createBitmap(sprite, 0, 0, sprite.width, bottom)
    }

    private class Sheet(val dir: String, val width: Int, val height: Int, val times: IntArray)

    private var animations: Map<Int, Sheet>? = null

    /** A sheet table (`id, frames, width, height, frame times in ms, shiny`) as id to sheet in [dir]. */
    private fun table(context: Context, dir: String, file: String): Map<Int, Sheet> = try {
        context.assets.open("$RESOURCES/$dir/$file").bufferedReader().readLines().filter { it.isNotBlank() }.associate { line ->
            val c = line.split('\t')
            c[0].toInt() to Sheet(dir, c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray())
        }
    } catch (e: Exception) {
        emptyMap()
    }

    /** The form's animation: the Mystery Dungeon style one (assets/pmd) where there is one, else the Black/White one. */
    private fun animation(context: Context, id: Int): Sheet? {
        if (animations == null) animations = table(context, "animated", "animated.tsv") + table(context, "pmd", "pmd.tsv")
        return animations?.get(id)
    }

    /**
     * The buddy's real animation as at most [MAX_FRAMES] pictures spread evenly over one loop, each [FLIP_MILLIS] long
     * when played by the widget's flipper, or null for a form without an animation.
     */
    fun idleFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? {
        val a = animation(context, formId) ?: return null
        val (w, h, times) = Triple(a.width, a.height, a.times)
        val sheet = asset(context, "${a.dir}/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: asset(context, "${a.dir}/$formId.png") ?: return null
        val total = times.sum()
        val count = minOf(MAX_FRAMES, (total + FLIP_MILLIS - 1) / FLIP_MILLIS).coerceAtLeast(1)
        // Mystery Dungeon sprites share one pixel size (a 32 pixel frame fills the canvas), as in the app.
        val fit = (CANVAS - 12) / maxOf(w, h)
        val scale = (if (a.dir == "pmd") minOf(fit, (CANVAS - 12) / 32) else fit).coerceAtLeast(1)
        return (0 until count).map { j ->
            val at = (j.toLong() * total / count).toInt()
            var index = 0
            var elapsed = 0
            while (index < times.size - 1 && elapsed + times[index] <= at) { elapsed += times[index]; index++ }
            standing(Bitmap.createBitmap(sheet, index * w, 0, w, h), scale)
        }
    }

    const val FLIP_MILLIS = 200
    private const val MAX_FRAMES = 20

    /** An item icon (a berry, the egg) enlarged by a whole number without smoothing. */
    fun item(context: Context, id: String, scale: Int): Bitmap? = remember("item:$id:$scale") {
        val icon = asset(context, "items/$id.png") ?: return@remember null
        Bitmap.createScaledBitmap(icon, icon.width * scale, icon.height * scale, false)
    }

    private const val FRAME = 2
}
