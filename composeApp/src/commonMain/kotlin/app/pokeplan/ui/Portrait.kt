package app.pokeplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import pokeplan.composeapp.generated.resources.Res

enum class Emotion(val file: String) {
    NORMAL("Normal"), HAPPY("Happy"), JOYOUS("Joyous"), INSPIRED("Inspired"), DETERMINED("Determined"), SURPRISED("Surprised"),
    SAD("Sad"), ANGRY("Angry"), WORRIED("Worried"), TEARY("Teary-Eyed"), PAIN("Pain"), SIGH("Sigh"), DIZZY("Dizzy"), STUNNED("Stunned"),
    ;

    internal val stand: List<Emotion> get() = when (this) {
        SAD -> listOf(TEARY, WORRIED); TEARY -> listOf(SAD, WORRIED); WORRIED -> listOf(SURPRISED, SAD)
        ANGRY -> listOf(DETERMINED); PAIN -> listOf(SAD, SURPRISED); SIGH -> listOf(SAD)
        DIZZY -> listOf(STUNNED, SURPRISED); STUNNED -> listOf(SURPRISED); else -> emptyList()
    }
}

private class Faces(val emotions: List<String>, val hasShiny: Boolean, val artists: String, val shinyEmotions: List<String>? = null)

private const val PORTRAIT_PIXELS = 40
private var faces: Map<Int, Faces>? = null

private suspend fun faces(): Map<Int, Faces> = faces ?: (
    orNull {
        Res.readBytes("files/pmd/portraits/portraits.tsv").decodeToString().lines().filter { '\t' in it }.associate { line ->
            val c = line.split('\t')
            c[0].toInt() to Faces(c[1].split(','), c[2] == "1", c.getOrElse(3) { "" }, c.getOrNull(4)?.takeIf { it.isNotBlank() }?.split(','))
        }
    } ?: emptyMap()
).also { faces = it }

suspend fun portraitCredit(formId: Int): String? = faces()[formId]?.artists?.takeIf { it.isNotBlank() }

private class Face(val sheet: ImageBitmap, val index: Int)

private suspend fun face(formId: Int, shiny: Boolean, emotion: Emotion): Face? {
    val f = faces()[formId] ?: return null
    if (shiny && !f.hasShiny) return null
    val has = if (shiny) f.shinyEmotions ?: f.emotions else f.emotions
    val index = (listOf(emotion) + emotion.stand).firstNotNullOfOrNull { e -> has.indexOf(e.file).takeIf { it >= 0 } } ?: 0
    val image = sheet(if (shiny) "pmd/portraits/$formId-shiny" else "pmd/portraits/$formId") ?: return null
    return Face(image, index)
}

@Composable
fun Portrait(formId: Int, shiny: Boolean, description: String, modifier: Modifier = Modifier, emotion: Emotion = Emotion.NORMAL, size: Dp = 64.dp, framed: Boolean = true) {
    val face by produceState<Pair<Any, Face?>?>(null, formId, shiny, emotion) { value = Triple(formId, shiny, emotion) to face(formId, shiny, emotion) }
    val loaded = face?.takeIf { it.first == Triple(formId, shiny, emotion) }
    val f = loaded?.second
    if (loaded != null && f == null) {
        Sprite(formId, shiny, description, modifier, size)
        return
    }
    val frame = Pixel.Outline
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        if (f == null) return@Canvas
        val border = if (framed) 2.dp.toPx().coerceAtLeast(1f) else 0f
        val scale = ((kotlin.math.min(this.size.width, this.size.height) - 2 * border) / PORTRAIT_PIXELS).toInt().coerceAtLeast(1)
        val side = PORTRAIT_PIXELS * scale
        val left = ((this.size.width - side) / 2).toInt()
        val top = ((this.size.height - side) / 2).toInt()
        drawImage(
            f.sheet, srcOffset = IntOffset(f.index * PORTRAIT_PIXELS, 0), srcSize = IntSize(PORTRAIT_PIXELS, PORTRAIT_PIXELS),
            dstOffset = IntOffset(left, top), dstSize = IntSize(side, side), filterQuality = FilterQuality.None,
        )
        if (framed) drawRect(frame, Offset(left - border / 2, top - border / 2), Size(side + border, side + border), style = Stroke(border))
    }
}
