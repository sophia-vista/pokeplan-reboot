package app.pokeplan.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object DialogueColors {
    val Edge = Color(0xFFA8C0E0)
    val Band = Color(0xFF6080F8)
    val Deep = Color(0xFF4820F8)
    val CardDeep = Color(0xFF3B4FB0)
}

class FrameTone(val edge: Color, val band: Color, val deep: Color)

fun frameToneOf(base: Color) = FrameTone(
    edge = Color(base.red + (1f - base.red) * 0.6f, base.green + (1f - base.green) * 0.6f, base.blue + (1f - base.blue) * 0.6f),
    band = base,
    deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f),
)

val LocalFrameTone = androidx.compose.runtime.compositionLocalOf<FrameTone?> { null }

const val CARD_OPACITY = 0.8f

class StairShape(
    private val insets: IntArray, private val px: Dp = 2.dp, private val roundTop: Boolean = true, private val roundBottom: Boolean = true,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { px.toPx() }
        val none = IntArray(insets.size)
        return Outline.Generic(stairPath(p, 0f, 0f, size.width, size.height, if (roundTop) insets else none, if (roundBottom) insets else none))
    }
}

val DialogueShape: Shape = StairShape(intArrayOf(2, 1, 0))

val PixelShape: Shape = StairShape(intArrayOf(1, 0))

val PixelDisc: Shape = StairShape(intArrayOf(5, 3, 2, 1, 1, 0), px = 3.dp)

internal fun stairRect(p: Float, l: Float, t: Float, r: Float, b: Float, insets: IntArray): Path = stairPath(p, l, t, r, b, insets, insets)

internal fun stairPath(p: Float, l: Float, t: Float, r: Float, b: Float, top: IntArray, bottom: IntArray): Path = Path().apply {
    val n = top.size
    moveTo(l + top[0] * p, t)
    for (k in 0 until n) { lineTo(r - top[k] * p, t + k * p); lineTo(r - top[k] * p, t + (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(r - bottom[k] * p, b - (k + 1) * p); lineTo(r - bottom[k] * p, b - k * p) }
    for (k in 0 until n) { lineTo(l + bottom[k] * p, b - k * p); lineTo(l + bottom[k] * p, b - (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(l + top[k] * p, t + (k + 1) * p); lineTo(l + top[k] * p, t + k * p) }
    close()
}

fun DrawScope.dialogueFrame(p: Float, fill: Color, sideBand: Int, topBand: Int, edge: Color = DialogueColors.Edge, band: Color = DialogueColors.Band, deep: Color = DialogueColors.Deep) {
    val w = size.width
    val h = size.height
    val side = 1 + sideBand
    val top = 1 + topBand
    val outerPath = stairRect(p, 0f, 0f, w, h, intArrayOf(2, 1, 0))
    val bandPath = stairRect(p, p, p, w - p, h - p, intArrayOf(1, 0))
    val deepPath = stairRect(p, side * p, top * p, w - side * p, h - top * p, intArrayOf(1, 0))
    val fillPath = stairRect(p, (side + 1) * p, (top + 1) * p, w - (side + 1) * p, h - (top + 1) * p, intArrayOf(0))
    fun ring(color: Color, big: Path, small: Path) = drawPath(Path().apply { fillType = PathFillType.EvenOdd; addPath(big); addPath(small) }, color)
    ring(edge, outerPath, bandPath)
    ring(band, bandPath, deepPath)
    ring(deep, deepPath, fillPath)
    drawPath(fillPath, fill)
}

@androidx.compose.runtime.Composable
fun Modifier.dialogueBox(fill: Color = Pixel.Panel.copy(alpha = CARD_OPACITY), pixel: Dp = 2.dp, tone: FrameTone? = LocalFrameTone.current): Modifier {
    val edge = tone?.edge ?: DialogueColors.Edge
    val band = tone?.band ?: DialogueColors.Band
    val deep = tone?.deep ?: if (Pixel.dark) DialogueColors.Deep else DialogueColors.CardDeep
    return drawBehind { dialogueFrame(pixel.toPx(), fill, sideBand = 2, topBand = 1, edge = edge, band = band, deep = deep) }
}

fun pixelDiscPath(c: Offset, radius: Float, p: Float): Path = Path().apply {
    if (radius <= 0f) return@apply
    val top = kotlin.math.floor((c.y - radius) / p) * p
    var y = top
    while (y < c.y + radius) {
        val dy = y + p / 2 - c.y
        if (kotlin.math.abs(dy) < radius) {
            val half = kotlin.math.round(kotlin.math.sqrt(radius * radius - dy * dy) / p).coerceAtLeast(1f) * p
            val left = kotlin.math.round((c.x - half) / p) * p
            addRect(androidx.compose.ui.geometry.Rect(left, y, left + 2 * half, y + p))
        }
        y += p
    }
}

fun DrawScope.drawPixelDisc(color: Color, center: Offset, radius: Float, p: Float = 2.dp.toPx()) = drawPath(pixelDiscPath(center, radius, p), color)

fun DrawScope.drawPixelRing(color: Color, center: Offset, radius: Float, width: Float, p: Float = 2.dp.toPx()) {
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addPath(pixelDiscPath(center, radius + width / 2, p))
        addPath(pixelDiscPath(center, (radius - width / 2).coerceAtLeast(0f), p))
    }
    drawPath(ring, color)
}
