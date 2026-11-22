package app.pokeplan.core.pokemon

import kotlin.random.Random

object CandyRules {
    const val MAX_LEVEL = 100

    const val BASE = 2
    const val CURVE = 300

    const val XP_PER_CANDY = 20

    const val XP_PER_FLOOR = 1

    const val CATCH_UP_GAP = 5

    fun candiesNeeded(level: Int, partnerLevel: Int): Int {
        val full = BASE + level * level / CURVE
        return if (partnerLevel - level > CATCH_UP_GAP) (full + 1) / 2 else full
    }

    fun candiesBetween(from: Int, to: Int, catchUp: Boolean = false): Int =
        (from until to).sumOf { level -> candiesNeeded(level, if (catchUp) level + CATCH_UP_GAP + 1 else level) }
}

object FriendshipRules {
    const val START = 70
    const val MAX = 255
    const val EVOLVE_AT = 220

    enum class Source(val base: Int) {
        PET(2),
        FOCUS_SESSION(3),
        TASK(1),
        BERRY(1),
        LEVEL_UP(1),
        CALM_BREAK(1),
        ROUTINE(1),
        SKILL(1),
        ACTIVITY(1),
        LEDGER(1),
        HABITAT(1),
        FOLLOW_UP(1),
    }

    fun amount(source: Source, style: BondStyle): Int {
        val matches = when (style) {
            BondStyle.COMFORT -> source == Source.PET || source == Source.ROUTINE
            BondStyle.PRESENCE -> source == Source.FOCUS_SESSION || source == Source.CALM_BREAK || source == Source.SKILL || source == Source.ACTIVITY || source == Source.LEDGER || source == Source.HABITAT || source == Source.FOLLOW_UP
            BondStyle.GIFTS -> source == Source.BERRY
            BondStyle.BALANCED -> false
        }
        return when {
            matches -> source.base * 2
            style == BondStyle.BALANCED -> (source.base * 3 + 1) / 2
            else -> source.base
        }
    }

    fun perkMultiplier(friendship: Int): Double = 1.0 + 0.2 * friendship.coerceIn(0, MAX) / MAX
}

object Shiny {
    const val BASE_ODDS = 128

    fun chance(sessionMinutes: Int, perkMultiplier: Double = 1.0): Double =
        (1.0 / BASE_ODDS) * (1.0 + 0.5 * sessionMinutes.coerceIn(0, 240) / 240.0) * perkMultiplier

    fun roll(rng: Random, sessionMinutes: Int, perkMultiplier: Double = 1.0): Boolean =
        rng.nextDouble() < chance(sessionMinutes, perkMultiplier)
}
