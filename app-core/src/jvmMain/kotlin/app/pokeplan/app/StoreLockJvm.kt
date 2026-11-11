package app.pokeplan.app

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val lock = ReentrantLock()

internal actual fun newStoreLock(): StoreLock = object : StoreLock {
    override fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
