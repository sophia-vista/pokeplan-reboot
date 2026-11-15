package app.pokeplan.app.storage

/** Where the app's JSON document lives. Each platform provides one (a file, browser storage). */
interface Storage {
    fun load(): String?
    fun save(text: String)

    /**
     * [save], saying whether [text] is now on the disk. False when the storage could not keep it and chose not to throw (a full phone):
     * the stores then carry on from memory, and share that memory ([SharedDocuments]) until a save fits.
     */
    fun saved(text: String): Boolean { save(text); return true }

    /** Keeps [text], a save that could not be read, somewhere [save] will not overwrite, so it can still be recovered. */
    fun keepAside(text: String)

    /** The save as it was before the last [save], if this storage keeps one, to fall back on when [load] gives something unreadable. */
    fun loadBackup(): String? = null

    /**
     * Names the document this storage holds, the same for every storage over that document in this process (the app's, a widget's, a
     * notification's): stores opened on the same key then see each other's writes instead of saving an older copy over them.
     * Null for a storage nothing else opens.
     */
    val shareKey: Any? get() = null
}

class MemoryStorage(var text: String? = null, override val shareKey: Any? = null) : Storage {
    var keptAside: String? = null
        private set

    /** What [loadBackup] returns, for a test of the fall-back. */
    var backup: String? = null

    override fun load(): String? = text
    /** Set to make the next saves fail quietly, as a full phone does. */
    var full = false

    override fun save(text: String) {
        if (!full) this.text = text
    }
    override fun saved(text: String): Boolean { save(text); return !full }
    override fun keepAside(text: String) {
        keptAside = text
    }
    override fun loadBackup(): String? = backup
}

fun interface Clock {
    fun nowMillis(): Long
}
