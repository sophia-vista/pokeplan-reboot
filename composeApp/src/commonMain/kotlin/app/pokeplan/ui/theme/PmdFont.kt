package app.pokeplan.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.orNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.roundToInt

/** White letters turned black and their black shadow white, for dialogue-font text on a light surface. */
private val INVERT = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)))

/** The pixel scale (screen pixels per art pixel) of dialogue-font text in the app: a whole number, about 1.75 dp, or 1.25 dp when [small] (tab labels). */
@Composable
fun appPixel(small: Boolean = false): Int = (LocalDensity.current.density * (if (small) 1.25f else 1.75f)).roundToInt().coerceAtLeast(1)

/**
 * A title or label in the dialogue font where the font can draw it exactly, and in the app's pixel font where it cannot (another
 * alphabet, a mark it lacks). Dark text on a light page, white on the dark theme. [large] is one step bigger, for screen titles.
 */
@Composable
fun PixelTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false, onLight: Boolean = !Pixel.dark, alpha: Float = 1f, small: Boolean = false) {
    val font by produceState<PmdFont?>(null) { value = PmdFont.load() }
    val f = font
    if (f != null && f.exact(text)) PmdText(text, appPixel(small) + if (large) 1 else 0, modifier, alpha, onLight)
    else Text(text, modifier, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = if (onLight) Pixel.Ink else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** One glyph of the dialogue font: its box in the sheet and how far its top is below the baseline (negative: above). */
class PmdGlyph(val x: Int, val y: Int, val w: Int, val h: Int, val dy: Int)

/**
 * The Pokémon dialogue bitmap font (assets/fonts/pmd-dialogue.png, measured into pmd-dialogue.tsv by tools/fonts/cut_pmd_font.py):
 * white letters with a black shadow, drawn at a whole-number scale without smoothing. It has the Latin letters, digits and a
 * few marks; text with anything else is drawn in the app's own font instead.
 */
class PmdFont(val sheet: ImageBitmap, val glyphs: Map<Char, PmdGlyph>) {
    /** The glyph's character for [c]: itself, an accent-less look-alike, a stand-in for a mark the font lacks, or `?`; null to leave it out. */
    private fun resolve(c: Char): Char? {
        val n = normalize(c)
        if (n == ' ' || n in glyphs) return n
        if (n in "()[]{}<>") return null
        if (n in "/\\|*#@~=^$") return '-'
        if (n == '&') return '+'
        val i = ACCENTED.indexOf(n.lowercaseChar())
        if (i >= 0) return PLAIN[i].let { if (n.isUpperCase()) it.uppercaseChar() else it }.takeIf { it in glyphs }
        return '?'
    }

    /** Whether every character of [text] has a glyph of its own (so the app can use this font for it and fall back to another when not). */
    fun exact(text: String): Boolean = text.all { it == ' ' || resolve(it) == it.let { c -> normalize(c) } }

    /** [text] as the characters this font draws. */
    fun clean(text: String): String = buildString { text.forEach { c -> resolve(c)?.let { append(it) } } }

    /** The width of [clean] text in pixels of the art. */
    fun width(clean: String) = clean.sumOf { if (it == ' ') SPACE else glyphs.getValue(it).w }

    /** [clean] cut with "..." so it is at most [limit] art pixels wide. */
    fun fit(clean: String, limit: Int): String {
        if (width(clean) <= limit) return clean
        val dots = width("...")
        var out = clean
        while (out.isNotEmpty() && width(out) + dots > limit) out = out.dropLast(1)
        return out.trimEnd() + "..."
    }

    companion object {
        const val SPACE = 4
        /** The height of a line in pixels of the art: the capitals and their shadow, so a line of text is centred by its letters; descenders hang below it. */
        const val LINE = 10
        /** The baseline's distance from the top of a line. */
        const val BASELINE = 9
        private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöøùúûüýÿ"
        private const val PLAIN = "aaaaaaceeeeiiiinoooooouuuuyy"
        private var cached: PmdFont? = null

        fun normalize(c: Char): Char = when (c) { '\'' -> '\u2019'; '"' -> '\u201d'; else -> c }

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

/**
 * [text] in the dialogue font at [pixel] screen pixels per art pixel (white; [alpha] softens it), on one line, cut with "..." if it
 * does not fit. The font is the only font the world register uses: marks it lacks become look-alikes or `?`, never another typeface.
 */
@Composable
fun PmdText(text: String, pixel: Int, modifier: Modifier = Modifier, alpha: Float = 1f, onLight: Boolean = false) {
    val font by produceState<PmdFont?>(null) { value = PmdFont.load() }
    val f = font ?: return
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val limit = if (constraints.hasBoundedWidth) constraints.maxWidth / pixel else Int.MAX_VALUE
        val shown = f.fit(f.clean(text), limit)
        Canvas(Modifier.size((f.width(shown) * pixel / density).dp, (PmdFont.LINE * pixel / density).dp).semantics { contentDescription = text }) {
            var cursor = 0
            for (ch in shown) {
                if (ch == ' ') { cursor += PmdFont.SPACE; continue }
                val g = f.glyphs.getValue(ch)
                drawImage(
                    f.sheet, srcOffset = IntOffset(g.x, g.y), srcSize = IntSize(g.w, g.h),
                    dstOffset = IntOffset(cursor * pixel, (PmdFont.BASELINE + g.dy) * pixel), dstSize = IntSize(g.w * pixel, g.h * pixel),
                    alpha = alpha, colorFilter = if (onLight) INVERT else null, filterQuality = FilterQuality.None,
                )
                cursor += g.w
            }
        }
    }
}


/** A button or tab label in the dialogue font (or the pixel font where it cannot draw the text): white on a coloured face, dark on a light one. */
@Composable
fun PixelLabel(text: String, color: Color, modifier: Modifier = Modifier, small: Boolean = false) {
    PixelTitle(text, modifier, onLight = color != Color.White, alpha = if (color == Pixel.InkSoft || color == Pixel.Gray) 0.65f else 1f, small = small)
}
