package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.ReminderActions
import app.pokeplan.app.ReminderRequest
import app.pokeplan.ui.Place

/**
 * How a Heal hydration reminder looks as a notification (the rules are in [app.pokeplan.app.HealReminders]). It says the same thing every time: no drink, no amount, no
 * target, nothing about the person's health, and the full text is also its lock-screen text, so there is nothing more private to hide. One button: "In 30 min". There is no
 * "done": a reminder is not a record and pays nothing. Scheduling and the button's routing are in the shared reminder feature ([SharedReminders]).
 */
object HydrationReminders {
    fun notificationId(ref: String) = ("hydration" to ref).hashCode()

    fun show(context: Context, ref: String, title: String, request: ReminderRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.HEAL_HYDRATION, "Heal reminders", NotificationManager.IMPORTANCE_LOW))
        val id = notificationId(ref)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.HEAL.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Channels.HEAL_HYDRATION)
            .setSmallIcon(notificationIcon(context, "fresh-water"))
            .setContentTitle(title)
            .setContentText("Only if you would like one.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "In 30 min", SharedReminders.actionIntent(context, request, ReminderActions.SNOOZE_30)).build())
            .build()
        notifySafely(context, id, notification)
    }
}
