package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.LightColors
import app.pokeplan.ui.gear.Sos
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * The flashlight: the phone's torch where there is one (its state is the hardware's, so another app switching it shows here), and a screen light
 * for every device, with colours (white, warm, red for the night, amber) and a brightness the screen is dimmed to in the app. An SOS signal exists
 * only on request, with a warning, only on the torch, never with Reduce motion on, never faster than one change in 250 ms and for at most five
 * minutes. Leaving the tool turns the torch off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlashlightTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val torch = model.torch?.takeIf { it.available() }
    var on by remember { mutableStateOf(torch?.isOn() == true) }
    var screen by remember { mutableStateOf(false) }
    var sos by remember { mutableStateOf(false) }
    var warned by remember { mutableStateOf(false) }
    val reduce = model.data.settings.reduceMotion
    // The light's state is the hardware's: another app or the system switching it shows here at once. Leaving the tool turns it off, so it is never left on by accident.
    DisposableEffect(torch) {
        val stop = torch?.observe { on = it; if (!it) sos = false }
        onDispose { stop?.invoke(); torch?.release() }
    }
    LaunchedEffect(sos) {
        if (!sos || torch == null) return@LaunchedEffect
        val end = store.nowMillis + Sos.MAX_MS
        try {
            while (sos && store.nowMillis < end) for ((lit, ms) in Sos.pattern()) { torch.set(lit); delay(ms); if (!sos) break }
        } finally {
            torch.set(false)
            sos = false
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (torch != null) {
                PixelButton(if (on) "Turn off" else "Turn on", { sos = false; on = torch.set(!on); onUse() }, Modifier.fillMaxWidth(), if (on) Pixel.InkSoft else Pixel.Tone)
            }
            PixelButton("Screen light", { screen = true; onUse() }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            if (torch != null) {
                if (!sos) {
                    PixelButton("SOS signal", { warned = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true, enabled = !reduce)
                    if (warned) {
                        Text("This flashes the light on and off. Do not use it if flashing lights bother you. It stops when you tap Stop, leave the tool, or after five minutes.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red) // copy-ok: result
                        PixelButton("Start the SOS", { warned = false; sos = true; onUse() }, Modifier.fillMaxWidth(), Pixel.Red, compact = true)
                    }
                } else PixelButton("Stop the SOS", { sos = false }, Modifier.fillMaxWidth(), Pixel.Red)
            }
        }
    }
    if (screen) Dialog({ screen = false }, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        val color = LightColors.byId(gear.lightColor)
        Box(Modifier.fillMaxSize().background(Color(color.argb(gear.lightBrightness))), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().padding(Spacing.L).background(Color(0xCC000000)).padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                FieldLabel("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    LightColors.all.forEach { c -> PixelButton(c.label, { store.changeGear { it.copy(lightColor = c.id) } }, color = if (c.id == color.id) Pixel.Tone else Pixel.InkSoft, compact = true) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Dimmer", { store.changeGear { it.copy(lightBrightness = (it.lightBrightness - 0.15f).coerceAtLeast(0.05f)) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    Text("${(gear.lightBrightness * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    PixelButton("Brighter", { store.changeGear { it.copy(lightBrightness = (it.lightBrightness + 0.15f).coerceAtMost(1f)) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                Text(color.note, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                PixelIconButton("close", "Close", { screen = false }, Pixel.Red)
            }
        }
    }
}
