package app.pokeplan.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.orNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res

/** One glyph of the dialogue font: its box in the sheet and how far its top is below the baseline (negative: above). */
internal class PmdGlyph(val x: Int, val y: Int, val w: Int, val h: Int, val dy: Int)

/**
 * The Pokémon dialogue bitmap font (assets/fonts/pmd-dialogue.png, measured into pmd-dialogue.tsv by tools/fonts/cut_pmd_font.py):
 * white letters with a black shadow, drawn at a whole-number scale without smoothing. It has the Latin letters, digits and a
 * few marks; text with anything else is drawn in the app's own font instead.
 */
internal class PmdFont(val sheet: ImageBitmap, val glyphs: Map<Char, PmdGlyph>) {
    fun supports(text: String) = text.all { it == ' ' || normalize(it) in glyphs }

    /** The width of [text] in pixels of the art. */
    fun width(text: String) = text.sumOf { if (it == ' ') SPACE else glyphs.getValue(normalize(it)).w }

    companion object {
        const val SPACE = 4
        /** The height of a line in pixels of the art: the capitals' height plus room for the descenders. */
        const val LINE = 12
        /** The baseline's distance from the top of a line. */
        const val BASELINE = 9
        private var cached: PmdFont? = null

        fun normalize(c: Char): Char = when (c) { '\'' -> '’'; '"' -> '”'; else -> c }

        suspend fun load(): PmdFont? = cached ?: orNull {
            val sheet = Res.readBytes("files/fonts/pmd-dialogue.png").decodeToImageBitmap()
            val glyphs = Res.readBytes("files/fonts/pmd-dialogue.tsv").decodeToString().lineSequence().filter { it.isNotBlank() }.associate { line ->
                val f = line.split('\t')
                f[0].single() to PmdGlyph(f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt())
            }
            PmdFont(sheet, glyphs)
        }?.also { cached = it }
    }
}

/** [text] in the dialogue font at [pixel] screen pixels per art pixel (white; [alpha] softens it), or in the app's own font if the font cannot draw it. */
@Composable
internal fun PmdText(text: String, pixel: Int, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val font by produceState<PmdFont?>(null) { value = PmdFont.load() }
    val f = font
    if (f == null || !f.supports(text)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = alpha), maxLines = 1, modifier = modifier)
        return
    }
    val density = LocalDensity.current.density
    Canvas(modifier.size((f.width(text) * pixel / density).dp, (PmdFont.LINE * pixel / density).dp)) {
        var cursor = 0
        for (ch in text) {
            if (ch == ' ') { cursor += PmdFont.SPACE; continue }
            val g = f.glyphs.getValue(PmdFont.normalize(ch))
            drawImage(
                f.sheet, srcOffset = IntOffset(g.x, g.y), srcSize = IntSize(g.w, g.h),
                dstOffset = IntOffset(cursor * pixel, (PmdFont.BASELINE + g.dy) * pixel), dstSize = IntSize(g.w * pixel, g.h * pixel),
                alpha = alpha, filterQuality = FilterQuality.None,
            )
            cursor += g.w
        }
    }
}

/** The colours of a dialogue box's frame. Every panel uses the same one; selection and the main action show in other ways. */
internal enum class PmdTone(val outer: Color, val inner: Color) {
    BLUE(Color(0xFF5088F0), Color(0xFF102058)),
}

/**
 * A dialogue box like the game's: a near-black panel with a two-pixel frame and notched corners, every part a whole number
 * of art pixels at [pixel]. The frame is drawn in code (UI chrome); the text inside is the dialogue font.
 */
@Composable
internal fun PmdBox(pixel: Int, modifier: Modifier = Modifier, tone: PmdTone = PmdTone.BLUE, inset: Int = 6, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.drawBehind { pmdFrame(pixel, tone) }.padding(horizontal = ((inset + 2) * pixel / density).dp, vertical = (inset * pixel / density).dp),
        content = content,
    )
}

private fun DrawScope.pmdFrame(p: Int, tone: PmdTone) {
    val w = size.width
    val h = size.height
    val fill = Color(0xEE080810)
    fun rect(x: Float, y: Float, rw: Float, rh: Float, c: Color) = drawRect(c, Offset(x, y), Size(rw, rh))
    // Panel without its corner pixels, then the outer ring, then the inner ring one pixel in.
    rect(p.toFloat(), 0f, w - 2 * p, h, fill)
    rect(0f, p.toFloat(), w, h - 2 * p, fill)
    rect(p.toFloat(), 0f, w - 2 * p, p.toFloat(), tone.outer)
    rect(p.toFloat(), h - p, w - 2 * p, p.toFloat(), tone.outer)
    rect(0f, p.toFloat(), p.toFloat(), h - 2 * p, tone.outer)
    rect(w - p, p.toFloat(), p.toFloat(), h - 2 * p, tone.outer)
    rect(2f * p, p.toFloat(), w - 4 * p, p.toFloat(), tone.inner)
    rect(2f * p, h - 2 * p, w - 4 * p, p.toFloat(), tone.inner)
    rect(p.toFloat(), 2f * p, p.toFloat(), h - 4 * p, tone.inner)
    rect(w - 2 * p, 2f * p, p.toFloat(), h - 4 * p, tone.inner)
}

/** One line of a dialogue menu: a touch target at least 48 dp tall with [text] in it, a small cursor in front when [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PmdMenuLine(text: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, center: Boolean = false, pixelText: Boolean = true, onHold: (() -> Unit)? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).combinedClickable(role = Role.Button, onLongClick = onHold, onClick = onClick),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (selected) Canvas(Modifier.size((3 * pixel / density).dp, (5 * pixel / density).dp)) {
            val p = pixel.toFloat()
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(3 * p, 2.5f * p); lineTo(0f, 5 * p); close() }, Color.White)
        }
        val start = Modifier.padding(start = if (center) 0.dp else (5 * pixel / density).dp)
        if (pixelText) PmdText(text, pixel, start, alpha = if (dim) 0.75f else 1f)
        else Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = if (dim) 0.75f else 1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = start)
    }
}

/** A one-pixel rule across a dialogue box, between its parts. */
@Composable
internal fun PmdRule(pixel: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    Box(modifier.fillMaxWidth().height((pixel / density).dp).background(Color.White.copy(alpha = 0.3f)))
}

/** The one main action of a box, in Poké Ball red with a darker lower edge: at least 48 dp tall, the label in the dialogue font. */
@Composable
internal fun PmdButton(text: String, pixel: Int, modifier: Modifier = Modifier, color: Color = Color(0xFFE84436), onClick: () -> Unit) {
    val edge = Color(color.red * 0.7f, color.green * 0.7f, color.blue * 0.7f)
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .drawBehind {
                val p = pixel.toFloat()
                drawRect(edge, Offset(0f, p), Size(size.width, size.height - p))
                drawRect(color, Offset(0f, 0f), Size(size.width, size.height - 2 * p))
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PmdText(text, pixel) }
}
