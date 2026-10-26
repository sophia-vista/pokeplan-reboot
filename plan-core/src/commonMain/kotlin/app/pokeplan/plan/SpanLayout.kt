package app.pokeplan.plan

import kotlinx.datetime.LocalDate

/** Something that covers whole days on a calendar row: an all-day event over [first] to [last], or one day's item. */
data class DaySpan<T>(val item: T, val first: LocalDate, val last: LocalDate)

/**
 * Lays whole-day spans on a row of consecutive [days] as bars in lanes, as Google Calendar and TickTick draw multi-day
 * events across a week: a bar runs from column [Bar.from] to [Bar.to], and bars that would overlap take different lanes.
 */
object SpanLayout {
    /** A span placed on the row. [clippedStart] and [clippedEnd] say it carries on before or after the row's days. */
    data class Bar<T>(val item: T, val from: Int, val to: Int, val lane: Int, val clippedStart: Boolean, val clippedEnd: Boolean)

    /**
     * The bars for the [spans] that touch [days], each in the lowest lane free over all its columns. Longer spans come
     * first so they keep the top lanes, then earlier ones; spans of the same length and day keep their given order.
     */
    fun <T> lay(spans: List<DaySpan<T>>, days: List<LocalDate>): List<Bar<T>> {
        if (days.isEmpty()) return emptyList()
        val first = days.first()
        val last = days.last()
        val placed = mutableListOf<Bar<T>>()
        val taken = mutableListOf<BooleanArray>()
        spans.filter { it.last >= first && it.first <= last }
            .sortedWith(compareBy({ -(minOf(it.last, last).toEpochDays() - maxOf(it.first, first).toEpochDays()) }, { it.first }))
            .forEach { s ->
                val from = days.indexOf(maxOf(s.first, first))
                val to = days.indexOf(minOf(s.last, last))
                val lane = taken.indexOfFirst { row -> (from..to).none { row[it] } }.let { if (it >= 0) it else taken.size.also { taken.add(BooleanArray(days.size)) } }
                (from..to).forEach { taken[lane][it] = true }
                placed += Bar(s.item, from, to, lane, clippedStart = (s.first < first), clippedEnd = (s.last > last))
            }
        return placed
    }
}
