package app.pokeplan.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.pokeplan.mint.CaptureChannel
import app.pokeplan.mint.CaptureSource

/**
 * Reads the alerts of the banks on MINT's list, and nothing else (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3).
 *
 * The component is declared off and is enabled only while the player has bank notifications switched on, so while it is
 * off Android never binds this service. Android grants notification access to an app, not to one listener, and every
 * notification reaches it: each is dropped on its package name here, before any field is read, and only a package on
 * [CaptureSource.packages] goes on to [AlertReader]. Nothing from another app is read, kept, counted or logged.
 */
class MintNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val source = CaptureSource.ofPackage(pkg) ?: return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        fun field(key: String) = extras.getCharSequence(key)?.toString()?.trim().orEmpty()
        val title = field(Notification.EXTRA_TITLE)
        val text = field(Notification.EXTRA_TEXT).ifBlank { field(Notification.EXTRA_BIG_TEXT) }
        if (title.isBlank() && text.isBlank()) return
        AlertReader.read(this, source, CaptureChannel.NOTIFICATION, title, text, sbn.postTime)
    }
}
