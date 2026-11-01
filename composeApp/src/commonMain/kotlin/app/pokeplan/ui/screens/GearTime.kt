package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.app.GearTimers
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.QUICK_TIMER_MINUTES
import app.pokeplan.ui.gear.formatStopwatch
import app.pokeplan.ui.gear.formatTimer
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * The Pokégear timers: several at once, each named, started from a quick duration or your own. They keep running when the app is closed (the
 * end time is saved and the Android app sets an alarm and a notification with Stop and +1 min), and a ringing one waits for you here too.
 * A timer opened from Focus is Plan's, not one of these; these pay nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    var now by remember { mutableLongStateOf(store.nowMillis) }
    // The clock face follows the clock twice a second while the tool is open; a timer that ran out is marked finished once.
    LaunchedEffect(Unit) { while (true) { now = store.nowMillis; store.timersTick(); delay(500) } }
    val timers = model.data.gear.timers
    var label by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    val ringing = timers.filter { it.finished }

    if (ringing.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Time is up: ${ringing.joinToString(", ") { it.label }}", style = MaterialTheme.typography.titleSmall, color = Pixel.Red)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("stop", "Stop", { store.timersDismissFinished(); onUse() }, Modifier.weight(1f), Pixel.Red)
                PixelButton("+1 min", { ringing.forEach { store.timerAddTime(it.id, 60_000) }; onUse() }, Modifier.weight(1f), Pixel.Blue, compact = true)
            }
        }
    }
    timers.filter { !it.finished }.forEach { t ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall)
                Text(formatTimer(t.leftAt(now)), style = MaterialTheme.typography.headlineMedium, color = if (t.running) Pixel.Ink else Pixel.InkSoft)
                Text(if (t.running) "Running" else if (t.paused) "Paused" else "Ready: ${formatTimer(t.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (t.running) PixelIconButton("pause", "Pause", { store.timerPause(t.id); onUse() }, Modifier.weight(1f), Pixel.InkSoft)
                    else PixelButton(if (t.paused) "Resume" else "Start", { store.timerStart(t.id); onUse() }, Modifier.weight(1f), Pixel.Blue, compact = true)
                    PixelButton("+1 min", { store.timerAddTime(t.id, 60_000); onUse() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelIconButton("reload", "Reset", { store.timerReset(t.id) }, Modifier.weight(1f), Pixel.InkSoft)
                    PixelIconButton("trash", "Delete", { store.timerRemove(t.id) }, Modifier.weight(1f), Pixel.InkSoft)
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("A new timer (up to ${GearTimers.MAX_TIMERS})")
            PixelField(label, { label = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = "Name (optional)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                QUICK_TIMER_MINUTES.forEach { m ->
                    PixelButton("$m min", { store.timerAdd(label, m * 60_000L)?.let { store.timerStart(it.id); label = "" }; onUse() }, color = Pixel.Blue, compact = true, enabled = timers.size < GearTimers.MAX_TIMERS)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(minutesText, { minutesText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Minutes")
                val minutes = minutesText.replace(',', '.').toDoubleOrNull()
                PixelButton("Start", { minutes?.let { m -> store.timerAdd(label, (m * 60_000).toLong())?.let { t -> store.timerStart(t.id); label = ""; minutesText = "" } }; onUse() }, Modifier.weight(0.6f), Pixel.Blue, compact = true, enabled = minutes != null && minutes > 0 && timers.size < GearTimers.MAX_TIMERS)
            }
            Text("Timers keep going when the app is closed and ring with a notification (allow notifications to hear them). A direct timer pays nothing; a Focus session is Plan's own.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

/** The Pokégear stopwatch: start and pause, laps with each lap's own time, the fastest and slowest marked and the average, and a copy of the table. */
@Composable
internal fun StopwatchTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val watch = model.data.gear.stopwatch
    var now by remember { mutableLongStateOf(store.nowMillis) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(watch.running) { while (watch.running) { now = store.nowMillis; delay(50) }; now = store.nowMillis }
    val elapsed = watch.elapsedAt(now)
    val splits = GearTimers.splits(watch.laps)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(formatStopwatch(elapsed), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (watch.running) PixelIconButton("pause", "Pause", { store.stopwatchPause(); onUse() }, Modifier.weight(1f), Pixel.InkSoft)
                else PixelButton(if (watch.accumulatedMs > 0) "Resume" else "Start", { store.stopwatchStart(); onUse() }, Modifier.weight(1f), Pixel.Blue, compact = true)
                PixelButton("Lap", { store.stopwatchLap(); onUse() }, Modifier.weight(1f), Pixel.Blue, compact = true, enabled = watch.running)
                PixelIconButton("reload", "Reset", { store.stopwatchReset() }, Modifier.weight(1f), Pixel.InkSoft, enabled = watch.running || watch.accumulatedMs > 0 || watch.laps.isNotEmpty())
            }
            if (splits.isNotEmpty()) {
                val fastest = splits.min()
                val slowest = splits.max()
                FieldLabel("Laps")
                splits.indices.reversed().forEach { i ->
                    val mark = if (splits.size > 1 && splits[i] == fastest) "  fastest" else if (splits.size > 1 && splits[i] == slowest) "  slowest" else ""
                    Text("${i + 1}.  ${formatStopwatch(splits[i])}   total ${formatStopwatch(watch.laps[i])}$mark", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Average lap ${formatStopwatch(splits.sum() / splits.size)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Copy the laps", { clipboard.setText(AnnotatedString(splits.indices.joinToString("\n") { i -> "${i + 1}\t${formatStopwatch(splits[i])}\t${formatStopwatch(watch.laps[i])}" })) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            Text("It keeps counting when the app is closed. A phone clock set back never takes time away.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}
