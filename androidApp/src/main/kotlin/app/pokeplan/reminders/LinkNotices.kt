package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.link.LinkReminderKind
import app.pokeplan.app.link.LinkReminders
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.ui.Place

object LinkNotices {
    fun notificationId(ref: String, kind: String) = ("link-$kind" to ref).hashCode()

    fun show(context: Context, request: ReminderRequest, title: String) {
        val kind = LinkReminderKind.of(request.kind) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.LINK_NOTICES, "Link reminders", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val id = notificationId(request.sourceRef, request.kind)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.LINK.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val generic = Notification.Builder(context, Channels.LINK_NOTICES).setSmallIcon(notificationIcon(context, "friend-ball")).setContentTitle(LinkReminders.GENERIC).build()
        val builder = Notification.Builder(context, Channels.LINK_NOTICES)
            .setSmallIcon(notificationIcon(context, "friend-ball"))
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(generic)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (kind == LinkReminderKind.FOLLOW_UP) builder.addAction(Notification.Action.Builder(null, "Done", SharedReminders.actionIntent(context, request, ReminderActions.DONE)).build())
        builder.addAction(Notification.Action.Builder(null, "Tomorrow", SharedReminders.actionIntent(context, request, ReminderActions.TOMORROW)).build())
        notifySafely(context, id, builder.build())
    }
}
