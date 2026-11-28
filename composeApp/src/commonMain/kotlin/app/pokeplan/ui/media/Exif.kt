package app.pokeplan.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * The EXIF orientation of a JPEG (1 to 8; 1 is upright), read straight from its first bytes with no platform call, so a phone photo
 * that was kept as it was taken shows the right way up on every platform. Anything that is not a JPEG with an orientation tag is 1.
 */
object Exif {
    fun orientation(bytes: ByteArray): Int {
        if (bytes.size < 12 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return 1
        var p = 2
        while (p + 4 <= bytes.size) {
            if (bytes[p] != 0xFF.toByte()) return 1
            val marker = bytes[p + 1].toInt() and 0xFF
            if (marker == 0xFF) { p++; continue }
            if (marker == 0xDA || marker == 0xD9) return 1
            val length = u16(bytes, p + 2, false)
            if (length < 2) return 1
            if (marker == 0xE1 && p + 10 <= bytes.size && bytes.decodeAscii(p + 4, 4) == "Exif") {
                return readTiff(bytes, p + 10, minOf(bytes.size, p + 2 + length))
            }
            p += 2 + length
        }
        return 1
    }

    private fun readTiff(b: ByteArray, start: Int, end: Int): Int {
        if (start + 8 > end) return 1
        val little = when {
            b[start] == 'I'.code.toByte() && b[start + 1] == 'I'.code.toByte() -> true
            b[start] == 'M'.code.toByte() && b[start + 1] == 'M'.code.toByte() -> false
            else -> return 1
        }
        val ifd = start + u32(b, start + 4, little)
        if (ifd < start || ifd + 2 > end) return 1
        val count = u16(b, ifd, little)
        for (i in 0 until count) {
            val e = ifd + 2 + i * 12
            if (e + 12 > end) return 1
            if (u16(b, e, little) == 0x0112) return u16(b, e + 8, little).takeIf { it in 1..8 } ?: 1
        }
        return 1
    }

    private fun u16(b: ByteArray, at: Int, little: Boolean): Int {
        val a = b[at].toInt() and 0xFF
        val c = b[at + 1].toInt() and 0xFF
        return if (little) a or (c shl 8) else (a shl 8) or c
    }

    private fun u32(b: ByteArray, at: Int, little: Boolean): Int =
        if (little) u16(b, at, true) or (u16(b, at + 2, true) shl 16) else (u16(b, at, false) shl 16) or u16(b, at + 2, false)

    private fun ByteArray.decodeAscii(at: Int, n: Int): String = buildString { for (i in 0 until n) append((this@decodeAscii[at + i].toInt() and 0x7F).toChar()) }
}

/** This picture turned the way its EXIF [orientation] says, so it shows upright; the same bitmap when it already is. */
fun ImageBitmap.upright(orientation: Int): ImageBitmap {
    if (orientation !in 2..8) return this
    val w = width.toFloat()
    val h = height.toFloat()
    val swap = orientation >= 5
    val out = ImageBitmap(if (swap) height else width, if (swap) width else height)
    val canvas = Canvas(out)
    // Canvas calls apply to the picture in reverse order: the last call here happens first.
    when (orientation) {
        2 -> { canvas.translate(w, 0f); canvas.scale(-1f, 1f) }
        3 -> { canvas.translate(w, h); canvas.rotate(180f) }
        4 -> { canvas.translate(0f, h); canvas.scale(1f, -1f) }
        5 -> { canvas.scale(-1f, 1f); canvas.rotate(90f) }
        6 -> { canvas.translate(h, 0f); canvas.rotate(90f) }
        7 -> { canvas.translate(h, w); canvas.scale(1f, -1f); canvas.rotate(90f) }
        8 -> { canvas.translate(0f, w); canvas.rotate(-90f) }
    }
    canvas.drawImage(this, Offset.Zero, Paint().apply { filterQuality = FilterQuality.None })
    return out
}

/** The picture in [bytes], decoded and turned upright. Null when the bytes are not a picture. */
fun decodeUpright(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    val bitmap = runCatching { org.jetbrains.compose.resources.decodeToImageBitmap(bytes) }.getOrNull() ?: return null
    return bitmap.upright(Exif.orientation(bytes))
}
