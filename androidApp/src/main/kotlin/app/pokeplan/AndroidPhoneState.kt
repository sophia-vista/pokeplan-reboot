package app.pokeplan

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import app.pokeplan.ui.PhoneState

/** Reads the screen and call state when the app goes to the background; needs no permission. */
class AndroidPhoneState(context: Context) : PhoneState {
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

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING)
    }
}
