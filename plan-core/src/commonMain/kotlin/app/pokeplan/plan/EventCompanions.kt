package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/**
 * What an event's companion earns when an occurrence ends (docs/pokelife/MODULES/PLAN.md 8.2, docs/economy.md "Event with a companion ends").
 * This object only decides which occurrences are owed and how much; the store pays them, so a companion is never read here.
 */
object EventCompanions {
    /** One Exp Candy's worth of exp for each this many minutes of a timed event *(tune)*. */
    const val MINUTES_PER_CANDY = 30

    /** An event shorter than this pays nothing; from here to a first full [MINUTES_PER_CANDY] it pays one candy. */
    const val MIN_MINUTES = 15

    /** At most this many candies for one occurrence. */
    const val MAX_CANDIES = 4

    /** At most this many candies' worth of exp a day across every event. */
    const val DAILY_CAP = 6

    /** The chance, in percent, that a paid occurrence also brings back one item from the ordinary drop table. */
    const val ITEM_PERCENT = 10

    /** An occurrence that ended longer ago than this many days is no longer owed (the app was closed). */
    const val LOOKBACK_DAYS = 2

    /** Candies an occurrence of [minutes] pays before the daily cap (0 for one that is too short). All-day events never pay; they are not asked. */
    fun candies(minutes: Int): Int = if (minutes < MIN_MINUTES) 0 else (minutes / MINUTES_PER_CANDY).coerceIn(1, MAX_CANDIES)

    /** One occurrence that is owed: [date] is the day it started, [eventId] the series it belongs to. */
    data class Due(val eventId: String, val date: LocalDate, val title: String, val companionId: String, val candies: Int)

    /**
     * The occurrences that have ended, are within [LOOKBACK_DAYS], have not been paid, and ended after their companion was named, oldest first.
     * [millis] turns a wall-clock time into epoch milliseconds (the store knows the time zone). A skipped occurrence is not an occurrence, so it
     * is never here; an all-day event and one with no companion never are either.
     */
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
