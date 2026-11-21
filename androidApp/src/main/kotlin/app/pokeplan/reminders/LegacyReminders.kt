package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderRequest
internal object LegacyReminders {
    private const val MARKER_PREFS = "reminder_migration"
    private const val VERSION = 1

    fun cancelOnce(context: Context) {
        val marker = context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
        if (marker.getInt("version", 0) >= VERSION) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        cancelAll(context, alarms, TASK_REMINDERS_PREFS, ReminderReceiver::class.java, "app.pokeplan.reminders.FIRE")
        cancelAll(context, alarms, "routine_reminders", RoutineReminderReceiver::class.java, "app.pokeplan.reminders.ROUTINE_FIRE")
        marker.edit().putInt("version", VERSION).apply()
    }

    private fun cancelAll(context: Context, alarms: AlarmManager?, prefsName: String, receiver: Class<*>, action: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        for (code in prefs.getStringSet("codes", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }) {
            val pending = PendingIntent.getBroadcast(context, code, Intent(context, receiver).setAction(action), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) { alarms?.cancel(pending); pending.cancel() }
        }
        prefs.edit().remove("codes").apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val taskId = intent.getStringExtra("task")
        val key = when (intent.action) {
            "app.pokeplan.reminders.DONE" -> ReminderActions.DONE
            "app.pokeplan.reminders.SNOOZE" -> ReminderActions.SNOOZE_60
            "app.pokeplan.reminders.SNOOZE_TEN" -> ReminderActions.SNOOZE_10
            "app.pokeplan.reminders.TOMORROW" -> ReminderActions.TOMORROW
            else -> null
        }
        doAsync {
            if (taskId != null && key != null) SharedReminders.act(app, ReminderRequest(ReminderModule.PLAN, taskId, "task", 0), key)
            else SharedReminders.reschedule(app)
        }
    }
}

class RoutineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val routineId = intent.getStringExtra("routine")
        val epochDay = intent.getLongExtra("day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.toInt()
        val key = when (intent.action) {
            "app.pokeplan.reminders.ROUTINE_DONE" -> ReminderActions.DONE
            "app.pokeplan.reminders.ROUTINE_LATER" -> ReminderActions.SNOOZE_30
            else -> null
        }
        doAsync {
            if (routineId != null && epochDay != null && key != null) {
                val day = kotlinx.datetime.LocalDate.fromEpochDays(epochDay).toString()
                SharedReminders.act(app, ReminderRequest(ReminderModule.CARE, routineId, "routine", 0, occurrence = day), key)
            } else SharedReminders.reschedule(app)
        }
    }
}
