package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WORLD.md 2.3: a team that suits the dungeon; the expedition formula and bestTeam are untouched. */
class TeamAdviceTest {
    private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val woods = Dungeons.byId("tiny-woods")!! // Grass, Bug, Flying
    private val pikachu = mon("a", 25, 50)
    private val rattata = mon("b", 19, 40)
    private val caterpie = mon("c", 10, 5)
    private val oddish = mon("d", 43, 5)

    @Test
    fun favouredTypesComeFirstEvenAtALowerLevel() {
        val advice = TeamAdvice.recommend(woods, listOf(pikachu, rattata, caterpie, oddish), 3)
        assertEquals(listOf("c", "d").toSet(), advice.picks.take(2).map { it.pokemon.id }.toSet())
        assertTrue(advice.picks.take(2).all { it.matches && "suits" in it.reason })
        assertEquals("a", advice.picks[2].pokemon.id)
        assertEquals("Strongest left", advice.picks[2].reason)
        assertFalse(advice.noMatch)
    }

    @Test
    fun tiesAreDeterministicWhateverTheOrderGiven() {
        val one = mon("x", 10, 5)
        val two = mon("y", 10, 5)
        assertEquals(listOf("x", "y"), TeamAdvice.recommend(woods, listOf(two, one)).ids)
        assertEquals(listOf("x", "y"), TeamAdvice.recommend(woods, listOf(one, two)).ids)
    }

    @Test
    fun nobodyMatchingSaysSoAndFallsBackToStrength() {
        val advice = TeamAdvice.recommend(woods, listOf(rattata, pikachu))
        assertTrue(advice.noMatch)
        assertEquals(listOf("a", "b"), advice.ids)
        assertTrue(advice.picks.none { it.matches })
    }

    @Test
    fun theTeamIsCappedAtTheSizeAndAnEmptyPoolGivesNothing() {
        val many = (1..8).map { mon("m$it", 10, it) }
        assertEquals(WorldTeam.MAX_MEMBERS, TeamAdvice.recommend(woods, many).picks.size)
        assertEquals(2, TeamAdvice.recommend(woods, many, 2).picks.size)
        assertTrue(TeamAdvice.recommend(woods, emptyList()).picks.isEmpty())
    }

    @Test
    fun poorMatchNeedsAboutAThirdOfTheMembersToFitTheDungeon() {
        assertFalse(TeamAdvice.isPoorMatch(woods, emptyList()))
        assertTrue(TeamAdvice.isPoorMatch(woods, listOf(pikachu, rattata)))
        assertFalse(TeamAdvice.isPoorMatch(woods, listOf(pikachu, rattata, caterpie)), "one of three is enough")
        assertTrue(TeamAdvice.isPoorMatch(woods, listOf(pikachu, rattata, pikachu, caterpie)), "one of four is not")
        assertFalse(TeamAdvice.isPoorMatch(woods, listOf(pikachu, caterpie)))
    }
}
