package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil

/**
 * The edits PokéPlan's own calendar allows, on the list of its [PlanEvent]s. An id may name an occurrence of a repeating
 * event ([PlanEvent.occurrenceId]); each edit then says what it does to the series.
 */
object PlanCalendar {
    /** The events after an edit, and the id the edited event (or occurrence) is shown with afterwards. */
    data class Edit(val events: List<PlanEvent>, val shownId: String)

    /** A new timed event, its length kept inside the day. */
    fun timed(id: String, title: String, day: LocalDate, start: LocalTime, minutes: Int, color: Long?, repeat: EventRepeat?): PlanEvent =
        PlanEvent(id, title.trim(), day, start, PlanEvent.fit(start.hour * 60 + start.minute, minutes), color = color, repeat = repeat)

    /** A new all-day event from [first] through [last]. */
    fun allDay(id: String, title: String, first: LocalDate, last: LocalDate, color: Long?, repeat: EventRepeat?): PlanEvent =
        PlanEvent(id, title.trim(), first, LocalTime(0, 0), allDay = true, lastDay = maxOf(first, last).takeIf { it > first }, color = color, repeat = repeat)

    /**
     * Changes an event's title, day, start, length, last day (all-day), colour, or whether it is all-day; the length is kept
     * inside the day. A blank title keeps the old one. Moving an all-day event's first day moves its last day with it.
     *
     * For an occurrence the change is made to the whole series, and moving the occurrence's [day] moves every one (with
     * the series' end and skipped days) by as many days.
     */
    fun update(
        events: List<PlanEvent>, id: String, title: String? = null, day: LocalDate? = null, start: LocalTime? = null,
        minutes: Int? = null, lastDay: LocalDate? = null, color: Long? = null, allDay: Boolean? = null,
    ): Edit {
        val seriesId = PlanEvent.seriesOf(id)
        val at = PlanEvent.occurrenceDate(id)
        var shown = id
        val changed = events.map { e ->
            if (e.id != seriesId) return@map e
            val from = at ?: e.day
            val to = day ?: from
            val shift = from.daysUntil(to)
            val s = start ?: e.start
            val first = e.day.plusDays(shift)
            val whole = allDay ?: e.allDay
            val last = when {
                !whole -> null
                lastDay != null -> first.plusDays(to.daysUntil(lastDay))
                else -> e.lastDay?.plusDays(shift)
            }
            if (at != null) shown = PlanEvent.occurrenceId(seriesId, to)
            e.copy(
                title = title?.trim()?.ifEmpty { null } ?: e.title,
                day = first,
                start = s,
                minutes = PlanEvent.fit(s.hour * 60 + s.minute, minutes ?: e.minutes),
                allDay = whole,
                lastDay = last?.takeIf { it > first },
                color = color ?: e.color,
                until = e.until?.plusDays(shift),
                skipped = e.skipped.map { it.plusDays(shift) }.toSet(),
                // A moved event keeps what it has paid: dragging a paid event to a later day must not pay it again.
                paid = e.paid.map { it.plusDays(shift) }.toSet(),
            )
        }
        return Edit(changed, shown)
    }

    /**
     * Moves an event to [day] at [start], as dragging it does. An occurrence of a repeating event moves on its own: it
     * is left out of the series and becomes a one-off event there, with [newId].
     */
    fun move(events: List<PlanEvent>, id: String, day: LocalDate, start: LocalTime, newId: () -> String): Edit {
        val at = PlanEvent.occurrenceDate(id) ?: return update(events, id, day = day, start = start)
        val series = events.firstOrNull { it.id == PlanEvent.seriesOf(id) } ?: return Edit(events, id)
        val single = PlanEvent(
            newId(), series.title, day, if (series.allDay) series.start else start, series.minutes, series.allDay,
            series.lastDay?.let { day.plusDays(series.spanDays) }, series.color,
        ).let { it.copy(minutes = PlanEvent.fit(it.startMinute, it.minutes)) }
        return Edit(events.map { if (it.id == series.id) it.copy(skipped = it.skipped + at) else it } + single, single.id)
    }

    /** Deletes an event; for an occurrence of a repeating event, every one of them. */
    fun delete(events: List<PlanEvent>, id: String): List<PlanEvent> = events.filterNot { it.id == PlanEvent.seriesOf(id) }

    /** Deletes just this occurrence of a repeating event (any other event is deleted), and the series once none is left. */
    fun deleteOccurrence(events: List<PlanEvent>, id: String): List<PlanEvent> {
        val at = PlanEvent.occurrenceDate(id) ?: return delete(events, id)
        return events.mapNotNull { e ->
            if (e.id != PlanEvent.seriesOf(id)) e
            else e.copy(skipped = e.skipped + at).takeUnless { left -> left.until?.let { left.startDates(left.day, it).isEmpty() } == true }
        }
    }

    /**
     * Sets how an event repeats (null: it happens once), from the one shown as [id] on. When [id] is a later occurrence
     * of a repeating event, the ones before it stay as they were: the series ends the day before and a new one (with
     * [newId]) starts there, so only it and the ones after follow the new rule (a one-off for null).
     */
    fun repeat(events: List<PlanEvent>, id: String, repeat: EventRepeat?, newId: () -> String): Edit {
        val seriesId = PlanEvent.seriesOf(id)
        val series = events.firstOrNull { it.id == seriesId } ?: return Edit(events, id)
        if (repeat == series.repeat) return Edit(events, id)
        val at = PlanEvent.occurrenceDate(id) ?: series.day
        val shownId = { e: PlanEvent -> if (repeat == null) e.id else PlanEvent.occurrenceId(e.id, at) }
        if (at == series.day) {
            val changed = series.copy(repeat = repeat, until = series.until.takeIf { repeat != null }, skipped = series.skipped.takeIf { repeat != null }.orEmpty())
            return Edit(events.map { if (it.id == seriesId) changed else it }, shownId(changed))
        }
        val next = series.copy(
            id = newId(), day = at, lastDay = series.lastDay?.let { at.plusDays(series.spanDays) }, repeat = repeat,
            until = series.until.takeIf { repeat != null }, skipped = series.skipped.filter { repeat != null && it > at }.toSet(),
        )
        val before = series.copy(until = at.plusDays(-1)).takeIf { it.startDates(it.day, at.plusDays(-1)).isNotEmpty() }
        return Edit(events.flatMap { if (it.id == seriesId) listOfNotNull(before, next) else listOf(it) }, shownId(next))
    }

    /** Sets the last day a repeating event can happen on (null: it never ends); never before its first day. */
    fun endOn(events: List<PlanEvent>, id: String, until: LocalDate?): List<PlanEvent> =
        events.map { if (it.id == PlanEvent.seriesOf(id)) it.copy(until = until?.let { u -> maxOf(u, it.day) }) else it }
}
