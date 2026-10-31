package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The utilities screen: the flashlight, a pocket calculator, a stopwatch, and links to the phone's own panels (internet,
 * volume, Bluetooth, display, battery saver, alarms). None of it needs a permission.
 */
@Composable
internal fun UtilitiesPage(host: LauncherHost, pixel: Int, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XL, bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine("Utilities", pixel, dim = true) {}
                }
            }
            if (host.hasTorch()) {
                var on by remember { mutableStateOf(host.torchOn()) }
                PmdBox(pixel, Modifier.fillMaxWidth()) { PmdMenuLine("Flashlight: ${if (on) "On" else "Off"}", pixel) { on = host.setTorch(!on) } }
            }
            CalculatorBox(pixel, pad)
            StopwatchBox(pixel, pad)
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdText("The phone's own pages", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.75f)
                    PmdRule(pixel)
                    Utility.entries.forEach { u -> PmdMenuLine(u.label, pixel) { host.openUtility(u) } }
                }
            }
        }
    }
}

@Composable
private fun CalculatorBox(pixel: Int, pad: androidx.compose.ui.unit.Dp) {
    var calc by remember { mutableStateOf(Calculator()) }
    // The calculator keeps its own state; this counter makes the display redraw after each key.
    var keys by remember { mutableLongStateOf(0L) }
    fun press(action: Calculator.() -> Unit) { calc.action(); keys++ }
    val rows: List<List<Pair<String, Calculator.() -> Unit>>> = listOf(
        listOf<Pair<String, Calculator.() -> Unit>>("C" to { clear() }, "Del" to { delete() }, "+-" to { negate() }, "div" to { operate('/') }),
        listOf<Pair<String, Calculator.() -> Unit>>("7" to { digit('7') }, "8" to { digit('8') }, "9" to { digit('9') }, "x" to { operate('x') }),
        listOf<Pair<String, Calculator.() -> Unit>>("4" to { digit('4') }, "5" to { digit('5') }, "6" to { digit('6') }, "-" to { operate('-') }),
        listOf<Pair<String, Calculator.() -> Unit>>("1" to { digit('1') }, "2" to { digit('2') }, "3" to { digit('3') }, "+" to { operate('+') }),
        listOf<Pair<String, Calculator.() -> Unit>>("0" to { digit('0') }, "." to { point() }, "Eq" to { equals() }),
    )
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            // keys is read so the display follows each press.
            Box(Modifier.fillMaxWidth().height(Spacing.Target + Spacing.M).padding(horizontal = pad), contentAlignment = Alignment.CenterEnd) { PmdText(if (keys >= 0) calc.display else "", pixel) }
            PmdRule(pixel)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    row.forEachIndexed { i, (label, action) ->
                        // The bottom row's zero is two keys wide.
                        Box(
                            Modifier.weight(if (row.size == 3 && i == 0) 2f else 1f).height(Spacing.Target + Spacing.S).background(Color.White.copy(alpha = 0.12f))
                                .clickable(role = Role.Button, onClickLabel = label) { press(action) },
                            contentAlignment = Alignment.Center,
                        ) { PmdText(label, pixel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchBox(pixel: Int, pad: androidx.compose.ui.unit.Dp) {
    var running by remember { mutableStateOf(false) }
    var before by remember { mutableLongStateOf(0L) }
    var mark by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var shown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) { while (running) { shown = before + mark.elapsedNow().inWholeMilliseconds; delay(100) } }
    val tenths = shown / 100
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(Spacing.Target + Spacing.M).padding(horizontal = pad), contentAlignment = Alignment.CenterStart) {
                PmdText("${two((tenths / 600).toInt())}:${two((tenths / 10 % 60).toInt())}.${tenths % 10}", pixel)
            }
            PmdRule(pixel)
            Row(Modifier.fillMaxWidth()) {
                PmdMenuLine(if (running) "Stop" else "Start", pixel, Modifier.weight(1f), center = true) {
                    if (running) { before += mark.elapsedNow().inWholeMilliseconds; shown = before; running = false } else { mark = TimeSource.Monotonic.markNow(); running = true }
                }
                PmdMenuLine("Reset", pixel, Modifier.weight(1f), center = true, dim = shown == 0L && !running) { running = false; before = 0L; shown = 0L }
            }
        }
    }
}
