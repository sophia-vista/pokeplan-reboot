package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The day view: tasks with a time on that day are blocks on the hours, and the day's tasks without one wait in a tray
 * to be given an hour. Tapping a tray task and then an hour plans it (dragging comes later). Events from the phone's
 * calendars sit on the same hours, read-only; all-day ones sit above them.
 */
object DayTimeline {
    /** A task on the hours, from [startMinute] to [endMinute] after midnight. Overlapping blocks sit side by side: this one is [lane] of [lanes]. */
    data class Block(val task: Task, val startMinute: Int, val endMinute: Int, val lane: Int, val lanes: Int)

    /** A calendar event on the hours, placed in the same lanes as the task blocks. */
    data class EventBlock(val event: CalendarEvent, val startMinute: Int, val endMinute: Int, val lane: Int, val lanes: Int)

    /**
     * The hours shown are [firstHour] up to (not including) [endHour]. [inbox] holds the tasks with no day at all.
     * [busyMinutes] is how much of the waking window the timed events take (overlaps counted once).
     */
    data class Plan(
        val day: LocalDate,
        val blocks: List<Block>,
        val tray: List<Task>,
        val inbox: List<Task>,
        val firstHour: Int,
        val endHour: Int,
        val events: List<EventBlock> = emptyList(),
        val allDay: List<CalendarEvent> = emptyList(),
        val busyMinutes: Int = 0,
    )

    private const val DAY_END = 24 * 60

    /** How long a block lasts: its estimate, or [DayLoad.DEFAULT_ESTIMATE_MINUTES]; it stops at midnight. */
    fun minutes(t: Task): Int = t.estimateMinutes ?: DayLoad.DEFAULT_ESTIMATE_MINUTES

    /**
     * The plan for [day]. Blocks are the open and finished tasks planned that day at a time; the tray is the open ones
     * planned or due that day without a time (and, on today, everything overdue). The hours cover the waking window
     * ([wakeStart] to [wakeEnd]) and stretch to fit any block outside it. [events] are the calendar events to show; the
     * ones not on [day] are left out.
     */
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
            day, blocks, tray, TaskViews.inbox(tasks), first, end.coerceAtMost(24),
            events = eventBlocks,
            allDay = events.filter { it.allDay && it.isOn(day) }.sortedBy { it.title },
            busyMinutes = eventBlocks.flatMap { (it.startMinute until it.endMinute).filter { m -> m in wake } }.toSet().size,
        )
    }

    /** A task or an event on the hours, from [start] to [end] minutes after midnight. */
    private class Span(val task: Task?, val event: CalendarEvent?, val start: Int, val end: Int)

    /**
     * Places the spans: each group of overlapping ones shares the width, each in the first lane that is free. Returns each
     * span with its lane and the number of lanes in its group.
     */
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
