package app.pokeplan.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.LocalUiSound
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.pressScale

enum class KeyState { Rest, Hovered, Pressed, Disabled, Loading }

class KeyLook(val face: Color, val edge: Color, val ink: Color, val tinted: Boolean)

internal fun isNeutralKey(key: Color) = key == Pixel.InkSoft || key == Pixel.Ink || key == Pixel.Gray

private fun legible(key: Color) = if (key.luminance() > 0.45f && !Pixel.dark) darker(key) else key

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

internal fun onFill(face: Color): Color = if (contrast(Color.White, face) >= contrast(Pixel.Outline, face)) Color.White else Pixel.Outline

fun keyLook(key: Color, state: KeyState, selected: Boolean = false): KeyLook {
    val neutral = isNeutralKey(key)
    val edgeKey = if (neutral) Pixel.InkSoft else key
    val letters = if (neutral) Pixel.Ink else legible(key)
    return when (state) {
        KeyState.Disabled, KeyState.Loading ->
            KeyLook(face = if (selected) Pixel.Shade else Pixel.Panel, edge = Pixel.Border, ink = Pixel.InkSoft.copy(alpha = 0.55f), tinted = true)
        KeyState.Pressed -> run { val fill = if (selected) darker(edgeKey) else edgeKey; KeyLook(face = fill, edge = fill, ink = onFill(fill), tinted = false) }
        KeyState.Hovered ->
            if (selected) edgeKey.copy(alpha = 0.88f).compositeOver(Pixel.Panel).let { fill -> KeyLook(face = fill, edge = edgeKey, ink = onFill(fill), tinted = false) }
            else KeyLook(face = edgeKey.copy(alpha = 0.10f).compositeOver(Pixel.Panel), edge = edgeKey, ink = letters, tinted = true)
        KeyState.Rest ->
            if (selected) KeyLook(face = edgeKey, edge = edgeKey, ink = onFill(edgeKey), tinted = false)
            else KeyLook(face = Pixel.Panel, edge = edgeKey, ink = letters, tinted = true)
    }
}

fun Modifier.pixelFrame(face: Color, edge: Color, visible: Dp? = null): Modifier = drawBehind {
    val p = 2.dp.toPx()
    val h = if (visible == null) size.height else minOf(size.height, visible.toPx())
    val top = (size.height - h) / 2
    drawPath(stairRect(p, 0f, top, size.width, top + h, intArrayOf(1, 0)), edge)
    drawPath(stairRect(p, p, top + p, size.width - p, top + h - p, intArrayOf(0)), face)
}

class KeyPreview(val state: KeyState, val focused: Boolean = false)

@Composable
fun PixelKey(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    key: Color = Pixel.Blue,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    fill: Boolean = false,
    role: Role = Role.Button,
    quiet: Boolean = false,
    sound: Sfx? = null,
    horizontal: Dp = if (compact) 12.dp else 16.dp,
    preview: KeyPreview? = null,
    content: @Composable BoxScope.(KeyLook) -> Unit,
) {
    val ui = LocalUiSound.current
    val source = remember { MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val hovering by source.collectIsHoveredAsState()
    val active = enabled && !loading && onClick != null
    val state = preview?.state ?: when {
        loading -> KeyState.Loading
        !enabled -> KeyState.Disabled
        held -> KeyState.Pressed
        hovering -> KeyState.Hovered
        else -> KeyState.Rest
    }
    val look = keyLook(key, state, selected)
    val visible = if (fill) null else if (compact) 32.dp else 38.dp
    val ringed = preview?.focused
    Box(
        modifier
            .pressScale(source)
            .defaultMinSize(minWidth = Spacing.Target, minHeight = Spacing.Target)
            .pixelFrame(look.face, look.edge, visible)
            .clip(DialogueShape)
            .let { if (ringed == null) it.focusRing(DialogueShape) else if (ringed) it.focusRing(DialogueShape, forced = true) else it }
            .let {
                if (onClick == null) it else it.clickable(interactionSource = source, indication = null, enabled = active, role = role) {
                    onClick()
                    if (!quiet) ui(sound ?: if (role == Role.Tab) Sfx.CURSOR else Sfx.CONFIRM)
                }
            }
            .semantics { if (role == Role.Tab || selected) this.selected = selected }
            .padding(PaddingValues(horizontal = horizontal, vertical = Spacing.S)),
        contentAlignment = Alignment.Center,
    ) { content(look) }
}

@Composable
fun KeyLabel(text: String, look: KeyLook) = PixelLabel(text, look.ink, small = true, tinted = look.tinted)

fun Modifier.pixelPlate(face: Color = Color.Unspecified, edge: Color = Pixel.Border): Modifier =
    this.pixelFrame(if (face == Color.Unspecified) Pixel.Panel else face, edge)
