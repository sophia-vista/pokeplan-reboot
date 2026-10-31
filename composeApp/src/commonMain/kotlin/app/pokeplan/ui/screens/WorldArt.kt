package app.pokeplan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.orNull
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.max
import kotlin.math.min

private val worldArt = mutableMapOf<String, ImageBitmap?>()

/** The dungeons that have a real tileset picture in assets/world (`dungeon-<id>.png`). */
internal val dungeonPictures = setOf("tiny-woods", "thunderwave-cave", "mt-steel")

/**
 * A real Mystery Dungeon picture from assets/world (docs/assets.md): the town, the guild room, the summit, the camp cave and a
 * tileset preview per dungeon. Drawn with square pixels. [crop] fills the box and shows the bottom middle; otherwise the whole
 * picture sits centred inside it.
 */
@Composable
internal fun WorldPicture(name: String, modifier: Modifier = Modifier, crop: Boolean = false, top: Boolean = false) {
    val image by produceState(worldArt[name], name) {
        if (value == null) {
            value = orNull { Res.readBytes("files/world/$name.png").decodeToImageBitmap() }
            worldArt[name] = value
        }
    }
    Canvas(modifier) {
        val picture = image ?: return@Canvas
        val cover = if (crop) max(size.width / picture.width, size.height / picture.height) else min(size.width / picture.width, size.height / picture.height)
        // Whole-number scale up where it fits, so pixels stay sharp; a smooth scale down otherwise.
        val scale = if (cover >= 1f) kotlin.math.floor(cover).coerceAtLeast(1f).let { if (crop) kotlin.math.ceil(cover) else it } else cover
        val w = (picture.width * scale).toInt()
        val h = (picture.height * scale).toInt()
        val srcW = if (w > size.width) (size.width / scale).toInt().coerceAtMost(picture.width) else picture.width
        val srcH = if (h > size.height) (size.height / scale).toInt().coerceAtMost(picture.height) else picture.height
        val dstW = (srcW * scale).toInt()
        val dstH = (srcH * scale).toInt()
        drawImage(
            picture,
            srcOffset = IntOffset((picture.width - srcW) / 2, if (top) 0 else picture.height - srcH), srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(((size.width - dstW) / 2).toInt(), if (crop) (if (top) 0 else (size.height - dstH).toInt()) else ((size.height - dstH) / 2).toInt()), dstSize = IntSize(dstW, dstH),
            filterQuality = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium,
        )
    }
}

/** A framed picture ([top] shows the top of it, where a scene's sky and buildings are) with a caption plate along its bottom, to open a World screen. */
@Composable
internal fun WorldBanner(name: String, title: String, subtitle: String? = null, sky: Color = Color(0xFFBFE3F2), height: Dp = 132.dp, top: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(height).border(2.dp, Pixel.Ink).background(sky).clipToBounds()) {
        WorldPicture(name, Modifier.fillMaxSize(), crop = true, top = top)
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC1B2230)))).padding(horizontal = Spacing.M, vertical = Spacing.S)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE3EEF5))
            }
        }
    }
}

private val tileAtlases = mutableMapOf<String, ImageBitmap?>()

/** The tileset atlas (assets/tiles) a dungeon is drawn with: its own where the game's sheet is here, a kindred dungeon's otherwise. */
internal fun dungeonTiles(dungeonId: String): String = when (dungeonId) {
    "tiny-woods" -> "tiny-woods"
    "thunderwave-cave" -> "thunderwave-cave"
    "mt-steel" -> "mt-steel"
    "waterfall-cave" -> "brine-cave"
    "glacier-palace" -> "crystal-cave"
    "ancient-ruins" -> "concealed-ruins"
    "sky-tower" -> "craggy-coast"
    "temporal-tower" -> "deep-sealed-ruin"
    else -> "dark-hill-deep"
}

/**
 * A team on its expedition: a corridor of the dungeon's own tiles (walls above and below, a floor between) that scrolls past
 * while the Pokémon walk to the right with their own walking animations, the leader in front. Whole-number pixel scale, no
 * smoothing. With Reduce motion ([animate] false) nothing moves.
 */
@Composable
internal fun DungeonStrip(dungeonId: String, members: List<app.pokeplan.core.pokemon.OwnedPokemon>, caption: String, animate: Boolean) {
    val tiles = dungeonTiles(dungeonId)
    val atlas by produceState(tileAtlases[tiles], tiles) {
        if (value == null) {
            value = orNull { Res.readBytes("files/tiles/$tiles.png").decodeToImageBitmap() }
            tileAtlases[tiles] = value
        }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (animate) { val start = withFrameNanos { it }; while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f } }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val pixel = (widthPx / (TILE_PX * 7)).toInt().coerceAtLeast(1)
        val tile = TILE_PX * pixel
        Box(Modifier.fillMaxWidth().height(with(density) { (tile * 5).toDp() }).border(2.dp, Pixel.Ink).clipToBounds()) {
            Canvas(Modifier.fillMaxSize()) {
                val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
                val scroll = (seconds * 36f).toInt() / pixel * pixel
                val first = scroll / tile
                val shift = scroll % tile
                for (c in 0..(size.width / tile).toInt() + 1) {
                    val k = c + first
                    for (r in 0 until 5) {
                        val (ax, ay) = when (r) {
                            0 -> 1 to 1
                            1 -> 1 to 2
                            4 -> 1 to 0
                            else -> 0 to 3
                        }
                        drawImage(image, IntOffset(ax * TILE_PX, ay * TILE_PX), IntSize(TILE_PX, TILE_PX), IntOffset(c * tile - shift, r * tile), IntSize(tile, tile), filterQuality = FilterQuality.None)
                    }
                }
            }
            // The team, leader on the right, on the two floor rows, staggered like a party in a corridor.
            val size = with(density) { (tile * 1.7f).toDp() }
            val step = with(density) { (tile * 1.35f).toDp() }
            members.take(4).forEachIndexed { i, p ->
                val order = members.take(4).size - 1 - i
                Box(Modifier.offset(x = step * order + with(density) { (tile * 0.3f).toDp() }, y = with(density) { (tile * (if (i % 2 == 0) 1.55f else 1.95f)).toDp() }.let { it - size * 0.15f })) {
                    app.pokeplan.ui.AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = size, animate = animate, action = app.pokeplan.ui.SpriteAction.WALK_SIDE)
                }
            }
            Text(caption, Modifier.align(Alignment.BottomEnd).background(Color(0xCC1B2230)).padding(horizontal = Spacing.S, vertical = Spacing.XS), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

private const val TILE_PX = 24
