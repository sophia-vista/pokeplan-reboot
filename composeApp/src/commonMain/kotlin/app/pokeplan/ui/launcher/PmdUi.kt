package app.pokeplan.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import app.pokeplan.ui.ItemIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.pokeplan.ui.theme.DialogueColors
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdGlyph
import app.pokeplan.ui.theme.PmdFont
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.dialogueFrame
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing
import androidx.compose.foundation.shape.RoundedCornerShape
import app.pokeplan.ui.orNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res

internal object PmdPalette {
    /** The pale hairline on the outside of the frame. */
    val Edge = DialogueColors.Edge
    /** The frame's main band. */
    val Band = DialogueColors.Band
    /** The one-pixel line between the band and the inside. */
    val Deep = DialogueColors.Deep
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
 * [inset] is the room, in art pixels, between the frame and what is inside (rules and buttons run to it; text lines add 6 more, where the cursor goes, so text is 9 in). The frame is drawn in code (UI chrome).
 */
@Composable
internal fun PmdBox(pixel: Int, modifier: Modifier = Modifier, tone: PmdTone = PmdTone.BLUE, inset: Int = 3, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.drawBehind { pmdFrame(pixel, tone) }
            .padding(horizontal = ((SIDE + inset) * pixel / density).dp, vertical = ((TOP + inset) * pixel / density).dp),
        content = content,
    )
}

/** The frame's thickness in art pixels: pale line 1 + band 1 + deep line 1 at the top and bottom, 1 + 5 + 1 at the sides. */
private const val TOP = 3
private const val SIDE = 7

private fun DrawScope.pmdFrame(pixel: Int, tone: PmdTone) = dialogueFrame(pixel.toFloat(), PmdPalette.Fill, sideBand = 5, topBand = 1, edge = tone.edge, band = tone.band, deep = tone.deep)

/** One line of a dialogue menu: a touch target at least 48 dp tall with [text] in it, a small cursor in front when [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PmdMenuLine(text: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, center: Boolean = false, onHold: (() -> Unit)? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(RoundedCornerShape(4.dp), Color.White).combinedClickable(role = Role.Button, onLongClick = onHold, onClick = onClick).semantics(mergeDescendants = true) {},
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (selected) Canvas(Modifier.padding(start = (2 * pixel / density).dp).size((3 * pixel / density).dp, (5 * pixel / density).dp)) {
            val p = pixel.toFloat()
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(3 * p, 2.5f * p); lineTo(0f, 5 * p); close() }, Color.White)
        }
        PmdText(text, pixel, Modifier.padding(horizontal = (6 * pixel / density).dp), alpha = if (dim) 0.75f else 1f)
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

/** A navigation entry of the world register: a real item icon over a label, at least 56 dp tall, the whole thing one touch target. */
@Composable
internal fun PmdNavItem(label: String, icon: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Column(
        modifier.defaultMinSize(minHeight = 56.dp).focusRing(RoundedCornerShape(4.dp), Color.White).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
            .padding(vertical = (2 * pixel / density).dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        ItemIcon(icon, size = 32.dp, description = null)
        Box(Modifier.padding(top = (2 * pixel / density).dp), contentAlignment = Alignment.Center) { PmdText(label, pixel, alpha = if (selected) 1f else 0.9f) }
    }
}
