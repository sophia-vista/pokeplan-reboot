package app.pokeplan.app

import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** A focus alert to set for a running session. */
data class FocusAlert(val kind: Kind, val atMillis: Long) {
    enum class Kind { HALFWAY, END }
}

/** The title and text of a notification. */
data class NudgeText(val title: String, val body: String)

/**
 * The pure rules behind the app's own notifications beyond task reminders (docs/design.md, "Notifications"): when the
 * evening streak reminder and the Sunday recap are due, whether the streak is at risk, what the recap says, and which
 * alerts a focus session needs. The Android side only sets alarms at these times and checks again when one goes off.
 */
object Nudges {
    /** The weekly recap goes out on Sunday at this time. *(tune)* */
    val RECAP_TIME = LocalTime(18, 0)
    val RECAP_DAY = DayOfWeek.SUNDAY

    /** Repeats of an unanswered task reminder, and the gap between them. *(tune)* */
    const val REMINDER_REPEATS = 3
    const val REMINDER_REPEAT_MILLIS = 10 * 60_000L

    /** The next moment strictly after [nowMillis] when the local clock reads [time] (on [day] when given). */
    fun nextAt(nowMillis: Long, zone: TimeZone, time: LocalTime, day: DayOfWeek? = null): Long {
        val today = nowMillis.toLocalDateTime(zone).date
        for (offset in 0..8) {
            val date = today.plus(DatePeriod(days = offset))
            if (day != null && date.dayOfWeek != day) continue
            val at = LocalDateTime(date, time).toEpochMillis(zone)
            if (at > nowMillis) return at
        }
        error("no moment found") // unreachable: nine days always hold the day
    }

    /** When the streak reminder should be checked next; null when it is switched off. The check itself is [streakAtRisk]. */
    fun nextStreakCheck(data: AppData, nowMillis: Long, zone: TimeZone): Long? =
        if (data.settings.streakReminder) nextAt(nowMillis, zone, LocalTime(data.settings.streakReminderHour, 0)) else null

    /** When the weekly recap goes out next; null when it is switched off. */
    fun nextRecap(data: AppData, nowMillis: Long, zone: TimeZone): Long? =
        if (data.settings.weeklyRecap) nextAt(nowMillis, zone, RECAP_TIME, RECAP_DAY) else null

    /**
     * True when the reminder should show at [nowMillis]: it is switched on, a streak is running, nothing has counted today
     * (a paid task or a finished session), today is not a rest day, and the hour is not in quiet hours.
     */
    fun streakAtRisk(data: AppData, nowMillis: Long, zone: TimeZone): Boolean {
        if (!data.settings.streakReminder) return false
        val now = nowMillis.toLocalDateTime(zone)
        val day = Streaks.streakDay(now)
        if (day in data.activeDays || day in data.restDays) return false
        if (data.settings.quietHours.contains(now.time)) return false
        return data.streakAt(now).current > 0
    }

    /** The streak reminder's text: an invitation, never a scolding. */
    fun streakText(data: AppData, nowMillis: Long, zone: TimeZone): NudgeText {
        val days = data.streakAt(nowMillis.toLocalDateTime(zone)).current
        return NudgeText("Your $days-day streak is waiting", "One task or one focus session today keeps it going.")
    }

    /**
     * The week behind [nowMillis] (the last seven days, today included): tasks finished, focus minutes and the category with
     * the most finished tasks. Null when it is switched off, it is quiet hours, or the week was empty (no scolding).
     */
    fun weeklyRecap(data: AppData, nowMillis: Long, zone: TimeZone): NudgeText? {
        if (!data.settings.weeklyRecap) return null
        val now = nowMillis.toLocalDateTime(zone)
        if (data.settings.quietHours.contains(now.time)) return null
        val start: LocalDate = now.date.plus(DatePeriod(days = -6))
        val s = Reviews.summary(start, data.tasks, data.focusHistory, data.series.mapNotNull { it.asHabit() }, zone)
        if (s.tasksDone == 0 && s.focusMinutes == 0) return null
        val top = s.categorySplit.entries.sortedWith(compareBy({ -it.value }, { it.key.ordinal })).firstOrNull()?.key
        val parts = buildList {
            add("${s.tasksDone} ${if (s.tasksDone == 1) "task" else "tasks"} finished")
            add(focusText(s.focusMinutes))
            if (top != null) add("most in ${top.label}")
        }
        return NudgeText("Your week in PokéLife", parts.joinToString(", ") + ".")
    }

    private fun focusText(minutes: Int): String = when {
        minutes < 60 -> "$minutes min of focus"
        minutes % 60 == 0 -> "${minutes / 60} h of focus"
        else -> "${minutes / 60} h ${minutes % 60} min of focus"
    }

    /**
     * The alerts to have set for [session] at [nowMillis]: a halfway alert for sessions of 15 minutes or more (when
     * switched on) and the end alert, both with pauses added back. None while the clock is paused (they are set again
     * when it runs), once the session is over, or for a moment already past.
     */
    fun focusAlerts(session: FocusSession?, settings: Settings, nowMillis: Long): List<FocusAlert> {
        if (session == null || session.isFinished || session.phase is Phase.Paused) return emptyList()
        val alerts = buildList {
            if (settings.focusHalfway) session.halfwayAtMillis?.let { add(FocusAlert(FocusAlert.Kind.HALFWAY, it + session.pausedMillis)) }
            if (settings.focusEnd) add(FocusAlert(FocusAlert.Kind.END, session.startedAtMillis + session.pausedMillis + session.durationMillis))
        }
        return alerts.filter { it.atMillis > nowMillis }
    }
}
