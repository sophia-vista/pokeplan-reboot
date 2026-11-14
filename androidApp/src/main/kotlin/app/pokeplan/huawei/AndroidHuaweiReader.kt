package app.pokeplan.huawei

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import app.pokeplan.app.HuaweiHealth
import app.pokeplan.app.HuaweiSeen
import app.pokeplan.ui.HuaweiReader
import app.pokeplan.ui.HuaweiReaderState

/** The Android side of HEAL's Huawei Health probe: the two services' state, read fresh on every call, and Android's settings pages to turn them on. */
class AndroidHuaweiReader(private val activity: ComponentActivity) : HuaweiReader {
    override fun state() = HuaweiReaderState(
        installed = activity.packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) != null,
        screenReading = HuaweiCapture.screen != null,
        notificationReading = activity.packageName in NotificationManagerCompat.getEnabledListenerPackages(activity),
        walking = HuaweiCapture.screen?.walking == true,
    )

    override fun seen(): HuaweiSeen = HuaweiCapture.seen()

    override fun clear() = HuaweiCapture.clear()

    override fun walk(done: (String?) -> Unit) {
        val screen = HuaweiCapture.screen ?: return done("Turn on screen reading first") // copy-ok: result
        screen.walk(done)
    }

    override fun openScreenReadingSettings() = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun openNotificationSettings() = open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    override fun openAppInfo() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))

    private fun open(intent: Intent) { runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
