package app.pokeplan.app

internal actual fun newStoreLock(): StoreLock = object : StoreLock {
    override fun <T> withLock(block: () -> T): T = block()
}
