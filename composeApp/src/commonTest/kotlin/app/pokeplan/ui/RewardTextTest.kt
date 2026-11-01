package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Reward
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardTextTest {
    private val hatched = Reward.Hatched(
        Hatching.create("a", HatchOption(25, Dex.defaultForm(25).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(isShiny = false), fromTask = false,
    )

    @Test
    fun anEggHatchesButWhatWorldGivesJoins() {
        assertEquals("Your egg hatched: Pikachu!", hatched.describe())
        assertEquals("Your egg hatched: Pikachu!", hatched.describe("Focus"))
        assertEquals("Pikachu joined you!", hatched.describe("World"))
        assertEquals("Pikachu joined you!", hatched.describe("World: exploring"))
        assertTrue(isWorldSource("World: exploring") && !isWorldSource("Care"))
        assertFalse(hatched.describe("World").contains("egg", ignoreCase = true))
    }
}
