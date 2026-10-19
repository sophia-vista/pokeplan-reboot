package app.pokeplan.app

/** Where the app's JSON document lives. Each platform provides one (a file, browser storage). */
interface Storage {
    fun load(): String?
    fun save(text: String)
}

class MemoryStorage(var text: String? = null) : Storage {
    override fun load(): String? = text
    override fun save(text: String) {
        this.text = text
    }
}

fun interface Clock {
    fun nowMillis(): Long
}
