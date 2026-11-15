package app.pokeplan.ui.gear

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
import app.pokeplan.app.ExactInput
import app.pokeplan.app.GearTimer
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
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.plan.duration
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.TypedValueField
import app.pokeplan.ui.screens.label

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
    // Only while the app is in sight: a stopped Activity keeps its composition, and the timers' alarms ring on their own.
    val foreground = app.pokeplan.ui.rememberInForeground()
    LaunchedEffect(foreground) { while (foreground) { now = store.nowMillis; store.timersTick(); delay(500) } }
    val timers = model.data.gear.timers
    var label by remember { mutableStateOf("") }
    var typedSeconds by remember { mutableStateOf<Long?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val ringing = timers.filter { it.finished }

    if (ringing.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Time is up: ${ringing.joinToString(", ") { it.label }}", style = MaterialTheme.typography.titleSmall, color = Pixel.Red)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelIconButton("stop", "Stop", { store.timersDismissFinished(); onUse() }, Pixel.Red)
                PixelButton("+1 min", { ringing.forEach { store.timerAddTime(it.id, 60_000) }; onUse() }, Modifier.weight(1f), Pixel.Tone, compact = true)
            }
        }
    }
    timers.filter { !it.finished }.forEach { t ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall)
                Text(formatTimer(t.leftAt(now)), style = MaterialTheme.typography.headlineMedium, color = if (t.running) Pixel.Ink else Pixel.InkSoft)
                Text(if (t.running) "Running" else if (t.paused) "Paused" else "Ready: ${formatTimer(t.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (t.running) PixelIconButton("pause", "Pause", { store.timerPause(t.id); onUse() }, Pixel.InkSoft)
                    else PixelButton(if (t.paused) "Resume" else "Start", { store.timerStart(t.id); onUse() }, Modifier.weight(1f), Pixel.Tone, compact = true)
                    PixelButton("+1 min", { store.timerAddTime(t.id, 60_000); onUse() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("reload", "Reset", { store.timerReset(t.id) }, Pixel.InkSoft)
                    PixelIconButton("pencil", "Edit", { editingId = if (editingId == t.id) null else t.id }, Pixel.InkSoft)
                    PixelIconButton("trash", "Delete", { store.timerRemove(t.id) }, Pixel.InkSoft)
                }
                if (editingId == t.id) TimerEditor(t) { name, ms -> store.timerEdit(t.id, name, ms); editingId = null; onUse() }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("A new timer (up to ${GearTimers.MAX_TIMERS})")
            PixelField(label, { label = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = "Name (optional)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                QUICK_TIMER_MINUTES.forEach { m ->
                    PixelButton("$m min", { store.timerAdd(label, m * 60_000L)?.let { store.timerStart(it.id); label = "" }; onUse() }, color = Pixel.Tone, compact = true, enabled = timers.size < GearTimers.MAX_TIMERS)
                }
            }
            TimerLength(typedSeconds, { typedSeconds = it })
            PixelButton(
                "Start", { typedSeconds?.let { sec -> store.timerAdd(label, sec * 1000)?.let { t -> store.timerStart(t.id); label = ""; typedSeconds = null } }; onUse() },
                Modifier.fillMaxWidth(), Pixel.Tone, compact = true, enabled = typedSeconds != null && timers.size < GearTimers.MAX_TIMERS,
            )
        }
    }
}

/** A length typed as hours, minutes and seconds (`90`, `1h30`, `1:30:00`, `45s`); a bare number is minutes. */
@Composable
internal fun TimerLength(seconds: Long?, onChange: (Long?) -> Unit) {
    TypedValueField(
        seconds, onChange, { ExactInput.parseDurationSeconds(it, 'm') }, { formatHms(it) },
        placeholder = "Length: 90, 1h30, 1:30:00, 45s", takes = "A length such as 25, 1h30, 1:30:00 or 45s", allowEmpty = true, label = "Length",
        reject = { if (it * 1000 !in GearTimers.MIN_MS..GearTimers.MAX_MS) "Between ${formatHms(GearTimers.MIN_MS / 1000)} and ${formatHms(GearTimers.MAX_MS / 1000)}" else null },
    )
}

internal fun formatHms(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Renames a timer and sets its length; saving puts it back to ready. */
@Composable
private fun TimerEditor(t: GearTimer, onSave: (String, Long) -> Unit) {
    var name by remember(t.id) { mutableStateOf(t.label) }
    var seconds by remember(t.id) { mutableStateOf<Long?>(t.durationMs / 1000) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = "Name")
        TimerLength(seconds, { seconds = it })
        PixelButton("Save (it goes back to ready)", { seconds?.let { onSave(name, it * 1000) } }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true, enabled = seconds != null)
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (watch.running) PixelIconButton("pause", "Pause", { store.stopwatchPause(); onUse() }, Pixel.InkSoft)
                else PixelButton(if (watch.accumulatedMs > 0) "Resume" else "Start", { store.stopwatchStart(); onUse() }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("Lap", { store.stopwatchLap(); onUse() }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = watch.running)
                PixelIconButton("reload", "Reset", { store.stopwatchReset() }, Pixel.InkSoft, enabled = watch.running || watch.accumulatedMs > 0 || watch.laps.isNotEmpty())
            }
            if (splits.isNotEmpty()) {
                val fastest = splits.min()
                val slowest = splits.max()
                FieldLabel("Laps")
                splits.indices.reversed().forEach { i ->
                    val mark = if (splits.size > 1 && splits[i] == fastest) "  fastest" else if (splits.size > 1 && splits[i] == slowest) "  slowest" else ""
                    Text("${i + 1}.  ${formatStopwatch(splits[i])}   total ${formatStopwatch(watch.laps[i])}$mark", style = MaterialTheme.typography.bodyMedium)
                }
                PixelButton("Copy the laps", { clipboard.setText(AnnotatedString(splits.indices.joinToString("\n") { i -> "${i + 1}\t${formatStopwatch(splits[i])}\t${formatStopwatch(watch.laps[i])}" })) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
}
