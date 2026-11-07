package app.pokeplan.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The on/off switch, drawn in pixels: a stair-cornered track with a square knob that sits left (off, grey track) or right (on,
 * green track). It is only a picture of the state; the row that holds it takes the tap and speaks the state.
 */
@Composable
fun PixelToggle(on: Boolean, modifier: Modifier = Modifier) {
    val track = if (on) Pixel.Green else Pixel.Shade
    val edge = if (on) Pixel.Green else Pixel.InkSoft
    Box(modifier.size(width = 44.dp, height = 24.dp).pixelFrame(track, edge), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.offset(x = if (on) (-4).dp else 4.dp).size(14.dp).pixelFrame(if (on) Color.White else Pixel.InkSoft, if (on) Color.White else Pixel.InkSoft))
    }
}

/** A slider in pixels: a flat stair-cornered track filled to the value, and a square knob. [steps] works as in Material's. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PixelSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, steps: Int = 0, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, color: Color = Pixel.Blue) {
    val interaction = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    Slider(
        value = value, onValueChange = onChange, valueRange = valueRange, steps = steps, colors = colors, interactionSource = interaction,
        thumb = { Box(Modifier.size(24.dp).pixelFrame(Pixel.Panel, color)) },
        track = { state ->
            val f = ((state.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(12.dp).drawBehind {
                    val p = 2.dp.toPx()
                    drawPath(stairRect(p, 0f, 0f, size.width, size.height, intArrayOf(1, 0)), Pixel.Border)
                    if (f > 0f) drawPath(stairRect(p, 0f, 0f, maxOf(size.width * f, 2 * p), size.height, intArrayOf(1, 0)), color)
                },
            )
        },
        modifier = modifier,
    )
}
