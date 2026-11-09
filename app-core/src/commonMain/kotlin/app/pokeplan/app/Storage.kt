package app.pokeplan.app

/** Where the app's JSON document lives. Each platform provides one (a file, browser storage). */
interface Storage {
    fun load(): String?
    fun save(text: String)

    /** Keeps [text], a save that could not be read, somewhere [save] will not overwrite, so it can still be recovered. */
    fun keepAside(text: String)

    /** The save as it was before the last [save], if this storage keeps one, to fall back on when [load] gives something unreadable. */
    fun loadBackup(): String? = null
}

class MemoryStorage(var text: String? = null) : Storage {
    var keptAside: String? = null
        private set

    /** What [loadBackup] returns, for a test of the fall-back. */
    var backup: String? = null

    override fun load(): String? = text
    override fun save(text: String) {
        this.text = text
    }
    override fun keepAside(text: String) {
        keptAside = text
    }
    override fun loadBackup(): String? = backup
}

fun interface Clock {
    fun nowMillis(): Long
}
