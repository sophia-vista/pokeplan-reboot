package app.pokeplan.ui

/** Tells the app when the phone is moved, for Deep Focus. Only the Android app provides one. */
interface MotionSource {
    /** Starts watching; [onMoved] is called when the phone is picked up or shaken. */
    fun start(onMoved: () -> Unit)
    fun stop()
}

/**
 * Why the app went to the background, so a focus session can tell leaving from a locked screen or a phone call
 * (design.md, Focus: neither counts as leaving). Only the Android app provides one.
 */
interface PhoneState {
    /** True when the screen is off or locked. */
    val screenOff: Boolean

    /** True while a call rings or is in progress. */
    val inCall: Boolean

    /** True while the phone's power saver (battery saver) is on. */
    val powerSaving: Boolean get() = false

    /** False when the phone does not let the app set exact alarms, so reminders and timers can arrive minutes late; Settings then says so. */
    val exactAlarms: Boolean get() = true

    /** Opens the phone's screen where the player allows exact alarms. */
    fun allowExactAlarms() {}
}
