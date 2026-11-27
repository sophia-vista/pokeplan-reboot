package app.pokeplan.ui.plan

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.app.integrations.EventKeys
import app.pokeplan.app.integrations.ProviderEdit
import app.pokeplan.app.integrations.ProviderFields
import app.pokeplan.app.integrations.ProviderRow
import app.pokeplan.app.integrations.SourceSnapshot
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.PlanDays
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.integrations.EventMemoryDraft
import app.pokeplan.ui.FeedbackKind
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

private enum class Scope(val label: String) { THIS("Only this event"), ALL("All events in the series") }

/**
 * An event from a phone calendar (PLAN 8.1B, 8.4, 8.5). It always shows where it comes from and what the calendar says, drawn as
 * read-only imported information, and "Your additions" below it. When the event is in the ONE calendar chosen for the sync it can
 * also be edited or deleted, and a repeating event asks whether the change is for one occurrence or the whole series; every other
 * calendar stays read-only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceEventPanel(model: AppModel, event: CalendarEvent, close: () -> Unit) {
    val sync = model.planSync
    val zone = remember { TimeZone.currentSystemDefault() }
    val providerId = event.id.substringBefore('@').toLongOrNull()
    val originalStart = event.id.substringAfter('@', "").toLongOrNull()
    val calendar = remember(event.calendarId) { model.calendars?.calendars()?.firstOrNull { it.id == event.calendarId } }
    val source = if (calendar?.google == true) EntrySource.GOOGLE else EntrySource.PHONE
    val editable = providerId != null && sync?.canEdit(event.calendarId, providerId) == true
    var row by remember(event.id) { mutableStateOf<ProviderRow?>(null) }
    LaunchedEffect(event.id, model.calendarRevision) {
        row = if (editable && sync != null && providerId != null) withContext(Dispatchers.Default) { sync.open(event.calendarId, providerId) } else null
    }
    val key = EventKeys.ofDeviceEvent(event.calendarId, event.id)
    val snapshot = SourceSnapshot(event.shownTitle, event.start.date.toString(), calendar?.name.orEmpty())
    val scope = rememberCoroutineScope()
    val today = model.store.today
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            SourceTag(source, eventColor(event), calendar?.name)
            val live = row
            if (editable && live != null) {
                val repeating = ProviderEdit.repeats(live.event)
                var which by remember(event.id) { mutableStateOf(if (repeating) Scope.THIS else Scope.ALL) }
                if (repeating) PixelChoice(Scope.entries.toList(), which, { it?.label ?: "" }, { s -> if (s != null) which = s }, label = "Change")
                val start = remember(event.id, which, live) { fieldsFor(event, live, which, zone) }
                var f by remember(event.id, which, live) { mutableStateOf(start) }
                var confirming by remember(event.id) { mutableStateOf(false) }
                if (f == null) {
                    Text("This event can't be edited here.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                } else {
                    val v = f!!
                    FormGroup(title = "Edit") {
                        PixelField(v.title, { f = v.copy(title = it) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true)
                        PixelField(
                            v.description, { f = v.copy(description = it) }, Modifier.fillMaxWidth(), label = "Description", minLines = 2, maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        PixelField(v.location, { f = v.copy(location = it) }, Modifier.fillMaxWidth(), label = "Location", singleLine = true)
                        val dayEditable = !repeating || which == Scope.THIS
                        if (v.allDay) {
                            if (dayEditable) DaySpanPicker(v.day, v.last, today, { d -> f = v.copy(day = d, last = maxOf(v.last, d)) }) { l -> f = v.copy(last = l) }
                        } else {
                            if (dayEditable) {
                                FieldLabel("Day")
                                PixelDateField(v.day, today, Pixel.Tone, { it?.let { d -> f = v.copy(day = d) } }, presets = PlanDays.presets(today), pickLabel = "Pick a day")
                            }
                            PixelTimeField(v.start, { t -> t?.let { f = v.copy(start = it) } }, Pixel.Tone, label = "Starts")
                            PixelDurationField(
                                v.minutes, { m -> m?.let { f = v.copy(minutes = it) } }, Pixel.Tone, label = "Length",
                                presets = listOf(30, 60, 90, 120), min = 1,
                            )
                        }
                    }
                    if (confirming) {
                        Text(
                            "Delete " + (if (repeating) (if (which == Scope.THIS) "this event" else "every event in the series") else "this event") + " from your calendar?",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (!confirming) {
                            PixelIconButton("check", "Save to your calendar", {
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) { save(sync!!, event, live, v, which, repeating, originalStart, zone) }
                                    if (ok) { model.calendarChanged(); model.say("Saved to your calendar"); close() }
                                    else model.say("Couldn't save to your calendar", FeedbackKind.ERROR)
                                }
                            }, Pixel.Tone, enabled = v.title.isNotBlank())
                            PixelIconButton("trash", "Delete", { confirming = true }, Pixel.Red)
                            PixelButton("Close", close, color = Pixel.InkSoft, compact = true)
                        } else {
                            PixelButton("Delete", {
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) {
                                        if (repeating && which == Scope.THIS && originalStart != null) sync!!.deleteOccurrence(event.calendarId, providerId!!, originalStart)
                                        else sync!!.deleteEvent(event.calendarId, providerId!!)
                                    }
                                    if (ok) { model.calendarChanged(); model.say("Deleted from your calendar"); close() }
                                    else { confirming = false; model.say("Couldn't delete it from your calendar", FeedbackKind.ERROR) }
                                }
                            }, color = Pixel.Red, compact = true)
                            PixelButton("Keep", { confirming = false }, color = Pixel.InkSoft, compact = true)
                        }
                    }
                }
            } else {
                // Imported information: what the calendar says, with no edit control.
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(event.shownTitle, style = MaterialTheme.typography.titleMedium, maxLines = 4)
                        Text(whenText(event), style = MaterialTheme.typography.bodyLarge)
                        if (event.location.isNotBlank() && !event.private) Text(event.location, style = MaterialTheme.typography.bodyLarge)
                        if (event.description.isNotBlank() && !event.private) Text(event.description, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 12)
                        if (!editable) Text("Read-only here", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                    }
                }
                if (key == null) PixelButton("Close", close, color = Pixel.InkSoft, compact = true)
            }
            if (key != null) {
                EventEnrichmentSection(model, key, snapshot, EventMemoryDraft(event.shownTitle, event.start.date, model.integrations.peopleOf(key)))
                if (!editable || row == null) PixelButton("Close", close, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

private fun whenText(e: CalendarEvent): String =
    if (e.allDay) e.start.date.short() + if (e.lastDay > e.start.date) " – ${e.lastDay.short()}" else ""
    else "${e.start.date.short()} ${clock(e.start.time)} – ${clock(e.end.time)}"

/** The editor's starting fields: one occurrence takes what the Calendar shows, the whole series what the provider holds. */
private fun fieldsFor(event: CalendarEvent, row: ProviderRow, scope: Scope, zone: TimeZone): ProviderFields? {
    val held = ProviderEdit.fields(row.event, zone) ?: return null
    if (scope == Scope.ALL) return held
    val minutes = if (event.allDay) 24 * 60 else ((event.end.toInstant(zone).toEpochMilliseconds() - event.start.toInstant(zone).toEpochMilliseconds()) / 60_000L).toInt().coerceAtLeast(1)
    return held.copy(
        allDay = event.allDay, day = event.start.date, last = event.lastDay, start = if (event.allDay) LocalTime(0, 0) else event.start.time, minutes = minutes,
    )
}

private fun kotlinx.datetime.LocalDateTime.toInstant(zone: TimeZone) = with(zone) { this@toInstant.toInstant() }

private fun save(
    sync: app.pokeplan.app.integrations.PlanCalendarSync, event: CalendarEvent, row: ProviderRow, f: ProviderFields, scope: Scope,
    repeating: Boolean, originalStart: Long?, zone: TimeZone,
): Boolean {
    val providerId = row.id
    return when {
        !repeating || scope == Scope.ALL -> sync.saveEvent(event.calendarId, providerId, ProviderEdit.series(row.event, f, zone))
        originalStart != null -> sync.saveOccurrence(event.calendarId, providerId, originalStart, ProviderEdit.occurrence(row.event, f, zone))
        else -> false
    }
}
