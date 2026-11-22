package app.pokeplan.platform

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import app.pokeplan.ui.PhoneState

class AndroidPhoneState(private val context: Context) : PhoneState {
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    override val screenOff: Boolean
        get() = power?.isInteractive == false || keyguard?.isKeyguardLocked == true

    override val inCall: Boolean
        get() = audio?.mode in CALL_MODES

    override val powerSaving: Boolean
        get() = power?.isPowerSaveMode == true

    override val exactAlarms: Boolean
        get() = app.pokeplan.reminders.Alarms.canSetExact(context)

    override fun allowExactAlarms() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 31) android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))
        else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {  }
    }

    override val notificationsAllowed: Boolean
        get() = context.getSystemService(android.app.NotificationManager::class.java)?.areNotificationsEnabled() != false

    override fun openNotificationSettings() = open(
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    override val canContact: Boolean = true

    override fun call(number: String) = open(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number.trim(), null)))
    override fun message(number: String) = open(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", number.trim(), null)))
    override fun email(address: String) = open(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("mailto", address.trim(), null)))

    override val canSetAlarm: Boolean
        get() = context.packageManager.queryIntentActivities(android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM), 0).isNotEmpty()

    override fun setAlarm(hour: Int, minute: Int, label: String) = open(
        android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
            .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false),
    )

    private fun open(intent: android.content.Intent) {
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {  }
    }

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING)
    }
}
