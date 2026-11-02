package app.pokeplan.ui.gear

/** One reading of where the phone is: degrees, how far off it may be (the radius of the circle it is probably in) and when. */
data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val atMillis: Long)

/** One place to get a fix: asks for the location permission when it is needed (the tool explains why first), then reads once. Never tracks. */
interface LocationFeed {
    /** Calls [onResult] once with a fix, or with null and a reason ("Location permission was not given", "No fix yet: try outdoors"). */
    fun fix(onResult: (Fix?, String?) -> Unit)
}

/** The microphone as 16-bit mono samples, only while a tool is open. */
interface SoundFeed {
    /** Starts listening (asking for the microphone permission first if needed); [onFrame] gets a block of samples and their rate in Hz, [onProblem] why it could not start. Returns what stops it, or null when there is no microphone. */
    fun start(onFrame: (ShortArray, Int) -> Unit, onProblem: (String) -> Unit): (() -> Unit)?
}

/** How the phone is connected right now. */
data class NetworkInfo(val kind: String, val metered: Boolean, val validated: Boolean) {
    val online: Boolean get() = kind != "No connection"
}

interface NetworkFeed { fun info(): NetworkInfo }

/** Reads a QR code or barcode with the camera. */
interface CodeScanner {
    /** Opens the scanner; [onResult] gets the text, or null when it was cancelled or could not open. */
    fun scan(onResult: (String?) -> Unit)
}

/** The device services the Pokégear's specialist tools use; each is null where the platform has none, and the tool says so. */
class GearServices(
    val location: LocationFeed? = null,
    val sound: SoundFeed? = null,
    val network: NetworkFeed? = null,
    val scanner: CodeScanner? = null,
)
