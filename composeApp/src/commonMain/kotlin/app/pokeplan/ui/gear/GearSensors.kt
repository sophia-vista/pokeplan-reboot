package app.pokeplan.ui.gear

import app.pokeplan.ui.theme.drawPixelDisc
import app.pokeplan.ui.theme.drawPixelRing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.pokeplan.ui.rememberInForeground
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.Capability
import app.pokeplan.ui.gear.SensorMath
import app.pokeplan.ui.gear.describe
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.label
import app.pokeplan.app.gear.changeGear

@Composable
internal fun Missing(why: String) {
    TextBox { Text(why, style = MaterialTheme.typography.titleSmall) }
}

private fun needs(model: AppModel, vararg caps: Capability): Capability? = caps.firstOrNull { !model.capabilities.status(it).available }

@Composable
internal fun CompassTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.sensors
    val lack = needs(model, Capability.ACCELEROMETER, Capability.MAGNETOMETER)
    if (feed == null || lack != null) return Missing(if (feed == null) "No compass on this device" else "No ${lack?.label?.lowercase()} on this device")
    var heading by remember { mutableStateOf<Float?>(null) }
    // Stops with the app: a sensor left registered behind a stopped Activity keeps sampling in the background.
    val foreground = rememberInForeground()
    DisposableEffect(feed, foreground) {
        var smooth: Float? = null
        var stop: (() -> Unit)? = null
        if (foreground) {
            stop = feed.orientation { g, m -> SensorMath.heading(g, m)?.let { smooth = SensorMath.smooth(smooth, it); heading = smooth } }
            onUse()
        }
        onDispose { stop?.invoke() }
    }
    val h = heading
    TextBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Canvas(Modifier.size(220.dp)) {
                val r = size.minDimension / 2 - 8f
                val c = Offset(size.width / 2, size.height / 2)
                drawPixelRing(Pixel.Border, c, r, 6f)
                val turn = -(h ?: 0f)
                for (i in 0 until 16) {
                    val a = turn + i * 22.5f
                    val (dx, dy) = SensorMath.pointOnCircle(a, r)
                    val (ix, iy) = SensorMath.pointOnCircle(a, r * if (i % 4 == 0) 0.82f else 0.92f)
                    drawLine(if (i == 0) Pixel.Red else Pixel.Ink, c + Offset(ix, iy), c + Offset(dx, dy), strokeWidth = if (i % 4 == 0) 6f else 3f)
                }
                val (nx, ny) = SensorMath.pointOnCircle(turn, r * 0.7f)
                drawLine(Pixel.Red, c, c + Offset(nx, ny), strokeWidth = 8f)
                drawPixelDisc(Pixel.Ink, c, 8f)
            }
            Text(if (h == null) "Reading the sensors" else "${h.toInt()}° ${SensorMath.cardinal(h)}", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun LevelTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.sensors
    if (feed == null || !model.capabilities.status(Capability.ACCELEROMETER).available) return Missing("No level on this device")
    var tilt by remember { mutableStateOf<SensorMath.Tilt?>(null) }
    var zero by remember { mutableStateOf(SensorMath.Tilt(0f, 0f)) }
    val foreground = rememberInForeground()
    DisposableEffect(feed, foreground) {
        var last: SensorMath.Tilt? = null
        var stop: (() -> Unit)? = null
        if (foreground) {
            stop = feed.gravity { g -> SensorMath.tilt(g)?.let { n -> last = last?.let { SensorMath.Tilt(SensorMath.smooth(it.pitch + 180f, n.pitch + 180f, 0.25f) - 180f, SensorMath.smooth(it.roll + 180f, n.roll + 180f, 0.25f) - 180f) } ?: n; tilt = last } }
            onUse()
        }
        onDispose { stop?.invoke() }
    }
    val t = tilt?.let { SensorMath.Tilt(it.pitch - zero.pitch, it.roll - zero.roll) }
    val level = t != null && SensorMath.isLevel(t)
    TextBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Canvas(Modifier.size(200.dp)) {
                val r = size.minDimension / 2 - 8f
                val c = Offset(size.width / 2, size.height / 2)
                drawPixelRing(if (level) Pixel.Green else Pixel.Border, c, r, 8f)
                drawLine(Pixel.Border, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f); drawLine(Pixel.Border, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)
                t?.let {
                    val dx = (it.roll * -6f).coerceIn(-r * 0.8f, r * 0.8f); val dy = (it.pitch * 6f).coerceIn(-r * 0.8f, r * 0.8f)
                    drawPixelDisc(if (level) Pixel.Green else Pixel.Blue, c + Offset(dx, dy), 18f)
                }
            }
            Text(if (t == null) "Reading the sensor" else if (level) "Level" else SensorMath.degreesText(t.fromFlat) + " off", style = MaterialTheme.typography.headlineMedium)
            if (t != null) Text("Forward and back ${SensorMath.degreesText(t.pitch)}, left and right ${SensorMath.degreesText(t.roll)}", style = MaterialTheme.typography.bodyLarge) // copy-ok: result
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelIconButton("check", "Call this flat", { tilt?.let { zero = it } })
                PixelIconButton("reload", "Back to true flat", { zero = SensorMath.Tilt(0f, 0f) })
            }
        }
    }
}

@Composable
internal fun BarometerTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.sensors
    val store = model.store
    if (feed == null || !model.capabilities.status(Capability.BAROMETER).available) return Missing("No barometer on this device")
    var hpa by remember { mutableStateOf<Float?>(null) }
    val foreground = rememberInForeground()
    DisposableEffect(feed, foreground) {
        var avg: Float? = null
        var stop: (() -> Unit)? = null
        if (foreground) {
            stop = feed.pressure { p -> avg = avg?.let { it + (p - it) * 0.2f } ?: p; hpa = avg }
            onUse()
        }
        onDispose { stop?.invoke() }
    }
    val sea = model.data.gear.seaLevelHpa
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(hpa?.let { SensorMath.pressureText(it) } ?: "Reading the sensor", style = MaterialTheme.typography.headlineMedium)
            hpa?.let { p -> SensorMath.altitudeMeters(p, sea)?.let { Text("About ${it.toInt()} m above the level where the pressure is ${SensorMath.pressureText(sea)}", style = MaterialTheme.typography.bodyLarge) } } // copy-ok: result
            FieldLabel("Pressure at sea level today")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("minus", "One hPa lower", { store.changeGear { it.copy(seaLevelHpa = (it.seaLevelHpa - 1f).coerceAtLeast(950f)) } })
                Text(SensorMath.pressureText(sea), style = MaterialTheme.typography.bodyLarge)
                PixelIconButton("plus", "One hPa higher", { store.changeGear { it.copy(seaLevelHpa = (it.seaLevelHpa + 1f).coerceAtMost(1050f)) } })
                PixelIconButton("reload", "Standard pressure", { store.changeGear { it.copy(seaLevelHpa = 1013.25f) } })
            }
        }
    }
}

@Composable
internal fun DeviceTool(model: AppModel) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            val all = model.capabilities.all()
                        all.forEach { s -> Text(s.describe(), style = MaterialTheme.typography.bodyMedium, color = if (s.available) Pixel.Ink else Pixel.InkSoft) }
        }
    }
}
