package app.pokeplan.app

import app.pokeplan.world.WorldSim
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WORLD settles under stable ledger event ids (WORLD.md 3.1 and 6; DATA_AND_MIGRATIONS.md 7.3.0). */
class WorldLedgerNotesTest {
    private class Clock(var millis: Long = 1_700_000_000_000L) : app.pokeplan.app.Clock { override fun nowMillis() = millis }

    private fun out(clock: Clock = Clock()): GameStore {
        val store = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }
        val team = listOf(store.data.player.pokemon.first().id, store.grantPokemon(25, "test").id, store.grantPokemon(74, "test").id)
        assertTrue(store.worldAssign(1, team))
        assertTrue(store.worldStart(1, "tiny-woods"))
        return store
    }

    @Test
    fun aSettleIsOneLedgerEventNamedByTheFloorsItCovers() {
        val clock = Clock()
        val store = out(clock)
        val start = store.data.world.team(1)!!.expedition!!.startedAt
        val coins = store.data.player.coins
        val bag = store.data.player.inventory.counts.values.sum()
        clock.millis += 6L * 60 * 60 * 1000
        assertNotNull(store.worldSettle())
        val climbed = store.data.world.team(1)!!.expedition!!.floorsClimbed
        val note = store.data.ledgerNotes.single { it.module == "WORLD" }
        assertEquals("world:expedition:1.$start.0-$climbed", note.eventId)
        assertEquals((store.data.player.coins - coins).toLong(), note.poke, "the note is the real change of the balance")
        assertEquals((store.data.player.inventory.counts.values.sum() - bag).toLong(), note.items.values.sum(), "and of the Bag")
        assertTrue(store.data.world.items.isEmpty() && store.data.world.pokeEarned == 0, "nothing is left staged in World")
    }

    @Test
    fun theNextSettleContinuesWhereTheLastStoppedSoNoFloorHasTwoIds() {
        val clock = Clock()
        val store = out(clock)
        clock.millis += 4L * 60 * 60 * 1000
        store.worldSettle()
        val first = store.data.world.team(1)!!.expedition!!.floorsClimbed
        clock.millis += 4L * 60 * 60 * 1000
        store.worldSettle()
        store.worldSettle() // nothing new: no event
        val ids = store.data.ledgerNotes.filter { it.module == "WORLD" }.map { it.eventId }
        assertEquals(2, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids[1].endsWith(".$first-${store.data.world.team(1)!!.expedition!!.floorsClimbed}"), ids[1])
    }

    @Test
    fun aTreasureBoxAndAResearchStepHaveEventsOfTheirOwn() {
        val clock = Clock()
        val store = out(clock)
        repeat(40) { if (store.data.world.treasure.values.sum() == 0) { clock.millis += 12L * 60 * 60 * 1000; store.worldSettle() } }
        val tier = assertNotNull(store.data.world.treasure.entries.firstOrNull { it.value > 0 }?.key, "a few days find a box")
        val found = store.worldOpenTreasure(tier)
        val note = store.data.ledgerNotes.last { it.module == "WORLD" }
        assertEquals("world:treasure:$tier:${clock.millis}", note.eventId)
        assertEquals(found.values.sum().toLong(), note.items.values.sum())
        assertEquals(0L, note.poke)
        assertTrue(store.data.world.items.isEmpty())
    }

    @Test
    fun aClockSetBackMovesTheSettlePointInTheStoreToo() {
        val clock = Clock()
        val store = out(clock)
        clock.millis += 3L * 60 * 60 * 1000
        store.worldSettle()
        val before = store.data.world.team(1)!!.expedition!!.settledTo
        clock.millis -= 2L * 60 * 60 * 1000
        assertNull(store.worldSettle(), "no floor in the past")
        assertEquals(clock.millis, store.data.world.team(1)!!.expedition!!.settledTo)
        assertTrue(store.data.world.team(1)!!.expedition!!.settledTo < before)
        clock.millis += 2L * 60 * 60 * 1000
        assertNotNull(store.worldSettle(), "the climb goes on from the new settle point")
    }

    @Test
    fun theNextFloorIsDueAtTheSettlePointPlusTheFloorTime() {
        val clock = Clock()
        val store = out(clock)
        val w = store.data.world
        val due = assertNotNull(WorldSim.nextFloorAt(w, store.data.player.pokemon, clock.millis))
        assertTrue(due > clock.millis)
        clock.millis = due
        assertNotNull(store.worldSettle(), "settling at the due instant climbs a floor")
        assertTrue(WorldSim.nextFloorAt(store.data.world, store.data.player.pokemon, clock.millis)!! > due)
        store.worldRecall(1)
        assertNull(WorldSim.nextFloorAt(store.data.world, store.data.player.pokemon, clock.millis), "no team out, nothing due")
    }
}
