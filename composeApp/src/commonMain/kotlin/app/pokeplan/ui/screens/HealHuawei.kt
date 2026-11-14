package app.pokeplan.ui.screens

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
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.HuaweiReader
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * Heal → Connections → Huawei Health, probe stage (INTEGRATIONS_AND_PRIVACY.md 5.2.2): turn on the two readers in Android's settings, read Huawei Health on a
 * tap, see what was seen, and copy it with every number hidden. Nothing here is saved or paid.
 */
@Composable
internal fun HuaweiProbeBox(model: AppModel, reader: HuaweiReader, startShowing: Boolean = false) {
    var state by remember { mutableStateOf(reader.state()) }
    var seen by remember { mutableStateOf(reader.seen()) }
    var note by remember { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf(startShowing) }
    val clipboard = LocalClipboardManager.current
    // Rechecked every second while the page is open: both readers are switched in Android's settings, and pages arrive while Huawei Health is in front.
    LaunchedEffect(reader) { while (true) { state = reader.state(); seen = reader.seen(); delay(1_000) } }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Huawei Health")
            if (!state.installed) Text("Huawei Health is not installed", style = MaterialTheme.typography.bodyLarge)
            else {
                StatusLine("Screen reading", if (state.screenReading) "On" else "Off")
                StatusLine("Notification reading", if (state.notificationReading) "On" else "Off")
                seen.latestSteps?.let { StatusLine("Steps", it.toString()) }
                if (!state.screenReading) {
                    PixelButton("Turn on screen reading", { reader.openScreenReadingSettings() }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                    Text("Greyed out? App info → ⋮ → Allow restricted settings", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelButton("Open App info", { reader.openAppInfo() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
                if (!state.notificationReading) PixelButton("Turn on notification reading", { reader.openNotificationSettings() }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                if (state.screenReading) PixelButton("Read Huawei Health", {
                    note = null
                    val before = reader.seen().screens.size
                    state = state.copy(walking = true)
                    reader.walk { problem ->
                        seen = reader.seen(); state = reader.state()
                        val added = (seen.screens.size - before).coerceAtLeast(0)
                        note = problem ?: if (added == 0) "No new pages" else "$added new ${if (added == 1) "page" else "pages"}" // copy-ok: result
                    }
                }, Modifier.fillMaxWidth(), Pixel.Tone, enabled = !state.walking, compact = true, loading = state.walking)
            }
            if (!seen.isEmpty) {
                StatusLine("Seen, not saved", "${count(seen.screens.size, "page")}, ${count(seen.notifications.size, "notification")}")
                if (showing) seen.screens.forEach { s ->
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
                PixelButton(if (showing) "Hide what it saw" else "Show what it saw", { showing = !showing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                PixelButton("Copy, numbers hidden", { clipboard.setText(AnnotatedString(seen.sample(AppVersion.NAME))); note = "Copied with every number hidden" }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true) // copy-ok: result
                PixelButton("Clear", { reader.clear(); seen = reader.seen(); showing = false; note = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
            }
            note?.let { Report(model, it) }
        }
    }
}

private fun count(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
