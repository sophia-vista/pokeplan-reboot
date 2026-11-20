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

    /** The Inbox (PLAN.md section 3.2): open tasks in no list and with no date; a date or a list takes a task out of it. */
    fun inbox(tasks: List<Task>): List<Task> = open(tasks).filter { it.listId == null && it.planned == null && it.due == null }

    /** Open tasks filed in the list [listId]. */
    fun inList(tasks: List<Task>, listId: String): List<Task> = open(tasks).filter { it.listId == listId }

    /** One group of [outstanding]: the tasks of the list [listId], or of the Inbox (no list) when null. */
    data class OutstandingGroup(val listId: String?, val tasks: List<Task>)

    /**
     * Every open task across the lists, grouped by list: the Inbox group first (tasks in no list, or in a list that is no longer in [listOrder]),
     * then the lists in [listOrder]. [filter] narrows by search, list and difficulty; its status is ignored (only open tasks).
     * Within a group the filter's order applies (by day, undated last). Empty groups are left out.
     */
    fun outstanding(tasks: List<Task>, listOrder: List<String>, filter: TaskFilter = TaskFilter()): List<OutstandingGroup> {
        val found = filter.copy(status = TaskFilter.Status.OPEN).apply(tasks)
        val known = listOrder.toSet()
        val byList = found.groupBy { t -> t.listId?.takeIf { it in known } }
        return (listOf<String?>(null) + listOrder).mapNotNull { id -> byList[id]?.let { OutstandingGroup(id, it) } }
    }

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
        // A nudge (a reminder with no date) shows from its time and never counts as overdue.
        return open(tasks).filter { it in overdue || it.planned == day || it.due?.date == day || (it.nudge != null && it.nudge <= now) }.sortedWith(order(now))
    }

    private fun timeOf(t: Task): LocalTime? = t.plannedTime ?: t.due?.time

    fun isEvening(t: Task): Boolean = t.evening || (timeOf(t)?.let { it >= EVENING_START } ?: false)

    /** Today's tasks, split into the main list and "This evening". */
    fun todaySplit(tasks: List<Task>, now: LocalDateTime): Pair<List<Task>, List<Task>> =
        today(tasks, now).partition { !isEvening(it) }

    /** Open tasks planned or due after today (within [days] days when given; Upcoming has no cap), soonest first. */
    fun upcoming(tasks: List<Task>, now: LocalDateTime, days: Int? = null): List<Task> {
        val start = now.date
        val end = days?.let { start.plus(DatePeriod(days = it)) } ?: LocalDate(9999, 12, 31)
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
