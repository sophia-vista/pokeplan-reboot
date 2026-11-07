package app.pokeplan.ui

/**
 * The phone's own screen lock, asked before Mint opens when the user turned the optional gate on (docs/pokelife `MODULES/MINT.md` section 13.2). It is only a
 * door in front of the screens: it holds no key and no password of its own, nothing is encrypted with it, and turning it off or losing it can never make a record
 * unreadable (the records stay sealed by the phone's own key either way). The platform asks; the app learns only "yes" or "no".
 */
interface DeviceGate {
    /** Whether the phone has a screen lock to ask. Without one the gate cannot be turned on, and a gate already on asks nothing. */
    fun available(): Boolean

    /** Asks the person to prove it is them with the screen lock (PIN, pattern, password or biometrics). [onResult] gets true only when they did. */
    fun authenticate(onResult: (Boolean) -> Unit)

    /** When the app last went out of sight, in epoch milliseconds; 0 when it has not since it started. */
    fun leftAt(): Long

    companion object {
        /** How long Mint stays open after the app or the module is left; coming back inside it asks nothing. */
        const val GRACE_MILLIS = 30_000L
    }
}
