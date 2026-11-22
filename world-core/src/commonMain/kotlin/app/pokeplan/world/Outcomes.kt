package app.pokeplan.world

object Outcomes {
    const val VERSION = 2

    const val CLEAR = 0.4
    const val RETREAT = 0.2

    fun recommended(rank: ExplorerRank): Int = when (rank) {
        ExplorerRank.NORMAL -> 24
        ExplorerRank.BRONZE -> 90
        ExplorerRank.SILVER -> 150
        ExplorerRank.GOLD -> 220
        ExplorerRank.PLATINUM -> 300
        ExplorerRank.DIAMOND -> 400
    }

    fun required(dungeon: DungeonDef, floor: Int): Double = recommended(dungeon.needs) * floor.coerceIn(1, dungeon.floors).toDouble() / dungeon.floors

    enum class Outcome { CLEARED, SETBACK, RETREAT }

    fun of(power: Double, dungeon: DungeonDef, floor: Int): Outcome {
        if (floor <= 1 || dungeon.needs <= ExplorerRank.NORMAL) return Outcome.CLEARED
        val ratio = power / required(dungeon, floor)
        return when {
            ratio >= CLEAR -> Outcome.CLEARED
            ratio >= RETREAT -> Outcome.SETBACK
            else -> Outcome.RETREAT
        }
    }

    fun clearsUpTo(power: Double, dungeon: DungeonDef): Int = (1..dungeon.floors).lastOrNull { of(power, dungeon, it) == Outcome.CLEARED && (1..it).all { f -> of(power, dungeon, f) == Outcome.CLEARED } } ?: 1

    fun retreatsAt(power: Double, dungeon: DungeonDef): Int? = (1..dungeon.floors).firstOrNull { of(power, dungeon, it) == Outcome.RETREAT }

    fun explain(power: Double, dungeon: DungeonDef): String {
        val clear = clearsUpTo(power, dungeon)
        val back = retreatsAt(power, dungeon)
        return when {
            clear >= dungeon.floors -> "This team clears every floor of ${dungeon.name} in full."
            back != null -> "This team clears floors up to $clear in full, has setbacks after that (half the Poké and Exploration XP, no treasure or finds) and turns back safely at floor $back."
            else -> "This team clears floors up to $clear in full and has setbacks after that (half the Poké and Exploration XP, no treasure or finds)."
        }
    }
}
