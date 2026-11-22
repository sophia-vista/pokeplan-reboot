package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

object DayTimeline {
    data class Block(val task: Task, val startMinute: Int, val endMinute: Int, val lane: Int, val lanes: Int)

    data class EventBlock(val event: CalendarEvent, val startMinute: Int, val endMinute: Int, val lane: Int, val lanes: Int)

    data class Plan(
        val day: LocalDate,
        val blocks: List<Block>,
        val tray: List<Task>,
        val undated: List<Task>,
        val firstHour: Int,
        val endHour: Int,
        val events: List<EventBlock> = emptyList(),
        val allDay: List<CalendarEvent> = emptyList(),
        val busyMinutes: Int = 0,
    )

    data class Week(val days: List<Plan>, val tray: List<Task>, val undated: List<Task>, val firstHour: Int, val endHour: Int)

    private const val DAY_END = 24 * 60

    const val SNAP_MINUTES = 15

    const val LAST_START = DAY_END - SNAP_MINUTES

    fun snap(minute: Int): Int = ((minute + SNAP_MINUTES / 2).floorDiv(SNAP_MINUTES) * SNAP_MINUTES).coerceIn(0, LAST_START)

    fun resized(startMinute: Int, endMinute: Int): Int {
        val end = (endMinute + SNAP_MINUTES / 2).floorDiv(SNAP_MINUTES) * SNAP_MINUTES
        return (end - startMinute).coerceIn(SNAP_MINUTES, (DAY_END - startMinute).coerceAtLeast(SNAP_MINUTES))
    }

    const val DEFAULT_ESTIMATE_MINUTES = 30

    fun minutes(t: Task): Int = t.estimateMinutes ?: DEFAULT_ESTIMATE_MINUTES

    fun build(
        tasks: List<Task>,
        day: LocalDate,
        now: LocalDateTime,
        wakeStart: LocalTime = LocalTime(8, 0),
        wakeEnd: LocalTime = LocalTime(22, 0),
        events: List<CalendarEvent> = emptyList(),
    ): Plan {
        val live = tasks.filter { it.state == TaskState.OPEN || it.state == TaskState.DONE }
        val timed = live.filter { it.planned == day && it.plannedTime != null }
        val spans = timed.map { t ->
            val start = t.plannedTime!!.let { it.hour * 60 + it.minute }
            Span(t, null, start, (start + minutes(t)).coerceAtMost(DAY_END))
        } + events.mapNotNull { e -> e.span(day)?.let { Span(null, e, it.first, it.last + 1) } }
        val placed = lanes(spans)
        val blocks = placed.mapNotNull { (s, lane, lanes) -> s.task?.let { Block(it, s.start, s.end, lane, lanes) } }
        val eventBlocks = placed.mapNotNull { (s, lane, lanes) -> s.event?.let { EventBlock(it, s.start, s.end, lane, lanes) } }
        val tray = if (day == now.date) TaskViews.today(tasks, now).filter { it !in timed }
        else live.filter { it.isOpen && it.plannedTime == null && (it.planned == day || (it.planned == null && it.due?.date == day)) }
        val first = minOf(wakeStart.hour, spans.minOfOrNull { it.start / 60 } ?: 24)
        val end = maxOf(wakeEnd.hour + if (wakeEnd.minute > 0) 1 else 0, spans.maxOfOrNull { (it.end + 59) / 60 } ?: 0)
        val wake = (wakeStart.hour * 60 + wakeStart.minute) until (wakeEnd.hour * 60 + wakeEnd.minute)
        return Plan(
            day, blocks, tray, TaskViews.undated(tasks), first, end.coerceAtMost(24),
            events = eventBlocks,
            allDay = events.filter { it.allDay && it.isOn(day) }.sortedBy { it.title },
            busyMinutes = eventBlocks.flatMap { (it.startMinute until it.endMinute).filter { m -> m in wake } }.toSet().size,
        )
    }

    fun week(
        tasks: List<Task>,
        from: LocalDate,
        now: LocalDateTime,
        days: Int = 7,
        wakeStart: LocalTime = LocalTime(8, 0),
        wakeEnd: LocalTime = LocalTime(22, 0),
        events: List<CalendarEvent> = emptyList(),
    ): Week {
        val plans = (0 until days).map { build(tasks, from.plus(DatePeriod(days = it)), now, wakeStart, wakeEnd, events) }
        return Week(
            plans, plans.flatMap { it.tray }.distinctBy { it.id }, TaskViews.undated(tasks),
            plans.minOf { it.firstHour }, plans.maxOf { it.endHour },
        )
    }

    private class Span(val task: Task?, val event: CalendarEvent?, val start: Int, val end: Int)

    private fun lanes(spans: List<Span>): List<Triple<Span, Int, Int>> {
        val sorted = spans.sortedWith(compareBy({ it.start }, { -it.end }))
        val out = mutableListOf<Triple<Span, Int, Int>>()
        var group = mutableListOf<Pair<Span, Int>>()
        var groupEnd = -1
        val laneEnds = mutableListOf<Int>()
        fun close() {
            val count = laneEnds.size
            out += group.map { (s, lane) -> Triple(s, lane, count) }
            group = mutableListOf()
            laneEnds.clear()
        }
        for (s in sorted) {
            if (s.start >= groupEnd) close()
            val free = laneEnds.indexOfFirst { it <= s.start }
            val lane = if (free >= 0) free else laneEnds.size.also { laneEnds += 0 }
            laneEnds[lane] = s.end
            group += s to lane
            groupEnd = if (group.size == 1) s.end else maxOf(groupEnd, s.end)
        }
        close()
        return out
    }
}
