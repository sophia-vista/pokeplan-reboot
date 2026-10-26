package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvolutionRulesTest {
    private fun ctx(hour: Int = 12, vararg items: Pair<String, Int>, trials: Set<String> = emptySet()) =
        EvolutionContext(hour, mapOf(*items), trials)

    private val candy = EvolutionRules.SUPER_CANDY to 1

    @Test
    fun levelEvolutionNeedsTheLevelAndASuperCandy() {
        val early = EvolutionRules.options(pokemon(Ids.BULBASAUR, level = 15), ctx(12, candy)).single()
        assertEquals(listOf("level 16"), early.missing, "the game's level")
        val noCandy = EvolutionRules.options(pokemon(Ids.BULBASAUR, level = 16), ctx()).single()
        assertEquals(listOf("a Super Candy"), noCandy.missing)
        val ready = EvolutionRules.options(pokemon(Ids.BULBASAUR, level = 16), ctx(12, candy)).single()
        assertTrue(ready.available)
        val result = EvolutionRules.evolve(pokemon(Ids.BULBASAUR, level = 16), ready)
        assertEquals(2, result.pokemon.speciesId)
        assertEquals("Ivysaur", result.pokemon.nickname)
        assertEquals(mapOf(EvolutionRules.SUPER_CANDY to 1), result.consumed)
    }

    @Test
    fun evolutionLevelsAreTheGameLevels() {
        fun level(from: Int, to: Int) = EvolutionRules.requiredLevel(app.pokeplan.core.dex.Dex.evolutionsFrom(from).first { it.to == to })
        assertEquals(16, level(1, 2))
        assertEquals(32, level(2, 3))
        assertEquals(16, level(Ids.CHARMANDER, 5))
        assertEquals(36, level(5, 6))
        assertEquals(7, level(Ids.WURMPLE, 266))
        assertEquals(null, level(Ids.PIKACHU, 26), "stone evolutions have no level")
        assertEquals(2, EvolutionRules.requiredLevel(app.pokeplan.core.dex.Evolution(1, 2, app.pokeplan.core.dex.EvolutionKind.LEVEL, 2, null, null, null, null, null, null, null, null, emptyList())), "never below 2")
    }

    @Test
    fun evolvingWhenNotReadyIsRefused() {
        val option = EvolutionRules.options(pokemon(Ids.BULBASAUR), ctx()).single()
        assertFailsWith<IllegalStateException> { EvolutionRules.evolve(pokemon(Ids.BULBASAUR), option) }
    }

    @Test
    fun pikachuGetsBothRaichuFormsFromAStone() {
        val options = EvolutionRules.options(pokemon(Ids.PIKACHU), ctx(12, "thunder-stone" to 1))
        assertEquals(setOf("raichu", "raichu-alola"), options.map { it.target.identifier }.toSet())
        assertTrue(options.all { it.available })
        val none = EvolutionRules.options(pokemon(Ids.PIKACHU), ctx())
        assertTrue(none.none { it.available })
        val result = EvolutionRules.evolve(pokemon(Ids.PIKACHU), options.first { it.target.identifier == "raichu-alola" })
        assertEquals("raichu-alola", result.pokemon.formIdentifier)
        assertEquals(mapOf("thunder-stone" to 1), result.consumed)
    }

    @Test
    fun tradeEvolutionUsesALinkCord() {
        val without = EvolutionRules.options(pokemon(Ids.MACHOKE), ctx()).single()
        assertEquals(listOf("a Link Cord"), without.missing)
        val with = EvolutionRules.options(pokemon(Ids.MACHOKE), ctx(12, EvolutionRules.LINK_CORD to 1)).single()
        assertTrue(with.available)
        assertEquals(mapOf(EvolutionRules.LINK_CORD to 1), EvolutionRules.evolve(pokemon(Ids.MACHOKE), with).consumed)
    }

    @Test
    fun eeveeBranchesByStoneAndByTimeOfDay() {
        val items = arrayOf(candy, "water-stone" to 1)
        val night = EvolutionRules.options(pokemon(Ids.EEVEE, friendship = 230), ctx(23, *items))
        val available = night.filter { it.available }.map { it.target.identifier }.toSet()
        assertTrue("umbreon" in available && "vaporeon" in available)
        assertFalse("espeon" in available, "Espeon is a daytime evolution")
        val day = EvolutionRules.options(pokemon(Ids.EEVEE, friendship = 230), ctx(10, *items))
            .filter { it.available }.map { it.target.identifier }.toSet()
        assertTrue("espeon" in day && "umbreon" !in day)
        val lowFriendship = EvolutionRules.options(pokemon(Ids.EEVEE, friendship = 100), ctx(10, *items))
        assertTrue(lowFriendship.first { it.target.identifier == "espeon" }.missing.any { it.startsWith("friendship") })
    }

    @Test
    fun galladeNeedsAMaleKirliaAndADawnStone() {
        val items = arrayOf(candy, "dawn-stone" to 1)
        val male = EvolutionRules.options(pokemon(Ids.KIRLIA, level = 30, gender = Gender.MALE), ctx(12, *items))
        assertTrue(male.first { it.target.identifier == "gallade" }.available)
        assertTrue(male.first { it.target.identifier == "gardevoir" }.available)
        val female = EvolutionRules.options(pokemon(Ids.KIRLIA, level = 30, gender = Gender.FEMALE), ctx(12, *items))
        assertEquals(listOf("to be male"), female.first { it.target.identifier == "gallade" }.missing)
        assertTrue(female.first { it.target.identifier == "gardevoir" }.available)
    }

    @Test
    fun wurmpleBranchIsFixedByPersonality() {
        val low = EvolutionRules.options(pokemon(Ids.WURMPLE, level = 7, personality = 10), ctx(12, candy)).single()
        val high = EvolutionRules.options(pokemon(Ids.WURMPLE, level = 7, personality = 60), ctx(12, candy)).single()
        assertEquals("silcoon", low.target.identifier)
        assertEquals("cascoon", high.target.identifier)
        assertTrue(low.available && high.available)
    }

    @Test
    fun specialConditionsNeedTheEvolutionTrial() {
        val p = pokemon(Ids.PANCHAM, level = 32)
        val locked = EvolutionRules.options(p, ctx(12, candy)).single()
        assertEquals(listOf("the evolution trial"), locked.missing)
        val done = EvolutionRules.options(p, ctx(12, candy, trials = setOf(EvolutionRules.trialId(locked.evolution)))).single()
        assertTrue(done.available)
    }

    @Test
    fun regionalFormsEvolveIntoTheirRegionalEvolutions() {
        val alolan = pokemon(Ids.RATTATA, "rattata-alola", level = 20)
        val night = EvolutionRules.options(alolan, ctx(23, candy)).single()
        assertEquals("raticate-alola", night.target.identifier)
        assertTrue(night.available)
        val day = EvolutionRules.options(alolan, ctx(12, candy)).single()
        assertFalse(day.available, "Alolan Raticate evolves at night")
        val plain = EvolutionRules.options(pokemon(Ids.RATTATA, level = 20), ctx(12, candy)).single()
        assertEquals("raticate", plain.target.identifier)
    }

    @Test
    fun galarianSlowpokeUsesItsOwnStones() {
        val p = pokemon(Ids.SLOWPOKE, "slowpoke-galar")
        val options = EvolutionRules.options(p, ctx(12, "galarica-cuff" to 1))
        assertEquals(setOf("slowbro-galar", "slowking-galar"), options.map { it.target.identifier }.toSet())
        assertEquals(listOf("slowbro-galar"), options.filter { it.available }.map { it.target.identifier })
    }

    @Test
    fun howToEvolveReadsLikeTheAppsRules() {
        fun how(from: Int, to: Int) = EvolutionRules.howTo(app.pokeplan.core.dex.Dex.evolutionsFrom(from).first { it.to == to })
        assertEquals("Lv. 16, Super Candy", how(Ids.CHARMANDER, 5))
        assertEquals("Water Stone", how(133, 134))
        assertEquals("Friendship, Super Candy, at night", how(133, 197))
        assertEquals("Link Cord", how(Ids.MACHOKE, 68))
        assertEquals("Link Cord, holding Metal Coat", how(95, 208))
    }
}
