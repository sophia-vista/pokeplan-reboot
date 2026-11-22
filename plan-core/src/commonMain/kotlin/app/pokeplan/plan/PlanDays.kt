package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

object PlanDays {
    fun presets(today: LocalDate): List<Pair<String, Int>> = listOf(
        "Today" to 0,
        "Tomorrow" to 1,
        "This weekend" to daysToWeekend(today),
        "Next week" to daysToNextMonday(today),
    ).distinctBy { it.second }

    fun daysToWeekend(today: LocalDate): Int = today.dayOfWeek.isoDayNumber.let { if (it >= 6) 0 else 6 - it }

    fun daysToNextMonday(today: LocalDate): Int = 8 - today.dayOfWeek.isoDayNumber

    fun week(first: DayOfWeek): List<DayOfWeek> = (0 until 7).map { DayOfWeek.entries[(first.ordinal + it) % 7] }
}

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

fun Task.instanceDay(): LocalDate? {
    val series = seriesId ?: return null
    if (!id.startsWith("$series-")) return null
    return runCatching { LocalDate.parse(id.removePrefix("$series-")) }.getOrNull()
}

val Task.movedOccurrence: Boolean get() = instanceDay()?.let { planned != null && planned != it } == true
