package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

object EventCompanions {
    const val MINUTES_PER_CANDY = 30

    const val MIN_MINUTES = 15

    const val MAX_CANDIES = 4

    const val DAILY_CAP = 6

    const val ITEM_PERCENT = 10

    const val LOOKBACK_DAYS = 2

    fun candies(minutes: Int): Int = if (minutes < MIN_MINUTES) 0 else (minutes / MINUTES_PER_CANDY).coerceIn(1, MAX_CANDIES)

    data class Due(val eventId: String, val date: LocalDate, val title: String, val companionId: String, val candies: Int)

    fun due(events: List<PlanEvent>, now: LocalDateTime, millis: (LocalDateTime) -> Long): List<Due> {
        val from = now.date.minus(DatePeriod(days = LOOKBACK_DAYS))
        val nowMillis = millis(now)
        return events.filter { it.companionId != null && !it.allDay }.flatMap { series ->
            series.occurrences(from, now.date).mapNotNull { o ->
                val ended = millis(o.toCalendarEvent().end)
                val candies = candies(o.minutes)
                if (candies == 0 || o.day in series.paid || ended > nowMillis || ended <= series.companionSince) null
                else Due(series.id, o.day, series.title, series.companionId!!, candies)
            }
        }.sortedWith(compareBy({ it.date }, { it.eventId }))
    }
}
