package app.pokeplan.world

/**
 * What a floor does to a team (WORLD.md 3.6): it is **cleared**, a **setback**, or the team **retreats**. The rule is a plain comparison of the team's power
 * (the sum of its members' levels, 20% more for a favored type: [WorldSim.power]) with what the floor asks, so the player can read it in advance and the same
 * team on the same floor always has the same outcome (no dice; deterministic whatever the settle frequency).
 *
 * - **Required power** of floor f in a dungeon of F floors: [recommended] (set by the rank the dungeon needs) times f/F, so the top floor asks the most.
 * - **Cleared** (power at least [CLEAR] of that): the floor pays in full, as before.
 * - **Setback** (at least [RETREAT] but under [CLEAR]): the floor is not cleared. The team keeps going, but earns half the Exploration XP and Poké of the floor, and
 *   the floor gives no treasure, find, milestone, side path or choice, does not count as a clear of the dungeon, and does not move a job forward (berries still count).
 * - **Retreat** (under [RETREAT]): the team turns back and comes home safely: the expedition ends at that floor and the report says why. Floors already climbed keep
 *   everything they paid. Only dungeons of Bronze rank and above can make a team retreat; the first floor and a Normal-rank dungeon are never worse than a setback.
 *
 * Nothing is lost, injured or locked: no Pokémon is hurt, no item destroyed, no timer started, and the team can be sent again at once (WORLD.md 3.6). The numbers
 * are *(tune)*; [VERSION] changes with them, so a report can say which rule it was settled under.
 */
object Outcomes {
    /** The version of this rule; bump it with any number below. */
    const val VERSION = 1

    /** The share of a floor's required power a team needs to clear it, and under which it retreats. *(tune)* */
    const val CLEAR = 0.4
    const val RETREAT = 0.2

    /** The power a team should bring to the top floor of a dungeon needing [rank]. *(tune: close to what the Defeat jobs ask at Bronze, Gold and Platinum, a bit under)* */
    fun recommended(rank: ExplorerRank): Int = when (rank) {
        ExplorerRank.NORMAL -> 24
        ExplorerRank.BRONZE -> 90
        ExplorerRank.SILVER -> 150
        ExplorerRank.GOLD -> 220
        ExplorerRank.PLATINUM -> 300
        ExplorerRank.DIAMOND -> 400
    }

    /** The power floor [floor] of [dungeon] asks for to be cleared in full: [recommended] scaled by how deep the floor is. */
    fun required(dungeon: DungeonDef, floor: Int): Double = recommended(dungeon.needs) * floor.coerceIn(1, dungeon.floors).toDouble() / dungeon.floors

    enum class Outcome { CLEARED, SETBACK, RETREAT }

    /** The outcome of floor [floor] of [dungeon] for a team of [power]. */
    fun of(power: Double, dungeon: DungeonDef, floor: Int): Outcome {
        val ratio = power / required(dungeon, floor)
        return when {
            floor <= 1 || ratio >= CLEAR -> Outcome.CLEARED
            ratio >= RETREAT || dungeon.needs <= ExplorerRank.NORMAL -> Outcome.SETBACK
            else -> Outcome.RETREAT
        }
    }

    /** The deepest floor a team of [power] still clears in full in [dungeon] (the top floor when the whole dungeon is within reach). */
    fun clearsUpTo(power: Double, dungeon: DungeonDef): Int = (1..dungeon.floors).lastOrNull { of(power, dungeon, it) == Outcome.CLEARED && (1..it).all { f -> of(power, dungeon, f) == Outcome.CLEARED } } ?: 1

    /** The first floor where a team of [power] would turn back, or null when it never would. */
    fun retreatsAt(power: Double, dungeon: DungeonDef): Int? = (1..dungeon.floors).firstOrNull { of(power, dungeon, it) == Outcome.RETREAT }

    /** A sentence for the team page: how far this team clears, and what happens beyond. */
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
