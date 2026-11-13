package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The days Plan offers as keys wherever a task or an event gets a day (PLAN.md section 4.7): Today, Tomorrow, This weekend
 * (the coming Saturday) and Next week (the coming Monday), each as days ahead of today. A key that lands on a day an earlier
 * key already names is left out (on a Friday, Tomorrow is the weekend; on a Sunday, Tomorrow is next week), so no two keys
 * light up together. "Pick a day" and "No date" belong to the field that shows them.
 */
object PlanDays {
    fun presets(today: LocalDate): List<Pair<String, Int>> = listOf(
        "Today" to 0,
        "Tomorrow" to 1,
        "This weekend" to daysToWeekend(today),
        "Next week" to daysToNextMonday(today),
    ).distinctBy { it.second }

    /** Days to the coming Saturday: 0 on a Saturday or a Sunday (the weekend is already here). */
    fun daysToWeekend(today: LocalDate): Int = today.dayOfWeek.isoDayNumber.let { if (it >= 6) 0 else 6 - it }

    /** Days to the next Monday, always after today (7 on a Monday). */
    fun daysToNextMonday(today: LocalDate): Int = 8 - today.dayOfWeek.isoDayNumber

    /** The days of the week in the order the player's week runs, starting on [first]. */
    fun week(first: DayOfWeek): List<DayOfWeek> = (0 until 7).map { DayOfWeek.entries[(first.ordinal + it) % 7] }
}

/** How a repeat reads after "repeats": "daily", "on weekdays", "every 2 weeks on Mon, Thu", "yearly". [start] gives a weekly repeat's day. */
fun Repeat.words(start: LocalDate): String = when (this) {
    is Repeat.Daily -> if (every == 1) "daily" else "every $every days"
    is Repeat.Weekly -> {
        val on = days.ifEmpty { setOf(start.dayOfWeek) }
        val workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val names = DayOfWeek.entries.filter { it in on }.joinToString(", ") { it.name.take(1) + it.name.substring(1, 3).lowercase() }
        when {
            every == 1 && on == workdays -> "on weekdays"
            every == 1 && on.size == 7 -> "daily"
            every == 1 && on.size == 1 -> "weekly"
            every == 1 -> "weekly on $names"
            else -> "every $every weeks on $names"
        }
    }
    is Repeat.Monthly -> if (every == 1) "monthly" else "every $every months"
    is Repeat.Yearly -> if (every == 1) "yearly" else "every $every years"
}

/**
 * The day a repeating task's instance was made for (its id is `<series>-<date>`, [RecurringTasks.instanceId]), or null for a
 * task that is not one. When it differs from the planned day the player moved this one only; the others keep their days.
 */
fun Task.instanceDay(): LocalDate? {
    val series = seriesId ?: return null
    if (!id.startsWith("$series-")) return null
    return runCatching { LocalDate.parse(id.removePrefix("$series-")) }.getOrNull()
}

/** True for a repeating task's instance planned on a day other than the one it was made for. */
val Task.movedOccurrence: Boolean get() = instanceDay()?.let { planned != null && planned != it } == true
