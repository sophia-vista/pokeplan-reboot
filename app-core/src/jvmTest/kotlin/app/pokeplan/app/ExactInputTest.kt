package app.pokeplan.app

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExactInputTest {
    private val today = LocalDate(2026, 11, 12)

    @Test fun datesYearFirst() {
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("2026-11-07", today))
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("2026/11/7", today))
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate(" 2026.11.07 ", today))
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("20261107", today))
    }

    @Test fun datesDayFirstNeedAFourDigitYear() {
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("7/11/2026", today))
        assertEquals(LocalDate(2027, 1, 2), ExactInput.parseDate("02-01-2027", today))
        assertNull(ExactInput.parseDate("7/11", today))
        assertNull(ExactInput.parseDate("7/11/26", today))
    }

    @Test fun datesWithMonthNames() {
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("7 Nov 2026", today))
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("7 nov", today))
        assertEquals(LocalDate(2026, 11, 7), ExactInput.parseDate("November 7, 2026", today))
        assertEquals(LocalDate(2027, 3, 1), ExactInput.parseDate("1st March 2027", today))
        assertEquals(LocalDate(2026, 9, 3), ExactInput.parseDate("sept 3", today))
        assertNull(ExactInput.parseDate("7 marx 2026", today))
        assertNull(ExactInput.parseDate("mon 9", today))
    }

    @Test fun datesWords() {
        assertEquals(today, ExactInput.parseDate("Today", today))
        assertEquals(LocalDate(2026, 11, 13), ExactInput.parseDate("tomorrow", today))
        assertEquals(LocalDate(2026, 11, 11), ExactInput.parseDate("yesterday", today))
    }

    @Test fun impossibleDatesAreNull() {
        assertNull(ExactInput.parseDate("2026-02-30", today))
        assertNull(ExactInput.parseDate("2026-13-01", today))
        assertNull(ExactInput.parseDate("31 Apr 2026", today))
        assertEquals(LocalDate(2028, 2, 29), ExactInput.parseDate("2028-02-29", today))
        assertNull(ExactInput.parseDate("2027-02-29", today))
        assertNull(ExactInput.parseDate("", today))
        assertNull(ExactInput.parseDate("soon", today))
    }

    @Test fun times() {
        fun t(s: String) = ExactInput.parseTime(s)
        assertEquals(LocalTime(9, 0), t("9"))
        assertEquals(LocalTime(9, 30), t("930"))
        assertEquals(LocalTime(9, 30), t("09:30"))
        assertEquals(LocalTime(9, 5), t("0905"))
        assertEquals(LocalTime(21, 5), t("21:05"))
        assertEquals(LocalTime(21, 0), t("9pm"))
        assertEquals(LocalTime(21, 30), t("9:30 PM"))
        assertEquals(LocalTime(0, 15), t("12:15am"))
        assertEquals(LocalTime(12, 0), t("12pm"))
        assertEquals(LocalTime(12, 0), t("noon"))
        assertEquals(LocalTime(0, 0), t("midnight"))
        assertEquals(LocalTime(7, 5), t("7h05"))
        assertNull(t("24:00"))
        assertNull(t("25"))
        assertNull(t("9:60"))
        assertNull(t("13pm"))
        assertNull(t("0pm"))
        assertNull(t("960"))
        assertNull(t("later"))
    }

    @Test fun endOfDay() {
        assertEquals(1440, ExactInput.parseMinuteOfDay("24:00", allowEndOfDay = true))
        assertNull(ExactInput.parseMinuteOfDay("24:30", allowEndOfDay = true))
        assertEquals("09:05", ExactInput.formatMinuteOfDay(9 * 60 + 5))
    }

    @Test fun durations() {
        fun m(s: String) = ExactInput.parseDurationMinutes(s)
        assertEquals(90, m("90"))
        assertEquals(90, m("90m"))
        assertEquals(90, m("1h30"))
        assertEquals(90, m("1h 30m"))
        assertEquals(90, m("1:30"))
        assertEquals(90, m("1.5h"))
        assertEquals(120, m("2 hours"))
        assertEquals(45, m("45 min"))
        assertNull(m("90s"))
        assertNull(m("abc"))
        assertNull(m(""))
        assertNull(m("1:75"))
        assertEquals(60, m("60s".replace("60s", "60m")))
    }

    @Test fun secondsDurations() {
        fun s(t: String, unit: Char = 's') = ExactInput.parseDurationSeconds(t, unit)
        assertEquals(90L, s("90"))
        assertEquals(90L, s("1:30"))
        assertEquals(5430L, s("1:30:30"))
        assertEquals(3725L, s("1h2m5s"))
        assertEquals(3725L, s("1h 2m 5"))
        assertEquals(300L, s("5", 'm'))
        assertNull(s("1:30:75"))
    }

    @Test fun durationWords() {
        assertEquals("45 min", ExactInput.formatMinutes(45))
        assertEquals("2 h", ExactInput.formatMinutes(120))
        assertEquals("1 h 30 min", ExactInput.formatMinutes(90))
    }
}
