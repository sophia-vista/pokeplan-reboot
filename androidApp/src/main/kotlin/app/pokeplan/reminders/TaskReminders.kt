package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.Nudges
import app.pokeplan.app.upcomingReminders
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.Snooze
import app.pokeplan.plan.Task
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.Place
import app.pokeplan.ui.screens.clock
import app.pokeplan.ui.screens.short
import app.pokeplan.widgetStore
import app.pokeplan.widgets.updateWidgets
import kotlinx.datetime.TimeZone

/**
 * Task reminders as notifications. The next [Alarms.MAX_ALARMS] reminders are set as alarms; each one that fires shows the task
 * with Done and Snooze, then sets the alarms again. The app calls [reschedule] whenever the reminders change.
 * A reminder nobody answers repeats up to [Nudges.REMINDER_REPEATS] times, [Nudges.REMINDER_REPEAT_MILLIS] apart: the
 * chain ends as soon as the notification is gone (done, snoozed, tapped or swiped away).
 */
object TaskReminders {
    private const val SNOOZE_MILLIS = 60 * 60_000L
    private const val REPEAT = -2L

    const val ACTION_FIRE = "app.pokeplan.reminders.FIRE"
    const val ACTION_DONE = "app.pokeplan.reminders.DONE"
    const val ACTION_SNOOZE = "app.pokeplan.reminders.SNOOZE"
    const val ACTION_SNOOZE_TEN = "app.pokeplan.reminders.SNOOZE_TEN"
    const val ACTION_TOMORROW = "app.pokeplan.reminders.TOMORROW"
    const val EXTRA_TASK = "task"
    const val EXTRA_SNOOZED = "snoozed"
    const val EXTRA_REPEAT = "repeat"

    /** Sets alarms for the next reminders and cancels the ones no longer wanted. */
    fun reschedule(context: Context, upcoming: List<DueReminder>) {
        Alarms.reschedule(context, TASK_REMINDERS_PREFS, upcoming, { code(it.taskId, it.atMillis) }, { it.atMillis }) { code, r, flags ->
            fireIntent(context, code, r?.taskId, flags)
        }
    }

    /** Works the reminders out from the save and sets them again (after a reboot, a time change or a fired reminder). */
    fun rescheduleFromSave(context: Context) {
        val store = widgetStore(context)
        reschedule(context, store.data.upcomingReminders(store.nowMillis, TimeZone.currentSystemDefault()))
    }

    private fun code(taskId: String, atMillis: Long) = (taskId to atMillis).hashCode()

    private fun fireIntent(context: Context, code: Int, taskId: String?, flags: Int, snoozed: Boolean = false, repeat: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).apply {
                taskId?.let { putExtra(EXTRA_TASK, it) }
                if (snoozed) putExtra(EXTRA_SNOOZED, true)
                if (repeat > 0) putExtra(EXTRA_REPEAT, repeat)
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Shows the task's reminder again at [atMillis] (a snooze, or the end of a focus session it fell in). */
    fun remindAgain(context: Context, taskId: String, atMillis: Long = System.currentTimeMillis() + SNOOZE_MILLIS) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        Alarms.set(context, alarms, atMillis, fireIntent(context, code(taskId, -1), taskId, PendingIntent.FLAG_UPDATE_CURRENT, snoozed = true)!!)
    }

    /** Shows the unanswered reminder again in [Nudges.REMINDER_REPEAT_MILLIS]; [count] is which repeat that will be (1 to 3). */
    fun scheduleRepeat(context: Context, taskId: String, count: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        Alarms.set(context, alarms, System.currentTimeMillis() + Nudges.REMINDER_REPEAT_MILLIS, fireIntent(context, code(taskId, REPEAT), taskId, PendingIntent.FLAG_UPDATE_CURRENT, repeat = count)!!)
    }

    /** The reminder was answered: no more repeats. */
    fun cancelRepeat(context: Context, taskId: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        fireIntent(context, code(taskId, REPEAT), null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }

    /** True while the task's reminder is still in the notification shade, which is how a repeat knows nobody answered it. */
    fun isShowing(context: Context, taskId: String): Boolean =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty().any { it.id == notificationId(taskId) }

    fun notificationId(taskId: String) = taskId.hashCode()

    fun show(context: Context, task: Task, nowMillis: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.TASK_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH))
        val id = notificationId(task.id)
        fun action(action: String) = PendingIntent.getBroadcast(
            context, id, Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_TASK, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.PLANNER.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Channels.TASK_REMINDERS)
            .setSmallIcon(notificationIcon(context, "poke-ball"))
            .setContentTitle(task.title)
            .setContentText(whenText(task, nowMillis))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Done", action(ACTION_DONE)).build())
            // Android shows three actions at most; "Tomorrow" is still handled (see ReminderReceiver) for older notifications.
            .addAction(Notification.Action.Builder(null, "10 min", action(ACTION_SNOOZE_TEN)).build())
            .addAction(Notification.Action.Builder(null, "1 hour", action(ACTION_SNOOZE)).build())
            .build()
        notifySafely(context, id, notification)
    }

    private fun whenText(task: Task, nowMillis: Long): String {
        val today = nowMillis.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val due = task.due
        val time = task.plannedTime
        val planned = task.planned
        return when {
            planned != null && time != null -> if (planned == today) "Planned for ${clock(time)}" else "Planned for ${planned.short()} ${clock(time)}"
            due != null -> if (due.date == today) "Due today at ${clock(due.time)}" else "Due ${due.date.short()} at ${clock(due.time)}"
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
        doAsync { handle(app, intent.action, taskId, intent.getBooleanExtra(TaskReminders.EXTRA_SNOOZED, false), intent.getIntExtra(TaskReminders.EXTRA_REPEAT, 0)) }
    }

    private suspend fun handle(context: Context, action: String?, taskId: String, snoozed: Boolean, repeat: Int) {
        val store = widgetStore(context)
        val task = store.data.tasks.firstOrNull { it.id == taskId }
        when (action) {
            TaskReminders.ACTION_FIRE -> {
                val session = store.data.focus?.takeIf { !it.isFinished }
                if (task != null && task.isOpen && store.data.settings.reminders && (snoozed || repeat > 0 || task.reminders.isNotEmpty())) {
                    when {
                        // Reminders wait for the end of a focus session (a repeat just stops: the reminder is still there to find).
                        session != null -> if (repeat == 0) TaskReminders.remindAgain(context, taskId, session.startedAtMillis + session.durationMillis + session.pausedMillis)
                        // A repeat only shows if the last one is still unanswered.
                        repeat > 0 && !TaskReminders.isShowing(context, taskId) -> Unit
                        else -> {
                            TaskReminders.show(context, task, store.nowMillis)
                            if (store.data.settings.repeatReminders && repeat < Nudges.REMINDER_REPEATS) TaskReminders.scheduleRepeat(context, taskId, repeat + 1)
                        }
                    }
                }
            }
            TaskReminders.ACTION_DONE -> {
                TaskReminders.cancelRepeat(context, taskId)
                // Paid when the app next opens, as a task ticked on the widget is.
                if (task != null && task.isOpen) store.completeTask(taskId)
                context.getSystemService(android.app.NotificationManager::class.java)?.cancel(TaskReminders.notificationId(taskId))
                updateWidgets(context)
            }
            TaskReminders.ACTION_SNOOZE, TaskReminders.ACTION_SNOOZE_TEN, TaskReminders.ACTION_TOMORROW -> {
                TaskReminders.cancelRepeat(context, taskId)
                val zone = TimeZone.currentSystemDefault()
                val snooze = when (action) {
                    TaskReminders.ACTION_SNOOZE_TEN -> Snooze.TEN_MINUTES
                    TaskReminders.ACTION_TOMORROW -> Snooze.TOMORROW
                    else -> Snooze.ONE_HOUR
                }
                TaskReminders.remindAgain(context, taskId, Reminders.snoozedTo(store.now, snooze, zone).toEpochMillis(zone))
                context.getSystemService(android.app.NotificationManager::class.java)?.cancel(TaskReminders.notificationId(taskId))
            }
        }
        TaskReminders.rescheduleFromSave(context)
        AppAlerts.rescheduleFromSave(context)
    }
}

/** After a restart, an app update or a clock or time zone change, alarms are set again from the save. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        doAsync {
            TaskReminders.rescheduleFromSave(app)
            AppAlerts.rescheduleFromSave(app)
            RoutineReminders.rescheduleFromSave(app)
        }
    }
}
