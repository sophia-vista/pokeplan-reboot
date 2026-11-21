package app.pokeplan.ui.gear

class UnitDef(val symbol: String, val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double) {
    constructor(symbol: String, name: String, perBase: Double) : this(symbol, name, { it * perBase }, { it / perBase })
}

class UnitKind(val label: String, val units: List<UnitDef>)

object Units {
    val kinds: List<UnitKind> = listOf("length", "mass", "volume", "speed", "temperature").map { id ->
        val c = UnitCatalog.category(id)!!
        UnitKind(if (id == "volume") "Volume" else if (id == "mass") "Mass" else c.label, c.units.map { UnitDef(it.symbol, it.name, it.toBase, it.fromBase) })
    }

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double = to.fromBase(from.toBase(value))
}
