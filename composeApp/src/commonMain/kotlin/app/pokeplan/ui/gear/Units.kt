package app.pokeplan.ui.gear

/** One unit: its [symbol] and [name], and how to get to and from the kind's base unit (a function, because temperature is not just a factor). */
class UnitDef(val symbol: String, val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double) {
    constructor(symbol: String, name: String, perBase: Double) : this(symbol, name, { it * perBase }, { it / perBase })
}

/** A kind of measure and its units; the first is the base the others convert through. */
class UnitKind(val label: String, val units: List<UnitDef>)

/**
 * The first kinds of the [UnitCatalog] in the shape the first converter used (kept so its tests and callers still work); the catalogue is the
 * source: every definition lives there once.
 */
object Units {
    val kinds: List<UnitKind> = listOf("length", "mass", "volume", "speed", "temperature").map { id ->
        val c = UnitCatalog.category(id)!!
        UnitKind(if (id == "volume") "Volume" else if (id == "mass") "Mass" else c.label, c.units.map { UnitDef(it.symbol, it.name, it.toBase, it.fromBase) })
    }

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double = to.fromBase(from.toBase(value))
}
