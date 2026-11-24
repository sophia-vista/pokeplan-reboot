package app.pokeplan.app.integrations

import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSyncTest {
    private val zone = TimeZone.of("Asia/Manila")
    private val today = LocalDate(2026, 11, 24)

    private fun timed(id: String = "e1", title: String = "Choir", day: LocalDate = LocalDate(2026, 11, 26), at: LocalTime = LocalTime(9, 30), minutes: Int = 90) =
        PlanEvent(id, title, day, at, minutes)

    private fun roundTrip(e: PlanEvent) = CalendarEventMapping.fromProvider(CalendarEventMapping.toProvider(e, zone), zone, e.id)

    @Test
    fun aTimedEventSurvivesTheTripToTheProviderAndBack() {
        val e = timed()
        assertEquals(CalendarEventMapping.fingerprint(e), CalendarEventMapping.fingerprint(assertNotNull(roundTrip(e))))
        val p = CalendarEventMapping.toProvider(e, zone)
        assertEquals(p.startMillis + 90 * 60_000L, p.endMillis)
        assertNull(p.rrule)
    }

    @Test
    fun anAllDayEventKeepsItsDaysAndUsesUtcMidnightsWithAnExclusiveEnd() {
        val e = PlanEvent("e1", "Retreat", LocalDate(2026, 12, 1), LocalTime(0, 0), allDay = true, lastDay = LocalDate(2026, 12, 3))
        val p = CalendarEventMapping.toProvider(e, zone)
        assertEquals("UTC", p.timezone)
        assertEquals(3 * 86_400_000L, p.endMillis!! - p.startMillis, "three days, end exclusive")
        val back = assertNotNull(roundTrip(e))
        assertEquals(LocalDate(2026, 12, 3), back.endDay)
        assertEquals(CalendarEventMapping.fingerprint(e), CalendarEventMapping.fingerprint(back))
    }

    @Test
    fun everyRepeatWithItsEndAndSkippedDaysRoundTripsAndUsesADurationNotAnEnd() {
        EventRepeat.entries.forEach { r ->
            val e = timed().copy(repeat = r, until = LocalDate(2027, 3, 1), skipped = setOf(LocalDate(2026, 12, 3)))
            val p = CalendarEventMapping.toProvider(e, zone)
            assertNull(p.endMillis, "a repeating event carries a duration")
            assertEquals("PT1H30M", p.duration)
            val back = assertNotNull(roundTrip(e), r.name)
            assertEquals(CalendarEventMapping.fingerprint(e), CalendarEventMapping.fingerprint(back), r.name)
        }
        val allDay = PlanEvent("a", "Rent", LocalDate(2026, 12, 1), LocalTime(0, 0), allDay = true, repeat = EventRepeat.MONTHLY, skipped = setOf(LocalDate(2027, 1, 1)))
        assertEquals(CalendarEventMapping.fingerprint(allDay), CalendarEventMapping.fingerprint(assertNotNull(roundTrip(allDay))))
        assertEquals("P1D", CalendarEventMapping.toProvider(allDay, zone).duration)
    }

    @Test
    fun aRuleOrSpanPlanCannotShowIsNotUnderstoodAndColourIsOnlyWritten() {
        val base = CalendarEventMapping.toProvider(timed(), zone)
        listOf("FREQ=DAILY;COUNT=5", "FREQ=WEEKLY;BYDAY=MO,WE", "FREQ=MONTHLY;BYSETPOS=2", "FREQ=DAILY;INTERVAL=3").forEach {
            assertNull(CalendarEventMapping.fromProvider(base.copy(endMillis = null, duration = "PT1H", rrule = it), zone), it)
        }
        assertNull(CalendarEventMapping.fromProvider(base.copy(endMillis = base.startMillis), zone), "no length")
        val coloured = timed().copy(color = 0x2D9CDB)
        assertEquals(0xFF2D9CDB.toInt(), CalendarEventMapping.toProvider(coloured, zone).color)
        assertEquals(CalendarEventMapping.fingerprint(coloured), CalendarEventMapping.fingerprint(timed()), "colour is not part of what the sync compares")
    }

    /** A provider in memory: ids start at 100, and a row the player or Google edits is changed straight in [rows]. */
    private class FakeWriter : CalendarWriter {
        val rows = mutableMapOf<Long, ProviderRow>()
        var next = 100L
        var unreadable = false
        var failWrites = false
        val deleted = mutableListOf<Long>()
        override fun writableCalendars() = listOf(TargetCalendar(7, "PokéLife", "me@example.com"))
        override fun read(calendarId: Long, ids: Set<Long>): Map<Long, ProviderRow>? = if (unreadable) null else rows.filterKeys { it in ids }
        override fun insert(calendarId: Long, event: ProviderEvent): Long? = if (failWrites) null else (next++).also { rows[it] = ProviderRow(it, false, event) }
        override fun update(id: Long, event: ProviderEvent): Boolean { if (failWrites) return false; rows[id] = ProviderRow(id, false, event); return true }
        override fun delete(id: Long): Boolean { if (failWrites) return false; rows.remove(id); deleted += id; return true }
    }

    private fun on() = CalendarSyncState(enabled = true, calendarId = 7, calendarName = "PokéLife")
    private fun run(events: List<PlanEvent>, state: CalendarSyncState, w: FakeWriter) = assertNotNull(CalendarSync.run(events, state, w, zone, today))
    private fun CalendarSyncState.after(o: SyncOutcome) = copy(links = o.links)

    @Test
    fun theFirstRunSendsRecentEventsAndAnOldOneStaysHome() {
        val w = FakeWriter()
        val old = timed("old", day = LocalDate(2026, 9, 1))
        val oldRepeating = timed("rep", day = LocalDate(2026, 1, 5)).copy(repeat = EventRepeat.WEEKLY)
        val o = run(listOf(timed(), old, oldRepeating), on(), w)
        assertEquals(setOf("e1", "rep"), o.links.map { it.eventId }.toSet(), "an unfinished series goes, a finished old event does not")
        assertEquals(2, o.created)
        assertEquals(2, w.rows.size)
        assertTrue(o.changes.isEmpty())
    }

    @Test
    fun aChangeOnOneSideGoesToTheOtherAndASecondRunIsQuiet() {
        val w = FakeWriter()
        var state = on()
        val e = timed()
        state = state.after(run(listOf(e), state, w))
        val id = state.links.single().providerId
        // edited here
        val moved = e.copy(title = "Choir practice", start = LocalTime(18, 0))
        val pushed = run(listOf(moved), state, w)
        assertEquals(1, pushed.pushed)
        assertEquals("Choir practice", w.rows.getValue(id).event.title)
        state = state.after(pushed)
        assertTrue(run(listOf(moved), state, w).quiet)
        // edited in Google
        w.rows[id] = ProviderRow(id, false, CalendarEventMapping.toProvider(moved.copy(day = LocalDate(2026, 11, 27)), zone))
        val got = run(listOf(moved), state, w)
        val change = got.changes.single() as LocalChange.Replace
        assertEquals(LocalDate(2026, 11, 27), change.new.day)
        assertEquals(moved.companionId, change.new.companionId, "what is PokéLife's own is kept")
        assertTrue(!change.overwroteLocalEdit)
    }

    @Test
    fun changedOnBothSidesTheCalendarWinsAndTheOverwriteIsFlagged() {
        val w = FakeWriter()
        var state = on()
        val e = timed()
        state = state.after(run(listOf(e), state, w))
        val id = state.links.single().providerId
        w.rows[id] = ProviderRow(id, false, CalendarEventMapping.toProvider(e.copy(title = "From Google"), zone))
        val o = run(listOf(e.copy(title = "From here")), state, w)
        val c = o.changes.single() as LocalChange.Replace
        assertEquals("From Google", c.new.title)
        assertTrue(c.overwroteLocalEdit)
        assertEquals(0, o.pushed)
    }

    @Test
    fun aDeleteOnOneSideIsADeleteOnTheOtherAndAnEditedOneIsKept() {
        val w = FakeWriter()
        var state = on()
        val e = timed()
        state = state.after(run(listOf(e), state, w))
        val id = state.links.single().providerId
        // deleted here
        val gone = run(emptyList(), state, w)
        assertEquals(1, gone.removedInCalendar)
        assertTrue(id in w.deleted && gone.links.isEmpty())
        // deleted in Google, untouched here
        w.rows.clear()
        val w2 = FakeWriter()
        state = on().after(run(listOf(e), on(), w2))
        w2.rows.clear()
        val o = run(listOf(e), state, w2)
        assertEquals(listOf<LocalChange>(LocalChange.Remove(e)), o.changes)
        assertTrue(o.links.isEmpty())
        // deleted in Google but edited here since: kept and written again
        val w3 = FakeWriter()
        state = on().after(run(listOf(e), on(), w3))
        w3.rows.clear()
        val kept = run(listOf(e.copy(title = "Still here")), state, w3)
        assertTrue(kept.changes.isEmpty() && kept.created == 1)
        assertEquals("Still here", w3.rows.values.single().event.title)
        // marked deleted by the provider counts as deleted
        val w4 = FakeWriter()
        state = on().after(run(listOf(e), on(), w4))
        val pid = state.links.single().providerId
        w4.rows[pid] = w4.rows.getValue(pid).copy(deleted = true)
        assertEquals(1, run(listOf(e), state, w4).changes.size)
    }

    @Test
    fun aRowPlanCannotShowKeepsPlansVersionAndWritesItBack() {
        val w = FakeWriter()
        var state = on()
        val e = timed()
        state = state.after(run(listOf(e), state, w))
        val id = state.links.single().providerId
        w.rows[id] = ProviderRow(id, false, w.rows.getValue(id).event.copy(endMillis = null, duration = "PT1H", rrule = "FREQ=DAILY;COUNT=3"))
        val o = run(listOf(e), state, w)
        assertEquals(1, o.unreadable)
        assertTrue(o.changes.isEmpty() && o.pushed == 1)
        assertNull(w.rows.getValue(id).event.rrule, "PokéLife's version is back")
    }

    @Test
    fun anUnreadableCalendarChangesNothingAndAFailedWriteIsRetriedNotLost() {
        val w = FakeWriter()
        w.unreadable = true
        assertNull(CalendarSync.run(listOf(timed()), on(), w, zone, today))
        val bad = FakeWriter().also { it.failWrites = true }
        val o = run(listOf(timed()), on(), bad)
        assertEquals(1, o.failed)
        assertTrue(o.links.isEmpty(), "not linked, so the next run tries again")
    }

    @Test
    fun onlyEventsPokeLifeMadeAreEverTouchedAndNothingIsImported() {
        val w = FakeWriter()
        w.rows[999] = ProviderRow(999, false, CalendarEventMapping.toProvider(timed("x", "Dentist"), zone))
        val o = run(listOf(timed()), on(), w)
        assertEquals(2, w.rows.size, "someone else's event stays, and ours was added")
        assertTrue(w.deleted.isEmpty() && o.changes.isEmpty(), "the other event is not imported or removed")
    }
}
