package app.pokeplan.ui.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CardDaysTest {
    @Test
    fun ordinals() {
        val expected = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 30 to "30th", 31 to "31st")
        expected.forEach { (n, s) -> assertEquals(s, ordinal(n)) }
    }

    @Test
    fun dayLines() {
        val today = LocalDate(2026, 11, 15)
        assertEquals("Statement on the 15th · today", dayLine("Statement", 15, today))
        assertEquals("Due on the 16th · tomorrow", dayLine("Due", 16, today))
        assertEquals("Statement on the 22nd · in 7 days", dayLine("Statement", 22, today))
        assertEquals(null, dayLine("Due", 0, today))
        assertEquals("31", dayInput("45"))
    }
}

class PercentTest {
    @Test
    fun percentsBecomeBasisPoints() {
        assertEquals(150, percentToBasisPoints("1.5"))
        assertEquals(150, percentToBasisPoints("1,5"))
        assertEquals(200, percentToBasisPoints("2"))
        assertEquals(25, percentToBasisPoints("0.25"))
        assertEquals(5, percentToBasisPoints(".05"))
        assertEquals(null, percentToBasisPoints(""))
        assertEquals(null, percentToBasisPoints("1.234"))
        assertEquals(null, percentToBasisPoints("1.2.3"))
    }
}
