package app.pokeplan.capture

import android.Manifest
import android.os.Build
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import app.pokeplan.mint.CaptureCandidate
import app.pokeplan.ui.mint.MessageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone side of MINT's capture (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3): the two Android permissions it needs,
 * the two components that are switched on only with it, and the sealed box the proposals wait in.
 */
class AndroidMessageCapture(private val activity: ComponentActivity) : MessageCapture {
    private var pending: ((Boolean) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }

    private var pendingAlert: ((Boolean) -> Unit)? = null
    private val alertLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingAlert?.invoke(granted)
        pendingAlert = null
    }

    override fun alertNotificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun requestAlertNotifications(done: (Boolean) -> Unit) {
        if (alertNotificationsAllowed()) return done(true)
        pendingAlert = done
        alertLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun notificationsGranted(): Boolean =
        activity.packageName in NotificationManagerCompat.getEnabledListenerPackages(activity)

    override fun openNotificationSettings() {
        open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun messagesGranted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    override fun requestMessages(done: (Boolean) -> Unit) {
        if (messagesGranted()) return done(true)
        pending = done
        launcher.launch(Manifest.permission.RECEIVE_SMS)
    }

    override fun watch(notifications: Boolean, messages: Boolean) {
        enable(MintNotificationService::class.java, notifications)
        enable(MintSmsReceiver::class.java, messages)
        if (notifications && notificationsGranted()) runCatching {
            NotificationListenerService.requestRebind(ComponentName(activity, MintNotificationService::class.java))
        }
    }

    override suspend fun take(): List<CaptureCandidate> = withContext(Dispatchers.IO) {
        val waiting = MintCaptureBox.take(activity)
        // The player is looking at the box now: the "a transaction is waiting" notification has done its job.
        if (waiting.isNotEmpty()) MintAlertNotification.cancel(activity)
        waiting
    }

    override suspend fun erase() {
        withContext(Dispatchers.IO) {
            MintCaptureBox.clear(activity)
            MintAlertNotification.cancel(activity)
        }
    }

    private fun enable(component: Class<*>, on: Boolean) {
        val name = ComponentName(activity, component)
        val pm = activity.packageManager
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(name) != wanted) pm.setComponentEnabledSetting(name, wanted, PackageManager.DONT_KILL_APP)
    }

    private fun open(intent: Intent) {
        runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
