package app.pokeplan.ui.mint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.pokeplan.mint.CardStyle
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelSlider
import app.pokeplan.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun hsvToRgb(h: Float, s: Float, v: Float): Int {
    val hh = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((hh / 60f).toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

internal fun rgbToHsv(rgb: Int): Triple<Float, Float, Float> {
    val r = (rgb shr 16 and 255) / 255f
    val g = (rgb shr 8 and 255) / 255f
    val b = (rgb and 255) / 255f
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Triple(if (h < 0f) h + 360f else h, if (max == 0f) 0f else d / max, max)
}

internal fun hexOf(rgb: Int): String = "#" + rgb.toString(16).uppercase().padStart(6, '0')

@Composable
internal fun accountColor(card: String): Color = CardStyle.customRgb(card)?.let { Color(0xFF000000.toInt() or it) } ?: cardColor(CardStyle.of(card))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardColorPicker(selected: String, onPick: (String) -> Unit) {
    val customNow = CardStyle.customRgb(selected)
    var open by remember { mutableStateOf(customNow != null) }
    val start = rgbToHsv(customNow ?: 0x3B82F6)
    var h by remember { mutableStateOf(start.first) }
    var s by remember { mutableStateOf(start.second) }
    var v by remember { mutableStateOf(start.third) }
    var hex by remember { mutableStateOf(hexOf(customNow ?: 0x3B82F6)) }
    fun pickHsv() { val rgb = hsvToRgb(h, s, v); hex = hexOf(rgb); onPick(hexOf(rgb)) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("Card colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            CardStyle.entries.forEach { c ->
                val on = customNow == null && c.id == CardStyle.of(selected).id
                Swatch(cardColor(c), on, c.label) { open = false; onPick(c.id) }
            }
            Row(
                Modifier.size(width = 108.dp, height = 36.dp).border(if (open || customNow != null) 3.dp else 2.dp, if (open || customNow != null) Pixel.Ink else Pixel.Border)
                    .clickable(role = Role.Button) { open = true; pickHsv() }.semantics { contentDescription = "Custom colour" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).background(Color(0xFF000000.toInt() or hsvToRgb(h, s, v))))
                Text("Custom", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).border(2.dp, Pixel.Ink).background(Color(0xFF000000.toInt() or hsvToRgb(h, s, v))))
                Column(Modifier.weight(1f)) {
                    PixelField(hex, { t ->
                        val clean = ("#" + t.filter { it.isLetterOrDigit() }.take(6)).uppercase()
                        hex = clean
                        if (clean.length == 7) clean.drop(1).toIntOrNull(16)?.let { rgb -> rgbToHsv(rgb).let { h = it.first; s = it.second; v = it.third }; onPick(clean) }
                    }, Modifier.fillMaxWidth(), label = "Hex", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            }
            SliderRow("Hue", h, 360f) { h = it; pickHsv() }
            SliderRow("Saturation", s * 100f, 100f) { s = it / 100f; pickHsv() }
            SliderRow("Brightness", v * 100f, 100f) { v = it / 100f; pickHsv() }
        }
    }
}

@Composable
private fun Swatch(color: Color, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).border(if (on) 3.dp else 2.dp, if (on) Pixel.Ink else Pixel.Border).padding(3.dp).background(color)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$label colour" },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, max: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.size(width = 92.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        PixelSlider(value, onChange, Modifier.weight(1f), valueRange = 0f..max)
    }
}
