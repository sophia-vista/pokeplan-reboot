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
import kotlinx.datetime.plus
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

    @Test
    fun aMilestonePaysASuperCandyOnceItIsADayOld() {
        val (s, clock) = started()
        val goal = s.addGoal("Learn guitar")
        s.addMilestone(goal.id, "First chord")
        val milestone = s.data.goals.single().milestones.single()
        clock.advance(25 * 3_600_000L)
        val result = s.completeMilestone(goal.id, milestone.id)
        assertTrue(result.pays)
        assertTrue(result.goalCompleted)
        assertEquals(1, s.data.player.inventory[Items.SUPER_CANDY])
        assertNotNull(s.data.goals.single().completedAtMillis)
        assertTrue(s.data.rewardLog.last().rewards.size >= 2, "the goal bonus item comes with it")
    }

    @Test
    fun aMilestoneTickedStraightAwayPaysNothing() {
        val (s, _) = started()
        val goal = s.addGoal("Learn guitar")
        s.addMilestone(goal.id, "First chord")
        val result = s.completeMilestone(goal.id, s.data.goals.single().milestones.single().id)
        assertFalse(result.pays)
        assertEquals(0, s.data.player.inventory[Items.SUPER_CANDY])
    }

    @Test
    fun theWeeklyReviewPaysABerryOnceAndCarriesTasksForward() {
        val (s, _) = started()
        val open = s.addTask("Unfinished", Rank.C, planned = s.today)
        assertTrue(s.canReview())
        s.completeReview("Went fine", carryTo = s.today.plus(kotlinx.datetime.DatePeriod(days = 1)))
        assertFalse(s.canReview())
        assertEquals(1, s.data.reviews.size)
        assertEquals(s.today.plus(kotlinx.datetime.DatePeriod(days = 1)), s.data.tasks.first { it.id == open.id }.planned)
        assertTrue(s.data.rewardLog.last().rewards.single() is Reward.ItemDrop)
    }

    @Test
    fun restDaysAreLimited() {
        val (s, _) = started()
        assertTrue(s.toggleRestDay(s.today))
        assertTrue(s.today in s.data.restDays)
        assertTrue(s.toggleRestDay(s.today))
        assertFalse(s.today in s.data.restDays)
    }

    private fun GameStore.withPoints(points: Int): GameStore {
        // Rank is decided by trainer points; the test store is loaded from a save that already has them.
        val text = GameStore.json.encodeToString(AppData.serializer(), data.copy(player = data.player.copy(trainerPoints = points)))
        return GameStore(MemoryStorage(text), Clock { nowMillis }, kotlinx.datetime.TimeZone.UTC, Random(8))
    }

    private val mewSpecies = app.pokeplan.core.dex.Dex.species.first { it.identifier == "mew" }.id
    private val uxieSpecies = app.pokeplan.core.dex.Dex.species.first { it.identifier == "uxie" }.id

    @Test
    fun aGuardianCannotBeTheBuddyOrJoinTheParty() {
        val (s0, _) = started()
        val s = s0.withPoints(3_300)
        val mew = s.grantPokemon(mewSpecies, "Quest: Mew")
        assertFalse(mew.inParty)
        assertFalse(s.setBuddy(mew.id))
        assertFalse(s.setInParty(mew.id, true))
        assertEquals(0, s.feedRareCandy(mew.id, 5))
        assertFalse(s.transfer(mew.id), "legendaries cannot be transferred")
        assertTrue(mewSpecies in s.data.player.caught)
    }

    @Test
    fun guardiansNeedASlotAndSlotsOpenWithRank() {
        val (s0, _) = started()
        val low = s0.withPoints(700)
        val mew = low.grantPokemon(mewSpecies, "Quest")
        assertEquals(0, low.guardianSlots)
        assertFalse(low.slotGuardian(mew.id))

        val s = s0.withPoints(3_300) // rank 4: one slot
        val m = s.grantPokemon(mewSpecies, "Quest")
        val u = s.grantPokemon(uxieSpecies, "Quest")
        assertEquals(1, s.guardianSlots)
        assertTrue(s.slotGuardian(m.id))
        assertFalse(s.slotGuardian(m.id), "already slotted")
        assertFalse(s.slotGuardian(u.id), "no free slot")
        s.unslotGuardian(m.id)
        assertTrue(s.slotGuardian(u.id))
        assertEquals(listOf(u.id), s.data.player.guardians)
        assertFalse(s.slotGuardian(s.data.player.buddyId!!), "an ordinary Pokémon is not a Guardian")
    }

    @Test
    fun aSlottedGuardianBoostsRewards() {
        val (s0, clock) = started()
        val s = s0.withPoints(3_300)
        val u = s.grantPokemon(uxieSpecies, "Quest")
        s.slotGuardian(u.id)
        val perks = app.pokeplan.core.pokemon.Perks.total(s.data.player.buddy, s.data.player.party, guardians = s.data.player.guardianPokemon)
        assertEquals(app.pokeplan.core.pokemon.Guardians.SIGNATURE_PERCENT,
            perks[app.pokeplan.core.pokemon.PerkBucket.SUPER_CANDY_CHANCE] - app.pokeplan.core.pokemon.Perks.total(s.data.player.buddy, s.data.player.party)[app.pokeplan.core.pokemon.PerkBucket.SUPER_CANDY_CHANCE], 1e-9)
    }

    @Test
    fun aQuestIsAcceptedProgressesAndPaysItsGuardianWhenClaimed() {
        val (s0, clock) = started()
        val s = s0.withPoints(3_300) // rank 4
        val azelf = app.pokeplan.app.quest.Quests.get("azelf")!!
        assertTrue(s.availableQuests.any { it.id == "azelf" })
        assertTrue(s.acceptQuest("azelf"))
        assertFalse(s.acceptQuest("azelf"), "already taken")
        assertEquals(1, s.activeQuests.size)
        assertFalse(s.questReady(azelf))
        assertEquals(null, s.claimQuest("azelf"))

        // Finish what it asks: 10 Deep Focus sessions, 10 hard tasks, and a 7 day streak.
        repeat(10) {
            s.startFocus(25, deepFocus = true)
            clock.advance(25 * 60_000L + 1)
            s.tickFocus()
        }
        val progress = s.questProgress(azelf)
        assertEquals(10, progress.first { it.first.kind == app.pokeplan.app.quest.RequirementKind.DEEP_FOCUS_SESSIONS }.second)
        assertFalse(s.questReady(azelf), "tasks and the streak are still missing")
    }

    @Test
    fun atMostThreeQuestsAreActiveAtOnce() {
        val (s0, _) = started()
        val s = s0.withPoints(10_000)
        val ids = s.availableQuests.take(4).map { it.id }
        assertEquals(4, ids.size)
        assertTrue(s.acceptQuest(ids[0]))
        assertTrue(s.acceptQuest(ids[1]))
        assertTrue(s.acceptQuest(ids[2]))
        assertFalse(s.acceptQuest(ids[3]))
    }
}
