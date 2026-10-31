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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** The tileset atlas (assets/tiles) a dungeon is drawn with: its own where the game's sheet is here, the Distortion Rift borrows the World Abyss. */
internal fun dungeonTiles(dungeonId: String): String = when (dungeonId) {
    "tiny-woods" -> "tiny-woods"
    "thunderwave-cave" -> "thunderwave-cave"
    "mt-steel" -> "mt-steel"
    "waterfall-cave" -> "waterfall-cave"
    "glacier-palace" -> "mt-freeze"
    "ancient-ruins" -> "wish-cave"
    "sky-tower" -> "sky-tower"
    "temporal-tower" -> "temporal-tower"
    else -> "world-abyss"
}

/**
 * A team's expedition box, made of the dungeon's own tiles: a frame of its walls round a floor that scrolls past while the team
 * walks to the right on it with their walking animations (the leader in front), and [content] below on a pale veil so the text
 * reads. Whole-number pixel scale, no smoothing. With Reduce motion ([animate] false) nothing moves.
 */
@Composable
internal fun TileBox(dungeonId: String, members: List<app.pokeplan.core.pokemon.OwnedPokemon>, animate: Boolean, content: @Composable () -> Unit) {
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
        val pixel = (widthPx / (TILE_PX * 10)).toInt().coerceAtLeast(1)
        val tile = TILE_PX * pixel
        val tileDp = with(density) { tile.toDp() }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Canvas(Modifier.matchParentSize()) {
                val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
                val cols = (size.width / tile).toInt() + 2
                val rows = (size.height / tile).toInt() + 1
                val scroll = (seconds * 36f).toInt() / pixel * pixel % tile
                fun put(ax: Int, ay: Int, x: Int, y: Int) =
                    drawImage(image, IntOffset(ax * TILE_PX, ay * TILE_PX), IntSize(TILE_PX, TILE_PX), IntOffset(x, y), IntSize(tile, tile), filterQuality = FilterQuality.None)
                // The floor, a tile wider each side, slides left.
                for (r in 0 until rows) for (c in -1..cols) put(0, 3, c * tile - scroll, r * tile)
                // A veil under the text, from below the walking strip to the bottom wall.
                val top = (STRIP_ROWS + 2) * tile.toFloat()
                drawRect(Color(0xE6FFFFFF), Offset(tile.toFloat(), top), Size(size.width - 2 * tile, size.height - top - tile))
                // The frame: walls round the room, their faces towards the floor.
                val right = (size.width / tile).toInt() - 1
                val bottom = ((size.height - 1) / tile).toInt()
                val lastX = (size.width - tile).toInt()
                val lastY = (size.height - tile).toInt()
                for (c in 0..right) { put(1, 2, if (c == right) lastX else c * tile, tile); put(1, 0, if (c == right) lastX else c * tile, lastY - tile) }
                for (r in 2 until bottom - 1) { put(2, 1, 0, r * tile); put(0, 1, lastX, r * tile) }
                for (c in 0..right) { put(1, 1, if (c == right) lastX else c * tile, 0); put(1, 1, if (c == right) lastX else c * tile, lastY) }
                put(2, 2, 0, tile); put(0, 2, lastX, tile); put(2, 0, 0, lastY - tile); put(0, 0, lastX, lastY - tile)
            }
            Column(Modifier.padding(horizontal = tileDp, vertical = tileDp)) {
                // The team on the strip of floor under the top wall, leader on the right, staggered like a party in a corridor.
                val strip = tileDp * (STRIP_ROWS + 1)
                Box(Modifier.fillMaxWidth().height(strip)) {
                    val size = tileDp * 1.7f
                    val step = tileDp * 1.35f
                    val shown = members.take(4)
                    shown.forEachIndexed { i, p ->
                        val order = shown.size - 1 - i
                        Box(Modifier.offset(x = step * order + tileDp * 0.3f, y = tileDp * (if (i % 2 == 0) 1.3f else 1.75f) - size * 0.15f)) {
                            app.pokeplan.ui.AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = size, animate = animate, action = app.pokeplan.ui.SpriteAction.WALK_SIDE)
                        }
                    }
                }
                Box(Modifier.padding(top = tileDp * 0.5f, bottom = tileDp * 0.7f)) { content() }
            }
        }
    }
}

private const val TILE_PX = 24
private const val STRIP_ROWS = 2
