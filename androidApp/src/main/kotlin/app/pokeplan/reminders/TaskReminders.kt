package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import app.pokeplan.MainActivity
import app.pokeplan.app.upcomingReminders
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.Task
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.screens.short
import app.pokeplan.widgetStore
import app.pokeplan.widgets.WidgetArt
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Task reminders as notifications. The next [MAX_ALARMS] reminders are set as alarms; each one that fires shows the task
 * with Done and Snooze, then sets the alarms again. The app calls [reschedule] whenever the reminders change.
 */
object TaskReminders {
    private const val CHANNEL = "task-reminders"
    private const val PREFS = "task_reminders"
    private const val MAX_ALARMS = 24
    private const val SNOOZE_MILLIS = 60 * 60_000L

    const val ACTION_FIRE = "app.pokeplan.reminders.FIRE"
    const val ACTION_DONE = "app.pokeplan.reminders.DONE"
    const val ACTION_SNOOZE = "app.pokeplan.reminders.SNOOZE"
    const val EXTRA_TASK = "task"
    const val EXTRA_SNOOZED = "snoozed"

    /** Sets alarms for the next reminders and cancels the ones no longer wanted. */
    fun reschedule(context: Context, upcoming: List<DueReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = upcoming.take(MAX_ALARMS).associateBy { code(it.taskId, it.atMillis) }
        val before = prefs.getStringSet("codes", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        for (code in before) if (code !in wanted) fireIntent(context, code, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        for ((code, r) in wanted) set(context, alarms, r.atMillis, fireIntent(context, code, r.taskId, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        prefs.edit().putStringSet("codes", wanted.keys.map { it.toString() }.toSet()).apply()
    }

    /** Works the reminders out from the save and sets them again (after a reboot, a time change or a fired reminder). */
    fun rescheduleFromSave(context: Context) {
        val store = widgetStore(context)
        reschedule(context, store.data.upcomingReminders(store.nowMillis, TimeZone.currentSystemDefault()))
    }

    private fun code(taskId: String, atMillis: Long) = (taskId to atMillis).hashCode()

    private fun fireIntent(context: Context, code: Int, taskId: String?, flags: Int, snoozed: Boolean = false): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).apply {
                taskId?.let { putExtra(EXTRA_TASK, it) }
                if (snoozed) putExtra(EXTRA_SNOOZED, true)
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** On time where the phone allows exact alarms, otherwise as close as it lets us. */
    private fun set(context: Context, alarms: AlarmManager, atMillis: Long, intent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    /** Shows the task's reminder again at [atMillis] (a snooze, or the end of a focus session it fell in). */
    fun remindAgain(context: Context, taskId: String, atMillis: Long = System.currentTimeMillis() + SNOOZE_MILLIS) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        set(context, alarms, atMillis, fireIntent(context, code(taskId, -1), taskId, PendingIntent.FLAG_UPDATE_CURRENT, snoozed = true)!!)
    }

    fun notificationId(taskId: String) = taskId.hashCode()

    fun show(context: Context, task: Task, nowMillis: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH))
        val id = notificationId(task.id)
        fun action(action: String) = PendingIntent.getBroadcast(
            context, id, Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_TASK, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "PLANNER"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = WidgetArt.item(context, "poke-ball", 1)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, android.R.drawable.ic_popup_reminder)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(task.title)
            .setContentText(whenText(task, nowMillis))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Done", action(ACTION_DONE)).build())
            .addAction(Notification.Action.Builder(null, "Snooze 1 hour", action(ACTION_SNOOZE)).build())
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications are not allowed: nothing to show.
        }
    }

    private fun whenText(task: Task, nowMillis: Long): String {
        val today = nowMillis.toLocalDateTime(TimeZone.currentSystemDefault()).date
        fun clock(h: Int, m: Int) = "$h:${m.toString().padStart(2, '0')}"
        val due = task.due
        val time = task.plannedTime
        val planned = task.planned
        return when {
            planned != null && time != null -> if (planned == today) "Planned for ${clock(time.hour, time.minute)}" else "Planned for ${planned.short()} ${clock(time.hour, time.minute)}"
            due != null -> if (due.date == today) "Due today at ${clock(due.hour, due.minute)}" else "Due ${due.date.short()} at ${clock(due.hour, due.minute)}"
            planned == today -> "Planned for today"
            else -> "Planned for ${planned?.short()}"
        }
    }
}

/** A reminder going off, or Done or Snooze tapped on one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskReminders.EXTRA_TASK) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(app, intent.action, taskId, intent.getBooleanExtra(TaskReminders.EXTRA_SNOOZED, false))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?, taskId: String, snoozed: Boolean) {
        val store = widgetStore(context)
        val task = store.data.tasks.firstOrNull { it.id == taskId }
        when (action) {
            TaskReminders.ACTION_FIRE -> {
                val session = store.data.focus?.takeIf { !it.isFinished }
                if (task != null && task.isOpen && store.data.settings.reminders && (snoozed || task.reminders.isNotEmpty())) {
                    // Reminders wait for the end of a focus session.
                    if (session != null) TaskReminders.remindAgain(context, taskId, session.startedAtMillis + session.durationMillis + session.pausedMillis)
                    else TaskReminders.show(context, task, store.nowMillis)
                }
            }
            TaskReminders.ACTION_DONE -> {
                // Paid when the app next opens, as a task ticked on the widget is.
                if (task != null && task.isOpen) store.completeTask(taskId)
                context.getSystemService(android.app.NotificationManager::class.java)?.cancel(TaskReminders.notificationId(taskId))
                updateWidgets(context)
            }
            TaskReminders.ACTION_SNOOZE -> {
                TaskReminders.remindAgain(context, taskId)
                context.getSystemService(android.app.NotificationManager::class.java)?.cancel(TaskReminders.notificationId(taskId))
            }
        }
        TaskReminders.rescheduleFromSave(context)
    }
}

/** After a restart, an app update or a clock or time zone change, alarms are set again from the save. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TaskReminders.rescheduleFromSave(app)
            } finally {
                pending.finish()
            }
        }
    }
}
