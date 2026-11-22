package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

object TaskViews {
    val EVENING_START = LocalTime(18, 0)

    private fun open(tasks: List<Task>) = tasks.filter { it.isOpen }

    fun undated(tasks: List<Task>): List<Task> = open(tasks).filter { it.planned == null && it.due == null }

    fun inbox(tasks: List<Task>): List<Task> = open(tasks).filter { it.listId == null && it.planned == null && it.due == null }

    fun inList(tasks: List<Task>, listId: String): List<Task> = open(tasks).filter { it.listId == listId }

    data class OutstandingGroup(val listId: String?, val tasks: List<Task>)

    fun outstanding(tasks: List<Task>, listOrder: List<String>, filter: TaskFilter = TaskFilter()): List<OutstandingGroup> {
        val found = filter.copy(status = TaskFilter.Status.OPEN).apply(tasks)
        val known = listOrder.toSet()
        val byList = found.groupBy { t -> t.listId?.takeIf { it in known } }
        return (listOf<String?>(null) + listOrder).mapNotNull { id -> byList[id]?.let { OutstandingGroup(id, it) } }
    }

    enum class When(val label: String) { BEFORE("From before"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("Next 7 days"), LATER("Later"), NO_DATE("No date") }

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
    fun overdue(tasks: List<Task>, now: LocalDateTime): List<Task> = open(tasks).filter { isOverdue(it, now) }

    private fun isOverdue(t: Task, now: LocalDateTime): Boolean {
        val due = t.due
        return if (due != null) due < now else t.planned != null && t.planned < now.date
    }

    fun today(tasks: List<Task>, now: LocalDateTime): List<Task> {
        val day = now.date
        val overdue = overdue(tasks, now).toSet()
        return open(tasks).filter { it in overdue || it.planned == day || it.due?.date == day || (it.nudge != null && it.nudge <= now) }.sortedWith(order(now))
    }

    private fun timeOf(t: Task): LocalTime? = t.plannedTime ?: t.due?.time

    fun isEvening(t: Task): Boolean = t.evening || (timeOf(t)?.let { it >= EVENING_START } ?: false)

    fun todaySplit(tasks: List<Task>, now: LocalDateTime): Pair<List<Task>, List<Task>> =
        today(tasks, now).partition { !isEvening(it) }

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
    private fun order(now: LocalDateTime): Comparator<Task> =
        // No list allocation per comparison: a sort calls this O(n log n) times and the old lambda built a list every time.
        compareBy<Task>({ if (isOverdue(it, now)) 0 else 1 }, { it.planned ?: it.due?.date ?: now.date }, { timeOf(it) ?: LocalTime(23, 59) }, { -it.rank.tier })
}
