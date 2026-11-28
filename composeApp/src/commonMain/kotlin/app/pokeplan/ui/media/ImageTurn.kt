package app.pokeplan.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import org.jetbrains.compose.resources.decodeToImageBitmap

/** This picture turned [quarterTurns] times clockwise (any integer; 0 gives the same bitmap). Used where a stored turn is drawn as a bitmap, such as a thumbnail. */
fun ImageBitmap.turned(quarterTurns: Int): ImageBitmap {
    val t = ((quarterTurns % 4) + 4) % 4
    if (t == 0) return this
    val w = width.toFloat()
    val h = height.toFloat()
    val swap = t % 2 == 1
    val out = ImageBitmap(if (swap) height else width, if (swap) width else height)
    val canvas = Canvas(out)
    when (t) {
        1 -> { canvas.translate(h, 0f); canvas.rotate(90f) }
        2 -> { canvas.translate(w, h); canvas.rotate(180f) }
        3 -> { canvas.translate(0f, w); canvas.rotate(-90f) }
    }
    canvas.drawImage(this, Offset.Zero, Paint().apply { filterQuality = FilterQuality.None })
    return out
}

/**
 * The picture in [bytes], decoded. The platform decoder (Skia, and Android's) already applies a JPEG's EXIF orientation, so nothing is
 * turned here: turning it again once showed such photos upside down. Null when the bytes are not a picture.
 */
fun decodePicture(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    return runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}
