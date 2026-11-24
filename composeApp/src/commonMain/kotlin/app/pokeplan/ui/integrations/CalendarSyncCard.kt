package app.pokeplan.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.pokeplan.app.integrations.PlanCalendarSync
import app.pokeplan.app.integrations.SyncReport
import app.pokeplan.app.integrations.TargetCalendar
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.CalendarWriteAccess
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One sync pass off the main thread; a change the calendar made to PLAN is announced with an Undo. */
internal suspend fun runCalendarSync(model: AppModel, sync: PlanCalendarSync): SyncReport? {
    val report = withContext(Dispatchers.Default) { sync.sync() } ?: return null
    announce(model, report)
    return report
}

private fun announce(model: AppModel, report: SyncReport) {
    if (!report.changedPlan) return
    val removed = report.outcome.removedHere
    val label = if (removed > 0 && report.outcome.pulled == 0) "Removed in the calendar: $removed" else "Calendar changed ${report.applied} of your events"
    model.offerUndo(label) { model.store.restoreEvents(report.before) }
}

/**
 * Settings: the two-way sync of PLAN events with one calendar on the phone (a Google calendar, for one). Off until turned on; it
 * asks for the calendar permission only then, and only writes the events PokéLife put there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarSyncCard(model: AppModel) {
    val sync = model.planSync ?: return
    val access = model.calendars as? CalendarWriteAccess ?: return
    val state = model.integrationData.calendarSync
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(access.writePermitted()) }
    var calendars by remember { mutableStateOf<List<TargetCalendar>?>(null) }
    var working by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf(false) }
    LaunchedEffect(permitted, state.enabled) { if (permitted && !state.enabled) calendars = withContext(Dispatchers.Default) { sync.calendars() } }
    val accent = ModuleAccent.PLAN.accent
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Calendar sync")
            when {
                state.enabled && turningOff -> {
                    Text("Turn off for ${state.calendarName}?", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Keep the events there", { sync.disable(removeFromCalendar = false); turningOff = false }, color = Pixel.InkSoft, compact = true)
                        PixelButton("Remove them", {
                            working = true
                            scope.launch { withContext(Dispatchers.Default) { sync.disable(removeFromCalendar = true) }; working = false; turningOff = false }
                        }, color = Pixel.Danger, compact = true)
                        PixelButton("Cancel", { turningOff = false }, color = Pixel.InkSoft, compact = true)
                    }
                }
                state.enabled -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Column(Modifier.weight(1f)) {
                            Text(state.calendarName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (state.lastNote.isNotBlank()) Text(state.lastNote, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Sync now", {
                            working = true
                            scope.launch { runCalendarSync(model, sync); working = false }
                        }, color = accent, compact = true, enabled = !working)
                        PixelButton("Turn off", { turningOff = true }, color = Pixel.InkSoft, compact = true)
                    }
                }
                !permitted -> PixelButton("Allow calendar access", { access.requestWritePermission { permitted = it } }, Modifier.fillMaxWidth(), accent, compact = true)
                calendars == null -> Unit
                calendars.orEmpty().isEmpty() -> Text("No calendar you can write to.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text("Send my events to", style = MaterialTheme.typography.bodyLarge)
                    calendars.orEmpty().forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.account.isNotBlank() && c.account != c.name) Text(c.account, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PixelButton("Use", {
                                working = true
                                scope.launch { val r = withContext(Dispatchers.Default) { sync.enable(c) }; if (r != null) announce(model, r); working = false }
                            }, color = accent, compact = true, enabled = !working)
                        }
                    }
                }
            }
        }
    }
}
