package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil

object PlanCalendar {
    data class Edit(val events: List<PlanEvent>, val shownId: String)

    fun timed(id: String, title: String, day: LocalDate, start: LocalTime, minutes: Int, color: Long?, repeat: EventRepeat?): PlanEvent =
        PlanEvent(id, title.trim(), day, start, PlanEvent.fit(start.hour * 60 + start.minute, minutes), color = color, repeat = repeat)

    fun allDay(id: String, title: String, first: LocalDate, last: LocalDate, color: Long?, repeat: EventRepeat?): PlanEvent =
        PlanEvent(id, title.trim(), first, LocalTime(0, 0), allDay = true, lastDay = maxOf(first, last).takeIf { it > first }, color = color, repeat = repeat)

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
                paid = e.paid.map { it.plusDays(shift) }.toSet(),
            )
        }
        return Edit(changed, shown)
    }

    fun move(events: List<PlanEvent>, id: String, day: LocalDate, start: LocalTime, newId: () -> String): Edit {
        val at = PlanEvent.occurrenceDate(id) ?: return update(events, id, day = day, start = start)
        val series = events.firstOrNull { it.id == PlanEvent.seriesOf(id) } ?: return Edit(events, id)
        val single = PlanEvent(
            newId(), series.title, day, if (series.allDay) series.start else start, series.minutes, series.allDay,
            series.lastDay?.let { day.plusDays(series.spanDays) }, series.color,
        ).let { it.copy(minutes = PlanEvent.fit(it.startMinute, it.minutes)) }
        return Edit(events.map { if (it.id == series.id) it.copy(skipped = it.skipped + at) else it } + single, single.id)
    }

    fun delete(events: List<PlanEvent>, id: String): List<PlanEvent> = events.filterNot { it.id == PlanEvent.seriesOf(id) }

    fun deleteOccurrence(events: List<PlanEvent>, id: String): List<PlanEvent> {
        val at = PlanEvent.occurrenceDate(id) ?: return delete(events, id)
        return events.mapNotNull { e ->
            if (e.id != PlanEvent.seriesOf(id)) e
            else e.copy(skipped = e.skipped + at).takeUnless { left -> left.until?.let { left.startDates(left.day, it).isEmpty() } == true }
        }
    }

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

    fun endOn(events: List<PlanEvent>, id: String, until: LocalDate?): List<PlanEvent> =
        events.map { if (it.id == PlanEvent.seriesOf(id)) it.copy(until = until?.let { u -> maxOf(u, it.day) }) else it }
}
