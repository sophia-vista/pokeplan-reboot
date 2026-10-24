package app.pokeplan.core.pokemon

import kotlin.random.Random

/** Rare Candy levelling. */
object CandyRules {
    const val MAX_LEVEL = 100

    /**
     * Rare Candies for one level: [BASE] plus level² / [CURVE], rounded down, the shape of the games' Medium Fast experience
     * group (each level needs more the higher it is): 2 candies up to level 12, 8 at 30, 18 at 50, 67 at 99. *(tune)*
     */
    const val BASE = 2
    const val CURVE = 150

    /** A Pokémon more than this many levels below your partner levels up on half the candies (rounded up). *(tune)* */
    const val CATCH_UP_GAP = 5

    fun candiesNeeded(level: Int, partnerLevel: Int): Int {
        val full = BASE + level * level / CURVE
        return if (partnerLevel - level > CATCH_UP_GAP) (full + 1) / 2 else full
    }

    /** Rare Candies to go from level [from] to level [to], with or without the catch-up discount. */
    fun candiesBetween(from: Int, to: Int, catchUp: Boolean = false): Int =
        (from until to).sumOf { level -> candiesNeeded(level, if (catchUp) level + CATCH_UP_GAP + 1 else level) }
}

/** Where friendship comes from, and what it does. Daily caps are applied by the caller. */
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
    }

    /** Friendship gained from [source]: doubled when it matches the bond style, +50% (rounded up) for balanced natures. */
    fun amount(source: Source, style: BondStyle): Int {
        val matches = when (style) {
            BondStyle.COMFORT -> source == Source.PET
            BondStyle.PRESENCE -> source == Source.FOCUS_SESSION
            BondStyle.GIFTS -> source == Source.BERRY
            BondStyle.BALANCED -> false
        }
        return when {
            matches -> source.base * 2
            style == BondStyle.BALANCED -> (source.base * 3 + 1) / 2
            else -> source.base
        }
    }

    /** Perk strength multiplier: 1.0 at no friendship up to 1.2 at the maximum. */
    fun perkMultiplier(friendship: Int): Double = 1.0 + 0.2 * friendship.coerceIn(0, MAX) / MAX
}

/** Shiny odds. */
object Shiny {
    const val BASE_ODDS = 128

    /**
     * Chance that a hatched Pokémon is shiny: 1 in [BASE_ODDS], up to 50% better for a 4 hour session, times any perk bonus.
     * *(tune)*
     */
    fun chance(sessionMinutes: Int, perkMultiplier: Double = 1.0): Double =
        (1.0 / BASE_ODDS) * (1.0 + 0.5 * sessionMinutes.coerceIn(0, 240) / 240.0) * perkMultiplier

    fun roll(rng: Random, sessionMinutes: Int, perkMultiplier: Double = 1.0): Boolean =
        rng.nextDouble() < chance(sessionMinutes, perkMultiplier)
}
