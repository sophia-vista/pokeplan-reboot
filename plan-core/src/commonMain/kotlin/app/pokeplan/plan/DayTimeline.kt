package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The day view: tasks with a time on that day are blocks on the hours, and the day's tasks without one wait in a tray
 * to be given an hour. Tapping a tray task and then an hour plans it (dragging comes later).
 */
object DayTimeline {
    /** A task on the hours, from [startMinute] to [endMinute] after midnight. Overlapping blocks sit side by side: this one is [lane] of [lanes]. */
    data class Block(val task: Task, val startMinute: Int, val endMinute: Int, val lane: Int, val lanes: Int)

    /** The hours shown are [firstHour] up to (not including) [endHour]. [inbox] holds the tasks with no day at all. */
    data class Plan(val day: LocalDate, val blocks: List<Block>, val tray: List<Task>, val inbox: List<Task>, val firstHour: Int, val endHour: Int)

    private const val DAY_END = 24 * 60

    /** How long a block lasts: its estimate, or [DayLoad.DEFAULT_ESTIMATE_MINUTES]; it stops at midnight. */
    fun minutes(t: Task): Int = t.estimateMinutes ?: DayLoad.DEFAULT_ESTIMATE_MINUTES

    /**
     * The plan for [day]. Blocks are the open and finished tasks planned that day at a time; the tray is the open ones
     * planned or due that day without a time (and, on today, everything overdue). The hours cover the waking window
     * ([wakeStart] to [wakeEnd]) and stretch to fit any block outside it.
     */
    fun build(
        tasks: List<Task>,
        day: LocalDate,
        now: LocalDateTime,
        wakeStart: LocalTime = LocalTime(8, 0),
        wakeEnd: LocalTime = LocalTime(22, 0),
    ): Plan {
        val live = tasks.filter { it.state == TaskState.OPEN || it.state == TaskState.DONE }
        val timed = live.filter { it.planned == day && it.plannedTime != null }
        val blocks = lanes(timed)
        val tray = if (day == now.date) TaskViews.today(tasks, now).filter { it !in timed }
        else live.filter { it.isOpen && it.plannedTime == null && (it.planned == day || (it.planned == null && it.due?.date == day)) }
        val first = minOf(wakeStart.hour, blocks.minOfOrNull { it.startMinute / 60 } ?: 24)
        val end = maxOf(wakeEnd.hour + if (wakeEnd.minute > 0) 1 else 0, blocks.maxOfOrNull { (it.endMinute + 59) / 60 } ?: 0)
        return Plan(day, blocks, tray, TaskViews.inbox(tasks), first, end.coerceAtMost(24))
    }

    /** Places the blocks: each group of overlapping ones shares the width, each in the first lane that is free. */
    private fun lanes(timed: List<Task>): List<Block> {
        val sorted = timed.map { t ->
            val start = t.plannedTime!!.let { it.hour * 60 + it.minute }
            Triple(t, start, (start + minutes(t)).coerceAtMost(DAY_END))
        }.sortedWith(compareBy({ it.second }, { -it.third }))
        val out = mutableListOf<Block>()
        var group = mutableListOf<Block>()
        var groupEnd = -1
        val laneEnds = mutableListOf<Int>()
        fun close() {
            val count = laneEnds.size
            out += group.map { it.copy(lanes = count) }
            group = mutableListOf()
            laneEnds.clear()
        }
        for ((t, start, end) in sorted) {
            if (start >= groupEnd) close()
            val free = laneEnds.indexOfFirst { it <= start }
            val lane = if (free >= 0) free else laneEnds.size.also { laneEnds += 0 }
            laneEnds[lane] = end
            group += Block(t, start, end, lane, 0)
            groupEnd = if (group.size == 1) end else maxOf(groupEnd, end)
        }
        close()
        return out
    }
}
