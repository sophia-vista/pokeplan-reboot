package app.pokeplan.plan

import kotlinx.datetime.LocalDate

data class DaySpan<T>(val item: T, val first: LocalDate, val last: LocalDate)

object SpanLayout {
    data class Bar<T>(val item: T, val from: Int, val to: Int, val lane: Int, val clippedStart: Boolean, val clippedEnd: Boolean)

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
