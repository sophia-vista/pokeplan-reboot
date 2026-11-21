package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.mint.MintReminders
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.ui.Place

object BillReminders {
    fun notificationId(ref: String) = ("bill" to ref).hashCode()

    fun show(context: Context, ref: String, title: String, request: ReminderRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.MINT_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val id = notificationId(ref)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.MINT.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val generic = Notification.Builder(context, Channels.MINT_BILLS).setSmallIcon(notificationIcon(context, "amulet-coin")).setContentTitle(MintReminders.PUBLIC_TEXT).build()
        val notification = Notification.Builder(context, Channels.MINT_BILLS)
            .setSmallIcon(notificationIcon(context, "amulet-coin"))
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(generic)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Tomorrow", SharedReminders.actionIntent(context, request, ReminderActions.TOMORROW)).build())
            .build()
        notifySafely(context, id, notification)
    }
}
