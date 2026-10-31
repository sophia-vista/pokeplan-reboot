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

/*
 * The control system. Every tappable thing that is a "key" (button, tab, chip, arrow, segment) is one [PixelKey]: the same
 * stair-cornered outline, the same states, the same 48 dp touch target. A control only chooses its key colour and its content.
 *
 *   state      face                       outline    letters            notes
 *   rest       panel                      key        key (ink if grey)
 *   hovered    key at 10% over the panel  key        key                pointer only (desktop, web)
 *   pressed    key                        key        white              also shrinks to 98% unless Reduce motion
 *   selected   key                        key        white              tabs, chips, segments; pressed goes darker
 *   focused    as above + a 2 dp blue ring round the touch target          keyboard and D-pad only
 *   disabled   panel                      border     ink-soft at 55%    not tappable, not announced as enabled
 *   loading    as disabled, three blinking pixels instead of the label     not tappable
 *
 * Colour is never the only signal: selected also sets the `selected` semantics and the white letters; disabled and loading
 * are announced by the semantics, not by colour.
 */

/** The visual states of a [PixelKey] (focus is separate: it can sit on top of any of them). */
enum class KeyState { Rest, Hovered, Pressed, Disabled, Loading }

/** What a key draws in one state: the face, the outline and the letters. */
class KeyLook(val face: Color, val edge: Color, val ink: Color, val tinted: Boolean)

/** True for the neutral keys, which show a grey outline and ink letters instead of their own colour. */
internal fun isNeutralKey(key: Color) = key == Pixel.InkSoft || key == Pixel.Ink || key == Pixel.Gray

/** A light key colour (yellow) is too pale to read as letters on the panel, so its letters are a darker shade of it. */
private fun legible(key: Color) = if (key.luminance() > 0.45f && !Pixel.dark) darker(key) else key

/** The look of a key of colour [key] in [state], [selected] or not. The one place where the table above is written in code. */
fun keyLook(key: Color, state: KeyState, selected: Boolean = false): KeyLook {
    val neutral = isNeutralKey(key)
    val edgeKey = if (neutral) Pixel.InkSoft else key
    val letters = if (neutral) Pixel.Ink else legible(key)
    return when (state) {
        KeyState.Disabled, KeyState.Loading ->
            KeyLook(face = if (selected) Pixel.Shade else Pixel.Panel, edge = Pixel.Border, ink = Pixel.InkSoft.copy(alpha = 0.55f), tinted = true)
        KeyState.Pressed -> KeyLook(face = if (selected) darker(edgeKey) else edgeKey, edge = if (selected) darker(edgeKey) else edgeKey, ink = Color.White, tinted = false)
        KeyState.Hovered ->
            if (selected) KeyLook(face = edgeKey.copy(alpha = 0.88f).compositeOver(Pixel.Panel), edge = edgeKey, ink = Color.White, tinted = false)
            else KeyLook(face = edgeKey.copy(alpha = 0.10f).compositeOver(Pixel.Panel), edge = edgeKey, ink = letters, tinted = true)
        KeyState.Rest ->
            if (selected) KeyLook(face = edgeKey, edge = edgeKey, ink = Color.White, tinted = false)
            else KeyLook(face = Pixel.Panel, edge = edgeKey, ink = letters, tinted = true)
    }
}

/**
 * Draws the key: an outline in whole 2 dp art pixels with stair-stepped corners, then the face. With [visible] set the key is
 * that tall and centred in a taller touch target; with null it fills the box.
 */
fun Modifier.pixelFrame(face: Color, edge: Color, visible: Dp? = null): Modifier = drawBehind {
    val p = 2.dp.toPx()
    val h = if (visible == null) size.height else minOf(size.height, visible.toPx())
    val top = (size.height - h) / 2
    drawPath(stairRect(p, 0f, top, size.width, top + h, intArrayOf(1, 0)), edge)
    drawPath(stairRect(p, p, top + p, size.width - p, top + h - p, intArrayOf(0)), face)
}

/** A state held still, for the component gallery and the screenshot test: [focused] draws the ring without a keyboard. */
class KeyPreview(val state: KeyState, val focused: Boolean = false)

/**
 * The key every button, tab and chip is built on (see the table at the top of this file). [onClick] null makes a key that only
 * shows (a legend). [fill] makes the key as tall as its 48 dp touch target (tabs and chips sit in wrapped rows and need no
 * gap of dead space); without it the key is 38 dp tall (32 dp when [compact]) inside the same 48 dp target. [quiet] leaves
 * the tap without a sound; [sound] overrides the confirm sound. [preview] holds one state still instead of reading the pointer.
 */
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
                    if (!quiet) ui(sound ?: Sfx.CONFIRM)
                }
            }
            .semantics { if (role == Role.Tab || selected) this.selected = selected }
            .padding(PaddingValues(horizontal = horizontal, vertical = Spacing.S)),
        contentAlignment = Alignment.Center,
    ) { content(look) }
}

/** The label of a key in the state's letters: the dialogue font, small. */
@Composable
fun KeyLabel(text: String, look: KeyLook) = PixelLabel(text, look.ink, small = true, tinted = look.tinted)

/** A quiet panel-coloured plate with the pixel outline, for things that are not tappable (a notice, a stat): same corner as the keys. */
fun Modifier.pixelPlate(face: Color = Color.Unspecified, edge: Color = Pixel.Border): Modifier =
    this.pixelFrame(if (face == Color.Unspecified) Pixel.Panel else face, edge)
