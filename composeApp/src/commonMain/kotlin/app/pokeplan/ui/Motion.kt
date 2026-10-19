package app.pokeplan.ui

/** Tells the app when the phone is moved, for Deep Focus. Only the Android app provides one. */
interface MotionSource {
    /** Starts watching; [onMoved] is called when the phone is picked up or shaken. */
    fun start(onMoved: () -> Unit)
    fun stop()
}
