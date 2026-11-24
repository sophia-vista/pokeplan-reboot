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

class PlanCalendarSyncTest {
    private val zone = TimeZone.UTC
    private var millis = LocalDate(2026, 11, 24).atStartOfDayIn(zone).toEpochMilliseconds() + 9 * 3_600_000L
    private val store = GameStore(MemoryStorage(), object : Clock { override fun nowMillis() = millis }, zone).also { it.start("Sophia", 25) }
    private val integrations = IntegrationStore(MemoryStorage())

    private class Provider : CalendarWriter {
        val rows = mutableMapOf<Long, ProviderRow>()
        var next = 50L
        override fun writableCalendars() = listOf(TargetCalendar(3, "PokéLife", "me@example.com"))
        override fun read(calendarId: Long, ids: Set<Long>): Map<Long, ProviderRow>? = rows.filterKeys { it in ids }
        override fun insert(calendarId: Long, event: ProviderEvent): Long? = (next++).also { rows[it] = ProviderRow(it, false, event) }
        override fun update(id: Long, event: ProviderEvent): Boolean { rows[id] = ProviderRow(id, false, event); return true }
        override fun delete(id: Long): Boolean = rows.remove(id) != null
    }

    private val provider = Provider()
    private val sync = PlanCalendarSync(store, integrations, provider) { zone }
    private val calendar = TargetCalendar(3, "PokéLife", "me@example.com")

    @Test
    fun turningItOnSendsEventsAndALaterEditInTheCalendarComesBack() {
        val e = store.addEvent("Choir", LocalDate(2026, 11, 26), LocalTime(9, 0), 60)
        val first = assertNotNull(sync.enable(calendar))
        assertEquals(1, first.outcome.created)
        assertTrue(sync.enabled && integrations.syncedProviderIds().size == 1)
        val id = integrations.syncedProviderIds().single()
        provider.rows[id] = provider.rows.getValue(id).let { it.copy(event = it.event.copy(title = "Choir at the hall")) }
        val second = assertNotNull(sync.sync())
        assertEquals(1, second.applied)
        assertTrue(second.changedPlan)
        assertEquals("Choir at the hall", store.data.events.single { it.id == e.id }.title)
        assertEquals(listOf("Choir"), second.before.map { it.title }, "the events as they were, for Undo")
        assertTrue(assertNotNull(sync.sync()).outcome.quiet, "nothing left to do")
    }

    @Test
    fun aDeleteInTheCalendarRemovesTheEventHereAndUndoBringsItBackToTheCalendar() {
        store.addEvent("Dentist", LocalDate(2026, 11, 27), LocalTime(14, 0), 30)
        sync.enable(calendar)
        provider.rows.clear()
        val report = assertNotNull(sync.sync())
        assertTrue(store.data.events.isEmpty())
        store.restoreEvents(report.before)
        val again = assertNotNull(sync.sync())
        assertEquals(1, again.outcome.created, "restored here, so it is written to the calendar again")
        assertEquals(1, provider.rows.size)
    }

    @Test
    fun anEditMadeWhileTheSyncRanIsNotOverwritten() {
        val e = store.addEvent("Choir", LocalDate(2026, 11, 26), LocalTime(9, 0), 60)
        sync.enable(calendar)
        val id = integrations.syncedProviderIds().single()
        provider.rows[id] = provider.rows.getValue(id).let { it.copy(event = it.event.copy(title = "From the calendar")) }
        val seen = store.data.events.single()
        store.updateEvent(e.id, title = "Typed just now")
        val applied = store.applyEventChanges(listOf(seen to seen.copy(title = "From the calendar")), emptyList())
        assertEquals(0, applied)
        assertEquals("Typed just now", store.data.events.single().title)
    }

    @Test
    fun aSyncedMoveRestartsTheCompanionLikeAnyOtherEdit() {
        val e = store.addEvent("Choir", LocalDate(2026, 11, 26), LocalTime(9, 0), 60)
        val buddy = store.data.player.pokemon.first()
        store.setInParty(buddy.id, true, app.pokeplan.core.pokemon.PartyIds.PLAN)
        store.setEventCompanion(e.id, buddy.id)
        val before = store.data.events.single()
        millis += 3_600_000L
        val moved = before.copy(day = LocalDate(2026, 11, 28))
        assertEquals(1, store.applyEventChanges(listOf(before to moved), emptyList()))
        val after = store.data.events.single()
        assertEquals(LocalDate(2026, 11, 28), after.day)
        assertEquals(buddy.id, after.companionId, "the companion stays")
        assertEquals(millis, after.companionSince, "but only pays for what it was there for after the move")
    }

    @Test
    fun turningItOffCanLeaveOrRemoveTheEventsAndOnlyOursAreEverRemoved() {
        store.addEvent("Choir", LocalDate(2026, 11, 26), LocalTime(9, 0), 60)
        provider.rows[900] = ProviderRow(900, false, CalendarEventMapping.toProvider(app.pokeplan.plan.PlanEvent("x", "Someone else's", LocalDate(2026, 11, 26), LocalTime(9, 0)), zone))
        sync.enable(calendar)
        assertEquals(2, provider.rows.size)
        sync.disable(removeFromCalendar = false)
        assertFalse(sync.enabled)
        assertEquals(2, provider.rows.size, "kept")
        assertNull(sync.sync(), "off means no pass")
        sync.enable(calendar)
        sync.disable(removeFromCalendar = true)
        assertEquals(setOf(900L), provider.rows.keys, "only PokéLife's rows are removed; the other event stays")
    }
}
