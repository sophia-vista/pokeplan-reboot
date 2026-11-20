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

    /** False when Android is blocking this app's notifications (the permission refused, or the app's notifications turned off); Settings then says so. */
    val notificationsAllowed: Boolean get() = true

    /** Opens the phone's own notification settings for this app. */
    fun openNotificationSettings() {}

    /** Whether this device can hand a number or an address to its dialer, messages and mail apps (Link shows Call, Message and Email only then). */
    val canContact: Boolean get() = false

    /** Opens the dialer with [number] typed in; it never places the call, the player does. */
    fun call(number: String) {}

    /** Opens the messages app with a new message to [number]. */
    fun message(number: String) {}

    /** Opens the mail app with a new message to [address]. */
    fun email(address: String) {}

    /** Whether the phone's own clock app can be asked to set an alarm. */
    val canSetAlarm: Boolean get() = false

    /** Hands a wake-up alarm for [hour]:[minute] to the phone's clock app, which shows it for the player to keep; it rings with the phone's alarm sound, even in Doze or silent mode. */
    fun setAlarm(hour: Int, minute: Int, label: String) {}
}
