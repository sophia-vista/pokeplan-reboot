package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    /** True while the dark palette is in use. Set by [PixelTheme]; reading a token in a composable follows it. */
    internal var dark by mutableStateOf(false)

    /** `background` */
    val Paper: Color get() = if (dark) Color(0xFF14151F) else Color(0xFFF7F5F0)
    /** `surface`: cards, dialogs, sheets. */
    val Panel: Color get() = if (dark) Color(0xFF1F2132) else Color(0xFFFFFFFF)
    /** `surfaceMuted`: secondary surfaces and empty progress tracks. */
    val Shade: Color get() = if (dark) Color(0xFF2B2E44) else Color(0xFFECECF2)
    /** `border` */
    val Border: Color get() = if (dark) Color(0xFF3A3E58) else Color(0xFFE1E1E8)
    /** `textPrimary` and `brandDark` */
    val Ink: Color get() = if (dark) Color(0xFFECEDF5) else Color(0xFF252637)
    /** Text and icons on something filled with [Ink] (the selected tab). */
    val OnInk: Color get() = if (dark) Color(0xFF14151F) else Color.White
    /** The dark frame of the EXP bars, dark in both palettes. */
    val Outline: Color get() = Color(0xFF252637)
    /** `textSecondary` */
    val InkSoft: Color get() = if (dark) Color(0xFFA9AEC4) else Color(0xFF666B7E)
    /** `brandPrimary` */
    val Red: Color get() = if (dark) Color(0xFFEC4C3E) else Color(0xFFD23B2E)
    val RedDark = Color(0xFFB62A1C)
    /** `info` */
    val Blue: Color get() = if (dark) Color(0xFF4A89E8) else Color(0xFF2C6CCC)
    /** `success` */
    val Green: Color get() = if (dark) Color(0xFF2E9E6A) else Color(0xFF1E7F52)
    /** `warning` */
    val Warning: Color get() = if (dark) Color(0xFFD99A1E) else Color(0xFF946000)
    /** `danger` */
    val Danger: Color get() = if (dark) Color(0xFFEC6A75) else Color(0xFFB42332)
    /** Care's header: a deep meadow green, the same in both palettes so white text stays readable on it. */
    val Meadow = Color(0xFF1E7F52)
    /** A highlight for selected items and rewards. */
    val Yellow = Color(0xFFFFCB05)
    val Gray = Color(0xFF9AA1B5)
    /** Identity colours for categories and regions (the same in both themes), so no screen needs its own literal. */
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Teal = Color(0xFF0D9488)
    val Amber = Color(0xFFF59E0B)
    /** The cool tint laid over a scene at night (how dark is [app.pokeplan.app.BuddyScene.darkness]). */
    val Night = Color(0xFF101C3C)
    /** Under text that sits straight on a picture, and behind sheets. */
    val Scrim = Color(0xFF000000)
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

    /** Room between a dialogue box's outer edge and what is in it: its 8 dp frame plus a gap, sideways and above and below. */
    val BoxX = 24.dp
    val BoxY = 18.dp

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

/** The body font: the same Pokémon DS-era font as the titles (the app has no other typeface), in its regular and bold. */
@Composable
fun bodyFont(): FontFamily = FontFamily(
    Font(Res.font.pokemon_dp, FontWeight.Normal),
    Font(Res.font.pokemon_dp, FontWeight.Medium),
    Font(Res.font.pokemon_dp_bold, FontWeight.SemiBold),
    Font(Res.font.pokemon_dp_bold, FontWeight.Bold),
)

/** [dark] picks the dark palette for everything below; the tokens in [Pixel] change with it. */
@Composable
fun PixelTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    // Set before anything below reads a token, so the first frame already has the right colours.
    remember(dark) { Pixel.dark = dark }
    val display = pixelFont()
    val body = bodyFont()
    fun style(font: FontFamily, size: Int, line: Int, weight: FontWeight = FontWeight.Normal, color: Color = Pixel.Ink) = TextStyle(
        fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = Pixel.Red, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
                background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
                surfaceVariant = Pixel.Shade, onSurfaceVariant = Pixel.InkSoft, error = Pixel.Danger, outline = Pixel.Border,
            )
        } else {
            lightColorScheme(
                primary = Pixel.Red, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
                background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
                error = Pixel.Danger, outline = Pixel.Border,
            )
        },
        typography = Typography(
            // Four sizes only: 24 (screen titles, big numbers), 18 (section titles, buttons), 16 (body), 14 (secondary and
            // labels). Hierarchy inside a size comes from the font, weight and colour. Pixel font for game headings, rank,
            // stats and button labels; everything meant to be read in bulk is the same font in its regular weight.
            headlineMedium = style(display, 24, 32, FontWeight.Bold),
            titleLarge = style(display, 24, 32, FontWeight.Bold),
            titleMedium = style(display, 18, 24, FontWeight.Bold),
            titleSmall = style(display, 18, 24, FontWeight.Bold),
            bodyLarge = style(body, 16, 24),
            bodyMedium = style(body, 14, 20, color = Pixel.InkSoft),
            labelLarge = style(display, 18, 24, FontWeight.Bold),
            labelMedium = style(body, 14, 20, FontWeight.Medium),
        ),
    ) { content() }
}

private val cardShape = RoundedCornerShape(Spacing.CardRadius)

/** The basic card of the app: a dialogue box (the game's blue frame around [Pixel.Panel]), with 16 dp of room inside. */
@Composable
fun TextBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .dialogueBox()
            .clip(DialogueShape)
            .padding(horizontal = Spacing.BoxX, vertical = Spacing.BoxY),
    ) { content() }
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // The outline: a darker shade of a solid key, the page line of a quiet one.
    val outline = if (raised) darker(color) else Pixel.Border
    Box(
        modifier
            .defaultMinSize(minWidth = Spacing.Target, minHeight = Spacing.Target)
            // Drawn in whole 2 dp art pixels with stair-stepped corners like the dialogue boxes: an outline, the face, and a one-pixel lip under a solid key that is gone while it is held.
            .drawBehind {
                val p = 2.dp.toPx()
                val lip = if (raised && !pressed) p else 0f
                drawPath(stairRect(p, 0f, 0f, size.width, size.height, intArrayOf(1, 0)), outline)
                drawPath(stairRect(p, p, p, size.width - p, size.height - p - lip, intArrayOf(0)), face)
            }
            .clip(DialogueShape)
            .focusRing(DialogueShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(PaddingValues(horizontal = if (compact) 16.dp else 24.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // The label sinks with the key while it is held.
        PixelLabel(text, label, Modifier.offset { IntOffset(0, if (pressed && raised) 2.dp.roundToPx() else 0) })
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
fun ExpBar(fraction: Float, label: String, modifier: Modifier = Modifier, tag: String = "EXP", color: Color = Pixel.Blue, tagColor: Color = Color.White) {
    val f = fraction.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(tag, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = pixelFont(), fontWeight = FontWeight.Bold), color = tagColor)
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Pixel.Outline).padding(2.dp)) {
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
    // The inner frame sits 3 dp in, so its corners are 3 dp smaller: the two outlines stay concentric.
    val outer = cardShape
    val inner = RoundedCornerShape(Spacing.CardRadius - 3.dp)
    Box(
        modifier
            .fillMaxWidth()
            .border(BorderStroke(3.dp, Pixel.Ink), outer)
            .padding(3.dp)
            .border(BorderStroke(2.dp, Pixel.Border), inner)
            .background(Pixel.Panel, inner)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { content() }
}
