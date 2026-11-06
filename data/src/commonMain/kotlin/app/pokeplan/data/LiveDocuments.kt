package app.pokeplan.data

/** What activation did, for the caller to log or show. */
sealed interface Activation {
    /** The database was already the live store; nothing was imported. */
    data object AlreadyActive : Activation

    /** The legacy documents were copied in, read back equal, and the marker was set. */
    data class Imported(val documents: List<String>) : Activation
}

/**
 * The live save inside the database (DATA_AND_MIGRATIONS.md section 12.6): the app's own JSON documents, written one
 * transaction at a time. Once [isActive] the database is the only authority and the legacy files are never written again
 * (they stay as the rollback copy). There is no dual write: before activation nothing here is read, after it nothing writes
 * the files.
 */
class LiveDocuments(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun isActive(): Boolean = q.meta(META_LIVE).executeAsOneOrNull() == LIVE_DATABASE

    fun read(name: String): String? = q.liveRead(name).executeAsOneOrNull()

    /** One document, one transaction. */
    fun write(name: String, json: String, nowMillis: Long) {
        store.transaction { q.liveWrite(name, json, nowMillis) }
    }

    /** Keeps a document that could not be read where [write] will not replace it. */
    fun keepAside(name: String, json: String, nowMillis: Long) {
        write("$ASIDE$name:$nowMillis", json, nowMillis)
    }

    fun names(): List<String> = q.liveNames().executeAsList()

    /** Every live document (not the kept-aside ones) as of one moment: a single read transaction. */
    fun snapshot(): Map<String, String> = store.transaction {
        q.liveNames().executeAsList().filter { !it.startsWith(ASIDE) }.associateWith { q.liveRead(it).executeAsOne() }
    }

    /**
     * Restores [docs] (a backup): in one transaction the documents being replaced are first kept aside, so a restore can itself
     * be undone, then the backup's documents are written. Documents not in [docs] are left alone.
     */
    fun replaceAll(docs: Map<String, String>, nowMillis: Long) {
        store.transaction {
            for ((name, json) in docs) {
                q.liveRead(name).executeAsOneOrNull()?.let { q.liveWrite("$ASIDE$name:$nowMillis", it, nowMillis) }
                q.liveWrite(name, json, nowMillis)
            }
        }
    }

    /**
     * Copies the legacy documents ([save], [care]; null when the file does not exist) in, reads every one back and checks it
     * is byte-for-byte what was given, then sets the marker, all in one transaction: a failure leaves the database exactly as
     * it was and the files stay the live store. Already active: does nothing, so it can be called on every start.
     */
    fun activate(save: String?, care: String?, nowMillis: Long): Activation {
        if (isActive()) return Activation.AlreadyActive
        val docs = listOfNotNull(save?.let { SAVE to it }, care?.let { CARE to it })
        store.transaction {
            for ((name, json) in docs) {
                q.liveWrite(name, json, nowMillis)
                check(q.liveRead(name).executeAsOne() == json) { "the copy of '$name' did not read back equal" }
            }
            q.setMeta(META_LIVE, LIVE_DATABASE)
            q.setMeta(META_LIVE_AT, nowMillis.toString())
        }
        return Activation.Imported(docs.map { it.first })
    }

    companion object {
        const val ASIDE = "aside:"
        const val SAVE = "save"
        const val CARE = "care"
        const val META_LIVE = "live_store"
        const val META_LIVE_AT = "live_store_since"
        const val LIVE_DATABASE = "database"
    }
}
