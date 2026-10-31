package app.pokeplan.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.pokeplan.app.BuddyScene
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.orNull
import app.pokeplan.ui.rememberFadeIn
import app.pokeplan.ui.theme.Pixel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Where the ground is on a scene backdrop, as fractions of the screen's height. */
private const val SCENE_TOP = 0.26f
private const val SCENE_BOTTOM = 0.56f

/** How many Pokémon walk the town at once: the buddy first, then the highest levels, so a big collection never slows the home screen. */
internal const val TOWN_CAP = 24

/** The size of a dungeon tile, in the pixels of the art. */
private const val TILE = 24

/** How many tiles wide the screen is, to the nearest whole pixel scale. */
private const val TILES_ACROSS = 14f

/**
 * The town's pixel scale: screen pixels per pixel of the art, a whole number. The tiles, the Pokémon and every position
 * use it, so all the art in the town has the same pixel size and sits on the same pixel grid.
 */
internal fun townScale(widthPx: Float): Int = (widthPx / (TILE * TILES_ACROSS)).roundToInt().coerceAtLeast(1)

/** Who lives in the town: the buddy, then the others by level (highest first), up to [TOWN_CAP]. */
internal fun townResidents(all: List<OwnedPokemon>, buddyId: String?): List<OwnedPokemon> =
    all.sortedWith(compareByDescending<OwnedPokemon> { it.id == buddyId }.thenByDescending { it.level }.thenBy { it.id }).take(TOWN_CAP)

/**
 * The home screen's town: a real scene or a town of a dungeon's tiles, with every Pokémon you own (up to [TOWN_CAP])
 * wandering its ground with its own idle and walking animations. A tap gives its cry and a hop. With Reduce motion they
 * stand still. Everything is drawn at one whole-number [townScale] without smoothing, on one pixel grid.
 */
@Composable
internal fun Town(model: AppModel, scene: String, active: Boolean, onMenu: (OwnedPokemon) -> Unit, modifier: Modifier = Modifier) {
    val data = model.data
    val residents = townResidents(data.player.pokemon, data.player.buddy?.id)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current.density
        val widthPx = maxWidth.value * density
        val heightPx = maxHeight.value * density
        val pixel = townScale(widthPx)
        TownBackdrop(scene, Modifier.fillMaxSize(), darkness = BuddyScene.darkness(model.store.now.hour, model.store.now.minute))
        val dungeon = TownScenes.isDungeon(scene)
        val top = if (dungeon) TownLayout.WALK_TOP else SCENE_TOP
        val bottom = if (dungeon) TownLayout.WALK_BOTTOM else SCENE_BOTTOM
        residents.forEachIndexed { i, mon ->
            key(mon.id) { Resident(model, mon, widthPx, heightPx, pixel, top, bottom, calm = data.settings.reduceMotion || !active, index = i, onMenu = onMenu) }
        }
    }
}

@Composable
private fun Resident(model: AppModel, mon: OwnedPokemon, widthPx: Float, heightPx: Float, pixel: Int, groundTop: Float, groundBottom: Float, calm: Boolean, index: Int, onMenu: (OwnedPokemon) -> Unit) {
    val random = remember(mon.id) { Random(mon.id.hashCode()) }
    // Position as fractions of the ground: x across the screen, y from the back of the ground (0) to the front (1).
    val spot = remember(mon.id) { Animatable(Offset(0.08f + random.nextFloat() * 0.84f, random.nextFloat()), Offset.VectorConverter) }
    var facingLeft by remember(mon.id) { mutableStateOf(false) }
    var hopKey by remember(mon.id) { mutableIntStateOf(0) }
    var hopping by remember(mon.id) { mutableStateOf(false) }
    // How it is walking now (null while it stands): to the side, towards the viewer or away, whichever way it mostly goes.
    var walk by remember(mon.id) { mutableStateOf<SpriteAction?>(null) }
    // Walking stops with Reduce motion and whenever the launcher is out of sight, and starts again when it is back.
    if (!calm) LaunchedEffect(mon.id) {
        delay(300L + index * 150L)
        while (true) {
            delay(1500L + random.nextLong(5000))
            val to = Offset(0.08f + random.nextFloat() * 0.84f, random.nextFloat())
            val dx = to.x - spot.value.x
            val dy = to.y - spot.value.y
            facingLeft = dx < 0
            // The ground is deeper than it looks (a step in depth covers more screen than a step across), so weigh it up before choosing a side or a front view.
            walk = if (abs(dx) * 2f >= abs(dy)) SpriteAction.WALK_SIDE else if (dy < 0) SpriteAction.WALK_UP else SpriteAction.WALK_DOWN
            // A steady stroll: about 6 seconds for the width of the screen.
            val seconds = 0.8f + abs(dx) * 6f + abs(dy) * 2f
            spot.animateTo(to, tween((seconds * 1000).toInt(), easing = LinearEasing))
            walk = null
        }
    }
    // Every Pokémon is drawn at the town's pixel scale in a box of three tiles, standing on its feet; the front ones are drawn over those behind.
    val box = TILE * 3 * pixel
    val depth = spot.value.y
    val feetX = snap(widthPx * spot.value.x, pixel)
    val feetY = snap(heightPx * groundTop + heightPx * (groundBottom - groundTop) * depth, pixel)
    val sizeDp = (box / LocalDensity.current.density).dp
    Box(
        Modifier.offset { IntOffset(feetX - box / 2, feetY - box) }.size(sizeDp).zIndex(depth)
            .pointerInput(mon.id) {
                detectTapGestures(onLongPress = { onMenu(mon) }) {
                    model.cry(mon.speciesId)
                    hopping = true
                    hopKey++
                }
            },
    ) {
        AnimatedSprite(
            mon.form.id, mon.isShiny, mon.nickname, Modifier.fillMaxSize(), size = sizeDp, animate = !calm, ground = 0.dp,
            action = if (hopping) SpriteAction.HOP else walk ?: SpriteAction.IDLE, actionKey = hopKey, onActionDone = { hopping = false },
            fixedScale = pixel, mirror = walk == SpriteAction.WALK_SIDE && facingLeft,
        )
    }
}

/** [value] rounded to a multiple of [pixel], so a position always falls on the pixel grid of the art. */
internal fun snap(value: Float, pixel: Int): Int = (value / pixel).roundToInt() * pixel

private val atlases = mutableMapOf<String, ImageBitmap?>()

/** The town's backdrop: a real Pokémon scene (whole-number scale, no smoothing), or a town laid out from a dungeon's tiles. */
@Composable
internal fun TownBackdrop(scene: String, modifier: Modifier = Modifier, darkness: Float = 0f) {
    if (TownScenes.isDungeon(scene)) TileTown(scene, modifier, darkness) else SceneImage(scene, modifier, darkness, crisp = true)
}

/**
 * A town of a dungeon's tiles (assets/tiles): [TownLayout]'s map drawn at the [townScale], without smoothing, centred on a
 * multiple of the scale, with the day's darkness over it like [SceneImage].
 */
@Composable
private fun TileTown(name: String, modifier: Modifier, darkness: Float) {
    val atlas by produceState(atlases[name], name) {
        if (value == null) {
            value = orNull { Res.readBytes("files/tiles/$name.png").decodeToImageBitmap() }
            atlases[name] = value
        }
    }
    val fade by rememberFadeIn(atlas != null)
    Canvas(modifier) {
        val image = atlas ?: return@Canvas drawRect(PmdPalette.Loading)
        val pixel = townScale(size.width)
        val tile = TILE * pixel
        val cols = (size.width / tile).toInt() + 2
        val rows = (size.height / tile).toInt() + 1
        val seed = name.hashCode()
        val map = TownLayout.build(cols, rows, seed)
        val shift = ((cols * tile - size.width) / 2).toInt() / pixel * pixel
        for (y in 0 until rows) for (x in 0 until cols) {
            val (ax, ay) = map.atlas(x, y, seed)
            drawImage(
                image, srcOffset = IntOffset(ax * TILE, ay * TILE), srcSize = IntSize(TILE, TILE),
                dstOffset = IntOffset(x * tile - shift, y * tile), dstSize = IntSize(tile, tile), alpha = fade, filterQuality = FilterQuality.None,
            )
        }
        if (darkness > 0f) drawRect(Pixel.Night.copy(alpha = darkness.coerceIn(0f, 1f)), Offset.Zero, Size(size.width, size.height))
    }
}
