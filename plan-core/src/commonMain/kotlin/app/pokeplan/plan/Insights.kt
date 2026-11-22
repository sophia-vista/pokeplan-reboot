package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

@kotlinx.serialization.Serializable
data class FocusRecord(
    val startedAtMillis: Long,
    val minutes: Int,
    val completed: Boolean,
    val taskId: String? = null,
    val deepFocus: Boolean = false,
)

object Insights {
    const val MINUTES_PER_TASK_POINT = 30

    private fun dayOf(millis: Long, zone: TimeZone): LocalDate = millis.toLocalDateTime(zone).date

    data class Wheel(val tasksDone: Map<String?, Int>, val focusMinutes: Map<String?, Int>) {
        fun score(listId: String?): Double = (tasksDone[listId] ?: 0) + (focusMinutes[listId] ?: 0).toDouble() / MINUTES_PER_TASK_POINT

        val lists: Set<String?> get() = tasksDone.keys + focusMinutes.keys

        private val total: Double get() = lists.sumOf { score(it) }

        fun share(listId: String?): Double = if (total == 0.0) 0.0 else score(listId) / total
    }

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

    fun estimateLine(task: Task): String? {
        val estimate = task.estimateMinutes ?: return null
        if (task.focusedMinutes <= 0) return null
        return "You guessed $estimate min; it took ${task.focusedMinutes} min."
    }
}
