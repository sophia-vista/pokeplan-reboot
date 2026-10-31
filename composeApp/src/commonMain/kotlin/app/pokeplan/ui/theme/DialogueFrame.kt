package app.pokeplan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colours of the game's dialogue-box frame (measured from its menu boxes): a pale hairline, a blue band and a deep line. */
object DialogueColors {
    val Edge = Color(0xFFA8C0E0)
    val Band = Color(0xFF6080F8)
    val Deep = Color(0xFF4820F8)
    /** The inner line on the app's light cards. */
    val CardDeep = Color(0xFF3B4FB0)
}

/** The three colours of a frame: [edge] the hairline, [band] the main band, [deep] the inner line. */
class FrameTone(val edge: Color, val band: Color, val deep: Color)

/** A frame tone built from one colour: it is the band, its pale tint the hairline, its dark shade the inner line. */
fun frameToneOf(base: Color) = FrameTone(
    edge = Color(base.red + (1f - base.red) * 0.6f, base.green + (1f - base.green) * 0.6f, base.blue + (1f - base.blue) * 0.6f),
    band = base,
    deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f),
)

/** The frame colours dialogue boxes below this point wear: the game's blue by default, a module's own colour inside its pages. */
val LocalFrameTone = androidx.compose.runtime.compositionLocalOf<FrameTone?> { null }

/** The corner of a dialogue box, for clipping what is drawn inside it (a ripple, a picture). */
val DialogueShape = RoundedCornerShape(6.dp)

/** A rectangle with stair-stepped (rounded) corners: [insets] is how far in each row from the corner starts, ending in 0. */
internal fun stairRect(p: Float, l: Float, t: Float, r: Float, b: Float, insets: IntArray): Path = Path().apply {
    val n = insets.size
    moveTo(l + insets[0] * p, t)
    for (k in 0 until n) { lineTo(r - insets[k] * p, t + k * p); lineTo(r - insets[k] * p, t + (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(r - insets[k] * p, b - (k + 1) * p); lineTo(r - insets[k] * p, b - k * p) }
    for (k in 0 until n) { lineTo(l + insets[k] * p, b - k * p); lineTo(l + insets[k] * p, b - (k + 1) * p) }
    for (k in n - 1 downTo 0) { lineTo(l + insets[k] * p, t + (k + 1) * p); lineTo(l + insets[k] * p, t + k * p) }
    close()
}

/**
 * Draws the dialogue-box frame over the whole draw area, every part a whole number of [p] pixels: a pale hairline, a blue band
 * ([sideBand] thick at the sides, [topBand] at the top and bottom), a deep line, rounded corners, then the [fill] inside. Each layer
 * is a ring, so a see-through fill shows what is behind the box.
 */
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

/**
 * The card of the app, drawn as a dialogue box: [Pixel.Panel] inside a thin version of the game's frame (2 dp art pixels: a
 * hairline, a band 2 pixels thick at the sides and 1 at the top and bottom, a deep line). It adds no padding: the frame is 8 dp
 * at the sides and 6 dp at the top, and the card's own padding (16 dp) keeps its content clear.
 */
@androidx.compose.runtime.Composable
fun Modifier.dialogueBox(fill: Color = Pixel.Panel, pixel: Dp = 2.dp, tone: FrameTone? = LocalFrameTone.current): Modifier {
    // On a light card the game's saturated violet line is too loud, so the app's default version of it is a muted indigo.
    val edge = tone?.edge ?: DialogueColors.Edge
    val band = tone?.band ?: DialogueColors.Band
    val deep = tone?.deep ?: if (Pixel.dark) DialogueColors.Deep else DialogueColors.CardDeep
    return drawBehind { dialogueFrame(pixel.toPx(), fill, sideBand = 2, topBand = 1, edge = edge, band = band, deep = deep) }
}
