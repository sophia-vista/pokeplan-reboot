package app.pokeplan.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.base.BaseReminderKind
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.ui.Place

object UpkeepReminders {
    fun notificationId(ref: String, kind: String) = ("home-$kind" to ref).hashCode()

    fun show(context: Context, request: ReminderRequest, title: String) {
        val kind = BaseReminderKind.of(request.kind) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(Channels.HOME_UPKEEP, "Base reminders", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val id = notificationId(request.sourceRef, request.kind)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.BASE.name),
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
        if (kind == BaseReminderKind.CHORE) builder.addAction(Notification.Action.Builder(null, "Done", SharedReminders.actionIntent(context, request, ReminderActions.DONE)).build())
        builder.addAction(Notification.Action.Builder(null, "Tomorrow", SharedReminders.actionIntent(context, request, ReminderActions.TOMORROW)).build())
        notifySafely(context, id, builder.build())
    }
}
