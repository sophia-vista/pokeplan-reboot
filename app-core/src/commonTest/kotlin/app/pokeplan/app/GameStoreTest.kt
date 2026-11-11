package app.pokeplan.app

import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.TaskState
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
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
    fun everyNewPokemonRemembersWhereItCameFrom() {
        val (s, clock) = started()
        assertEquals(Origin(Origin.How.STARTER), s.data.origins[s.data.player.buddyId])
        hatchOne(s, clock)
        val hatched = s.data.player.pokemon.last()
        assertEquals(Origin(Origin.How.FOCUS_EGG, app.pokeplan.core.dex.Region.PLAN), s.data.origins[hatched.id])
        val quest = s.grantPokemon(16, "Quest: Pidgey")
        assertEquals(Origin(Origin.How.QUEST), s.data.origins[quest.id])
        assertTrue(s.transfer(quest.id))
        assertFalse(quest.id in s.data.origins, "a transferred Pokémon's origin goes with it")
    }

    @Test
    fun startCreatesTheTrainerBuddyAndAFirstTask() {
        val (s, _) = started()
        assertEquals("Sophia", s.data.trainerName)
        assertTrue(s.data.onboarded)
        val buddy = assertNotNull(s.data.player.buddy)
        assertEquals(25, buddy.speciesId)
        assertFalse(buddy.inParty, "the starter is the buddy and in no party: one place only")
        assertEquals(Place.Buddy, s.placeOf(buddy.id))
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
        val storage = MemoryStorage("{not json")
        val s = GameStore(storage, FakeClock(start), utc)
        assertFalse(s.data.onboarded)
        assertEquals("{not json", storage.keptAside, "the damaged save is kept where the next write cannot reach it")
        s.start("Ash", 25)
        assertEquals("{not json", storage.keptAside)
    }

    @Test
    fun aSaveTheRulesRejectIsKeptAsideToo() {
        val (s, _) = started()
        val good = GameStore.json.encodeToString(AppData.serializer(), s.data)
        val bad = good.replace("\"title\":\"${s.data.tasks.single().title}\"", "\"title\":\" \"")
        val storage = MemoryStorage(bad)
        assertFalse(GameStore(storage, FakeClock(start), utc).data.onboarded)
        assertEquals(bad, storage.keptAside)
    }

    @Test
    fun reloadIgnoresASaveCaughtHalfWritten() {
        val storage = MemoryStorage()
        val s = GameStore(storage, FakeClock(start), utc).also { it.start("Ash", 25) }
        storage.text = storage.text!!.take(40)
        s.reload()
        assertTrue(s.data.onboarded, "a half-written save never replaces the progress in memory")
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
        val habit = s.addFrequencyTask("Stretch", HabitTarget.Daily, Rank.E)
        s.completeFrequencyTask(habit.template.id)
        assertEquals(1, s.data.rewardLog.size)
        s.completeFrequencyTask(habit.template.id)
        assertEquals(1, s.data.rewardLog.size, "the second tick the same day pays nothing")
        assertEquals(setOf(s.today), s.frequencyTasks().single().completions)
        s.undoFrequencyTask(habit.template.id)
        assertEquals(emptySet(), s.frequencyTasks().single().completions)
        s.completeFrequencyTask(habit.template.id)
        assertEquals(1, s.data.rewardLog.size, "undoing and ticking again the same day pays nothing")
    }

    @Test
    fun habitsOfAnOlderSaveBecomeRecurringTasksWithTheirHistoryAndPayNothing() {
        val (s, _) = started()
        val day = s.today
        val habit = app.pokeplan.plan.Habit("h1", "Read", Rank.D, app.pokeplan.plan.Category.PERSONAL, HabitTarget.PerWeek(3), day.minus(DatePeriod(days = 10)),
            completions = setOf(day.minus(DatePeriod(days = 2)), day.minus(DatePeriod(days = 1))), paidOn = day.minus(DatePeriod(days = 1)))
        val goal = s.addGoal("Learn")
        val old = s.data.copy(habits = listOf(habit), goals = listOf(goal.copy(linkedHabitIds = setOf("h1"))))
        val storage = MemoryStorage(GameStore.json.encodeToString(AppData.serializer(), old))
        val coins = old.player.coins
        val reopened = GameStore(storage, FakeClock(start), utc, Random(7))
        val series = reopened.frequencyTasks().single()
        assertEquals("h1", series.template.id, "the id is kept, so the goal that links it still does")
        assertEquals(setOf("h1"), reopened.data.goals.single().linkedHabitIds)
        assertEquals(HabitTarget.PerWeek(3), series.target)
        assertEquals(habit.completions, series.completions); assertEquals(habit.paidOn, series.paidOn); assertEquals(habit.createdOn, series.createdOn)
        assertEquals(listOf(habit), reopened.data.legacyHabits, "the old habits are kept aside")
        assertTrue(reopened.data.habits.isEmpty()); assertNotNull(reopened.data.habitsMergedAtMillis)
        assertEquals(coins, reopened.data.player.coins, "the migration pays nothing")
        assertEquals(old.rewardLog, reopened.data.rewardLog)
        // The history counts as progress and as a streak; yesterday's payment is not paid again, and a new day's first tick pays once.
        assertEquals(0, reopened.frequencyProgress(series)!!.done, "the test day is a Monday: the two days before it belong to last week")
        assertEquals(3, reopened.frequencyProgress(series)!!.target)
        assertEquals(habit.streak(day), reopened.frequencyStreak(series))
        // Loading again changes nothing (idempotent).
        val again = GameStore(storage, FakeClock(start), utc, Random(7))
        assertEquals(1, again.data.series.size)
        reopened.completeFrequencyTask("h1")
        assertEquals(old.rewardLog.size + 1, reopened.data.rewardLog.size)
        reopened.completeFrequencyTask("h1")
        assertEquals(old.rewardLog.size + 1, reopened.data.rewardLog.size)
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
        assertFalse(s.setInParty(id, false), "the buddy is in no party to leave")
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
    fun aPokemonIsInOnePartyAtATimeAndCareHasItsOwn() {
        val (s, clock) = started()
        hatchOne(s, clock); hatchOne(s, clock); hatchOne(s, clock)
        val buddy = s.data.player.buddyId!!
        val (first, second, third) = s.data.player.party.map { it.id }
        assertTrue(s.setInParty(second, true, PartyIds.CARE))
        assertEquals(listOf(second), s.data.player.partyOf(PartyIds.CARE).map { it.id })
        assertEquals(listOf(first, third), s.data.player.party.map { it.id })
        assertEquals(Place.Party(PartyIds.CARE), s.placeOf(second))
        // Moving back takes it out of Care's party; the buddy joining Care stops being the buddy (one place only).
        assertTrue(s.setInParty(second, true, PartyIds.PLAN))
        assertTrue(s.data.player.partyOf(PartyIds.CARE).isEmpty())
        assertTrue(s.setInParty(buddy, true, PartyIds.CARE))
        assertNull(s.data.player.buddyId)
        assertEquals(buddy, s.data.player.careLead?.id)
        assertEquals(TransferBlock.BUDDY.takeIf { false }, null)
        // A Care member that is not the lead transfers, and the lead passes on when it leaves.
        assertTrue(s.setInParty(third, true, PartyIds.CARE))
        assertNull(s.transferBlock(third))
        assertTrue(s.setCareLead(third))
        assertEquals(third, s.data.player.careLead?.id)
        assertTrue(s.setInParty(third, false))
        assertEquals(buddy, s.data.player.careLead?.id)
        assertFalse(s.setCareLead(first))
    }

    @Test
    fun transferGivesCandiesAndCoinsAndKeepsThePokedexEntry() {
        val (s, clock) = started()
        hatchOne(s, clock); hatchOne(s, clock)
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
    fun aTaskCanBeMadeToRepeatAndKeepsItsOwnDay() {
        val (s, _) = started()
        val task = s.addTask("Water plants", Rank.E)
        assertTrue(s.makeRepeating(task.id, Repeat.Daily()))
        // It had no day, so it is now planned for today; the repeat starts after it, with no second task on the same day.
        val own = s.data.tasks.first { it.id == task.id }
        assertEquals(s.today, own.planned)
        assertNull(own.seriesId)
        assertNotNull(s.seriesOf(own))
        val later = s.data.tasks.filter { it.seriesId != null }
        assertTrue(later.size >= 13)
        assertTrue(later.all { it.planned!! > s.today })
        assertEquals(1, s.data.tasks.count { it.title == "Water plants" && it.planned == s.today })
        s.refresh()
        assertEquals(later.size, s.data.tasks.count { it.seriesId != null })
    }

    @Test
    fun aTaskThatRepeatsStillWaitsForItsMinimumAge() {
        val (s, _) = started()
        val task = s.addTask("Water plants", Rank.C)
        s.makeRepeating(task.id, Repeat.Daily())
        val pending = s.completeTask(task.id)
        assertFalse(pending.pays)
    }

    @Test
    fun changingARepeatMakesTheLaterOnesAgainOnTheNewSchedule() {
        val (s, _) = started()
        val task = s.addTask("Bins out", Rank.E)
        s.makeRepeating(task.id, Repeat.Daily())
        s.makeRepeating(task.id, Repeat.Weekly())
        val later = s.data.tasks.filter { it.seriesId != null }
        assertEquals(2, later.size)
        assertTrue(later.all { it.planned!!.dayOfWeek == s.today.dayOfWeek })
    }

    @Test
    fun stoppingARepeatRemovesTheUntouchedLaterOnesAndKeepsTheTask() {
        val (s, _) = started()
        val task = s.addTask("Water plants", Rank.E)
        s.makeRepeating(task.id, Repeat.Daily())
        assertTrue(s.data.tasks.any { it.seriesId != null })
        s.stopRepeating(task.id)
        assertTrue(s.data.series.isEmpty())
        assertTrue(s.data.tasks.none { it.seriesId != null })
        assertNotNull(s.data.tasks.firstOrNull { it.id == task.id })
        assertNull(s.seriesOf(s.data.tasks.first { it.id == task.id }))
    }

    @Test
    fun anInstanceThatWasWorkedOnIsKeptWhenARepeatStops() {
        val (s, _) = started()
        val task = s.addTask("Water plants", Rank.E)
        s.makeRepeating(task.id, Repeat.Daily())
        val tomorrow = s.data.tasks.first { it.seriesId != null }
        s.addSubtask(tomorrow.id, "Fill the can")
        s.completeSubtask(tomorrow.id, s.data.tasks.first { it.id == tomorrow.id }.subtasks.single().id)
        s.stopRepeating(tomorrow.id)
        assertEquals(listOf(tomorrow.id), s.data.tasks.filter { it.seriesId != null }.map { it.id })
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
    fun aGuardianCannotBeTheBuddyButCanJoinAParty() {
        val (s0, _) = started()
        val s = s0.withPoints(3_300)
        val mew = s.grantPokemon(mewSpecies, "Quest: Mew")
        assertFalse(mew.inParty)
        assertFalse(s.setBuddy(mew.id))
        assertTrue(s.setInParty(mew.id, true), "a legendary is a normal Pokémon for a party")
        assertEquals(0, s.feedRareCandy(mew.id, 5))
        assertFalse(s.transfer(mew.id), "legendaries cannot be transferred")
        assertTrue(mewSpecies in s.data.player.caught)
    }

    @Test
    fun everyModuleHasOneGuardianSlotAtAnyRank() {
        val (s, _) = started()
        val mew = s.grantPokemon(mewSpecies, "Quest")
        val uxie = s.grantPokemon(uxieSpecies, "Quest")
        assertTrue(s.slotGuardian(mew.id, PartyIds.CARE), "no rank to wait for")
        assertEquals(mew.id, s.guardianOf(PartyIds.CARE)?.id)
        assertEquals(app.pokeplan.app.Place.Guardian(PartyIds.CARE), app.pokeplan.app.Places.of(s.data, mew.id))
        // One place only: moving it to another module empties the first slot.
        assertTrue(s.slotGuardian(mew.id, PartyIds.HEAL))
        assertEquals(null, s.guardianOf(PartyIds.CARE))
        assertEquals(mew.id, s.guardianOf(PartyIds.HEAL)?.id)
        // A second Guardian can hold another module, and replacing one sends the old one to the PC.
        assertTrue(s.slotGuardian(uxie.id, PartyIds.PLAN))
        assertTrue(s.slotGuardian(uxie.id, PartyIds.HEAL))
        assertEquals(uxie.id, s.guardianOf(PartyIds.HEAL)?.id)
        assertEquals(app.pokeplan.app.Place.Pc, app.pokeplan.app.Places.of(s.data, mew.id), "the one it replaced goes to the PC")
        s.unslotGuardian(PartyIds.HEAL)
        assertEquals(app.pokeplan.app.Place.Pc, app.pokeplan.app.Places.of(s.data, uxie.id))
        assertFalse(s.slotGuardian(s.data.player.buddyId!!, PartyIds.PLAN), "an ordinary Pokémon is not a Guardian")
        assertFalse(s.slotGuardian(mew.id, "gear"), "a place that is not a module has no slot")
    }

    @Test
    fun aGuardianIsANormalPokemonForPartiesAndTeamsAndTheSlotTakesItFromThem() {
        val (s, _) = started()
        val mew = s.grantPokemon(mewSpecies, "Quest")
        assertTrue(s.setInParty(mew.id, true, PartyIds.CARE))
        assertTrue(s.slotGuardian(mew.id, PartyIds.PLAN), "slotting takes it out of the party")
        assertFalse(s.data.player.pokemon.first { it.id == mew.id }.inParty)
        assertTrue(s.setInParty(mew.id, true, PartyIds.CARE), "joining a party takes it out of the slot")
        assertEquals(null, s.guardianOf(PartyIds.PLAN))
        assertTrue(s.worldAssign(1, listOf(mew.id)), "and it can be on a World team")
        assertEquals(app.pokeplan.app.Place.WorldTeam(1), app.pokeplan.app.Places.of(s.data, mew.id))
    }

    @Test
    fun anOldGlobalGuardianListBecomesTheFirstModulesInOrder() {
        val (s, _) = started()
        val a = s.grantPokemon(mewSpecies, "Quest")
        val b = s.grantPokemon(uxieSpecies, "Quest")
        val old = s.data.copy(player = s.data.player.copy(guardians = listOf(a.id, "gone", b.id)))
        val moved = app.pokeplan.app.Places.guardiansToModules(old)
        assertEquals(mapOf(PartyIds.PLAN to a.id, PartyIds.CARE to b.id), moved.player.moduleGuardians)
        assertTrue(moved.player.guardians.isEmpty())
        assertEquals(moved, app.pokeplan.app.Places.guardiansToModules(moved), "once")
    }

    @Test
    fun aSlottedGuardianBoostsRewards() {
        val (s0, clock) = started()
        val s = s0.withPoints(TrainerRank.thresholds[4])
        val u = s.grantPokemon(uxieSpecies, "Quest")
        s.slotGuardian(u.id, PartyIds.PLAN)
        val perks = app.pokeplan.core.pokemon.Perks.total(s.data.player.buddy, s.data.player.party, guardians = s.data.player.guardianPokemon)
        assertEquals(app.pokeplan.core.pokemon.Guardians.SIGNATURE_PERCENT,
            perks[app.pokeplan.core.pokemon.PerkBucket.SUPER_CANDY_CHANCE] - app.pokeplan.core.pokemon.Perks.total(s.data.player.buddy, s.data.player.party)[app.pokeplan.core.pokemon.PerkBucket.SUPER_CANDY_CHANCE], 1e-9)
    }

    @Test
    fun everyStarterCanBeChosen() {
        assertEquals(29, GameStore.starters.size)
        assertEquals(GameStore.starters.size, GameStore.starters.toSet().size)
        for (id in GameStore.starters) {
            val (s, _) = store()
            s.start("Sophia", id)
            assertEquals(id, s.data.player.buddy!!.speciesId)
            assertFalse(s.data.player.buddy!!.inParty)
        }
    }

    @Test
    fun aQuestIsAcceptedProgressesAndPaysItsGuardianWhenClaimed() {
        val (s0, clock) = started()
        val s = s0.withPoints(TrainerRank.thresholds[4]) // rank 4
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

    @Test
    fun petting_the_buddy_gives_friendship_up_to_the_daily_cap_and_resets_tomorrow() {
        val (s, clock) = started()
        val before = s.data.player.buddy!!.friendship
        var total = 0
        repeat(20) { total += s.pet() }
        assertTrue(total in 10..11, "gained $total")
        assertEquals(before + total, s.data.player.buddy!!.friendship)
        assertEquals(0, s.pet())
        clock.advance(24 * 3_600_000L)
        assertTrue(s.pet() > 0)
        assertEquals(1, s.data.counters.keys.count { it.startsWith("pet-") })
    }

    @Test
    fun a_change_never_writes_an_older_copy_over_another_stores_change() {
        // The app and a notification each open their own store on the same document of the same process.
        val storage = MemoryStorage(shareKey = "a_change_never_writes_an_older_copy")
        val clock = FakeClock(start)
        val app = GameStore(storage, clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val notification = GameStore(storage, clock, utc, Random(8))
        val task = notification.addTask("Call the bank")
        assertTrue(app.data.tasks.none { it.id == task.id }, "the open app has not looked yet")
        // The app's next change starts from the saved document, not from its older copy.
        app.addTask("Water the plants")
        assertTrue(app.data.tasks.any { it.id == task.id })
        assertTrue(GameStore(storage, clock, utc, Random(9)).data.tasks.any { it.id == task.id }, "and the save still has it")
        // Without a change of its own, the tick picks it up and tells the screen once.
        val second = notification.addTask("Post the letter")
        var told = 0
        app.observe { told++ }
        app.tick()
        assertTrue(app.data.tasks.any { it.id == second.id })
        assertEquals(1, told)
        app.tick()
        assertEquals(1, told, "nothing new the second time")
    }

    @Test
    fun reload_picks_up_a_change_made_by_another_store_on_the_same_save() {
        val storage = MemoryStorage()
        val clock = FakeClock(start)
        val app = GameStore(storage, clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val widget = GameStore(storage, clock, utc, Random(8))
        val before = widget.data.player.buddy!!.friendship
        assertTrue(widget.pet() > 0)
        assertEquals(before, app.data.player.buddy!!.friendship)
        var seen: AppData? = null
        app.observe { seen = it }
        app.reload()
        assertTrue(app.data.player.buddy!!.friendship > before)
        assertNotNull(seen)
        seen = null
        app.reload()
        assertNull(seen, "nothing changed the second time")
    }

    @Test
    fun the_buddy_only_has_an_event_for_a_few_minutes_after_a_payout() {
        val (s, clock) = started()
        assertNull(s.buddyEvent())
        s.completeTask(s.data.tasks.single().id)
        clock.advance(11_000)
        s.tick()
        assertEquals(BuddyEvent.DONE, s.buddyEvent()?.event)
        clock.advance(6 * 60_000L)
        assertNull(s.buddyEvent())
    }

    @Test
    fun a_module_lead_celebrates_only_what_its_module_paid() {
        val (s, clock) = started()
        s.completeTask(s.data.tasks.single().id)
        clock.advance(11_000)
        s.tick()
        assertNotNull(s.moduleEvent(null), "a finished task is Plan's moment")
        assertNull(s.moduleEvent(GameStore.HEAL_LOG_PREFIX), "and not Heal's")
        assertNull(s.moduleEvent(GameStore.CARE_LOG_PREFIX), "nor Care's")
    }

    @Test
    fun level_progress_follows_the_candies_fed() {
        val (s, _) = started()
        val buddy = s.data.player.buddy!!
        assertEquals(0f, s.levelProgress(buddy))
        val toThirty = app.pokeplan.core.pokemon.CandyRules.candiesBetween(buddy.level, 30)
        s.grant("rare-candy", toThirty + 3)
        s.feedRareCandy(buddy.id, toThirty + 3)
        assertEquals(30, s.data.player.buddy!!.level)
        assertEquals(3f / 5, s.levelProgress(s.data.player.buddy!!), 0.001f, "5 candies a level at 30")
    }

    @Test
    fun the_scene_follows_the_type_and_night_falls_in_the_evening() {
        assertEquals("mountain", BuddyScene.of(listOf(app.pokeplan.core.dex.PokemonType.FIRE)))
        assertEquals("meadow", BuddyScene.of(emptyList()))
        assertTrue(BuddyScene.isNight(22) && BuddyScene.isNight(3) && !BuddyScene.isNight(12))
    }

    @Test
    fun pet_reactions_come_from_the_buddys_bond_style_type_and_time_of_day() {
        val (s, _) = started()
        val buddy = s.data.player.buddy!!
        val pool = BuddyReaction.petPool(buddy, hour = 22)
        assertTrue(pool.size >= 8, "a wide set, got $pool")
        assertTrue(pool.any { it in listOf("😴", "💤", "🌙") }, "night adds a sleepy one")
        val random = Random(3)
        val seen = (1..200).map { BuddyReaction.pet(buddy, 22, random) }.toSet()
        assertTrue(seen.all { it in pool })
        assertTrue(seen.size >= 5, "should vary, got $seen")
    }

    @Test
    fun the_scene_darkens_slowly_and_a_pet_never_repeats_its_emoji() {
        assertEquals(0f, BuddyScene.darkness(12, 0))
        assertEquals(BuddyScene.MAX_DARKNESS, BuddyScene.darkness(23, 0))
        assertTrue(BuddyScene.darkness(19, 30) > 0f && BuddyScene.darkness(19, 30) < BuddyScene.MAX_DARKNESS)
        assertTrue(BuddyScene.darkness(6, 0) > 0f && BuddyScene.darkness(8, 0) == 0f)
        val (s, _) = started()
        val buddy = s.data.player.buddy!!
        val random = Random(5)
        var last: String? = null
        repeat(100) {
            val next = BuddyReaction.pet(buddy, 14, random, avoid = last)
            assertTrue(next != last, "repeated $next")
            last = next
        }
    }

    @Test
    fun a_second_tap_on_start_keeps_the_running_session() {
        val (s, _) = started()
        val first = s.startFocus(25, deepFocus = false)
        assertEquals(first.id, s.startFocus(50, deepFocus = true).id)
        assertEquals(first, s.data.focus)
    }

    @Test
    fun a_transfer_gives_up_the_pokemons_world_team_seat_and_waits_for_an_expedition() {
        val (s, _) = started()
        val a = s.grantPokemon(16, "Test")
        val b = s.grantPokemon(16, "Test")
        assertTrue(s.worldAssign(1, listOf(a.id, b.id)))
        assertTrue(s.transfer(a.id))
        assertTrue(s.data.world.teams.none { a.id in it.members }, "no team keeps a Pokémon that is gone")
        assertTrue(s.worldStart(1, "tiny-woods"))
        assertFalse(s.transfer(b.id), "a Pokémon out on an expedition stays")
        assertTrue(s.data.player.pokemon.any { it.id == b.id })
    }

    @Test
    fun a_starred_pokemon_cannot_be_transferred_until_unstarred() {
        val (s, _) = started()
        val extra = s.grantPokemon(16, "Test")
        assertNull(s.transferBlock(extra.id))
        s.toggleFavorite(extra.id)
        assertTrue(s.isFavorite(extra.id))
        assertFalse(s.transfer(extra.id))
        s.toggleFavorite(extra.id)
        assertTrue(s.transfer(extra.id))
    }


    @Test
    fun lists_file_tasks_and_deleting_a_list_sends_its_tasks_to_the_inbox() {
        val (s, _) = started()
        assertNull(s.addList("   "))
        val groceries = s.addList("Groceries")!!
        assertEquals(groceries, s.listNamed("groceries"))
        val milk = s.addTask("Milk", listId = groceries.id)
        val stray = s.addTask("Stray", listId = "no-such-list")
        assertEquals(groceries.id, s.data.tasks.first { it.id == milk.id }.listId)
        assertNull(s.data.tasks.first { it.id == stray.id }.listId)
        s.moveToList(stray.id, groceries.id)
        assertEquals(2, app.pokeplan.plan.TaskViews.inList(s.data.tasks, groceries.id).size)
        s.updateList(groceries.copy(archived = true))
        assertTrue(s.activeLists.isEmpty())
        s.deleteList(groceries.id)
        assertTrue(s.data.lists.isEmpty())
        assertTrue(s.data.tasks.none { it.listId != null })
    }

    @Test
    fun an_all_day_event_spans_its_days_and_moves_as_one() {
        val (s, _) = started()
        val first = s.today
        val home = s.addAllDayEvent("Home", first, first.plus(kotlinx.datetime.DatePeriod(days = 2)), app.pokeplan.plan.TaskList.COLORS[0])
        assertTrue(home.allDay)
        assertEquals(first.plus(kotlinx.datetime.DatePeriod(days = 2)), home.endDay)
        s.updateEvent(home.id, day = first.plus(kotlinx.datetime.DatePeriod(days = 1)))
        val moved = s.data.events.single()
        assertEquals(first.plus(kotlinx.datetime.DatePeriod(days = 3)), moved.endDay, "the last day moves with the first")
        s.updateEvent(home.id, allDay = false)
        assertNull(s.data.events.single().lastDay)
        val wfh = s.addAllDayEvent("WFH", first)
        assertNull(wfh.lastDay)
    }

    @Test
    fun a_repeating_event_is_edited_as_a_series_and_moved_or_deleted_one_at_a_time() {
        val storage = MemoryStorage()
        val (s, clock) = store(storage)
        s.start("Sophia", 25)
        fun d(n: Int) = kotlinx.datetime.LocalDate(2026, 10, 19).plus(kotlinx.datetime.DatePeriod(days = n))
        val gym = s.addEvent("Gym", d(0), kotlinx.datetime.LocalTime(18, 0), repeat = app.pokeplan.plan.EventRepeat.WEEKLY)
        val second = app.pokeplan.plan.PlanEvent.occurrenceId(gym.id, d(7))
        assertEquals(listOf(d(0), d(7), d(14)), s.data.events.single().startDates(d(0), d(20)))
        assertEquals(listOf(s.data.events.single()), GameStore(storage, clock, utc).data.events, "saved with the rest")

        // Nudging one changes them all; moving one moves just that one.
        assertEquals(second, s.updateEvent(second, start = kotlinx.datetime.LocalTime(18, 30), minutes = 90))
        assertEquals(kotlinx.datetime.LocalTime(18, 30), s.data.events.single().start)
        val moved = s.moveEvent(second, d(8), kotlinx.datetime.LocalTime(7, 0))
        val series = s.data.events.first { it.id == gym.id }
        assertEquals(listOf(d(0), d(14)), series.startDates(d(0), d(20)), "the moved one is left out of the series")
        val single = s.data.events.first { it.id == moved }
        assertEquals(Triple(d(8), kotlinx.datetime.LocalTime(7, 0), 90), Triple(single.day, single.start, single.minutes))
        assertNull(single.repeat)

        // Moving the series' day moves every one, and the skipped day with it.
        val third = app.pokeplan.plan.PlanEvent.occurrenceId(gym.id, d(14))
        assertEquals(app.pokeplan.plan.PlanEvent.occurrenceId(gym.id, d(15)), s.updateEvent(third, day = d(15)))
        assertEquals(listOf(d(1), d(15)), s.data.events.first { it.id == gym.id }.startDates(d(0), d(20)))

        s.deleteOccurrence(app.pokeplan.plan.PlanEvent.occurrenceId(gym.id, d(15)))
        assertEquals(listOf(d(1), d(22)), s.data.events.first { it.id == gym.id }.startDates(d(0), d(25)))
        s.deleteEvent(app.pokeplan.plan.PlanEvent.occurrenceId(gym.id, d(22)))
        assertEquals(listOf(moved), s.data.events.map { it.id }, "Delete all removes the whole series")
    }

    @Test
    fun changing_how_an_event_repeats_from_a_later_one_keeps_the_ones_before() {
        val (s, _) = started()
        fun d(n: Int) = kotlinx.datetime.LocalDate(2026, 10, 19).plus(kotlinx.datetime.DatePeriod(days = n))
        val home = s.addAllDayEvent("Home", d(5), d(6))
        val first = s.repeatEvent(home.id, app.pokeplan.plan.EventRepeat.WEEKLY)
        assertEquals(app.pokeplan.plan.PlanEvent.occurrenceId(home.id, d(5)), first)
        val later = app.pokeplan.plan.PlanEvent.occurrenceId(home.id, d(19))
        val split = s.repeatEvent(later, app.pokeplan.plan.EventRepeat.FORTNIGHTLY)
        val shown = s.data.events.flatMap { it.occurrences(d(0), d(50)) }
        assertEquals(listOf(d(5), d(12), d(19), d(33), d(47)), shown.map { it.day }.sorted())
        assertTrue(shown.all { it.spanDays == 1 }, "every weekend keeps its two days")
        assertTrue(split in shown.map { it.id })

        s.endEventOn(split, d(33))
        assertEquals(listOf(d(5), d(12), d(19), d(33)), s.data.events.flatMap { it.startDates(d(0), d(60)) }.sorted())
        // Once, from the last one: it stays as a one-off and nothing comes after it.
        val once = s.repeatEvent(app.pokeplan.plan.PlanEvent.occurrenceId(app.pokeplan.plan.PlanEvent.seriesOf(split), d(33)), null)
        assertNull(s.data.events.first { it.id == once }.repeat)
        assertEquals(listOf(d(5), d(12), d(19), d(33)), s.data.events.flatMap { it.startDates(d(0), d(60)) }.sorted())
        // Once on the first one of a series turns it back into a single event with its plain id.
        assertEquals(home.id, s.repeatEvent(first, null))
        assertEquals(listOf(d(5)), s.data.events.first { it.id == home.id }.startDates(d(0), d(60)))
    }

    @Test
    fun a_shiny_stays_in_the_shiny_dex_after_it_is_transferred() {
        val base = started().first
        val shiny = base.data.player.pokemon.first().copy(isShiny = true)
        val data = base.data.copy(player = base.data.player.copy(pokemon = listOf(shiny) + base.data.player.pokemon.drop(1)))
        val storage = MemoryStorage(GameStore.json.encodeToString(AppData.serializer(), data))
        val store = GameStore(storage, FakeClock(start), utc, Random(7))
        store.changeSettings { it.copy(reduceMotion = true) }
        assertTrue(shiny.speciesId in store.data.shinySpecies)
    }

    @Test
    fun the_seen_version_is_remembered() {
        val (s, _) = started()
        assertEquals("0.1.0", s.data.lastSeenVersion)
        s.markVersionSeen("0.2.0")
        assertEquals("0.2.0", s.data.lastSeenVersion)
    }

    @Test
    fun reminders_are_saved_listed_and_switched_off() {
        val storage = MemoryStorage()
        val (s, clock) = store(storage)
        s.start("Sophia", 25)
        val task = s.addTask("Hand in essay", due = LocalDateTime(2026, 10, 19, 17, 0))
        s.setReminders(task.id, listOf(ReminderOffset.Before(60), ReminderOffset.AtTime, ReminderOffset.AtTime, ReminderOffset.Before(10), ReminderOffset.Before(24 * 60)))
        assertEquals(listOf(ReminderOffset.Before(60), ReminderOffset.AtTime, ReminderOffset.Before(10)), s.data.tasks.first { it.id == task.id }.reminders, "no repeats, at most three")
        assertEquals(s.data, GameStore(storage, clock, utc).data, "reminders survive a restart")
        val times = s.upcomingReminders().map { it.atMillis }
        assertEquals(listOf(16, 16, 17).zip(listOf(0, 50, 0)).map { (h, m) -> LocalDateTime(2026, 10, 19, h, m).toInstant(utc).toEpochMilliseconds() }, times)
        s.changeSettings { it.copy(reminders = false) }
        assertTrue(s.upcomingReminders().isEmpty())
        s.changeSettings { it.copy(reminders = true) }
        s.completeTask(task.id)
        assertTrue(s.upcomingReminders().isEmpty(), "a finished task does not remind")
    }

    @Test
    fun a_planned_time_gives_the_task_a_day() {
        val (s, _) = started()
        val loose = s.addTask("Stretch")
        s.setPlannedTime(loose.id, kotlinx.datetime.LocalTime(15, 0))
        val planned = s.data.tasks.first { it.id == loose.id }
        assertEquals(s.today, planned.planned, "no day yet: today")
        assertEquals(kotlinx.datetime.LocalTime(15, 0), planned.plannedTime)
        val due = s.addTask("Essay", due = LocalDateTime(2026, 10, 22, 17, 0))
        s.setPlannedTime(due.id, kotlinx.datetime.LocalTime(9, 0))
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 22), s.data.tasks.first { it.id == due.id }.planned, "the deadline's day")
        s.setPlannedTime(loose.id, null)
        assertNull(s.data.tasks.first { it.id == loose.id }.plannedTime)
    }

    @Test
    fun the_day_timeline_moves_a_task_to_its_day_and_hour() {
        val (s, _) = started()
        val late = s.addTask("Call the bank", planned = kotlinx.datetime.LocalDate(2026, 10, 17))
        val tomorrow = kotlinx.datetime.LocalDate(2026, 10, 20)
        s.scheduleTask(late.id, tomorrow, kotlinx.datetime.LocalTime(10, 0))
        val moved = s.data.tasks.first { it.id == late.id }
        assertEquals(tomorrow, moved.planned)
        assertEquals(kotlinx.datetime.LocalTime(10, 0), moved.plannedTime)
        s.completeTask(late.id)
        s.scheduleTask(late.id, s.today, kotlinx.datetime.LocalTime(8, 0))
        assertEquals(tomorrow, s.data.tasks.first { it.id == late.id }.planned, "a finished task stays where it was")
    }

    @Test
    fun own_events_are_added_moved_resized_and_deleted_and_never_pay() {
        val storage = MemoryStorage()
        val (s, clock) = store(storage)
        s.start("Sophia", 25)
        val coinsBefore = s.data.player.coins
        val day = kotlinx.datetime.LocalDate(2026, 10, 20)
        val e = s.addEvent("  Dentist ", day, kotlinx.datetime.LocalTime(23, 0), 120)
        assertEquals("Dentist", e.title)
        assertEquals(60, e.minutes, "kept inside the day")
        s.updateEvent(e.id, start = kotlinx.datetime.LocalTime(9, 30), minutes = 45, title = " ")
        val moved = s.data.events.single()
        assertEquals(kotlinx.datetime.LocalTime(9, 30), moved.start)
        assertEquals(45, moved.minutes)
        assertEquals("Dentist", moved.title, "a blank title keeps the old one")
        assertEquals(coinsBefore, s.data.player.coins)
        assertEquals(listOf(moved), GameStore(storage, clock, utc).data.events, "saved with the rest")
        s.deleteEvent(e.id)
        assertTrue(s.data.events.isEmpty())
    }

    @Test
    fun resizing_a_block_sets_the_estimate_of_an_open_task_only() {
        val (s, _) = started()
        val task = s.addTask("Write the report", estimateMinutes = 30)
        s.setEstimate(task.id, 75)
        assertEquals(75, s.data.tasks.first { it.id == task.id }.estimateMinutes)
        s.setEstimate(task.id, 0)
        assertNull(s.data.tasks.first { it.id == task.id }.estimateMinutes, "zero clears it")
        s.completeTask(task.id)
        s.setEstimate(task.id, 45)
        assertNull(s.data.tasks.first { it.id == task.id }.estimateMinutes, "a finished task keeps its estimate")
    }


    @Test
    fun the_dex_tells_owned_caught_seen_and_unknown_apart() {
        val (s, _) = started()
        val extra = s.grantPokemon(16, "Test")
        s.markSeen(listOf(150, 25))
        assertEquals(DexState.OWNED, DexState.of(s.data, 25))
        assertEquals(DexState.OWNED, DexState.of(s.data, 16))
        assertEquals(DexState.SEEN, DexState.of(s.data, 150))
        assertEquals(DexState.UNKNOWN, DexState.of(s.data, 151))
        assertTrue(s.transfer(extra.id))
        assertEquals(DexState.CAUGHT, DexState.of(s.data, 16))
        assertTrue(DexState.CAUGHT.caught && !DexState.SEEN.caught)
        // Seen once is seen for good, and marking it again saves nothing new.
        val before = s.data
        s.markSeen(listOf(150))
        assertTrue(before === s.data)
    }

    @Test
    fun the_export_has_the_players_data_and_erasing_starts_over() {
        val (s, _) = started()
        s.addTask("Buy milk")
        val export = s.exportJson()
        assertTrue("Buy milk" in export && "Sophia" in export)
        assertFalse("\"account\"" in export)
        s.eraseDevice()
        assertFalse(s.data.onboarded)
        assertTrue(s.data.tasks.isEmpty())
    }

    @Test
    fun theChosenCalendarsStayOnThePhone() {
        val (s, _) = started()
        s.setShownCalendars(setOf(3L, 7L))
        assertEquals(setOf(3L, 7L), s.data.shownCalendars)
        assertFalse("shownCalendars" in s.exportJson(), "calendar ids differ per phone, so the export leaves them out")
    }

    // --- Pokémon roles (docs/pokelife POKEMON_ECOSYSTEM.md, section 2.2) ---------------------------------------------

    @Test
    fun aSaveFromBeforeTheRolesWereSplitMakesTheOldBuddyPlansLeadOnce() {
        val (s, _) = started()
        // The old shape: the starter was the buddy and in Plan's party, with no lead chosen.
        val old = s.data.copy(
            player = s.data.player.copy(planLeadId = null, rolesVersion = 0, pokemon = s.data.player.pokemon.map { it.copy(inParty = true) }),
            placesVersion = 0,
        )
        val starter = s.data.player.buddyId!!
        val storage = MemoryStorage(GameStore.json.encodeToString(AppData.serializer(), old))
        val reopened = GameStore(storage, FakeClock(start), utc, Random(7))
        val player = reopened.data.player
        assertEquals(GameStore.ROLES_VERSION, player.rolesVersion)
        // ...and then the one-place rule: the starter stays the buddy, and Plan's party, which it led, is empty.
        assertEquals(Place.Buddy, reopened.placeOf(starter))
        assertEquals(starter, player.buddyId)
        assertTrue(player.party.isEmpty())
        assertEquals(Places.VERSION, reopened.data.placesVersion)
    }

    @Test
    fun theBuddyAndPlansLeadAreChosenSeparately() {
        val (s, clock) = started()
        hatchOne(s, clock)
        val starter = s.data.player.buddyId!!
        val hatched = s.data.player.pokemon.last().id
        assertEquals(hatched, s.data.player.planLead?.id)
        assertEquals(starter, s.data.player.buddyId, "choosing Plan's lead leaves the global buddy alone")
        // One place only: making the lead the buddy takes it out of Plan's party, and the old buddy goes to the PC.
        assertTrue(s.setBuddy(hatched))
        assertEquals(hatched, s.data.player.buddyId)
        assertNull(s.data.player.planLead)
        assertEquals(Place.Pc, s.placeOf(starter))
        assertTrue(s.setInParty(starter, true))
        assertEquals(starter, s.data.player.planLead?.id)
        assertEquals(hatched, s.data.player.buddyId)
    }

    @Test
    fun theGlobalBuddyDoesNotChangeWhatPlanPays() {
        val (a, clockA) = started()
        val (b, clockB) = started()
        hatchOne(a, clockA); hatchOne(b, clockB)
        // Only the buddy differs: a Pokémon from the PC becomes the buddy (the starter goes to the PC); Plan's lead is untouched.
        val other = b.grantPokemon(1, "test", Origin.How.QUEST)
        assertTrue(b.setBuddy(other.id))
        assertEquals(a.data.player.planLead?.id, b.data.player.planLead?.id)
        assertEquals(a.perks, b.perks)
        assertEquals(a.perkSources, b.perkSources)
    }

    @Test
    fun aLeadThatLeavesThePartyPassesTheLeadOn() {
        val (s, clock) = started()
        hatchOne(s, clock); hatchOne(s, clock)
        val (first, second) = s.data.player.party.map { it.id }
        assertEquals(first, s.data.player.planLead?.id)
        assertTrue(s.setInParty(first, false), "a lead can leave; it goes to the PC")
        assertEquals(second, s.data.player.planLead?.id)
        assertEquals(Place.Pc, s.placeOf(first))
        assertFalse(s.setInParty(second, false), "Plan's party is never left empty by taking its last member out")
    }

    @Test
    fun aLeadIsNeverSetOutsideItsOwnParty() {
        val (s, clock) = started()
        hatchOne(s, clock); hatchOne(s, clock)
        val other = s.data.player.pokemon.last().id
        assertTrue(s.setInParty(other, true, PartyIds.CARE))
        assertFalse(s.setPlanLead(other), "a Pokémon in Care's party cannot lead Plan's")
        assertFalse(s.setCareLead(s.data.player.planLead!!.id), "a Pokémon in Plan's party cannot lead Care's")
        assertTrue(s.setCareLead(other))
    }

    // --- One currency: Poké (docs/pokelife POKEMON_ECOSYSTEM.md, section 11.2) ------------------------------------------

    @Test
    fun anOldSaveMergesCoinsAndWorldPokeOneToOneOnceAndKeepsBothSources() {
        val (s, _) = started()
        val old = s.data.copy(
            player = s.data.player.copy(coins = 120),
            world = s.data.world.copy(legacyPoke = 35), // an old save stored World's balance as "poke"
            pokeMigration = null,
        )
        val storage = MemoryStorage(GameStore.json.encodeToString(AppData.serializer(), old))
        val reopened = GameStore(storage, FakeClock(start), utc, Random(7))
        assertEquals(155, reopened.data.player.coins)
        assertEquals(0, reopened.data.world.pokeEarned, "World holds no balance of its own")
        assertEquals(0, reopened.data.world.legacyPoke, "the old stored balance is read once and gone")
        assertFalse("\"poke\"" in GameStore.json.encodeToString(app.pokeplan.world.WorldData.serializer(), reopened.data.world), "and never written again")
        val audit = assertNotNull(reopened.data.pokeMigration)
        assertEquals(120, audit.legacyCoins)
        assertEquals(35, audit.legacyWorldPoke)
        assertEquals(app.pokeplan.core.reward.RewardRules.VERSION, audit.rulesVersion)
        // Opening the merged save again adds nothing.
        val again = GameStore(storage, FakeClock(start), utc, Random(7))
        assertEquals(155, again.data.player.coins)
        assertEquals(audit, again.data.pokeMigration)
    }

    @Test
    fun worldSpendsAndEarnsFromTheSameBalanceAsPlan() {
        val storage = MemoryStorage()
        val (s, _) = store(storage)
        s.start("Sophia", 25)
        val base = s.data.player.coins
        // Plan earned 5,000 (written as a widget would leave it, then picked up).
        storage.save(GameStore.json.encodeToString(AppData.serializer(), s.data.copy(player = s.data.player.copy(coins = base + 5_000))))
        s.reload()
        assertEquals(base + 5_000, s.data.player.coins)
        val candyBefore = s.data.player.inventory[app.pokeplan.core.pokemon.Items.RARE_CANDY]
        assertTrue(s.worldBuyCandy(), "World's shop is paid from Plan's earnings")
        assertTrue(s.data.player.coins < base + 5_000)
        assertEquals(0, s.data.world.pokeEarned)
        assertTrue(s.data.player.inventory[app.pokeplan.core.pokemon.Items.RARE_CANDY] > candyBefore)
        val afterShop = s.data.player.coins
        assertEquals(1, s.buyRareCandies(1))
        assertEquals(afterShop - app.pokeplan.core.pokemon.Shop.COINS_PER_RARE_CANDY, s.data.player.coins, "Plan's shop is paid from the same balance")
        assertEquals(0, s.data.world.pokeEarned)
    }
}

class QuickAddTimeTest {
    @kotlin.test.Test
    fun aTimeAndTheEveningAreKeptOnAPlannedDayOnly() {
        val s = GameStore(MemoryStorage(), object : Clock { override fun nowMillis() = 1_760_000_000_000L }).also { it.start("A", 25) }
        val day = s.today
        val timed = s.addTask("Call mum", planned = day, plannedTime = kotlinx.datetime.LocalTime(17, 0))
        kotlin.test.assertEquals(kotlinx.datetime.LocalTime(17, 0), timed.plannedTime)
        val night = s.addTask("Read", planned = day, evening = true)
        kotlin.test.assertTrue(night.evening)
        val loose = s.addTask("Someday", plannedTime = kotlinx.datetime.LocalTime(9, 0), evening = true)
        kotlin.test.assertNull(loose.plannedTime)
        kotlin.test.assertFalse(loose.evening)
    }
}
