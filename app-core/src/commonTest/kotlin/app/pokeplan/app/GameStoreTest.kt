package app.pokeplan.app

import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.TaskState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advance(ms: Long) { millis += ms }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()

    private fun store(storage: Storage = MemoryStorage(), clock: FakeClock = FakeClock(start)) =
        GameStore(storage, clock, utc, Random(7)) to clock

    private fun started(): Pair<GameStore, FakeClock> = store().also { it.first.start("Sophia", 25) }

    private fun hatchOne(s: GameStore, clock: FakeClock) {
        s.startFocus(25, deepFocus = false)
        clock.advance(25 * 60_000L + 1)
        s.tickFocus()
    }

    @Test
    fun startCreatesTheTrainerBuddyAndAFirstTask() {
        val (s, _) = started()
        assertEquals("Sophia", s.data.trainerName)
        assertTrue(s.data.onboarded)
        val buddy = assertNotNull(s.data.player.buddy)
        assertEquals(25, buddy.speciesId)
        assertTrue(buddy.inParty)
        assertTrue(25 in s.data.player.caught)
        assertTrue(s.data.tasks.single().tutorial)
    }

    @Test
    fun theSaveSurvivesARestart() {
        val storage = MemoryStorage()
        val (s, clock) = store(storage)
        s.start("Sophia", 4)
        s.addTask("Buy milk", Rank.D)
        val again = GameStore(storage, clock, utc)
        assertEquals(s.data, again.data)
    }

    @Test
    fun aDamagedSaveStartsFreshInsteadOfCrashing() {
        val s = GameStore(MemoryStorage("{not json"), FakeClock(start), utc)
        assertFalse(s.data.onboarded)
    }

    @Test
    fun theTutorialTaskPaysAfterTheUndoWindow() {
        val (s, clock) = started()
        val task = s.data.tasks.single()
        val pending = s.completeTask(task.id)
        assertTrue(pending.pays)
        assertEquals(TaskState.DONE, s.data.tasks.single().state)
        assertTrue(s.data.rewardLog.isEmpty(), "nothing is paid inside the undo window")
        clock.advance(10_001)
        s.tick()
        assertTrue(s.data.pending.isEmpty())
        val entry = s.data.rewardLog.single()
        assertTrue(entry.rewards.any { it is Reward.Candies })
        assertTrue(s.today in s.data.activeDays)
        assertTrue(s.data.player.trainerPoints > 0)
    }

    @Test
    fun undoCancelsTheReward() {
        val (s, clock) = started()
        val task = s.data.tasks.single()
        s.completeTask(task.id)
        clock.advance(5_000)
        assertTrue(s.undoCompletion(task.id))
        assertEquals(TaskState.OPEN, s.data.tasks.single().state)
        clock.advance(10_000)
        s.tick()
        assertTrue(s.data.rewardLog.isEmpty())
        assertEquals(0, s.data.player.trainerPoints)
    }

    @Test
    fun undoIsTooLateAfterTheWindow() {
        val (s, clock) = started()
        val task = s.data.tasks.single()
        s.completeTask(task.id)
        clock.advance(10_001)
        assertFalse(s.undoCompletion(task.id))
    }

    @Test
    fun aTaskFinishedTooSoonPaysNothingButPaysOnceLater() {
        val (s, clock) = started()
        val task = s.addTask("Write report", Rank.C)
        s.completeTask(task.id)
        clock.advance(10_001)
        s.tick()
        assertTrue(s.data.rewardLog.isEmpty())
        s.reopen(task.id)
        clock.advance(11 * 60_000L)
        s.completeTask(task.id)
        clock.advance(10_001)
        s.tick()
        assertEquals(1, s.data.rewardLog.size)
        s.reopen(task.id)
        s.completeTask(task.id)
        clock.advance(10_001)
        s.tick()
        assertEquals(1, s.data.rewardLog.size, "a task never pays twice")
    }

    @Test
    fun aHabitPaysOncePerDay() {
        val (s, _) = started()
        val habit = s.addHabit("Stretch", HabitTarget.Daily, Rank.E)
        s.completeHabit(habit.id)
        assertEquals(1, s.data.rewardLog.size)
        s.completeHabit(habit.id)
        assertEquals(1, s.data.rewardLog.size, "the second tick the same day pays nothing")
        assertEquals(setOf(s.today), s.data.habits.single().completions)
    }

    @Test
    fun aCompletedFocusSessionHatchesAnEgg() {
        val (s, clock) = started()
        s.startFocus(25, deepFocus = false)
        assertNotNull(s.data.focus)
        val before = s.data.player.pokemon.size
        clock.advance(25 * 60_000L + 1)
        val result = s.tickFocus()
        assertIs<FocusResult.Hatched>(result)
        assertNull(s.data.focus)
        assertEquals(before + 1, s.data.player.pokemon.size)
        assertTrue(s.data.focusHistory.single().completed)
    }

    @Test
    fun leavingTheAppForTooLongFailsTheSession() {
        val (s, clock) = started()
        s.startFocus(25, deepFocus = false)
        clock.advance(60_000)
        assertNull(s.focusEvent(FocusEvent.Left))
        clock.advance(11_000)
        assertEquals(FocusResult.Failed(FailReason.LEFT_APP), s.tickFocus())
        assertNull(s.data.focus)
        assertFalse(s.data.focusHistory.single().completed)
        assertEquals(1, s.data.player.pokemon.size)
    }

    @Test
    fun comingBackInTimeKeepsTheSession() {
        val (s, clock) = started()
        s.startFocus(25, deepFocus = false)
        s.focusEvent(FocusEvent.Left)
        clock.advance(5_000)
        s.focusEvent(FocusEvent.Returned)
        clock.advance(5 * 60_000L)
        assertNull(s.tickFocus())
        assertNotNull(s.data.focus)
    }

    @Test
    fun rareCandiesLevelUpThePokemon() {
        val (s, _) = started()
        val id = s.data.player.buddyId!!
        s.grant(Items.RARE_CANDY, 25)
        assertEquals(25, s.feedRareCandy(id, 25))
        assertTrue(s.data.player.buddy!!.level > 1)
        assertEquals(0, s.data.player.inventory[Items.RARE_CANDY])
    }

    @Test
    fun theBuddyCannotBeTransferredOrBenched() {
        val (s, _) = started()
        val id = s.data.player.buddyId!!
        assertFalse(s.transfer(id))
        assertFalse(s.setInParty(id, false))
    }

    @Test
    fun partyHoldsAtMostSix() {
        val (s, clock) = started()
        repeat(8) { hatchOne(s, clock) }
        assertEquals(9, s.data.player.pokemon.size)
        assertEquals(6, s.data.player.party.size)
        val benched = s.data.player.pokemon.first { !it.inParty }
        assertFalse(s.setInParty(benched.id, true))
    }

    @Test
    fun transferGivesCandiesAndCoinsAndKeepsThePokedexEntry() {
        val (s, clock) = started()
        hatchOne(s, clock)
        val hatched = s.data.player.pokemon.last()
        val coins = s.data.player.coins
        assertTrue(s.transfer(hatched.id))
        assertTrue(s.data.player.coins > coins)
        assertTrue(s.data.player.inventory[Items.RARE_CANDY] > 0)
        assertTrue(hatched.speciesId in s.data.player.caught)
    }

    @Test
    fun recurringTasksAreCreatedAheadWithoutDuplicates() {
        val (s, _) = started()
        val template = s.addTask("Water plants", Rank.E).copy(planned = s.today)
        s.addRecurring(template, Recurrence(Repeat.Daily(), s.today))
        val count = s.data.tasks.count { it.seriesId != null }
        assertTrue(count >= 14)
        s.refresh()
        assertEquals(count, s.data.tasks.count { it.seriesId != null })
    }

    @Test
    fun streakCountsActiveDays() {
        val (s, clock) = started()
        s.completeTask(s.data.tasks.single().id)
        clock.advance(10_001)
        s.tick()
        assertEquals(1, s.streak.current)
    }
}
