package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.ui.Place
import kotlinx.datetime.LocalDate

object RoutineReminders {
    fun notificationId(routineId: String) = ("routine" to routineId).hashCode()

    fun show(context: Context, routineId: String, title: String, day: LocalDate, request: ReminderRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.CARE_ROUTINES, "Care routines", NotificationManager.IMPORTANCE_DEFAULT).withGameSound(context))
        val id = notificationId(routineId)
        fun action(key: String) = SharedReminders.actionIntent(context, request, key)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.CARE.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Channels.CARE_ROUTINES)
            .setSmallIcon(notificationIcon(context, "oaks-parcel"))
            .setContentTitle(title)
            .setContentText("A small thing for you, whenever you're ready.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Done", action(ReminderActions.DONE)).build())
            .addAction(Notification.Action.Builder(null, "In 30 min", action(ReminderActions.SNOOZE_30)).build())
            .build()
        notifySafely(context, id, notification)
    }
}

