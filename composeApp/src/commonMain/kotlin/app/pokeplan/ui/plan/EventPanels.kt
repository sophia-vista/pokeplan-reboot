package app.pokeplan.ui.plan

import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.plan.PlanDays
import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.minus
import androidx.compose.foundation.selection.selectable
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.integrations.EventMemoryDraft
import app.pokeplan.ui.integrations.peopleOfEvent
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewEventPanel(draft: Draft, change: (Draft) -> Unit, add: () -> Unit, today: LocalDate = draft.day, party: List<app.pokeplan.core.pokemon.OwnedPokemon> = emptyList(), model: AppModel? = null, cancel: () -> Unit) {
    val at = LocalTime(draft.start / 60, draft.start % 60)
    var more by remember { mutableStateOf(draft.repeat != null || draft.color != null || draft.companion != null || draft.people.isNotEmpty() || draft.description.isNotBlank() || draft.location.isNotBlank() || draft.reminder != null) }
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.L)) {
            PixelTitle("New event")
            PixelField(
                draft.title, { change(draft.copy(title = it)) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, placeholder = "What",
            )
            Segmented(listOf("At a time", "All day"), if (draft.allDay) 1 else 0) { change(draft.copy(allDay = it == 1, lastDay = draft.day)) }
            val details: @Composable () -> Unit = {
                FormDisclosure("Additional details", more, { more = !more }) {
                    FormGroup {
                        PixelField(draft.location, { change(draft.copy(location = it)) }, Modifier.fillMaxWidth(), label = "Location", singleLine = true)
                        PixelField(
                            draft.description, { change(draft.copy(description = it)) }, Modifier.fillMaxWidth(), label = "Description", minLines = 2, maxLines = 6,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        )
                        ReminderPicker(draft.reminder) { change(draft.copy(reminder = it)) }
                        FieldLabel("Colour")
                        EventColors(draft.color) { change(draft.copy(color = it)) }
                        RepeatPicker(draft.repeat) { change(draft.copy(repeat = it)) }
                        if (!draft.allDay) CompanionPicker(party, draft.companion) { change(draft.copy(companion = it)) }
                        if (model != null) app.pokeplan.ui.integrations.EventPeoplePicker(model, draft.people) { change(draft.copy(people = it)) }
                    }
                }
            }
            if (draft.allDay) {
                DaySpanPicker(draft.day, draft.lastDay, today, { change(draft.movedTo(it)) }) { change(draft.copy(lastDay = it)) }
                details()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("plus", "Add", add, Pixel.Tone, enabled = draft.title.isNotBlank())
                    PixelIconButton("close", "Cancel", cancel, Pixel.InkSoft)
                }
                return@Column
            }
            FormGroup {
                FieldLabel("Day")
                PixelDateField(draft.day, today, Pixel.Tone, { it?.let { d -> change(draft.copy(day = d, lastDay = d)) } }, presets = PlanDays.presets(today), pickLabel = "Pick a day")
                PixelTimeField(
                    at, { t -> t?.let { change(draft.copy(start = it.hour * 60 + it.minute).let { d -> d.copy(minutes = PlanEvent.fit(d.start, d.minutes)) }) } },
                    Pixel.Tone, label = "Starts",
                )
                PixelDurationField(
                    draft.minutes, { m -> m?.let { change(draft.copy(minutes = PlanEvent.fit(draft.start, it))) } }, Pixel.Tone, label = "Length",
                    presets = listOf(30, 60, 90, 120),
                    max = 24 * 60 - draft.start, min = 1,
                )
            }
            details()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelIconButton("plus", "Add", add, Pixel.Tone, enabled = draft.title.isNotBlank())
                PixelIconButton("close", "Cancel", cancel, Pixel.InkSoft)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventPanel(model: AppModel, event: PlanEvent, follow: (String) -> Unit, close: () -> Unit) {
    val store = model.store
    var title by remember(PlanEvent.seriesOf(event.id)) { mutableStateOf(event.title) }
    val series = model.data.events.firstOrNull { it.id == PlanEvent.seriesOf(event.id) } ?: event
    val repeats = event.repeat != null
    var description by remember(series.id) { mutableStateOf(series.description) }
    var location by remember(series.id) { mutableStateOf(series.location) }
    // The words are saved as they are typed, so Back never loses them.
    LaunchedEffect(description, location) {
        kotlinx.coroutines.delay(600)
        val live = model.data.events.firstOrNull { it.id == series.id }
        if (live != null && (live.description != description.trim() || live.location != location.trim())) store.setEventDetails(series.id, description, location, live.reminderMinutes)
    }
    val synced = model.integrationData.calendarSync.takeIf { s -> s.enabled && s.links.any { it.eventId == series.id } }
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            SourceTag(EntrySource.EVENT, OwnEventColor, synced?.let { "kept in step with ${it.calendarName}" })
            PixelField(
                title, { title = it }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, placeholder = "Event",
            )
            FieldLabel("Colour")
            EventColors(event.color) { store.updateEvent(event.id, color = it) }
            RepeatPicker(event.repeat) { store.updateEvent(event.id, title = title); follow(store.repeatEvent(event.id, it)) }
            if (repeats) RepeatEnd(series, event) { store.endEventOn(event.id, it) }
            if (!event.allDay) CompanionPicker(model.data.player.partyOf(app.pokeplan.core.pokemon.PartyIds.PLAN), series.companionId) { store.setEventCompanion(event.id, it) }
            PixelField(location, { location = it }, Modifier.fillMaxWidth(), label = "Location", singleLine = true)
            PixelField(
                description, { description = it }, Modifier.fillMaxWidth(), label = "Description", minLines = 2, maxLines = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            )
            ReminderPicker(series.reminderMinutes) { store.setEventDetails(series.id, description, location, it) }
            val deletes: @Composable () -> Unit = {
                if (repeats) {
                    PixelButton("Delete this one", { val before = model.data.events; store.deleteOccurrence(event.id); close(); model.offerUndo("Deleted: ${event.title}") { store.restoreEvents(before) } }, color = Pixel.Red, compact = true)
                    PixelButton("Delete all", { val before = model.data.events; store.deleteEvent(event.id); close(); model.offerUndo("Deleted: ${event.title}") { store.restoreEvents(before) } }, color = Pixel.Red, compact = true)
                } else PixelIconButton("trash", "Delete", { val before = model.data.events; store.deleteEvent(event.id); close(); model.offerUndo("Deleted: ${event.title}") { store.restoreEvents(before) } }, Pixel.Red)
            }
            if (event.allDay) {
                DaySpanPicker(event.day, event.endDay, store.today, { follow(store.updateEvent(event.id, day = it)) }) { store.updateEvent(event.id, lastDay = it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("At a time", { store.updateEvent(event.id, allDay = false); close() }, color = Pixel.InkSoft, compact = true)
                    deletes()
                    PixelIconButton("check", "Done", { store.updateEvent(event.id, title = title); close() }, Pixel.Tone)
                }
                EventEnrichmentSection(model, series.id, null, EventMemoryDraft(title.ifBlank { event.title }, event.day, peopleOfEvent(model, series.id)))
                return@Column
            }
            PixelDateField(event.day, store.today, Pixel.Tone, { it?.let { d -> follow(store.updateEvent(event.id, day = d)) } }, presets = PlanDays.presets(store.today), pickLabel = "Pick a day")
            PixelTimeField(
                event.start, { t -> t?.let { store.updateEvent(event.id, start = it) } }, Pixel.Tone, label = "Starts",
            )
            PixelDurationField(
                event.minutes, { m -> m?.let { store.updateEvent(event.id, minutes = it) } }, Pixel.Tone, label = "Length",
                presets = listOf(30, 60, 90, 120),
                max = 24 * 60 - (event.start.hour * 60 + event.start.minute), min = 1,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("All day", { store.updateEvent(event.id, allDay = true) }, color = Pixel.InkSoft, compact = true)
                deletes()
                PixelIconButton("check", "Done", { store.updateEvent(event.id, title = title); close() }, Pixel.Tone)
            }
            EventEnrichmentSection(model, series.id, null, EventMemoryDraft(title.ifBlank { event.title }, event.day, peopleOfEvent(model, series.id)))
        }
    }
}

@Composable
private fun ReminderPicker(minutes: Int?, pick: (Int?) -> Unit) {
    val options = listOf<Int?>(null) + app.pokeplan.plan.EventReminders.PRESETS + listOfNotNull(minutes?.takeIf { it !in app.pokeplan.plan.EventReminders.PRESETS })
    PixelChoice(options, minutes, { if (it == null) "None" else app.pokeplan.plan.EventReminders.label(it) }, pick, label = "Reminder")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(selected: EventRepeat?, pick: (EventRepeat?) -> Unit) {
    PixelChoice(listOf<EventRepeat?>(null) + EventRepeat.entries, selected, { it?.label ?: "Once" }, pick, label = "Repeats")
}

private const val END_AFTER = 10

@Composable
private fun RepeatEnd(series: PlanEvent, shown: PlanEvent, set: (LocalDate?) -> Unit) {
    val until = series.until
    val open = series.copy(until = null)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (until == null) {
            Text("Never ends.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            PixelButton("Set an end", {
                set(open.startDates(shown.day, shown.day.plus(DatePeriod(years = 20))).filter { it >= shown.day }.take(END_AFTER).lastOrNull())
            }, color = Pixel.InkSoft, compact = true)
            return@Row
        }
        val times = series.startDates(series.day, until).size
        Text("Ends ${until.short()}, $times in all.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val earlier = series.startDates(shown.day, until.minus(DatePeriod(days = 1))).lastOrNull { it >= shown.day }
        val later = open.startDates(until.plus(DatePeriod(days = 1)), until.plus(DatePeriod(years = 2))).firstOrNull { it > until }
        ArrowButton("‹", "End one earlier", enabled = earlier != null) { set(earlier) }
        ArrowButton("›", "End one later", enabled = later != null) { set(later) }
        PixelButton("Never", { set(null) }, color = Pixel.InkSoft, compact = true)
    }
}

@Composable
private fun EventColors(selected: Long?, pick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskList.COLORS.forEachIndexed { i, c ->
            val on = c == (selected ?: TaskList.COLORS[4])
            Box(
                Modifier.size(36.dp).clip(DialogueShape).selectable(on, role = Role.RadioButton) { pick(c) }
                    .semantics { contentDescription = taskColorNames[i] },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).background(Color(c), DialogueShape).border(if (on) 3.dp else 0.dp, if (on) Pixel.Ink else Color.Transparent, DialogueShape))
            }
        }
    }
}

@Composable
internal fun DaySpanPicker(first: LocalDate, last: LocalDate, today: LocalDate, setFirst: (LocalDate) -> Unit, setLast: (LocalDate) -> Unit) {
    FieldLabel("Starts")
    PixelDateField(first, today, Pixel.Tone, { it?.let(setFirst) }, presets = PlanDays.presets(today), pickLabel = "Pick a day")
    FieldLabel("Ends")
    PixelDateField(last, today, Pixel.Tone, { it?.let(setLast) }, presets = emptyList(), min = first)
}

@Composable
internal fun CompanionPicker(party: List<app.pokeplan.core.pokemon.OwnedPokemon>, chosen: String?, onChange: (String?) -> Unit) {
    if (party.isEmpty()) return
    PixelDropdown(
        listOf<String?>(null) + party.map { it.id }, chosen?.takeIf { id -> party.any { it.id == id } },
        { id -> party.firstOrNull { it.id == id }?.nickname ?: "None" }, onChange, Modifier.fillMaxWidth(), label = "Companion",
    )
}
