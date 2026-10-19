package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pokeplan.composeapp.generated.resources.Res
import pokeplan.composeapp.generated.resources.pressstart2p_regular
import org.jetbrains.compose.resources.Font

/** The palette: a DS-era handheld. Warm paper, dark navy outlines, saturated primaries. */
object Pixel {
    val Paper = Color(0xFFF4E9C8)
    val PaperDot = Color(0xFFE9DCB4)
    val Panel = Color(0xFFFFFDF4)
    val Ink = Color(0xFF202840)
    val InkSoft = Color(0xFF68708C)
    val Red = Color(0xFFE8483C)
    val RedDark = Color(0xFFA82820)
    val Blue = Color(0xFF3C78D8)
    val BlueDark = Color(0xFF204C98)
    val Green = Color(0xFF48B060)
    val GreenDark = Color(0xFF207838)
    val Yellow = Color(0xFFF8D038)
    val YellowDark = Color(0xFFC09810)
    val Shade = Color(0xFFD6C99A)
    val Sky = Color(0xFF88C8F0)
    val SkyLight = Color(0xFFB8E4FA)
    val Grass = Color(0xFF70C050)
    val GrassDark = Color(0xFF489838)
    val Gray = Color(0xFF9098A8)
    val GrayDark = Color(0xFF606878)
}

@Composable
fun pixelFont(): FontFamily = FontFamily(Font(Res.font.pressstart2p_regular))

@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val heading = pixelFont()
    val body = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pixel.Red, onPrimary = Color.White, secondary = Pixel.Blue, tertiary = Pixel.Green,
            background = Pixel.Paper, surface = Pixel.Panel, onBackground = Pixel.Ink, onSurface = Pixel.Ink,
        ),
        typography = Typography(
            // The pixel font is for headings only; body text stays readable.
            headlineMedium = TextStyle(fontFamily = heading, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = heading, fontSize = 11.sp, lineHeight = 18.sp),
            bodyLarge = TextStyle(fontFamily = body, fontSize = 17.sp, lineHeight = 24.sp, color = Pixel.Ink),
            bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontFamily = heading, fontSize = 10.sp, lineHeight = 16.sp),
        ),
    ) { content() }
}

/** A rectangle with its four corners cut a pixel step, like the panels in the GBA and DS games. */
class NotchShape(private val step: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.dp.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(s, 0f); lineTo(w - s, 0f); lineTo(w - s, s); lineTo(w, s)
            lineTo(w, h - s); lineTo(w - s, h - s); lineTo(w - s, h); lineTo(s, h)
            lineTo(s, h - s); lineTo(0f, h - s); lineTo(0f, s); lineTo(s, s); close()
        }
        return Outline.Generic(path)
    }
}

private val panelShape = NotchShape(3f)
private val buttonShape = NotchShape(3f)

/** A GBA text box: cream panel, navy outline, a coloured inner line and a hard drop shadow. */
@Composable
fun TextBox(modifier: Modifier = Modifier, accent: Color = Pixel.Shade, content: @Composable () -> Unit) {
    Box(
        modifier.padding(end = 3.dp, bottom = 3.dp).drawBehind {
            val o = 3.dp.toPx()
            drawRect(Pixel.Ink.copy(alpha = 0.22f), Offset(o, o), Size(size.width - o, size.height - o))
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(3.dp, Pixel.Ink), panelShape)
                .padding(3.dp)
                .border(BorderStroke(2.dp, accent), panelShape)
                .background(Pixel.Panel, panelShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) { content() }
    }
}

/** A chunky key: coloured face, lighter top line, darker bottom edge, navy outline. */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pixel.Blue,
    enabled: Boolean = true,
) {
    val face = if (enabled) color else Pixel.Gray
    val edge = if (enabled) darker(color) else Pixel.GrayDark
    Box(
        modifier
            .border(BorderStroke(3.dp, Pixel.Ink), buttonShape)
            .clip(buttonShape)
            .background(face)
            .drawBehind {
                val line = 3.dp.toPx()
                drawRect(edge, Offset(0f, size.height - line * 2), Size(size.width, line * 2))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(line, line), Size(size.width - line * 2, line / 2))
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

fun darker(c: Color): Color = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

/** An HP-style bar: label on the left, a bordered track with a coloured fill. [fraction] is 0 to 1. */
@Composable
fun PixelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pixel.Green) {
    Box(
        modifier.fillMaxWidth().height(14.dp)
            .border(BorderStroke(3.dp, Pixel.Ink), NotchShape(2f))
            .background(Pixel.Panel, NotchShape(2f)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(8.dp).padding(2.dp).align(Alignment.Center)) {
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) {
                drawRect(color, Offset.Zero, Size(size.width * f, size.height))
                drawRect(Color.White.copy(alpha = 0.4f), Offset.Zero, Size(size.width * f, size.height / 3))
            }
        }
    }
}
