package app.pokeplan.huawei

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Keeps the two Huawei Health reader services out of Android's resident list while the reader is off (REP-055). Both are declared disabled in the manifest;
 * a component is enabled only while the player has switched it on in Android's settings, or has just been sent there to do so (the entry must exist to be
 * switched), and disabled again at the next look once it is not switched on. Disabling with DONT_KILL_APP never restarts the app.
 */
internal object HuaweiComponents {
    private val screen = HuaweiScreenService::class.java
    private val notification = HuaweiNotificationService::class.java

    /** Applies the reader's state: call on app start and after any change. [canSwitch] keeps screen reading available when adb granted WRITE_SECURE_SETTINGS. */
    fun sync(context: Context, canSwitch: Boolean = false) {
        set(context, screen, screenListed(context) || canSwitch)
        val listed = notificationListed(context)
        set(context, notification, listed)
        // The listener is unbound after it has read the notifications (see the service); ask Android to bind it once so the latest step count is read again.
        if (listed) runCatching { NotificationListenerService.requestRebind(ComponentName(context, notification)) }
    }

    /** Makes the screen reader appear in Accessibility settings (or bindable by [AndroidHuaweiReader.walk]). */
    fun enableScreen(context: Context) = set(context, screen, true)

    /** Makes the notification listener appear in Notification access settings. */
    fun enableNotification(context: Context) = set(context, notification, true)

    fun screenEnabled(context: Context) = enabled(context, screen)
    fun notificationEnabled(context: Context) = enabled(context, notification)

    private fun screenListed(context: Context): Boolean {
        val me = ComponentName(context, screen).flattenToString()
        val list = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty()
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun notificationListed(context: Context) = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun enabled(context: Context, cls: Class<*>) =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, cls)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun set(context: Context, cls: Class<*>, on: Boolean) {
        if (enabled(context, cls) == on) return
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching { context.packageManager.setComponentEnabledSetting(ComponentName(context, cls), state, PackageManager.DONT_KILL_APP) }
    }
}
