package app.pokeplan.platform

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import app.pokeplan.ui.PhoneState

/** Reads the screen and call state when the app goes to the background; needs no permission. */
class AndroidPhoneState(private val context: Context) : PhoneState {
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    override val screenOff: Boolean
        get() = power?.isInteractive == false || keyguard?.isKeyguardLocked == true

    // The audio mode says a call rings or is on without reading the phone's state.
    override val inCall: Boolean
        get() = audio?.mode in CALL_MODES

    override val powerSaving: Boolean
        get() = power?.isPowerSaveMode == true

    override val exactAlarms: Boolean
        get() = app.pokeplan.reminders.Alarms.canSetExact(context)

    /** The phone's own screen for the "Alarms & reminders" permission (Android 12 and later); the app's settings page before that. */
    override fun allowExactAlarms() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 31) android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))
        else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* no such screen on this phone */ }
    }

    override val canContact: Boolean = true

    // The dialer, messages and mail apps are opened with what the person has written down; none of them is started without the player pressing send or call, and no permission is needed.
    override fun call(number: String) = open(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number.trim(), null)))
    override fun message(number: String) = open(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", number.trim(), null)))
    override fun email(address: String) = open(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("mailto", address.trim(), null)))

    override val canSetAlarm: Boolean
        get() = context.packageManager.queryIntentActivities(android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM), 0).isNotEmpty()

    // The clock app shows the alarm for the player to save: a wake-up alarm is the phone's own, so it rings loudly and on time whatever this app is doing.
    override fun setAlarm(hour: Int, minute: Int, label: String) = open(
        android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
            .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false),
    )

    private fun open(intent: android.content.Intent) {
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* no app on this phone handles it */ }
    }

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING)
    }
}
