package app.pokeplan.app

/** Where the app's JSON document lives. Each platform provides one (a file, browser storage). */
interface Storage {
    fun load(): String?
    fun save(text: String)

    /** Keeps [text], a save that could not be read, somewhere [save] will not overwrite, so it can still be recovered. */
    fun keepAside(text: String)
}

class MemoryStorage(var text: String? = null) : Storage {
    var keptAside: String? = null
        private set

    override fun load(): String? = text
    override fun save(text: String) {
        this.text = text
    }
    override fun keepAside(text: String) {
        keptAside = text
    }
}

fun interface Clock {
    fun nowMillis(): Long
}
