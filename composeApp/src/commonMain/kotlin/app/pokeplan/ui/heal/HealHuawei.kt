package app.pokeplan.ui.heal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import app.pokeplan.app.heal.HuaweiControls
import app.pokeplan.app.heal.HuaweiWalk
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.rememberInForeground
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.HuaweiReader
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label

@Composable
internal fun HuaweiProbeBox(model: AppModel, reader: HuaweiReader, startShowing: Boolean = false) {
    var state by remember { mutableStateOf(reader.state()) }
    var seen by remember { mutableStateOf(reader.seen()) }
    var note by remember { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf(startShowing) }
    var scanAll by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val heal = model.heal
    val settings = model.healData.settings
    var dismissed by remember { mutableStateOf(0) } // the number of pages seen when the player said Not now
    // Rechecked every second while the page is open: both readers are switched in Android's settings, and pages arrive while Huawei Health is in front.
    val foreground = rememberInForeground()
    LaunchedEffect(reader, foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) { state = reader.state(); seen = reader.seen(); delay(if (state.walking) 1_000 else 3_000) }
    }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Huawei Health")
            if (!state.installed) Text("Huawei Health is not installed", style = MaterialTheme.typography.bodyLarge)
            else {
                StatusLine("Screen reading", if (state.screenReading) "On" else "Off")
                StatusLine("Notification reading", if (state.notificationReading) "On" else "Off")
                val home = remember(seen) { seen.home }
                (seen.latestSteps ?: home.steps)?.let { StatusLine("Steps", it.toString()) }
                home.sleepMinutes?.let { StatusLine("Sleep", "${it / 60} h ${it % 60} min") }
                home.getUpMinute?.let { StatusLine("Got up", "${it / 60}:${(it % 60).toString().padStart(2, '0')}") }
                home.weight?.let { StatusLine("Weight", "${app.pokeplan.heal.HealRules.format(it)} ${home.weightUnit}") }
                home.heartRate?.let { StatusLine("Heart rate", "$it bpm") }
                home.exerciseKm?.let { StatusLine("Exercise", "${app.pokeplan.heal.HealRules.format(it)} km") }
                home.restingHeartRate?.let { StatusLine("Resting heart rate", "$it bpm") }
                home.spo2?.let { StatusLine("SpO2", "$it%") }
                home.skinTemperature?.let { StatusLine("Skin temperature", "${app.pokeplan.heal.HealRules.format(it)} °C") }
                home.stress?.let { StatusLine("Stress", it.toString()) }
                home.sleepScore?.let { StatusLine("Sleep score", it.toString()) }
                if (!state.screenReading && !state.canSwitchItself) {
                    PixelButton("Turn on screen reading", { reader.openScreenReadingSettings() }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                    Text("Greyed out? App info → ⋮ → Allow restricted settings", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelButton("Open App info", { reader.openAppInfo() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
                if (!state.notificationReading) PixelButton("Turn on notification reading", { reader.openNotificationSettings() }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                if (state.screenReading || state.canSwitchItself) {
                    // Two reads (owner, 22 November 2026): only what is new, or every page and its All data again.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        TabButton("New only", !scanAll, { scanAll = false }, Modifier.weight(1f), enabled = !state.walking)
                        TabButton("Everything", scanAll, { scanAll = true }, Modifier.weight(1f), enabled = !state.walking)
                    }
                    PixelButton(if (scanAll) "Read everything" else "Read new", {
                        note = null
                        val before = reader.seen().screens.size
                        val scan = if (scanAll) HuaweiWalk.Scan.ALL else HuaweiWalk.Scan.NEW
                        state = state.copy(walking = true)
                        reader.walk(heal.huaweiKnown(), { problem ->
                            seen = reader.seen(); state = reader.state()
                            heal.markHuaweiRead()
                            val added = (seen.screens.size - before).coerceAtLeast(0)
                            note = problem ?: when {
                                added == 0 && scan == HuaweiWalk.Scan.ALL -> "Read everything; no new pages" // copy-ok: result
                                added == 0 -> "No new pages" // copy-ok: result
                                scan == HuaweiWalk.Scan.ALL -> "Read everything; $added new ${if (added == 1) "page" else "pages"}" // copy-ok: result
                                else -> "$added new ${if (added == 1) "page" else "pages"}" // copy-ok: result
                            }
                        }, scan)
                    }, Modifier.fillMaxWidth(), Pixel.Tone, enabled = !state.walking, compact = true, loading = state.walking)
                }
                if (state.screenReading && !state.walking) PixelButton("Turn off screen reading", { reader.turnOff() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                Text("Off after each read, for banking apps", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            // What the pages hold as Heal records, previewed before anything is saved (HEAL.md 10.3); Huawei sessions pay like ones logged by hand.
            // Not while a read is running: pages arrive every second then, and parsing every captured page each time froze the screen (21 November 2026).
            val read = remember(seen, state.walking) { if (state.walking) app.pokeplan.heal.ProviderRead(emptyList(), 0, 0) else heal.readHuawei(seen) }
            val plan = remember(read, model.healData) { heal.previewImport(read) }
            if (!plan.isEmpty && dismissed != seen.screens.size) ImportPreview(plan,
                onAdd = { val p = heal.importFromProvider(read); note = "Added ${count(p.candidates.size, "record")}" }, // copy-ok: result
                onCancel = { dismissed = seen.screens.size })
            if (state.installed) {
                PixelTitle("Remind me to read")
                val every = settings.huaweiEveryDays
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    TabButton("Off", every == null, { heal.setHuaweiReminder(null, settings.huaweiRemindAt) }, Modifier.weight(1f))
                    TabButton("Nightly", every == 1, { heal.setHuaweiReminder(1, settings.huaweiRemindAt) }, Modifier.weight(1f))
                    TabButton("Weekly", every == 7, { heal.setHuaweiReminder(7, settings.huaweiRemindAt) }, Modifier.weight(1f))
                }
                if (every != null) PixelTimeField(settings.huaweiRemindAt, { t -> t?.let { heal.setHuaweiReminder(every, it) } }, ModuleAccent.HEAL.accent, label = "At")
            }
            if (!seen.isEmpty) {
                StatusLine("Seen", "${count(seen.screens.size, "page")}, ${count(seen.notifications.size, "notification")}")
                if (showing) {
                    ListRow {
                        Text("Controls", style = MaterialTheme.typography.bodyLarge)
                        Text(HuaweiControls.lines(seen).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Pixel.InkSoft)
                    }
                    if (seen.log.isNotEmpty()) ListRow {
                        Text("Walk log", style = MaterialTheme.typography.bodyLarge)
                        Text(seen.log.joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Pixel.InkSoft)
                    }
                }
                // Only the newest pages are drawn: a long read keeps up to 150, and laying out all their text made this page unresponsive. Save, numbers hidden has them all.
                if (showing && seen.screens.size > SHOWN_PAGES) Text("The newest $SHOWN_PAGES of ${seen.screens.size} pages; Save, numbers hidden has all of them", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                if (showing) seen.screens.takeLast(SHOWN_PAGES).forEach { s ->
                    ListRow {
                        Text(s.title.ifBlank { "Page" }, style = MaterialTheme.typography.bodyLarge)
                        Text(s.nodes.joinToString("\n") { it.line() }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Pixel.InkSoft)
                    }
                }
                if (showing) seen.notifications.forEach { n ->
                    ListRow {
                        Text(if (n.customLayout) "Notification (no text)" else "Notification", style = MaterialTheme.typography.bodyLarge)
                        Text(listOf(n.title, n.text, n.subText, n.bigText).filter { it.isNotBlank() }.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft)
                    }
                }
                PixelButton(if (showing) "Hide controls and pages" else "Show controls and pages", { showing = !showing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                PixelButton("Save, numbers hidden", {
                    val text = seen.sample(AppVersion.NAME)
                    note = model.files?.save("pokelife-huawei-probe.txt", text) ?: run { clipboard.setText(AnnotatedString(text)); "Copied with every number hidden" } // copy-ok: result
                }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                PixelButton("Clear", { reader.clear(); seen = reader.seen(); showing = false; note = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
            }
            note?.let { Report(model, it) }
        }
    }
}

private const val SHOWN_PAGES = 6

private fun count(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
