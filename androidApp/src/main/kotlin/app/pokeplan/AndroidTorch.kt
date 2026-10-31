package app.pokeplan

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import app.pokeplan.ui.Torch

/** The phone's flashlight for the Pokégear: the first camera that has a flash, switched with `setTorchMode` (no camera permission is needed). */
class AndroidTorch(context: Context) : Torch {
    private val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val id: String? = try {
        cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (e: Exception) {
        null
    }
    private var on = false

    override fun available(): Boolean = id != null

    override fun set(on: Boolean): Boolean {
        val camera = id ?: return false
        try {
            cameras.setTorchMode(camera, on)
            this.on = on
        } catch (e: Exception) {
            // The camera is in use by an app: the light stays as it was.
        }
        return this.on
    }
}
