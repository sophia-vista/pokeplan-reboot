package app.pokeplan.ui

import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DeviceCalendar
import kotlinx.datetime.LocalDate

/**
 * Reads the phone's calendars (Google Calendar's among them once its account syncs on the phone), read-only. Only the
 * Android app provides one. The reads can take a moment, so callers run them off the main thread.
 */
interface CalendarSource {
    /** Whether the app may read the calendars. */
    fun permitted(): Boolean

    /** Asks for the calendar permission; [done] gets the answer. */
    fun requestPermission(done: (Boolean) -> Unit)

    fun calendars(): List<DeviceCalendar>

    /** The events of [calendarIds] that touch the days [from] to [to], recurring ones expanded. Declined and cancelled ones are left out. */
    fun events(calendarIds: Set<Long>, from: LocalDate, to: LocalDate): List<CalendarEvent>
}
