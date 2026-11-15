package app.pokeplan.huawei

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.pokeplan.app.heal.HuaweiHealth
import app.pokeplan.app.heal.NotificationCapture
/**
 * Reads Huawei Health's own notification, for its step count (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Android cannot narrow a notification listener to one app,
 * so every notification from any other package is dropped here on arrival, before a single field is read. Nothing is logged; what is kept stays in memory.
 */
class HuaweiNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach { take(it) }
        // Steps only count up through the day, so the latest notification is all the app needs: let go now and stay out of the resident list (REP-055).
        // The app asks Android to bind again when it comes to the front (HuaweiComponents.sync).
        runCatching { requestUnbind() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = take(sbn)

    private fun take(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != HuaweiHealth.PACKAGE) return
        val n = sbn.notification ?: return
        val extras = n.extras
        fun field(key: String) = extras?.getCharSequence(key)?.toString()?.trim()?.take(300).orEmpty()
        val title = field(Notification.EXTRA_TITLE)
        val text = field(Notification.EXTRA_TEXT)
        val sub = field(Notification.EXTRA_SUB_TEXT)
        val big = field(Notification.EXTRA_BIG_TEXT)
        @Suppress("DEPRECATION") val custom = listOf(title, text, sub, big).all { it.isEmpty() } && (n.contentView != null || n.bigContentView != null)
        if (listOf(title, text, sub, big).all { it.isEmpty() } && !custom) return
        HuaweiCapture.addNotification(NotificationCapture(sbn.postTime, title, text, sub, big, custom))
    }
}
