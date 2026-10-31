package app.pokeplan.ui.screens

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

/** A segmented filter (the planner's views, pickers): the selected one is filled, the others outlined. At least 44 dp tall. */
@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = Spacing.Target)
            .clip(shape)
            .background(if (selected) Pixel.Blue else Pixel.Panel, shape)
            .border(1.dp, if (selected) Pixel.Blue else Pixel.Border, shape)
            .focusRing(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        contentAlignment = Alignment.Center,
    ) {
        PixelLabel(label, if (selected) Color.White else Pixel.Ink, small = true)
    }
}

/** A bottom navigation item: a real item icon over the label; the selected one has a filled pill and a bold label. */
@Composable
fun NavButton(label: String, iconItem: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Pixel.Red.copy(alpha = 0.12f) else Color.Transparent)
            .focusRing(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(iconItem, size = 28.dp, description = null)
        PixelLabel(label, if (selected) Pixel.Red else Pixel.InkSoft, small = true)
    }
}
