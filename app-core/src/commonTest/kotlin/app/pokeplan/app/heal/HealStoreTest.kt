package app.pokeplan.app.heal

import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.DoseStatus
import app.pokeplan.heal.GoalKind
import app.pokeplan.heal.GoalStatus
import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealKinds
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.MedicationStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.Place
import app.pokeplan.app.Tutorials
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class HealStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<HealEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun heal(event: HealEvent): List<Reward>? { events += event; return inner.heal(event) }
    }

    private class Setup(val game: GameStore, val heal: HealStore, val spy: Spy, val clock: FakeClock, val storage: MemoryStorage)

    private fun setup(started: Boolean = true, research: Boolean = true): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val spy = Spy(game)
        val storage = MemoryStorage()
        val heal = HealStore(storage, clock, spy, utc, rng = Random(3))
        if (!research) heal.setFieldResearch(false)
        return Setup(game, heal, spy, clock, storage)
    }

    private val Setup.candies get() = game.data.player.inventory[app.pokeplan.core.pokemon.Items.RARE_CANDY]

    @Test
    fun aLoggedActivityKeepsOnlyWhatWasEnteredAndPaysOnceWhenFieldResearchIsOn() {
        val s = setup()
        val out = s.heal.logActivity(ActivityType.WALK, 30, note = "  park  ")
        val a = assertNotNull(out.record)
        assertEquals("park", a.note)
        assertEquals(30, a.minutes)
        assertEquals(saturday - 30 * 60_000L, a.startMillis)
        assertTrue(a.paid)
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(s.candies > 0)
    }

    @Test
    fun theEventLeavingHealCarriesNothingButTheContract() {
        val s = setup()
        s.heal.logActivity(ActivityType.RUN, 45, distanceMeters = 7000, note = "secret")
        s.heal.logDrink(500, "water")
        assertEquals(2, s.spy.events.size)
        for (e in s.spy.events) {
            val text = GameStore.json.encodeToString(HealEvent.serializer(), e)
            assertTrue("secret" !in text && "7000" !in text && "45" !in text && "500" !in text && "water" !in text, text)
            assertEquals("heal", e.module)
            assertEquals(e.ref, e.id.substringAfterLast(':'))
        }
        assertEquals(listOf(HealKind.ACTIVITY, HealKind.PARTICIPATION), s.spy.events.map { it.kind })
        val saved = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        assertTrue("secret" !in saved && "distanceMeters" !in saved && "RUN" !in saved)
    }

    @Test
    fun fieldResearchIsOnByDefaultAndCanBeTurnedOffWithoutLosingTracking() {
        assertTrue(setup().heal.data.settings.fieldResearch)
        val s = setup(research = false)
        val before = s.candies
        val a = assertNotNull(s.heal.logActivity(ActivityType.WALK, 30).record)
        assertFalse(a.paid)
        assertNotNull(s.heal.logDrink(250).record)
        assertEquals(before, s.candies)
        assertTrue(s.spy.events.isEmpty(), "nothing is sent when Field Research is off")
        assertEquals(30, HealRules.activeMinutes(s.heal.data, s.heal.today))
    }

    @Test
    fun tooShortOrOldOrFutureSessionsAreLoggedButNeverPaid() {
        val s = setup()
        assertFalse(assertNotNull(s.heal.logActivity(ActivityType.WALK, 5).record).paid, "too short")
        assertFalse(assertNotNull(s.heal.logActivity(ActivityType.WALK, 30, endedAtMillis = saturday - 13 * 3_600_000L).record).paid, "backfilled")
        assertFalse(assertNotNull(s.heal.logActivity(ActivityType.WALK, 30, endedAtMillis = saturday + 3_600_000L).record).paid, "not happened yet")
        assertTrue(s.spy.events.isEmpty())
        assertEquals(3, s.heal.data.activities.size)
    }

    @Test
    fun theDayHasACapOnPaidSessionsAndANewDayStartsFresh() {
        val s = setup()
        val paid = (1..5).count { assertNotNull(s.heal.logActivity(ActivityType.WALK, 20).record).paid }
        assertEquals(HealRules.ACTIVITIES_PAID_PER_DAY, paid)
        assertEquals(5, s.heal.data.activities.size, "the rest are still logged")
        s.clock.advanceDays(1)
        assertTrue(assertNotNull(s.heal.logActivity(ActivityType.WALK, 20).record).paid)
    }

    @Test
    fun onlyTheDaysFirstDrinkPaysAndNothingPaysForEveryEntry() {
        val s = setup()
        val first = s.heal.logDrink(250)
        val second = s.heal.logDrink(250)
        assertTrue(first.record!!.paid && first.rewards.isNotEmpty())
        assertFalse(second.record!!.paid)
        assertTrue(second.rewards.isEmpty())
        assertEquals(500, HealRules.hydrationOn(s.heal.data, s.heal.today))
    }

    @Test
    fun editingDeletingOrReplayingNeverPaysAgain() {
        val s = setup()
        val a = assertNotNull(s.heal.logActivity(ActivityType.WALK, 30).record)
        val candies = s.candies
        s.heal.updateActivity(a.copy(minutes = 90, type = ActivityType.RUN))
        assertTrue(s.heal.data.activities.single().paid)
        assertEquals(candies, s.candies)
        s.heal.deleteActivity(a.id)
        assertNull(s.game.heal(HealEvent("heal:activity:${a.id}", HealKind.ACTIVITY, saturday, a.id)), "the same event id never pays twice")
        assertEquals(candies, s.candies)
        val restored = HealStore(MemoryStorage(s.storage.text), s.clock, s.spy, utc)
        assertEquals(0, restored.data.activities.size)
        assertEquals(candies, s.candies)
    }

    @Test
    fun theGamePaysTheSameEventIdOnlyOnceEvenWithRoomInTheDay() {
        val s = setup()
        val e = HealEvent("heal:activity:x", HealKind.ACTIVITY, saturday, "x")
        assertNotNull(s.game.heal(e))
        assertNull(s.game.heal(e))
        assertEquals(1, s.game.data.healPaid.size)
    }

    @Test
    fun sleepMeasurementsAndGoalsNeverSendAnything() {
        val s = setup()
        val night = s.heal.logSleep(saturday - 8 * 3_600_000L, saturday).record
        assertNotNull(night)
        assertEquals(480, night.minutes)
        assertNotNull(s.heal.logMeasurement(MeasureType.WEIGHT, 70.0, "kg").record)
        val goal = assertNotNull(s.heal.addGoal(GoalKind.WEEKLY_ACTIVE_MINUTES, 150))
        s.heal.setGoalStatus(goal.id, GoalStatus.PAUSED)
        assertEquals(GoalStatus.PAUSED, s.heal.data.goals.single().status)
        assertTrue(s.spy.events.isEmpty())
        assertNull(s.heal.logSleep(10, 5).record)
        assertEquals("Waking up has to be after falling asleep.", s.heal.logSleep(10, 5).refused)
        assertNull(s.heal.addGoal(GoalKind.WEEKLY_ACTIVE_MINUTES, 0))
    }

    @Test
    fun aNightThatHasNotEndedAndAWeekOfEightDaysAreRefused() {
        val s = setup()
        assertEquals("That night is not over yet.", s.heal.logSleep(saturday - 3_600_000L, saturday + 3_600_000L).refused)
        assertTrue(s.heal.data.sleep.isEmpty())
        assertNull(s.heal.addGoal(GoalKind.WEEKLY_ACTIVE_DAYS, 8))
        assertNotNull(s.heal.addGoal(GoalKind.WEEKLY_ACTIVE_DAYS, 7))
    }

    @Test
    fun aPaidRecordIsSavedAlreadyMarkedInOneWrite() {
        val s = setup()
        var writes = 0
        val stop = s.heal.observe { writes++ }
        val before = writes
        assertTrue(assertNotNull(s.heal.logActivity(ActivityType.RUN, 20).record).paid)
        assertTrue(assertNotNull(s.heal.logDrink(250).record).paid)
        assertEquals(2, writes - before)
        assertTrue(s.heal.data.activities.single().paid && s.heal.data.hydration.single().paid)
        stop()
    }

    @Test
    fun aGoalPausedOrMissedLeavesItsRecordsAndPaysNothingLess() {
        val s = setup()
        s.heal.logActivity(ActivityType.WALK, 20)
        val goal = assertNotNull(s.heal.addGoal(GoalKind.WEEKLY_ACTIVE_MINUTES, 150))
        s.heal.setGoalStatus(goal.id, GoalStatus.ARCHIVED)
        s.heal.deleteGoal(goal.id)
        assertEquals(1, s.heal.data.activities.size)
    }

    @Test
    fun eraseRemovesOneKindAndLeavesEverythingElse() {
        val s = setup()
        s.heal.logActivity(ActivityType.WALK, 30)
        s.heal.logDrink(250)
        s.heal.logSleep(saturday - 7 * 3_600_000L, saturday)
        val candies = s.candies
        val pokemon = s.game.data.player.pokemon.size
        s.heal.erase(HealKinds.SLEEP)
        assertTrue(s.heal.data.sleep.isEmpty())
        assertEquals(1, s.heal.data.activities.size)
        assertEquals(1, s.heal.data.hydration.size)
        assertTrue(s.heal.data.settings.fieldResearch, "settings stay")
        s.heal.eraseAll()
        assertTrue(s.heal.data.activities.isEmpty() && s.heal.data.hydration.isEmpty())
        assertEquals(candies, s.candies)
        assertEquals(pokemon, s.game.data.player.pokemon.size)
    }

    @Test
    fun exportHoldsOnlyTheChosenKindsAndNothingOfTheGame() {
        val s = setup()
        s.heal.logActivity(ActivityType.WALK, 30, note = "park")
        s.heal.logMeasurement(MeasureType.WEIGHT, 70.0, "kg")
        val onlyActivity = s.heal.export(setOf(HealKinds.ACTIVITY))
        assertTrue("park" in onlyActivity && "weight" !in onlyActivity.lowercase())
        assertTrue("pokemon" !in s.heal.export().lowercase() && "pokelife-heal-export" in s.heal.export())
    }

    @Test
    fun hydrationRemindersAreOffByDefaultAndAtMostThree() {
        val s = setup()
        assertTrue(s.heal.data.settings.hydrationReminders.isEmpty())
        s.heal.setHydrationReminders(listOf(10, 12, 14, 16, 12).map { LocalTime(it, 0) })
        assertEquals(listOf(10, 12, 14).map { LocalTime(it, 0) }, s.heal.data.settings.hydrationReminders)
    }

    @Test
    fun nothingPaysBeforeAGameHasStarted() {
        val s = setup(started = false)
        val a = assertNotNull(s.heal.logActivity(ActivityType.WALK, 30).record)
        assertFalse(a.paid, "no trainer yet: tracking still works, the world pays nothing")
    }

    @Test
    fun theHealWalkGivesOneFieldPokemonIntoHealsPartyOnceWithNoRecordStreakOrCap() {
        val s = setup()
        assertTrue(s.game.tutorialOffered(Tutorials.HEAL_WALK))
        val rewards = assertNotNull(s.game.completeTutorial(Tutorials.HEAL_WALK))
        val egg = rewards.filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(Place.Party(PartyIds.HEAL), s.game.placeOf(egg.id))
        assertEquals(egg.id, s.game.data.player.healLead?.id)
        assertEquals(app.pokeplan.core.dex.Region.HEAL, s.game.data.origins[egg.id]?.region)
        assertEquals(0, s.game.healEggMeter(), "no meter")
        assertTrue(s.game.data.healPaid.isEmpty() && s.game.data.activeDays.isEmpty(), "no streak, no event, no cap used")
        assertTrue(s.heal.data.activities.isEmpty() && s.heal.data.hydration.isEmpty(), "no HEAL record")
        assertFalse(s.game.tutorialOffered(Tutorials.HEAL_WALK))
        assertNull(s.game.completeTutorial(Tutorials.HEAL_WALK))
        assertEquals(2, s.game.data.player.pokemon.size)
        assertTrue(assertNotNull(s.heal.logActivity(ActivityType.WALK, 30).record).paid)
    }

    @Test
    fun aPlayerWhoAlreadyLoggedAnActivityIsNotOfferedTheWalk() {
        val s = setup()
        assertFalse(s.game.tutorialOffered(Tutorials.HEAL_WALK, healActivities = 1))
    }

    @Test
    fun theWalkDoesNotMoveTheBuddyOrOtherPartiesAndHealPerksStayInHeal() {
        val s = setup()
        val buddy = s.game.data.player.buddyId
        s.game.completeTutorial(Tutorials.HEAL_WALK)
        assertEquals(buddy, s.game.data.player.buddyId)
        assertTrue(s.game.data.player.party.isEmpty() && s.game.data.player.partyOf(PartyIds.CARE).isEmpty())
        assertTrue(s.game.setInParty(buddy!!, true, PartyIds.HEAL))
        assertEquals(Place.Party(PartyIds.HEAL), s.game.placeOf(buddy))
        assertNull(s.game.data.player.buddyId)
        assertTrue(s.game.setHealLead(buddy))
        assertEquals(buddy, s.game.data.player.healLead?.id)
    }

    @Test
    fun aDrinkCanBeMovedToAnotherMomentAndKeepsItsPayment() {
        val s = setup()
        val d = assertNotNull(s.heal.logDrink(250).record)
        val moved = LocalDateTime(2026, 11, 3, 8, 15).toInstant(utc).toEpochMilliseconds()
        val out = s.heal.updateDrink(d.copy(ml = 300, atMillis = moved, drink = " tea "))
        val e = assertNotNull(out.record)
        assertEquals(300, e.ml)
        assertEquals("tea", e.drink)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 3), e.day)
        assertEquals(d.paid, e.paid)
        assertEquals(1, s.heal.data.hydration.size)
        assertNull(s.heal.updateDrink(d.copy(ml = 0)).record)
        assertNull(s.heal.updateDrink(d.copy(id = "gone")).record)
    }

    @Test
    fun aNightAndAMeasurementCanBeCorrectedUnderTheSameRulesAsNewOnes() {
        val s = setup()
        val wake = LocalDateTime(2026, 11, 7, 7, 0).toInstant(utc).toEpochMilliseconds()
        val night = assertNotNull(s.heal.logSleep(wake - 7 * 3_600_000L, wake).record)
        val fixed = assertNotNull(s.heal.updateSleep(night.copy(startMillis = wake - 8 * 3_600_000L + 30 * 60_000L)).record)
        assertEquals(450, fixed.minutes)
        assertNull(s.heal.updateSleep(night.copy(endMillis = night.startMillis)).record)
        assertNull(s.heal.updateSleep(night.copy(endMillis = s.clock.millis + 3 * 3_600_000L)).record)

        val m = assertNotNull(s.heal.logMeasurement(MeasureType.WEIGHT, 60.0, "kg").record)
        val at = LocalDateTime(2026, 11, 1, 6, 30).toInstant(utc).toEpochMilliseconds()
        val edited = assertNotNull(s.heal.updateMeasurement(m.copy(value = 59.4, atMillis = at)).record)
        assertEquals(59.4, edited.value)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 1), edited.day)
        assertNull(s.heal.updateMeasurement(m.copy(value = -1.0)).record)
    }

    @Test
    fun anActivityCanBeMovedToAnotherDay() {
        val s = setup()
        val a = assertNotNull(s.heal.logActivity(ActivityType.WALK, 30).record)
        val at = LocalDateTime(2026, 11, 2, 17, 0).toInstant(utc).toEpochMilliseconds()
        val moved = assertNotNull(s.heal.updateActivity(a.copy(startMillis = at, minutes = 45)).record)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 2), moved.day)
        assertEquals(a.paid, moved.paid)
    }

    @Test
    fun aMedicationIsKeptAsEnteredAndTakingADoseNeverPays() {
        val s = setup()
        val out = s.heal.addMedication(
            "  Metformin ", " 500 mg ", " with food ", listOf(LocalTime(20, 0), LocalTime(8, 0), LocalTime(8, 0)),
            startDay = LocalDate(2026, 11, 1), supplyLeft = 10, refillAt = 3,
        )
        val m = assertNotNull(out.record)
        assertEquals("Metformin", m.name)
        assertEquals("500 mg", m.strength)
        assertEquals("with food", m.instructions)
        assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), m.times, "sorted and distinct")
        assertEquals(10, m.supplyLeft)
        assertEquals(1, s.heal.data.medications.size)
        assertTrue(s.spy.events.isEmpty() && s.candies == 0, "a medication record never pays")
        assertNull(s.heal.addMedication(" ").record)
        assertNull(s.heal.addMedication("X", times = List(HealRules.MEDICATION_TIMES_MAX + 1) { LocalTime(it, 0) }).record)
    }

    @Test
    fun aTakenDoseUsesOneOfTheSupplyItCountedAndUndoPutsItBack() {
        val s = setup()
        val m = assertNotNull(s.heal.addMedication("Metformin", times = listOf(LocalTime(8, 0), LocalTime(20, 0)), startDay = LocalDate(2026, 11, 1), supplyLeft = 10).record)
        val dose = assertNotNull(s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(8, 0), DoseStatus.TAKEN).record)
        assertEquals(9, s.heal.data.medications.single().supplyLeft)
        assertNull(s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(8, 0), DoseStatus.TAKEN).record, "a scheduled dose is logged once")
        s.heal.deleteDose(dose.id)
        assertEquals(10, s.heal.data.medications.single().supplyLeft)
        assertEquals(0, s.heal.data.doses.size)
        assertTrue(s.spy.events.isEmpty() && s.candies == 0, "logging a dose never pays")
    }

    @Test
    fun aSkippedDoseIsHistoryButChangesNoSupplyAndAnAsNeededDoseCanBeLogged() {
        val s = setup()
        val m = assertNotNull(s.heal.addMedication("Vitamin D", startDay = LocalDate(2026, 11, 1), supplyLeft = 5).record)
        assertTrue(s.heal.data.medications.single().times.isEmpty(), "no times means as needed")
        val dose = assertNotNull(s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(13, 5), DoseStatus.SKIPPED).record)
        assertEquals(5, s.heal.data.medications.single().supplyLeft)
        assertEquals(DoseStatus.SKIPPED, dose.status)
        assertNull(s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(13, 5), DoseStatus.TAKEN).record, "the same entry is not rewritten")
    }

    @Test
    fun pausingStopsTheScheduleAndDeletingTakesItsHistory() {
        val s = setup()
        val m = assertNotNull(s.heal.addMedication("Metformin", times = listOf(LocalTime(8, 0)), startDay = LocalDate(2026, 11, 1)).record)
        s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(8, 0), DoseStatus.TAKEN)
        s.heal.setMedicationStatus(m.id, MedicationStatus.PAUSED)
        assertEquals(emptyList(), HealRules.medicationTimesOn(s.heal.data.medications.single(), LocalDate(2026, 11, 7)))
        s.heal.setMedicationStatus(m.id, MedicationStatus.ACTIVE)
        assertEquals(listOf(LocalTime(8, 0)), HealRules.medicationTimesOn(s.heal.data.medications.single(), LocalDate(2026, 11, 7)))
        s.heal.deleteMedication(m.id)
        assertTrue(s.heal.data.medications.isEmpty() && s.heal.data.doses.isEmpty(), "the dose log goes with its medication")
    }

    @Test
    fun aRefillIsOnlyTheCountThePlayerTypesAndTheKindEraseCoversBothFamilies() {
        val s = setup()
        val m = assertNotNull(s.heal.addMedication("Metformin", times = listOf(LocalTime(8, 0)), startDay = LocalDate(2026, 11, 1), supplyLeft = 2, refillAt = 3).record)
        assertTrue(HealRules.medicationLowSupply(s.heal.data.medications.single()))
        s.heal.refillMedication(m.id, 30)
        assertEquals(30, s.heal.data.medications.single().supplyLeft)
        assertFalse(HealRules.medicationLowSupply(s.heal.data.medications.single()))
        assertNull(s.heal.refillMedication(m.id, -1).record)
        s.heal.setMedicationRemind(m.id, true)
        assertTrue(s.heal.data.medications.single().remind)
        s.heal.logDose(m.id, LocalDate(2026, 11, 7), LocalTime(8, 0), DoseStatus.TAKEN)
        s.heal.erase(HealKinds.MEDICATIONS)
        assertTrue(s.heal.data.medications.isEmpty() && s.heal.data.doses.isEmpty())
        val export = HealExport.json(HealData(medications = listOf(m), doses = listOf()), setOf(HealKinds.MEDICATIONS))
        assertTrue(export.contains("medications") && export.contains("Metformin") && export.contains("doses"))
        assertTrue(!export.contains("activities"))
    }
}
