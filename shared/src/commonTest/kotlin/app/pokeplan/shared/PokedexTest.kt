package app.pokeplan.shared

import app.pokeplan.shared.data.Pokedex
import app.pokeplan.shared.model.Rarity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PokedexTest {
    @Test
    fun dexNumbersRunFromOneWithoutGaps() {
        assertEquals((1..Pokedex.all.size).toList(), Pokedex.all.map { it.dexNum })
        assertEquals(150, Pokedex.all.size)
    }

    @Test
    fun namesAreUnique() {
        assertEquals(Pokedex.all.size, Pokedex.all.map { it.name }.toSet().size)
    }

    @Test
    fun everyEvolutionResolvesAndHasALevel() {
        for (s in Pokedex.all) {
            assertEquals(s.evolvesTo == null, s.evolveLevel == null, "${s.name}: evolveLevel and evolvesTo go together")
            s.evolvesTo?.let { assertNotNull(Pokedex.species(it), "${s.name} evolves into unknown $it") }
        }
    }

    @Test
    fun everyRarityHasSomethingToHatch() {
        for (r in Rarity.entries) assertTrue(Pokedex.hatchable(r).isNotEmpty(), "no species for $r")
    }

    @Test
    fun hatchablesAreTheOnesWithARarity() {
        val hatchable = Rarity.entries.flatMap { Pokedex.hatchable(it) }
        assertEquals(Pokedex.all.count { it.rarity != null }, hatchable.size)
        // Original data: 22 common, 25 uncommon, 9 rare, 10 super rare.
        assertEquals(listOf(22, 25, 9, 10), Rarity.entries.map { Pokedex.hatchable(it).size })
    }

    @Test
    fun everySpeciesHasOneOrTwoTypesAndAnEntry() {
        for (s in Pokedex.all) {
            assertTrue(s.types.size in 1..2, "${s.name} types")
            assertTrue(Pokedex.entry(s.dexNum).isNotBlank(), "${s.name} entry")
        }
    }
}
