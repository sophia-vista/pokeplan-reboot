package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.NotchShape
import app.pokeplan.ui.theme.Pixel

/** A small segmented button: the filter tabs in the planner and the rank picker. */
@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(if (selected) Pixel.Yellow else Pixel.Ink, NotchShape(2f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 8.sp),
            color = if (selected) Pixel.Ink else Pixel.Shade,
        )
    }
}

/** A bottom navigation button: a real item icon over the label. */
@Composable
fun NavButton(label: String, iconItem: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(if (selected) Pixel.Yellow else Pixel.Ink, NotchShape(2f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(iconItem, size = 30.dp)
        Text(
            label, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 7.sp),
            color = if (selected) Pixel.Ink else Pixel.Shade,
        )
    }
}
