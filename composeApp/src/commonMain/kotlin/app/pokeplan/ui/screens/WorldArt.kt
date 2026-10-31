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
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.dialogueBox
import androidx.compose.ui.draw.clip
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
 * A team's expedition box: the app's dialogue border round the dungeon's own tiles. The top is a floor that scrolls past while the
 * team walks to the right on it with their walking animations (the leader in front); below it, [content] is set on wall tiles
 * (under a dark veil, in white) like writing on the dungeon's wall. Whole-number pixel scale, no smoothing. With Reduce motion
 * ([animate] false) nothing moves; with [scrolls] false only the Pokémon move (a screen of many boxes, such as the map, does not redraw them all every frame).
 */
@Composable
internal fun TileBox(dungeonId: String, walkers: List<TileWalker>, animate: Boolean, modifier: Modifier = Modifier, scrolls: Boolean = true, content: @Composable () -> Unit) {
    val tiles = dungeonTiles(dungeonId)
    val atlas by produceState(tileAtlases[tiles], tiles) {
        if (value == null) {
            value = orNull { Res.readBytes("files/tiles/$tiles.png").decodeToImageBitmap() }
            tileAtlases[tiles] = value
        }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate && scrolls) {
        if (animate && scrolls) { val start = withFrameNanos { it }; while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f } }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { (maxWidth - FRAME * 2).toPx() }
        val pixel = (widthPx / (TILE_PX * 8)).toInt().coerceAtLeast(1)
        val tilePx = TILE_PX * pixel
        val tile = with(density) { tilePx.toDp() }
        // Dark fill, so a pixel the tiles do not reach shows dark and never the white of a plain box.
        Box(Modifier.fillMaxWidth().dialogueBox(fill = Color(0xFF141C28)).clip(DialogueShape)) {
            Box(Modifier.matchParentSize().padding(start = FRAME, end = FRAME, top = FRAME, bottom = FRAME - 2.dp).clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) {
                    val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
                    val cols = (size.width / tilePx).toInt() + 2
                    val rows = (size.height / tilePx).toInt() + 2
                    val scroll = (seconds * 36f).toInt() / pixel * pixel % tilePx
                    fun put(ax: Int, ay: Int, x: Int, y: Int) =
                        drawImage(image, IntOffset(ax * TILE_PX, ay * TILE_PX), IntSize(TILE_PX, TILE_PX), IntOffset(x, y), IntSize(tilePx, tilePx), filterQuality = FilterQuality.None)
                    for (r in 0 until rows) for (c in -1..cols) {
                        // Row 0: a single layer of wall; then the floor; then the top edge of the wall below it, and wall.
                        val x = c * tilePx - scroll
                        when {
                            r == 0 -> put(1, 2, x, 0)
                            r <= STRIP_ROWS -> put(0, 3, x, r * tilePx)
                            r == STRIP_ROWS + 1 -> put(1, 0, x, r * tilePx)
                            else -> put(1, 1, x, r * tilePx)
                        }
                    }
                    drawRect(Color(0xB3141C28), Offset(0f, (STRIP_ROWS + 2) * tilePx.toFloat()), Size(size.width, size.height))
                }
                // The team on the floor rows, leader on the right: feet on the floor, never on a wall.
                val shown = walkers.take(4)
                val size = tile * 1.5f
                val step = tile * 1.3f
                shown.forEachIndexed { i, p ->
                    val order = shown.size - 1 - i
                    val feet = tile * (if (i % 2 == 0) 2.2f else 2.9f)
                    Box(Modifier.offset(x = step * order + tile * 0.3f, y = feet - size)) {
                        app.pokeplan.ui.AnimatedSprite(p.formId, p.shiny, p.name, size = size, animate = animate, ground = 0.dp, action = app.pokeplan.ui.SpriteAction.WALK_SIDE)
                    }
                }
            }
            Column(Modifier.padding(start = Spacing.BoxX + 8.dp, end = Spacing.BoxX + 8.dp, top = FRAME + tile * (STRIP_ROWS + 2) + Spacing.L, bottom = Spacing.BoxY + 12.dp)) { onWall(content) }
        }
    }
}

/** Someone who walks on a [TileBox]'s floor. */
internal data class TileWalker(val formId: Int, val shiny: Boolean, val name: String)

/** [content] in white, for writing on a dark wall. */
@Composable
private fun onWall(content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    val white = Color.White
    val soft = Color(0xFFE3EEF5)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides white) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = t.copy(titleSmall = t.titleSmall.copy(color = white), bodyLarge = t.bodyLarge.copy(color = white), bodyMedium = t.bodyMedium.copy(color = soft)),
        ) { content() }
    }
}

private val FRAME = 8.dp

private const val TILE_PX = 24
/** Rows of floor the team walks on. */
private const val STRIP_ROWS = 2

/** The tileset the Camp is drawn with: a woodland clearing that grows into a meadow, then an orchard, as the Camp is built up. */
internal fun campTiles(level: Int): String = when {
    level >= 4 -> "apple-woods"
    level >= 2 -> "lush-prairie"
    else -> "tiny-woods"
}

/**
 * The Camp's yard: the dialogue border round a clearing of tiles where the recruited Pokémon roam, each walking back and forth
 * at its own pace and facing the way it goes, on its own strip of floor. Still under Reduce motion.
 */
@Composable
internal fun CampYard(level: Int, residents: List<TileWalker>, animate: Boolean) {
    val tiles = campTiles(level)
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
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inner = maxWidth - FRAME * 2
        val pixel = (with(density) { inner.toPx() } / (TILE_PX * 8)).toInt().coerceAtLeast(1)
        val tilePx = TILE_PX * pixel
        val tile = with(density) { tilePx.toDp() }
        Box(Modifier.fillMaxWidth().height(tile * (YARD_ROWS) + FRAME * 2 - 2.dp).dialogueBox(fill = Color(0xFF141C28)).clip(DialogueShape)) {
            Box(Modifier.matchParentSize().padding(start = FRAME, end = FRAME, top = FRAME, bottom = FRAME - 2.dp).clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) {
                    val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
                    fun put(ax: Int, ay: Int, x: Int, y: Int) =
                        drawImage(image, IntOffset(ax * TILE_PX, ay * TILE_PX), IntSize(TILE_PX, TILE_PX), IntOffset(x, y), IntSize(tilePx, tilePx), filterQuality = FilterQuality.None)
                    for (r in 0..YARD_ROWS) for (c in 0..(size.width / tilePx).toInt() + 1) {
                        put(if (r == 0) 1 else 0, if (r == 0) 2 else 3, c * tilePx, r * tilePx)
                    }
                }
                val spriteSize = tile * 1.5f
                residents.take(8).forEachIndexed { i, p ->
                    // Each walks a sine path along its own strip of floor; the cosine says which way it faces.
                    val speed = 0.35f + (i * 7 % 5) * 0.08f
                    val phase = i * 1.7f
                    val angle = seconds * speed + phase
                    val span = (inner - spriteSize).value.coerceAtLeast(1f)
                    val x = span / 2f * (1f + kotlin.math.sin(angle))
                    val facingLeft = animate && kotlin.math.cos(angle) < 0f
                    val feet = tile * (1.5f + (i % 3) * 0.95f)
                    Box(Modifier.offset(x = x.dp, y = feet - spriteSize)) {
                        app.pokeplan.ui.AnimatedSprite(p.formId, p.shiny, p.name, size = spriteSize, animate = animate, ground = 0.dp, action = app.pokeplan.ui.SpriteAction.WALK_SIDE, mirror = facingLeft)
                    }
                }
            }
        }
    }
}

private const val YARD_ROWS = 5
