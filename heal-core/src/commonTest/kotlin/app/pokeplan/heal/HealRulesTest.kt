package app.pokeplan.heal

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealRulesTest {
    private val day = LocalDate(2026, 11, 7)
    private val now = 1_000_000_000_000L
    private fun activity(minutes: Int = 30, end: Long = now, source: SourceKind = SourceKind.MANUAL, d: LocalDate = day, id: String = "a$minutes$end") =
        ActivityRecord(id, ActivityType.WALK, end - minutes * 60_000L, minutes, d, source = source)

    @Test
    fun missingIsNotZero() {
        val empty = HealData()
        assertNull(HealRules.activeMinutes(empty, day))
        assertNull(HealRules.hydrationOn(empty, day))
        assertNull(HealRules.latestSleep(empty))
        assertNull(HealRules.weekActiveMinutes(empty, day))
        val goal = HealGoal("g", GoalKind.WEEKLY_ACTIVE_MINUTES, 150, day)
        val p = HealRules.progress(goal, empty, day)
        assertNull(p.done)
        assertEquals(0f, p.fraction)
        assertFalse(p.met)
    }

    @Test
    fun totalsSumOnlyTheDayAndAGoalIsMetWithoutAnythingFailing() {
        val data = HealData(activities = listOf(activity(20, id = "x"), activity(25, end = now + 1, id = "y"), activity(60, d = LocalDate(2026, 11, 2), id = "z")))
        assertEquals(45, HealRules.activeMinutes(data, day))
        val weekStart = HealRules.weekStartOf(day)
        assertEquals(LocalDate(2026, 11, 2), weekStart)
        assertEquals(105, HealRules.weekActiveMinutes(data, weekStart))
        assertEquals(2, HealRules.weekActiveDays(data, weekStart))
        val goal = HealGoal("g", GoalKind.WEEKLY_ACTIVE_MINUTES, 150, day)
        assertEquals(HealRules.Progress(105, 150, false), HealRules.progress(goal, data, day))
        assertTrue(HealRules.progress(goal.copy(target = 100), data, day).met)
    }

    @Test
    fun anActivityPaysOnlyWhenLongEnoughFreshAndWithinTheDaysShare() {
        assertTrue(HealRules.activityPays(activity(30), now, 0))
        assertFalse(HealRules.activityPays(activity(HealRules.RESEARCH_MIN_MINUTES - 1), now, 0), "a few minutes is logged but not researched")
        assertFalse(HealRules.activityPays(activity(30, end = now - HealRules.FRESH_MILLIS - 1), now, 0), "backfilled history never pays")
        assertTrue(HealRules.activityPays(activity(30, end = now - HealRules.FRESH_MILLIS), now, 0))
        assertFalse(HealRules.activityPays(activity(30, end = now + HealRules.FUTURE_SLACK_MILLIS + 1), now, 0), "an activity cannot be paid before it has happened")
        assertFalse(HealRules.activityPays(activity(30), now, HealRules.ACTIVITIES_PAID_PER_DAY), "the day's share is used up")
        assertFalse(HealRules.activityPays(activity(30, source = SourceKind.IMPORTED), now, 0), "imported records never pay")
    }

    @Test
    fun hydrationPaysOncePerDayAndNotWhenBackfilled() {
        val e = HydrationEntry("h", now, day, 250)
        assertTrue(HealRules.participationPays(e, now, 0))
        assertFalse(HealRules.participationPays(e, now, 1))
        assertFalse(HealRules.participationPays(e.copy(atMillis = now - HealRules.FRESH_MILLIS - 1), now, 0))
    }

    @Test
    fun validationRefusesNonsenseAndSaysWhy() {
        assertTrue(HealRules.checkActivity(0, null) is Check.Refused)
        assertTrue(HealRules.checkActivity(30, -1) is Check.Refused)
        assertEquals(Check.Ok, HealRules.checkActivity(30, null))
        assertTrue(HealRules.checkHydration(0) is Check.Refused)
        assertTrue(HealRules.checkHydration(9000) is Check.Refused)
        assertTrue(HealRules.checkSleep(10, 5) is Check.Refused)
        assertEquals(Check.Ok, HealRules.checkSleep(0, 8 * 3_600_000L))
        assertTrue(HealRules.checkMeasurement(MeasureType.WEIGHT, 70.0, "cm") is Check.Refused)
        assertTrue(HealRules.checkMeasurement(MeasureType.BODY_FAT, 120.0, "%") is Check.Refused)
        assertEquals(Check.Ok, HealRules.checkMeasurement(MeasureType.OTHER, 12.0, "reps"))
    }

    @Test
    fun trendsAppearOnlyWithEnoughComparableEntriesAndNeverJudge() {
        fun w(i: Int, v: Double, unit: String = "kg") = Measurement("m$i", MeasureType.WEIGHT, v, unit, i * 1000L, day)
        assertNull(HealRules.trendText(HealData(measurements = listOf(w(1, 70.0), w(2, 70.4))), MeasureType.WEIGHT, "kg"))
        val three = HealData(measurements = listOf(w(1, 70.0), w(2, 69.7), w(3, 69.5)))
        val text = HealRules.trendText(three, MeasureType.WEIGHT, "kg")!!
        assertEquals("3 entries; the latest, 69.5 kg, is 0.5 kg lower than the first.", text)
        assertTrue(listOf("better", "worse", "improv", "good", "bad").none { it in text })
        assertNull(HealRules.trendText(three, MeasureType.WEIGHT, "lb"), "kg and lb entries are not comparable")
    }

    @Test
    fun unitsConvertBothWaysAndKeepTheOriginal() {
        assertEquals(154.3, HealUnits.convert(70.0, "kg", "lb")!!.let { (it * 10).toInt() / 10.0 })
        assertEquals(70.0, HealUnits.convert(HealUnits.convert(70.0, "kg", "lb")!!, "lb", "kg")!!.let { kotlin.math.round(it * 1000) / 1000 })
        assertNull(HealUnits.convert(1.0, "kg", "cm"))
        val m = Measurement("m", MeasureType.WEIGHT, 70.0, "kg", 0, day)
        assertEquals("70 kg", HealUnits.show(m, "kg"))
        assertEquals("154.3 lb", HealUnits.show(m, "lb"))
    }

    @Test
    fun weeksStartWhereAsked() {
        assertEquals(LocalDate(2026, 11, 1), HealRules.weekStartOf(day, DayOfWeek.SUNDAY))
        assertEquals("1 h 05 min", HealRules.duration(65))
        assertEquals("45 min", HealRules.duration(45))
    }

    private fun medication(
        id: String = "m",
        times: List<LocalTime> = listOf(LocalTime(8, 0), LocalTime(20, 0)),
        status: MedicationStatus = MedicationStatus.ACTIVE,
        start: LocalDate = LocalDate(2026, 11, 1),
        end: LocalDate? = null,
        supply: Int? = null,
        refill: Int? = null,
    ) = Medication(id, "Metformin", "500 mg", "with food", times, start, end, status, supplyLeft = supply, refillAt = refill)

    @Test
    fun aMedicationIsJustWhatThePlayerTypedAndIsCheckedForShapeOnly() {
        assertTrue(HealRules.checkMedication(medication()) is Check.Ok)
        assertTrue(HealRules.checkMedication(medication().copy(name = " ")) is Check.Refused)
        assertTrue(HealRules.checkMedication(medication(times = List(HealRules.MEDICATION_TIMES_MAX + 1) { LocalTime(it % 24, 0) })) is Check.Refused)
        assertTrue(HealRules.checkMedication(medication().copy(startDay = day, endDay = LocalDate(2026, 11, 6))) is Check.Refused, "an end date before the start")
        assertTrue(HealRules.checkMedication(medication(supply = -1)) is Check.Refused)
        assertTrue(HealRules.checkMedication(medication(supply = HealRules.MEDICATION_SUPPLY_MAX + 1)) is Check.Refused)
        assertTrue(HealRules.checkMedication(medication().copy(instructions = "x".repeat(HealRules.NOTE_MAX + 1))) is Check.Refused)
    }

    @Test
    fun theScheduleOnlyRunsInsideTheEnteredWindowAndDropsPausedFinishedOrStoppedOnes() {
        val m = medication()
        assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), HealRules.medicationTimesOn(m, day))
        assertEquals(emptyList(), HealRules.medicationTimesOn(m.copy(status = MedicationStatus.PAUSED), day))
        assertEquals(emptyList(), HealRules.medicationTimesOn(m.copy(status = MedicationStatus.STOPPED), day))
        assertEquals(emptyList(), HealRules.medicationTimesOn(m, LocalDate(2026, 10, 31)), "before the start date")
        assertEquals(emptyList(), HealRules.medicationTimesOn(m.copy(endDay = LocalDate(2026, 11, 6)), day), "after the end date")
        assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), HealRules.medicationTimesOn(m.copy(times = listOf(LocalTime(20, 0), LocalTime(8, 0), LocalTime(8, 0))), day), "sorted and distinct")
    }

    @Test
    fun aLoggedDoseDropsOutOfTheDueTimesAndIsCountedAsTaken() {
        val m = medication(supply = 3)
        val data = HealData(medications = listOf(m), doses = listOf(MedicationDose("d", "m", day, LocalTime(8, 0), DoseStatus.TAKEN, now)))
        assertEquals(listOf(LocalTime(20, 0)), HealRules.medicationDueTimes(data, m, day))
        assertEquals(1, HealRules.medicationTakenOn(data, day))
        assertEquals(2, HealRules.medicationScheduledOn(data, day))
        assertEquals(emptyList(), HealRules.medicationDueTimes(data.copy(doses = data.doses + MedicationDose("d2", "m", day, LocalTime(20, 0), DoseStatus.SKIPPED, now)), m, day))
    }

    @Test
    fun lowSupplyOnlyWhenThePlayerSetBothCountsAndItIsStillBeingTaken() {
        assertFalse(HealRules.medicationLowSupply(medication()), "no count, no alert")
        assertTrue(HealRules.medicationLowSupply(medication(supply = 2, refill = 3)))
        assertFalse(HealRules.medicationLowSupply(medication(supply = 4, refill = 3)))
        assertFalse(HealRules.medicationLowSupply(medication(supply = 1, refill = 3, status = MedicationStatus.STOPPED)))
    }
}
