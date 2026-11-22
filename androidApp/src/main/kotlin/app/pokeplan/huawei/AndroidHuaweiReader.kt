package app.pokeplan.huawei

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import app.pokeplan.app.heal.HuaweiHealth
import app.pokeplan.app.heal.HuaweiSeen
import app.pokeplan.ui.HuaweiReader
import app.pokeplan.ui.HuaweiReaderState

class AndroidHuaweiReader(private val activity: ComponentActivity) : HuaweiReader {
    override fun state() = HuaweiReaderState(
        installed = activity.packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) != null,
        screenReading = HuaweiCapture.screen != null,
        notificationReading = activity.packageName in NotificationManagerCompat.getEnabledListenerPackages(activity),
        walking = HuaweiCapture.screen?.walking == true,
        canSwitchItself = canSwitch(),
    )

    private fun canSwitch() = activity.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun switchOn(): Boolean = runCatching {
        val me = ComponentName(activity, HuaweiScreenService::class.java).flattenToString()
        val cr = activity.contentResolver
        val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (enabled.split(':').none { it.equals(me, ignoreCase = true) }) {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (enabled.isBlank()) me else "$enabled:$me")
        }
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)

    override fun turnOff() { HuaweiCapture.screen?.turnOff() }

    override fun seen(): HuaweiSeen = HuaweiCapture.seen()

    override fun clear() = HuaweiCapture.clear()

    override fun walk(known: app.pokeplan.app.heal.HuaweiWalk.Known, done: (String?) -> Unit, scan: app.pokeplan.app.heal.HuaweiWalk.Scan) {
        HuaweiCapture.screen?.let { return it.walk(known, done, scan) }
        if (!canSwitch() || !switchOn()) return done("Turn on screen reading first") // copy-ok: result
        // Android binds the service a moment after the setting changes: wait for it (up to about 3 seconds), then walk; the walk turns it off again.
        val main = Handler(Looper.getMainLooper())
        var tries = 0
        fun waitForIt() {
            val screen = HuaweiCapture.screen
            when {
                screen != null -> screen.walk(known, done, scan)
                ++tries > 30 -> done("Screen reading did not start") // copy-ok: result
                else -> main.postDelayed(::waitForIt, 100)
            }
        }
        waitForIt()
    }

    override fun openScreenReadingSettings() { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    override fun openNotificationSettings() { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

    fun sync() { HuaweiComponents.rebind(activity) }

    override fun openAppInfo() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))

    private fun open(intent: Intent) { runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
