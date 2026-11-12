package app.pokeplan.app

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

/** A routine's gentle reminder: the routine, the day it is for, and when it goes off. */
data class RoutineReminder(val routineId: String, val title: String, val day: LocalDate, val atMillis: Long)

/**
 * Care's routine reminders (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md): one at each routine's own time, on the days it is meant for, while
 * it is not done yet that day. Gentle: no repeats, no streak talk, nothing when Care or its reminders are off. The user chose
 * the time, so rest days (Plan's) do not move it. Delivery follows the shared [ReminderPolicy]: the global quiet hours
 * (INTEGRATIONS_AND_PRIVACY.md, section 9.4; no module may bypass them) and a focus session, which holds it until its end.
 */
object CareReminders {
    /** How far ahead alarms are set; the app sets them again on every change and each fired reminder. */
    const val DAYS_AHEAD = 2

    /** The reminders still to come after [nowMillis], earliest first. */
    fun upcoming(care: CareData, app: AppData, nowMillis: Long, zone: TimeZone): List<RoutineReminder> {
        if (!app.settings.careModule || !care.settings.reminders) return emptyList()
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        val today = nowMillis.toLocalDateTime(zone).date
        return (0..DAYS_AHEAD).flatMap { offset ->
            val day = today.plus(DatePeriod(days = offset))
            care.routines.filter { it.time != null && it.isDueOn(day) && !it.doneOn(day) }.map { r -> reminder(r, day, app.settings.quietHours, zone, focus) }
        }.filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    private fun reminder(r: Routine, day: LocalDate, quiet: QuietHours, zone: TimeZone, focus: FocusWindow?): RoutineReminder {
        val at = LocalDateTime(day, r.time!!).toEpochMillis(zone)
        val request = ReminderRequest(ReminderModule.CARE, r.id, "routine", at, occurrence = day.toString())
        return RoutineReminder(r.id, r.title, day, ReminderPolicy.deliverAt(request, quiet, zone, focus))
    }

    /** Whether the reminder for [routineId] on [day] should still show: Care and reminders on, the routine due and not done. */
    fun stillWanted(care: CareData, app: AppData, routineId: String, day: LocalDate): Boolean {
        if (!app.settings.careModule || !care.settings.reminders) return false
        val r = care.routines.firstOrNull { it.id == routineId } ?: return false
        return r.isDueOn(day) && !r.doneOn(day)
    }
}
