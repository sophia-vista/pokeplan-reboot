package app.pokeplan.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.ui.gear.CodeScanner
import app.pokeplan.ui.gear.Fix
import app.pokeplan.ui.gear.GearServices
import app.pokeplan.ui.gear.LocationFeed
import app.pokeplan.ui.gear.NetworkFeed
import app.pokeplan.ui.gear.NetworkInfo
import app.pokeplan.ui.gear.SoundFeed
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * The phone's location, microphone, network state and QR scanner for the Pokégear's field tools. Permissions are asked for only when a tool is
 * used (the screen has said why first); nothing runs in the background. Create it in `onCreate`, before the activity starts, because it
 * registers the permission launchers.
 */
fun androidGearServices(activity: ComponentActivity): GearServices = GearServices(
    location = AndroidLocationFeed(activity), sound = AndroidSoundFeed(activity), network = AndroidNetworkFeed(activity), scanner = AndroidCodeScanner(activity),
)

/** One permission request at a time, answered to the callback that asked. */
private class PermissionAsker(private val activity: ComponentActivity, private val permission: String) {
    private var waiting: ((Boolean) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> waiting?.invoke(granted); waiting = null }

    fun granted(): Boolean = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun ask(then: (Boolean) -> Unit) {
        if (granted()) { then(true); return }
        waiting = then
        launcher.launch(permission)
    }
}

private class AndroidLocationFeed(private val activity: ComponentActivity) : LocationFeed {
    private val permission = PermissionAsker(activity, Manifest.permission.ACCESS_FINE_LOCATION)
    private val main = Handler(Looper.getMainLooper())

    override fun fix(onResult: (Fix?, String?) -> Unit) {
        permission.ask { ok ->
            if (!ok) onResult(null, "Location permission was not given. You can allow it in the phone's settings for PokéLife.") else read(onResult)
        }
    }

    @SuppressLint("MissingPermission")
    private fun read(onResult: (Fix?, String?) -> Unit) {
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) { onResult(null, "This phone has no location service."); return }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { try { manager.isProviderEnabled(it) } catch (e: Exception) { false } }
        if (provider == null) { onResult(null, "Location is switched off. Turn it on in the phone's quick settings and try again."); return }
        var done = false
        fun finish(l: Location?) {
            if (done) return
            done = true
            if (l == null) onResult(null, "No fix yet. Try outdoors with a clear sky.")
            else onResult(Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 0f, System.currentTimeMillis()), null)
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) manager.getCurrentLocation(provider, null, activity.mainExecutor) { finish(it) }
            else manager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) = finish(location)
                @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, Looper.getMainLooper())
        } catch (e: Exception) {
            finish(null)
        }
        main.postDelayed({ finish(null) }, 25_000)
    }
}

private class AndroidSoundFeed(private val activity: ComponentActivity) : SoundFeed {
    private val permission = PermissionAsker(activity, Manifest.permission.RECORD_AUDIO)
    private val main = Handler(Looper.getMainLooper())

    override fun start(onFrame: (ShortArray, Int) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) return null
        var running = true
        var thread: Thread? = null
        permission.ask { ok ->
            if (!ok) { onProblem("Microphone permission was not given. You can allow it in the phone's settings for PokéLife."); return@ask }
            if (!running) return@ask
            thread = Thread { record({ running }, onFrame, onProblem) }.also { it.isDaemon = true; it.start() }
        }
        return { running = false; thread?.interrupt() }
    }

    @SuppressLint("MissingPermission")
    private fun record(alive: () -> Boolean, onFrame: (ShortArray, Int) -> Unit, onProblem: (String) -> Unit) {
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) { main.post { onProblem("This phone's microphone cannot be read at the rate the meter needs.") }; return }
        val recorder = try { AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2) } catch (e: Exception) { null }
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) { main.post { onProblem("The microphone could not be opened (another app may be using it).") }; recorder?.release(); return }
        try {
            recorder.startRecording()
            val buffer = ShortArray(1024)
            while (alive()) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) break
                val copy = buffer.copyOf(n)
                main.post { if (alive()) onFrame(copy, rate) }
            }
        } catch (e: Exception) {
            // Stopped: nothing to report.
        } finally {
            try { recorder.stop() } catch (e: Exception) { /* already stopped */ }
            recorder.release()
        }
    }
}

private class AndroidNetworkFeed(context: Context) : NetworkFeed {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun info(): NetworkInfo {
        val network = try { manager?.activeNetwork } catch (e: Exception) { null }
        val caps = network?.let { try { manager?.getNetworkCapabilities(it) } catch (e: Exception) { null } }
            ?: return NetworkInfo("No connection", false, false)
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        return NetworkInfo(kind, !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }
}

/** Google's code scanner: it opens its own screen, needs no camera permission from PokéLife and returns the text. It needs Google Play services. */
private class AndroidCodeScanner(private val activity: ComponentActivity) : CodeScanner {
    override fun scan(onResult: (String?) -> Unit) {
        try {
            GmsBarcodeScanning.getClient(activity).startScan()
                .addOnSuccessListener { onResult(it.rawValue) }
                .addOnCanceledListener { onResult(null) }
                .addOnFailureListener { onResult(null) }
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
