package app.pokeplan.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pokeplan.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

private val cache = BitmapCache(CacheBudget.STILL_SPRITES)

/**
 * [block]'s result, or null when it fails (a missing or broken file). A cancelled load is not a failure and is passed on:
 * caught here, a page left before its sprite was read (Next in the Pokédex) would cache that sprite as missing for good.
 */
internal inline fun <T> orNull(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * A bundled picture read and decoded off the UI dispatcher. Compose resources are plain files and `decodeToImageBitmap` is
 * CPU work: doing it in the composition coroutine made a screen with many first-seen sprites (the Pokédex, the PC, the
 * Town) drop frames while a dozen PNGs were decoded one after another on the main thread.
 */
internal suspend fun decodeAsset(path: String): ImageBitmap? = withContext(Dispatchers.Default) {
    orNull { Res.readBytes(path).decodeToImageBitmap() }
}

/** The real game icon of a bag item (Rare Candy, a berry, an evolution stone...). Draws nothing if there is no icon for [itemId]. */
@Composable
fun ItemIcon(itemId: String, modifier: Modifier = Modifier, size: Dp = 32.dp, description: String? = itemId) {
    val path = if (itemId.startsWith("pmd2-")) "files/pmditems/$itemId.png" else "files/items/$itemId.png"
    val bitmap by produceState(cache[path], path) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = cache[path]
        if (value == null) {
            value = decodeAsset(path)
            cache[path] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) }
    }
}

/** A Gym badge picture (`assets/badges/N.png`, PokeAPI/sprites); with a [silhouette] colour it is drawn as a flat shape, for a slot not yet earned. */
@Composable
fun BadgeImage(badgeId: Int, modifier: Modifier = Modifier, size: Dp = 32.dp, description: String? = null, silhouette: Color? = null) {
    val path = "files/badges/$badgeId.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = decodeAsset(path)
            cache[path] = value
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

/**
 * A simple control glyph from Pixelarticons (MIT, assets/pixelarticons; arrows, edit, copy, trash...), tinted with [tint].
 * Pokémon item icons stay the main icons ([ItemIcon]); these stand in for words on small controls.
 */
@Composable
fun Glyph(name: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, description: String? = null) {
    val path = "files/pixelarticons/$name.png"
    val bitmap by produceState(cache[path], path) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = cache[path]
        if (value == null) {
            value = decodeAsset(path)
            cache[path] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, colorFilter = ColorFilter.tint(tint)) }
    }
}

/**
 * A Pokémon sprite by form number, scaled without blur so the pixels stay crisp: the first frame of its Mystery Dungeon
 * style sprite (SpriteCollab) where there is one, else its still sprite.
 */
@Composable
fun Sprite(formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 96.dp, silhouette: Color? = null) {
    // Seeded from the caches, so a list being scrolled does not flash the still sprite first.
    val pmd by produceState(loadedAnimations()?.get(formId)?.takeIf { it.dir == "pmd" }, formId) {
        value = animations()[formId]?.takeIf { it.dir == "pmd" }
    }
    val a = pmd
    val pmdSheet by produceState(a?.let { loadedSheet(it.sheetName(formId, shiny)) }, a, shiny) { value = a?.let { sheet(it.sheetName(formId, shiny)) } }
    val frame = pmdSheet
    if (a != null && frame != null) {
        Canvas(modifier.size(size).semantics { contentDescription = description }) {
            // A whole-number scale shared by all Mystery Dungeon sprites, centred in the box like the still sprites.
            val scale = pixelScale(min(this.size.width, this.size.height), a)
            val w = a.width * scale
            val h = a.height * scale
            drawImage(
                frame, srcOffset = IntOffset.Zero, srcSize = IntSize(a.width, a.height),
                dstOffset = IntOffset(((this.size.width - w) / 2).toInt(), ((this.size.height - h) / 2).toInt()), dstSize = IntSize(w, h),
                filterQuality = FilterQuality.None, colorFilter = silhouette?.let { c -> ColorFilter.tint(c, BlendMode.SrcIn) },
            )
        }
        return
    }
    val name = if (shiny) "$formId-shiny" else "$formId"
    val bitmap by produceState(cache[name], name) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = cache[name]
        if (value == null) {
            value = decodeAsset("files/sprites/$name.png")
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
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = cache[path]
        if (value == null) {
            value = decodeAsset(path)
            cache[path] = value
        }
    }
    val image = bitmap
    if (image != null) Image(image, name, modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    else ItemIcon("poke-ball", modifier, size = size / 2)
}

/** A type name plate (Fire, Water...), real game art. */
@Composable
fun TypePlate(type: app.pokeplan.core.dex.PokemonType, modifier: Modifier = Modifier, width: Dp = 72.dp) {
    val path = "files/types/${type.name.lowercase()}.png"
    val bitmap by produceState(cache[path], path) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = cache[path]
        if (value == null) {
            value = decodeAsset(path)
            cache[path] = value
        }
    }
    bitmap?.let {
        Image(it, type.label, modifier.size(width, width * it.height / it.width), contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None)
    }
}
