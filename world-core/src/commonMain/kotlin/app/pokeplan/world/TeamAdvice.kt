package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon

data class TeamPick(val pokemon: OwnedPokemon, val reason: String, val matches: Boolean)

data class TeamRecommendation(val picks: List<TeamPick>, val noMatch: Boolean) {
    val ids: List<String> get() = picks.map { it.pokemon.id }
}

object TeamAdvice {
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

    fun isPoorMatch(dungeon: DungeonDef, members: List<OwnedPokemon>): Boolean {
        if (members.isEmpty()) return false
        val fit = members.count { matching(dungeon, it).isNotEmpty() }
        return fit * 100 < members.size * POOR_MATCH_PERCENT
    }
}
