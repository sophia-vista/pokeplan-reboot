package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Rank
import app.pokeplan.ui.ItemIcon

/** What a difficulty rank means, in words. Harder tasks earn more; rank is how hard it feels, not how urgent it is. */
val Rank.title: String
    get() = when (this) {
        Rank.E -> "Tiny"
        Rank.D -> "Easy"
        Rank.C -> "Medium"
        Rank.B -> "Hard"
        Rank.A -> "Very hard"
    }

/** "C, Medium": spoken by screen readers and shown where there is room. */
val Rank.spoken: String get() = "difficulty $letter, $title"

/** The colour of a difficulty rank, from quiet grey for the easiest to red for the hardest. */
fun rankColor(rank: Rank): Color = when (rank) {
    Rank.E -> Pixel.InkSoft
    Rank.D -> Pixel.Green
    Rank.C -> Pixel.Blue
    Rank.B -> Pixel.Warning
    Rank.A -> Pixel.Danger
}

/**
 * A round badge with the rank letter. The meaning is in the accessible description and, in pickers, in the label under it;
 * colour is never the only signal. A clickable badge has a 48 dp touch target.
 */
@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val badge = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(if (selected) rankColor(rank) else Pixel.Shade)
    Box(
        modifier
            .then(if (onClick != null) Modifier.defaultMinSize(Spacing.Target, Spacing.Target).clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .semantics { contentDescription = rank.spoken; if (onClick != null) this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(badge, contentAlignment = Alignment.Center) {
            // The letter sits in a fixed-size circle, so it keeps its size when the system text size grows.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Text(rank.letter.toString(), style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Pixel.InkSoft)
            }
        }
    }
}

/**
 * The bar across the top of a screen: slim Pokémon red with the page title in the pixel font, an item icon that says where
 * you are, a line of context, and optional content at the right and underneath.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    footer: (@Composable () -> Unit)? = null,
    /** Shows Back at the start of the bar, where every phone app puts it; it takes the icon's place. */
    onBack: (() -> Unit)? = null,
    /** The bar's colour: the brand red, or a region's own ([RegionLook.header]) on that region's pages. */
    color: Color = Pixel.Red,
    trailing: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(bottomStart = Spacing.CardRadius, bottomEnd = Spacing.CardRadius)
    Column(modifier.fillMaxWidth().clip(shape).background(color, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.Page, end = Spacing.L, top = Spacing.M, bottom = if (footer == null) Spacing.M else Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            if (onBack != null) PixelButton("‹ Back", onBack, color = Pixel.InkSoft, compact = true)
            else if (icon != null) ItemIcon(icon, size = 36.dp, description = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.semantics { heading() })
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
            trailing()
        }
        if (footer != null) Box(Modifier.fillMaxWidth().padding(start = Spacing.Page, end = Spacing.Page, bottom = Spacing.M)) { footer() }
    }
}

/** A pill under a Pokémon: its name and level. */
@Composable
fun NamePlate(name: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Pixel.Panel, RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Pixel.Border), RoundedCornerShape(50))
            .padding(horizontal = Spacing.L, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A section title ("Today"): semibold, with space above it. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: String? = null) {
    Row(modifier.padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (icon != null) ItemIcon(icon, size = 28.dp, description = null)
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    }
}

/** A white capsule holding an icon and a number. */
@Composable
fun StatChip(text: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Pixel.Panel, RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Pixel.Border), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A tap with no ripple, for a Pokémon or a tile whose answer is plain to see (a page opens, a cry plays): a ripple there
 * reads as a grey box stuck on the sprite.
 */
fun Modifier.quietClickable(enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)

/** A card row for a list item: [lead] at the left, [content] in the middle, [trail] at the right. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    lead: @Composable () -> Unit = {},
    trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    /** What tapping the row does, for screen readers ("Show options"). */
    onClickLabel: String? = null,
    /** False for no ripple highlight when tapped (party rows: the page opening is answer enough). */
    highlight: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Spacing.CardRadius)
    Row(
        modifier
            .fillMaxWidth()
            .background(Pixel.Panel, shape)
            .border(BorderStroke(1.dp, Pixel.Border), shape)
            .clip(shape)
            .let {
                when {
                    onClick == null -> it
                    highlight -> it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    else -> it.quietClickable(onClickLabel = onClickLabel, onClick = onClick)
                }
            }
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = Spacing.M, vertical = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        lead()
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}

/**
 * A setting that is on or off: the label, a line of detail, and a switch, the control every phone uses for this. The whole
 * row is the touch target, not just the switch.
 */
@Composable
fun ToggleRow(label: String, on: Boolean, modifier: Modifier = Modifier, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.Target)
            .clip(RoundedCornerShape(Spacing.S))
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        // The row handles the tap and speaks the state, so the switch itself is only a picture of it.
        Switch(
            checked = on, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Pixel.Green, checkedBorderColor = Pixel.Green,
                uncheckedThumbColor = Pixel.InkSoft, uncheckedTrackColor = Pixel.Shade, uncheckedBorderColor = Pixel.Border,
            ),
        )
    }
}

/**
 * One choice out of a few, as a full-width row with a radio dot: the dot is filled and the border darker on the chosen
 * one, so the choice does not rest on colour, and choices never look like action buttons.
 */
@Composable
fun ChoiceRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, lead: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.Target)
            .clip(shape)
            .background(if (selected) Pixel.Blue.copy(alpha = 0.10f) else Pixel.Panel, shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Pixel.Blue else Pixel.Border), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Box(Modifier.size(20.dp).border(BorderStroke(2.dp, if (selected) Pixel.Blue else Pixel.InkSoft), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(Pixel.Blue, CircleShape))
        }
        lead()
        Box(Modifier.weight(1f)) { content() }
    }
}

/** The small name over a group of controls inside a card ("Move to", "Remind me"), so each group reads as one chunk. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = Spacing.XS), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Pixel.InkSoft)
}
