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
 * the only shapes drawn in code are the shadow under the buddy and the hearts of a pet, which are UI chrome.
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
    fun scene(context: Context, name: String, night: Boolean, width: Int, height: Int, radius: Float): Bitmap? =
        remember("scene:$name:$night:$width:$height:$radius") {
            val source = asset(context, "scenes/$name.png") ?: return@remember null
            val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
            val srcW = (width / scale).toInt().coerceAtMost(source.width)
            val srcH = (height / scale).toInt().coerceAtMost(source.height)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.clipPath(roundedPath(width.toFloat(), height.toFloat(), radius, true))
            canvas.drawBitmap(
                source, android.graphics.Rect((source.width - srcW) / 2, source.height - srcH, (source.width - srcW) / 2 + srcW, source.height),
                RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { isFilterBitmap = true },
            )
            if (night) canvas.drawColor(Color.argb(0x99, 0x10, 0x1C, 0x3C))
            out
        }

    /** What a still picture of the buddy shows around it. */
    enum class Effect { NONE, HEARTS, SPARKLES }

    private const val CANVAS = 192

    /** One square picture of the buddy standing on a soft shadow, bottom-centred; [sprite] is enlarged by [scale] without smoothing. */
    private fun standing(sprite: Bitmap, scale: Int, effect: Effect): Bitmap {
        val scaled = Bitmap.createScaledBitmap(sprite, sprite.width * scale, sprite.height * scale, false)
        val out = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawOval(RectF(CANVAS * 0.2f, CANVAS - 26f, CANVAS * 0.8f, CANVAS - 8f), Paint().apply { color = Color.argb(70, 0, 0, 0); isAntiAlias = true })
        val lift = if (effect == Effect.NONE) 0f else 14f
        canvas.drawBitmap(scaled, (CANVAS - scaled.width) / 2f, CANVAS - scaled.height - 12f - lift, Paint().apply { isFilterBitmap = false })
        when (effect) {
            Effect.HEARTS -> { heart(canvas, CANVAS * 0.18f, 30f, 22f); heart(canvas, CANVAS * 0.45f, 4f, 26f); heart(canvas, CANVAS * 0.70f, 22f, 20f) }
            Effect.SPARKLES -> { star(canvas, CANVAS * 0.2f, 40f, 9f); star(canvas, CANVAS * 0.5f, 10f, 12f); star(canvas, CANVAS * 0.8f, 36f, 8f); star(canvas, CANVAS * 0.35f, 70f, 6f) }
            Effect.NONE -> Unit
        }
        return out
    }

    /** A still picture of the buddy from its regular sprite. */
    fun still(context: Context, formId: Int, shiny: Boolean, effect: Effect = Effect.NONE): Bitmap? =
        remember("still:$formId:$shiny:$effect") {
            val sprite = asset(context, "sprites/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: return@remember null
            standing(sprite, 2, effect)
        }

    private var animations: Map<Int, Triple<Int, Int, IntArray>>? = null

    /** assets/animated/animated.tsv: id to (frame width, frame height, frame times in ms) plus whether a shiny sheet exists. */
    private fun animation(context: Context, id: Int): Triple<Int, Int, IntArray>? {
        if (animations == null) {
            animations = try {
                context.assets.open("$RESOURCES/animated/animated.tsv").bufferedReader().readLines().filter { it.isNotBlank() }.associate { line ->
                    val c = line.split('\t')
                    c[0].toInt() to Triple(c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray())
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }
        return animations?.get(id)
    }

    /**
     * The buddy's real animation as at most [MAX_FRAMES] pictures spread evenly over one loop, each [FLIP_MILLIS] long
     * when played by the widget's flipper, or null for a species without a Black/White animation.
     */
    fun idleFrames(context: Context, formId: Int, shiny: Boolean): List<Bitmap>? {
        val (w, h, times) = animation(context, formId) ?: return null
        val sheet = asset(context, "animated/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: asset(context, "animated/$formId.png") ?: return null
        val total = times.sum()
        val count = minOf(MAX_FRAMES, (total + FLIP_MILLIS - 1) / FLIP_MILLIS).coerceAtLeast(1)
        val scale = ((CANVAS - 12) / maxOf(w, h)).coerceAtLeast(1)
        return (0 until count).map { j ->
            val at = (j.toLong() * total / count).toInt()
            var index = 0
            var elapsed = 0
            while (index < times.size - 1 && elapsed + times[index] <= at) { elapsed += times[index]; index++ }
            standing(Bitmap.createBitmap(sheet, index * w, 0, w, h), scale, Effect.NONE)
        }
    }

    const val FLIP_MILLIS = 200
    private const val MAX_FRAMES = 20

    /** A small red heart, used for pets and friendship. */
    fun heart(size: Int): Bitmap = remember("heart:$size") {
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { heart(Canvas(it), 0f, 0f, size.toFloat()) }
    }!!

    private fun heart(canvas: Canvas, left: Float, top: Float, size: Float) {
        val w = size
        val h = size
        val path = Path().apply {
            moveTo(left + w / 2, top + h * 0.92f)
            cubicTo(left - w * 0.15f, top + h * 0.45f, left + w * 0.15f, top - h * 0.1f, left + w / 2, top + h * 0.28f)
            cubicTo(left + w * 0.85f, top - h * 0.1f, left + w * 1.15f, top + h * 0.45f, left + w / 2, top + h * 0.92f)
            close()
        }
        canvas.drawPath(path, Paint().apply { color = Color.rgb(0xE8, 0x44, 0x36); isAntiAlias = true })
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r * 0.28f, cy - r * 0.28f); lineTo(cx + r, cy); lineTo(cx + r * 0.28f, cy + r * 0.28f)
            lineTo(cx, cy + r); lineTo(cx - r * 0.28f, cy + r * 0.28f); lineTo(cx - r, cy); lineTo(cx - r * 0.28f, cy - r * 0.28f); close()
        }
        canvas.drawPath(path, Paint().apply { color = Color.rgb(0xFF, 0xCB, 0x05); isAntiAlias = true })
    }

    /** An item icon (a berry, the egg) enlarged by a whole number without smoothing. */
    fun item(context: Context, id: String, scale: Int): Bitmap? = remember("item:$id:$scale") {
        val icon = asset(context, "items/$id.png") ?: return@remember null
        Bitmap.createScaledBitmap(icon, icon.width * scale, icon.height * scale, false)
    }

    private const val FRAME = 2
}
