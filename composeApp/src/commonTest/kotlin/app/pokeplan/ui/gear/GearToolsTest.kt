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
    private fun unit(category: String, symbol: String) = UnitCatalog.category(category)!!.units.first { it.symbol == symbol }
    private fun close(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-6, "$a is not $b")

    @Test
    fun convertsBetweenUnits() {
        close(1.609344, UnitCatalog.convert(1.0, unit("length", "mi"), unit("length", "km")))
        close(12.0, UnitCatalog.convert(1.0, unit("length", "ft"), unit("length", "in")))
        close(2.2046226, UnitCatalog.convert(1.0, unit("mass", "kg"), unit("mass", "lb")))
        close(3.785411784, UnitCatalog.convert(1.0, unit("volume", "gal"), unit("volume", "L")))
        close(36.0, UnitCatalog.convert(10.0, unit("speed", "m/s"), unit("speed", "km/h")))
    }

    @Test
    fun convertsTemperatures() {
        close(212.0, UnitCatalog.convert(100.0, unit("temperature", "C"), unit("temperature", "F")))
        close(-40.0, UnitCatalog.convert(-40.0, unit("temperature", "F"), unit("temperature", "C")))
        close(273.15, UnitCatalog.convert(0.0, unit("temperature", "C"), unit("temperature", "K")))
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
    fun theRatesAnswerIsValidatedAndNormalisedToTheDollar() {
        val eur = parseRates("""{"base":"EUR","date":"2026-10-30","rates":{"USD":1.25,"PHP":70.0,"JPY":200.0}}""", 123)!!
        close(0.8, eur.perUsd.getValue("EUR"))
        close(56.0, eur.perUsd.getValue("PHP"))
        close(160.0, eur.perUsd.getValue("JPY"))
        assertEquals(1.0, eur.perUsd["USD"])
        assertEquals(123L, eur.fetchedAtMillis)
        assertEquals(RATES_SOURCE, eur.source)
        assertNull(parseRates("""{"base":"EUR","date":"2026-10-30","rates":{"PHP":70.0}}"""))
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
        assertTrue(!ask.usableNow && ask.needsAsking && ask.describe().contains("asks first"))
        assertTrue(!none.usableNow && !none.needsAsking && none.describe().startsWith("Barometer: not available."))
    }
}

class CurrenciesTest {
    private val rates = GearRates("2026-10-30", mapOf("USD" to 1.0, "PHP" to 58.0, "JPY" to 150.0, "EUR" to 0.5, "CNY" to 7.0))
    private fun near(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-9, "$a is not $b")

    @Test
    fun searchByNameCodeAliasAndASlip() {
        fun first(q: String) = Currencies.search(q).firstOrNull()?.code
        assertEquals("CNY", first("rmb")); assertEquals("CNY", first("yuan")); assertEquals("JPY", first("yen")); assertEquals("PHP", first("peso"))
        assertEquals("KRW", first("won")); assertEquals("USD", first("usd")); assertEquals("EUR", first("euro")); assertEquals("PHP", first("philipine"))
        assertTrue(Currencies.search("peso").map { it.code }.containsAll(listOf("PHP", "MXN")))
        assertEquals(listOf("JPY", "PHP"), Currencies.search("", among = listOf("JPY", "PHP")).map { it.code })
        assertTrue(Currencies.search("zzzz").isEmpty())
        assertEquals("PHP, Philippine peso", Currencies.label("PHP"))
        assertEquals("XYZ", Currencies.label("XYZ"))
    }

    @Test
    fun amountsShowTheRightMinorUnitsAndGrouping() {
        assertEquals("1,234.50 PHP", Currencies.format(1234.5, "PHP"))
        assertEquals("1,235 JPY", Currencies.format(1234.6, "JPY"))
        assertEquals("1,000 KRW", Currencies.format(999.9, "KRW"))
        assertEquals("0.00 USD", Currencies.format(0.0, "USD"))
        assertEquals("-12.35 EUR", Currencies.format(-12.346, "EUR"))
        assertEquals("1,000,000.00 PHP", Currencies.format(1_000_000.0, "PHP"))
        assertEquals("5.00 XYZ", Currencies.format(5.0, "XYZ"))
    }

    @Test
    fun ratesGoThroughTheDollarAndAManualRateWinsForTheHomePair() {
        near(150.0 / 58.0, Currencies.rate("PHP", "JPY", rates, "PHP", emptyMap())!!)
        near(1.0, Currencies.rate("PHP", "PHP", rates, "PHP", emptyMap())!!)
        val manual = mapOf("JPY" to 2.5)
        near(2.5, Currencies.rate("PHP", "JPY", rates, "PHP", manual)!!)
        near(0.4, Currencies.rate("JPY", "PHP", rates, "PHP", manual)!!)
        near(7.0 / 150.0, Currencies.rate("JPY", "CNY", rates, "PHP", manual)!!)
        assertNull(Currencies.rate("PHP", "XXX", rates, "PHP", emptyMap()))
        assertNull(Currencies.rate("PHP", "JPY", null, "PHP", emptyMap()))
        near(150.0 / 58.0, Currencies.rate("PHP", "JPY", rates, "PHP", mapOf("JPY" to -1.0))!!)
    }

    @Test
    fun whatItReallyCostsKeepsTheReferenceTheRateAndTheFeesApart() {
        val c = Travel.estimate(15_000.0, 2.5, 2.4, 3.0, 50.0)!!
        near(6_000.0, c.reference); near(6_250.0, c.atQuotedRate); near(6_250.0 * 0.03 + 50.0, c.fees)
        near(6_250.0 + 6_250.0 * 0.03 + 50.0, c.total); near(c.total - 6_000.0, c.extra); near(c.extra / 6_000.0 * 100.0, c.extraPercent)
        val plain = Travel.estimate(15_000.0, 2.5, null, 0.0, 0.0)!!
        near(0.0, plain.extra); near(plain.reference, plain.total)
        assertNull(Travel.estimate(-1.0, 2.5, null, 0.0, 0.0)); assertNull(Travel.estimate(10.0, 0.0, null, 0.0, 0.0))
        assertNull(Travel.estimate(10.0, 2.5, -2.0, 0.0, 0.0)); assertNull(Travel.estimate(10.0, 2.5, null, -1.0, 0.0)); assertNull(Travel.estimate(10.0, 2.5, null, 0.0, Double.NaN))
        near(0.0, Travel.estimate(0.0, 2.5, null, 0.0, 0.0)!!.extraPercent)
    }
}

class TimeFormatTest {
    @Test
    fun aTimerShowsWholeSecondsRoundedUp() {
        assertEquals("0:00", formatTimer(0)); assertEquals("0:01", formatTimer(1)); assertEquals("0:01", formatTimer(1000)); assertEquals("0:02", formatTimer(1001))
        assertEquals("1:00", formatTimer(60_000)); assertEquals("59:59", formatTimer(3_599_000)); assertEquals("1:00:00", formatTimer(3_600_000)); assertEquals("1:01:01", formatTimer(3_661_000))
        assertEquals("0:00", formatTimer(-5)); assertEquals("99:00:00", formatTimer(99L * 3_600_000))
    }

    @Test
    fun aStopwatchShowsHundredthsWithoutRunningAhead() {
        assertEquals("0:00.00", formatStopwatch(0)); assertEquals("0:00.99", formatStopwatch(999)); assertEquals("0:01.00", formatStopwatch(1000))
        assertEquals("1:02.34", formatStopwatch(62_345)); assertEquals("1:00:00.00", formatStopwatch(3_600_000)); assertEquals("0:00.00", formatStopwatch(-1))
    }
}
