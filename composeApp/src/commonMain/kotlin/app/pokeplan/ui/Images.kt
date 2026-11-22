package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The image for [key], taken from [cache] when it is there already and read with [load] once when it is not.
 *
 * It holds the Compose rule every loader used to repeat: the key may change under the same composable, so the value starts
 * from what is cached for the new key and never keeps the old key's image. One helper also means a fix to the loading goes
 * everywhere at once.
 */
@Composable
internal fun rememberCachedImage(cache: BitmapCache, key: String, load: suspend () -> ImageBitmap?): ImageBitmap? =
    produceState(cache[key], key) {
        value = cache[key]
        if (value == null) {
            value = load()
            cache[key] = value
        }
    }.value

/** The same for a plain map, for keys that are not strings. */
@Composable
internal fun <K> rememberCachedImage(cache: MutableMap<K, ImageBitmap?>, key: K, load: suspend () -> ImageBitmap?): ImageBitmap? =
    produceState(cache[key], key) {
        value = cache[key]
        if (value == null) {
            value = load()
            cache[key] = value
        }
    }.value
