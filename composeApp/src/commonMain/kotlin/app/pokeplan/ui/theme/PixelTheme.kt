package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import pokeplan.composeapp.generated.resources.Res
import pokeplan.composeapp.generated.resources.pokemon_dp
import pokeplan.composeapp.generated.resources.pokemon_dp_bold

/**
 * The colour tokens (version 0.1 of docs/ui-ux-brief.md). Pokémon types keep their own colours elsewhere (the type plates);
 * these colours are for the app's own controls. The names are kept from the first version of the theme so screens read the same.
 */
object Pixel {
    /** `background` */
    val Paper = Color(0xFFF7F5F0)
    /** `surface`: cards, dialogs, sheets. */
    val Panel = Color(0xFFFFFFFF)
    /** `surfaceMuted`: secondary surfaces and empty progress tracks. */
    val Shade = Color(0xFFECECF2)
    /** `border` */
    val Border = Color(0xFFE1E1E8)
    /** `textPrimary` and `brandDark` */
    val Ink = Color(0xFF252637)
    /** `textSecondary` */
    val InkSoft = Color(0xFF666B7E)
    /** `brandPrimary` */
    val Red = Color(0xFFE84436)
    val RedDark = Color(0xFFB62A1C)
    /** `info` */
    val Blue = Color(0xFF3478DB)
    /** `success` */
    val Green = Color(0xFF218858)
    /** `warning` */
    val Warning = Color(0xFF946000)
    /** `danger` */
    val Danger = Color(0xFFB42332)
    /** A highlight for selected items and rewards. */
    val Yellow = Color(0xFFFFCB05)
    val Gray = Color(0xFF9AA1B5)
}

/** The spacing scale (4 dp base) and shape of the brief. */
object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp
    val XXXL = 32.dp

    /** Page horizontal padding. */
    val Page = 20.dp

    /** Space between sections. */
    val Section = 24.dp
    val CardRadius = 16.dp

    /** Smallest touch target for an important control. */
    val Target = 48.dp
}

/** The Pokémon DS-era (Diamond and Pearl) font, from the pokemon-assets package: for headings, big numbers and button labels. */
@Composable
fun pixelFont(): FontFamily = FontFamily(
    Font(Res.font.pokemon_dp, FontWeight.Normal),
    Font(Res.font.pokemon_dp_bold, FontWeight.Bold),
)

@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val display = pixelFont()
    val body = FontFamily.SansSerif
    fun style(font: FontFamily, size: Int, line: Int, weight: FontWeight = FontWeight.Normal, color: Color = Pixel.Ink) = TextStyle(
        fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pixel.Red, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
            background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
            error = Pixel.Danger, outline = Pixel.Border,
        ),
        typography = Typography(
            // Pixel font: game headings, rank, stats, button labels. Everything meant to be read in bulk is plain sans.
            headlineMedium = style(display, 30, 38, FontWeight.Bold),
            titleLarge = style(display, 24, 32, FontWeight.Bold),
            titleMedium = style(display, 20, 28, FontWeight.Bold),
            titleSmall = style(display, 20, 26, FontWeight.Bold),
            bodyLarge = style(body, 16, 24),
            bodyMedium = style(body, 14, 20, color = Pixel.InkSoft),
            labelLarge = style(display, 18, 24, FontWeight.Bold),
            labelMedium = style(body, 12, 16, FontWeight.Medium),
        ),
    ) { content() }
}

/** Rounded corners; the old "step" argument is kept so callers read the same: a step is 5dp of radius. */
fun NotchShape(step: Float): Shape = RoundedCornerShape((step * 5).dp)

private val cardShape = RoundedCornerShape(Spacing.CardRadius)

/** The basic card: white surface, 16 dp corners, a thin border, no shadow. [accent] puts a coloured strip on the left edge. */
@Composable
fun TextBox(modifier: Modifier = Modifier, accent: Color = Color.Transparent, content: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Pixel.Panel, cardShape)
            .border(BorderStroke(1.dp, Pixel.Border), cardShape)
            .clip(cardShape),
    ) {
        if (accent != Color.Transparent) Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Box(Modifier.weight(1f).padding(Spacing.L)) { content() }
    }
}

/**
 * A button. Solid colour for the main action; the neutral colours (`Pixel.InkSoft`, `Pixel.Ink`, `Pixel.Gray`) give the quiet
 * secondary look: white with a border and dark text. A disabled button is grey. At least 44 dp tall (48 dp when not [compact]).
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pixel.Blue,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val secondary = color == Pixel.InkSoft || color == Pixel.Ink || color == Pixel.Gray
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val face = when {
        !enabled -> Pixel.Shade
        secondary -> Pixel.Panel
        else -> color
    }
    val label = when {
        !enabled -> Pixel.InkSoft
        secondary -> Pixel.Ink
        else -> Color.White
    }
    val raised = enabled && !secondary
    val edge = if (raised) darker(color) else Color.Transparent
    val corner = if (compact) 12.dp else 16.dp
    Box(
        modifier
            .defaultMinSize(minWidth = Spacing.Target, minHeight = if (compact) 44.dp else Spacing.Target)
            // A solid action key has a darker lower edge, like the buttons in the games; quiet buttons stay flat.
            .drawBehind { if (raised) drawRoundRect(edge, Offset(0f, 3.dp.toPx()), Size(size.width, size.height), CornerRadius(corner.toPx())) }
            .padding(bottom = if (raised) 3.dp else 0.dp)
            .clip(shape)
            .background(face, shape)
            .then(if (secondary && enabled) Modifier.border(BorderStroke(1.5.dp, Pixel.Border), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(PaddingValues(horizontal = if (compact) 14.dp else 20.dp, vertical = 8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = label)
    }
}

fun darker(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

/**
 * A progress bar: a neutral track with a semantic fill. [label] is read out by screen readers ("3 of 5 tasks done"); put the
 * same words next to the bar so the meaning does not rest on colour.
 */
@Composable
fun PixelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pixel.Green, label: String? = null) {
    val shape = RoundedCornerShape(8.dp)
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(Pixel.Border, shape)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
                if (label != null) contentDescription = label
            },
    ) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(10.dp).background(color, shape))
    }
}

/**
 * A bar in the style of the games' EXP and HP bars: a dark outline, a short label, a bright fill. Used for the trainer rank,
 * where it adds the game feel; [label] is also read out by screen readers.
 */
@Composable
fun ExpBar(fraction: Float, label: String, modifier: Modifier = Modifier, tag: String = "EXP", color: Color = Pixel.Blue) {
    val f = fraction.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(tag, style = MaterialTheme.typography.labelMedium.copy(fontFamily = pixelFont(), fontWeight = FontWeight.Bold), color = Pixel.Warning)
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Pixel.Ink).padding(2.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Pixel.Shade))
            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

/**
 * The pixel-bordered text box of the games, for the moments that deserve it: rewards, the Professor's dialogue, a hatch.
 * Everything else uses the calm [TextBox].
 */
@Composable
fun GameBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = NotchShape(3f)
    Box(
        modifier
            .fillMaxWidth()
            .border(BorderStroke(3.dp, Pixel.Ink), shape)
            .padding(3.dp)
            .border(BorderStroke(2.dp, Pixel.Border), shape)
            .background(Pixel.Panel, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { content() }
}
