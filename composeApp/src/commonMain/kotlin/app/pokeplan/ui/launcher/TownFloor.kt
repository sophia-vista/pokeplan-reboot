package app.pokeplan.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.TownFinds
import app.pokeplan.ui.BitmapCache
import app.pokeplan.ui.CacheBudget
import app.pokeplan.ui.orNull
import app.pokeplan.ui.rememberFadeIn
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Which tileset a dungeon town draws (LAUNCHER.md 4.5): an Explorers of Sky one (`ex-NNN`) has its full autotile set; a named town that
 * is one of those dungeons draws from it too, so its walls and water take any shape; the rest (Red Rescue Team, the autumn forest) keep the 3 x 3 block.
 */
internal object TownTiles {
    private val explorersOf = mapOf(
        "lush-prairie" to "ex-090", "apple-woods" to "ex-005", "dusk-forest" to "ex-033", "deep-dusk-forest" to "ex-035", "drenched-bluff" to "ex-002",
        "craggy-coast" to "ex-006", "beach-cave" to "ex-001", "brine-cave" to "ex-039", "crystal-cave" to "ex-022", "crystal-cave-deep" to "ex-023",
        "crystal-crossing" to "ex-024", "dark-hill" to "ex-028", "dark-hill-deep" to "ex-029", "deep-sealed-ruin" to "ex-031", "concealed-ruins" to "ex-050",
        "amp-plains" to "ex-014",
    )

    /** The atlas the town's floor is drawn from. */
    fun source(scene: String): String = explorersOf[scene] ?: scene

    /** Whether that atlas has a full autotile set (`ex-NNN-auto.png`). */
    fun organic(scene: String): Boolean = source(scene).startsWith("ex-")

    /** The day's floor of [scene]: the same all day, another after New layout ([roll]). */
    fun seed(scene: String, epochDay: Long, roll: Int): Long = scene.hashCode().toLong() * 1_000_003L + epochDay * 7_919L + roll
}

/** The order of `assets/tiles/floor-items.png` (`FLOOR_ITEMS` in tools/tiles/extract_sky_floor.py): the column of each item's floor picture. */
internal val FLOOR_ITEM_COLUMNS = listOf(
    "berry-spicy", "berry-sour", "berry-sweet", "berry-dry", "berry-bitter", "hp-up", "protein", "iron", "calcium", "zinc", "carbos", TownFinds.POKE,
)

/** How [mon] gets about the floor ([Mover]): a Ghost type through walls, a Water or Flying type over water, the rest on foot. */
internal fun moverOf(mon: OwnedPokemon): Mover = Mover(
    water = PokemonType.WATER in mon.form.types || PokemonType.FLYING in mon.form.types,
    walls = PokemonType.GHOST in mon.form.types,
)

/**
 * Where the squares of a [floor] are on the town: [tilePx] screen pixels a square, [screenPx] a page, [heightPx] tall. A Pokémon's spot is
 * the town's fraction (x in pages, y down the whole height) of its feet, a little below the middle of its square.
 */
internal class FloorGeometry(val floor: DungeonFloor, val tilePx: Float, val screenPx: Float, val heightPx: Float) {
    fun spotOf(c: Cell): Offset = Offset((c.x + 0.5f) * tilePx / screenPx, (c.y + FEET) * tilePx / heightPx)

    fun cellOf(spot: Offset): Cell = Cell(
        (spot.x * screenPx / tilePx).toInt().coerceIn(0, floor.cols - 1),
        (spot.y * heightPx / tilePx - FEET + 0.5f).toInt().coerceIn(0, floor.rows - 1),
    )

    /** A square [mover] can stop on, at random from [random], on page [page] if it has one. */
    fun restingCell(random: Random, mover: Mover, page: Int? = null): Cell {
        val all = floor.restingCells(mover)
        val on = if (page == null) all else all.filter { it.x * tilePx / screenPx >= page && it.x * tilePx / screenPx < page + 1 }
        return (on.ifEmpty { all }.ifEmpty { listOf(floor.stairs) }).random(random)
    }

    /** The square nearest [wanted] that [mover] can stop on and nobody stands on ([taken]), searching outwards a few squares. */
    fun nearestResting(wanted: Cell, mover: Mover, taken: Set<Cell>): Cell? {
        for (r in 0..6) {
            val ring = (-r..r).flatMap { dy -> (-r..r).map { dx -> Cell(wanted.x + dx, wanted.y + dy) } }.filter { maxOf(kotlin.math.abs(it.x - wanted.x), kotlin.math.abs(it.y - wanted.y)) == r }
            ring.firstOrNull { floor.canRest(it, mover) && it !in taken }?.let { return it }
        }
        return null
    }

    companion object {
        /** The feet stand this far down a square (0 its top, 1 its bottom). */
        const val FEET = 0.75f

        fun of(floor: DungeonFloor, pixel: Int, screenPx: Float, heightPx: Float) = FloorGeometry(floor, (TILE * pixel).toFloat(), screenPx, heightPx)

        /** The floor's size in squares for a town of [screens] pages of [screenPx] by [heightPx] at [pixel]. */
        fun size(screenPx: Float, heightPx: Float, pixel: Int): Pair<Int, Int> {
            val tile = (TILE * pixel).toFloat()
            return ceil(screenPx / tile).toInt().coerceAtLeast(6) to ceil(heightPx / tile).toInt().coerceAtLeast(8)
        }
    }
}

private const val TILE = 24
private val floorImages = BitmapCache(CacheBudget.TILES)

@Composable
private fun floorImage(name: String): ImageBitmap? {
    val image by produceState(floorImages[name], name) {
        value = floorImages[name]
        if (value == null) {
            // Decoded off the main thread, so coming back to the home screen does not wait on it.
            value = orNull { Res.readBytes("files/tiles/$name.png").let { bytes -> withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() } } }
            floorImages[name] = value
        }
    }
    return image
}

/**
 * The dungeon floor drawn from the game's tiles: the ground (with its alternates), and the walls and water from the full autotile set
 * ([Autotile], one of the game's three variants a square) or, without one, the 3 x 3 block with its inner corners. One layer for the whole
 * town, worked out once per floor and size, so a walking sprite never draws every tile again.
 */
@Composable
internal fun FloorBackdrop(scene: String, floor: DungeonFloor, pixel: Int, weather: Weather, modifier: Modifier = Modifier) {
    val name = TownTiles.source(scene)
    val atlas = floorImage(name)
    val auto = if (floor.organic) floorImage("$name-auto") else null
    // The weather's colours, worked out off the main thread by [gradedFor] (once per tileset and weather).
    val shown by produceState(atlas, atlas, weather) { value = atlas?.let { gradedFor(name, it, weather) } }
    val shownAuto by produceState(auto, auto, weather) { value = auto?.let { gradedFor("$name-auto", it, weather) } }
    val fade by rememberFadeIn(atlas != null)
    // Which tile each square draws, worked out once per floor (not on every draw), and the drawing kept in a layer of its own whose alpha is
    // the fade-in: a recomposition (the clock's seconds) or a walking sprite never draws the tiles again.
    val tiles = remember(floor) {
        val seed = floor.stairs.hashCode()
        val map = floor.map
        IntArray(floor.rows * floor.cols * 3).also { out ->
            for (y in 0 until floor.rows) for (x in 0 until floor.cols) {
                val i = (y * floor.cols + x) * 3
                val cell = map.at(x, y)
                if (cell != TownCell.GROUND && floor.organic) {
                    val roll = ((x * 73856093) xor (y * 19349663) xor (seed * 83492791)).let { (it ushr 3) % 100 }
                    val variant = if (roll < 80) 0 else if (roll < 90) 1 else 2
                    out[i] = Autotile.column(map, x, y) * TILE; out[i + 1] = ((if (cell == TownCell.WATER) 3 else 0) + variant) * TILE; out[i + 2] = 1
                } else {
                    val (ax, ay) = map.atlas(x, y, seed)
                    out[i] = ax * TILE; out[i + 1] = ay * TILE; out[i + 2] = map.innerCorners(x, y) shl 1
                }
            }
        }
    }
    val draw = remember(floor, pixel, atlas, auto, tiles) {
        Modifier.graphicsLayer { alpha = fade }.drawWithCache {
            val tile = TILE * pixel
            onDrawBehind {
                val image = shown ?: atlas ?: return@onDrawBehind drawRect(PmdPalette.Loading)
                val autoImage = shownAuto ?: auto
                for (y in 0 until floor.rows) for (x in 0 until floor.cols) {
                    val dx = x * tile
                    if (dx > size.width) continue
                    val i = (y * floor.cols + x) * 3
                    val fromAuto = tiles[i + 2] == 1
                    if (fromAuto && autoImage == null) continue
                    drawImage(
                        if (fromAuto) autoImage!! else image, srcOffset = IntOffset(tiles[i], tiles[i + 1]), srcSize = IntSize(TILE, TILE),
                        dstOffset = IntOffset(dx, y * tile), dstSize = IntSize(tile, tile), filterQuality = FilterQuality.None,
                    )
                    val corners = tiles[i + 2] shr 1
                    if (!fromAuto && corners != 0) drawInnerCorners(image, corners, dx, y * tile, TILE, pixel)
                }
            }
        }
    }
    Box(modifier.then(draw))
}

/** A find just picked up: it rises and fades with its name. */
internal class Pickup(val cell: Cell, val column: Int, val label: String)

/**
 * What lies on the floor, over the tiles and under the Pokémon: the stairs, the traps found today (and the Wonder Tile, always shown) and
 * the day's [finds] (null for one already collected), with what was just picked up rising from its square. A screen reader gets each
 * find on Home as a button.
 */
@Composable
internal fun FloorThings(
    geo: FloorGeometry, pixel: Int, revealed: Set<Cell>, finds: List<TownFinds.Find?>, onScreen: (Cell) -> Boolean, onCollect: (Int) -> Unit,
    pickups: FloorPickups, modifier: Modifier = Modifier,
) {
    val traps = floorImage("floor-traps")
    val items = floorImage("floor-items")
    val floor = geo.floor
    val tile = (TILE * pixel).toFloat()
    Canvas(modifier) {
        fun trap(index: Int, c: Cell) {
            val image = traps ?: return
            drawImage(image, IntOffset(index * TILE, 0), IntSize(TILE, TILE), IntOffset((c.x * tile).toInt(), (c.y * tile).toInt()), IntSize(tile.toInt(), tile.toInt()), filterQuality = FilterQuality.None)
        }
        trap(STAIRS_TILE, floor.stairs)
        floor.traps.forEach { (c, t) -> if (t == FloorTrap.WONDER || c in revealed) trap(t.tile, c) }
        val image = items ?: return@Canvas
        finds.forEachIndexed { i, f ->
            val c = floor.finds.getOrNull(i) ?: return@forEachIndexed
            val column = f?.let { FLOOR_ITEM_COLUMNS.indexOf(it.itemId) } ?: return@forEachIndexed
            if (column < 0) return@forEachIndexed
            val px = (c.x * tile + (tile - 16 * pixel) / 2).toInt()
            val py = (c.y * tile + (tile - 16 * pixel) / 2).toInt()
            drawImage(image, IntOffset(column * 16, 0), IntSize(16, 16), IntOffset(px, py), IntSize(16 * pixel, 16 * pixel), filterQuality = FilterQuality.None)
        }
    }
    val density = LocalDensity.current.density
    finds.forEachIndexed { i, f ->
        val c = floor.finds.getOrNull(i) ?: return@forEachIndexed
        if (f == null || !onScreen(c)) return@forEachIndexed
        val name = if (f.isPoke) "${f.amount} Poké" else Items.name(f.itemId)
        Box(
            Modifier.offset { IntOffset((c.x * tile).toInt(), (c.y * tile).toInt()) }.size((tile / density).dp)
                .semantics { contentDescription = "$name on the floor"; role = Role.Button; onClick("Pick up $name") { onCollect(i); true } },
        )
    }
    pickups.list.forEach { p ->
        key(p) {
            val rise = remember { Animatable(0f) }
            LaunchedEffect(Unit) { rise.animateTo(1f, tween(1_100)); pickups.list.remove(p) }
            Box(Modifier.offset { IntOffset((p.cell.x * tile).toInt() - tile.toInt(), (p.cell.y * tile - rise.value * tile * 1.2f).toInt()) }.graphicsLayer { alpha = 1f - rise.value * rise.value }) {
                if (items != null && p.column >= 0) Canvas(Modifier.offset(x = ((tile + (tile - 16 * pixel) / 2) / density).dp).size((16 * pixel / density).dp)) {
                    drawImage(items, IntOffset(p.column * 16, 0), IntSize(16, 16), IntOffset.Zero, IntSize(16 * pixel, 16 * pixel), filterQuality = FilterQuality.None)
                }
                PmdText(p.label, pixel, Modifier.offset(y = ((16 * pixel + 2 * pixel) / density).dp))
            }
        }
    }
}

/** The finds picked up a moment ago, still rising from the floor. */
internal class FloorPickups {
    internal val list = mutableStateListOf<Pickup>()

    fun add(cell: Cell, find: TownFinds.Find) {
        list += Pickup(cell, FLOOR_ITEM_COLUMNS.indexOf(find.itemId), if (find.isPoke) "${find.amount} Poké" else Items.name(find.itemId))
    }
}
