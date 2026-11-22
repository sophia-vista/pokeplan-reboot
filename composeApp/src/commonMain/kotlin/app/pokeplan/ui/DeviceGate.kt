package app.pokeplan.ui

interface DeviceGate {
    fun available(): Boolean

    fun authenticate(onResult: (Boolean) -> Unit)

    fun leftAt(): Long

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}
