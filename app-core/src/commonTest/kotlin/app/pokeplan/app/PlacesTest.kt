package app.pokeplan.app

import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.world.WorldTeam
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One Pokémon, one place (POKEMON_ECOSYSTEM.md section 2.2, owner decision 7 November 2026). */
class PlacesTest {
    private class FakeClock(var millis: Long = 1_793_000_000_000L) : Clock { override fun nowMillis() = millis }

    private fun store(): GameStore = GameStore(MemoryStorage(), FakeClock(), TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }

    @Test
    fun aSaveWithAPokemonInManyPlacesKeepsTheHighestAndNothingIsRemoved() {
        val s = store()
        val a = s.grantPokemon(25, "t"); val b = s.grantPokemon(74, "t"); val c = s.grantPokemon(10, "t"); val d = s.grantPokemon(16, "t")
        val d0 = s.data
        // a: buddy + party + team + helper; b: party + helper; c: helper only (the buddy is a).
        val old = d0.copy(
            player = d0.player.copy(
                buddyId = a.id,
                pokemon = d0.player.pokemon.map { if (it.id == a.id || it.id == b.id) it.copy(inParty = true, partyId = PartyIds.PLAN) else it },
            ),
            world = d0.world.copy(teams = listOf(WorldTeam(1, members = listOf(a.id)))),
            gearHelpers = mapOf("FLASHLIGHT" to b.id, "COIN" to c.id),
            placesVersion = 0,
        )
        val resolved = Places.resolve(old)
        assertEquals(Place.WorldTeam(1), Places.of(resolved, a.id), "a team beats a party, a helper and the buddy")
        assertEquals(Place.Party(PartyIds.PLAN), Places.of(resolved, b.id), "a party beats a helper")
        assertEquals(Place.GearHelper("COIN"), Places.of(resolved, c.id))
        assertEquals(old.player.pokemon.size, resolved.player.pokemon.size, "nobody leaves the collection")
        assertEquals(Places.VERSION, resolved.placesVersion)
        // Every Pokémon is in exactly one place: the references never overlap.
        val claims = buildList {
            resolved.player.buddyId?.let { add(it) }
            addAll(resolved.player.pokemon.filter { it.inParty }.map { it.id })
            addAll(resolved.player.guardians); addAll(resolved.player.moduleGuardians.values); addAll(resolved.world.assigned); addAll(resolved.gearHelpers.values)
        }
        assertEquals(claims.size, claims.toSet().size)
        // The buddy was lost, so the best Pokémon in the PC takes it; it runs once.
        assertTrue(resolved.player.buddyId != null && Places.of(resolved, resolved.player.buddyId!!) == Place.Buddy)
        assertEquals(resolved, Places.resolve(resolved))
    }

    @Test
    fun theBuddyBeatsAPartyAndAHelperSoTheStarterStaysTheBuddy() {
        val s = store()
        val starter = s.data.player.buddyId!!
        val old = s.data.copy(
            player = s.data.player.copy(pokemon = s.data.player.pokemon.map { it.copy(inParty = true) }, planLeadId = starter),
            gearHelpers = mapOf("FLASHLIGHT" to starter), placesVersion = 0,
        )
        val resolved = Places.resolve(old)
        assertEquals(Place.Buddy, Places.of(resolved, starter))
        assertEquals(starter, resolved.player.buddyId)
        assertTrue(resolved.player.party.isEmpty() && resolved.player.planLeadId == null)
        assertTrue(resolved.gearHelpers.isEmpty())
    }

    @Test
    fun aSaveAlreadyResolvedIsLeftAlone() {
        val s = store()
        assertEquals(s.data, Places.resolve(s.data))
    }

    @Test
    fun anExpeditionUnderWayHoldsItsMembersAndAnyOtherAssignmentTakesThePokemonFromItsOldPlace() {
        val s = store()
        val p = s.grantPokemon(179, "t")
        assertTrue(s.assignGearHelper(app.pokeplan.core.pokemon.GearTool.FLASHLIGHT, p.id))
        assertTrue(s.setBuddy(p.id), "the helper becomes the buddy and stops helping")
        assertTrue(s.data.gearHelpers.isEmpty())
        assertEquals(Place.Buddy, s.placeOf(p.id))
        assertTrue(s.setInParty(p.id, true, PartyIds.CARE))
        assertNull(s.data.player.buddyId)
        assertEquals(Place.Party(PartyIds.CARE), s.placeOf(p.id))
    }
}
