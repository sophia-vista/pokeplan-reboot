package app.pokeplan.ui.gear

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnitCatalogTest {
    private fun u(id: String) = assertNotNull(UnitCatalog.unit(id), id)
    private fun conv(v: Double, from: String, to: String) = UnitCatalog.convert(v, u(from), u(to))
    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-9) = assertTrue(abs(expected - actual) <= tolerance * maxOf(1.0, abs(expected)), "expected $expected but was $actual")

    @Test
    fun everyUnitHasAStableUniqueIdAndASymbolThatIsUniqueInItsCategory() {
        val ids = UnitCatalog.categories.flatMap { it.units }.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids are unique")
        UnitCatalog.categories.forEach { c -> assertEquals(c.units.size, c.units.map { it.symbol }.toSet().size, c.id) }
        assertTrue(UnitCatalog.categories.size >= 15)
        assertTrue(UnitCatalog.categories.sumOf { it.units.size } >= 110)
    }

    @Test
    fun referenceValuesInEveryCategory() {
        near(1.609344, conv(1.0, "length:mi", "length:km"))
        near(12.0, conv(1.0, "length:ft", "length:in"))
        near(1.852, conv(1.0, "length:nmi", "length:km"))
        near(4046.8564224, conv(1.0, "area:ac", "area:m²"))
        near(10000.0, conv(1.0, "area:ha", "area:m²"))
        near(3.785411784, conv(1.0, "volume:gal", "volume:L"))
        near(4.54609, conv(1.0, "volume:imp gal", "volume:L"))
        near(0.2365882365, conv(1.0, "volume:cup", "volume:L"))
        near(250.0, conv(1.0, "volume:cup (metric)", "volume:mL"))
        near(3.0, conv(1.0, "volume:tbsp (metric)", "volume:tsp (metric)"))
        near(2.2046226218, conv(1.0, "mass:kg", "mass:lb"))
        near(16.0, conv(1.0, "mass:lb", "mass:oz"), 1e-9)
        near(36.0, conv(10.0, "speed:m/s", "speed:km/h"))
        near(86400.0, conv(1.0, "time:d", "time:s"))
        near(180.0, conv(Math_PI, "angle:rad", "angle:°"))
        near(1.0, conv(1.0, "pressure:atm", "pressure:bar") / 1.01325)
        near(1055.05585262, conv(1.0, "energy:BTU", "energy:J"))
        near(3.6e6, conv(1.0, "energy:kWh", "energy:J"))
        near(4184.0, conv(1.0, "energy:kcal", "energy:J"))
        near(745.69987158227, conv(1.0, "power:hp", "power:W"))
        near(9.80665, conv(1.0, "force:kgf", "force:N"))
        near(1000.0, conv(1.0, "frequency:kHz", "frequency:Hz"))
        near(1.0, conv(60.0, "frequency:rpm", "frequency:Hz"))
    }

    @Test
    fun temperatureAndFuelEconomyAreNotSimpleMultipliers() {
        near(212.0, conv(100.0, "temperature:C", "temperature:F"))
        near(-40.0, conv(-40.0, "temperature:F", "temperature:C"))
        near(273.15, conv(0.0, "temperature:C", "temperature:K"))
        near(-459.67, conv(0.0, "temperature:K", "temperature:F"), 1e-9)
        near(10.0, conv(10.0, "fuel:km/L", "fuel:L/100km"))
        near(5.0, conv(20.0, "fuel:km/L", "fuel:L/100km"))
        near(23.5214583, conv(10.0, "fuel:L/100km", "fuel:mpg"), 1e-6)
        near(1.2009499, conv(1.0, "fuel:mpg", "fuel:mpg (UK)"), 1e-6)
    }

    @Test
    fun bitsAndBytesAndDecimalAndBinaryPrefixesAreKeptApart() {
        near(8.0, conv(1.0, "data:B", "data:bit"))
        near(1000.0, conv(1.0, "data:MB", "data:kB"))
        near(1024.0, conv(1.0, "data:MiB", "data:KiB"))
        near(1.048576, conv(1.0, "data:MiB", "data:MB"))
        near(1.0, conv(8.0, "rate:Mbps", "rate:MB/s"))
        near(125.0, conv(1.0, "rate:Gbps", "rate:MB/s"))
        assertTrue(UnitCatalog.category("data")!!.note.contains("decimal") && UnitCatalog.category("rate")!!.note.contains("small b"))
        assertTrue(u("volume:gal").note.contains("imperial") && u("volume:cup").note.contains("250"))
    }

    @Test
    fun volumeNeverConvertsToWeight() {
        val ids = UnitCatalog.category("volume")!!.units.map { it.id }
        assertTrue(ids.none { it.startsWith("mass:") })
        assertTrue(UnitCatalog.category("volume")!!.note.contains("cannot be turned into a weight"))
        assertTrue(UnitCatalog.search("flour").none { it.unit?.category == "volume" || it.unit?.category == "mass" }, "no ingredient conversion is on offer")
    }

    @Test
    fun resultsAreRoundedToTheChosenPlacesAndTinyOrHugeOnesUseExponents() {
        assertEquals("1.609344", UnitCatalog.show(1.609344, 6))
        assertEquals("1.61", UnitCatalog.show(1.609344, 2))
        assertEquals("2", UnitCatalog.show(1.9999999, 2))
        assertEquals("0", UnitCatalog.show(0.0, 6))
        assertEquals("1.5e-9", UnitCatalog.show(1.5e-9, 6))
        assertEquals("1e20", UnitCatalog.show(1e20, 6))
        assertEquals("—", UnitCatalog.show(Double.POSITIVE_INFINITY, 6))
        assertEquals("-3.5", UnitCatalog.show(-3.5, 4))
    }

    @Test
    fun searchFindsUnitsAndCategoriesByNameSymbolAliasAndASlip() {
        fun first(q: String) = UnitCatalog.search(q).firstOrNull()
        assertEquals("mass:lb", first("lbs")?.unit?.id)
        assertEquals("mass:lb", first("pounds")?.unit?.id)
        assertEquals("speed:km/h", first("kph")?.unit?.id)
        assertEquals("fuel", first("mileage")?.category?.id)
        assertEquals("temperature", first("oven")?.category?.id)
        assertEquals("length:ft", first("feet")?.unit?.id)
        assertEquals("volume:tbsp", first("tablespoon")?.unit?.id)
        assertEquals("area:m²", first("sqm")?.unit?.id)
        assertTrue(UnitCatalog.search("kilogramm").any { it.unit?.id == "mass:kg" })
        assertTrue(UnitCatalog.search("metre").any { it.unit?.id == "length:m" })
        assertTrue(UnitCatalog.search("").isEmpty() && UnitCatalog.search("zzzzzz").isEmpty())
    }

    private companion object { const val Math_PI = kotlin.math.PI }
}
