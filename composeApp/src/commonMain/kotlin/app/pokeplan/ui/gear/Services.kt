package app.pokeplan.ui.gear

data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val atMillis: Long)

interface LocationFeed {
    fun fix(onResult: (Fix?, String?) -> Unit)
}

interface SoundFeed {
    fun start(onFrame: (ShortArray, Int) -> Unit, onProblem: (String) -> Unit): (() -> Unit)?
}

data class NetworkInfo(val kind: String, val metered: Boolean, val validated: Boolean) {
    val online: Boolean get() = kind != "No connection"
}

interface NetworkFeed { fun info(): NetworkInfo }

interface CodeScanner {
    fun scan(onResult: (String?) -> Unit)
}

class GearServices(
    val location: LocationFeed? = null,
    val sound: SoundFeed? = null,
    val network: NetworkFeed? = null,
    val scanner: CodeScanner? = null,
)
