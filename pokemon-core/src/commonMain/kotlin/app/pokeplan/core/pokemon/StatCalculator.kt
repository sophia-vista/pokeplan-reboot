package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.BaseStats

object StatCalculator {
    const val MAX_IV = 31
    const val MAX_EV_PER_STAT = 252
    const val MAX_EV_TOTAL = 510

    fun compute(base: BaseStats, ivs: StatSpread, evs: StatSpread, level: Int, nature: Nature): StatSpread {
        require(level in 1..100) { "level must be 1..100" }
        fun stat(s: Stat): Int {
            val core = (2 * base[s] + ivs[s] + evs[s] / 4) * level / 100
            return if (s == Stat.HP) {
                if (base.hp == 1) 1 else core + level + 10
            } else {
                (core + 5) * nature.percent(s) / 100
            }
        }
        return StatSpread(
            stat(Stat.HP), stat(Stat.ATTACK), stat(Stat.DEFENSE),
            stat(Stat.SP_ATTACK), stat(Stat.SP_DEFENSE), stat(Stat.SPEED),
        )
    }
}
