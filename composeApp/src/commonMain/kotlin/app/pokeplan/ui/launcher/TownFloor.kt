package app.pokeplan.ui.launcher

import app.pokeplan.ui.StatusKind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.ui.zIndex
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
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

internal object TownTiles {
    private val explorersOf = mapOf(
        "lush-prairie" to "ex-090", "apple-woods" to "ex-005", "dusk-forest" to "ex-033", "deep-dusk-forest" to "ex-035", "drenched-bluff" to "ex-002",
        "craggy-coast" to "ex-006", "beach-cave" to "ex-001", "brine-cave" to "ex-039", "crystal-cave" to "ex-022", "crystal-cave-deep" to "ex-023",
        "crystal-crossing" to "ex-024", "dark-hill" to "ex-028", "dark-hill-deep" to "ex-029", "deep-sealed-ruin" to "ex-031", "concealed-ruins" to "ex-050",
        "amp-plains" to "ex-014",
    )

    fun source(scene: String): String = explorersOf[scene] ?: scene

    fun organic(scene: String): Boolean = source(scene).startsWith("ex-")

    fun seed(scene: String, epochDay: Long, roll: Int): Long = scene.hashCode().toLong() * 1_000_003L + epochDay * 7_919L + roll
}

internal val FLOOR_ITEM_COLUMNS = listOf(
    "berry-spicy", "berry-sour", "berry-sweet", "berry-dry", "berry-bitter", "hp-up", "protein", "iron", "calcium", "zinc", "carbos", TownFinds.POKE,
)

internal fun moverOf(mon: OwnedPokemon): Mover = Mover(
    water = PokemonType.WATER in mon.form.types || PokemonType.FLYING in mon.form.types,
    walls = PokemonType.GHOST in mon.form.types,
)

internal class FloorGeometry(val floor: DungeonFloor, val tilePx: Float, val screenPx: Float, val heightPx: Float) {
    fun spotOf(c: Cell): Offset = Offset((c.x + 0.5f) * tilePx / screenPx, (c.y + FEET) * tilePx / heightPx)

    fun cellOf(spot: Offset): Cell = Cell(
        (spot.x * screenPx / tilePx).toInt().coerceIn(0, floor.cols - 1),
        (spot.y * heightPx / tilePx - FEET + 0.5f).toInt().coerceIn(0, floor.rows - 1),
    )

    fun restingCell(random: Random, mover: Mover, page: Int? = null, crowded: (Cell) -> Boolean = { false }, roomsOnly: Boolean = false): Cell {
        val all = floor.restingCells(mover).let { cells -> if (roomsOnly) cells.filter { floor.roomOf(it) != null }.ifEmpty { cells } else cells }
        val on = (if (page == null) all else all.filter { it.x * tilePx / screenPx >= page && it.x * tilePx / screenPx < page + 1 }).ifEmpty { all }
        val spaced = on.filter { !crowded(it) }
        return (spaced.ifEmpty { on }.ifEmpty { listOf(floor.stairs) }).random(random)
    }

    fun nearestResting(wanted: Cell, mover: Mover, taken: Set<Cell>, crowded: (Cell) -> Boolean = { false }): Cell? {
        fun search(blocked: (Cell) -> Boolean): Cell? {
            for (r in 0..12) {
                val ring = (-r..r).flatMap { dy -> (-r..r).map { dx -> Cell(wanted.x + dx, wanted.y + dy) } }.filter { maxOf(kotlin.math.abs(it.x - wanted.x), kotlin.math.abs(it.y - wanted.y)) == r }
                ring.firstOrNull { floor.canRoam(it, mover) && !blocked(it) }?.let { return it }
            }
            return null
        }
        return search { it in taken || crowded(it) } ?: search { it in taken }
    }

    companion object {
        const val FEET = 0.75f

        fun of(floor: DungeonFloor, pixel: Int, screenPx: Float, heightPx: Float) = FloorGeometry(floor, (TILE * pixel).toFloat(), screenPx, heightPx)

        fun size(screenPx: Float, heightPx: Float, pixel: Int): Pair<Int, Int> {
            val tile = (TILE * pixel).toFloat()
            return ceil(screenPx / tile).toInt().coerceAtLeast(6) to ceil(heightPx / tile).toInt().coerceAtLeast(8)
        }
    }
}

private const val TILE = 24
private val floorImages = BitmapCache(CacheBudget.TILES)

@Composable
internal fun floorImage(name: String): ImageBitmap? {
    val image by produceState(floorImages[name], name) {
        value = floorImages[name]
        if (value == null) {
            value = orNull { Res.readBytes("files/tiles/$name.png").let { bytes -> withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() } } }
            floorImages[name] = value
        }
    }
    return image
}

@Composable
internal fun FloorBackdrop(scene: String, floor: DungeonFloor, pixel: Int, weather: Weather, modifier: Modifier = Modifier) {
    val name = TownTiles.source(scene)
    val atlas = floorImage(name)
    val auto = if (floor.organic) floorImage("$name-auto") else null
    val shown by produceState(atlas, atlas, weather) { value = atlas?.let { gradedFor(name, it, weather) } }
    val shownAuto by produceState(auto, auto, weather) { value = auto?.let { gradedFor("$name-auto", it, weather) } }
    val fade by rememberFadeIn(atlas != null)
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

internal class Pickup(val cell: Cell, val column: Int, val label: String)

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
            // above every Pokémon (they sort by depth, 0 to 1), as the games draw a pick-up's name over the monsters
            Box(Modifier.zIndex(PICKUP_LAYER).offset { IntOffset((p.cell.x * tile).toInt() - tile.toInt(), (p.cell.y * tile - rise.value * tile * 1.2f).toInt()) }.graphicsLayer { alpha = 1f - rise.value * rise.value }) {
                if (items != null && p.column >= 0) Canvas(Modifier.offset(x = ((tile + (tile - 16 * pixel) / 2) / density).dp).size((16 * pixel / density).dp)) {
                    drawImage(items, IntOffset(p.column * 16, 0), IntSize(16, 16), IntOffset.Zero, IntSize(16 * pixel, 16 * pixel), filterQuality = FilterQuality.None)
                }
                PmdText(p.label, pixel, Modifier.offset(y = ((16 * pixel + 2 * pixel) / density).dp))
            }
        }
    }
}

/** Layers above the Pokémon, which sort by depth in 0 to 1: a pick-up's name, then a trap's effect, then the weather (10). */
internal const val PICKUP_LAYER = 4f
internal const val TRAP_FX_LAYER = 5f

internal class FloorPickups {
    internal val list = mutableStateListOf<Pickup>()

    fun add(cell: Cell, find: TownFinds.Find) {
        list += Pickup(cell, FLOOR_ITEM_COLUMNS.indexOf(find.itemId), if (find.isPoke) "${find.amount} Poké" else Items.name(find.itemId))
    }
}

internal enum class TrapFx(val file: String, val width: Int, val height: Int, val originX: Int, val originY: Int, val ticks: List<Int>) {
    SPIN("trap-spin", 16, 16, 8, 12, listOf(1, 1, 3, 3, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 1, 1, 1)),
    GUST("trap-gust", 56, 80, 28, 74, listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
    SLOW("trap-slow", 32, 240, 16, 231, listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 30, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
    ;

    companion object {
        fun of(trap: FloorTrap): TrapFx? = when (trap) {
            FloorTrap.SPIN -> SPIN
            FloorTrap.GUST -> GUST
            FloorTrap.SLOW -> SLOW
            else -> null
        }
    }
}

@Composable
internal fun TrapEffect(fx: TrapFx, feet: () -> Offset, pixel: Int, onDone: () -> Unit) {
    val strip = floorImage(fx.file)
    var frame by remember(fx) { mutableIntStateOf(0) }
    LaunchedEffect(fx, strip != null) {
        if (strip == null) return@LaunchedEffect
        for (i in fx.ticks.indices) { frame = i; delay(fx.ticks[i] * 1000L / 60L) }
        onDone()
    }
    val image = strip ?: return
    val density = LocalDensity.current.density
    Canvas(
        Modifier.offset { feet().let { IntOffset(it.x.toInt() - fx.originX * pixel, it.y.toInt() - fx.originY * pixel) } }
            .size((fx.width * pixel / density).dp, (fx.height * pixel / density).dp).zIndex(TRAP_FX_LAYER),
    ) {
        drawImage(image, IntOffset(frame * fx.width, 0), IntSize(fx.width, fx.height), IntOffset.Zero, IntSize(fx.width * pixel, fx.height * pixel), filterQuality = FilterQuality.None)
    }
}

internal enum class Condition(val icon: StatusKind, val seconds: Int, val pace: Float) {
    CONFUSED(StatusKind.QUESTION, 25, 1f), POISONED(StatusKind.SKULL, 30, 0.7f),
}
