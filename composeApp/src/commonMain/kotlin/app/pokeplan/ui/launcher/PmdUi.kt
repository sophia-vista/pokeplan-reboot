package app.pokeplan.ui.launcher

import app.pokeplan.ui.pressScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import app.pokeplan.ui.entrance
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
internal fun PmdBox(pixel: Int, modifier: Modifier = Modifier, tone: PmdTone = PmdTone.BLUE, inset: Int = 3, compact: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current.density
    val fill = LocalGridFill.current
    Box(
        modifier.then(if (fill) Modifier.fillMaxHeight() else Modifier).entrance().drawBehind { pmdFrame(pixel, tone) }
            // Room inside the frame is the design system's own (Spacing.BoxX and BoxY, 24 and 18 dp), never less than the frame itself needs.
            // A compact box (a portrait's frame) keeps only the frame's own room, and so does a box on the widget grid: its content is centred in the cells, so the room above and below is whatever the cells leave, never more than they have.
            .padding(horizontal = ((SIDE + inset) * pixel / density).dp.let { if (compact) it else maxOf(it, Spacing.BoxX) }, vertical = ((TOP + inset) * pixel / density).dp.let { if (compact || fill) it else maxOf(it, Spacing.BoxY) }),
        content = if (!fill) content else { {
            // On the widget grid the frame is as tall as its cells, and what does not fit is cut off inside it, never squeezed; the content sits in the middle, so a box taller than its lines has the same room above and below.
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.CenterStart) { Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically, unbounded = true)) { content() } }
        } },
    )
}

/** True while a widget is drawn on the grid: its [PmdBox] then fills the cells it was given. */
internal val LocalGridFill = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The room at the side of a line of text in a box, in art pixels: every row (a menu line, a slider's label and bar) starts and ends this far in, so they line up. */
private const val ROW_X = 6

internal fun pmdRowPad(pixel: Int, density: Float) = (ROW_X * pixel / density).dp

/** The frame's thickness in art pixels: pale line 1 + band 1 + deep line 1 at the top and bottom, 1 + 5 + 1 at the sides. */
private const val TOP = 3
private const val SIDE = 7

private fun DrawScope.pmdFrame(pixel: Int, tone: PmdTone) = dialogueFrame(pixel.toFloat(), PmdPalette.Fill, sideBand = 5, topBand = 1, edge = tone.edge, band = tone.band, deep = tone.deep)

/** One line of a dialogue menu: a touch target at least 48 dp tall with [text] in it, a small cursor in front when [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PmdMenuLine(text: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, center: Boolean = false, onHold: (() -> Unit)? = null, icon: String? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val ui = app.pokeplan.ui.LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.pressScale(source).fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(RoundedCornerShape(4.dp), Color.White).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = onHold?.let { h -> { ui(app.pokeplan.ui.Sfx.LONG_PRESS); h() } }, onClick = { ui(app.pokeplan.ui.Sfx.CURSOR); onClick() }).semantics(mergeDescendants = true) {},
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (selected) Canvas(Modifier.padding(start = (2 * pixel / density).dp).size((3 * pixel / density).dp, (5 * pixel / density).dp)) {
            val p = pixel.toFloat()
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(3 * p, 2.5f * p); lineTo(0f, 5 * p); close() }, Color.White)
        }
        if (icon == null) PmdText(text, pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density)), alpha = if (dim) 0.75f else 1f)
        // With an icon, a real item picture (a small bag or ball for the setting) leads the line and the words follow it.
        else Row(Modifier.padding(horizontal = pmdRowPad(pixel, density)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
            app.pokeplan.ui.ItemIcon(icon, size = (9 * pixel / density).dp.coerceAtLeast(24.dp), description = null, modifier = Modifier.alpha(if (dim) 0.75f else 1f))
            PmdText(text, pixel, alpha = if (dim) 0.75f else 1f)
        }
    }
}

/** A one-pixel rule across a dialogue box, between its parts. */
@Composable
internal fun PmdRule(pixel: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    Box(modifier.fillMaxWidth().height((pixel / density).dp).background(Color.White.copy(alpha = 0.3f)))
}

/** A navigation entry of the world register: a real item icon over a label, at least 56 dp tall, the whole thing one touch target. */
@Composable
internal fun PmdNavItem(label: String, icon: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, showLabel: Boolean = true, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Column(
        modifier.defaultMinSize(minHeight = if (showLabel) 56.dp else 40.dp).focusRing(RoundedCornerShape(4.dp), Color.White).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = (2 * pixel / density).dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        ItemIcon(icon, size = 32.dp, description = null)
        if (showLabel) Box(Modifier.padding(top = (2 * pixel / density).dp), contentAlignment = Alignment.Center) { PmdText(label, pixel, alpha = if (selected) 1f else 0.9f) }
    }
}
