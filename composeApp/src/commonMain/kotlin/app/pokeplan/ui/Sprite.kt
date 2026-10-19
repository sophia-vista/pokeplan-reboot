package app.pokeplan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pokeplan.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

private val cache = mutableMapOf<String, ImageBitmap?>()

/** The real game icon of a bag item (Rare Candy, a berry, an evolution stone...). Draws nothing if there is no icon for [itemId]. */
@Composable
fun ItemIcon(itemId: String, modifier: Modifier = Modifier, size: Dp = 32.dp, description: String? = itemId) {
    val path = "files/items/$itemId.png"
    val bitmap by produceState(cache[path], path) {
        if (value == null) {
            value = try {
                Res.readBytes(path).decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            cache[path] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) }
    }
}

/** A Pokémon sprite by form number, scaled without blur so the pixels stay crisp. */
@Composable
fun Sprite(formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 96.dp, silhouette: Color? = null) {
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
            Image(
                it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                colorFilter = silhouette?.let { c -> ColorFilter.tint(c, BlendMode.SrcIn) },
            )
        }
    }
}

/** A character portrait from assets/npc ("oak"). A Poké Ball stands in until the file is there. */
@Composable
fun NpcPortrait(name: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val path = "files/npc/$name.png"
    val bitmap by produceState(cache[path], path) {
        if (value == null) {
            value = try {
                Res.readBytes(path).decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            cache[path] = value
        }
    }
    val image = bitmap
    if (image != null) Image(image, name, modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    else ItemIcon("poke-ball", modifier, size = size / 2)
}

/** A type name plate (Fire, Water...), real game art. */
@Composable
fun TypePlate(type: app.pokeplan.core.model.PokemonType, modifier: Modifier = Modifier, width: Dp = 72.dp) {
    val path = "files/types/${type.name.lowercase()}.png"
    val bitmap by produceState(cache[path], path) {
        if (value == null) {
            value = try {
                Res.readBytes(path).decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            cache[path] = value
        }
    }
    bitmap?.let {
        Image(it, type.label, modifier.size(width, width * it.height / it.width), contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None)
    }
}
