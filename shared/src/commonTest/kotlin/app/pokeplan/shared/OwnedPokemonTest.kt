package app.pokeplan.shared

import app.pokeplan.shared.data.Pokedex
import app.pokeplan.shared.model.Nature
import app.pokeplan.shared.model.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedPokemonTest {
    private fun bulbasaur() = OwnedPokemon.hatched("a", Pokedex.species("Bulbasaur")!!, partySize = 0, rng = Random(1))

    @Test
    fun newPokemonStartsAtLevelOneWithItsSpeciesNameAndARealNature() {
        val p = bulbasaur()
        assertEquals(1, p.level)
        assertEquals("Bulbasaur", p.nickname)
        assertTrue(p.nature in Nature.all)
    }

    @Test
    fun partyHoldsSixThenOverflowsToThePC() {
        val s = Pokedex.species("Bulbasaur")!!
        assertTrue(OwnedPokemon.hatched("a", s, partySize = 5).inParty)
        assertFalse(OwnedPokemon.hatched("a", s, partySize = 6).inParty)
    }

    @Test
    fun tenCandiesMakeOneLevel() {
        var p = bulbasaur()
        repeat(9) { p = p.feedRareCandy() }
        assertEquals(1, p.level)
        p = p.feedRareCandy()
        assertEquals(2, p.level)
        assertEquals(10, p.fedCandy)
    }

    @Test
    fun levelStopsAtOneHundred() {
        val p = bulbasaur().copy(level = 100)
        assertFalse(p.canFeed)
        assertFailsWith<IllegalStateException> { p.feedRareCandy() }
    }

    @Test
    fun evolutionNeedsTheSpeciesLevel() {
        val p = bulbasaur()
        assertFalse(p.canEvolve)
        assertFailsWith<IllegalStateException> { p.evolve() }
        val ready = p.copy(level = 16)
        assertTrue(ready.canEvolve)
        assertEquals("Ivysaur", ready.evolve().species.name)
    }

    @Test
    fun defaultNicknameFollowsEvolutionButACustomOneStays() {
        val ready = bulbasaur().copy(level = 16)
        assertEquals("Ivysaur", ready.evolve().nickname)
        assertEquals("Bulby", ready.copy(nickname = "Bulby").evolve().nickname)
    }

    @Test
    fun fullyEvolvedSpeciesCannotEvolve() {
        val v = bulbasaur().copy(species = Pokedex.species("Venusaur")!!, level = 100)
        assertFalse(v.canEvolve)
    }
}
