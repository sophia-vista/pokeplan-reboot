package app.pokeplan.ui

interface Torch {
    fun available(): Boolean

    fun set(on: Boolean): Boolean

    fun isOn(): Boolean = false

    fun observe(listener: (Boolean) -> Unit): () -> Unit = {}

    fun release() {}
}
