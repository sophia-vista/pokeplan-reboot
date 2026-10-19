package app.pokeplan

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.pokeplan.ui.HapticPlayer

/**
 * A short tick through the phone's vibrator. Compose's own haptic feedback follows the system "touch feedback" setting and
 * is often silent, so this talks to the vibrator directly (it still respects the phone's vibration and Do Not Disturb settings).
 */
class AndroidHaptics(context: Context) : HapticPlayer {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun tick() {
        try {
            val v = vibrator?.takeIf { it.hasVibrator() } ?: return
            v.vibrate(VibrationEffect.createOneShot(25, 120))
        } catch (e: Exception) {
            // No vibrator, or not allowed right now: nothing to do.
        }
    }
}
