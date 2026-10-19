package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** The Sunday ritual. Notes are private; nothing here scores the person. */
@Serializable
data class WeeklyReview(
    val weekStart: LocalDate,
    val completedAtMillis: Long,
    val prompt: String,
    /** The optional answer to the week's reflection question. */
    val note: String?,
    val carriedForwardTaskIds: List<String>,
    val rewarded: Boolean,
)

object Reviews {
    /** Completing the review earns this many berries, once a week. *(tune)* */
    const val REWARD_BERRIES = 1

    data class HabitResult(val habitId: String, val title: String, val completions: Int)

    data class Summary(
        val weekStart: LocalDate,
        val tasksDone: Int,
        val focusMinutes: Int,
        val sessionsCompleted: Int,
        val sessionsFailed: Int,
        val habits: List<HabitResult>,
        /** Open tasks planned or due in the week (or overdue) that can be carried forward. */
        val unfinished: List<Task>,
        val categorySplit: Map<Category, Int>,
    )

    fun summary(weekStart: LocalDate, tasks: List<Task>, focus: List<FocusRecord>, habits: List<Habit>, zone: TimeZone): Summary {
        val end = weekStart.plus(DatePeriod(days = 6))
        fun inWeek(millis: Long) = millis.toLocalDateTime(zone).date in weekStart..end
        val done = tasks.filter { it.state == TaskState.DONE && it.completedAtMillis != null && inWeek(it.completedAtMillis) }
        val sessions = focus.filter { inWeek(it.startedAtMillis) }
        val unfinished = tasks.filter { t ->
            t.isOpen && ((t.planned != null && t.planned <= end) || (t.due != null && t.due.date <= end))
        }
        return Summary(
            weekStart = weekStart,
            tasksDone = done.size,
            focusMinutes = sessions.filter { it.completed }.sumOf { it.minutes },
            sessionsCompleted = sessions.count { it.completed },
            sessionsFailed = sessions.count { !it.completed },
            habits = habits.map { h -> HabitResult(h.id, h.title, h.completions.count { it in weekStart..end }) },
            unfinished = unfinished,
            categorySplit = done.groupingBy { it.category }.eachCount(),
        )
    }

    /** Moves an unfinished task to [newDate] ("carry forward"). */
    fun carryForward(task: Task, newDate: LocalDate): Task = task.copy(planned = newDate)

    /** One review per week; it can be done during that week or after it. */
    fun canComplete(weekStart: LocalDate, today: LocalDate, existing: List<WeeklyReview>): Boolean =
        weekStart <= today && existing.none { it.weekStart == weekStart }

    fun complete(weekStart: LocalDate, nowMillis: Long, note: String?, carried: List<Task>, existing: List<WeeklyReview>): WeeklyReview {
        check(existing.none { it.weekStart == weekStart }) { "this week was already reviewed" }
        return WeeklyReview(weekStart, nowMillis, promptFor(weekStart), note?.trim()?.ifEmpty { null }, carried.map { it.id }, rewarded = true)
    }

    /** The week's reflection question: the same for everyone, rotating through the pool. */
    fun promptFor(weekStart: LocalDate): String {
        val weekNumber = LocalDate(1970, 1, 1).daysUntil(weekStart) / 7
        return prompts[((weekNumber % prompts.size) + prompts.size) % prompts.size]
    }

    val prompts: List<String> = listOf(
        "What felt easier than expected this week?",
        "What are you proud of this week?",
        "What would make next week a little easier?",
        "What gave you energy this week?",
        "What drained you the most?",
        "Which small win do you want to remember?",
        "What did you finally start?",
        "What can you let go of next week?",
        "Who helped you this week?",
        "What did you learn about how you work best?",
        "What was the best part of your week?",
        "What would you do again?",
        "What would you do differently?",
        "What are you looking forward to?",
        "When did you feel most focused?",
        "What is one thing you are glad you did?",
        "What surprised you?",
        "What do you need more of next week?",
        "What do you need less of next week?",
        "What is one kind thing you did for yourself?",
        "What is one kind thing someone did for you?",
        "Which task was bigger than it looked?",
        "Which task was smaller than it looked?",
        "What made a hard day easier?",
        "What routine helped you most?",
        "What did you put off, and what would a tiny first step be?",
        "Where did your time actually go?",
        "What would a good week look like next time?",
        "What did you enjoy that was not on your list?",
        "How did you rest this week?",
        "What made you laugh?",
        "What is one thing you want to keep doing?",
        "What is one thing you want to stop doing?",
        "What helped you get started when it was hard?",
        "What did you finish that you had been carrying around?",
        "Who would you like to spend more time with?",
        "What are you curious about right now?",
        "What was your best focus session like?",
        "What got in the way of focusing?",
        "What is a small habit worth protecting?",
        "What do you want to say no to next week?",
        "What gave you a sense of progress?",
        "What would your future self thank you for?",
        "What is one thing that went better than last week?",
        "What did you notice about your mood and your energy?",
        "What did you do that took courage?",
        "What would make tomorrow feel lighter?",
        "What do you want to celebrate?",
        "What is one thing you can forgive yourself for?",
        "What is your intention for next week?",
    )
}
