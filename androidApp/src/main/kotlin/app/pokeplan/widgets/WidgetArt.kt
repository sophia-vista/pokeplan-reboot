package app.pokeplan.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import app.pokeplan.app.ReactionIcon
import app.pokeplan.ui.StatusKind
import app.pokeplan.ui.emote
import app.pokeplan.ui.status
import kotlin.math.roundToInt

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
        if (cache.size > 24) cache.remove(cache.keys.first())
        cache[key] = bitmap
        return bitmap
    }

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

    fun scene(context: Context, name: String, darkness: Float, width: Int, height: Int, radius: Float, roundBottom: Boolean = true): Bitmap? {
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

    private fun standing(sprite: Bitmap, scale: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(sprite, sprite.width * scale, sprite.height * scale, false)
        val out = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
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

    fun still(context: Context, formId: Int, shiny: Boolean): Bitmap? =
        remember("still:$formId:$shiny") {
            pmdFrames(context, formId, shiny)?.first()?.let { return@remember it }
            val sprite = asset(context, "sprites/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: return@remember null
            standing(withoutEmptyRowsBelow(sprite), 2)
        }

    private fun pmdFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? =
        animation(context, formId)?.takeIf { it.dir == "pmd" }?.let { idleFrames(context, formId, shiny) }

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

    private class Sheet(val dir: String, val width: Int, val height: Int, val times: IntArray, val hasShiny: Boolean)

    private var animations: Map<Int, Sheet>? = null

    private fun table(context: Context, dir: String, file: String): Map<Int, Sheet> = try {
        context.assets.open("$RESOURCES/$dir/$file").bufferedReader().readLines().filter { it.isNotBlank() }.associate { line ->
            val c = line.split('\t')
            c[0].toInt() to Sheet(dir, c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray(), c[5] == "1")
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun animation(context: Context, id: Int): Sheet? {
        if (animations == null) animations = table(context, "animated", "animated.tsv") + table(context, "pmd", "pmd.tsv")
        return animations?.get(id)
    }

    fun idleFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? = frames(context, formId, shiny, animation(context, formId))

    private var sleeping: Map<Int, Sheet>? = null

    fun sleepFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? {
        if (sleeping == null) sleeping = table(context, "pmd/sleep", "sleep.tsv")
        val a = sleeping?.get(formId)?.takeIf { !shiny || it.hasShiny } ?: return null
        return frames(context, formId, shiny, a)
    }

    private fun frames(context: Context, formId: Int, shiny: Boolean, sheetInfo: Sheet?): List<Bitmap>? {
        val a = sheetInfo ?: return null
        val (w, h, times) = Triple(a.width, a.height, a.times)
        val sheet = asset(context, "${a.dir}/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: asset(context, "${a.dir}/$formId.png") ?: return null
        val total = times.sum()
        val count = minOf(MAX_FRAMES, (total + FLIP_MILLIS - 1) / FLIP_MILLIS).coerceAtLeast(1)
        val fit = (CANVAS - 12) / maxOf(w, h)
        val scale = (if (a.dir.startsWith("pmd")) minOf(fit, (CANVAS - 12) / 32) else fit).coerceAtLeast(1)
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

    fun item(context: Context, id: String, scale: Int): Bitmap? = remember("item:$id:$scale") {
        val icon = asset(context, "items/$id.png") ?: return@remember null
        Bitmap.createScaledBitmap(icon, icon.width * scale, icon.height * scale, false)
    }

    fun reaction(context: Context, icon: ReactionIcon, targetPx: Int): Bitmap? = remember("reaction:${icon.key}:$targetPx") {
        val kind = icon.status()
        val (source, tint) = if (kind != null) {
            val strip = asset(context, "status/${kind.file}.png") ?: return@remember null
            val frame = when (kind) { StatusKind.SLEEP -> kind.frames - 1; StatusKind.TWINKLE -> 1; else -> kind.frames / 3 }
            Bitmap.createBitmap(strip, frame * kind.cell, 0, kind.cell, strip.height) to null
        } else {
            val emote = icon.emote() ?: return@remember null
            if (emote.item != null) (asset(context, "items/${emote.item}.png") ?: return@remember null) to null
            else (asset(context, "pixelarticons/${emote.glyph}.png") ?: return@remember null) to emote.tint.toArgb()
        }
        val scale = (targetPx / maxOf(source.width, source.height)).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, false)
        if (tint == null) scaled else Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(scaled, 0f, 0f, Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN) })
        }
    }

    private const val FRAME = 2
}
