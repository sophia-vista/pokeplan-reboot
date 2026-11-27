package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * The light review of what was left behind (owner list of 27 November 2026, P2): one task at a time, one tap each, no guilt.
 * A leftover is an open task planned for an earlier day or past its deadline (the Today list's "From before"). Tasks another
 * module owns (a chore, a follow-up) are not offered: their own module decides when they are done or skipped.
 */
object OutstandingReview {
    enum class Choice(val label: String) {
        TODAY("Today"), TOMORROW("Tomorrow"), NEXT_WEEK("Next week"), NO_DATE("No date"), WONT_DO("Won't do"),
    }

    private val END_OF_DAY = LocalTime(23, 59)

    /** Oldest first, so the review starts with what has waited longest. */
    fun leftovers(tasks: List<Task>, now: LocalDateTime): List<Task> =
        TaskViews.overdue(tasks, now).filter { !it.ownedElsewhere }
            .sortedWith(compareBy<Task>({ it.planned ?: it.due?.date ?: now.date }, { -it.rank.tier }, { it.createdAtMillis }))

    /** The first day of next week: the first day of the player's week that is after today. */
    fun nextWeek(today: LocalDate, weekStart: DayOfWeek): LocalDate {
        val sinceStart = (today.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
        return today.plus(DatePeriod(days = 7 - sinceStart))
    }

    fun dayOf(choice: Choice, today: LocalDate, weekStart: DayOfWeek): LocalDate? = when (choice) {
        Choice.TODAY -> today
        Choice.TOMORROW -> today.plus(DatePeriod(days = 1))
        Choice.NEXT_WEEK -> nextWeek(today, weekStart)
        Choice.NO_DATE, Choice.WONT_DO -> null
    }

    /**
     * The task as it stands after [choice], or null for [Choice.WONT_DO] (the store records that, so a task another module owns
     * and the Undo stay in one place). A task's deadline goes with it: a past deadline would leave the task "from before" however
     * far it was moved. It keeps its time of day, or the end of the day when that moment has already passed.
     * *No date* clears the planned day and the deadline.
     */
    fun reschedule(task: Task, choice: Choice, now: LocalDateTime, weekStart: DayOfWeek): Task? {
        if (choice == Choice.WONT_DO) return null
        val day = dayOf(choice, now.date, weekStart)
            ?: return task.copy(planned = null, plannedTime = null, due = null, evening = false)
        val due = task.due?.let { d ->
            val moved = LocalDateTime(day, d.time)
            if (moved < now) LocalDateTime(day, END_OF_DAY) else moved
        }
        return task.copy(planned = day, plannedTime = task.plannedTime, due = due)
    }
}
