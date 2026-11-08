package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovesetTest {
    private fun mon(species: Int, level: Int) = pokemon(species, level = level)

    @Test
    fun everyPokemonHasNaturalMovesThatGrowWithItsLevel() {
        for (s in Dex.species) {
            val p = mon(s.id, 50)
            val moves = p.activeMoves
            assertTrue(moves.isNotEmpty() && moves.size <= Moveset.MAX, s.name)
            assertEquals(moves.map { it.id }.distinct(), moves.map { it.id }, s.name)
        }
        val young = mon(25, 3).activeMoves.map { it.id }.toSet()
        val grown = mon(25, 60).activeMoves.map { it.id }.toSet()
        assertTrue(young != grown, "Pikachu learns new moves as it grows")
    }

    @Test
    fun aChoiceIsKeptOnlyForMovesThePokemonMayHave() {
        val p = mon(25, 40)
        val all = Moveset.learnable(p)
        assertTrue(all.isNotEmpty() && all.map { it.name } == all.map { it.name }.sorted())
        val pick = all.take(2).map { it.identifier }
        assertTrue(Moveset.canSet(p, pick))
        assertEquals(pick, p.withMoves(pick).activeMoves.map { it.identifier })
        assertTrue(Moveset.canSet(p, emptyList()), "empty goes back to natural")
        assertFalse(Moveset.canSet(p, listOf(pick[0], pick[0])), "no repeats")
        assertFalse(Moveset.canSet(p, all.take(5).map { it.identifier }), "no more than four")
        assertFalse(Moveset.canSet(p, listOf("not-a-move")))
        // A young Pokémon cannot hold what only the machines and tutors teach.
        val machineOnly = Dex.learnset(p.form).machine.mapNotNull { Dex.move(it) }.firstOrNull { m -> Dex.learnset(p.form).levelUp.none { it.first == m.id } && m.id !in Dex.learnset(p.form).egg }
        if (machineOnly != null) {
            assertFalse(Moveset.canSet(mon(25, 5), listOf(machineOnly.identifier)))
            assertTrue(Moveset.canSet(mon(25, Moveset.TUTOR_LEVEL), listOf(machineOnly.identifier)))
        }
    }

    @Test
    fun anOldSaveReadsAsNaturalAndTheChoiceSurvivesTheSave() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val p = mon(25, 40)
        val text = json.encodeToString(OwnedPokemon.serializer(), p)
        assertFalse("\"moves\"" in text, "nothing stored until a choice is made, so the row stays as it was")
        val chosen = p.withMoves(Moveset.learnable(p).take(3).map { it.identifier })
        assertEquals(chosen, json.decodeFromString(OwnedPokemon.serializer(), json.encodeToString(OwnedPokemon.serializer(), chosen)))
    }
}
