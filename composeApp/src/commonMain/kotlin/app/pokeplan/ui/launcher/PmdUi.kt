package app.pokeplan.ui.launcher

import app.pokeplan.ui.theme.PixelShape

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
import app.pokeplan.ui.orNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res

internal object PmdPalette {
    val Edge = DialogueColors.Edge
    val Band = DialogueColors.Band
    val Deep = DialogueColors.Deep
    val Fill = Color(0xA6000010)
    val Loading = Color(0xFF3B4A3A)
}

internal enum class PmdTone(val edge: Color, val band: Color, val deep: Color) {
    BLUE(PmdPalette.Edge, PmdPalette.Band, PmdPalette.Deep),
}

@Composable
internal fun PmdBox(
    pixel: Int, modifier: Modifier = Modifier, tone: PmdTone = PmdTone.BLUE, inset: Int = 3, compact: Boolean = false,
    alignTop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current.density
    val fill = LocalGridFill.current
    Box(
        modifier.then(if (fill) Modifier.fillMaxHeight() else Modifier).entrance().drawBehind { pmdFrame(pixel, tone) }
            .padding(horizontal = ((SIDE + inset) * pixel / density).dp.let { if (compact) it else maxOf(it, Spacing.BoxX) }, vertical = ((TOP + inset) * pixel / density).dp.let { if (compact || fill) it else maxOf(it, Spacing.BoxY) }),
        content = if (!fill) content else { {
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = if (alignTop) Alignment.TopStart else Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().wrapContentHeight(if (alignTop) Alignment.Top else Alignment.CenterVertically, unbounded = true)) { content() }
            }
        } },
    )
}

internal val LocalGridFill = androidx.compose.runtime.staticCompositionLocalOf { false }

private const val ROW_X = 6

internal fun pmdRowPad(pixel: Int, density: Float) = (ROW_X * pixel / density).dp

private const val TOP = 3
private const val SIDE = 7

private fun DrawScope.pmdFrame(pixel: Int, tone: PmdTone) = dialogueFrame(pixel.toFloat(), PmdPalette.Fill, sideBand = 5, topBand = 1, edge = tone.edge, band = tone.band, deep = tone.deep)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PmdMenuLine(text: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, center: Boolean = false, onHold: (() -> Unit)? = null, icon: String? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val ui = app.pokeplan.ui.LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.pressScale(source).fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(PixelShape, Color.White).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = onHold?.let { h -> { ui(app.pokeplan.ui.Sfx.LONG_PRESS); h() } }, onClick = { ui(app.pokeplan.ui.Sfx.CURSOR); onClick() }).semantics(mergeDescendants = true) {},
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (selected) Canvas(Modifier.padding(start = (2 * pixel / density).dp).size((3 * pixel / density).dp, (5 * pixel / density).dp)) {
            val p = pixel.toFloat()
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(3 * p, 2.5f * p); lineTo(0f, 5 * p); close() }, Color.White)
        }
        if (icon == null) PmdText(text, pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density)), alpha = if (dim) 0.75f else 1f)
        else Row(Modifier.padding(horizontal = pmdRowPad(pixel, density)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
            if (icon.startsWith("pa-")) app.pokeplan.ui.Glyph(icon.removePrefix("pa-"), Color.White, size = 24.dp, modifier = Modifier.alpha(if (dim) 0.75f else 1f))
            else app.pokeplan.ui.ItemIcon(icon, size = (9 * pixel / density).dp.coerceAtLeast(24.dp), description = null, modifier = Modifier.alpha(if (dim) 0.75f else 1f))
            PmdText(text, pixel, alpha = if (dim) 0.75f else 1f)
        }
    }
}

@Composable
internal fun PmdRule(pixel: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    Box(modifier.fillMaxWidth().height((pixel / density).dp).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
internal fun PmdNavItem(label: String, icon: String, pixel: Int, modifier: Modifier = Modifier, selected: Boolean = false, showLabel: Boolean = true, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    Column(
        modifier.defaultMinSize(minHeight = if (showLabel) 56.dp else 40.dp).focusRing(PixelShape, Color.White).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = (2 * pixel / density).dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        ItemIcon(icon, size = 32.dp, description = null)
        if (showLabel) Box(Modifier.padding(top = (2 * pixel / density).dp), contentAlignment = Alignment.Center) { PmdText(label, pixel, alpha = if (selected) 1f else 0.9f) }
    }
}
