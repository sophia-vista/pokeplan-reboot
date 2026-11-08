package app.pokeplan.ui.screens

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

/** Setting up a new event: its title, all day or at a time, its colour, then when (its days, or tap an hour and nudge it), then Add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewEventPanel(draft: Draft, week: Boolean, change: (Draft) -> Unit, add: () -> Unit, hours: Boolean = true, cancel: () -> Unit) {
    val at = LocalTime(draft.start / 60, draft.start % 60)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("New event")
            PixelField(
                draft.title, { change(draft.copy(title = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = "What",
            )
            Segmented(listOf("At a time", "All day"), if (draft.allDay) 1 else 0) { change(draft.copy(allDay = it == 1, lastDay = draft.day)) }
            EventColors(draft.color) { change(draft.copy(color = it)) }
            RepeatPicker(draft.repeat) { change(draft.copy(repeat = it)) }
            if (draft.allDay) {
                DaySpanPicker(draft.day, draft.lastDay, { change(draft.movedTo(it)) }) { change(draft.copy(lastDay = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("plus", "Add", add, Pixel.Green, enabled = draft.title.isNotBlank())
                    PixelIconButton("close", "Cancel", cancel, Pixel.InkSoft)
                }
                return@Column
            }
            Text(
                "${draft.day.short()}, ${clock(at)} for ${duration(draft.minutes)}." +
                    (if (hours) " Tap ${if (week) "a day's hour" else "an hour"} to change it." else "") + " Events never pay.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("-15", { change(draft.copy(start = shifted(at, -15).let { it.hour * 60 + it.minute }).let { it.copy(minutes = PlanEvent.fit(it.start, it.minutes)) }) }, color = Pixel.InkSoft, compact = true)
                PixelButton("+15", { change(draft.copy(start = shifted(at, 15).let { it.hour * 60 + it.minute }).let { it.copy(minutes = PlanEvent.fit(it.start, it.minutes)) }) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Shorter", { change(draft.copy(minutes = PlanEvent.fit(draft.start, draft.minutes - DayTimeline.SNAP_MINUTES))) }, color = Pixel.InkSoft, enabled = draft.minutes > DayTimeline.SNAP_MINUTES, compact = true)
                PixelButton("Longer", { change(draft.copy(minutes = PlanEvent.fit(draft.start, draft.minutes + DayTimeline.SNAP_MINUTES))) }, color = Pixel.InkSoft, compact = true)
                PixelIconButton("plus", "Add", add, Pixel.Green, enabled = draft.title.isNotBlank())
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
internal fun EventPanel(model: AppModel, event: PlanEvent, week: Boolean, follow: (String) -> Unit, close: () -> Unit) {
    val store = model.store
    var title by remember(PlanEvent.seriesOf(event.id)) { mutableStateOf(event.title) }
    val series = model.data.events.firstOrNull { it.id == PlanEvent.seriesOf(event.id) } ?: event
    val repeats = event.repeat != null
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = "Event",
            )
            EventColors(event.color) { store.updateEvent(event.id, color = it) }
            RepeatPicker(event.repeat) { store.updateEvent(event.id, title = title); follow(store.repeatEvent(event.id, it)) }
            if (repeats) RepeatEnd(series, event) { store.endEventOn(event.id, it) }
            val deletes: @Composable () -> Unit = {
                if (repeats) {
                    PixelButton("Delete this one", { store.deleteOccurrence(event.id); close() }, color = Pixel.Red, compact = true)
                    PixelButton("Delete all", { store.deleteEvent(event.id); close() }, color = Pixel.Red, compact = true)
                } else PixelIconButton("trash", "Delete", { store.deleteEvent(event.id); close() }, Pixel.Red)
            }
            if (event.allDay) {
                DaySpanPicker(event.day, event.endDay, { follow(store.updateEvent(event.id, day = it)) }) { store.updateEvent(event.id, lastDay = it) }
                if (repeats) Text("Changes here are made to every one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("At a time", { store.updateEvent(event.id, allDay = false); close() }, color = Pixel.InkSoft, compact = true)
                    deletes()
                    PixelIconButton("check", "Done", { store.updateEvent(event.id, title = title); close() }, Pixel.Green)
                }
                return@Column
            }
            Text(
                "${if (week) event.day.short() + ", " else "At "}${clock(event.start)} for ${duration(event.minutes)}. " +
                    if (repeats) "Changes here are made to every one; tap another hour or drag it to move just this one."
                    else "Tap another hour to move it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("-15", { store.updateEvent(event.id, start = shifted(event.start, -15)) }, color = Pixel.InkSoft, compact = true)
                PixelButton("+15", { store.updateEvent(event.id, start = shifted(event.start, 15)) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Shorter", { store.updateEvent(event.id, minutes = event.minutes - DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, enabled = event.minutes > DayTimeline.SNAP_MINUTES, compact = true)
                PixelButton("Longer", { store.updateEvent(event.id, minutes = event.minutes + DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, compact = true)
                PixelButton("All day", { store.updateEvent(event.id, allDay = true) }, color = Pixel.InkSoft, compact = true)
                deletes()
                PixelIconButton("check", "Done", { store.updateEvent(event.id, title = title); close() }, Pixel.Green)
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
                        .background(if (on) Pixel.Blue.copy(alpha = 0.10f) else Pixel.Panel, shape)
                        .border(if (on) 2.dp else 1.dp, if (on) Pixel.Blue else Pixel.Border, shape)
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

/** The first and last day of an all-day event, each with ‹ › to move it a day; the last never comes before the first. */
@Composable
private fun DaySpanPicker(first: LocalDate, last: LocalDate, setFirst: (LocalDate) -> Unit, setLast: (LocalDate) -> Unit) {
    val days = first.daysUntil(last) + 1
    Text("${first.short()}${if (days > 1) " to ${last.short()}, $days days" else ", one day"}. Events never pay.", style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Starts", Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        ArrowButton("‹", "Start a day earlier", enabled = true) { setFirst(first.minus(DatePeriod(days = 1))) }
        ArrowButton("›", "Start a day later", enabled = true) { setFirst(first.plus(DatePeriod(days = 1))) }
        Spacer(Modifier.width(Spacing.M))
        Text("Ends", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        ArrowButton("‹", "End a day earlier", enabled = last > first) { setLast(last.minus(DatePeriod(days = 1))) }
        ArrowButton("›", "End a day later", enabled = true) { setLast(last.plus(DatePeriod(days = 1))) }
    }
}
