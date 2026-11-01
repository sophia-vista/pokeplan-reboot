package app.pokeplan.ui.gear

import app.pokeplan.app.GearRates
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
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

    @Test
    fun theRatesAnswerIsValidatedAndNormalisedToTheDollar() {
        // Another base: the dollar's own rate is used to turn everything into dollars (1 EUR = 1.25 USD, so 1 USD = 0.8 EUR).
        val eur = parseRates("""{"base":"EUR","date":"2026-10-30","rates":{"USD":1.25,"PHP":70.0,"JPY":200.0}}""", 123)!!
        close(0.8, eur.perUsd.getValue("EUR"))
        close(56.0, eur.perUsd.getValue("PHP"))
        close(160.0, eur.perUsd.getValue("JPY"))
        assertEquals(1.0, eur.perUsd["USD"])
        assertEquals(123L, eur.fetchedAtMillis)
        assertEquals(RATES_SOURCE, eur.source)
        // Without the dollar among the rates of another base there is nothing to normalise by.
        assertNull(parseRates("""{"base":"EUR","date":"2026-10-30","rates":{"PHP":70.0}}"""))
        // Bad rates are dropped, a bad date, base or an answer with nothing usable is refused.
        val mixed = parseRates("""{"base":"USD","date":"2026-10-30","rates":{"EUR":0.5,"AB1":3.0,"JPY":-1.0,"PHP":0.0,"KRW":1e999,"xyz":2.0,"CNY":7.2}}""")!!
        assertEquals(setOf("USD", "EUR", "CNY"), mixed.perUsd.keys)
        assertNull(parseRates("""{"base":"USD","date":"2026-13-45","rates":{"EUR":0.5}}"""))
        assertNull(parseRates("""{"base":"USD","date":"yesterday","rates":{"EUR":0.5}}"""))
        assertNull(parseRates("""{"base":"dollars","date":"2026-10-30","rates":{"EUR":0.5}}"""))
        assertNull(parseRates("""{"base":"USD","date":"2026-10-30","rates":{"EUR":-2.0}}"""))
        assertNull(parseRates("""{"base":"USD","date":"2026-10-30"}"""))
    }

    @Test
    fun theFreshnessLineSaysWhereRatesAreFromAndHowOldTheyAre() {
        val now = 1_000_000_000_000L
        val fresh = GearRates("2026-10-30", mapOf("USD" to 1.0), fetchedAtMillis = now - 3 * 3_600_000L, source = RATES_SOURCE)
        val text = ratesFreshness(fresh, now)
        assertTrue("Rates of 2026-10-30, fetched 3 h ago" in text && "European Central Bank" in text && "not what a bank" in text, text)
        assertTrue("fetched 2 days ago" in ratesFreshness(fresh.copy(fetchedAtMillis = now - 49 * 3_600_000L), now))
        assertTrue("just now" in ratesFreshness(fresh.copy(fetchedAtMillis = now - 10_000L), now))
        // A cache from before the fetch time was kept says only its date, and a clock behind it says nothing about age.
        assertTrue("fetched" !in ratesFreshness(GearRates("2026-10-30", mapOf("USD" to 1.0)), now))
        assertTrue("fetched" !in ratesFreshness(fresh, now - 10 * 3_600_000L))
    }

    @Test
    fun aFailedFetchReportsWhyAndTheClientCanBeClosed() = kotlinx.coroutines.test.runTest {
        val broken = RatesClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respondError(io.ktor.http.HttpStatusCode.ServiceUnavailable) }))
        assertNull(broken.latest(1))
        assertEquals("The rates service answered 503.", broken.lastError)
        broken.close()
        val garbage = RatesClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("<html>not rates</html>") }))
        assertNull(garbage.latest(1))
        assertEquals("The rates service sent something that is not rates.", garbage.lastError)
        garbage.close()
        val good = RatesClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("""{"base":"USD","date":"2026-10-30","rates":{"PHP":58.0}}""") }))
        assertEquals(58.0, good.latest(5)?.perUsd?.get("PHP"))
        assertNull(good.lastError)
        good.close()
    }
}

class CapabilitiesTest {
    @Test
    fun aPlatformWithoutHardwareSaysSoForEveryCapability() {
        val all = NoCapabilities.all()
        assertEquals(Capability.entries, all.map { it.capability })
        assertTrue(all.none { it.available || it.usableNow }, "nothing is faked")
        assertTrue(all.all { it.describe().contains("not available") && it.describe().contains("this platform") })
    }

    @Test
    fun aGrantedMissingOrAskedForCapabilityIsToldApart() {
        val ready = CapabilityStatus(Capability.CAMERA, true, PermissionNeed.CAMERA, true)
        val ask = CapabilityStatus(Capability.CAMERA, true, PermissionNeed.CAMERA, false)
        val none = CapabilityStatus(Capability.BAROMETER, false, note = "Few phones have one.")
        val free = CapabilityStatus(Capability.TORCH, true)
        assertTrue(ready.usableNow && !ready.needsAsking && free.usableNow)
        assertTrue(!ask.usableNow && ask.needsAsking && ask.describe().contains("asked for when you use it"))
        assertTrue(!none.usableNow && !none.needsAsking && none.describe().startsWith("Barometer: not available."))
    }
}
