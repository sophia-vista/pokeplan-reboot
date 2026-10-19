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

    /**
     * The buddy standing on a soft shadow, drawn on a transparent square. When [petted] it hops a little and hearts rise
     * above it, so a tap on the widget visibly answers.
     */
    fun buddyStage(context: Context, formId: Int, shiny: Boolean, petted: Boolean): Bitmap? =
        remember("buddy:$formId:$shiny:$petted") {
            val sprite = asset(context, "sprites/${if (shiny) "$formId-shiny" else "$formId"}.png") ?: return@remember null
            val size = 360
            val scale = 3
            val scaled = Bitmap.createScaledBitmap(sprite, sprite.width * scale, sprite.height * scale, false)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val shadow = Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true }
            canvas.drawOval(RectF(size * 0.22f, size - 62f, size * 0.78f, size - 26f), shadow)
            val lift = if (petted) 22f else 0f
            canvas.drawBitmap(scaled, (size - scaled.width) / 2f, size - scaled.height - 36f - lift, Paint().apply { isFilterBitmap = false })
            if (petted) {
                heart(canvas, size * 0.22f, 52f, 34f)
                heart(canvas, size * 0.46f, 6f, 40f)
                heart(canvas, size * 0.70f, 40f, 30f)
            }
            out
        }

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

    /** An item icon (a berry, the egg) enlarged by a whole number without smoothing. */
    fun item(context: Context, id: String, scale: Int): Bitmap? = remember("item:$id:$scale") {
        val icon = asset(context, "items/$id.png") ?: return@remember null
        Bitmap.createScaledBitmap(icon, icon.width * scale, icon.height * scale, false)
    }

    private const val FRAME = 2
}
