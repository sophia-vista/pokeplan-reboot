package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Stat
import kotlin.random.Random

/**
 * What an egg left by a befriended visitor may take from it (docs/pokelife `MODULES/CARE.md` section 6.6): light, probabilistic, and
 * the hatchling keeps its own build. A visitor has a stable temperament (a nature and a strong stat) that comes from its id alone, so
 * the same visitor always has the same one; what a given egg takes is decided by the visitor and the egg together, so it is the same
 * on a retry. Never touches shininess, the ability, the species or the level, and there is no pairing or choosing: this is a gift from
 * a visit, not breeding.
 */
object VisitorInheritance {
    /** Chance the egg takes the visitor's nature, and chance it takes its strong stat. *(tune)* */
    const val NATURE_PERCENT = 40
    const val STAT_PERCENT = 40

    /** The Individual Value floor of an inherited strong stat, and of the second one in the rare case (a floor, never above what the egg rolled). *(tune)* */
    const val STAT_FLOOR = 24
    const val RARE_STAT_FLOOR = 28

    /** Chance of the rare special trait: a second strong stat, along with the other two inherited traits. *(tune)* */
    const val RARE_PERCENT = 5

    /** What an egg takes: any of these may be null. */
    data class Plan(val nature: Nature?, val stat: Stat?, val rareStat: Stat?) {
        val any: Boolean get() = nature != null || stat != null || rareStat != null

        /** The traits in words, for the hatchling's origin note. */
        fun labels(): List<String> = buildList {
            nature?.let { add("${it.name.lowercase().replaceFirstChar(Char::uppercase)} nature") }
            stat?.let { add("a strong ${it.name.lowercase().replace('_', ' ')}") }
            rareStat?.let { add("a rare second strength: ${it.name.lowercase().replace('_', ' ')}") }
        }
    }

    private fun hash(text: String): Long = text.fold(1125899906842597L) { h, c -> 31 * h + c.code }

    /** The temperament of the visitor with [visitorId]: the same every time. */
    fun natureOf(visitorId: String): Nature = Nature.entries[(hash("nature:$visitorId") and Long.MAX_VALUE).rem(Nature.entries.size).toInt()]

    fun strongStatOf(visitorId: String): Stat = Stat.entries[(hash("stat:$visitorId") and Long.MAX_VALUE).rem(Stat.entries.size).toInt()]

    /** What the egg [eggId] from the visitor [visitorId] takes; nothing for a visitor without an id (an old save's friend). */
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

    /** [p] with the plan applied: the nature as given, and the IV of an inherited stat raised to its floor (never lowered). */
    fun apply(p: OwnedPokemon, plan: Plan): OwnedPokemon {
        var out = p
        plan.nature?.let { out = out.copy(nature = it) }
        plan.stat?.let { s -> out = out.copy(ivs = out.ivs.with(s, maxOf(out.ivs[s], STAT_FLOOR))) }
        plan.rareStat?.let { s -> out = out.copy(ivs = out.ivs.with(s, maxOf(out.ivs[s], RARE_STAT_FLOOR))) }
        return out
    }
}
