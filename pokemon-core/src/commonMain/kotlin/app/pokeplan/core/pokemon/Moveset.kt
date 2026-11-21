package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.MoveInfo

object Moveset {
    const val MAX = 4

    const val TUTOR_LEVEL = 30

    fun natural(p: OwnedPokemon): List<MoveInfo> {
        val set = Dex.learnset(p.form)
        val ids = set.levelUp.filter { it.second <= p.level }.map { it.first }.distinct()
        val chosen = if (ids.isNotEmpty()) ids.takeLast(MAX) else set.levelUp.map { it.first }.distinct().take(MAX)
        return chosen.mapNotNull { Dex.move(it) }
    }

    fun active(p: OwnedPokemon): List<MoveInfo> {
        if (p.moves.isEmpty()) return natural(p)
        val set = Dex.learnset(p.form)
        val kept = p.moves.distinct().mapNotNull { Dex.move(it) }.filter { set.learns(it.id) }.take(MAX)
        return kept.ifEmpty { natural(p) }
    }

    fun learnable(p: OwnedPokemon): List<MoveInfo> {
        val set = Dex.learnset(p.form)
        val ids = buildSet {
            set.levelUp.filter { it.second <= p.level }.forEach { add(it.first) }
            addAll(set.egg)
            if (p.level >= TUTOR_LEVEL) { addAll(set.tutor); addAll(set.machine) }
        }
        return ids.mapNotNull { Dex.move(it) }.sortedBy { it.name }
    }

    fun canSet(p: OwnedPokemon, identifiers: List<String>): Boolean {
        if (identifiers.isEmpty()) return true
        if (identifiers.size > MAX || identifiers.toSet().size != identifiers.size) return false
        val allowed = learnable(p).map { it.identifier }.toSet()
        return identifiers.all { it in allowed }
    }
}
