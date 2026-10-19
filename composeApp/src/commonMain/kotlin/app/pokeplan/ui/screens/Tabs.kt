package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel

/** A segmented pill: the filter tabs in the planner and the pickers. */
@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pixel.Ink else Pixel.Panel)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Pixel.Yellow else Pixel.InkSoft,
        )
    }
}

/** A bottom navigation item: a real item icon over the label; the selected one sits in a soft red pill. */
@Composable
fun NavButton(label: String, iconItem: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Pixel.Red.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(iconItem, size = 30.dp)
        Text(
            label, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Pixel.Red else Pixel.InkSoft,
        )
    }
}
