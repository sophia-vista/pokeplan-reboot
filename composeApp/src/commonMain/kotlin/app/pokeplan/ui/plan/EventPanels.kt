package app.pokeplan.ui.plan

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
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

// Help: events never pay; with a repeating event, changes are made to every one, and moving it on the calendar moves just that one.
/** Setting up a new event: its title, all day or at a time, its colour, then when (its days, or tap an hour and nudge it), then Add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewEventPanel(draft: Draft, change: (Draft) -> Unit, add: () -> Unit, today: LocalDate = draft.day, cancel: () -> Unit) {
    val at = LocalTime(draft.start / 60, draft.start % 60)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("New event")
            PixelField(
                draft.title, { change(draft.copy(title = it)) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "What",
            )
            Segmented(listOf("At a time", "All day"), if (draft.allDay) 1 else 0) { change(draft.copy(allDay = it == 1, lastDay = draft.day)) }
            EventColors(draft.color) { change(draft.copy(color = it)) }
            RepeatPicker(draft.repeat) { change(draft.copy(repeat = it)) }
            if (draft.allDay) {
                DaySpanPicker(draft.day, draft.lastDay, today, { change(draft.movedTo(it)) }) { change(draft.copy(lastDay = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("plus", "Add", add, Pixel.Tone, enabled = draft.title.isNotBlank())
                    PixelIconButton("close", "Cancel", cancel, Pixel.InkSoft)
                }
                return@Column
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelIconButton("plus", "Add", add, Pixel.Tone, enabled = draft.title.isNotBlank())
                PixelIconButton("close", "Cancel", cancel, Pixel.InkSoft)
            }
        }
    }
}

/**
 * What to do with a picked event of PokéPlan's own: rename it, nudge it or its length, change its colour or how it
 * repeats, or delete it. For an occurrence of a repeating event the changes are made to every one (moving it on the
 * calendar moves just that one), and Delete asks this one or all. [follow] gets the id the event has after a change
 * that gives it a new one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventPanel(model: AppModel, event: PlanEvent, follow: (String) -> Unit, close: () -> Unit) {
    val store = model.store
    var title by remember(PlanEvent.seriesOf(event.id)) { mutableStateOf(event.title) }
    val series = model.data.events.firstOrNull { it.id == PlanEvent.seriesOf(event.id) } ?: event
    val repeats = event.repeat != null
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Event",
            )
            EventColors(event.color) { store.updateEvent(event.id, color = it) }
            RepeatPicker(event.repeat) { store.updateEvent(event.id, title = title); follow(store.repeatEvent(event.id, it)) }
            if (repeats) RepeatEnd(series, event) { store.endEventOn(event.id, it) }
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
        }
    }
}

/** How an event repeats: Once or one of [EventRepeat], as a row of choices (the chosen one bold with a thicker border). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(selected: EventRepeat?, pick: (EventRepeat?) -> Unit) {
    val shape = PixelShape
    Column {
        FieldLabel("Repeats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            (listOf<EventRepeat?>(null) + EventRepeat.entries).forEach { r ->
                val on = r == selected
                Box(
                    Modifier
                        .defaultMinSize(minHeight = Spacing.Target)
                        .clip(shape)
                        .background(if (on) Pixel.Tone.copy(alpha = 0.10f) else Pixel.Panel, shape)
                        .border(if (on) 2.dp else 1.dp, if (on) Pixel.Tone else Pixel.Border, shape)
                        .selectable(on, role = Role.RadioButton) { pick(r) }
                        .padding(horizontal = Spacing.M),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        r?.label ?: "Once",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) Pixel.Ink else Pixel.InkSoft,
                    )
                }
            }
        }
    }
}

/** How many occurrences Set an end keeps, counting the one shown. */
private const val END_AFTER = 10

/**
 * When a repeating event ends: never, or on one of its occurrences, stepped with ‹ › (never before the one [shown]).
 * Set an end picks the tenth from the one shown.
 */
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

/** The colour names, for screen readers, in the order of [TaskList.COLORS]. */
private val eventColorNames = listOf("Red", "Blue", "Green", "Amber", "Teal", "Purple", "Pink", "Slate")

/** An event's colour: the eight list colours as swatches (teal is the own events' usual colour). */
@Composable
private fun EventColors(selected: Long?, pick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskList.COLORS.forEachIndexed { i, c ->
            val on = c == (selected ?: TaskList.COLORS[4])
            Box(
                Modifier.size(36.dp).clip(DialogueShape).selectable(on, role = Role.RadioButton) { pick(c) }
                    .semantics { contentDescription = eventColorNames[i] },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).background(Color(c), DialogueShape).border(if (on) 3.dp else 0.dp, if (on) Pixel.Ink else Color.Transparent, DialogueShape))
            }
        }
    }
}

/** The first and last day of an all-day event, each typed or picked; the last never comes before the first. */
@Composable
private fun DaySpanPicker(first: LocalDate, last: LocalDate, today: LocalDate, setFirst: (LocalDate) -> Unit, setLast: (LocalDate) -> Unit) {
    FieldLabel("Starts")
    PixelDateField(first, today, Pixel.Tone, { it?.let(setFirst) }, presets = PlanDays.presets(today), pickLabel = "Pick a day")
    FieldLabel("Ends")
    PixelDateField(last, today, Pixel.Tone, { it?.let(setLast) }, presets = emptyList(), min = first)
}
