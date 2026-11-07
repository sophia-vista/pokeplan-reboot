package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.PixelShape
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing
import app.pokeplan.ui.theme.KeyLabel
import app.pokeplan.ui.theme.PixelKey

/** A filter or choice key (the planner's views, pickers): the selected one is filled, the others outlined. Same states as every key. */
@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, key: Color = Pixel.Blue) {
    PixelKey(onClick, modifier, key = key, selected = selected, enabled = enabled, fill = true, role = Role.Tab, horizontal = Spacing.M) { look -> KeyLabel(label, look) }
}

/** A bottom navigation item: a real item icon over the label; the selected one has a filled pill and a bold label. */
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
