package app.pokeplan.ui.gear

import app.pokeplan.app.GearRates
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GearToolsTest {
    private fun unit(kind: String, symbol: String) = Units.kinds.first { it.label == kind }.units.first { it.symbol == symbol }
    private fun close(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-6, "$a is not $b")

    @Test
    fun convertsBetweenUnits() {
        close(1.609344, Units.convert(1.0, unit("Length", "mi"), unit("Length", "km")))
        close(12.0, Units.convert(1.0, unit("Length", "ft"), unit("Length", "in")))
        close(2.2046226, Units.convert(1.0, unit("Mass", "kg"), unit("Mass", "lb")))
        close(3.785411784, Units.convert(1.0, unit("Volume", "gal"), unit("Volume", "L")))
        close(36.0, Units.convert(10.0, unit("Speed", "m/s"), unit("Speed", "km/h")))
    }

    @Test
    fun convertsTemperatures() {
        close(212.0, Units.convert(100.0, unit("Temperature", "C"), unit("Temperature", "F")))
        close(-40.0, Units.convert(-40.0, unit("Temperature", "F"), unit("Temperature", "C")))
        close(273.15, Units.convert(0.0, unit("Temperature", "C"), unit("Temperature", "K")))
    }

    @Test
    fun readsTheRatesServiceAnswer() {
        val rates = parseRates("""{"amount":1.0,"base":"USD","date":"2026-10-30","rates":{"EUR":0.5,"JPY":150.0}}""")
        assertEquals("2026-10-30", rates?.date)
        assertEquals(1.0, rates?.perUsd?.get("USD"))
        assertEquals(150.0, rates?.perUsd?.get("JPY"))
        assertNull(parseRates("nonsense"))
        assertNull(parseRates("""{"date":"x","rates":{}}"""))
    }

    @Test
    fun convertsCurrencyThroughTheDollar() {
        val rates = GearRates("2026-10-30", mapOf("USD" to 1.0, "EUR" to 0.5, "JPY" to 150.0))
        // One euro is two dollars, which is 300 yen.
        close(300.0, convertCurrency(1.0, "EUR", "JPY", rates)!!)
        close(0.5, convertCurrency(1.0, "USD", "EUR", rates)!!)
        assertNull(convertCurrency(1.0, "XXX", "EUR", rates))
    }
}
