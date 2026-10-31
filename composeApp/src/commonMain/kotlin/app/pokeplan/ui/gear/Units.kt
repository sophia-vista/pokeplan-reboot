package app.pokeplan.ui.gear

/** One unit: its [symbol] and [name], and how to get to and from the kind's base unit (a function, because temperature is not just a factor). */
class UnitDef(val symbol: String, val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double) {
    constructor(symbol: String, name: String, perBase: Double) : this(symbol, name, { it * perBase }, { it / perBase })
}

/** A kind of measure and its units; the first is the base the others convert through. */
class UnitKind(val label: String, val units: List<UnitDef>)

/** The Calculator tool's unit converter: [convert] goes through the kind's base unit. */
object Units {
    val kinds: List<UnitKind> = listOf(
        UnitKind("Length", listOf(
            UnitDef("m", "metre", 1.0), UnitDef("km", "kilometre", 1000.0), UnitDef("cm", "centimetre", 0.01), UnitDef("mm", "millimetre", 0.001),
            UnitDef("in", "inch", 0.0254), UnitDef("ft", "foot", 0.3048), UnitDef("yd", "yard", 0.9144), UnitDef("mi", "mile", 1609.344),
        )),
        UnitKind("Mass", listOf(
            UnitDef("kg", "kilogram", 1.0), UnitDef("g", "gram", 0.001), UnitDef("mg", "milligram", 0.000001), UnitDef("lb", "pound", 0.45359237), UnitDef("oz", "ounce", 0.028349523125),
        )),
        UnitKind("Volume", listOf(
            UnitDef("L", "litre", 1.0), UnitDef("mL", "millilitre", 0.001), UnitDef("gal", "US gallon", 3.785411784), UnitDef("cup", "US cup", 0.2365882365), UnitDef("fl oz", "US fluid ounce", 0.0295735295625),
        )),
        UnitKind("Speed", listOf(
            UnitDef("m/s", "metre per second", 1.0), UnitDef("km/h", "kilometre per hour", 1000.0 / 3600.0), UnitDef("mph", "mile per hour", 0.44704), UnitDef("kn", "knot", 1852.0 / 3600.0),
        )),
        UnitKind("Temperature", listOf(
            UnitDef("C", "Celsius", { it }, { it }),
            UnitDef("F", "Fahrenheit", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
            UnitDef("K", "Kelvin", { it - 273.15 }, { it + 273.15 }),
        )),
    )

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double = to.fromBase(from.toBase(value))
}
