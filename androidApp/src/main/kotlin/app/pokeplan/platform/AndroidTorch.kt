package app.pokeplan.platform

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import app.pokeplan.ui.Torch

/**
 * The phone's flashlight for the Pokégear, through `CameraManager.setTorchMode` (no camera permission is needed). It knows every camera
 * that has a flash and tries them in turn, and a `TorchCallback` keeps its state true: the light switched by another app, the system or a
 * camera taking the flash is reported here as it happens. [release] turns off a light this app turned on and unregisters the callback (the
 * activity calls it when it stops), so the torch is never left on by accident and the camera service holds no reference to this object while the app
 * is in the background. The callback registers again on the next use, and the system reports the current state of every flash when it does.
 */
class AndroidTorch(context: Context) : Torch {
    private val cameras = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val flashIds: List<String> = try {
        cameras.cameraIdList.filter { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (e: Exception) {
        emptyList()
    }

    /** The flash cameras whose torch is on, and those another app has taken, as the callback last said. */
    private val lit = mutableSetOf<String>()
    private val taken = mutableSetOf<String>()
    private val listeners = mutableListOf<(Boolean) -> Unit>()
    private var ours = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val before = isOn()
            taken.remove(cameraId)
            if (enabled) lit.add(cameraId) else lit.remove(cameraId)
            if (!isOn()) ours = false
            if (isOn() != before) listeners.toList().forEach { it(isOn()) }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            val before = isOn()
            taken.add(cameraId)
            lit.remove(cameraId)
            if (!isOn()) ours = false
            if (isOn() != before) listeners.toList().forEach { it(isOn()) }
        }
    }

    private var registered = false

    /** Listens for the light changing, once, until [release]; without the callback the state is only what this app last set. */
    private fun listen() {
        if (registered || flashIds.isEmpty()) return
        try {
            cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (e: Exception) {
            // Without the callback the state is only what this app last set.
        }
    }

    override fun available(): Boolean = flashIds.isNotEmpty()

    override fun isOn(): Boolean {
        listen()
        return lit.isNotEmpty()
    }

    override fun set(on: Boolean): Boolean {
        if (flashIds.isEmpty()) return false
        listen()
        if (!on) {
            // Switch off every flash that is lit (not one another app is holding).
            for (id in flashIds) if (id in lit) try { cameras.setTorchMode(id, false) } catch (e: Exception) { /* busy: the callback will say */ }
            ours = false
            return isOn()
        }
        // On: the first flash camera that accepts it (one in use by an app refuses, so try the next).
        for (id in flashIds) {
            if (id in taken) continue
            try {
                cameras.setTorchMode(id, true)
                lit.add(id)
                ours = true
                return true
            } catch (e: Exception) {
                continue
            }
        }
        return isOn()
    }

    override fun observe(listener: (Boolean) -> Unit): () -> Unit {
        listen()
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Turns the light off if this app turned it on (called when the tool is left and when the activity stops). A light another app switched on is left alone. */
    override fun release() {
        if (ours) set(false)
        if (registered) {
            try { cameras.unregisterTorchCallback(callback) } catch (e: Exception) { /* already gone */ }
            registered = false
        }
        // What the callback said is stale once it is not listening; it says it all again when it is registered next.
        lit.clear()
        taken.clear()
    }
}
