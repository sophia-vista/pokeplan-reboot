package app.pokeplan.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.reminders.Channels
import app.pokeplan.reminders.notificationIcon
import app.pokeplan.reminders.notifySafely
import app.pokeplan.ui.Place

/**
 * The one notification MINT's capture posts: that a transaction the phone read is waiting to be confirmed (MINT.md 10.4).
 *
 * It never says what the alert was — no amount, no bank, no merchant — on the lock screen or anywhere else; the detail is
 * in MINT's "To confirm" box and nothing leaves the phone. One notification is kept and updated as more arrive, with
 * [setOnlyAlertOnce] so a second alert does not buzz again, and it is cancelled when the box is read or erased.
 */
internal object MintAlertNotification {
    /** One id, so the notification is updated rather than stacked. */
    const val ID = 0x4d494e54

    /** What the lock screen may say; also the title when one alert waits. */
    const val PUBLIC_TEXT = "A transaction is waiting"

    fun show(context: Context, waiting: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(Channels.MINT_ALERTS, "Mint alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A transaction read from a bank alert is waiting to be confirmed"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        val open = PendingIntent.getActivity(
            context, ID, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.MINT.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = notificationIcon(context, "amulet-coin")
        val generic = Notification.Builder(context, Channels.MINT_ALERTS).setSmallIcon(icon).setContentTitle(PUBLIC_TEXT).build()
        val notification = Notification.Builder(context, Channels.MINT_ALERTS)
            .setSmallIcon(icon)
            .setContentTitle(if (waiting > 1) "$waiting transactions are waiting" else PUBLIC_TEXT)
            .setContentText(if (waiting > 1) "Open Mint to confirm or skip them." else "Open Mint to confirm or skip it.")
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(generic)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notifySafely(context, ID, notification)
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}
