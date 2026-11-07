package app.pokeplan.app

import app.pokeplan.core.pokemon.GearTool
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GearTest {
    private class FixedClock : Clock { override fun nowMillis() = 1_700_000_000_000L }

    private fun store() = GameStore(MemoryStorage(), FixedClock(), TimeZone.UTC, Random(3)).also { it.start("Sophia", 25) }

    @Test
    fun theSignatureLinesCanHelp() {
        assertTrue(GearTool.FLASHLIGHT.helps(181))
        assertTrue(GearTool.COIN.helps(1000))
        assertTrue(GearTool.CURRENCY.helps(863))
        assertFalse(GearTool.PICKER.helps(25))
        GearTool.entries.forEach { assertTrue(it.helps(it.loaner), "${it.name}'s loaner is in its own line") }
    }

    @Test
    fun aToolWithNoHelperHasNoneUntilItIsLent() {
        val store = store()
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
        store.lendGearHelper(GearTool.FLASHLIGHT)
        val loaner = assertIs<GearHelper.Loaner>(store.gearHelper(GearTool.FLASHLIGHT))
        assertEquals(181, loaner.speciesId)
        // A loan never adds to the collection.
        assertFalse(store.data.player.pokemon.any { it.speciesId == 181 })
    }

    @Test
    fun aChosenPokemonTakesOverFromTheLoanerAndHelpsOneToolInNoOtherPlace() {
        val store = store()
        store.lendGearHelper(GearTool.FLASHLIGHT)
        val mareep = store.grantPokemon(179, "test")
        // Nothing picks a helper from the collection by itself (it would take the Pokémon out of the PC): the loaner stays until a choice.
        assertIs<GearHelper.Loaner>(store.gearHelper(GearTool.FLASHLIGHT))
        assertTrue(store.assignGearHelper(GearTool.FLASHLIGHT, mareep.id))
        assertEquals(Place.GearHelper(GearTool.FLASHLIGHT.name), store.placeOf(mareep.id))
        val ampharos = store.grantPokemon(181, "test")
        assertTrue(store.assignGearHelper(GearTool.FLASHLIGHT, ampharos.id))
        assertEquals(ampharos.id, assertIs<GearHelper.Owned>(store.gearHelper(GearTool.FLASHLIGHT)).pokemon.id)
        assertEquals(Place.Pc, store.placeOf(mareep.id), "the one it replaced goes back to the PC")
        // Only the tools its line fits.
        assertFalse(store.assignGearHelper(GearTool.COIN, ampharos.id))
        assertFalse(store.assignGearHelper(GearTool.FLASHLIGHT, "nobody"))
        store.clearGearHelper(GearTool.FLASHLIGHT)
        assertEquals(Place.Pc, store.placeOf(ampharos.id))
        assertIs<GearHelper.Loaner>(store.gearHelper(GearTool.FLASHLIGHT))
        assertTrue(store.gearCandidates(GearTool.FLASHLIGHT).size == 2)
    }

    @Test
    fun theHelperGoesWhenTransferredAndNothingPays() {
        val store = store()
        val first = store.grantPokemon(179, "test")
        val second = store.grantPokemon(179, "test")
        store.assignGearHelper(GearTool.FLASHLIGHT, first.id)
        val inventory = store.data.player.inventory
        assertTrue(store.transfer(first.id), "a duplicate Mareep can be transferred")
        // The other Mareep does not take over by itself, and the tool has no helper.
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
        assertTrue(store.data.player.inventory != inventory, "the transfer itself pays (it is not a tool)")
        assertEquals(setOf(second.id), store.gearCandidates(GearTool.FLASHLIGHT).map { it.id }.toSet())
    }

    @Test
    fun thePickerKeepsATidyShortList() {
        val store = store()
        store.setPickerOptions(listOf(" pizza ", "", "sushi", "pizza") + (1..40).map { "x$it" })
        assertEquals(GameStore.MAX_PICKER_OPTIONS, store.data.pickerOptions.size)
        assertEquals(listOf("pizza", "sushi", "x1"), store.data.pickerOptions.take(3))
    }

    @Test
    fun theRatesAreKeptForOffline() {
        val store = store()
        store.saveGearRates(GearRates("2026-10-31", mapOf("EUR" to 0.92)))
        assertEquals(0.92, store.data.gearRates?.perUsd?.get("EUR"))
    }
}
