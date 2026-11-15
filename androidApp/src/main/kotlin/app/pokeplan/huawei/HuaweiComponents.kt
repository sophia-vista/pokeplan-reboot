package app.pokeplan.huawei

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Asks Android to bind the step-notification listener again. The listener lets go after each read (see [HuaweiNotificationService]), so it is rebound when the
 * app or the launcher comes to the front. Both reader services stay enabled components: Android binds a listener or accessibility service only while the
 * player has switched it on in its settings, so disabling the components would save nothing and could lose access already granted.
 */
internal object HuaweiComponents {
    /** Rebinds the notification listener, only when notification access is granted to this app. Returns whether a rebind was requested. */
    fun rebind(context: Context): Boolean {
        if (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)) return false
        return runCatching { NotificationListenerService.requestRebind(ComponentName(context, HuaweiNotificationService::class.java)) }.isSuccess
    }
}
