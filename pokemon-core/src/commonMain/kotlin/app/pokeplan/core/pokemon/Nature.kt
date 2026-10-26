package app.pokeplan.core.pokemon

import kotlin.random.Random

/** How a Pokémon likes to bond; set by its nature. See "Bond styles" in docs/design.md. */
enum class BondStyle {
    /** Focus sessions and calm breaks with it as buddy give double friendship. */
    PRESENCE,

    /** Petting and Care routines give double friendship. */
    COMFORT,

    /** Berries give double friendship. */
    GIFTS,

    /** No favourite: +50% from every source. */
    BALANCED,
}

/** The 25 natures. A non-neutral nature raises one stat 10% and lowers another 10% (never HP). */
enum class Nature(val raised: Stat?, val lowered: Stat?) {
    HARDY(null, null),
    LONELY(Stat.ATTACK, Stat.DEFENSE),
    BRAVE(Stat.ATTACK, Stat.SPEED),
    ADAMANT(Stat.ATTACK, Stat.SP_ATTACK),
    NAUGHTY(Stat.ATTACK, Stat.SP_DEFENSE),
    BOLD(Stat.DEFENSE, Stat.ATTACK),
    DOCILE(null, null),
    RELAXED(Stat.DEFENSE, Stat.SPEED),
    IMPISH(Stat.DEFENSE, Stat.SP_ATTACK),
    LAX(Stat.DEFENSE, Stat.SP_DEFENSE),
    TIMID(Stat.SPEED, Stat.ATTACK),
    HASTY(Stat.SPEED, Stat.DEFENSE),
    SERIOUS(null, null),
    JOLLY(Stat.SPEED, Stat.SP_ATTACK),
    NAIVE(Stat.SPEED, Stat.SP_DEFENSE),
    MODEST(Stat.SP_ATTACK, Stat.ATTACK),
    MILD(Stat.SP_ATTACK, Stat.DEFENSE),
    QUIET(Stat.SP_ATTACK, Stat.SPEED),
    BASHFUL(null, null),
    RASH(Stat.SP_ATTACK, Stat.SP_DEFENSE),
    CALM(Stat.SP_DEFENSE, Stat.ATTACK),
    GENTLE(Stat.SP_DEFENSE, Stat.DEFENSE),
    SASSY(Stat.SP_DEFENSE, Stat.SPEED),
    CAREFUL(Stat.SP_DEFENSE, Stat.SP_ATTACK),
    QUIRKY(null, null);

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val isNeutral: Boolean get() = raised == null

    /** Percent multiplier for [stat]: 110, 90 or 100. */
    fun percent(stat: Stat): Int = when (stat) {
        raised -> 110
        lowered -> 90
        else -> 100
    }

    /** Attack and Speed natures like company, Defense and Sp. Def like comfort, Sp. Atk likes gifts. *(tune)* */
    val bondStyle: BondStyle
        get() = when (raised) {
            Stat.ATTACK, Stat.SPEED -> BondStyle.PRESENCE
            Stat.DEFENSE, Stat.SP_DEFENSE -> BondStyle.COMFORT
            Stat.SP_ATTACK -> BondStyle.GIFTS
            else -> BondStyle.BALANCED
        }

    companion object {
        fun random(rng: Random): Nature = entries[rng.nextInt(entries.size)]
    }
}
