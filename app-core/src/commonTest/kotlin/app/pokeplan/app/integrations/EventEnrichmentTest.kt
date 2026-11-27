package app.pokeplan.app.integrations

import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.EventReminders
import app.pokeplan.plan.QuietHours
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

class EventEnrichmentTest {
    private val store = IntegrationStore(MemoryStorage())
    private val key = EventKeys.device(3, 42)
    private val snap = SourceSnapshot("Choir", "2026-11-28", "Family")

    @Test fun aDeviceEventKeyIsTheCalendarAndTheProvidersEventIdSoEveryOccurrenceSharesIt() {
        assertEquals(key, EventKeys.ofDeviceEvent(3, "42@1700000000000"))
        assertEquals(key, EventKeys.ofDeviceEvent(3, "42@1800000000000"))
        assertEquals(3L to 42L, EventKeys.parse(key))
        assertNull(EventKeys.ofDeviceEvent(-1, "42@1"), "an overlay is not a phone-calendar event")
        assertNull(EventKeys.parse("plan-event-id"))
    }

    @Test fun notesChecklistTasksAndRecordsAreKeptApartAndBounded() {
        store.setNotes(key, "Bring the score", snap)
        store.addLine(key, "  Print copies ", "l1"); store.addLine(key, "   ", "l2")
        store.linkTask(key, "t1"); store.linkTask(key, "t1")
        store.linkRecord(key, RecordRef("goal", "g1"))
        val e = assertNotNull(store.enrichmentOf(key))
        assertEquals("Bring the score", e.notes); assertEquals(listOf("Print copies"), e.checklist.map { it.text })
        assertEquals(listOf("t1"), e.taskIds); assertEquals(listOf(RecordRef("goal", "g1")), e.records)
        assertEquals(snap, e.seen)
        store.tickLine(key, "l1")
        assertTrue(store.enrichmentOf(key)!!.checklist.single().done)
        (1..40).forEach { store.addLine(key, "line $it", "x$it") }
        assertEquals(IntegrationStore.MAX_LINES, store.enrichmentOf(key)!!.checklist.size)
        store.setNotes(key, "n".repeat(10_000))
        assertEquals(IntegrationStore.MAX_NOTES, store.enrichmentOf(key)!!.notes.length)
    }

    @Test fun emptyingTheLastThingRemovesTheRecordButPeopleKeepItAlive() {
        store.setNotes(key, "x", snap); store.setNotes(key, "")
        assertNull(store.enrichmentOf(key))
        store.setPeople(key, listOf("p1")); store.setNotes(key, "x"); store.setNotes(key, "")
        assertNotNull(store.enrichmentOf(key), "people on the event keep its record")
    }

    @Test fun pruningNeverDropsAPhoneCalendarEventsNotesOrAKeptNote() {
        store.setNotes(key, "keep me", snap)
        store.setNotes("plan-1", "plan note"); store.setNotes("plan-gone", "gone"); store.linkTask("plan-1", "dead")
        store.keepAsUnavailable("plan-gone", SourceSnapshot("Dentist", "2026-11-20", "Family"))
        store.prune(setOf("plan-1"), emptySet(), setOf("t-live"))
        assertEquals("keep me", store.enrichmentOf(key)?.notes)
        assertTrue(store.enrichmentOf("plan-gone")!!.unavailable, "a kept note survives")
        assertEquals(emptyList(), store.enrichmentOf("plan-1")!!.taskIds, "a link to a task that is gone is dropped")
        store.prune(setOf(), emptySet())
        assertNull(store.enrichmentOf("plan-1"), "a deleted PLAN event takes its notes with it")
    }

    @Test fun anEventThatVanishedIsMarkedOnlyWhenTheProviderConfirmsItAndItWasInTheRange() {
        store.setNotes(key, "n", snap)
        val range = LocalDate(2026, 11, 22)..LocalDate(2026, 11, 29)
        fun run(loaded: Map<String, SourceSnapshot>, shown: Set<Long> = setOf(3), exists: (Long, Long) -> Boolean?) =
            EventSources.reconcile(store.data, loaded, range, shown, exists)
        assertTrue(run(emptyMap()) { _, _ -> true }.missing.isEmpty(), "it still exists, so it only moved")
        assertTrue(run(emptyMap()) { _, _ -> null }.missing.isEmpty(), "cannot tell: nothing is marked")
        assertTrue(run(emptyMap(), shown = setOf(9)) { _, _ -> false }.missing.isEmpty(), "its calendar was not read")
        assertEquals(setOf(key), run(emptyMap()) { _, _ -> false }.missing)
        assertTrue(run(mapOf(key to snap)) { _, _ -> false }.missing.isEmpty(), "it is in the read")
        val outside = EventSources.reconcile(store.data, emptyMap(), LocalDate(2026, 12, 1)..LocalDate(2026, 12, 7), setOf(3)) { _, _ -> false }
        assertTrue(outside.missing.isEmpty(), "last seen outside the days that were read")

        store.markUnavailable(setOf(key))
        assertEquals(listOf(key), store.kept().map { it.key })
        store.seen(mapOf(key to snap.copy(title = "Choir (moved)")))
        assertFalse(store.enrichmentOf(key)!!.unavailable, "back in the calendar, the note is live again")
        assertEquals("Choir (moved)", store.enrichmentOf(key)!!.seen!!.title)
        store.markUnavailable(setOf(key)); store.discard(key)
        assertNull(store.enrichmentOf(key)); assertTrue(store.kept().isEmpty())
    }

    @Test fun anEventReminderFiresBeforeEveryOccurrenceAndNeverAfterTheStart() {
        val zone = TimeZone.UTC
        val now = LocalDate(2026, 11, 27).atStartOfDayIn(zone).toEpochMilliseconds() + 8 * 3_600_000L
        val e = PlanEvent("e1", "Choir", LocalDate(2026, 11, 28), LocalTime(9, 0), reminderMinutes = 30, repeat = app.pokeplan.plan.EventRepeat.DAILY)
        val due = EventReminders.upcoming(listOf(e), now, zone, QuietHours(LocalTime(22, 0), LocalTime(7, 0)))
        assertEquals(LocalDate(2026, 11, 28), due.first().day)
        assertEquals(LocalDate(2026, 11, 28).atStartOfDayIn(zone).toEpochMilliseconds() + (8 * 60 + 30) * 60_000L, due.first().atMillis)
        assertTrue(due.size >= 10, "every occurrence in the next two weeks")
        // Quiet hours (22:00 to 07:00) would push a 06:30 reminder to 07:00, still before an 08:00 start; one for a 06:40 start would land after it and is dropped.
        val early = e.copy(id = "e2", start = LocalTime(6, 40), reminderMinutes = 10)
        assertTrue(EventReminders.upcoming(listOf(early), now, zone).none { it.eventId == "e2" })
        assertTrue(EventReminders.stillWanted(listOf(e), "e1", LocalDate(2026, 11, 29)))
        assertFalse(EventReminders.stillWanted(listOf(e.copy(reminderMinutes = null)), "e1", LocalDate(2026, 11, 29)))
        assertFalse(EventReminders.stillWanted(emptyList(), "e1", LocalDate(2026, 11, 29)))
    }

    @Test fun aMemoryIsAttachedToAnEventByItsIdAndNeverSharesItsFate() {
        // A PLAN event (its series id) and a phone-calendar event both take a memory; the same one twice is one link.
        store.linkMemory("series-1", "m1"); store.linkMemory("series-1", "m1"); store.linkMemory(key, "m1", snap)
        store.linkMemory("series-1", "m2")
        assertEquals(listOf("m1", "m2"), store.memoriesOf("series-1"))
        assertEquals(setOf("series-1", key), store.eventsOfMemory("m1").toSet())
        assertEquals(snap, store.enrichmentOf(key)!!.seen, "a phone-calendar event keeps what it looked like")
        // The link is only the id: nothing of the memory is stored, so deleting or editing the memory has nothing to change here.
        assertEquals(listOf(RecordRef(RecordRef.MEMORY, "m1"), RecordRef(RecordRef.MEMORY, "m2")), store.enrichmentOf("series-1")!!.records)
        // Detaching is explicit and leaves other links, a goal and the event's other additions alone.
        store.linkRecord("series-1", RecordRef(RecordRef.GOAL, "g1")); store.setNotes("series-1", "Bring cake")
        store.unlinkMemory("series-1", "m1")
        assertEquals(listOf("m2"), store.memoriesOf("series-1"))
        assertEquals(listOf(RecordRef(RecordRef.GOAL, "g1")), store.enrichmentOf("series-1")!!.records.filter { it.module == RecordRef.GOAL })
        assertEquals("Bring cake", store.enrichmentOf("series-1")!!.notes)
        assertEquals(listOf(key), store.eventsOfMemory("m1"))
        // The twelve-link cap holds for memories too.
        (1..30).forEach { store.linkMemory("busy", "m$it") }
        assertEquals(IntegrationStore.MAX_LINKS, store.memoriesOf("busy").size)
    }
}
