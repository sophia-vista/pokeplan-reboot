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

    @Test
    fun onlyPlansEggsAndAVisitorsGiftHatchEveryOtherModuleHasItsOwnWords() {
        assertEquals("A new friend wandered over: Pikachu!", hatched.describe("Care: a routine"))
        assertEquals("Field Research found Pikachu!", hatched.describe("Heal: Field Research"))
        assertEquals("Ledger Research catalogued Pikachu!", hatched.describe("Mint: Ledger Research"))
        assertEquals("Habitat Discovery found Pikachu!", hatched.describe("Home: Habitat Discovery"))
        assertEquals("Your egg hatched: Pikachu!", hatched.describe(app.pokeplan.app.GameStore.CARE_FRIEND_SOURCE), "a visitor's gift really is an egg")
        for (source in listOf("Care: a routine", "Heal: Field Research", "Mint: Ledger Research", "Home: Habitat Discovery")) assertFalse(hatched.describe(source).contains("egg", ignoreCase = true), source)
        assertEquals(Acquisition.ARRIVAL, acquisitionOf(app.pokeplan.app.Origin.How.CARE_EGG))
        assertEquals(Acquisition.FIELD, acquisitionOf(app.pokeplan.app.Origin.How.FIELD_EGG))
        assertEquals(Acquisition.LEDGER, acquisitionOf(app.pokeplan.app.Origin.How.LEDGER_EGG))
        assertEquals(Acquisition.HABITAT, acquisitionOf(app.pokeplan.app.Origin.How.HABITAT_EGG))
        for (how in listOf(app.pokeplan.app.Origin.How.FOCUS_EGG, app.pokeplan.app.Origin.How.TASK_EGG, app.pokeplan.app.Origin.How.BEFRIENDED, null)) assertEquals(Acquisition.EGG, acquisitionOf(how))
    }
}
