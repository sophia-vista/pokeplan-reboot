package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import app.pokeplan.ui.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.LocalFrameTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.KeyLabel
import app.pokeplan.ui.theme.PixelKey
import app.pokeplan.ui.pokemon.label

@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, key: Color = LocalFrameTone.current?.band ?: Pixel.Blue) {
    PixelKey(onClick, modifier, key = key, selected = selected, enabled = enabled, fill = true, role = Role.Tab, horizontal = Spacing.M) { look -> KeyLabel(label, look) }
}

@Composable
fun <T> PixelDropdown(
    options: List<T?>, selected: T?, text: (T?) -> String, onSelect: (T?) -> Unit, modifier: Modifier = Modifier,
    key: Color = LocalFrameTone.current?.band ?: Pixel.Blue, placeholder: String = "Choose", enabled: Boolean = true, label: String? = null,
) {
    if (label != null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FormLabel(label)
            PixelDropdown(options, selected, text, onSelect, Modifier.fillMaxWidth(), key, placeholder, enabled)
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val current = if (selected == null && null !in options) placeholder else text(selected)
    Box(modifier.onSizeChanged { anchor = it }) {
        PixelKey({ open = !open }, Modifier.fillMaxWidth(), key = key, selected = open, enabled = enabled, fill = true, role = Role.Button, horizontal = Spacing.M) { look ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clipToBounds()) { KeyLabel(current, look) }
                app.pokeplan.ui.Glyph(if (open) "chevron-up" else "chevron-down", look.ink, size = 16.dp)
            }
        }
        if (open) androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(0, anchor.height),
            onDismissRequest = { open = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.width(with(density) { anchor.width.toDp() }).heightIn(max = 320.dp).dialogueBox(fill = Pixel.Panel)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Spacing.M, vertical = Spacing.S),
            ) {
                options.forEach { option ->
                    val on = option == selected
                    val label = text(option)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.Target)
                            .selectable(selected = on, role = Role.RadioButton) { onSelect(option); open = false },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (on) Pixel.Tone else Pixel.Ink)
                        if (on) app.pokeplan.ui.Glyph("check", Pixel.Tone, size = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun NavButton(label: String, iconItem: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ui = app.pokeplan.ui.LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovering by source.collectIsHoveredAsState()
    Column(
        modifier
            .pressScale(source)
            .defaultMinSize(minHeight = 56.dp)
            .clip(PixelShape)
            .background(if (selected) Color.White.copy(alpha = if (hovering) 0.32f else 0.24f) else if (hovering) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .focusRing(PixelShape)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Tab) { ui(app.pokeplan.ui.Sfx.CURSOR); onClick() }
            .semantics { this.selected = selected }
            .padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(iconItem, size = 28.dp, description = null)
        PixelLabel(label, if (selected) Color.White else Color.White.copy(alpha = 0.82f), small = true)
    }
}
