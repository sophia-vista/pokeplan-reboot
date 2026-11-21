package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Stat
import kotlin.random.Random

object VisitorInheritance {
    const val NATURE_PERCENT = 40
    const val STAT_PERCENT = 40

    const val STAT_FLOOR = 24
    const val RARE_STAT_FLOOR = 28

    const val RARE_PERCENT = 5

    data class Plan(val nature: Nature?, val stat: Stat?, val rareStat: Stat?) {
        val any: Boolean get() = nature != null || stat != null || rareStat != null

        fun labels(): List<String> = buildList {
            nature?.let { add("${it.name.lowercase().replaceFirstChar(Char::uppercase)} nature") }
            stat?.let { add("a strong ${it.name.lowercase().replace('_', ' ')}") }
            rareStat?.let { add("a rare second strength: ${it.name.lowercase().replace('_', ' ')}") }
        }
    }

    private fun hash(text: String): Long = text.fold(1125899906842597L) { h, c -> 31 * h + c.code }

    fun natureOf(visitorId: String): Nature = Nature.entries[(hash("nature:$visitorId") and Long.MAX_VALUE).rem(Nature.entries.size).toInt()]

    fun strongStatOf(visitorId: String): Stat = Stat.entries[(hash("stat:$visitorId") and Long.MAX_VALUE).rem(Stat.entries.size).toInt()]

    fun plan(visitorId: String, eggId: String): Plan {
        if (visitorId.isEmpty()) return Plan(null, null, null)
        val rng = Random(hash("$visitorId>$eggId"))
        val nature = natureOf(visitorId).takeIf { rng.nextInt(100) < NATURE_PERCENT }
        val strong = strongStatOf(visitorId)
        val stat = strong.takeIf { rng.nextInt(100) < STAT_PERCENT }
        val rare = rng.nextInt(100) < RARE_PERCENT
        val second = Stat.entries.filter { it != strong }.let { it[rng.nextInt(it.size)] }
        return if (rare) Plan(nature ?: natureOf(visitorId), stat ?: strong, second) else Plan(nature, stat, null)
    }

    fun apply(p: OwnedPokemon, plan: Plan): OwnedPokemon {
        var out = p
        plan.nature?.let { out = out.copy(nature = it) }
        plan.stat?.let { s -> out = out.copy(ivs = out.ivs.with(s, maxOf(out.ivs[s], STAT_FLOOR))) }
        plan.rareStat?.let { s -> out = out.copy(ivs = out.ivs.with(s, maxOf(out.ivs[s], RARE_STAT_FLOOR))) }
        return out
    }
}
