package app.pokeplan.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import app.pokeplan.ui.gear.Capability
import app.pokeplan.ui.gear.CapabilityStatus
import app.pokeplan.ui.gear.DeviceCapabilities
import app.pokeplan.ui.gear.PermissionNeed

class AndroidCapabilities(context: Context) : DeviceCapabilities {
    private val app = context.applicationContext
    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val packages = app.packageManager

    private fun hasSensor(type: Int): Boolean = try { sensors?.getDefaultSensor(type) != null } catch (e: Exception) { false }
    private fun granted(permission: String): Boolean = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun torch(): Boolean = try {
        val cameras = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameras.cameraIdList.any { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (e: Exception) {
        false
    }

    override fun status(capability: Capability): CapabilityStatus = when (capability) {
        Capability.TORCH -> CapabilityStatus(capability, torch(), note = "No permission needed; a camera in use by another app can refuse it.")
        Capability.CAMERA -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY), PermissionNeed.CAMERA, granted(Manifest.permission.CAMERA))
        Capability.MICROPHONE -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_MICROPHONE), PermissionNeed.RECORD_AUDIO, granted(Manifest.permission.RECORD_AUDIO))
        Capability.ACCELEROMETER -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_ACCELEROMETER))
        Capability.GYROSCOPE -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_GYROSCOPE))
        Capability.MAGNETOMETER -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_MAGNETIC_FIELD), note = "Metal, magnets and speakers nearby disturb it.")
        Capability.BAROMETER -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_PRESSURE))
        Capability.LIGHT -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_LIGHT), note = "A phone sensor estimate, not a calibrated meter.")
        Capability.PROXIMITY -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_PROXIMITY))
        Capability.STEP_COUNTER -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_STEP_COUNTER), PermissionNeed.NONE, note = "Counts from this phone only, not a watch.")
        Capability.AMBIENT_TEMPERATURE -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_AMBIENT_TEMPERATURE), note = "Few phones have one; the battery temperature is not the air.")
        Capability.HUMIDITY -> CapabilityStatus(capability, hasSensor(Sensor.TYPE_RELATIVE_HUMIDITY))
        Capability.LOCATION -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS), PermissionNeed.LOCATION, granted(Manifest.permission.ACCESS_FINE_LOCATION), "Indoors and in cities the accuracy can be poor.")
        Capability.NFC -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_NFC))
        Capability.BLUETOOTH_LE -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE), PermissionNeed.NEARBY_DEVICES, granted(Manifest.permission.BLUETOOTH_SCAN))
        Capability.WIFI -> CapabilityStatus(capability, packages.hasSystemFeature(PackageManager.FEATURE_WIFI), note = "Android limits and throttles Wi-Fi scans and gates them behind permissions.")
    }
}
