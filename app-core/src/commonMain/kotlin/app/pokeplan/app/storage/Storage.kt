package app.pokeplan.app.storage

interface Storage {
    fun load(): String?
    fun save(text: String)

    fun saved(text: String): Boolean { save(text); return true }

    fun keepAside(text: String)

    fun loadBackup(): String? = null

    val shareKey: Any? get() = null
}

class MemoryStorage(var text: String? = null, override val shareKey: Any? = null) : Storage {
    var keptAside: String? = null
        private set

    var backup: String? = null

    override fun load(): String? = text
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
