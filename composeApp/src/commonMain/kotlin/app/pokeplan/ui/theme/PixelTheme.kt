package app.pokeplan.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pokeplan.composeapp.generated.resources.Res
import pokeplan.composeapp.generated.resources.pressstart2p_regular
import org.jetbrains.compose.resources.Font

/** The palette: a GBA/DS-era handheld, warm paper background, dark outlines, primary colours for actions. */
object Pixel {
    val Paper = Color(0xFFF8F0D8)
    val Panel = Color(0xFFFFFFFF)
    val Ink = Color(0xFF303038)
    val InkSoft = Color(0xFF6B6B78)
    val Red = Color(0xFFE04038)
    val Blue = Color(0xFF3868C8)
    val Green = Color(0xFF58B058)
    val Yellow = Color(0xFFF8D030)
    val Shade = Color(0xFFD8D0B8)
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
            headlineMedium = TextStyle(fontFamily = heading, fontSize = 18.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontFamily = heading, fontSize = 12.sp, lineHeight = 18.sp),
            bodyLarge = TextStyle(fontFamily = body, fontSize = 17.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = heading, fontSize = 11.sp, lineHeight = 16.sp),
        ),
    ) { content() }
}

private val boxShape = RoundedCornerShape(4.dp)

/** A GBA text box: white panel, dark outline, a lighter inner line. */
@Composable
fun TextBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .border(BorderStroke(3.dp, Pixel.Ink), boxShape)
            .padding(3.dp)
            .border(BorderStroke(2.dp, Pixel.Shade), boxShape)
            .background(Pixel.Panel, boxShape)
            .padding(12.dp),
    ) { content() }
}

/** A chunky button. [color] is the face; the outline and a bottom edge give it the pressed-key look. */
@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pixel.Blue, enabled: Boolean = true) {
    Box(
        modifier
            .border(BorderStroke(3.dp, Pixel.Ink), boxShape)
            .background(if (enabled) color else Pixel.Shade, boxShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Pixel.InkSoft, fontWeight = FontWeight.Normal)
    }
}
