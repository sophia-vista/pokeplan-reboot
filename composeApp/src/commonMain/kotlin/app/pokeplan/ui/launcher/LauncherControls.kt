package app.pokeplan.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.Glyph
import app.pokeplan.ui.LocalUiSound
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.pressScale
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelToggle
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing

/** One choice in a [PmdChoiceDialog]: what it is called, and, when the name alone does not say it, what it does. */
internal data class ChoiceOption<T>(val value: T, val label: String, val note: String = "")

/**
 * A setting that is on or off, as a line of a dialogue menu: its name, and a switch at the right that shows the state (green and to the right when on).
 * The whole line is one touch target; a screen reader hears it as a switch with its state.
 */
@Composable
internal fun PmdToggleLine(text: String, on: Boolean, pixel: Int, icon: String? = null, onChange: (Boolean) -> Unit) {
    val density = LocalDensity.current.density
    val ui = LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(source).fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(PixelShape, Color.White)
            .toggleable(value = on, interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Switch) { ui(Sfx.CURSOR); onChange(it) }
            .semantics { stateDescription = if (on) "On" else "Off" }
            .padding(horizontal = pmdRowPad(pixel, density)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
            if (icon != null) Glyph(icon.removePrefix("pa-"), Color.White, size = 24.dp)
            PmdText(text, pixel)
        }
        PixelToggle(on)
    }
}

/** A setting with a few named values, as a line of a dialogue menu: its name, the value it has now at the right, and a mark that it opens a list. */
@Composable
internal fun PmdChoiceLine(text: String, value: String, pixel: Int, icon: String? = null, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val ui = LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(source).fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).focusRing(PixelShape, Color.White)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClickLabel = "Choose $text") { ui(Sfx.CURSOR); onClick() }
            .semantics(mergeDescendants = true) { stateDescription = value }
            .padding(horizontal = pmdRowPad(pixel, density)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
            if (icon != null) Glyph(icon.removePrefix("pa-"), Color.White, size = 24.dp)
            PmdText(text, pixel)
        }
        PmdText(value, pixel, alpha = 0.75f)
        Glyph("chevron-right", Color.White.copy(alpha = 0.75f), size = 20.dp)
    }
}

/** The dim sheet behind a dialogue box that asks something: a tap outside it closes it. */
@Composable
private fun PmdModal(pixel: Int, onClose: () -> Unit, content: @Composable () -> Unit) {
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Pixel.Scrim.copy(alpha = 0.55f)).clickable(indication = null, interactionSource = none, onClick = onClose), contentAlignment = Alignment.Center) {
        PmdBox(pixel, Modifier.padding(horizontal = Spacing.XL).clickable(indication = null, interactionSource = none) {}) {
            Column(Modifier.widthIn(min = 240.dp)) { content() }
        }
    }
}

/**
 * A list to choose from instead of a button that cycles: the question as the heading, every [options] under it (the one in use marked with a cursor and a tick,
 * each with its note under the name when it has one), and Cancel. Choosing closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> PmdChoiceDialog(title: String, options: List<ChoiceOption<T>>, current: T, pixel: Int, onPick: (T) -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    PmdModal(pixel, onClose) {
        PmdMenuLine(title, pixel, dim = true) {}
        PmdRule(pixel)
        options.forEach { option ->
            val chosen = option.value == current
            Column(Modifier.fillMaxWidth()) {
                PmdMenuLine(option.label, pixel, selected = chosen, icon = if (chosen) "pa-check" else null) { onPick(option.value) }
                if (option.note.isNotEmpty()) PmdText(option.note, pixel, Modifier.padding(start = pmdRowPad(pixel, density), end = pmdRowPad(pixel, density), bottom = Spacing.S), alpha = 0.65f)
            }
        }
        PmdRule(pixel)
        PmdMenuLine("Cancel", pixel, dim = true, onClick = onClose)
    }
}

/** A question that must be answered before something that is hard to take back: what will happen as the heading, the action named for what it does, and Cancel. */
@Composable
internal fun PmdConfirmDialog(title: String, note: String, confirm: String, pixel: Int, onConfirm: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    PmdModal(pixel, onClose) {
        PmdMenuLine(title, pixel, dim = true) {}
        PmdRule(pixel)
        PmdText(note, pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M), alpha = 0.85f)
        PmdRule(pixel)
        PmdMenuLine(confirm, pixel, icon = "pa-trash", onClick = onConfirm)
        PmdMenuLine("Cancel", pixel, dim = true, onClick = onClose)
    }
}
