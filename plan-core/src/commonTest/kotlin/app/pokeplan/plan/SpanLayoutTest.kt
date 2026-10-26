package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class SpanLayoutTest {
    private val monday = LocalDate(2026, 10, 26)
    private val week = (0 until 7).map { monday.plus(DatePeriod(days = it)) }
    private fun d(i: Int) = monday.plus(DatePeriod(days = i))

    @Test
    fun overlapping_spans_take_different_lanes_and_longer_ones_go_on_top() {
        val bars = SpanLayout.lay(
            listOf(DaySpan("rto", d(1), d(1)), DaySpan("condo", d(1), d(5)), DaySpan("home", d(0), d(0)), DaySpan("wfh", d(2), d(2))),
            week,
        ).associateBy { it.item }
        assertEquals(0, bars.getValue("condo").lane)
        assertEquals(1 to 5, bars.getValue("condo").let { it.from to it.to })
        assertEquals(0, bars.getValue("home").lane, "a free column reuses the top lane")
        assertEquals(1, bars.getValue("rto").lane)
        assertEquals(1, bars.getValue("wfh").lane)
    }

    @Test
    fun a_span_running_past_the_row_is_cut_at_its_edges() {
        val bar = SpanLayout.lay(listOf(DaySpan("trip", d(-2), d(9)), DaySpan("before", d(-5), d(-1))), week).single()
        assertEquals(0 to 6, bar.from to bar.to)
        assertEquals(true to true, bar.clippedStart to bar.clippedEnd)
    }
}
