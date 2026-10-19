package app.pokeplan.ui

/** A short tick when the buddy is petted or fed. Android vibrates; the other platforms have none. */
fun interface HapticPlayer {
    fun tick()
}
