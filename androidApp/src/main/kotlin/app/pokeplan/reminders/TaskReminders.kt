package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.plan.Task
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.Place
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.screens.short
import kotlinx.datetime.TimeZone

/**
 * How a Plan task reminder looks as a notification. Scheduling, snoozes, repeats and the buttons' routing are in the shared
 * reminder feature ([SharedReminders]); this only builds the notification.
 */
object TaskReminders {
    /** True while the task's reminder is still in the notification shade, which is how a repeat knows nobody answered it. */
    fun isShowing(context: Context, taskId: String): Boolean =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty().any { it.id == notificationId(taskId) }

    fun notificationId(taskId: String) = taskId.hashCode()

    fun show(context: Context, task: Task, nowMillis: Long, request: ReminderRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.TASK_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH).withGameSound(context))
        val id = notificationId(task.id)
        fun action(key: String) = SharedReminders.actionIntent(context, request, key)
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
            .addAction(Notification.Action.Builder(null, "Done", action(ReminderActions.DONE)).build())
            // Android shows three actions at most; "Tomorrow" is still handled (see ReminderReceiver) for older notifications.
            .addAction(Notification.Action.Builder(null, "10 min", action(ReminderActions.SNOOZE_10)).build())
            .addAction(Notification.Action.Builder(null, "1 hour", action(ReminderActions.SNOOZE_60)).build())
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


/** After a restart, an app update or a clock or time zone change, alarms are set again from the saves. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        doAsync {
            SharedReminders.rescheduleAfterReboot(app)
            AppAlerts.rescheduleFromSave(app)
        }
    }
}
