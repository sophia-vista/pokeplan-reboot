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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

private val cache = BitmapCache(CacheBudget.STILL_SPRITES)

internal inline fun <T> orNull(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

@Composable
fun ItemIcon(itemId: String, modifier: Modifier = Modifier, size: Dp = 32.dp, description: String? = itemId) {
    val path = if (itemId.startsWith("pmd2-")) "files/pmditems/$itemId.png" else "files/items/$itemId.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            cache[path] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) }
    }
}

@Composable
fun BadgeImage(badgeId: Int, modifier: Modifier = Modifier, size: Dp = 32.dp, description: String? = null, silhouette: Color? = null) {
    val path = "files/badges/$badgeId.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
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

@Composable
fun Glyph(name: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, description: String? = null) {
    val path = "files/pixelarticons/$name.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            cache[path] = value
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, description, Modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, colorFilter = ColorFilter.tint(tint)) }
    }
}

@Composable
fun Sprite(formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 96.dp, silhouette: Color? = null) {
    val pmd by produceState(loadedAnimations()?.get(formId)?.takeIf { it.dir == "pmd" }, formId) {
        value = animations()[formId]?.takeIf { it.dir == "pmd" }
    }
    val a = pmd
    val pmdSheet by produceState(a?.let { loadedSheet(it.sheetName(formId, shiny)) }, a, shiny) { value = a?.let { sheet(it.sheetName(formId, shiny)) } }
    val frame = pmdSheet
    if (a != null && frame != null) {
        Canvas(modifier.size(size).semantics { contentDescription = description }) {
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
        value = cache[name]
        if (value == null) {
            value = orNull { Res.readBytes("files/sprites/$name.png").decodeToImageBitmap() }
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

@Composable
fun NpcPortrait(name: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val path = "files/npc/$name.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            cache[path] = value
        }
    }
    val image = bitmap
    if (image != null) Image(image, name, modifier.size(size), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    else ItemIcon("poke-ball", modifier, size = size / 2)
}

@Composable
fun TypePlate(type: app.pokeplan.core.dex.PokemonType, modifier: Modifier = Modifier, width: Dp = 72.dp) {
    val path = "files/types/${type.name.lowercase()}.png"
    val bitmap by produceState(cache[path], path) {
        value = cache[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            cache[path] = value
        }
    }
    bitmap?.let {
        Image(it, type.label, modifier.size(width, width * it.height / it.width), contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None)
    }
}
