package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** One proposed hour for a task. Nothing is scheduled until the player accepts it. */
data class Suggestion(val taskId: String, val day: LocalDate, val start: LocalTime, val minutes: Int) {
    val startMinute: Int get() = start.hour * 60 + start.minute
}

/**
 * Optional planning suggestions (owner list of 27 November 2026, P2): where the tasks that have no time yet could go in a day,
 * using only the gaps between what is already planned. Pure and deterministic, no task is ever moved or paid by it.
 *
 * Order: a deadline today first (earliest first), then harder ranks, then the oldest. A task takes its estimate (half an hour
 * without one) and starts on a quarter hour; a gap of [BUFFER] minutes is left after each, and an evening task looks from 18:00.
 */
object PlanSuggestions {
    const val DAY_START = 8 * 60
    const val DAY_END = 20 * 60
    const val DEFAULT_MINUTES = 30
    const val BUFFER = 10
    const val STEP = 15
    const val MAX = 6

    /**
     * [busy] is the day's occupied time as minutes of the day (tasks with a time, events, phone-calendar events). For today nothing
     * is suggested before the next quarter hour from [now].
     */
    fun suggest(candidates: List<Task>, busy: List<IntRange>, day: LocalDate, now: LocalDateTime, max: Int = MAX): List<Suggestion> {
        val floor = if (day == now.date) maxOf(DAY_START, roundUp(now.hour * 60 + now.minute)) else DAY_START
        val taken = busy.filter { !it.isEmpty() }.toMutableList()
        val order = candidates.filter { it.isOpen && it.plannedTime == null && !it.ownedElsewhere }
            .sortedWith(compareBy<Task>({ if (it.due?.date == day) 0 else 1 }, { if (it.due?.date == day) it.due!!.time else LocalTime(23, 59) }, { -it.rank.tier }, { it.createdAtMillis }))
        val out = mutableListOf<Suggestion>()
        for (t in order) {
            if (out.size >= max) break
            val minutes = (t.estimateMinutes ?: DEFAULT_MINUTES).coerceAtLeast(1)
            val from = if (t.evening || TaskViews.isEvening(t)) maxOf(floor, 18 * 60) else floor
            val start = firstFit(taken, from, minutes) ?: continue
            out += Suggestion(t.id, day, LocalTime(start / 60, start % 60), minutes)
            taken += start until (start + minutes + BUFFER)
        }
        return out
    }

    private fun firstFit(taken: List<IntRange>, from: Int, minutes: Int): Int? {
        var at = roundUp(from)
        while (at + minutes <= DAY_END) {
            val clash = taken.filter { it.first < at + minutes && it.last >= at }.maxOfOrNull { it.last + 1 }
            if (clash == null) return at
            at = roundUp(clash)
        }
        return null
    }

    private fun roundUp(minute: Int): Int = (minute + STEP - 1) / STEP * STEP
}
