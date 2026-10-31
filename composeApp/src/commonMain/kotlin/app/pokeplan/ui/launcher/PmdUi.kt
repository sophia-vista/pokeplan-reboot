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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Canvas(modifier.size((f.width(text) * pixel / density).dp, (PmdFont.LINE * pixel / density).dp).semantics { contentDescription = text }) {
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
internal object PmdPalette {
    /** The pale hairline on the outside of the frame. */
    val Edge = Color(0xFFA8C0E0)
    /** The frame's main band. */
    val Band = Color(0xFF6080F8)
    /** The one-pixel line between the band and the inside. */
    val Deep = Color(0xFF4820F8)
    /** The inside: black, see-through, so the town shows behind the words. */
    val Fill = Color(0xA6000010)
    /** Behind a town while its tiles load. */
    val Loading = Color(0xFF3B4A3A)
}

/** The frame's colours: the pale hairline, the band and the deep line. Every panel uses the same ones. */
internal enum class PmdTone(val edge: Color, val band: Color, val deep: Color) {
    BLUE(PmdPalette.Edge, PmdPalette.Band, PmdPalette.Deep),
}

/**
 * A dialogue box like the game's: a see-through black panel in a rounded, layered frame (a pale hairline, a blue band that is
 * thicker at the sides than at the top and bottom, and a deep-blue line), every part a whole number of art pixels at [pixel].
 * [inset] is the room, in art pixels, between the frame and the words. The frame is drawn in code (UI chrome).
 */
@Composable
internal fun PmdBox(pixel: Int, modifier: Modifier = Modifier, tone: PmdTone = PmdTone.BLUE, inset: Int = 6, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.drawBehind { pmdFrame(pixel, tone) }
            .padding(horizontal = ((SIDE + inset) * pixel / density).dp, vertical = ((TOP + inset / 2) * pixel / density).dp),
        content = content,
    )
}

/** The frame's thickness in art pixels: pale line 1 + band 3 + deep line 1 at the top and bottom, 1 + 5 + 1 at the sides. */
private const val TOP = 5
private const val SIDE = 7

/** A rectangle with stair-stepped (rounded) corners: [insets] is how far in each row from the corner starts, ending in 0. */
private fun DrawScope.stairRect(p: Float, l: Float, t: Float, r: Float, b: Float, insets: IntArray): Path = Path().apply {
    val n = insets.size
    moveTo(l + insets[0] * p, t)
    for (k in 0 until n) { lineTo(r - insets[k] * p, t + k * p); lineTo(r - insets[k] * p, t + (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(r - insets[k] * p, b - (k + 1) * p); lineTo(r - insets[k] * p, b - k * p) }
    for (k in 0 until n) { lineTo(l + insets[k] * p, b - k * p); lineTo(l + insets[k] * p, b - (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(l + insets[k] * p, t + (k + 1) * p); lineTo(l + insets[k] * p, t + k * p) }
    close()
}

private fun DrawScope.pmdFrame(pixel: Int, tone: PmdTone) {
    val p = pixel.toFloat()
    val w = size.width
    val h = size.height
    val outer = stairRect(p, 0f, 0f, w, h, intArrayOf(2, 1, 0))
    val band = stairRect(p, p, p, w - p, h - p, intArrayOf(1, 0))
    val deep = stairRect(p, 6 * p, 4 * p, w - 6 * p, h - 4 * p, intArrayOf(1, 0))
    val fill = stairRect(p, 7 * p, 5 * p, w - 7 * p, h - 5 * p, intArrayOf(0))
    // Each layer is a ring (the shape minus the next one in), so the translucent fill sits over the town, not over a coat of paint.
    fun ring(color: Color, big: Path, small: Path) = drawPath(Path().apply { fillType = PathFillType.EvenOdd; addPath(big); addPath(small) }, color)
    ring(tone.edge, outer, band)
    ring(tone.band, band, deep)
    ring(tone.deep, deep, fill)
    drawPath(fill, PmdPalette.Fill)
}

/** One line of a dialogue menu: a touch target at least 48 dp tall with [text] in it, a small cursor in front when [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PmdMenuLine(text: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, center: Boolean = false, pixelText: Boolean = true, onHold: (() -> Unit)? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(RoundedCornerShape(4.dp), Color.White).combinedClickable(role = Role.Button, onLongClick = onHold, onClick = onClick).semantics(mergeDescendants = true) {},
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
internal fun PmdButton(text: String, pixel: Int, modifier: Modifier = Modifier, color: Color = Pixel.Red, onClick: () -> Unit) {
    val edge = Color(color.red * 0.7f, color.green * 0.7f, color.blue * 0.7f)
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target)
            .drawBehind {
                val p = pixel.toFloat()
                drawRect(edge, Offset(0f, p), Size(size.width, size.height - p))
                drawRect(color, Offset(0f, 0f), Size(size.width, size.height - 2 * p))
            }
            .focusRing(RoundedCornerShape(2.dp), Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) { PmdText(text, pixel) }
}
