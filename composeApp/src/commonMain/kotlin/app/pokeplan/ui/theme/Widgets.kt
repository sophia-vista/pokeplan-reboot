package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Rank

/** The colour of a difficulty rank: grey for the easiest up to red for the hardest. */
fun rankColor(rank: Rank): Color = when (rank) {
    Rank.E -> Pixel.Gray
    Rank.D -> Pixel.Green
    Rank.C -> Pixel.Blue
    Rank.B -> Color(0xFFF08030)
    Rank.A -> Pixel.Red
}

/** A small square with the rank letter, like the badges in the games' menus. */
@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val face = if (selected) rankColor(rank) else Pixel.Shade
    Box(
        modifier
            .size(36.dp)
            .border(BorderStroke(3.dp, Pixel.Ink), NotchShape(2f))
            .background(face, NotchShape(2f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.letter.toString(), style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Pixel.InkSoft)
    }
}

/** The bar across the top of a screen: the title on navy with a yellow rule, and optional content at the right. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Pixel.Ink)
            .drawBehind { drawRect(Pixel.Yellow, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 4.dp.toPx())) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Pixel.Yellow)
        }
        trailing()
    }
}

/** A small white plate with a name and a level, like the one under a Pokémon in the games. */
@Composable
fun NamePlate(name: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .border(BorderStroke(3.dp, Pixel.Ink), NotchShape(3f))
            .background(Pixel.Panel, NotchShape(3f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** A label tab for a section ("Today"), navy with white text. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Pixel.Ink, NotchShape(2f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White) }
}

/** A small white capsule holding an icon and a number. */
@Composable
fun StatChip(text: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Pixel.Panel, NotchShape(2f))
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A compact white row for a list item: [lead] at the left, [content] in the middle, [trail] at the right. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    lead: @Composable () -> Unit = {},
    trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .border(BorderStroke(3.dp, Pixel.Ink), NotchShape(3f))
            .background(Pixel.Panel, NotchShape(3f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lead()
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}
