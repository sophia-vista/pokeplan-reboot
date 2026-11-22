package app.pokeplan.ui.gear

enum class Capability(val label: String) {
    TORCH("Flashlight"), CAMERA("Camera"), MICROPHONE("Microphone"), ACCELEROMETER("Accelerometer"), GYROSCOPE("Gyroscope"),
    MAGNETOMETER("Magnetic field sensor"), BAROMETER("Barometer"), LIGHT("Light sensor"), PROXIMITY("Proximity sensor"), STEP_COUNTER("Step counter"),
    AMBIENT_TEMPERATURE("Ambient temperature sensor"), HUMIDITY("Humidity sensor"), LOCATION("Location (GNSS)"), NFC("NFC"), BLUETOOTH_LE("Bluetooth LE"), WIFI("Wi-Fi"),
}

enum class PermissionNeed(val label: String) { NONE("none"), CAMERA("Camera"), RECORD_AUDIO("Microphone"), LOCATION("Location"), NEARBY_DEVICES("Nearby devices"), NOTIFICATIONS("Notifications") }

data class CapabilityStatus(val capability: Capability, val available: Boolean, val permission: PermissionNeed = PermissionNeed.NONE, val granted: Boolean? = null, val note: String = "") {
    val usableNow: Boolean get() = available && granted != false

    val needsAsking: Boolean get() = available && granted == false
}

interface DeviceCapabilities {
    fun status(capability: Capability): CapabilityStatus

    fun all(): List<CapabilityStatus> = Capability.entries.map(::status)
}

object NoCapabilities : DeviceCapabilities {
    override fun status(capability: Capability) = CapabilityStatus(capability, available = false, note = "Not available on this platform.")
}

fun CapabilityStatus.describe(): String = when {
    !available -> "${capability.label}: not available. $note".trim()
    needsAsking -> "${capability.label}: available, asks first"
    else -> "${capability.label}: available." + if (note.isNotEmpty()) " $note" else ""
}
