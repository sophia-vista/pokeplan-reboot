package app.pokeplan.ui

import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DeviceCalendar
import kotlinx.datetime.LocalDate

/**
 * Read-only entries another part of the app adds to the PLAN calendar (LINK dates, for one), without PLAN knowing what they are.
 * An entry's id says which overlay owns it; a tap hands the id back to [open].
 */
interface CalendarOverlay {
    fun events(from: LocalDate, to: LocalDate): List<CalendarEvent>

    fun owns(eventId: String): Boolean

    fun open(eventId: String)
}

/** The permission for the calendar sync to write (PLAN 8.1); asked only when the player turns the sync on. */
interface CalendarWriteAccess {
    fun writePermitted(): Boolean

    fun requestWritePermission(done: (Boolean) -> Unit)
}

interface CalendarSource {
    fun permitted(): Boolean

    fun requestPermission(done: (Boolean) -> Unit)

    fun calendars(): List<DeviceCalendar>

    fun events(calendarIds: Set<Long>, from: LocalDate, to: LocalDate): List<CalendarEvent>

    /** Whether the provider still holds this event (PLAN 8.4: a note whose event is gone is kept, not deleted). Null when it cannot tell. */
    fun eventExists(calendarId: Long, providerId: Long): Boolean? = null
}
