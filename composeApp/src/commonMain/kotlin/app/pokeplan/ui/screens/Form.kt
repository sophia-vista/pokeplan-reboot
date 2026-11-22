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

@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier, color: Color = Pixel.InkSoft) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
fun FormBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().dialogueBox().clip(DialogueShape).padding(horizontal = Spacing.XL - Spacing.XS, vertical = Spacing.L)) { content() }
}

@Composable
fun FormStack(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.L)) { content() }
}

@Composable
fun FormGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = Pixel.Ink)
        content()
    }
}

@Composable
fun FormDivider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.XS), color = Pixel.Border)
}

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

const val SEGMENT_LABEL_MAX = 9

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

@Composable
fun FormSave(label: String, onClick: () -> Unit, accent: Color, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    PixelButton(label, onClick, modifier.fillMaxWidth(), accent, enabled = enabled, loading = loading)
}
