package app.pokeplan.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
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

private fun lettersOnly(color: Color) = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, color.red * 255f,
    0f, 0f, 0f, 0f, color.green * 255f,
    0f, 0f, 0f, 0f, color.blue * 255f,
    color.alpha, 0f, 0f, 0f, 0f,
)))

private val INVERT = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)))

@Composable
fun appPixel(small: Boolean = false): Int = (LocalDensity.current.density * (if (small) 1.25f else 1.75f)).roundToInt().coerceAtLeast(1)

@Composable
fun PixelTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false, onLight: Boolean = !Pixel.dark, alpha: Float = 1f, small: Boolean = false, tint: Color? = null) {
    val font by produceState<PmdFont?>(null) { value = PmdFont.load() }
    val f = font
    if (f != null && f.exact(text)) PmdText(text, appPixel(small) + if (large) 1 else 0, modifier, alpha, onLight, tint)
    else Text(text, modifier, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = tint ?: if (onLight) Pixel.Ink else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

class PmdGlyph(val x: Int, val y: Int, val w: Int, val h: Int, val dy: Int)

class PmdFont(val sheet: ImageBitmap, val glyphs: Map<Char, PmdGlyph>) {
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

    fun exact(text: String): Boolean = text.all { it == ' ' || resolve(it) == it.let { c -> normalize(c) } }

    fun clean(text: String): String = buildString { text.forEach { c -> resolve(c)?.let { append(it) } } }

    fun width(clean: String) = clean.sumOf { if (it == ' ') SPACE else glyphs.getValue(it).w }

    fun fit(clean: String, limit: Int): String {
        if (width(clean) <= limit) return clean
        val dots = width("...")
        var out = clean
        while (out.isNotEmpty() && width(out) + dots > limit) out = out.dropLast(1)
        return out.trimEnd() + "..."
    }

    companion object {
        const val SPACE = 4
        const val LINE = 10
        const val BASELINE = 10
        private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöøùúûüýÿ"
        private const val PLAIN = "aaaaaaceeeeiiiinoooooouuuuyy"
        private var cached: PmdFont? = null

        fun cachedOrNull(): PmdFont? = cached

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

internal class TextRun(val text: String, val emoji: Boolean)

private fun codePointAt(text: String, i: Int): Int {
    val c = text[i]
    return if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 0x10000 + ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) else c.code
}

private fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF || cp in 0x2190..0x21FF ||
        cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 || cp in 0x24C2..0x24C2 || cp in 0x25A0..0x25FF || cp in 0x2934..0x2935 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299

private fun isEmojiTail(cp: Int): Boolean = cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F

internal fun splitEmoji(text: String): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    val current = StringBuilder()
    var inEmoji = false
    var i = 0
    while (i < text.length) {
        val cp = codePointAt(text, i)
        val width = if (cp > 0xFFFF) 2 else 1
        val emoji = isEmojiCodePoint(cp) || (inEmoji && isEmojiTail(cp) || (inEmoji && cp == 0x200D))
        if (emoji != inEmoji && current.isNotEmpty()) { runs += TextRun(current.toString(), inEmoji); current.clear() }
        inEmoji = emoji
        current.append(text, i, i + width)
        i += width
    }
    if (current.isNotEmpty()) runs += TextRun(current.toString(), inEmoji)
    return runs
}

internal fun hasEmoji(text: String): Boolean = splitEmoji(text).any { it.emoji }

@Composable
fun PmdText(text: String, pixel: Int, modifier: Modifier = Modifier, alpha: Float = 1f, onLight: Boolean = false, tint: Color? = null) {
    val font by produceState<PmdFont?>(null) { value = PmdFont.load() }
    val f = font ?: return
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val limit = if (constraints.hasBoundedWidth) constraints.maxWidth / pixel else Int.MAX_VALUE
        if (!hasEmoji(text)) {
            val shown = f.fit(f.clean(text), limit)
            PmdRun(f, shown, pixel, density, alpha, onLight, tint, Modifier.semantics { contentDescription = text })
            return@BoxWithConstraints
        }
        val emojiWidth = PmdFont.LINE + 2
        var left = limit
        Row(Modifier.semantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
            for (run in splitEmoji(text)) {
                if (left <= 0) break
                if (run.emoji) {
                    val glyphs = run.text.length
                    if (emojiWidth > left) break
                    Text(run.text, fontSize = (PmdFont.LINE * pixel / density * 0.9f).sp, maxLines = 1, modifier = Modifier.padding(end = (2 * pixel / density).dp))
                    left -= emojiWidth
                } else {
                    val shown = f.fit(f.clean(run.text), left)
                    if (shown.isEmpty()) continue
                    PmdRun(f, shown, pixel, density, alpha, onLight, tint, Modifier)
                    left -= f.width(shown)
                }
            }
        }
    }
}

@Composable
private fun PmdRun(f: PmdFont, shown: String, pixel: Int, density: Float, alpha: Float, onLight: Boolean, tint: Color?, modifier: Modifier) {
    Canvas(modifier.size((f.width(shown) * pixel / density).dp, (PmdFont.LINE * pixel / density).dp)) {
        var cursor = 0
        for (ch in shown) {
            if (ch == ' ') { cursor += PmdFont.SPACE; continue }
            val g = f.glyphs.getValue(ch)
            drawImage(
                f.sheet, srcOffset = IntOffset(g.x, g.y), srcSize = IntSize(g.w, g.h),
                dstOffset = IntOffset(cursor * pixel, (PmdFont.BASELINE + g.dy) * pixel), dstSize = IntSize(g.w * pixel, g.h * pixel),
                alpha = alpha, colorFilter = if (tint != null) lettersOnly(tint) else if (onLight) INVERT else null, filterQuality = FilterQuality.None,
            )
            cursor += g.w
        }
    }
}


@Composable
fun PixelLabel(text: String, color: Color, modifier: Modifier = Modifier, small: Boolean = false, tinted: Boolean = false) {
    PixelTitle(text, modifier, onLight = color.luminance() < 0.5f, alpha = if (!tinted && (color == Pixel.InkSoft || color == Pixel.Gray)) 0.65f else 1f, small = small, tint = if (tinted) color else null)
}
