package app.pokeplan.app.plan

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
import app.pokeplan.app.AppData
import app.pokeplan.app.Settings
import app.pokeplan.app.streakAt

data class FocusAlert(val kind: Kind, val atMillis: Long) {
    enum class Kind { HALFWAY, END }
}

data class NudgeText(val title: String, val body: String)

object Nudges {
    val RECAP_TIME = LocalTime(18, 0)
    val RECAP_DAY = DayOfWeek.SUNDAY

    const val REMINDER_REPEATS = 3
    const val REMINDER_REPEAT_MILLIS = 10 * 60_000L

    fun nextAt(nowMillis: Long, zone: TimeZone, time: LocalTime, day: DayOfWeek? = null): Long {
        val today = nowMillis.toLocalDateTime(zone).date
        for (offset in 0..8) {
            val date = today.plus(DatePeriod(days = offset))
            if (day != null && date.dayOfWeek != day) continue
            val at = LocalDateTime(date, time).toEpochMillis(zone)
            if (at > nowMillis) return at
        }
        error("no moment found")
    }

    fun nextStreakCheck(data: AppData, nowMillis: Long, zone: TimeZone): Long? =
        if (data.settings.streakReminder) nextAt(nowMillis, zone, LocalTime(data.settings.streakReminderHour, 0)) else null

    fun nextRecap(data: AppData, nowMillis: Long, zone: TimeZone): Long? =
        if (data.settings.weeklyRecap) nextAt(nowMillis, zone, RECAP_TIME, RECAP_DAY) else null

    fun streakAtRisk(data: AppData, nowMillis: Long, zone: TimeZone, quietApplies: Boolean = true): Boolean {
        if (!data.settings.streakReminder) return false
        val now = nowMillis.toLocalDateTime(zone)
        val day = Streaks.streakDay(now)
        if (day in data.activeDays || day in data.restDays) return false
        if (quietApplies && data.settings.quietHours.contains(now.time)) return false
        return data.streakAt(now).current > 0
    }

    fun streakText(data: AppData, nowMillis: Long, zone: TimeZone): NudgeText {
        val days = data.streakAt(nowMillis.toLocalDateTime(zone)).current
        return NudgeText("Your $days-day streak is waiting", "One task or one focus session today keeps it going.")
    }

    fun weeklyRecap(data: AppData, nowMillis: Long, zone: TimeZone, quietApplies: Boolean = true): NudgeText? {
        if (!data.settings.weeklyRecap) return null
        val now = nowMillis.toLocalDateTime(zone)
        if (quietApplies && data.settings.quietHours.contains(now.time)) return null
        val start: LocalDate = now.date.plus(DatePeriod(days = -6))
        val s = Reviews.summary(start, data.tasks, data.focusHistory, data.series.mapNotNull { it.asHabit() }, zone)
        if (s.tasksDone == 0 && s.focusMinutes == 0) return null
        val top = s.listSplit.entries.filter { it.key != null }.sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull()?.key
            ?.let { id -> data.lists.firstOrNull { it.id == id }?.name }
        val parts = buildList {
            add("${s.tasksDone} ${if (s.tasksDone == 1) "task" else "tasks"} finished")
            add(focusText(s.focusMinutes))
            if (top != null) add("most in $top")
        }
        return NudgeText("Your week in PokéLife", parts.joinToString(", ") + ".")
    }

    private fun focusText(minutes: Int): String = when {
        minutes < 60 -> "$minutes min of focus"
        minutes % 60 == 0 -> "${minutes / 60} h of focus"
        else -> "${minutes / 60} h ${minutes % 60} min of focus"
    }

    fun focusAlerts(session: FocusSession?, settings: Settings, nowMillis: Long): List<FocusAlert> {
        if (session == null || session.isFinished || session.phase is Phase.Paused) return emptyList()
        val alerts = buildList {
            if (settings.focusHalfway) session.halfwayAtMillis?.let { add(FocusAlert(FocusAlert.Kind.HALFWAY, it + session.pausedMillis)) }
            if (settings.focusEnd) add(FocusAlert(FocusAlert.Kind.END, session.startedAtMillis + session.pausedMillis + session.durationMillis))
        }
        return alerts.filter { it.atMillis > nowMillis }
    }
}
