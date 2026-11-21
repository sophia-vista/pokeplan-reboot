package app.pokeplan.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.ui.DeviceGate

class AndroidDeviceGate(private val activity: ComponentActivity) : DeviceGate {
    private var waiting: ((Boolean) -> Unit)? = null
    @Volatile private var leftAt = 0L

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val done = waiting; waiting = null
        done?.invoke(result.resultCode == Activity.RESULT_OK)
    }

    private val keyguard get() = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    override fun available(): Boolean = keyguard?.isDeviceSecure == true

    override fun authenticate(onResult: (Boolean) -> Unit) {
        @Suppress("DEPRECATION")
        val intent = keyguard?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent("Open Mint", "Use your screen lock to open your money records.")
        if (intent == null || waiting != null) { onResult(false); return }
        waiting = onResult
        runCatching { launcher.launch(intent) }.onFailure { waiting = null; onResult(false) }
    }

    override fun leftAt(): Long = leftAt

    fun left() { leftAt = System.currentTimeMillis() }
}
