package app.pokeplan.app.gear

import app.pokeplan.core.pokemon.GearTool
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.GearHelper
import app.pokeplan.app.Place
import app.pokeplan.app.Places
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.world.worldAssign
import app.pokeplan.app.world.worldStart

class GearTest {
    private class FixedClock : Clock { override fun nowMillis() = 1_700_000_000_000L }

    private fun store() = GameStore(MemoryStorage(), FixedClock(), TimeZone.UTC, Random(3)).also { it.start("Sophia", 25) }

    @Test
    fun aToolHasNoHelperUntilThePlayerChoosesAnOwnedOne() {
        val store = store()
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
        // Owning one who could help does not make it the helper by itself (it would take the Pokémon out of the PC).
        store.grantPokemon(179, "test")
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
    }

    @Test
    fun aChosenPokemonHelpsOneToolInNoOtherPlace() {
        val store = store()
        val mareep = store.grantPokemon(179, "test")
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
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
        assertIs<GearHelper.None>(store.gearHelper(GearTool.FLASHLIGHT))
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
    fun everyPlaceHasItsOwnLabelSoNoOneIsShownInThePcByMistake() {
        val store = store()
        val a = store.grantPokemon(179, "test").id
        assertEquals("Resting in the PC", Places.label(store.data, a))
        store.assignGearHelper(GearTool.FLASHLIGHT, a)
        assertEquals("Helping the Flashlight", Places.label(store.data, a))
        val b = store.grantPokemon(25, "test").id
        store.setInParty(b, true, app.pokeplan.core.pokemon.PartyIds.MINT)
        assertTrue(Places.label(store.data, b).endsWith("Mint's party"), Places.label(store.data, b))
        val c = store.grantPokemon(1, "test").id
        store.setInParty(c, true, app.pokeplan.core.pokemon.PartyIds.BASE)
        assertTrue("Base's" in Places.label(store.data, c))
        val d = store.grantPokemon(4, "test").id
        assertTrue(store.worldAssign(1, listOf(d)))
        assertTrue(Places.label(store.data, d).startsWith("On "), Places.label(store.data, d))
        assertEquals("Your buddy", Places.label(store.data, store.data.player.buddyId!!))
    }

    @Test
    fun anyPlaceButTheBuddyCanBeSentBackToThePc() {
        val store = store()
        val helper = store.grantPokemon(179, "test").id
        store.assignGearHelper(GearTool.FLASHLIGHT, helper)
        assertTrue(store.sendToPc(helper))
        assertEquals(Place.Pc, store.placeOf(helper))
        assertFalse(store.sendToPc(helper), "already in the PC")
        val member = store.grantPokemon(4, "test").id
        assertTrue(store.worldAssign(1, listOf(member)))
        assertTrue(store.sendToPc(member))
        assertTrue(store.data.world.teams.none { member in it.members })
        assertFalse(store.sendToPc(store.data.player.buddyId!!), "the buddy is changed by choosing another")
    }

    @Test
    fun aTeamOutExploringIsNotOfferedAsAHelper() {
        val store = store()
        val out = store.grantPokemon(179, "test").id
        assertTrue(store.worldAssign(1, listOf(out)))
        assertTrue(store.worldStart(1, "tiny-woods"))
        assertTrue(store.gearCandidates(GearTool.FLASHLIGHT).none { it.id == out })
        assertFalse(store.assignGearHelper(GearTool.FLASHLIGHT, out))
        assertFalse(store.sendToPc(out))
    }
}
