package app.pokeplan.ui.theme

import androidx.compose.foundation.layout.size
import app.pokeplan.ui.pressScale
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

object Pixel {
    internal var dark by mutableStateOf(false)

    val Paper: Color get() = if (dark) Color(0xFF14151F) else Color(0xFFF7F5F0)
    val Panel: Color get() = if (dark) Color(0xFF1F2132) else Color(0xFFFFFFFF)
    val Shade: Color get() = if (dark) Color(0xFF2B2E44) else Color(0xFFECECF2)
    val Border: Color get() = if (dark) Color(0xFF3A3E58) else Color(0xFFE1E1E8)
    val Ink: Color get() = if (dark) Color(0xFFECEDF5) else Color(0xFF252637)
    val OnInk: Color get() = if (dark) Color(0xFF14151F) else Color.White
    val Outline: Color get() = Color(0xFF252637)
    val InkSoft: Color get() = if (dark) Color(0xFFA9AEC4) else Color(0xFF666B7E)
    val Red: Color get() = if (dark) Color(0xFFF2665A) else Color(0xFFD23B2E)
    val Blue: Color get() = if (dark) Color(0xFF5B96EE) else Color(0xFF2C6CCC)
    val Tone: Color @androidx.compose.runtime.Composable get() = LocalFrameTone.current?.band ?: Blue
    val Green: Color get() = if (dark) Color(0xFF34B07A) else Color(0xFF1E7F52)
    val Warning: Color get() = if (dark) Color(0xFFD99A1E) else Color(0xFF946000)
    val Danger: Color get() = if (dark) Color(0xFFEC6A75) else Color(0xFFB42332)
    val Chrome = Color(0xFF2B3050)
    val Meadow = Color(0xFF1E7F52)
    val Yellow = Color(0xFFFFCB05)
    val Gray = Color(0xFF9AA1B5)
    val Violet: Color get() = if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    val Pink = Color(0xFFEC4899)
    val Teal: Color get() = if (dark) Color(0xFF2DB5A8) else Color(0xFF0F766E)
    val Amber = Color(0xFFF59E0B)
    val Night = Color(0xFF101C3C)
    val Scrim = Color(0xFF000000)
}

object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp

    val BoxX = 24.dp
    val BoxY = 18.dp

    val Page = 20.dp

    val Section = 24.dp

    val Target = 48.dp
}

object Sizes {
    val Hairline = 1.dp
    val Line = 2.dp
    val IconS = 24.dp
    val Icon = 32.dp
    val IconL = 40.dp
    val SpriteS = 56.dp
    val SpriteM = 72.dp
    val SpriteL = 96.dp
}

@Composable
fun pixelFont(): FontFamily = FontFamily(
    Font(Res.font.pokemon_dp, FontWeight.Normal),
    Font(Res.font.pokemon_dp_bold, FontWeight.Bold),
)

@Composable
fun bodyFont(): FontFamily = FontFamily(
    Font(Res.font.pokemon_dp, FontWeight.Normal),
    Font(Res.font.pokemon_dp, FontWeight.Medium),
    Font(Res.font.pokemon_dp_bold, FontWeight.SemiBold),
    Font(Res.font.pokemon_dp_bold, FontWeight.Bold),
)

@Composable
fun PixelTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    remember(dark) { Pixel.dark = dark }
    val display = pixelFont()
    val body = bodyFont()
    fun style(font: FontFamily, size: Int, line: Int, weight: FontWeight = FontWeight.Normal, color: Color = Pixel.Ink) = TextStyle(
        fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color, baselineShift = androidx.compose.ui.text.style.BaselineShift(when (size) { 14 -> 0.5f; 16 -> 0.45f; 18 -> 0.37f; else -> 0.42f }),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = Pixel.Blue, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
                background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
                surfaceVariant = Pixel.Shade, onSurfaceVariant = Pixel.InkSoft, error = Pixel.Danger, outline = Pixel.Border,
            )
        } else {
            lightColorScheme(
                primary = Pixel.Blue, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
                background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
                error = Pixel.Danger, outline = Pixel.Border,
            )
        },
        shapes = androidx.compose.material3.Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(2.dp)),
        typography = Typography(
            headlineMedium = style(display, 24, 28, FontWeight.Bold),
            titleLarge = style(display, 24, 28, FontWeight.Bold),
            titleMedium = style(display, 18, 22, FontWeight.Bold),
            titleSmall = style(display, 18, 22, FontWeight.Bold),
            bodyLarge = style(body, 16, 20),
            bodyMedium = style(body, 14, 18, color = Pixel.InkSoft),
            labelLarge = style(display, 18, 22, FontWeight.Bold),
            labelMedium = style(body, 14, 18, FontWeight.Medium),
            bodySmall = style(body, 14, 18, color = Pixel.InkSoft),
            labelSmall = style(body, 14, 18, FontWeight.Medium),
        ),
    ) { content() }
}

private val cardShape = PixelShape

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

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalFrameTone.current?.band ?: Pixel.Blue,
    enabled: Boolean = true,
    compact: Boolean = false,
    sound: app.pokeplan.ui.Sfx? = null,
    loading: Boolean = false,
    preview: KeyPreview? = null,
) {
    PixelKey(
        onClick, modifier, key = color, enabled = enabled, loading = loading, compact = compact, preview = preview,
        sound = sound ?: if (text.endsWith("Back")) app.pokeplan.ui.Sfx.BACK else app.pokeplan.ui.Sfx.CONFIRM,
    ) { look ->
        if (loading || preview?.state == KeyState.Loading) {
            Box(contentAlignment = Alignment.Center) {
                // The label stays where it was, unseen, so the key keeps its width while the pixels blink (5.6).
                KeyLabel(text, KeyLook(look.face, look.edge, Color.Transparent, look.tinted))
                LoadingDots(color = Pixel.InkSoft, description = null)
            }
        } else KeyLabel(text, look)
    }
}

@Composable
fun PixelIconButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    color: Color = Pixel.InkSoft,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    PixelKey(
        onClick, modifier.width(Spacing.Target).semantics { contentDescription = description }, key = color, enabled = enabled,
        loading = loading, compact = true, horizontal = 0.dp, sound = app.pokeplan.ui.Sfx.CONFIRM,
    ) { look ->
        if (loading) Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { LoadingDots(color = look.ink, dot = 4.dp, gap = 3.dp, description = null) }
        else app.pokeplan.ui.Glyph(glyph, look.ink, size = 20.dp)
    }
}

fun darker(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

@Composable
fun PixelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pixel.Green, label: String? = null) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .drawBehind {
                val p = 2.dp.toPx()
                drawPath(stairRect(p, 0f, 0f, size.width, size.height, intArrayOf(1, 0)), Pixel.Border)
                if (f > 0f) drawPath(stairRect(p, 0f, 0f, maxOf(size.width * f, 2 * p), size.height, intArrayOf(1, 0)), color)
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
                if (label != null) contentDescription = label
            },
    )
}

@Composable
fun ExpBar(fraction: Float, label: String, modifier: Modifier = Modifier, tag: String = "EXP", color: Color = Pixel.Blue, tagColor: Color = Color.White) {
    val f = fraction.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        if (tag.isNotEmpty()) Text(tag, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = pixelFont(), fontWeight = FontWeight.Bold), color = tagColor)
        Box(
            Modifier.weight(1f).height(14.dp).drawBehind {
                val p = 2.dp.toPx()
                drawPath(stairRect(p, 0f, 0f, size.width, size.height, intArrayOf(1, 0)), Pixel.Outline)
                drawPath(stairRect(p, p, p, size.width - p, size.height - p, intArrayOf(0)), Pixel.Shade)
                if (f > 0f) drawPath(stairRect(p, p, p, p + maxOf((size.width - 2 * p) * f, p), size.height - p, intArrayOf(0)), color)
            },
        )
    }
}

@Composable
fun GameBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val outer = cardShape
    val inner = PixelShape
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
