package app.pokeplan.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pokeplan.composeapp.generated.resources.Res
import pokeplan.composeapp.generated.resources.pokemon_dp
import pokeplan.composeapp.generated.resources.pokemon_dp_bold
import org.jetbrains.compose.resources.Font

/** The palette: a DS-era handheld. Warm paper, dark navy outlines, saturated primaries. */
object Pixel {
    val Paper = Color(0xFFF3F5FA)
    val PaperDot = Color(0xFFE7EAF2)
    val Panel = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1F2B)
    val InkSoft = Color(0xFF6A7088)
    val Red = Color(0xFFE8412F)
    val RedDark = Color(0xFFB62A1C)
    val Blue = Color(0xFF3B82F6)
    val BlueDark = Color(0xFF1F5BC4)
    val Green = Color(0xFF2FBF71)
    val GreenDark = Color(0xFF1B8A50)
    val Yellow = Color(0xFFFFCB05)
    val YellowDark = Color(0xFFC79A00)
    val Shade = Color(0xFFDDE1EC)
    val Sky = Color(0xFF88C8F0)
    val SkyLight = Color(0xFFB8E4FA)
    val Grass = Color(0xFF70C050)
    val GrassDark = Color(0xFF489838)
    val Gray = Color(0xFF9AA1B5)
    val GrayDark = Color(0xFF626A82)
}

/** The Pokémon DS-era (Diamond and Pearl) font, from the pokemon-assets package, for all text. */
@Composable
fun pixelFont(): FontFamily = FontFamily(
    Font(Res.font.pokemon_dp, FontWeight.Normal),
    Font(Res.font.pokemon_dp_bold, FontWeight.Bold),
)

@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val font = pixelFont()
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(
            fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = Pixel.Ink,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pixel.Red, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
            background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
        ),
        typography = Typography(
            headlineMedium = style(32, 44, FontWeight.Bold),
            titleMedium = style(22, 32, FontWeight.Bold),
            bodyLarge = style(22, 32),
            bodyMedium = style(18, 26),
            labelLarge = style(20, 28, FontWeight.Bold),
            labelMedium = style(16, 22),
        ),
    ) { content() }
}

/** Rounded corners; the old "step" argument is kept so callers read the same: a step is 5dp of radius. */
fun NotchShape(step: Float): Shape = RoundedCornerShape((step * 5).dp)

private val panelShape = RoundedCornerShape(18.dp)

/** A white card with rounded corners and a soft shadow. [accent] draws a coloured strip along the left edge. */
@Composable
fun TextBox(modifier: Modifier = Modifier, accent: Color = Color.Transparent, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, panelShape, ambientColor = Pixel.Ink.copy(alpha = 0.25f), spotColor = Pixel.Ink.copy(alpha = 0.25f))
            .background(Pixel.Panel, panelShape)
            .clip(panelShape)
            .drawBehind { if (accent != Color.Transparent) drawRect(accent, Offset.Zero, Size(6.dp.toPx(), size.height)) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) { content() }
}

/** A pill-shaped key with a darker lower edge. */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pixel.Blue,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val face = if (enabled) color else Pixel.Shade
    val edge = if (enabled) darker(color) else Pixel.Gray
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    Box(
        modifier
            .padding(bottom = 3.dp)
            .drawBehind { drawRoundRect(edge, Offset(0f, 3.dp.toPx()), Size(size.width, size.height), CornerRadius(if (compact) 14.dp.toPx() else 18.dp.toPx())) }
            .background(face, shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (compact) PaddingValues(horizontal = 14.dp, vertical = 6.dp) else PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Pixel.InkSoft)
    }
}

fun darker(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

/** A rounded progress bar: [fraction] is 0 to 1. */
@Composable
fun PixelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pixel.Green) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.fillMaxWidth().height(14.dp).clip(shape).background(Pixel.Shade, shape)) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(14.dp).background(color, shape))
    }
}
