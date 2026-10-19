package app.pokeplan.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Rank
import app.pokeplan.ui.ItemIcon

/** The colour of a difficulty rank: grey for the easiest up to red for the hardest. */
fun rankColor(rank: Rank): Color = when (rank) {
    Rank.E -> Pixel.Gray
    Rank.D -> Pixel.Green
    Rank.C -> Pixel.Blue
    Rank.B -> Color(0xFFF59E0B)
    Rank.A -> Pixel.Red
}

/** A round badge with the rank letter. Unselected badges (in a picker) are pale. */
@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) rankColor(rank) else Pixel.Shade)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(rank.letter.toString(), style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Pixel.InkSoft)
    }
}

/** The bar across the top of a screen: Pokémon red, rounded at the bottom. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFF05A47), Pixel.Red)), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            trailing()
        }
    }
}

/** A pill under a Pokémon: its name and level. */
@Composable
fun NamePlate(name: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(3.dp, RoundedCornerShape(50))
            .background(Pixel.Panel, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** A label for a section ("Today"): a dark pill. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Pixel.Ink, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White) }
}

/** A white capsule holding an icon and a number. */
@Composable
fun StatChip(text: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.White, RoundedCornerShape(50))
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A white card row for a list item: [lead] at the left, [content] in the middle, [trail] at the right. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    lead: @Composable () -> Unit = {},
    trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Pixel.Ink.copy(alpha = 0.25f), spotColor = Pixel.Ink.copy(alpha = 0.25f))
            .background(Pixel.Panel, shape)
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead()
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}
