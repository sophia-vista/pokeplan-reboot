package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.Glyph
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.theme.LocalFrameTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.focusRing

/*
 * The form kit (owner's principles, 16 November 2026; docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4A): every form is built from these so
 * they share one label style, one field height (48 dp), one gap between fields and one gap between groups.
 *
 *   FormBox       the dialogue frame of a form, with less inner padding than a text box so a field has room on a phone
 *   FormStack     the form's groups, [Spacing.L] apart
 *   FormGroup     related fields, [Spacing.S] apart, with an optional small heading
 *   FormLabel     the one small label above every field, dropdown and choice (PixelField draws the same)
 *   PixelChoice   one of a few: a segmented row for up to four short options, otherwise a dropdown, never a wall of pills
 *   FormDisclosure  optional or advanced fields behind one "Additional details" row
 *   FormSave      the one full-width primary action at the bottom
 */

/** The label above a field: small, soft, the same as [app.pokeplan.ui.theme.PixelField]'s own. */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier, color: Color = Pixel.InkSoft) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color) // the same voice as FieldLabel and PixelField's label
}

/** The dialogue frame of a form: the same box as a text box, but 16 dp inside the frame's edge instead of 24 so fields keep their width. */
@Composable
fun FormBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().dialogueBox().clip(DialogueShape).padding(horizontal = Spacing.XL - Spacing.XS, vertical = Spacing.L)) { content() }
}

/** The groups of one form, [Spacing.L] apart. */
@Composable
fun FormStack(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.L)) { content() }
}

/** Related fields: [Spacing.S] apart under an optional heading in the form's label voice. */
@Composable
fun FormGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = Pixel.Ink)
        content()
    }
}

/** A thin rule between the figure a form is about and the rest of it. */
@Composable
fun FormDivider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.XS), color = Pixel.Border)
}

/**
 * One of a few options. Up to four options with short labels ([SEGMENT_LABEL_MAX] letters) are a segmented row of equal keys filling the width; anything
 * else is a [PixelDropdown]. A null entry is an "All" or "None" choice written by [text]. [label] sits above it like every field's.
 */
@Composable
fun <T> PixelChoice(
    options: List<T?>, selected: T?, text: (T?) -> String, onSelect: (T?) -> Unit, modifier: Modifier = Modifier, label: String? = null,
    key: Color = LocalFrameTone.current?.band ?: Pixel.Blue, placeholder: String = "Choose",
) {
    val segmented = options.size in 2..4 && options.all { text(it).length <= SEGMENT_LABEL_MAX }
    if (!segmented) { PixelDropdown(options, selected, text, onSelect, modifier, key, placeholder, label = label); return }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        if (label != null) FormLabel(label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            options.forEach { option -> TabButton(text(option), option == selected, { onSelect(option) }, Modifier.weight(1f), key = key) }
        }
    }
}

/** The longest label a segmented key holds on a 360 dp phone with four keys. */
const val SEGMENT_LABEL_MAX = 9

/** Optional or advanced fields behind one quiet row with a chevron: closed until asked for (or until [open] is forced by what the form holds). */
@Composable
fun FormDisclosure(title: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).clip(DialogueShape).focusRing(DialogueShape)
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide $title" else "Show $title") { onToggle() }
                .semantics { stateDescription = if (open) "Shown" else "Hidden" },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Pixel.Ink)
            Glyph(if (open) "chevron-up" else "chevron-down", Pixel.Ink, size = 20.dp)
        }
        if (open) content()
    }
}

/** The one primary action of a form: full width, in the module's colour. */
@Composable
fun FormSave(label: String, onClick: () -> Unit, accent: Color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PixelButton(label, onClick, modifier.fillMaxWidth(), accent, enabled = enabled)
}
