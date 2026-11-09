package app.pokeplan.app

/**
 * Makes one change to a store at a time. A store is changed from the UI thread, from widgets, from notification receivers and from
 * background work, and a change reads the document, builds the next one and writes it: two at once would both start from the same
 * document and the later write would silently drop the earlier one. Re-entrant, so a change may call another store function.
 */
internal interface StoreLock {
    fun <T> withLock(block: () -> T): T
}

/** The platform's lock: a re-entrant one on the JVM (and Android), none in the browser, where there is one thread. */
internal expect fun newStoreLock(): StoreLock
