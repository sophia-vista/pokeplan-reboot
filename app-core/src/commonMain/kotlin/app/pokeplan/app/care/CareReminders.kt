package app.pokeplan.app.care

import app.pokeplan.care.CareData
import app.pokeplan.care.Routine
import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import app.pokeplan.app.AppData
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.app.reminders.ReminderRequest

data class RoutineReminder(val routineId: String, val title: String, val day: LocalDate, val atMillis: Long)

object CareReminders {
    const val DAYS_AHEAD = 2

    fun upcoming(care: CareData, app: AppData, nowMillis: Long, zone: TimeZone): List<RoutineReminder> {
        if (!care.settings.reminders) return emptyList()
        val focus = ReminderPolicy.focusWindow(app.focus)
        val today = nowMillis.toLocalDateTime(zone).date
        return (0..DAYS_AHEAD).flatMap { offset ->
            val day = today.plus(DatePeriod(days = offset))
            care.routines.filter { it.time != null && it.plannedOn(day) && !it.doneOn(day) }.map { r -> reminder(r, day, app.settings.quietHours, zone, focus) }
        }.filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    private fun reminder(r: Routine, day: LocalDate, quiet: QuietHours, zone: TimeZone, focus: FocusWindow?): RoutineReminder {
        val at = LocalDateTime(day, r.time!!).toEpochMillis(zone)
        val request = ReminderRequest(ReminderModule.CARE, r.id, "routine", at, occurrence = day.toString())
        return RoutineReminder(r.id, r.title, day, ReminderPolicy.deliverAt(request, quiet, zone, focus))
    }

    fun stillWanted(care: CareData, app: AppData, routineId: String, day: LocalDate): Boolean {
        if (!care.settings.reminders) return false
        val r = care.routines.firstOrNull { it.id == routineId } ?: return false
        return r.plannedOn(day) && !r.doneOn(day)
    }
}
