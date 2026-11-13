package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.HomeReminderKind
import app.pokeplan.app.ReminderActions
import app.pokeplan.app.ReminderRequest
import app.pokeplan.ui.Place

/**
 * How a Home reminder looks as a notification (the rules are in [app.pokeplan.app.HomeReminders]; HOME.md section 6). The lock screen shows the same words for
 * every record whatever the user allowed (the kind only: "A chore is due"); the full text, which names the chore, supply or job only when the user said so, shows
 * after unlocking. There is never a quantity, a serial number or a note. It opens Home. A chore's notification has "Done" (the chore's own completion) and every kind
 * has "Tomorrow"; a reminder never marks maintenance done, restocks a supply or changes a warranty.
 */
object UpkeepReminders {
    fun notificationId(ref: String, kind: String) = ("home-$kind" to ref).hashCode()

    fun show(context: Context, request: ReminderRequest, title: String) {
        val kind = HomeReminderKind.of(request.kind) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.HOME_UPKEEP, "Nest reminders", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val id = notificationId(request.sourceRef, request.kind)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.HOUSEHOLD.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val generic = Notification.Builder(context, Channels.HOME_UPKEEP).setSmallIcon(notificationIcon(context, "chipped-pot")).setContentTitle(kind.publicText).build()
        val builder = Notification.Builder(context, Channels.HOME_UPKEEP)
            .setSmallIcon(notificationIcon(context, "chipped-pot"))
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(generic)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (kind == HomeReminderKind.CHORE) builder.addAction(Notification.Action.Builder(null, "Done", SharedReminders.actionIntent(context, request, ReminderActions.DONE)).build())
        builder.addAction(Notification.Action.Builder(null, "Tomorrow", SharedReminders.actionIntent(context, request, ReminderActions.TOMORROW)).build())
        notifySafely(context, id, builder.build())
    }
}
