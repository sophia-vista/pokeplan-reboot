package app.pokeplan.app

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual fun newStoreLock(): StoreLock = object : StoreLock {
    private val lock = ReentrantLock()
    override fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
