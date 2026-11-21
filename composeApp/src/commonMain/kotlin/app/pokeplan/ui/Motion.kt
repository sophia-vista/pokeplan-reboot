package app.pokeplan.ui

interface MotionSource {
    fun start(onMoved: () -> Unit)
    fun stop()
}

interface PhoneState {
    val screenOff: Boolean

    val inCall: Boolean

    val powerSaving: Boolean get() = false

    val exactAlarms: Boolean get() = true

    fun allowExactAlarms() {}

    val notificationsAllowed: Boolean get() = true

    fun openNotificationSettings() {}

    val canContact: Boolean get() = false

    fun call(number: String) {}

    fun message(number: String) {}

    fun email(address: String) {}

    val canSetAlarm: Boolean get() = false

    fun setAlarm(hour: Int, minute: Int, label: String) {}
}
