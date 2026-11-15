package app.pokeplan.app.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.GearData
import app.pokeplan.app.GearStopwatch
import app.pokeplan.app.GearTimer
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class GearTimersTest {
    private val t0 = 1_700_000_000_000L
    private fun gearWith(vararg timers: GearTimer) = GearData(timers = timers.toList())

    @Test
    fun aTimerRunsPausesResumesAndFinishes() {
        var g = GearTimers.add(GearData(), "a", "  Tea ", 180_000)!!
        assertEquals("Tea", g.timers.single().label)
        assertEquals(180_000, g.timers.single().leftAt(t0), "idle: the whole time")
        g = GearTimers.start(g, "a", t0)
        assertEquals(179_000, g.timers.single().leftAt(t0 + 1000))
        g = GearTimers.pause(g, "a", t0 + 60_000)
        assertEquals(120_000, g.timers.single().leftAt(t0 + 999_999), "paused: what was left, whatever the clock does")
        assertTrue(g.timers.single().paused && !g.timers.single().running)
        g = GearTimers.start(g, "a", t0 + 100_000)
        assertEquals(t0 + 220_000, g.timers.single().endsAt)
        // Starting a running timer again changes nothing (a doubled tap).
        assertEquals(g, GearTimers.start(g, "a", t0 + 150_000))
        val (done, ran) = GearTimers.tick(g, t0 + 220_000)
        assertEquals(listOf("a"), ran.map { it.id })
        assertTrue(done.timers.single().finished && !done.timers.single().running)
        assertEquals(0, done.timers.single().leftAt(t0 + 999_999))
        // Ticking again finds nothing new: a timer is announced once.
        assertTrue(GearTimers.tick(done, t0 + 300_000).second.isEmpty())
    }

    @Test
    fun theLimitsAndTheOddCases() {
        assertNull(GearTimers.add(GearData(), "x", "Too short", 500))
        assertNull(GearTimers.add(GearData(), "x", "Too long", GearTimers.MAX_MS + 1))
        var g = GearData()
        repeat(GearTimers.MAX_TIMERS) { g = GearTimers.add(g, "t$it", "", 60_000)!! }
        assertNull(GearTimers.add(g, "more", "", 60_000), "at most ${GearTimers.MAX_TIMERS}")
        assertEquals("Timer", g.timers.first().label, "a blank label gets a name")
        assertEquals(24, GearTimers.add(GearData(), "x", "x".repeat(60), 60_000)!!.timers.single().label.length)
        // Unknown ids change nothing.
        assertEquals(g, GearTimers.start(g, "nope", t0))
        assertEquals(g, GearTimers.remove(g, "nope"))
        // Pausing an idle timer does nothing; a clock set back cannot give a negative time left.
        assertEquals(g, GearTimers.pause(g, "t0", t0))
        val running = GearTimers.start(gearWith(GearTimer("r", "R", 60_000)), "r", t0)
        assertEquals(60_000, running.timers.single().leftAt(t0), "just started: the whole time")
        assertEquals(65_000, running.timers.single().leftAt(t0 - 5_000), "a clock set back gives more time, never a negative one")
        assertEquals(0, running.timers.single().leftAt(t0 + 999_999), "and past the end it is zero")
    }

    @Test
    fun addingTimeAndTheRingingTimersPlusAMinute() {
        val g = GearTimers.start(gearWith(GearTimer("a", "A", 60_000)), "a", t0)
        assertEquals(t0 + 120_000, GearTimers.addTime(g, "a", 60_000, t0 + 10).timers.single().endsAt, "running: later end")
        val paused = GearTimers.pause(g, "a", t0 + 20_000)
        assertEquals(100_000, GearTimers.addTime(paused, "a", 60_000, t0 + 30_000).timers.single().pausedLeftMs)
        assertEquals(120_000, GearTimers.addTime(gearWith(GearTimer("i", "I", 60_000)), "i", 60_000, t0).timers.single().durationMs, "idle: longer")
        // A ringing (finished) timer given a minute runs for a minute from now, and a second tap on it only adds to the running one.
        val ringing = GearTimers.tick(g, t0 + 60_000).first
        val again = GearTimers.addTime(ringing, "a", 60_000, t0 + 61_000)
        assertFalse(again.timers.single().finished)
        assertEquals(t0 + 121_000, again.timers.single().endsAt)
        // Dismissing is idempotent and leaves running timers alone.
        val mixed = GearData(timers = listOf(GearTimer("f", "F", 1000, finished = true), GearTimer("r", "R", 1000, endsAt = t0 + 5000)))
        val dismissed = GearTimers.dismissFinished(mixed)
        assertTrue(dismissed.timers.none { it.finished } && dismissed.timers.last().running)
        assertEquals(dismissed, GearTimers.dismissFinished(dismissed))
        assertEquals(GearTimer("a", "A", 60_000), GearTimers.reset(ringing, "a").timers.single())
    }

    @Test
    fun theNextEndIsTheEarliestRunningTimerAfterNow() {
        val data = AppData(gear = gearWith(GearTimer("a", "A", 1000, endsAt = t0 + 5_000), GearTimer("b", "B", 1000, endsAt = t0 + 2_000), GearTimer("c", "C", 1000, pausedLeftMs = 100), GearTimer("d", "D", 1000, endsAt = t0 - 1)))
        assertEquals(t0 + 2_000, GearTimers.nextEnd(data, t0))
        assertEquals(listOf("d"), GearTimers.due(data, t0).map { it.id })
        assertNull(GearTimers.nextEnd(AppData(), t0))
    }

    @Test
    fun theStopwatchCountsLapsAndNeverGoesBackwards() {
        var g = GearData()
        g = GearTimers.stopwatchStart(g, t0)
        g = GearTimers.stopwatchStart(g, t0 + 5_000)
        assertEquals(t0, g.stopwatch.runningSince, "a second start does not restart it")
        g = GearTimers.stopwatchLap(g, t0 + 12_345)
        g = GearTimers.stopwatchLap(g, t0 + 30_000)
        assertEquals(listOf(12_345L, 30_000L), g.stopwatch.laps)
        assertEquals(listOf(12_345L, 17_655L), GearTimers.splits(g.stopwatch.laps))
        g = GearTimers.stopwatchPause(g, t0 + 40_000)
        assertEquals(40_000, g.stopwatch.accumulatedMs)
        assertEquals(40_000, g.stopwatch.elapsedAt(t0 + 999_999), "stopped: it stays")
        assertEquals(g, GearTimers.stopwatchLap(g, t0 + 50_000), "no laps while stopped")
        g = GearTimers.stopwatchStart(g, t0 + 100_000)
        assertEquals(45_000, g.stopwatch.elapsedAt(t0 + 105_000))
        // A clock set back never shows less than what was gathered.
        assertEquals(40_000, g.stopwatch.elapsedAt(t0 + 50_000))
        g = GearTimers.stopwatchReset(g)
        assertEquals(GearStopwatch(), g.stopwatch)
    }

    @Test
    fun theStorePersistsTimersAndPaysNothing() {
        val store = GameStore(MemoryStorage(), object : Clock { var now = t0; override fun nowMillis() = now }, kotlinx.datetime.TimeZone.UTC, kotlin.random.Random(1)).also { it.start("Sophia", 25) }
        val points = store.data.player.trainerPoints
        val bag = store.data.player.inventory.counts.values.sum()
        val timer = assertNotNull(store.timerAdd("Eggs", 60_000))
        store.timerStart(timer.id)
        assertTrue(store.data.gear.timers.single().running)
        assertTrue(store.timersTick().isEmpty())
        store.stopwatchStart(); store.stopwatchLap(); store.stopwatchPause()
        // Nothing a utility does pays or records anything else.
        assertEquals(points, store.data.player.trainerPoints)
        assertEquals(bag, store.data.player.inventory.counts.values.sum())
        assertTrue(store.data.rewardLog.isEmpty())
        // They are in the save and survive a reload.
        val saved = (store.data.gear)
        assertEquals(saved, GameStore.json.decodeFromString(AppData.serializer(), GameStore.json.encodeToString(AppData.serializer(), store.data)).gear)
    }

    @Test
    fun aTimerCanBeRenamedAndResizedAndGoesBackToReady() {
        var g = GearTimers.add(GearData(), "a", "Tea", 180_000)!!
        g = GearTimers.start(g, "a", t0)
        g = GearTimers.edit(g, "a", " Pasta ", 600_000)
        val t = g.timers.single()
        assertEquals("Pasta", t.label)
        assertEquals(600_000, t.durationMs)
        assertFalse(t.running || t.paused || t.finished)
        assertEquals(g, GearTimers.edit(g, "a", "Tea", 0), "a length outside the limits changes nothing")
        assertEquals("Pasta", GearTimers.edit(g, "a", "  ", 60_000).timers.single().label, "a blank name keeps the old one")
    }
}
