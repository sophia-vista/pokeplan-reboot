package app.pokeplan.ui

import androidx.compose.ui.graphics.ImageBitmap

internal class BitmapCache(private val maxBytes: Long) {
    private val entries = LinkedHashMap<String, ImageBitmap?>()
    private var bytes = 0L

    operator fun contains(name: String): Boolean = name in entries

    operator fun get(name: String): ImageBitmap? {
        if (name !in entries) return null
        val image = entries.remove(name)
        entries[name] = image
        return image
    }

    operator fun set(name: String, image: ImageBitmap?) {
        entries.remove(name)?.let { bytes -= size(it) }
        entries[name] = image
        bytes += size(image)
        while (bytes > maxBytes && entries.size > 1) {
            val oldest = entries.keys.first()
            bytes -= size(entries.remove(oldest))
        }
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }

    val count: Int get() = entries.size
    val usedBytes: Long get() = bytes

    private fun size(image: ImageBitmap?): Long = if (image == null) 0 else image.width.toLong() * image.height * 4
}

private const val MB = 1024L * 1024L

internal object CacheBudget {
    const val SCENES = 32 * MB
    const val SPRITE_SHEETS = 24 * MB
    const val TILES = 16 * MB
    const val WORLD_ART = 16 * MB
    const val STILL_SPRITES = 16 * MB
    const val SMALL = 8 * MB
}
