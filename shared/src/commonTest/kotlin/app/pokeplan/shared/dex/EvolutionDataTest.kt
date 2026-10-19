package app.pokeplan.shared.dex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvolutionDataTest {
    private fun evo(from: Int, to: Int) = Dex.evolutionsFrom(from).filter { it.to == to }

    @Test
    fun everyEvolutionLinksKnownSpeciesAndMatchesEvolvesFrom() {
        assertTrue(Dex.evolutions.size > 500)
        for (e in Dex.evolutions) {
            assertNotNull(Dex.speciesOrNull(e.from), "from ${e.from}")
            assertEquals(e.from, Dex.species(e.to).evolvesFrom, "${e.from} -> ${e.to}")
        }
    }

    @Test
    fun everyNonBasicSpeciesHasAWayToEvolveIntoIt() {
        for (s in Dex.species.filter { it.evolvesFrom != null }) {
            assertTrue(Dex.evolutionsInto(s.id).isNotEmpty(), "${s.name} has no evolution into it")
        }
    }

    @Test
    fun levelEvolution() {
        val e = evo(1, 2).single()
        assertEquals(EvolutionKind.LEVEL, e.kind)
        assertEquals(16, e.level)
    }

    @Test
    fun stoneEvolutionWithFormVariants() {
        val rows = evo(25, 26)
        assertTrue(rows.all { it.kind == EvolutionKind.STONE && it.item == "thunder-stone" })
        assertTrue(rows.any { it.evolvedForm == "raichu-alola" })
    }

    @Test
    fun tradeEvolutions() {
        assertEquals(EvolutionKind.TRADE, evo(67, 68).single().kind)
        assertEquals("kings-rock", evo(61, 186).single().heldItem)
    }

    @Test
    fun friendshipAndTimeOfDay() {
        assertEquals(EvolutionKind.FRIENDSHIP, evo(42, 169).single().kind)
        val espeon = evo(133, 196).single()
        assertEquals(EvolutionKind.FRIENDSHIP, espeon.kind)
        assertEquals(TimeOfDay.DAY, espeon.timeOfDay)
        assertEquals(TimeOfDay.NIGHT, evo(133, 197).single().timeOfDay)
    }

    @Test
    fun eeveeBranchesIntoEight() {
        assertEquals(8, Dex.evolutionsFrom(133).map { it.to }.toSet().size)
        assertEquals(EvolutionKind.STONE, evo(133, 470).single().kind, "Leafeon uses the Leaf Stone, not a location")
    }

    @Test
    fun genderDependentEvolution() {
        val gallade = evo(281, 475).single()
        assertEquals(Gender.MALE, gallade.gender)
        assertEquals("dawn-stone", gallade.item)
    }

    @Test
    fun randomBranchAndTrial() {
        assertTrue(evo(265, 266).all { it.kind == EvolutionKind.RANDOM })
        assertTrue(evo(265, 268).all { it.kind == EvolutionKind.RANDOM })
        val pangoro = evo(674, 675).single()
        assertEquals(EvolutionKind.TRIAL, pangoro.kind)
        assertTrue(pangoro.conditions.any { it.contains("dark") })
    }
}
