package app.pokeplan.app.integrations

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderEditTest {
    private val zone = TimeZone.UTC
    private val day = LocalDate(2026, 11, 30)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atStartOfDayIn(zone).toEpochMilliseconds() + (h * 60 + m) * 60_000L

    // A weekly invite with a COUNT rule that PLAN cannot hold, with a description and a colour.
    private val series = ProviderEvent(
        "Team sync", false, at(day, 10), null, "PT1H", zone.id, "FREQ=WEEKLY;COUNT=8", "20261207T100000Z", color = 0xFF112233.toInt(),
        description = "Agenda in the doc", location = "Room 4",
    )

    @Test fun theFieldsAreReadOutInTheDevicesZoneAndAWholeDayAsDays() {
        val f = assertNotNull(ProviderEdit.fields(series, zone))
        assertEquals(LocalTime(10, 0), f.start); assertEquals(60, f.minutes); assertEquals("Room 4", f.location)
        val trip = ProviderEvent("Trip", true, at(day, 0), at(LocalDate(2026, 12, 3), 0), null, "UTC", null, null)
        val t = assertNotNull(ProviderEdit.fields(trip, zone))
        assertTrue(t.allDay); assertEquals(day, t.day); assertEquals(LocalDate(2026, 12, 2), t.last)
        assertNull(ProviderEdit.fields(series.copy(duration = null), zone), "no length, nothing to edit")
    }

    @Test fun aSeriesEditChangesWordsAndTimeButKeepsTheRuleTheSkippedDaysAndTheColour() {
        val f = ProviderEdit.fields(series, zone)!!.copy(title = "Team sync (moved)", location = "", start = LocalTime(11, 30), minutes = 45)
        val next = ProviderEdit.series(series, f, zone)
        assertEquals("Team sync (moved)", next.title); assertNull(next.location); assertEquals("Agenda in the doc", next.description)
        assertEquals(at(day, 11, 30), next.startMillis)
        assertEquals("PT45M", next.duration); assertNull(next.endMillis, "a repeating row has a duration and no end")
        assertEquals("FREQ=WEEKLY;COUNT=8", next.rrule); assertEquals(series.exdate, next.exdate); assertEquals(series.color, next.color)
    }

    @Test fun anOccurrenceEditIsASingleRowWithNoRepeatAndItsOwnEnd() {
        val f = ProviderEdit.fields(series, zone)!!.copy(day = LocalDate(2026, 12, 4), start = LocalTime(15, 0), minutes = 30, description = "Moved to Friday")
        val one = ProviderEdit.occurrence(series, f, zone)
        assertNull(one.rrule); assertNull(one.exdate); assertNull(one.duration)
        assertEquals(at(LocalDate(2026, 12, 4), 15), one.startMillis); assertEquals(at(LocalDate(2026, 12, 4), 15, 30), one.endMillis)
        assertEquals("Moved to Friday", one.description)
    }

    @Test fun anAllDaySeriesKeepsItsLengthAsADuration() {
        val old = ProviderEvent("Retreat", true, at(day, 0), null, "P2D", "UTC", "FREQ=YEARLY", null)
        val f = ProviderEdit.fields(old, zone)!!.copy(last = LocalDate(2026, 12, 2))
        val next = ProviderEdit.series(old, f, zone)
        assertEquals("P3D", next.duration); assertNull(next.endMillis); assertEquals("FREQ=YEARLY", next.rrule)
    }

    private class Provider : CalendarWriter {
        val rows = mutableMapOf<Long, ProviderRow>()
        val log = mutableListOf<String>()
        override fun writableCalendars() = listOf(TargetCalendar(3, "Family", "me@example.com"))
        override fun read(calendarId: Long, ids: Set<Long>): Map<Long, ProviderRow>? = rows.filterKeys { it in ids }
        override fun insert(calendarId: Long, event: ProviderEvent): Long? = null
        override fun update(id: Long, event: ProviderEvent): Boolean { log += "update $id ${event.title}"; rows[id] = ProviderRow(id, false, event); return true }
        override fun delete(id: Long): Boolean { log += "delete $id"; return rows.remove(id) != null }
        override fun saveOccurrence(seriesId: Long, originalStartMillis: Long, event: ProviderEvent): Boolean { log += "occurrence $seriesId@$originalStartMillis ${event.title}"; return true }
        override fun cancelOccurrence(seriesId: Long, originalStartMillis: Long): Boolean { log += "cancel $seriesId@$originalStartMillis"; return true }
    }

    @Test fun onlyTheChosenCalendarsOtherEventsCanBeEditedAndOnlyWhileTheSyncIsOn() {
        val store = GameStore(MemoryStorage(), object : Clock { override fun nowMillis() = at(day, 8) }, zone).also { it.start("Sophia", 25) }
        val integrations = IntegrationStore(MemoryStorage())
        val provider = Provider().also { it.rows[77] = ProviderRow(77, false, series); it.rows[78] = ProviderRow(78, false, series) }
        val sync = PlanCalendarSync(store, integrations, provider) { zone }
        assertFalse(sync.canEdit(3, 77), "the sync is off")
        sync.enable(TargetCalendar(3, "Family", "me@example.com"))
        integrations.setCalendarSync(integrations.data.calendarSync.copy(links = listOf(SyncLink("plan-event", 78, "x"))))
        assertTrue(sync.canEdit(3, 77))
        assertFalse(sync.canEdit(4, 77), "another calendar stays read-only")
        assertFalse(sync.canEdit(3, 78), "an event PLAN already holds is edited as a PLAN event")
        assertNotNull(sync.open(3, 77)); assertNull(sync.open(3, 78))

        assertTrue(sync.saveEvent(3, 77, series.copy(title = "Renamed")))
        assertTrue(sync.saveOccurrence(3, 77, 123L, series))
        assertTrue(sync.deleteOccurrence(3, 77, 456L))
        assertTrue(sync.deleteEvent(3, 77))
        assertFalse(sync.saveEvent(4, 77, series) || sync.deleteEvent(3, 78) || sync.saveOccurrence(3, 78, 1L, series))
        assertEquals(listOf("update 77 Renamed", "occurrence 77@123 Team sync", "cancel 77@456", "delete 77"), provider.log)
        assertNull(sync.open(3, 77), "deleted")
        assertEquals(1, integrations.data.calendarSync.links.size, "a direct edit never touches the sync links")
    }
}
