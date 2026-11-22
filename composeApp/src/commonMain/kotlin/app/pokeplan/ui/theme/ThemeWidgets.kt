package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.ui.pressScale
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Rank
import app.pokeplan.ui.ItemIcon

val Rank.title: String
    get() = when (this) {
        Rank.E -> "Tiny"
        Rank.D -> "Easy"
        Rank.C -> "Medium"
        Rank.B -> "Hard"
        Rank.A -> "Very hard"
    }

val Rank.spoken: String get() = "difficulty $letter, $title"

fun rankColor(rank: Rank): Color = when (rank) {
    Rank.E -> Pixel.InkSoft
    Rank.D -> Pixel.Green
    Rank.C -> Pixel.Blue
    Rank.B -> Pixel.Warning
    Rank.A -> Pixel.Danger
}

@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val badge = Modifier
        .size(36.dp)
        .clip(DialogueShape)
        .background(if (selected) rankColor(rank) else Pixel.Shade)
    Box(
        modifier
            .then(if (onClick != null) Modifier.defaultMinSize(Spacing.Target, Spacing.Target).clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .semantics { contentDescription = rank.spoken; if (onClick != null) this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(badge, contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Text(rank.letter.toString(), style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Pixel.InkSoft)
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    footer: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    color: Color = Pixel.Chrome,
    trailing: @Composable () -> Unit = {},
) {
    val shape = StairShape(intArrayOf(4, 2, 1, 0), roundTop = false)
    val chrome = LocalChromeColor.current
    androidx.compose.runtime.SideEffect { chrome?.value = color }
    Column(modifier.fillMaxWidth().clip(shape).background(color, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.Page, end = Spacing.L, top = Spacing.M, bottom = if (footer == null) Spacing.M else Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            if (onBack != null) BackButton(onBack)
            else if (icon != null) ItemIcon(icon, size = 36.dp, description = null)
            Column(Modifier.weight(1f)) {
                PixelTitle(title, Modifier.semantics { heading() }, large = true, onLight = false)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            trailing()
        }
        if (footer != null) Box(Modifier.fillMaxWidth().padding(start = Spacing.Page, end = Spacing.Page, bottom = Spacing.M)) { footer() }
    }
}

@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PixelKey(onBack, modifier, key = Pixel.InkSoft, compact = true, horizontal = Spacing.S) { look ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            app.pokeplan.ui.Glyph("chevron-left", look.ink, size = 16.dp)
            KeyLabel("Back", look)
        }
    }
}

val LocalChromeColor = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<Color>?> { null }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: String? = null) {
    Row(modifier.padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (icon != null) ItemIcon(icon, size = 28.dp, description = null)
        PixelTitle(text, Modifier.semantics { heading() })
    }
}

enum class NoticeTone { INFO, WARNING, DANGER }

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.INFO) {
    val color = when (tone) { NoticeTone.INFO -> Pixel.Blue; NoticeTone.WARNING -> Pixel.Warning; NoticeTone.DANGER -> Pixel.Danger }
    Row(
        modifier.fillMaxWidth().pixelPlate(color.copy(alpha = 0.10f).compositeOver(Pixel.Panel), color.copy(alpha = 0.6f)).padding(Spacing.M)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
fun StatChip(text: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .pixelPlate()
            .padding(start = Spacing.XS, end = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

fun Modifier.quietClickable(enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)

@Composable
fun Modifier.focusRing(shape: Shape, color: Color = Pixel.Blue, forced: Boolean = false): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focused = it.isFocused }.then(if (focused || forced) Modifier.border(2.dp, color, shape) else Modifier)
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    lead: @Composable () -> Unit = {},
    trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    highlight: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = DialogueShape
    val source = remember { MutableInteractionSource() }
    val hovering by source.collectIsHoveredAsState()
    Row(
        modifier
            .let { if (onClick != null) it.pressScale(source) else it }
            .fillMaxWidth()
            .dialogueBox()
            .clip(shape)
            .let { if (hovering && onClick != null) it.background(Pixel.Blue.copy(alpha = 0.06f)) else it }
            .let {
                when {
                    onClick == null -> it
                    highlight -> it.focusRing(shape).clickable(interactionSource = source, indication = LocalIndication.current, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    else -> it.focusRing(shape).clickable(interactionSource = source, indication = null, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                }
            }
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = Spacing.XL, vertical = Spacing.M + Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        lead()
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean, modifier: Modifier = Modifier, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.Target)
            .clip(PixelShape)
            .focusRing(PixelShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        PixelToggle(on)
    }
}

@Composable
fun ChoiceRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, lead: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val shape = DialogueShape
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.Target)
            .pixelFrame(if (selected) Pixel.Blue.copy(alpha = 0.10f).compositeOver(Pixel.Panel) else Pixel.Panel, if (selected) Pixel.Blue else Pixel.Border)
            .clip(shape)
            .focusRing(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Box(Modifier.size(20.dp).pixelFrame(Pixel.Panel, if (selected) Pixel.Blue else Pixel.InkSoft), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).background(Pixel.Blue))
        }
        lead()
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

@Composable
fun Accordion(title: String, modifier: Modifier = Modifier, summary: String? = null, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).clip(DialogueShape)
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide $title" else "Show $title") { open = !open }
                .semantics { stateDescription = if (open) "Shown" else "Hidden" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            if (!open && summary != null) Text(summary, style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, maxLines = 1)
            app.pokeplan.ui.Glyph(if (open) "chevron-up" else "chevron-down", Pixel.Ink, size = 20.dp)
        }
        if (open) content()
    }
}
