package app.pokeplan.ui

import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DeviceCalendar
import kotlinx.datetime.LocalDate

interface CalendarSource {
    fun permitted(): Boolean

    fun requestPermission(done: (Boolean) -> Unit)

    fun calendars(): List<DeviceCalendar>

    fun events(calendarIds: Set<Long>, from: LocalDate, to: LocalDate): List<CalendarEvent>
}
