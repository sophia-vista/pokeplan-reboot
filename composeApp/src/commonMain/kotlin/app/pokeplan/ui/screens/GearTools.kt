package app.pokeplan.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.dialogueBox

/** A labelled drop-down of [options] showing the one at [selected]. */
@Composable
internal fun OptionPicker(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier, short: List<String>? = null, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FieldLabel(label)
        Box {
            PixelButton((short ?: options).getOrElse(selected) { "" }, { open = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            // A pixel list in a dialogue frame, not Material's rounded menu: it opens under the key and scrolls when long.
            if (open) androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }),
                onDismissRequest = { open = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(220.dp).heightIn(max = 320.dp).dialogueBox(fill = Pixel.Panel)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Spacing.M, vertical = 10.dp),
                ) {
                    options.forEachIndexed { i, option ->
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = Spacing.Target)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Choose $option") { onSelect(i); open = false },
                            contentAlignment = Alignment.CenterStart,
                        ) { Text(option, style = MaterialTheme.typography.bodyLarge, color = if (i == selected) Pixel.Blue else Pixel.Ink) }
                    }
                }
            }
        }
    }
}
