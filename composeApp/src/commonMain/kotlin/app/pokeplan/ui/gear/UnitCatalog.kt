package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * One unit of the catalogue. [id] is stable (`category:symbol`, saved with favourites, never a translated label). [toBase] and [fromBase] go
 * to and from the category's base unit, as functions because temperature and fuel economy are not a simple factor. [note] explains an unit that is
 * easy to mistake (a US gallon is not an imperial one, a decimal kilobyte is not a binary one).
 */
class UnitInfo(
    val category: String, val symbol: String, val name: String, val aliases: List<String>,
    val toBase: (Double) -> Double, val fromBase: (Double) -> Double, val note: String = "",
) {
    val id: String get() = "$category:$symbol"
}

class UnitCategory(val id: String, val label: String, val units: List<UnitInfo>, val note: String = "", val aliases: List<String> = emptyList())

/** A search result: a category, and a unit in it when the query named one. */
data class UnitHit(val category: UnitCategory, val unit: UnitInfo?)

/**
 * The unit catalogue (data, not screens): categories of units with the factors to a base unit, every definition tested against reference values.
 * Cooking quantities by volume are here with the cup and spoon standard named; **volume and weight are never converted into each other**
 * (a cup of flour and a cup of honey weigh differently), and bits and bytes, decimal and binary prefixes are separate units.
 */
object UnitCatalog {
    private fun cat(id: String, label: String, note: String = "", aliases: List<String> = emptyList(), build: Builder.() -> Unit): UnitCategory {
        val b = Builder(id).apply(build)
        return UnitCategory(id, label, b.units, note, aliases)
    }

    private class Builder(private val category: String) {
        val units = mutableListOf<UnitInfo>()

        /** A unit that is [perBase] of the base unit... as a factor: 1 of this = [perBase] base units. */
        fun u(symbol: String, name: String, perBase: Double, vararg aliases: String, note: String = "") {
            units += UnitInfo(category, symbol, name, aliases.toList(), { it * perBase }, { it / perBase }, note)
        }

        fun fn(symbol: String, name: String, toBase: (Double) -> Double, fromBase: (Double) -> Double, vararg aliases: String, note: String = "") {
            units += UnitInfo(category, symbol, name, aliases.toList(), toBase, fromBase, note)
        }
    }

    val categories: List<UnitCategory> = listOf(
        cat("length", "Length", aliases = listOf("distance", "height", "width")) {
            u("m", "metre", 1.0, "meter"); u("km", "kilometre", 1000.0, "kilometer"); u("cm", "centimetre", 0.01, "centimeter"); u("mm", "millimetre", 0.001, "millimeter")
            u("µm", "micrometre", 1e-6, "micron", "um"); u("nm", "nanometre", 1e-9)
            u("in", "inch", 0.0254, "inches"); u("ft", "foot", 0.3048, "feet"); u("yd", "yard", 0.9144); u("mi", "mile", 1609.344)
            u("nmi", "nautical mile", 1852.0, note = "Used at sea and in the air: 1,852 metres.")
        },
        cat("area", "Area", aliases = listOf("land", "floor")) {
            u("m²", "square metre", 1.0, "m2", "sqm"); u("km²", "square kilometre", 1e6, "km2"); u("cm²", "square centimetre", 1e-4, "cm2"); u("mm²", "square millimetre", 1e-6, "mm2")
            u("ha", "hectare", 1e4, "hectares"); u("ft²", "square foot", 0.09290304, "ft2", "sqft"); u("in²", "square inch", 0.00064516, "in2"); u("yd²", "square yard", 0.83612736, "yd2")
            u("ac", "acre", 4046.8564224, "acres"); u("mi²", "square mile", 2589988.110336, "mi2")
        },
        cat("volume", "Volume and cooking", note = "A volume cannot be turned into a weight without knowing the ingredient (a cup of flour is not a cup of honey), so there is no such conversion here.", aliases = listOf("capacity", "cooking", "recipe", "liquid")) {
            u("L", "litre", 1.0, "liter", "l"); u("mL", "millilitre", 0.001, "milliliter", "ml", "cc", "cm³"); u("m³", "cubic metre", 1000.0, "m3")
            u("tsp", "teaspoon (US)", 0.00492892159375, "teaspoon", note = "US teaspoon, 4.93 mL. The metric teaspoon is 5 mL.")
            u("tbsp", "tablespoon (US)", 0.01478676478125, "tablespoon", note = "US tablespoon, 14.79 mL. The metric tablespoon is 15 mL.")
            u("fl oz", "fluid ounce (US)", 0.0295735295625, "fluid ounce", "oz fl")
            u("cup", "cup (US)", 0.2365882365, "cups", note = "US cup, 236.6 mL. The metric cup is 250 mL.")
            u("pt", "pint (US)", 0.473176473, "pint"); u("qt", "quart (US)", 0.946352946, "quart"); u("gal", "gallon (US)", 3.785411784, "gallon", note = "US gallon, 3.785 L. The imperial gallon is 4.546 L.")
            u("tsp (metric)", "teaspoon (metric, 5 mL)", 0.005); u("tbsp (metric)", "tablespoon (metric, 15 mL)", 0.015); u("cup (metric)", "cup (metric, 250 mL)", 0.25)
            u("imp fl oz", "fluid ounce (imperial)", 0.0284130625); u("imp pt", "pint (imperial)", 0.56826125); u("imp gal", "gallon (imperial)", 4.54609, note = "The imperial gallon is 4.546 L; the US gallon is 3.785 L.")
        },
        cat("mass", "Mass and weight", aliases = listOf("weight")) {
            u("kg", "kilogram", 1.0, "kilo", "kilograms"); u("g", "gram", 0.001, "grams"); u("mg", "milligram", 1e-6); u("µg", "microgram", 1e-9, "ug"); u("t", "tonne", 1000.0, "metric ton")
            u("lb", "pound", 0.45359237, "lbs", "pounds"); u("oz", "ounce", 0.028349523125, "ounces"); u("st", "stone", 6.35029318, "stones"); u("ct", "carat", 0.0002, "carats")
        },
        cat("temperature", "Temperature", aliases = listOf("oven", "celsius", "fahrenheit", "kelvin")) {
            fn("C", "Celsius", { it }, { it }, "°c", "centigrade")
            fn("F", "Fahrenheit", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }, "°f")
            fn("K", "Kelvin", { it - 273.15 }, { it + 273.15 })
        },
        cat("speed", "Speed", aliases = listOf("velocity", "pace")) {
            u("m/s", "metre per second", 1.0); u("km/h", "kilometre per hour", 1000.0 / 3600.0, "kph", "kmh"); u("mph", "mile per hour", 0.44704)
            u("kn", "knot", 1852.0 / 3600.0, "knots"); u("ft/s", "foot per second", 0.3048, "fps")
        },
        cat("time", "Time", aliases = listOf("duration")) {
            u("ms", "millisecond", 0.001); u("s", "second", 1.0, "sec"); u("min", "minute", 60.0, "minutes"); u("h", "hour", 3600.0, "hours", "hr")
            u("d", "day", 86400.0, "days"); u("wk", "week", 604800.0, "weeks")
            u("mo", "month (average)", 2629746.0, "months", note = "An average month, 30.44 days (a real month is 28 to 31).")
            u("yr", "year (average)", 31556952.0, "years", note = "An average year, 365.2425 days.")
        },
        cat("angle", "Angle") {
            u("°", "degree", 1.0, "deg", "degrees"); u("rad", "radian", 180.0 / PI, "radians"); u("grad", "gradian", 0.9, "gon"); u("turn", "turn", 360.0, "revolution")
            u("′", "arcminute", 1.0 / 60.0, "arcmin"); u("″", "arcsecond", 1.0 / 3600.0, "arcsec")
        },
        cat("data", "Data storage", note = "Eight bits make a byte. kB, MB and GB are decimal (1,000); KiB, MiB and GiB are binary (1,024).", aliases = listOf("storage", "memory", "file size")) {
            u("bit", "bit", 1.0, "bits"); u("B", "byte", 8.0, "bytes"); u("kB", "kilobyte (decimal)", 8e3); u("MB", "megabyte (decimal)", 8e6); u("GB", "gigabyte (decimal)", 8e9); u("TB", "terabyte (decimal)", 8e12)
            u("KiB", "kibibyte (binary)", 8.0 * 1024); u("MiB", "mebibyte (binary)", 8.0 * 1024 * 1024); u("GiB", "gibibyte (binary)", 8.0 * 1024 * 1024 * 1024); u("TiB", "tebibyte (binary)", 8.0 * 1024 * 1024 * 1024 * 1024)
        },
        cat("rate", "Data rate", note = "A small b is a bit and a capital B is a byte: 8 Mbps is 1 MB/s.", aliases = listOf("bandwidth", "internet", "speed test")) {
            u("bps", "bit per second", 1.0); u("kbps", "kilobit per second", 1e3); u("Mbps", "megabit per second", 1e6); u("Gbps", "gigabit per second", 1e9)
            u("B/s", "byte per second", 8.0); u("kB/s", "kilobyte per second", 8e3); u("MB/s", "megabyte per second", 8e6); u("GB/s", "gigabyte per second", 8e9)
        },
        cat("pressure", "Pressure", aliases = listOf("barometer", "tyre", "tire")) {
            u("Pa", "pascal", 1.0); u("kPa", "kilopascal", 1e3); u("hPa", "hectopascal", 100.0, "millibar"); u("bar", "bar", 1e5); u("mbar", "millibar", 100.0)
            u("atm", "atmosphere", 101325.0); u("mmHg", "millimetre of mercury", 133.322387415, "torr"); u("inHg", "inch of mercury", 3386.389); u("psi", "pound per square inch", 6894.757293168)
        },
        cat("energy", "Energy", aliases = listOf("calories", "electricity")) {
            u("J", "joule", 1.0); u("kJ", "kilojoule", 1e3); u("cal", "calorie (thermochemical)", 4.184); u("kcal", "kilocalorie (food Calorie)", 4184.0, "Cal", "calories")
            u("Wh", "watt-hour", 3600.0); u("kWh", "kilowatt-hour", 3.6e6, "kwh"); u("BTU", "British thermal unit", 1055.05585262, "btu")
        },
        cat("power", "Power") {
            u("W", "watt", 1.0, "watts"); u("kW", "kilowatt", 1e3); u("MW", "megawatt", 1e6); u("hp", "horsepower (mechanical)", 745.69987158227, "horsepower"); u("PS", "horsepower (metric)", 735.49875, "cv")
        },
        cat("force", "Force") {
            u("N", "newton", 1.0); u("kN", "kilonewton", 1e3); u("lbf", "pound-force", 4.4482216152605); u("kgf", "kilogram-force", 9.80665)
        },
        cat("frequency", "Frequency") {
            u("Hz", "hertz", 1.0); u("kHz", "kilohertz", 1e3); u("MHz", "megahertz", 1e6); u("GHz", "gigahertz", 1e9); u("rpm", "revolution per minute", 1.0 / 60.0)
        },
        cat("fuel", "Fuel economy", note = "More distance per fuel is better with km/L and mpg and worse with L/100km, which is the other way round.", aliases = listOf("mileage", "consumption", "gas")) {
            u("km/L", "kilometre per litre", 1.0)
            fn("L/100km", "litre per 100 km", { if (it == 0.0) Double.POSITIVE_INFINITY else 100.0 / it }, { if (it == 0.0) Double.POSITIVE_INFINITY else 100.0 / it })
            u("mpg", "mile per US gallon", 1.609344 / 3.785411784, "mpg us", note = "US gallon.")
            u("mpg (UK)", "mile per imperial gallon", 1.609344 / 4.54609, note = "Imperial gallon: a UK mpg is about 1.2 times a US one.")
        },
    )

    private val byId: Map<String, UnitInfo> by lazy { categories.flatMap { it.units }.associateBy { it.id } }

    fun category(id: String): UnitCategory? = categories.firstOrNull { it.id == id }
    fun unit(id: String): UnitInfo? = byId[id]

    /** [value] of [from] in [to] (both in one category), going through the category's base unit. */
    fun convert(value: Double, from: UnitInfo, to: UnitInfo): Double = to.fromBase(from.toBase(value))

    /**
     * [value] as a result: rounded to [decimals] places (0 to 12), without a tail of zeros, and in `1.5e-8` style when it would otherwise show as zero
     * or has too many digits.
     */
    fun show(value: Double, decimals: Int): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val ax = abs(value)
        if (ax != 0.0 && (ax < 10.0.pow(-decimals.coerceAtMost(12)) / 2 || ax >= 1e15)) return CalcEngine.format(value)
        val scale = 10.0.pow(decimals.coerceIn(0, 12))
        return CalcEngine.format(round(value * scale) / scale)
    }

    /** Categories and units matching [query] (names, symbols and aliases, ignoring case and one wrong letter in a longer word), best matches first. */
    fun search(query: String): List<UnitHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<Pair<Int, UnitHit>>()
        for (c in categories) {
            val cs = score(q, listOf(c.label.lowercase(), c.id) + c.aliases.map { it.lowercase() })
            if (cs > 0) hits += cs to UnitHit(c, null)
            for (u in c.units) {
                val us = score(q, listOf(u.symbol.lowercase(), u.name.lowercase()) + u.aliases.map { it.lowercase() })
                if (us > 0) hits += us to UnitHit(c, u)
            }
        }
        return hits.sortedByDescending { it.first }.map { it.second }.take(12)
    }

    /** 100 for an exact match, 60 for a start, 40 for a part, 20 for one wrong letter in a word of four or more (0 for none). */
    private fun score(q: String, names: List<String>): Int {
        var best = 0
        for (n in names) {
            val s = when {
                n == q -> 100
                n.startsWith(q) -> 60
                q.length >= 2 && n.contains(q) -> 40
                q.length >= 4 && n.split(' ', '(', ')', '/').any { w -> w.length >= 4 && withinOne(q, w) } -> 20
                else -> 0
            }
            if (s > best) best = s
        }
        return best
    }

    private fun withinOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            edits++
            if (edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
