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
 * A rounded pixel square with the rank letter. The meaning is in the accessible description and, in pickers, in the label under it;
 * colour is never the only signal. A clickable badge has a 48 dp touch target.
 */
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
            // The letter sits in a fixed-size square, so it keeps its size when the system text size grows.
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
    /** The bar's colour: the app's neutral [Pixel.Chrome], or a module's own ([ModuleAccent.header]) on that module's pages. */
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

/**
 * Back: a pixel chevron and the label in the dialogue font, on one centre line. (It used to be the text "‹ Back", whose arrow the
 * dialogue font lacks, so the whole label fell back to another font and sat off the centre of its key.)
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PixelKey(onBack, modifier, key = Pixel.InkSoft, compact = true, horizontal = Spacing.S) { look ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            app.pokeplan.ui.Glyph("chevron-left", look.ink, size = 16.dp)
            KeyLabel("Back", look)
        }
    }
}

/** The colour of the header on the page now shown, which the bottom tab bar takes on (`App` provides it; headers write it). */
val LocalChromeColor = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<Color>?> { null }

/** A section title ("Today"): semibold, with space above it. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: String? = null) {
    Row(modifier.padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (icon != null) ItemIcon(icon, size = 28.dp, description = null)
        PixelTitle(text, Modifier.semantics { heading() })
    }
}

/** How serious a [Notice] is: Info for what is going on, Warning for what needs a look, Danger only where data could be lost. */
enum class NoticeTone { INFO, WARNING, DANGER }

/**
 * One calm sentence about the state of something (offline, a save to choose, a sign-in that did not work): a tinted card with
 * a coloured edge, announced politely by screen readers. Say what happened and what to do; never blame the user.
 */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.INFO) {
    val color = when (tone) { NoticeTone.INFO -> Pixel.Blue; NoticeTone.WARNING -> Pixel.Warning; NoticeTone.DANGER -> Pixel.Danger }
    Row(
        modifier.fillMaxWidth().pixelPlate(color.copy(alpha = 0.10f).compositeOver(Pixel.Panel), color.copy(alpha = 0.6f)).padding(Spacing.M)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

/** A white capsule holding an icon and a number. */
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

/**
 * A tap with no ripple, for a Pokémon or a tile whose answer is plain to see (a page opens, a cry plays): a ripple there
 * reads as a grey box stuck on the sprite.
 */
fun Modifier.quietClickable(enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)

/**
 * A visible keyboard focus ring (2 dp, [color]) around [shape] while the control after it in the chain has focus. Put it
 * just before the `clickable`, `selectable` or `toggleable`; touch users never see it.
 */
@Composable
fun Modifier.focusRing(shape: Shape, color: Color = Pixel.Blue, forced: Boolean = false): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focused = it.isFocused }.then(if (focused || forced) Modifier.border(2.dp, color, shape) else Modifier)
}

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
    val shape = DialogueShape
    val source = remember { MutableInteractionSource() }
    val hovering by source.collectIsHoveredAsState()
    Row(
        modifier
            .let { if (onClick != null) it.pressScale(source) else it }
            .fillMaxWidth()
            .dialogueBox()
            .clip(shape)
            // A pointer over a tappable row tints it faintly (desktop and web); touch has the ripple or the press scale.
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
        // The row handles the tap and speaks the state, so the switch itself is only a picture of it.
        PixelToggle(on)
    }
}

/**
 * One choice out of a few, as a full-width row with a radio dot: the dot is filled and the border darker on the chosen
 * one, so the choice does not rest on colour, and choices never look like action buttons.
 */
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

/** The small name over a group of controls inside a card ("Move to", "Remind me"), so each group reads as one chunk. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    // One label voice for every field (the same as PixelField's own): small and soft, not bold.
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

/**
 * A folded section: a title row with a chevron (and an optional [summary] while folded) that opens [content] below it. Folded by default,
 * so a screen shows its facts first and the detail on a tap. The whole row is the target and stays [Spacing.Target] tall.
 */
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
