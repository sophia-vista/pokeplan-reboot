package app.pokeplan.platform

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import app.pokeplan.ui.Torch

class AndroidTorch(context: Context) : Torch {
    private val cameras = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val flashIds: List<String> = try {
        cameras.cameraIdList.filter { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (e: Exception) {
        emptyList()
    }

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

    private fun listen() {
        if (registered || flashIds.isEmpty()) return
        try {
            cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (e: Exception) {
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
            for (id in flashIds) if (id in lit) try { cameras.setTorchMode(id, false) } catch (e: Exception) {  }
            ours = false
            return isOn()
        }
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

    override fun release() {
        if (ours) set(false)
        if (registered) {
            try { cameras.unregisterTorchCallback(callback) } catch (e: Exception) {  }
            registered = false
        }
        lit.clear()
        taken.clear()
    }
}
