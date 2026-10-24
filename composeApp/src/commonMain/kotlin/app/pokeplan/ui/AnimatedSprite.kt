package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    return (if (a.dir == "pmd") min(fit, (boxPx / PMD_REFERENCE_PIXELS).toInt()) else fit).coerceAtLeast(1)
}

private var animations: Map<Int, Animation>? = null
private val sheets = mutableMapOf<String, ImageBitmap?>()

/** A sheet table: `id, frames, width, height, comma separated frame times, shiny`. */
private suspend fun table(dir: String, file: String): Map<Int, Animation> = try {
    Res.readBytes("files/$dir/$file").decodeToString().lines().filter { it.isNotBlank() }.associate { line ->
        val c = line.split('\t')
        c[0].toInt() to Animation(dir, c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].split(',').map { it.toInt() }.toIntArray(), c[5] == "1")
    }
} catch (e: Exception) {
    emptyMap()
}

/**
 * Every form's animation, read once: the Mystery Dungeon style sprite (assets/pmd) where SpriteCollab has one, else the
 * Black/White animation (assets/animated).
 */
internal suspend fun animations(): Map<Int, Animation> {
    animations?.let { return it }
    val parsed = table("animated", "animated.tsv") + table("pmd", "pmd.tsv")
    animations = parsed
    return parsed
}

private var credits: Map<Int, String>? = null

/** Who made a form's SpriteCollab sprite (assets/pmd/credits.tsv), for the credit its licence asks for; null if it has none. */
suspend fun spriteCredit(formId: Int): String? {
    val all = credits ?: try {
        Res.readBytes("files/pmd/credits.tsv").decodeToString().lines().filter { '\t' in it }.associate { line ->
            line.substringBefore('\t').toInt() to line.substringAfter('\t')
        }
    } catch (e: Exception) {
        emptyMap()
    }.also { credits = it }
    return all[formId]
}

/** The animations if they have been read already, else null. */
internal fun loadedAnimations(): Map<Int, Animation>? = animations

/** A sheet if it has been decoded already, else null. */
internal fun loadedSheet(name: String): ImageBitmap? = sheets[name]

/** A sheet by [Animation.sheetName], decoded once. */
internal suspend fun sheet(name: String): ImageBitmap? {
    if (name in sheets) return sheets[name]
    val image = try {
        Res.readBytes("files/$name.png").decodeToImageBitmap()
    } catch (e: Exception) {
        null
    }
    sheets[name] = image
    return image
}

/**
 * A Pokémon with its idle animation (Mystery Dungeon style from SpriteCollab, or the Black/White one where SpriteCollab
 * has none), drawn at a whole-number scale so the pixels stay sharp, standing on the bottom edge of the [size] box
 * ([ground] up from it). Forms with neither show the still [Sprite]. With [animate] off it shows the first frame, for
 * "Reduce motion".
 */
@Composable
fun AnimatedSprite(
    formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, size: Dp = 144.dp,
    animate: Boolean = true, ground: Dp = 12.dp,
) {
    val animation by produceState(loadedAnimations()?.get(formId), formId) { value = animations()[formId] }
    val a = animation
    val name = a?.sheetName(formId, shiny)
    val sheet by produceState(name?.let { sheets[it] }, name) {
        if (value == null && name != null) value = sheet(name)
    }
    val image = sheet
    if (a == null || image == null) {
        Sprite(formId, shiny, description, modifier, size)
        return
    }
    var frame by remember(formId) { mutableIntStateOf(0) }
    LaunchedEffect(a, animate) {
        if (!animate) {
            frame = 0
            return@LaunchedEffect
        }
        var i = 0
        while (true) {
            frame = i
            delay(a.times[i].toLong())
            i = (i + 1) % a.frames
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val box = min(this.size.width, this.size.height - ground.toPx())
        val scale = pixelScale(box, a)
        val w = a.width * scale
        val h = a.height * scale
        drawImage(
            image,
            srcOffset = IntOffset(frame * a.width, 0), srcSize = IntSize(a.width, a.height),
            dstOffset = IntOffset(((this.size.width - w) / 2).toInt(), (this.size.height - ground.toPx() - h).toInt()), dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
}
