package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** One finished or failed focus session, as kept in the history. */
@kotlinx.serialization.Serializable
data class FocusRecord(
    val startedAtMillis: Long,
    val minutes: Int,
    val completed: Boolean,
    val taskId: String? = null,
    val deepFocus: Boolean = false,
)

/** The Growth screen's numbers. See "Growth" in docs/design.md. */
object Insights {
    /** A 30 minute session counts about as much as one finished task on the balance wheel. *(tune)* */
    const val MINUTES_PER_TASK_POINT = 30

    private fun dayOf(millis: Long, zone: TimeZone): LocalDate = millis.toLocalDateTime(zone).date

    /** What was done per list id (null: no list). */
    data class Wheel(val tasksDone: Map<String?, Int>, val focusMinutes: Map<String?, Int>) {
        fun score(listId: String?): Double = (tasksDone[listId] ?: 0) + (focusMinutes[listId] ?: 0).toDouble() / MINUTES_PER_TASK_POINT

        /** Every list that has anything done, in no particular order. */
        val lists: Set<String?> get() = tasksDone.keys + focusMinutes.keys

        private val total: Double get() = lists.sumOf { score(it) }

        /** The list's share of everything done, 0..1 (0 for all when nothing was done). */
        fun share(listId: String?): Double = if (total == 0.0) 0.0 else score(listId) / total
    }

    /** Finished tasks and completed focus time per list for [from]..[to] inclusive. Focus without a task counts as no list. */
    fun balanceWheel(tasks: List<Task>, focus: List<FocusRecord>, from: LocalDate, to: LocalDate, zone: TimeZone): Wheel {
        val byId = tasks.associateBy { it.id }
        val done = tasks.filter { it.state == TaskState.DONE && it.completedAtMillis != null && dayOf(it.completedAtMillis, zone) in from..to }
            .groupingBy { it.listId }.eachCount()
        val minutes = focus.filter { it.completed && dayOf(it.startedAtMillis, zone) in from..to }
            .groupBy { byId[it.taskId]?.listId }
            .mapValues { (_, records) -> records.sumOf { it.minutes } }
        return Wheel(done, minutes)
    }

    data class WeekPoint(val weekStart: LocalDate, val focusMinutes: Int, val tasksDone: Int)

    /** The last [weeks] weeks up to and including the week of [today], oldest first. */
    fun weeklyTrend(tasks: List<Task>, focus: List<FocusRecord>, weeks: Int, today: LocalDate, zone: TimeZone, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<WeekPoint> {
        val current = Streaks.weekStartOf(today, weekStart)
        return (weeks - 1 downTo 0).map { back ->
            val start = current.plus(DatePeriod(days = -7 * back))
            val end = start.plus(DatePeriod(days = 6))
            WeekPoint(
                start,
                focus.filter { it.completed && dayOf(it.startedAtMillis, zone) in start..end }.sumOf { it.minutes },
                tasks.count { it.state == TaskState.DONE && it.completedAtMillis != null && dayOf(it.completedAtMillis, zone) in start..end },
            )
        }
    }

    enum class Bias { UNDERESTIMATES, ABOUT_RIGHT, OVERESTIMATES }

    data class EstimateAccuracy(val samples: Int, val averageRatio: Double, val bias: Bias)

    /**
     * How long tasks really took against the estimate, from finished tasks that have both an estimate and focused minutes.
     * A ratio above 1 means tasks took longer than guessed. Needs at least [MIN_SAMPLES] tasks, otherwise null.
     */
    fun estimateAccuracy(tasks: List<Task>): EstimateAccuracy? {
        val samples = tasks.filter { it.state == TaskState.DONE && it.estimateMinutes != null && it.focusedMinutes > 0 }
        if (samples.size < MIN_SAMPLES) return null
        val ratio = samples.map { it.focusedMinutes.toDouble() / it.estimateMinutes!! }.average()
        val bias = when {
            ratio > 1.2 -> Bias.UNDERESTIMATES
            ratio < 0.8 -> Bias.OVERESTIMATES
            else -> Bias.ABOUT_RIGHT
        }
        return EstimateAccuracy(samples.size, ratio, bias)
    }

    const val MIN_SAMPLES = 3

    /** After a task, the estimate-versus-actual line shown to the player. */
    fun estimateLine(task: Task): String? {
        val estimate = task.estimateMinutes ?: return null
        if (task.focusedMinutes <= 0) return null
        return "You guessed $estimate min; it took ${task.focusedMinutes} min."
    }
}
