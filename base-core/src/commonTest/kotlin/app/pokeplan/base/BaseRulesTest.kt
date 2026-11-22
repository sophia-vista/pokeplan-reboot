package app.pokeplan.base

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseRulesTest {
    private val today = d("2026-11-07")

    private fun d(s: String): LocalDate = LocalDate.parse(s)

    private fun rec(kind: RepeatKind, every: Int = 1, vararg weekdays: Int) = Recurrence(kind, every, weekdays.toList())

    private fun chore(id: String, due: String? = null, r: Recurrence = Recurrence(), status: RecordStatus = RecordStatus.ACTIVE, title: String = id) =
        Chore(id, title, recurrence = r, firstDue = due?.let(::d), status = status)

    private fun entry(
        id: String, chore: String, due: String, status: EntryStatus = EntryStatus.DONE, day: String = due,
        defer: String? = null, paid: Boolean = false, at: Long = 0,
    ) = ChoreEntry(id, chore, d(due), status, d(day), at, deferTo = defer?.let(::d), paid = paid)

    private fun supply(id: String, qty: Long? = null, low: Long? = null, quieted: Boolean = false, archived: Boolean = false, name: String = id) =
        Supply(id, name, quantityMilli = qty, lowMilli = low, lowDismissed = quieted, archived = archived)

    private fun schedule(id: String, next: String, r: Recurrence = rec(RepeatKind.MONTHLY), first: String = next, policy: SchedulePolicy = SchedulePolicy.FIXED, status: RecordStatus = RecordStatus.ACTIVE) =
        MaintenanceSchedule(id, id, recurrence = r, policy = policy, firstDue = d(first), nextDue = d(next), status = status)

    private fun view(c: Chore, vararg entries: ChoreEntry, on: LocalDate = today) = BaseRules.view(BaseData(chores = listOf(c), entries = entries.toList()), c, on)

    @Test
    fun dailyEveryDayOccursFromTheAnchorOn() {
        val r = rec(RepeatKind.DAILY)
        val a = d("2026-11-01")
        assertFalse(Schedules.occursOn(r, a, d("2026-10-31")))
        assertTrue(Schedules.occursOn(r, a, a))
        assertTrue(Schedules.occursOn(r, a, d("2027-03-09")))
        assertEquals(d("2026-11-07"), Schedules.lastOnOrBefore(r, a, d("2026-11-07")))
        assertEquals(d("2026-11-07"), Schedules.firstOnOrAfter(r, a, d("2026-11-07")))
        assertEquals(d("2026-11-08"), Schedules.nextAfter(r, a, d("2026-11-07")))
    }

    @Test
    fun dailyEveryThreeDaysCountsFromTheAnchor() {
        val r = rec(RepeatKind.DAILY, 3)
        val a = d("2026-11-01")
        assertTrue(Schedules.occursOn(r, a, d("2026-11-04")))
        assertFalse(Schedules.occursOn(r, a, d("2026-11-05")))
        assertTrue(Schedules.occursOn(r, a, d("2026-11-07")))
        assertEquals(d("2026-11-07"), Schedules.lastOnOrBefore(r, a, d("2026-11-09")))
        assertEquals(d("2026-11-10"), Schedules.firstOnOrAfter(r, a, d("2026-11-08")))
        assertEquals(d("2026-11-10"), Schedules.nextAfter(r, a, d("2026-11-07")))
        assertEquals(d("2026-11-10"), Schedules.nextAfter(r, a, d("2026-11-08")))
    }

    @Test
    fun beforeTheAnchorNothingOccursAndTheFirstOccurrenceIsTheAnchor() {
        for (r in listOf(rec(RepeatKind.DAILY, 3), rec(RepeatKind.MONTHLY), rec(RepeatKind.YEARLY), rec(RepeatKind.ONCE), rec(RepeatKind.WEEKLY, 2))) {
            val a = d("2026-11-05")
            val before = d("2026-10-20")
            assertFalse(Schedules.occursOn(r, a, before), "$r")
            assertNull(Schedules.lastOnOrBefore(r, a, before), "$r")
            assertEquals(a, Schedules.firstOnOrAfter(r, a, before), "$r")
            assertEquals(a, Schedules.nextAfter(r, a, d("2026-11-04")), "$r")
        }
    }

    @Test
    fun weeklyWithoutWeekdaysUsesTheAnchorsWeekday() {
        val r = rec(RepeatKind.WEEKLY)
        val saturday = d("2026-11-07")
        assertTrue(Schedules.occursOn(r, saturday, d("2026-11-14")))
        assertFalse(Schedules.occursOn(r, saturday, d("2026-11-13")))
        assertFalse(Schedules.occursOn(r, saturday, d("2026-11-08")))
        assertEquals(d("2026-11-14"), Schedules.nextAfter(r, saturday, saturday))
        assertEquals(d("2026-11-21"), Schedules.lastOnOrBefore(r, saturday, d("2026-11-27")))
        assertEquals(d("2026-11-28"), Schedules.firstOnOrAfter(r, saturday, d("2026-11-22")))
    }

    @Test
    fun weeklyOnMondayAndThursday() {
        val r = rec(RepeatKind.WEEKLY, 1, 1, 4)
        val monday = d("2026-11-02")
        assertTrue(Schedules.occursOn(r, monday, d("2026-11-02")))
        assertTrue(Schedules.occursOn(r, monday, d("2026-11-05")))
        assertTrue(Schedules.occursOn(r, monday, d("2026-11-09")))
        assertFalse(Schedules.occursOn(r, monday, d("2026-11-03")))
        assertFalse(Schedules.occursOn(r, monday, d("2026-11-07")))
        assertEquals(d("2026-11-05"), Schedules.nextAfter(r, monday, monday))
        assertEquals(d("2026-11-09"), Schedules.nextAfter(r, monday, d("2026-11-05")))
        assertEquals(d("2026-11-05"), Schedules.lastOnOrBefore(r, monday, d("2026-11-08")))
        assertEquals(d("2026-11-09"), Schedules.firstOnOrAfter(r, monday, d("2026-11-06")))
    }

    @Test
    fun weeklyEveryTwoWeeksSkipsTheOddWeeks() {
        val r = rec(RepeatKind.WEEKLY, 2)
        val monday = d("2026-11-02")
        assertFalse(Schedules.occursOn(r, monday, d("2026-11-09")))
        assertTrue(Schedules.occursOn(r, monday, d("2026-11-16")))
        assertEquals(d("2026-11-16"), Schedules.nextAfter(r, monday, monday))
        assertEquals(d("2026-11-16"), Schedules.lastOnOrBefore(r, monday, d("2026-11-29")))
        assertEquals(d("2026-11-30"), Schedules.firstOnOrAfter(r, monday, d("2026-11-17")))

        val both = rec(RepeatKind.WEEKLY, 2, 1, 4)
        assertTrue(Schedules.occursOn(both, monday, d("2026-11-05")), "Thursday of the first week")
        assertFalse(Schedules.occursOn(both, monday, d("2026-11-12")), "Thursday of the skipped week")
        assertTrue(Schedules.occursOn(both, monday, d("2026-11-19")))
        assertEquals(d("2026-11-05"), Schedules.lastOnOrBefore(both, monday, d("2026-11-15")))
        assertEquals(d("2026-11-16"), Schedules.firstOnOrAfter(both, monday, d("2026-11-06")))
        assertEquals(d("2026-11-19"), Schedules.nextAfter(both, monday, d("2026-11-16")))
    }

    @Test
    fun weeklyOnWeekdaysCountsFromAnAnchorInTheMiddleOfTheWeek() {
        val r = rec(RepeatKind.WEEKLY, 1, 1, 4)
        val thursday = d("2026-11-05")
        assertFalse(Schedules.occursOn(r, thursday, d("2026-11-02")))
        assertEquals(d("2026-11-09"), Schedules.nextAfter(r, thursday, thursday))
    }

    @Test
    fun monthlyOnThe31stClampsToTheLastDayAndNeverDrifts() {
        val r = rec(RepeatKind.MONTHLY)
        val a = d("2026-01-31")
        assertTrue(Schedules.occursOn(r, a, d("2026-02-28")))
        assertFalse(Schedules.occursOn(r, a, d("2026-02-27")))
        assertTrue(Schedules.occursOn(r, a, d("2026-04-30")))
        assertFalse(Schedules.occursOn(r, a, d("2026-04-29")))
        val seq = generateSequence(a) { Schedules.nextAfter(r, a, it) }.take(7).toList()
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31").map(::d), seq)
        val leap = d("2028-01-31")
        assertEquals(d("2028-02-29"), Schedules.nextAfter(r, leap, leap))
        assertEquals(d("2028-03-31"), Schedules.nextAfter(r, leap, d("2028-02-29")))
        assertEquals(d("2026-02-28"), Schedules.lastOnOrBefore(r, a, d("2026-03-30")))
        assertEquals(d("2026-01-31"), Schedules.lastOnOrBefore(r, a, d("2026-02-27")))
        assertEquals(d("2026-04-30"), Schedules.firstOnOrAfter(r, a, d("2026-04-01")))
        assertEquals(d("2026-05-31"), Schedules.firstOnOrAfter(r, a, d("2026-05-01")))
    }

    @Test
    fun monthlyEveryTwoMonthsOnThe31st() {
        val r = rec(RepeatKind.MONTHLY, 2)
        val a = d("2026-01-31")
        assertFalse(Schedules.occursOn(r, a, d("2026-02-28")))
        val seq = generateSequence(a) { Schedules.nextAfter(r, a, it) }.take(6).toList()
        assertEquals(listOf("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31", "2026-09-30", "2026-11-30").map(::d), seq)
        assertEquals(d("2026-03-31"), Schedules.lastOnOrBefore(r, a, d("2026-04-15")))
        assertEquals(d("2026-05-31"), Schedules.firstOnOrAfter(r, a, d("2026-04-01")))
        assertEquals(d("2027-01-31"), Schedules.nextAfter(r, a, d("2026-11-30")))
    }

    @Test
    fun monthlyAcrossTheYearEnd() {
        val r = rec(RepeatKind.MONTHLY)
        val a = d("2026-11-15")
        assertEquals(d("2026-12-15"), Schedules.nextAfter(r, a, a))
        assertEquals(d("2027-01-15"), Schedules.nextAfter(r, a, d("2026-12-15")))
        assertEquals(d("2027-03-15"), Schedules.firstOnOrAfter(r, a, d("2027-02-16")))
        assertEquals(d("2027-02-15"), Schedules.lastOnOrBefore(r, a, d("2027-03-14")))
    }

    @Test
    fun yearlyOnTheLeapDayFallsBackToThe28thInOtherYears() {
        val r = rec(RepeatKind.YEARLY)
        val a = d("2024-02-29")
        assertTrue(Schedules.occursOn(r, a, d("2025-02-28")))
        assertFalse(Schedules.occursOn(r, a, d("2025-03-01")))
        assertTrue(Schedules.occursOn(r, a, d("2028-02-29")))
        assertFalse(Schedules.occursOn(r, a, d("2028-02-28")))
        val seq = generateSequence(a) { Schedules.nextAfter(r, a, it) }.take(6).toList()
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29", "2029-02-28").map(::d), seq)
        assertEquals(d("2027-02-28"), Schedules.lastOnOrBefore(r, a, d("2028-02-28")))
        assertEquals(d("2028-02-29"), Schedules.firstOnOrAfter(r, a, d("2027-03-01")))
        val every2 = rec(RepeatKind.YEARLY, 2)
        assertTrue(Schedules.occursOn(every2, a, d("2026-02-28")))
        assertFalse(Schedules.occursOn(every2, a, d("2025-02-28")))
        assertEquals(d("2028-02-29"), Schedules.nextAfter(every2, a, d("2026-02-28")))
    }

    @Test
    fun onceOccursOnlyOnItsDate() {
        val r = rec(RepeatKind.ONCE)
        val a = d("2026-11-10")
        assertTrue(Schedules.occursOn(r, a, a))
        assertFalse(Schedules.occursOn(r, a, d("2026-11-11")))
        assertFalse(Schedules.occursOn(r, a, d("2026-11-09")))
        assertEquals(a, Schedules.lastOnOrBefore(r, a, d("2027-01-01")))
        assertNull(Schedules.lastOnOrBefore(r, a, d("2026-11-09")))
        assertEquals(a, Schedules.firstOnOrAfter(r, a, d("2026-11-01")))
        assertEquals(a, Schedules.firstOnOrAfter(r, a, a))
        assertNull(Schedules.firstOnOrAfter(r, a, d("2026-11-11")))
        assertNull(Schedules.nextAfter(r, a, a))
        assertEquals(a, Schedules.nextAfter(r, a, d("2026-11-09")))
    }

    @Test
    fun lookupsAgreeWithOccursOnForEveryDay() {
        val recurrences = listOf(
            rec(RepeatKind.DAILY), rec(RepeatKind.DAILY, 3), rec(RepeatKind.DAILY, 10),
            rec(RepeatKind.WEEKLY), rec(RepeatKind.WEEKLY, 2), rec(RepeatKind.WEEKLY, 1, 1, 4), rec(RepeatKind.WEEKLY, 2, 1, 4, 7), rec(RepeatKind.WEEKLY, 3, 2),
            rec(RepeatKind.MONTHLY), rec(RepeatKind.MONTHLY, 2), rec(RepeatKind.MONTHLY, 5),
            rec(RepeatKind.YEARLY), rec(RepeatKind.YEARLY, 2),
        )
        val anchors = listOf("2026-01-31", "2024-02-29", "2026-11-02", "2026-11-05", "2026-03-15", "2025-12-30").map(::d)
        for (r in recurrences) for (a in anchors) {
            if (!Schedules.occursOn(r, a, a)) continue
            val occ = (0..1900).map { a.plus(it, DateTimeUnit.DAY) }.filter { Schedules.occursOn(r, a, it) }
            fun firstIndexAtOrAfter(day: LocalDate): Int { val i = occ.binarySearch(day); return if (i >= 0) i else -i - 1 }
            for (offset in -3..900) {
                val day = a.plus(offset, DateTimeUnit.DAY)
                val i = firstIndexAtOrAfter(day)
                val expectedFirst = occ[i]
                val expectedLast = if (i < occ.size && occ[i] == day) day else occ.getOrNull(i - 1)
                assertEquals(expectedLast, Schedules.lastOnOrBefore(r, a, day), "last: $r anchor $a day $day")
                assertEquals(expectedFirst, Schedules.firstOnOrAfter(r, a, day), "first: $r anchor $a day $day")
                val j = firstIndexAtOrAfter(day.plus(1, DateTimeUnit.DAY))
                assertEquals(occ[j], Schedules.nextAfter(r, a, day), "next: $r anchor $a day $day")
            }
        }
    }

    @Test
    fun anchorOutsideTheChosenWeekdaysIsNotAnOccurrence() {
        val r = rec(RepeatKind.WEEKLY, 1, 1, 4)
        val wednesday = d("2026-11-04")
        assertFalse(Schedules.occursOn(r, wednesday, wednesday))
        assertEquals(d("2026-11-05"), Schedules.firstOnOrAfter(r, wednesday, d("2026-11-01")))
        assertEquals(d("2026-11-05"), Schedules.nextAfter(r, wednesday, d("2026-11-03")))
    }

    @Test
    fun schedulesDependOnlyOnTheirInputs() {
        val r = rec(RepeatKind.MONTHLY)
        val a = d("2026-01-31")
        val first = (0 until 200).map { Schedules.nextAfter(r, a, a.plus(it * 3, DateTimeUnit.DAY)) }
        val second = (0 until 200).map { Schedules.nextAfter(r, a, a.plus(it * 3, DateTimeUnit.DAY)) }
        assertEquals(first, second)
        assertEquals(Schedules.firstOnOrAfter(r, a, d("2027-03-01")), generateSequence(a) { Schedules.nextAfter(r, a, it) }.first { it >= d("2027-03-01") })
    }

    @Test
    fun aTitleOnlyChoreIsWheneverUntilItIsDone() {
        val c = chore("sweep")
        assertEquals(ChoreView(c, ChoreState.ANYTIME, null, null, null), view(c))
        val done = entry("e1", "sweep", "2026-11-06", day = "2026-11-06")
        assertEquals(ChoreView(c, ChoreState.DONE, d("2026-11-06"), d("2026-11-06"), done), view(c, done))
        assertEquals(ChoreState.ANYTIME, view(c, entry("e2", "sweep", "2026-11-06", EntryStatus.SKIPPED)).state)
        assertEquals(ChoreState.ANYTIME, view(c, entry("e3", "sweep", "2026-11-06", EntryStatus.DEFERRED)).state)
        val later = entry("e4", "sweep", "2026-11-07")
        assertEquals(later, view(c, done, later, entry("x", "other", "2026-11-07")).entry)
    }

    @Test
    fun aOneTimeChoreIsDueOverdueOrUpcoming() {
        assertEquals(ChoreView(chore("a", "2026-11-07"), ChoreState.DUE, today, today, null), view(chore("a", "2026-11-07")))
        assertEquals(ChoreView(chore("b", "2026-11-01"), ChoreState.OVERDUE, d("2026-11-01"), d("2026-11-01"), null), view(chore("b", "2026-11-01")))
        assertEquals(ChoreView(chore("c", "2026-11-20"), ChoreState.UPCOMING, d("2026-11-20"), d("2026-11-20"), null), view(chore("c", "2026-11-20")))
    }

    @Test
    fun aOneTimeChoreDoneOrSkippedStaysSettled() {
        val c = chore("a", "2026-11-01")
        val done = entry("e", "a", "2026-11-01", day = "2026-11-06")
        assertEquals(ChoreView(c, ChoreState.DONE, d("2026-11-01"), d("2026-11-01"), done), view(c, done))
        val skipped = entry("s", "a", "2026-11-01", EntryStatus.SKIPPED)
        assertEquals(ChoreView(c, ChoreState.SKIPPED, d("2026-11-01"), d("2026-11-01"), skipped), view(c, skipped))
        assertEquals(ChoreState.DONE, view(c, skipped, done).state)
    }

    @Test
    fun aDeferredOneTimeChoreIsUpcomingUntilItsNewDayThenDue() {
        val c = chore("a", "2026-11-01")
        val deferred = entry("e", "a", "2026-11-01", EntryStatus.DEFERRED, defer = "2026-11-09")
        assertEquals(ChoreView(c, ChoreState.UPCOMING, d("2026-11-01"), d("2026-11-09"), deferred), view(c, deferred))
        assertEquals(ChoreView(c, ChoreState.DUE, d("2026-11-01"), d("2026-11-09"), deferred), view(c, deferred, on = d("2026-11-09")))
        assertEquals(ChoreState.OVERDUE, view(c, deferred, on = d("2026-11-10")).state)
        val bare = entry("b", "a", "2026-11-01", EntryStatus.DEFERRED)
        assertEquals(ChoreState.OVERDUE, view(c, bare).state)
        assertEquals(d("2026-11-01"), view(c, bare).dueDay)
    }

    @Test
    fun aRecurringChoreAMonthBehindShowsOneOverdueOccurrenceNotABacklog() {
        val weekly = chore("w", "2026-10-01", rec(RepeatKind.WEEKLY))
        val data = BaseData(chores = listOf(weekly, chore("m", "2026-08-15", rec(RepeatKind.MONTHLY))), entries = listOf(entry("e", "m", "2026-08-15")))
        val views = BaseRules.views(data, today)
        assertEquals(2, views.size)
        val w = views.single { it.chore.id == "w" }
        assertEquals(ChoreView(weekly, ChoreState.OVERDUE, d("2026-11-05"), d("2026-11-05"), null), w)
        val m = views.single { it.chore.id == "m" }
        assertEquals(ChoreState.OVERDUE, m.state)
        assertEquals(d("2026-10-15"), m.occurrence)
        assertEquals(2, BaseRules.attention(views).size)
    }

    @Test
    fun aRecurringChoreDoneTodayIsDoneAndPointsToTheNextOccurrence() {
        val c = chore("d", "2026-11-01", rec(RepeatKind.DAILY))
        assertEquals(ChoreState.DUE, view(c).state)
        val done = entry("e", "d", "2026-11-07")
        assertEquals(ChoreView(c, ChoreState.DONE, today, d("2026-11-08"), done), view(c, done))
        val skipped = entry("s", "d", "2026-11-07", EntryStatus.SKIPPED)
        assertEquals(ChoreView(c, ChoreState.SKIPPED, today, d("2026-11-08"), skipped), view(c, skipped))
    }

    @Test
    fun anOverdueOccurrenceDoneLateLeavesTheNextOneUpcoming() {
        val c = chore("w", "2026-10-01", rec(RepeatKind.WEEKLY))
        val late = entry("e", "w", "2026-11-05", day = "2026-11-07")
        assertEquals(ChoreView(c, ChoreState.UPCOMING, d("2026-11-12"), d("2026-11-12"), null), view(c, late))
        assertEquals(ChoreState.OVERDUE, view(c, entry("old", "w", "2026-10-29")).state)
    }

    @Test
    fun anOccurrenceDoneEarlyIsDoneAndTheNextIsTheOneAfter() {
        val weekly = chore("w", "2026-10-01", rec(RepeatKind.WEEKLY))
        val early = entry("e", "w", "2026-11-12", day = "2026-11-07")
        assertEquals(ChoreView(weekly, ChoreState.DONE, d("2026-11-12"), d("2026-11-19"), early), view(weekly, early))
        val daily = chore("d", "2026-11-01", rec(RepeatKind.DAILY))
        val tomorrow = entry("t", "d", "2026-11-08", day = "2026-11-07")
        assertEquals(ChoreView(daily, ChoreState.DONE, d("2026-11-08"), d("2026-11-09"), tomorrow), view(daily, tomorrow))
    }

    @Test
    fun aDeferredRecurringOccurrenceFollowsItsNewDay() {
        val c = chore("w", "2026-10-01", rec(RepeatKind.WEEKLY))
        val toMonday = entry("e", "w", "2026-11-05", EntryStatus.DEFERRED, defer = "2026-11-09")
        assertEquals(ChoreView(c, ChoreState.UPCOMING, d("2026-11-05"), d("2026-11-09"), toMonday), view(c, toMonday))
        val toToday = entry("f", "w", "2026-11-05", EntryStatus.DEFERRED, defer = "2026-11-07")
        assertEquals(ChoreState.DUE, view(c, toToday).state)
    }

    @Test
    fun aRecurringChoreThatHasNotStartedIsUpcomingOnItsFirstDate() {
        val c = chore("d", "2026-11-20", rec(RepeatKind.DAILY))
        assertEquals(ChoreView(c, ChoreState.UPCOMING, d("2026-11-20"), d("2026-11-20"), null), view(c))
    }

    @Test
    fun pausedAndArchivedChoresShowNoOccurrence() {
        val paused = chore("p", "2026-11-01", status = RecordStatus.PAUSED)
        val archived = chore("a", "2026-11-01", status = RecordStatus.ARCHIVED)
        assertEquals(ChoreView(paused, ChoreState.PAUSED, null, null, null), view(paused))
        assertEquals(ChoreView(archived, ChoreState.ARCHIVED, null, null, null), view(archived))
        val data = BaseData(chores = listOf(paused, archived))
        assertEquals(listOf("p"), BaseRules.views(data, today).map { it.chore.id })
        assertTrue(BaseRules.attention(BaseRules.views(data, today)).isEmpty())
    }

    @Test
    fun choresAreListedMostPressingFirst() {
        val chores = listOf(
            chore("paused", "2026-11-01", status = RecordStatus.PAUSED),
            chore("done", "2026-11-01"),
            chore("whenever"),
            chore("later", "2026-11-20"),
            chore("soon", "2026-11-09"),
            chore("today", "2026-11-07"),
            chore("old", "2026-10-01"),
            chore("older", "2026-09-01"),
        )
        val data = BaseData(chores = chores, entries = listOf(entry("e", "done", "2026-11-01")))
        assertEquals(listOf("older", "old", "today", "soon", "later", "whenever", "done", "paused"), BaseRules.views(data, today).map { it.chore.id })
    }

    @Test
    fun editingAChoreNeverChangesWhatWasRecorded() {
        val c = chore("w", "2026-10-01", rec(RepeatKind.WEEKLY), title = "Water the plants")
        val entries = listOf(entry("e1", "w", "2026-10-29", at = 1), entry("e2", "w", "2026-11-05", EntryStatus.SKIPPED, at = 2))
        val before = BaseData(chores = listOf(c), entries = entries)
        val edited = before.copy(chores = listOf(c.copy(title = "Water the ferns", recurrence = rec(RepeatKind.DAILY), instructions = "Less water")))
        assertEquals(before.entries, edited.entries)
        assertEquals(BaseRules.history(before, "w"), BaseRules.history(edited, "w"))
        assertEquals(listOf("e2", "e1"), BaseRules.history(edited).map { it.id }, "newest first")
        assertEquals(listOf("e2"), BaseRules.history(edited, "w").take(1).map { it.id })
        assertTrue(BaseRules.history(edited, "nope").isEmpty())
    }

    @Test
    fun aSupplyIsLowOnlyWhenTheQuantityAndTheThresholdAreKnownAndTheQuantityIsAtOrUnderIt() {
        assertFalse(BaseRules.isLow(supply("a", qty = null, low = 2000)), "unknown is not zero")
        assertFalse(BaseRules.isLow(supply("b", qty = 0, low = null)), "no threshold is never inferred")
        assertFalse(BaseRules.isLow(supply("c", qty = null, low = null)))
        assertTrue(BaseRules.isLow(supply("d", qty = 2000, low = 2000)), "equal to the threshold is low")
        assertTrue(BaseRules.isLow(supply("e", qty = 0, low = 0)))
        assertTrue(BaseRules.isLow(supply("f", qty = 1999, low = 2000)))
        assertFalse(BaseRules.isLow(supply("g", qty = 2001, low = 2000)))
        assertFalse(BaseRules.isLow(supply("h", qty = 0, low = 2000, archived = true)))
    }

    @Test
    fun quietedLowSuppliesLeaveTheOverviewButNotTheList() {
        val data = BaseData(supplies = listOf(
            supply("rice", qty = 100, low = 500, name = "Rice"),
            supply("Soap", qty = 100, low = 500, quieted = true, name = "soap"),
            supply("tea", qty = 900, low = 500, name = "Tea"),
            supply("salt", qty = null, low = 500, name = "Salt"),
            supply("old", qty = 0, low = 500, archived = true, name = "Old"),
        ))
        assertEquals(listOf("rice"), BaseRules.lowSupplies(data).map { it.id })
        assertEquals(listOf("rice", "Soap"), BaseRules.lowSupplies(data, includeQuieted = true).map { it.id })
    }

    @Test
    fun theRestockListIsEveryLowSupplyThenWhatWasWrittenByHand() {
        val bread = supply("bread", qty = 5000, low = 1000, name = "Bread")
        val data = BaseData(
            supplies = listOf(supply("soap", qty = 0, low = 1000, quieted = true, name = "Soap"), supply("rice", qty = 100, low = 500, name = "Rice"), bread),
            restock = listOf(RestockEntry("r1", "Light bulbs"), RestockEntry("r2", "Bread", supplyId = "bread"), RestockEntry("r3", "Gone", supplyId = "missing")),
        )
        val list = BaseRules.restockList(data)
        assertEquals(5, list.size)
        assertEquals(listOf("rice", "soap"), list.take(2).map { assertIs<RestockLine.Low>(it).supply.id })
        assertEquals(RestockLine.Manual(data.restock[0], null), list[2])
        assertEquals(RestockLine.Manual(data.restock[1], bread), list[3])
        assertEquals(RestockLine.Manual(data.restock[2], null), list[4])
        assertTrue(BaseRules.restockList(BaseData()).isEmpty())
    }

    @Test
    fun quantitiesAreParsedInThousandths() {
        assertEquals(12_000L, Quantity.parse("12"))
        assertEquals(1_500L, Quantity.parse("1.5"))
        assertEquals(250L, Quantity.parse("0,25"))
        assertEquals(500L, Quantity.parse(".5"))
        assertEquals(1_234L, Quantity.parse("1.234"))
        assertEquals(7_000L, Quantity.parse("  7 "))
        assertEquals(0L, Quantity.parse("0"))
        assertEquals(Quantity.MAX_MILLI, Quantity.parse("1000000"))
    }

    @Test
    fun notAQuantityIsNull() {
        for (bad in listOf("1.2345", "abc", "-1", "", "   ", ".", "1.2.3", "1,2,3", "12kg", "1 000", "1000001", "1234567890")) assertNull(Quantity.parse(bad), "'$bad'")
    }

    @Test
    fun quantitiesAreShownPlainlyAndUnknownIsNotZero() {
        assertEquals("12", Quantity.format(12_000))
        assertEquals("1.5", Quantity.format(1_500))
        assertEquals("0.25", Quantity.format(250))
        assertEquals("1.001", Quantity.format(1_001))
        assertEquals("0", Quantity.format(0))
        assertEquals("Unknown", Quantity.format(null))
        for (milli in listOf(0L, 1L, 10L, 250L, 999L, 1_000L, 1_500L, 12_345L, 999_999_999L, Quantity.MAX_MILLI)) {
            assertEquals(milli, Quantity.parse(Quantity.format(milli)), "round trip of $milli")
        }
    }

    @Test
    fun aCalendarScheduleDoneOnTimeMovesToTheNextDate() {
        val s = schedule("boiler", next = "2026-11-15", first = "2026-01-15")
        assertEquals(d("2026-12-15"), BaseRules.advance(s, d("2026-11-15")).nextDue)
        assertEquals(s.copy(nextDue = d("2026-12-15")), BaseRules.advance(s, d("2026-11-15")))
    }

    @Test
    fun aCalendarScheduleDoneEarlyStillMovesToTheDateAfterTheDueOne() {
        val s = schedule("boiler", next = "2026-11-15", first = "2026-01-15")
        assertEquals(d("2026-12-15"), BaseRules.advance(s, d("2026-11-01")).nextDue)
    }

    @Test
    fun aCalendarScheduleDoneLateLeavesNoBacklogAndKeepsItsDay() {
        val s = schedule("boiler", next = "2026-11-15", first = "2026-01-15")
        assertEquals(d("2027-01-15"), BaseRules.advance(s, d("2026-12-20")).nextDue, "done after the 15 Dec date: the next is 15 Jan, nothing is owed for December")
        assertEquals(d("2026-12-15"), BaseRules.advance(s, d("2026-11-20")).nextDue)
        val end = schedule("gutters", next = "2026-02-28", first = "2026-01-31")
        assertEquals(d("2026-03-31"), BaseRules.advance(end, d("2026-02-28")).nextDue)
        val twice = BaseRules.advance(BaseRules.advance(end, d("2026-02-28")), d("2026-03-31"))
        assertEquals(d("2026-04-30"), twice.nextDue)
    }

    @Test
    fun anAfterDoneScheduleStartsAgainFromTheDayItWasDone() {
        val s = schedule("filter", next = "2026-11-15", first = "2026-01-15", policy = SchedulePolicy.AFTER_DONE)
        assertEquals(d("2026-12-20"), BaseRules.advance(s, d("2026-11-20")).nextDue)
        assertEquals(d("2026-12-01"), BaseRules.advance(s, d("2026-11-01")).nextDue, "done early: counted from the day it was done")
        assertEquals(d("2027-01-10"), BaseRules.advance(schedule("w", next = "2026-12-13", r = rec(RepeatKind.WEEKLY, 4), policy = SchedulePolicy.AFTER_DONE), d("2026-12-13")).nextDue)
    }

    @Test
    fun aOneTimeScheduleIsArchivedWhenDone() {
        val s = schedule("sweep", next = "2026-11-15", r = rec(RepeatKind.ONCE))
        assertEquals(s.copy(status = RecordStatus.ARCHIVED), BaseRules.advance(s, d("2026-11-15")))
        assertEquals(d("2026-11-15"), BaseRules.advance(s, d("2026-12-01")).nextDue)
    }

    @Test
    fun aScheduleIsOverdueDueSoonOrLater() {
        fun state(next: String, status: RecordStatus = RecordStatus.ACTIVE) = BaseRules.scheduleView(schedule("s", next = next, status = status), today)
        assertEquals(DueState.OVERDUE, state("2026-11-06").state)
        assertEquals(-1, state("2026-11-06").daysUntil)
        assertEquals(DueState.DUE, state("2026-11-07").state)
        assertEquals(0, state("2026-11-07").daysUntil)
        assertEquals(DueState.SOON, state("2026-11-08").state)
        assertEquals(DueState.SOON, state("2026-11-21").state)
        assertEquals(14, state("2026-11-21").daysUntil)
        assertEquals(DueState.LATER, state("2026-11-22").state)
        assertEquals(DueState.PAUSED, state("2026-11-01", RecordStatus.PAUSED).state)
        assertEquals(DueState.PAUSED, state("2026-11-01", RecordStatus.ARCHIVED).state)
    }

    @Test
    fun schedulesAreListedMostPressingFirstWithPausedLastAndArchivedHidden() {
        val data = BaseData(schedules = listOf(
            schedule("later", "2027-01-01"), schedule("paused", "2026-10-01", status = RecordStatus.PAUSED), schedule("archived", "2026-10-01", status = RecordStatus.ARCHIVED),
            schedule("overdue", "2026-10-15"), schedule("soon", "2026-11-10"),
        ))
        assertEquals(listOf("overdue", "soon", "later", "paused"), BaseRules.schedules(data, today).map { it.schedule.id })
    }

    @Test
    fun aWarrantyIsUndatedCoveredEndingOrExpired() {
        fun state(end: String?) = BaseRules.warrantyState(Warranty("w", "a", expiryDay = end?.let(::d)), today)
        assertEquals(WarrantyState.UNDATED, state(null))
        assertEquals(WarrantyState.ACTIVE, state("2026-12-08"))
        assertEquals(WarrantyState.ENDING, state("2026-12-07"))
        assertEquals(WarrantyState.ENDING, state("2026-11-07"))
        assertEquals(WarrantyState.EXPIRED, state("2026-11-06"))
    }

    @Test
    fun maintenanceHistoryIsNewestFirstForOneAssetOrAll() {
        fun rec(id: String, asset: String?, day: String, at: Long = 0) = MaintenanceRecord(id, id, assetId = asset, day = d(day), atMillis = at)
        val data = BaseData(maintenance = listOf(rec("a", "x", "2026-10-01"), rec("b", "y", "2026-11-01"), rec("c", "x", "2026-11-01", at = 5), rec("d", null, "2026-09-01")))
        assertEquals(listOf("c", "b", "a", "d"), BaseRules.maintenanceHistory(data).map { it.id })
        assertEquals(listOf("c", "a"), BaseRules.maintenanceHistory(data, "x").map { it.id })
    }

    private fun refused(c: Check): String = assertIs<Check.Refused>(c).reason

    @Test
    fun aChoreNeedsAFairTitleAndSaneNumbers() {
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "Sweep")))
        assertEquals("Give the chore a name.", refused(BaseRules.checkChore(Chore("a", "   "))))
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "x".repeat(80))))
        assertIs<Check.Refused>(BaseRules.checkChore(Chore("a", "x".repeat(81))))
        assertIs<Check.Refused>(BaseRules.checkChore(Chore("a", "Sweep", instructions = "x".repeat(BaseRules.TEXT_MAX + 1))))
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "Sweep", instructions = "x".repeat(BaseRules.TEXT_MAX))))
        assertIs<Check.Refused>(BaseRules.checkChore(Chore("a", "Sweep", minutes = 0)))
        assertIs<Check.Refused>(BaseRules.checkChore(Chore("a", "Sweep", minutes = 1441)))
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "Sweep", minutes = 1440)))
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "Sweep", minutes = 1)))
        assertIs<Check.Refused>(BaseRules.checkChore(Chore("a", "Sweep", recurrence = rec(RepeatKind.DAILY))), "repeating needs a first date")
        assertEquals(Check.Ok, BaseRules.checkChore(Chore("a", "Sweep", recurrence = rec(RepeatKind.DAILY), firstDue = today)))
    }

    @Test
    fun aRecurrenceIsRefusedWhenItsNumbersMakeNoSense() {
        assertEquals(Check.Ok, BaseRules.checkRecurrence(Recurrence(), null), "a one-time chore needs no date to be valid")
        assertEquals(Check.Ok, BaseRules.checkRecurrence(rec(RepeatKind.WEEKLY, 99, 1, 7), today))
        assertEquals("Repeat every 1 to 99.", refused(BaseRules.checkRecurrence(rec(RepeatKind.DAILY, 0), today)))
        assertIs<Check.Refused>(BaseRules.checkRecurrence(rec(RepeatKind.DAILY, 100), today))
        assertIs<Check.Refused>(BaseRules.checkRecurrence(rec(RepeatKind.DAILY, -1), today))
        assertEquals("A weekday is Monday to Sunday.", refused(BaseRules.checkRecurrence(rec(RepeatKind.WEEKLY, 1, 0), today)))
        assertIs<Check.Refused>(BaseRules.checkRecurrence(rec(RepeatKind.WEEKLY, 1, 1, 8), today))
        assertEquals("Choose the first date.", refused(BaseRules.checkRecurrence(rec(RepeatKind.MONTHLY), null)))
        assertEquals("Choose the first date.", refused(BaseRules.checkRecurrence(rec(RepeatKind.YEARLY), null)))
    }

    @Test
    fun aSupplyNeedsANameAndAnHonestQuantity() {
        assertEquals(Check.Ok, BaseRules.checkSupply(Supply("s", "Rice")))
        assertEquals(Check.Ok, BaseRules.checkSupply(Supply("s", "Rice", quantityMilli = 0, lowMilli = 500, unit = "kg")))
        assertEquals("Give the supply a name.", refused(BaseRules.checkSupply(Supply("s", " "))))
        assertIs<Check.Refused>(BaseRules.checkSupply(Supply("s", "Rice", quantityMilli = -1)))
        assertIs<Check.Refused>(BaseRules.checkSupply(Supply("s", "Rice", lowMilli = -1)))
        assertIs<Check.Refused>(BaseRules.checkSupply(Supply("s", "Rice", quantityMilli = Quantity.MAX_MILLI + 1)))
        assertEquals(Check.Ok, BaseRules.checkSupply(Supply("s", "Rice", quantityMilli = Quantity.MAX_MILLI)))
        assertIs<Check.Refused>(BaseRules.checkSupply(Supply("s", "Rice", unit = "x".repeat(17))))
        assertEquals(Check.Ok, BaseRules.checkSupply(Supply("s", "Rice", unit = "x".repeat(16))))
        assertIs<Check.Refused>(BaseRules.checkSupply(Supply("s", "Rice", notes = "x".repeat(BaseRules.NOTE_MAX + 1))))
        assertEquals(Check.Ok, BaseRules.checkQuantity(null))
        assertIs<Check.Refused>(BaseRules.checkQuantity(-5))
    }

    @Test
    fun theOverviewListsOnlyWhatNeedsAttention() {
        val data = BaseData(
            chores = listOf(
                chore("overdue", "2026-11-01"), chore("due", "2026-11-07"), chore("upcoming", "2026-11-20"), chore("whenever"), chore("done", "2026-11-01"),
                chore("paused", "2026-11-01", status = RecordStatus.PAUSED),
            ),
            entries = listOf(entry("e", "done", "2026-11-01")),
            schedules = listOf(
                schedule("m-overdue", "2026-11-01"), schedule("m-due", "2026-11-07"), schedule("m-soon", "2026-11-15"), schedule("m-later", "2026-12-30"),
                schedule("m-paused", "2026-11-01", status = RecordStatus.PAUSED),
            ),
            supplies = listOf(
                supply("low", qty = 1000, low = 2000), supply("quieted", qty = 1000, low = 2000, quieted = true), supply("fine", qty = 5000, low = 2000), supply("unknown"),
            ),
            warranties = listOf(
                Warranty("ending", "a", expiryDay = d("2026-11-20")), Warranty("active", "a", expiryDay = d("2027-06-01")),
                Warranty("expired", "a", expiryDay = d("2026-01-01")), Warranty("undated", "a"),
            ),
        )
        val o = BaseRules.overview(data, today)
        assertEquals(listOf("overdue", "due"), o.chores.map { it.chore.id })
        assertEquals(listOf("m-overdue", "m-due", "m-soon"), o.maintenance.map { it.schedule.id })
        assertEquals(listOf("low"), o.low.map { it.id })
        assertEquals(listOf("ending"), o.warranties.map { it.id })
        assertTrue(o.needsAttention)
    }

    @Test
    fun anOverviewWithNothingToLookAtSaysSo() {
        assertFalse(BaseRules.overview(BaseData(), today).needsAttention)
        val calm = BaseData(
            chores = listOf(chore("later", "2026-12-01"), chore("whenever")),
            schedules = listOf(schedule("later", "2027-01-01")),
            supplies = listOf(supply("ok", qty = 5000, low = 1000)),
            warranties = listOf(Warranty("w", "a", expiryDay = d("2030-01-01"))),
        )
        assertFalse(BaseRules.overview(calm, today).needsAttention)
    }

    private val searchData = BaseData(
        areas = listOf(BaseArea("kit", "Kitchen")),
        chores = listOf(
            Chore("c1", "Clean fridge", areaId = "kit"), Chore("c2", "air fridge vent"), Chore("c3", "Sweep", instructions = "Use the FRIDGE brush"), Chore("c4", "Mop", category = "floors"),
        ),
        supplies = listOf(
            Supply("s1", "Fridge magnets"), Supply("s2", "Old fridge pads", archived = true), Supply("s3", "Rice", areaId = "kit"),
        ),
        assets = listOf(
            Asset("a1", "Fridge", brand = "Cool", model = "X1", serial = "SN-123"), Asset("a2", "Retired fridge", archived = true), Asset("a3", "Kettle", areaId = "kit"),
        ),
        schedules = listOf(MaintenanceSchedule("m1", "Defrost", assetId = "a1", firstDue = today, nextDue = today)),
        warranties = listOf(Warranty("w1", "a1", provider = "CoolCare")),
        references = listOf(Reference("r1", "Manual", text = "fridge manual", areaId = "kit"), Reference("r2", "Wifi", text = "password")),
    )

    @Test
    fun searchFindsRecordsOfEveryKindIgnoringCase() {
        val hits = BaseRules.search(searchData, "FrIdGe")
        assertEquals(
            listOf(
                Hit(HitKind.CHORE, "c2", "air fridge vent", ""),
                Hit(HitKind.CHORE, "c1", "Clean fridge", "Kitchen"),
                Hit(HitKind.CHORE, "c3", "Sweep", ""),
                Hit(HitKind.SUPPLY, "s1", "Fridge magnets", ""),
                Hit(HitKind.ASSET, "a1", "Fridge", "Cool X1"),
                Hit(HitKind.MAINTENANCE, "m1", "Defrost", "Fridge"),
                Hit(HitKind.WARRANTY, "w1", "Fridge", "CoolCare"),
                Hit(HitKind.REFERENCE, "r1", "Manual", ""),
            ),
            hits,
        )
        assertEquals(hits, BaseRules.search(searchData, "  fridge  "))
    }

    @Test
    fun searchLooksAtAreasCategoriesAndSerials() {
        assertEquals(listOf(HitKind.CHORE, HitKind.SUPPLY, HitKind.ASSET, HitKind.REFERENCE), BaseRules.search(searchData, "kitchen").map { it.kind })
        assertEquals(listOf("c4"), BaseRules.search(searchData, "FLOORS").map { it.id })
        assertEquals(listOf("a1"), BaseRules.search(searchData, "sn-123").map { it.id })
        assertEquals(listOf("w1"), BaseRules.search(searchData, "coolcare").map { it.id })
        assertTrue(BaseRules.search(searchData, "zzz").isEmpty())
    }

    @Test
    fun searchWithNoWordsFindsNothing() {
        assertTrue(BaseRules.search(searchData, "").isEmpty())
        assertTrue(BaseRules.search(searchData, "   ").isEmpty())
    }

    @Test
    fun searchSkipsArchivedSuppliesAndAssetsAndNamesAWarrantyAfterItsAsset() {
        val ids = BaseRules.search(searchData, "fridge").map { it.id }
        assertFalse("s2" in ids)
        assertFalse("a2" in ids)
        val orphan = BaseData(warranties = listOf(Warranty("w", "gone", provider = "Acme")))
        assertEquals(listOf(Hit(HitKind.WARRANTY, "w", "Warranty", "Acme")), BaseRules.search(orphan, "acme"))
    }

    @Test
    fun searchLeavesOutWhatNoListShows() {
        val data = BaseData(
            chores = listOf(chore("live", title = "Dust shelf"), chore("old", status = RecordStatus.ARCHIVED, title = "Dust attic"), chore("held", status = RecordStatus.PAUSED, title = "Dust lamp")),
            assets = listOf(Asset("gone", "Dusty fan", archived = true)),
            schedules = listOf(schedule("Dust filter", "2026-12-01"), schedule("Dust vent", "2026-10-01", status = RecordStatus.ARCHIVED)),
            warranties = listOf(Warranty("w", "gone", provider = "Dustco")),
        )
        assertEquals(listOf("held", "live", "Dust filter"), BaseRules.search(data, "dust").map { it.id })
    }

    @Test
    fun theDailyShareIsCountedByTheGameNotByTheRules() {
        val many = (1..BaseRules.CHORES_PAID_PER_DAY + 2).map { entry("p$it", "c", "2026-11-01", day = "2026-11-07", paid = true) }
        assertTrue(BaseRules.chorePays(BaseData(entries = many), today))
        assertTrue(BaseRules.maintenancePays(BaseData(), today))
    }

    @Test
    fun historyOlderThanFourHundredDaysIsDropped() {
        val old = entry("old", "c", "2025-09-01", day = "2025-09-01")
        val edge = entry("edge", "c", "2025-10-12", day = "2025-10-12")
        val recent = entry("new", "c", "2026-11-01", day = "2026-11-01")
        val kept = BaseRules.trimEntries(listOf(old, edge, recent), today)
        assertEquals(listOf("edge", "new"), kept.map { it.id })
        val same = listOf(edge, recent)
        assertTrue(BaseRules.trimEntries(same, today) === same)
    }

    @Test
    fun nothingPaysWhenProgressionIsOff() {
        val off = BaseData(settings = BaseSettings(progression = false))
        assertFalse(BaseRules.chorePays(off, today))
        assertFalse(BaseRules.maintenancePays(off, today))
    }

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun fullData() = BaseData(
        areas = listOf(BaseArea("kit", "Kitchen"), BaseArea("old", "Garage", archived = true)),
        chores = listOf(
            Chore("c1", "Water plants", "Twice a week", "kit", "plants", rec(RepeatKind.WEEKLY, 1, 1, 4), d("2026-11-02"), 15, RecordStatus.ACTIVE, 10, 20),
            Chore("c2", "Descale kettle", recurrence = rec(RepeatKind.MONTHLY, 2), firstDue = d("2026-01-31"), status = RecordStatus.PAUSED),
            Chore("c3", "Sweep"),
        ),
        entries = listOf(
            ChoreEntry("e1", "c1", d("2026-11-02"), EntryStatus.DONE, d("2026-11-03"), 1234, "late", null, true),
            ChoreEntry("e2", "c1", d("2026-11-05"), EntryStatus.DEFERRED, d("2026-11-05"), 1300, deferTo = d("2026-11-06")),
        ),
        supplies = listOf(Supply("s1", "Rice", "food", 1500, "kg", 500, "kit", "basmati", lowDismissed = true, archived = false, createdMillis = 1, updatedMillis = 2), Supply("s2", "Soap")),
        adjustments = listOf(StockAdjustment("j1", "s1", null, 1500, "bought", 99), StockAdjustment("j2", "s1", 1500, null)),
        restock = listOf(RestockEntry("r1", "Light bulbs", null, 5), RestockEntry("r2", "Rice", "s1")),
        assets = listOf(Asset("a1", "Fridge", "appliance", "Cool", "X1", "SN-123", d("2024-02-29"), "kit", "left side", false, 3, 4)),
        schedules = listOf(MaintenanceSchedule("m1", "Defrost", "a1", "Twice a year", rec(RepeatKind.MONTHLY, 6), SchedulePolicy.AFTER_DONE, d("2026-01-31"), d("2026-07-31"), RecordStatus.ACTIVE, 7)),
        maintenance = listOf(MaintenanceRecord("h1", "Defrosted", "a1", "m1", d("2026-07-31"), "ok", "PHP 500", 8, true)),
        warranties = listOf(Warranty("w1", "a1", "CoolCare", "parts", d("2024-03-01"), d("2027-03-01"), "REF-9", "keep receipt", 9)),
        references = listOf(Reference("r1", "Wifi", "password on the router", "tech", "kit", "a1", 11, 12)),
        settings = BaseSettings(progression = false, tutorialSeen = true, units = listOf("pieces", "jars"), hidden = listOf("supplies")),
    )

    @Test
    fun aFullyPopulatedDocumentSurvivesAJsonRoundTrip() {
        val data = fullData()
        val text = json.encodeToString(BaseData.serializer(), data)
        assertEquals(data, json.decodeFromString(BaseData.serializer(), text))
        assertEquals(text, json.encodeToString(BaseData.serializer(), json.decodeFromString(BaseData.serializer(), text)), "writing it again gives the same document")
    }

    @Test
    fun anEmptyDocumentRoundTripsToo() {
        val text = json.encodeToString(BaseData.serializer(), BaseData())
        assertEquals(BaseData(), json.decodeFromString(BaseData.serializer(), text))
        assertEquals(BaseData(), json.decodeFromString(BaseData.serializer(), "{}"))
    }

    @Test
    fun anOldDocumentWithoutTheNewerFieldsStillDecodesWithDefaults() {
        val old = """
            {"chores":[{"id":"c","title":"Old chore","firstDue":"2026-11-01"}],
             "entries":[{"id":"e","choreId":"c","dueDay":"2026-11-01","status":"DONE","day":"2026-11-01","somethingRemoved":true}],
             "supplies":[{"id":"s","name":"Rice","quantityMilli":2000}],
             "settings":{"progression":false},
             "aFieldFromTheFuture":{"x":1}}
        """.trimIndent()
        val data = json.decodeFromString(BaseData.serializer(), old)
        val chore = data.chores.single()
        assertEquals(Chore("c", "Old chore", firstDue = d("2026-11-01")), chore)
        assertEquals(Recurrence(), chore.recurrence)
        assertEquals(RecordStatus.ACTIVE, chore.status)
        assertEquals(ChoreEntry("e", "c", d("2026-11-01"), EntryStatus.DONE, d("2026-11-01")), data.entries.single())
        val supply = data.supplies.single()
        assertEquals(2000L, supply.quantityMilli)
        assertNull(supply.lowMilli)
        assertFalse(supply.lowDismissed)
        assertEquals(BaseSettings(progression = false), data.settings)
        assertEquals(BaseRules.STARTING_UNITS, data.settings.units)
        assertTrue(data.assets.isEmpty() && data.references.isEmpty() && data.schedules.isEmpty() && data.warranties.isEmpty() && data.restock.isEmpty())
    }
}
