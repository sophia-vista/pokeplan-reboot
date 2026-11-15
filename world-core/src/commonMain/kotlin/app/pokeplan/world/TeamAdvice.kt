package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon

/** One pick of [TeamAdvice.recommend]: who, and the one-line [reason] (the favoured type it matches, else "Strongest left"). */
data class TeamPick(val pokemon: OwnedPokemon, val reason: String, val matches: Boolean)

/** The advice: the [picks] in order, and [noMatch] when nobody among the candidates has a type [dungeon] favours (then the picks are plain strength). */
data class TeamRecommendation(val picks: List<TeamPick>, val noMatch: Boolean) {
    val ids: List<String> get() = picks.map { it.pokemon.id }
}

/**
 * A team that suits a dungeon (WORLD.md 2.3 and 3.2; the expedition formula is untouched). Members of a favoured type come first, then the
 * larger [WorldSim.contribution], then the higher level, then the id, so the advice is the same every time. [WorldSim.bestTeam] (what
 * "Send my strongest" uses) is unchanged: it ranks by contribution alone.
 */
object TeamAdvice {
    /** A team is a poor match when fewer than this share (percent: one of three is enough, one of four is not) of its members have a favoured type. *(tune)* */
    const val POOR_MATCH_PERCENT = 33

    fun matching(dungeon: DungeonDef, p: OwnedPokemon): List<PokemonType> = p.form.types.filter { it in dungeon.favored }

    fun recommend(dungeon: DungeonDef, candidates: List<OwnedPokemon>, size: Int = WorldTeam.MAX_MEMBERS): TeamRecommendation {
        val ranked = candidates.distinctBy { it.id }.sortedWith(
            compareByDescending<OwnedPokemon> { matching(dungeon, it).isNotEmpty() }
                .thenByDescending { WorldSim.contribution(it, dungeon) }
                .thenByDescending { it.level }.thenBy { it.id },
        ).take(size.coerceAtLeast(0))
        val picks = ranked.map { p ->
            val types = matching(dungeon, p)
            TeamPick(p, if (types.isEmpty()) "Strongest left" else "${types.first().label} suits ${dungeon.name}", types.isNotEmpty())
        }
        return TeamRecommendation(picks, noMatch = candidates.none { matching(dungeon, it).isNotEmpty() })
    }

    /** True when [members] are a poor match for [dungeon]: none has a favoured type, or fewer than [POOR_MATCH_PERCENT] percent do. An empty team is not marked. */
    fun isPoorMatch(dungeon: DungeonDef, members: List<OwnedPokemon>): Boolean {
        if (members.isEmpty()) return false
        val fit = members.count { matching(dungeon, it).isNotEmpty() }
        return fit * 100 < members.size * POOR_MATCH_PERCENT
    }
}
