package app.pokeplan.ui.gear

/**
 * What a Pokégear tool may need from the device. The Android app finds out at run time which of them this phone really has (never from the
 * model's name); every other platform has none, so a tool that needs one says it is not available there instead of faking a reading.
 */
enum class Capability(val label: String) {
    TORCH("Flashlight"), CAMERA("Camera"), MICROPHONE("Microphone"), ACCELEROMETER("Accelerometer"), GYROSCOPE("Gyroscope"),
    MAGNETOMETER("Magnetic field sensor"), BAROMETER("Barometer"), LIGHT("Light sensor"), PROXIMITY("Proximity sensor"), STEP_COUNTER("Step counter"),
    AMBIENT_TEMPERATURE("Ambient temperature sensor"), HUMIDITY("Humidity sensor"), LOCATION("Location (GNSS)"), NFC("NFC"), BLUETOOTH_LE("Bluetooth LE"), WIFI("Wi-Fi"),
}

/** The permission a capability needs once it is used (asked for only then, with a reason, and a no-permission fallback where there is one). */
enum class PermissionNeed(val label: String) { NONE("none"), CAMERA("Camera"), RECORD_AUDIO("Microphone"), LOCATION("Location"), NEARBY_DEVICES("Nearby devices"), NOTIFICATIONS("Notifications") }

/**
 * One capability on this device: whether the hardware (or API) is there, which permission using it needs and whether that is granted
 * ([granted] is null when none is needed), and a short [note] on what limits it (a sensor missing, an Android restriction, "this platform").
 */
data class CapabilityStatus(val capability: Capability, val available: Boolean, val permission: PermissionNeed = PermissionNeed.NONE, val granted: Boolean? = null, val note: String = "") {
    /** Whether a tool can use it right now without asking for anything. */
    val usableNow: Boolean get() = available && granted != false

    /** Whether the hardware is there but the permission still has to be asked for (when the player opens the tool, not before). */
    val needsAsking: Boolean get() = available && granted == false
}

/** What this device can do, found out at run time. */
interface DeviceCapabilities {
    fun status(capability: Capability): CapabilityStatus

    /** Every capability, in the order of [Capability.entries]. */
    fun all(): List<CapabilityStatus> = Capability.entries.map(::status)
}

/** The platforms that have no hardware tools (desktop and the web): everything says so. */
object NoCapabilities : DeviceCapabilities {
    override fun status(capability: Capability) = CapabilityStatus(capability, available = false, note = "Not available on this platform.")
}

/** One line for a capability's status, for a list that tells the player what their device can do. */
fun CapabilityStatus.describe(): String = when {
    !available -> "${capability.label}: not available. $note".trim()
    needsAsking -> "${capability.label}: available; ${permission.label} permission is asked for when you use it."
    else -> "${capability.label}: available." + if (note.isNotEmpty()) " $note" else ""
}
