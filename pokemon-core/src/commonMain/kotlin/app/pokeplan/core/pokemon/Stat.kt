package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.BaseStats

enum class Stat(val label: String) {
    HP("HP"),
    ATTACK("Attack"),
    DEFENSE("Defense"),
    SP_ATTACK("Sp. Atk"),
    SP_DEFENSE("Sp. Def"),
    SPEED("Speed"),
}

operator fun BaseStats.get(stat: Stat): Int = when (stat) {
    Stat.HP -> hp
    Stat.ATTACK -> attack
    Stat.DEFENSE -> defense
    Stat.SP_ATTACK -> spAttack
    Stat.SP_DEFENSE -> spDefense
    Stat.SPEED -> speed
}

/** Six per-stat numbers: IVs, EVs or computed stats. */
data class StatSpread(
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val spAttack: Int = 0,
    val spDefense: Int = 0,
    val speed: Int = 0,
) {
    operator fun get(stat: Stat): Int = when (stat) {
        Stat.HP -> hp
        Stat.ATTACK -> attack
        Stat.DEFENSE -> defense
        Stat.SP_ATTACK -> spAttack
        Stat.SP_DEFENSE -> spDefense
        Stat.SPEED -> speed
    }

    fun with(stat: Stat, value: Int): StatSpread = when (stat) {
        Stat.HP -> copy(hp = value)
        Stat.ATTACK -> copy(attack = value)
        Stat.DEFENSE -> copy(defense = value)
        Stat.SP_ATTACK -> copy(spAttack = value)
        Stat.SP_DEFENSE -> copy(spDefense = value)
        Stat.SPEED -> copy(speed = value)
    }

    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed

    val values: List<Int> get() = Stat.entries.map { this[it] }

    companion object {
        val ZERO = StatSpread()

        fun of(value: Int) = StatSpread(value, value, value, value, value, value)

        /** Each stat independently random in 0..[max]. */
        fun random(max: Int, rng: kotlin.random.Random) = StatSpread(
            rng.nextInt(max + 1), rng.nextInt(max + 1), rng.nextInt(max + 1),
            rng.nextInt(max + 1), rng.nextInt(max + 1), rng.nextInt(max + 1),
        )
    }
}
