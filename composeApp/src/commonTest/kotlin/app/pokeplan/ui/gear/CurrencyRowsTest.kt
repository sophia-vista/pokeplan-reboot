package app.pokeplan.ui.gear

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyRowsTest {
    @Test
    fun plainDigitsFollowTheMinorUnits() {
        assertEquals("1000", Currencies.plain(1000.0, "PHP"))
        assertEquals("1234.5", Currencies.plain(1234.5, "USD"))
        assertEquals("12.34", Currencies.plain(12.3449, "USD"))
        assertEquals("1235", Currencies.plain(1234.6, "JPY"))
        assertEquals("0", Currencies.plain(0.0, "EUR"))
        assertEquals("-3.5", Currencies.plain(-3.5, "EUR"))
    }

    @Test
    fun rowsAreHomeFirstThenTheBoardOnceEachWithOnlyKnownRates() {
        assertEquals(listOf("PHP", "USD", "JPY"), Currencies.rows("PHP", listOf("USD", "PHP", "JPY", "USD", "XXX"), setOf("PHP", "USD", "JPY")))
        assertEquals(listOf("EUR"), Currencies.rows("EUR", emptyList(), setOf("EUR")))
    }
}
