package app.pokeplan.app

/**
 * Makes one change at a time, across every store in the process: the app, a widget and a notification each open their own store on the
 * same save, so a lock of one store's own would not keep them apart. Changes are short (build the next document, write it).
 *
 * Makes one change to a store at a time. A store is changed from the UI thread, from widgets, from notification receivers and from
 * background work, and a change reads the document, builds the next one and writes it: two at once would both start from the same
 * document and the later write would silently drop the earlier one. Re-entrant, so a change may call another store function.
 */
internal interface StoreLock {
    fun <T> withLock(block: () -> T): T
}

/** The platform's lock: one re-entrant lock for the whole process on the JVM (and Android), none in the browser, where there is one thread. */
internal expect fun newStoreLock(): StoreLock

/**
 * How many times each shared document ([Storage.shareKey]) was written by any store in this process. A store that last saw another
 * number knows its copy is old. Read and written only under the store lock.
 */
internal object SharedDocuments {
    private val revisions = mutableMapOf<Any, Long>()
    fun revision(key: Any): Long = revisions[key] ?: 0L
    fun written(key: Any): Long = (revision(key) + 1).also { revisions[key] = it }
}
