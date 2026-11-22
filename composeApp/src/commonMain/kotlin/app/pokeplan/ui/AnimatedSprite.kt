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

internal class Animation(val dir: String, val frames: Int, val width: Int, val height: Int, val times: IntArray, val hasShiny: Boolean) {
    fun sheetName(formId: Int, shiny: Boolean) = if (shiny && hasShiny) "$dir/$formId-shiny" else "$dir/$formId"
}

private const val PMD_REFERENCE_PIXELS = 32

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

enum class SpriteAction(internal val folder: String?, internal val plays: Int, internal val stand: SpriteAction? = null) {
    IDLE(null, 0), SLEEP("sleep", 0), HOP("hop", 1),
    EAT("eat", 3, stand = HOP),
    WALK_DOWN("walk-down", 0), WALK_SIDE("walk-side", 0), WALK_UP("walk-up", 0),
    IDLE_SIDE("idle-side", 0), IDLE_UP("idle-up", 0),
    ROTATE("rotate", 2, stand = HOP), WAKE("wake", 1, stand = HOP), POSE("pose", 1, stand = HOP), DEEP_BREATH("deep-breath", 1),
}

private val actionTables = mutableMapOf<SpriteAction, Map<Int, Animation>>()

internal suspend fun actionAnimation(formId: Int, shiny: Boolean, action: SpriteAction): Animation? {
    val folder = action.folder ?: return null
    val all = actionTables[action] ?: table("pmd/$folder", "$folder.tsv").also { actionTables[action] = it }
    return all[formId]?.takeIf { !shiny || it.hasShiny } ?: action.stand?.let { actionAnimation(formId, shiny, it) }
}

suspend fun hasAction(formId: Int, shiny: Boolean, action: SpriteAction): Boolean =
    action == SpriteAction.IDLE || actionAnimation(formId, shiny, action) != null

internal fun loadedAnimations(): Map<Int, Animation>? = animations

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

private class Picked(val formId: Int, val shiny: Boolean, val action: SpriteAction, val animation: Animation?, val sheet: ImageBitmap?)

@Composable
fun AnimatedSprite(
    formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 144.dp,
    animate: Boolean = true, ground: Dp = 12.dp,
    action: SpriteAction = SpriteAction.IDLE, actionKey: Int = 0, onActionDone: () -> Unit = {},
    fixedScale: Int? = null,
    mirror: Boolean = false,
) {
    val animation by produceState(loadedAnimations()?.get(formId), formId) { value = animations()[formId] }
    val idle = animation
    val name = idle?.sheetName(formId, shiny)
    val sheet by produceState<Pair<String, ImageBitmap?>?>(name?.let { n -> sheets[n]?.let { n to it } }, name) {
        if (name != null && value?.first != name) value = name to sheet(name)
    }
    val loaded = sheet?.takeIf { it.first == name }
    val idleImage = loaded?.second
    val picked by produceState<Picked?>(null, formId, shiny, action) {
        val a = actionAnimation(formId, shiny, action)
        value = Picked(formId, shiny, action, a, a?.let { sheet(it.sheetName(formId, shiny)) })
    }
    val current = picked?.takeIf { it.formId == formId && it.shiny == shiny && it.action == action }
    val special = current?.takeIf { it.animation != null && it.sheet != null }
    val done by rememberUpdatedState(onActionDone)
    LaunchedEffect(current, actionKey) {
        if (current != null && special == null && action.plays > 0) done()
    }

    if (idle != null && loaded == null) {
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
