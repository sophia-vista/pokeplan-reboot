package app.pokeplan.ui

/** The phone's flashlight, for the Pokégear's Flashlight tool. Only the Android app provides one; elsewhere the tool lights the screen white instead. */
interface Torch {
    /** Whether this device has a flashlight. */
    fun available(): Boolean

    /** Turns it on or off; returns whether it is on afterwards (the camera may be in use by another app). */
    fun set(on: Boolean): Boolean

    /** Whether the light is on right now, as the hardware last reported it (another app or the system may have changed it). */
    fun isOn(): Boolean = false

    /** Calls [listener] on the main thread whenever the light goes on or off, whoever did it; the returned function stops listening. */
    fun observe(listener: (Boolean) -> Unit): () -> Unit = {}

    /** Turns the light off if this app turned it on; one another app switched on is left alone. Called when the tool or the app is left. */
    fun release() {}
}
