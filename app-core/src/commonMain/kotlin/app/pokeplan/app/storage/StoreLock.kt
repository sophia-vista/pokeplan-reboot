package app.pokeplan.app.storage

internal interface StoreLock {
    fun <T> withLock(block: () -> T): T
}

internal expect fun newStoreLock(): StoreLock

internal object SharedDocuments {
    private val revisions = mutableMapOf<Any, Long>()
    fun revision(key: Any): Long = revisions[key] ?: 0L
    fun written(key: Any): Long = (revision(key) + 1).also { revisions[key] = it }

    private val unsaved = mutableMapOf<Any, String>()
    fun unsaved(key: Any): String? = unsaved[key]
    fun keep(key: Any, text: String?) { if (text == null) unsaved.remove(key) else unsaved[key] = text }
}
