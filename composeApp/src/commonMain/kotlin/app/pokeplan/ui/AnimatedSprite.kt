package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.min

/**
 * One animated sprite: [frames] side by side in a sheet of [width] x [height] frames, each shown for the matching entry of
 * [times] (ms). The sheets are files/[dir]/<id>.png and <id>-shiny.png.
 */
internal class Animation(val dir: String, val frames: Int, val width: Int, val height: Int, val times: IntArray, val hasShiny: Boolean) {
    fun sheetName(formId: Int, shiny: Boolean) = if (shiny && hasShiny) "$dir/$formId-shiny" else "$dir/$formId"
}

/**
 * Mystery Dungeon sprites share one pixel size, as in the games: the scale fits a frame of this many pixels (seven in ten
 * sheets are this size or smaller) into the space, so a Weedle stays smaller than a Snorlax, and only bigger sprites step
 * down to fit.
 */
private const val PMD_REFERENCE_PIXELS = 32

/** The whole-number scale to draw [a]'s frames at in a square of [boxPx]. */
internal fun pixelScale(boxPx: Float, a: Animation): Int {
    val fit = min(boxPx / a.width, boxPx / a.height).toInt()
    return (if (a.dir.startsWith("pmd")) min(fit, (boxPx / PMD_REFERENCE_PIXELS).toInt()) else fit).coerceAtLeast(1)
}

private var animations: Map<Int, Animation>? = null
private val sheets = BitmapCache(CacheBudget.SPRITE_SHEETS)

/** A sheet table: `id, frames, width, height, comma separated frame times, shiny`. */
private suspend fun table(dir: String, file: String): Map<Int, Animation> = withContext(Dispatchers.Default) {
    orNull {
        Res.readBytes("files/$dir/$file").decodeToString().lines().filter { it.isNotBlank() }.associate { line ->
            val c = line.split('\t')
            c[0].toInt() to Animation(dir, c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray(), c[5] == "1")
        }
    } ?: emptyMap()
}

/**
 * Every form's animation, read once: the Mystery Dungeon style sprite (assets/pmd) where SpriteCollab has one, else the
 * Black/White animation (assets/animated).
 */
private val tablesLock = Mutex()

internal suspend fun animations(): Map<Int, Animation> {
    animations?.let { return it }
    return tablesLock.withLock {
        animations ?: (table("animated", "animated.tsv") + table("pmd", "pmd.tsv")).also { animations = it }
    }
}

private var credits: Map<Int, String>? = null

/** Who made a form's SpriteCollab sprite (assets/pmd/credits.tsv), for the credit its licence asks for; null if it has none. */
suspend fun spriteCredit(formId: Int): String? {
    val all = credits ?: withContext(Dispatchers.Default) {
        orNull {
            Res.readBytes("files/pmd/credits.tsv").decodeToString().lines().filter { '\t' in it }.associate { line ->
                line.substringBefore('\t').toInt() to line.substringAfter('\t')
            }
        } ?: emptyMap()
    }.also { credits = it }
    return all[formId]
}

/**
 * What a Pokémon is doing: each action is one of its SpriteCollab animations (assets/pmd/<folder>). [plays] is how many
 * times a one-off action runs before it hands back to idle; 0 loops it.
 */
enum class SpriteAction(internal val folder: String?, internal val plays: Int, internal val stand: SpriteAction? = null) {
    IDLE(null, 0), SLEEP("sleep", 0), HOP("hop", 1),
    /** A bite; a form without an Eat row (most of them) hops for it. */
    EAT("eat", 3, stand = HOP),
    /** Walking towards the viewer, to the side (facing right; mirror it for left) and away: they loop. */
    WALK_DOWN("walk-down", 0), WALK_SIDE("walk-side", 0), WALK_UP("walk-up", 0),
    /** Standing still facing the side (right; mirror it for left) or away, where a form has those rows: a Pokémon that stopped walking keeps its direction. They loop; a form without them shows its front idle. */
    IDLE_SIDE("idle-side", 0), IDLE_UP("idle-up", 0),
    /** One-offs for a moment: a spin (a level up, an evolution), waking up, a pose (a hatch, a rank up, a recruit) and a deep breath. A form without the row hops instead, except for the breath. */
    ROTATE("rotate", 2, stand = HOP), WAKE("wake", 1, stand = HOP), POSE("pose", 1, stand = HOP), DEEP_BREATH("deep-breath", 1),
}

private val actionTables = mutableMapOf<SpriteAction, Map<Int, Animation>>()

/** The form's own animation for [action] (else its stand-in's, a hop for a bite), or null without one (for a shiny, without its shiny recolour). */
internal suspend fun actionAnimation(formId: Int, shiny: Boolean, action: SpriteAction): Animation? {
    val folder = action.folder ?: return null
    val all = actionTables[action] ?: table("pmd/$folder", "$folder.tsv").also { actionTables[action] = it }
    // The regular colours on a shiny would look like a different Pokémon for the length of the action.
    return all[formId]?.takeIf { !shiny || it.hasShiny } ?: action.stand?.let { actionAnimation(formId, shiny, it) }
}

/** Whether the form has its own animation for [action], so a screen can skip the stand-in it draws otherwise (a hop). */
suspend fun hasAction(formId: Int, shiny: Boolean, action: SpriteAction): Boolean =
    action == SpriteAction.IDLE || actionAnimation(formId, shiny, action) != null

/** The animations if they have been read already, else null. */
internal fun loadedAnimations(): Map<Int, Animation>? = animations

/** A sheet if it has been decoded already, else null. */
internal fun loadedSheet(name: String): ImageBitmap? = sheets[name]

/** Sheets being decoded now, so several sprites asking for the same one share a single read. Main-thread only, like the cache. */
private val decoding = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()

/** A sheet by [Animation.sheetName], decoded once, off the UI dispatcher. */
internal suspend fun sheet(name: String): ImageBitmap? {
    sheets[name]?.let { return it }
    decoding[name]?.let { return it.await() }
    val pending = CompletableDeferred<ImageBitmap?>()
    decoding[name] = pending
    return try {
        val image = withContext(Dispatchers.Default) { orNull { Res.readBytes("files/$name.png").decodeToImageBitmap() } }
        sheets[name] = image
        pending.complete(image)
        image
    } catch (e: Throwable) {
        pending.complete(null)
        throw e
    } finally {
        decoding.remove(name)
    }
}

/** The animation picked for one form and action, with its decoded sheet (both null when the form has no such animation). */
private class Picked(val formId: Int, val shiny: Boolean, val action: SpriteAction, val animation: Animation?, val sheet: ImageBitmap?)

/**
 * A Pokémon with its idle animation (Mystery Dungeon style from SpriteCollab, or the Black/White one where SpriteCollab
 * has none), drawn at a whole-number scale so the pixels stay sharp, standing on the bottom edge of the [size] box
 * ([ground] up from it). Forms with neither show the still [Sprite]. With [animate] off it shows the first frame, for
 * "Reduce motion".
 *
 * [action] plays another of its SpriteCollab animations instead, at the idle one's size: [SpriteAction.SLEEP] loops, and
 * a one-off ([SpriteAction.HOP], [SpriteAction.EAT]) runs its [SpriteAction.plays] and then calls [onActionDone]; a new
 * [actionKey] plays it again. A form without that animation stays idle and calls [onActionDone] at once.
 */
@Composable
fun AnimatedSprite(
    formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 144.dp,
    animate: Boolean = true, ground: Dp = 12.dp,
    action: SpriteAction = SpriteAction.IDLE, actionKey: Int = 0, onActionDone: () -> Unit = {},
    /** A fixed whole-number scale (screen pixels per sprite pixel) instead of one fitted to [size], with the sprite kept on that pixel grid, for scenes drawn on a grid of their own. */
    fixedScale: Int? = null,
    /** Draws the frames flipped left to right, pixel for pixel. */
    mirror: Boolean = false,
) {
    val animation by produceState(loadedAnimations()?.get(formId), formId) { value = animations()[formId] }
    val idle = animation
    val name = idle?.sheetName(formId, shiny)
    // Paired with its name, so a sheet still being read can be told from one that is missing.
    val sheet by produceState<Pair<String, ImageBitmap?>?>(name?.let { n -> sheets[n]?.let { n to it } }, name) {
        // Not only when nothing is held: after an evolution the same sprite shows another form, and the sheet held is the old one's.
        if (name != null && value?.first != name) value = name to sheet(name)
    }
    val loaded = sheet?.takeIf { it.first == name }
    val idleImage = loaded?.second
    val picked by produceState<Picked?>(null, formId, shiny, action) {
        val a = actionAnimation(formId, shiny, action)
        value = Picked(formId, shiny, action, a, a?.let { sheet(it.sheetName(formId, shiny)) })
    }
    // Only the pick made for what is asked now: the old one stays in the state for a moment after a change.
    val current = picked?.takeIf { it.formId == formId && it.shiny == shiny && it.action == action }
    val special = current?.takeIf { it.animation != null && it.sheet != null }
    val done by rememberUpdatedState(onActionDone)
    LaunchedEffect(current, actionKey) {
        if (current != null && special == null && action.plays > 0) done()
    }

    if (idle != null && loaded == null) {
        // A moment of empty space while the sheet is read: the still sprite would flash in another art style first.
        Box(modifier.size(size).semantics { contentDescription = description })
        return
    }
    if (idle == null || idleImage == null) {
        Sprite(formId, shiny, description, modifier, size)
        return
    }
    val a = special?.animation ?: idle
    val image = special?.sheet ?: idleImage
    var frame by remember(formId) { mutableIntStateOf(0) }
    // A stopped Activity keeps its composition: without this gate every visible sprite would keep waking the CPU at
    // its frame rate with the screen off. Coming back to the front restarts the animation from its first frame.
    val foreground = rememberInForeground()
    LaunchedEffect(a, animate, actionKey, foreground) {
        val oneOff = special != null && action.plays > 0
        frame = 0
        if (!animate) {
            if (oneOff) done()
            return@LaunchedEffect
        }
        if (!foreground) return@LaunchedEffect
        var i = 0
        var loops = 0
        while (true) {
            frame = i
            delay(a.times[i].toLong())
            i = (i + 1) % a.frames
            if (i == 0 && oneOff && ++loops == action.plays) done()
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val box = min(this.size.width, this.size.height - ground.toPx())
        // Every action draws at the idle sprite's scale, so the Pokémon never changes size when it lies down, eats or hops.
        // A hop's frames are taller than the idle ones (they hold the jump), so the top of a jump may reach above the box.
        val scale = fixedScale ?: pixelScale(box, idle)
        val w = a.width * scale
        val h = a.height * scale
        val left = ((this.size.width - w) / 2).toInt().let { if (fixedScale != null) it / fixedScale * fixedScale else it }
        val top = (this.size.height - ground.toPx() - h).toInt().let { if (fixedScale != null) it / fixedScale * fixedScale else it }
        withTransform({ if (mirror) scale(-1f, 1f, Offset(this.size.width / 2f, 0f)) }) {
            drawImage(
                image,
                srcOffset = IntOffset(frame.coerceAtMost(a.frames - 1) * a.width, 0), srcSize = IntSize(a.width, a.height),
                dstOffset = IntOffset(left, top), dstSize = IntSize(w, h),
                filterQuality = FilterQuality.None,
            )
        }
    }
}
