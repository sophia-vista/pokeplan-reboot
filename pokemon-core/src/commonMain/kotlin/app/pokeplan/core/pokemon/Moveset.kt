package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.MoveInfo

/**
 * A Pokémon's moves (docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` 4.1 and 8.3): up to [MAX] active moves, kept on the owned record as move
 * identifiers ([OwnedPokemon.moves]). A record with none stored has its **natural** moves: the last [MAX] level-up moves its form has learned by
 * its level, so every Pokémon has moves, they grow with it, and an old save needs no migration. Choosing moves stores the choice; an empty
 * choice goes back to the natural moves.
 *
 * What a Pokémon may be given: the level-up moves of its form up to its level, its egg moves, and (from [TUTOR_LEVEL]) its tutor and machine
 * moves. The moves only matter where something reads them: WORLD's team power (`BattleIdentity` in world-core) is the one reader today, and
 * the screen that offers the choice says so. Numbers marked *(tune)* are starting values.
 */
object Moveset {
    const val MAX = 4

    /** The level from which tutor and machine moves may be chosen, so a young Pokémon cannot hold the strongest move of the game. *(tune)* */
    const val TUTOR_LEVEL = 30

    /** The last [MAX] distinct level-up moves [p]'s form has learned by its level; the first ones it learns if it has learned none yet. */
    fun natural(p: OwnedPokemon): List<MoveInfo> {
        val set = Dex.learnset(p.form)
        val ids = set.levelUp.filter { it.second <= p.level }.map { it.first }.distinct()
        val chosen = if (ids.isNotEmpty()) ids.takeLast(MAX) else set.levelUp.map { it.first }.distinct().take(MAX)
        return chosen.mapNotNull { Dex.move(it) }
    }

    /**
     * The moves [p] has now: the stored choice when it is still legal for its form (an evolution can leave a move behind), otherwise
     * [natural]. Always at most [MAX], never repeated.
     */
    fun active(p: OwnedPokemon): List<MoveInfo> {
        if (p.moves.isEmpty()) return natural(p)
        val set = Dex.learnset(p.form)
        val kept = p.moves.distinct().mapNotNull { Dex.move(it) }.filter { set.learns(it.id) }.take(MAX)
        return kept.ifEmpty { natural(p) }
    }

    /** Every move [p] may be given now, by name. */
    fun learnable(p: OwnedPokemon): List<MoveInfo> {
        val set = Dex.learnset(p.form)
        val ids = buildSet {
            set.levelUp.filter { it.second <= p.level }.forEach { add(it.first) }
            addAll(set.egg)
            if (p.level >= TUTOR_LEVEL) { addAll(set.tutor); addAll(set.machine) }
        }
        return ids.mapNotNull { Dex.move(it) }.sortedBy { it.name }
    }

    /** Whether [identifiers] is a choice [p] may have: one to [MAX] different moves, all of them [learnable]. An empty list (back to natural) is always allowed. */
    fun canSet(p: OwnedPokemon, identifiers: List<String>): Boolean {
        if (identifiers.isEmpty()) return true
        if (identifiers.size > MAX || identifiers.toSet().size != identifiers.size) return false
        val allowed = learnable(p).map { it.identifier }.toSet()
        return identifiers.all { it in allowed }
    }
}
