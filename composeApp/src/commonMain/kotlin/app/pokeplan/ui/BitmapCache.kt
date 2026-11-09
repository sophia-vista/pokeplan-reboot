package app.pokeplan.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decoded pictures kept by name, up to [maxBytes] of pixels (width × height × 4); once over, the ones used longest ago are let go and
 * read again if they are asked for later. A name that has no picture (a missing file) is remembered too, so it is not read again at
 * every frame. The newest picture always stays, even if it alone is over the limit. Used from the UI thread only, like the maps it replaces.
 */
internal class BitmapCache(private val maxBytes: Long) {
    private val entries = LinkedHashMap<String, ImageBitmap?>()
    private var bytes = 0L

    operator fun contains(name: String): Boolean = name in entries

    /** The picture, or null if there is none or it has not been read; it counts as used now. */
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

    /** Forgets everything, for a memory warning. */
    fun clear() {
        entries.clear()
        bytes = 0
    }

    val count: Int get() = entries.size
    val usedBytes: Long get() = bytes

    private fun size(image: ImageBitmap?): Long = if (image == null) 0 else image.width.toLong() * image.height * 4
}

private const val MB = 1024L * 1024L

/** Budgets for the caches, one per kind of picture, so a big scene cannot push every sprite sheet out. */
internal object CacheBudget {
    const val SCENES = 32 * MB
    const val SPRITE_SHEETS = 24 * MB
    const val TILES = 16 * MB
    const val WORLD_ART = 16 * MB
    const val STILL_SPRITES = 16 * MB
    const val SMALL = 8 * MB
}
