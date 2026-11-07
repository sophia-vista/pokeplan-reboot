package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.MintReminders
import app.pokeplan.app.ReminderActions
import app.pokeplan.app.ReminderRequest
import app.pokeplan.ui.Place

/**
 * How a Mint bill reminder looks as a notification (the rules are in [app.pokeplan.app.MintReminders]; MINT.md section 12). The lock screen shows the same words for every
 * bill whatever the user allowed ([MintReminders.PUBLIC_TEXT]); the full text, which names the bill only when the user said so, shows after unlocking. There is never an
 * amount, a balance, an account or a merchant. The notification opens Mint and has one button, "Tomorrow"; there is no "Paid", because a reminder never settles a bill.
 */
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
