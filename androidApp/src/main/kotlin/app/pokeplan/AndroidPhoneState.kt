package app.pokeplan

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

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING)
    }
}
