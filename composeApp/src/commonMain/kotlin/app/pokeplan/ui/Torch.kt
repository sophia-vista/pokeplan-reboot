package app.pokeplan.ui

/** The phone's flashlight, for the Pokégear's Flashlight tool. Only the Android app provides one; elsewhere the tool lights the screen white instead. */
interface Torch {
    /** Whether this device has a flashlight. */
    fun available(): Boolean

    /** Turns it on or off; returns whether it is on afterwards (the camera may be in use by another app). */
    fun set(on: Boolean): Boolean
}
