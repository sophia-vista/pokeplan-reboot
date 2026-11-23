package app.pokeplan.app.world

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.world.WorldSim
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settlement is driven by elapsed time, so the app must not need a poll: the two events that settle are the app coming
 * back to the foreground and the floor alarm, and repeating either of them must pay nothing twice.
 */
class WorldSettlementTest {
    private class Clock(var millis: Long = 1_700_000_000_000L) : app.pokeplan.app.storage.Clock {
        override fun nowMillis() = millis
    }

    private fun store(clock: Clock = Clock()) =
        GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }

    private fun GameStore.teamOne() {
        val team = listOf(data.player.pokemon.first().id, grantPokemon(25, "test").id, grantPokemon(74, "test").id)
        assertTrue(worldAssign(1, team))
    }

    private fun GameStore.explore(clock: Clock): Long {
        teamOne()
        assertTrue(worldStart(1, "tiny-woods"))
        return assertNotNull(WorldAlerts.nextFloorAt(data, clock.millis), "a team that is out has a next floor")
    }

    @Test
    fun settlingBeforeTheFloorIsDuePaysNothing() {
        val clock = Clock()
        val store = store(clock)
        val due = store.explore(clock)

        clock.millis = due - 1
        assertNull(store.worldSettle(), "no poll: before the due instant there is nothing to settle")
        assertEquals(0, store.data.world.xp)
        assertTrue(store.data.world.finds.isEmpty())
    }

    @Test
    fun theFloorAlarmInstantIsExactlyWhenSettlingPaysTheFirstFloor() {
        val clock = Clock()
        val store = store(clock)
        val due = store.explore(clock)

        clock.millis = due
        assertNotNull(store.worldSettle(), "an alarm at nextFloorAt lands on a settled floor")
        assertTrue(store.data.world.xp > 0)
        assertEquals(due, store.data.world.teams.first { it.id == 1 }.expedition?.settledTo)
    }

    @Test
    fun settlingAgainWithoutElapsedTimeChangesNothing() {
        val clock = Clock()
        val store = store(clock)
        val due = store.explore(clock)
        clock.millis = due + 3 * 60_000L
        val first = assertNotNull(store.worldSettle())
        val settled = store.data
        val rewardLog = settled.rewardLog.size
        val xp = settled.world.xp
        val finds = settled.world.finds.toList()
        val coins = settled.player.coins

        assertNull(store.worldSettle(), "a second settle at the same instant is a no-op")
        assertEquals(settled, store.data, "the foreground and an alarm may both settle: neither pays twice")
        assertNull(store.worldSettle())
        assertEquals(rewardLog, store.data.rewardLog.size)
        assertEquals(xp, store.data.world.xp)
        assertEquals(finds, store.data.world.finds)
        assertEquals(coins, store.data.player.coins)
        assertEquals(first.data.xp, store.data.world.xp)
    }

    @Test
    fun oneSettleAtTheEndMatchesSettlingAllAlongSoNoPollIsNeeded() {
        val startedAt = 1_700_000_000_000L
        val poll = Clock(startedAt)
        val once = Clock(startedAt)
        val polled = store(poll)
        val single = store(once)
        polled.teamOne(); single.teamOne()
        assertTrue(polled.worldStart(1, "tiny-woods"))
        assertTrue(single.worldStart(1, "tiny-woods"))

        val span = 5L * 60 * 60 * 1000
        while (poll.millis < startedAt + span) {
            poll.millis = minOf(poll.millis + 1_000L, startedAt + span)
            polled.worldSettle()
        }
        once.millis = startedAt + span
        assertNotNull(single.worldSettle())

        assertEquals(polled.data.world.xp, single.data.world.xp, "the same floors for the same elapsed time")
        assertEquals(polled.data.world.deepest, single.data.world.deepest)
        assertEquals(polled.data.world.clears, single.data.world.clears)
        assertEquals(polled.data.world.finds.map { it.speciesId to it.floor }, single.data.world.finds.map { it.speciesId to it.floor })
        assertEquals(polled.data.world.teams.map { it.expedition?.floorsClimbed }, single.data.world.teams.map { it.expedition?.floorsClimbed })
        assertEquals(polled.data.player.pokemon.map { it.id to it.level }, single.data.player.pokemon.map { it.id to it.level })
    }

    @Test
    fun noTeamOutMeansNoFloorAlarmToSet() {
        val store = store()
        store.teamOne()
        assertNull(WorldAlerts.nextFloorAt(store.data, store.nowMillis), "resting teams need no alarm")
        assertTrue(store.worldStart(1, "tiny-woods"))
        assertNotNull(WorldAlerts.nextFloorAt(store.data, store.nowMillis))
        store.worldRecall(1)
        assertNull(WorldAlerts.nextFloorAt(store.data, store.nowMillis), "recalling cancels the next floor alarm")
    }

    @Test
    fun theFloorAlarmMatchesTheSimulationClock() {
        val clock = Clock()
        val store = store(clock)
        val due = store.explore(clock)
        assertEquals(WorldSim.nextFloorAt(store.data.world, store.data.player.pokemon, due), WorldAlerts.nextFloorAt(store.data, due))
    }
}
