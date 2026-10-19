package app.pokeplan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pokeplan.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

private val cache = mutableMapOf<String, ImageBitmap?>()

/** A Pokémon sprite by form number, scaled without blur so the pixels stay crisp. */
@Composable
fun Sprite(formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val name = if (shiny) "$formId-shiny" else "$formId"
    val bitmap by produceState(cache[name], name) {
        if (value == null) {
            value = try {
                Res.readBytes("files/sprites/$name.png").decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            cache[name] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let {
            Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
    }
}
