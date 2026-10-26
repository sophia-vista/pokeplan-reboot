package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** The lists the planner shows. All take wall-clock `today` / `now` so they work in any time zone. */
object TaskViews {
    val EVENING_START = LocalTime(18, 0)

    private fun open(tasks: List<Task>) = tasks.filter { it.isOpen }

    /** Open tasks with neither a planned date nor a due date (the Calendar's "No date" chips). */
    fun undated(tasks: List<Task>): List<Task> = open(tasks).filter { it.planned == null && it.due == null }

    /** The Inbox, as in TickTick: open tasks not filed in a list, whatever their date. */
    fun inbox(tasks: List<Task>): List<Task> = open(tasks).filter { it.listId == null }

    /** Open tasks filed in the list [listId]. */
    fun inList(tasks: List<Task>, listId: String): List<Task> = open(tasks).filter { it.listId == listId }

    /** Where a task falls in a list's view, soonest first. */
    enum class When(val label: String) { BEFORE("From before"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("Next 7 days"), LATER("Later"), NO_DATE("No date") }

    /**
     * A list's tasks grouped by when they are planned or due, as TickTick groups a list: from before, today, tomorrow,
     * the next 7 days, later, then those with no date. Empty groups are left out.
     */
    fun byWhen(tasks: List<Task>, now: LocalDateTime): List<Pair<When, List<Task>>> {
        val today = now.date
        val late = overdue(tasks, now).toSet()
        fun group(t: Task): When {
            val day = t.planned ?: t.due?.date ?: return When.NO_DATE
            return when {
                t in late -> When.BEFORE
                day <= today -> When.TODAY
                day == today.plus(DatePeriod(days = 1)) -> When.TOMORROW
                day <= today.plus(DatePeriod(days = 7)) -> When.WEEK
                else -> When.LATER
            }
        }
        val dated = compareBy<Task>({ it.planned ?: it.due?.date }, { timeOf(it) ?: LocalTime(23, 59) }, { -it.rank.tier })
        return open(tasks).groupBy(::group).entries.sortedBy { it.key.ordinal }.map { (w, ts) ->
            w to if (w == When.NO_DATE) ts.sortedByDescending { it.createdAtMillis } else ts.sortedWith(dated)
        }
    }

    /** Past due, or planned for an earlier day and not finished. */
    fun overdue(tasks: List<Task>, now: LocalDateTime): List<Task> = open(tasks).filter {
        val due = it.due
        if (due != null) due < now else it.planned != null && it.planned < now.date
    }

    /** Planned today, due today, or overdue. */
    fun today(tasks: List<Task>, now: LocalDateTime): List<Task> {
        val day = now.date
        val overdue = overdue(tasks, now).toSet()
        return open(tasks).filter { it in overdue || it.planned == day || it.due?.date == day }.sortedWith(order(now))
    }

    private fun timeOf(t: Task): LocalTime? = t.plannedTime ?: t.due?.time

    fun isEvening(t: Task): Boolean = t.evening || (timeOf(t)?.let { it >= EVENING_START } ?: false)

    /** Today's tasks, split into the main list and "This evening". */
    fun todaySplit(tasks: List<Task>, now: LocalDateTime): Pair<List<Task>, List<Task>> =
        today(tasks, now).partition { !isEvening(it) }

    /** Open tasks planned or due in the next [days] days (not today), soonest first. */
    fun upcoming(tasks: List<Task>, now: LocalDateTime, days: Int = 7): List<Task> {
        val start = now.date
        val end = start.plus(DatePeriod(days = days))
        val todays = today(tasks, now).toSet()
        return open(tasks).filter { t ->
            val day = t.planned ?: t.due?.date
            t !in todays && day != null && day > start && day <= end
        }.sortedWith(order(now))
    }

    /** Overdue first, then by time of day (untimed last), then harder ranks first. */
    private fun order(now: LocalDateTime): Comparator<Task> {
        val overdue = { t: Task -> if (overdue(listOf(t), now).isNotEmpty()) 0 else 1 }
        return compareBy<Task>({ overdue(it) }, { it.planned ?: it.due?.date ?: now.date }, { timeOf(it) ?: LocalTime(23, 59) }, { -it.rank.tier })
    }
}

/** How full a day is: planned minutes against free time. */
data class DayLoad(val plannedMinutes: Int, val freeMinutes: Int) {
    enum class Level { CALM, FULL, OVER }

    val ratio: Double get() = if (freeMinutes <= 0) Double.POSITIVE_INFINITY else plannedMinutes.toDouble() / freeMinutes

    val level: Level get() = when {
        ratio <= 0.8 -> Level.CALM
        ratio <= 1.0 -> Level.FULL
        else -> Level.OVER
    }

    companion object {
        /** Tasks without an estimate count as this many minutes. *(tune)* */
        const val DEFAULT_ESTIMATE_MINUTES = 30

        /**
         * [tasks] are the tasks planned for the day; [busyMinutes] is time taken by imported calendar events.
         * Free time is the waking window ([wakeStart] to [wakeEnd]) minus the busy minutes.
         */
        fun compute(
            tasks: List<Task>,
            busyMinutes: Int = 0,
            wakeStart: LocalTime = LocalTime(8, 0),
            wakeEnd: LocalTime = LocalTime(22, 0),
        ): DayLoad {
            val awake = (wakeEnd.hour * 60 + wakeEnd.minute) - (wakeStart.hour * 60 + wakeStart.minute)
            val planned = tasks.filter { it.isOpen }.sumOf { it.estimateMinutes ?: DEFAULT_ESTIMATE_MINUTES }
            return DayLoad(planned, (awake - busyMinutes).coerceAtLeast(0))
        }
    }
}
