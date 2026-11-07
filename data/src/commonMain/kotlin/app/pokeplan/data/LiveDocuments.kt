package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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

    /** Whether [name] is kept in tables rather than as a row (Care, once [moveCareToTables] has run). */
    private fun inTables(name: String): Boolean = name == CARE && store.careTables.isActive()

    /** Whether the PLAN part of [name] is kept in tables (the save, once [movePlanToTables] has run); the rest of it stays a row. */
    private fun planSplit(name: String): Boolean = name == SAVE && store.planTables.isActive()

    fun read(name: String): String? = when {
        inTables(name) -> store.careTables.read()
        planSplit(name) -> store.transaction { assembleSave() }
        else -> q.liveRead(name).executeAsOneOrNull()
    }

    /** The save put back together: its row (everything that is not PLAN's) with PLAN's fields from the tables. */
    private fun assembleSave(): String? {
        val rest = q.liveRead(SAVE).executeAsOneOrNull()?.let { JSON.parseToJsonElement(it) as? JsonObject }
        val plan = store.planTables.read()
        if (rest == null && plan.isEmpty()) return null
        return JSON.encodeToString(JsonObject.serializer(), JsonObject((rest ?: JsonObject(emptyMap())) + plan))
    }

    /** One document, one transaction. A document kept in tables is split into its rows, and only the rows that changed are written. */
    fun write(name: String, json: String, nowMillis: Long) {
        when {
            inTables(name) -> store.careTables.write(json)
            planSplit(name) -> store.transaction {
                val (plan, rest) = store.planTables.split(json)
                store.planTables.write(plan)
                q.liveWrite(name, JSON.encodeToString(JsonObject.serializer(), rest), nowMillis)
            }
            else -> store.transaction { q.liveWrite(name, json, nowMillis) }
        }
    }

    /**
     * Moves PLAN's part of the save from its row into PLAN's tables (REP-014), once: the rows are written and read back equal to the
     * document, the marker is set, the whole old row is kept under `aside:save:plan:<millis>` (never deleted) and the `save` row keeps
     * only what is not PLAN's, all in one transaction, so a failure leaves the save row as the live save. Does nothing once done, so it
     * can run on every start.
     */
    fun movePlanToTables(nowMillis: Long) {
        if (store.planTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(SAVE).executeAsOneOrNull()
            val rest = store.planTables.activate(doc, nowMillis)
            if (doc != null && rest != null) {
                q.liveWrite("${ASIDE}$SAVE:plan:$nowMillis", doc, nowMillis)
                q.liveWrite(SAVE, JSON.encodeToString(JsonObject.serializer(), rest), nowMillis)
            }
        }
    }

    /**
     * Moves the Care document from its row into Care's tables (REP-014), once: the rows are written and read back equal to the
     * document, the marker is set, and the old row is kept under `aside:care:tables:<millis>` (never deleted), all in one
     * transaction, so a failure leaves the document row as the live Care save. Does nothing once done, so it can run on every start.
     */
    fun moveCareToTables(nowMillis: Long) {
        if (store.careTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(CARE).executeAsOneOrNull()
            store.careTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$CARE:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(CARE)
            }
        }
    }

    /** Keeps a document that could not be read where [write] will not replace it. */
    fun keepAside(name: String, json: String, nowMillis: Long) {
        write("$ASIDE$name:$nowMillis", json, nowMillis)
    }

    fun names(): List<String> = (q.liveNames().executeAsList() + listOfNotNull(CARE.takeIf { store.careTables.isActive() && store.careTables.read() != null })).distinct().sorted()

    /** Every live document (not the kept-aside ones) as of one moment: a single read transaction. Care's tables are put back together as `care`. */
    fun snapshot(): Map<String, String> = store.transaction {
        val docs = q.liveNames().executeAsList().filter { !it.startsWith(ASIDE) }.associateWith { q.liveRead(it).executeAsOne() }.toMutableMap()
        if (store.careTables.isActive()) store.careTables.read()?.let { docs[CARE] = it }
        if (store.planTables.isActive()) assembleSave()?.let { docs[SAVE] = it }
        docs
    }

    /**
     * Restores [docs] (a backup): in one transaction the documents being replaced are first kept aside, so a restore can itself
     * be undone, then the backup's documents are written. Documents not in [docs] are left alone.
     */
    fun replaceAll(docs: Map<String, String>, nowMillis: Long) {
        store.transaction {
            for ((name, json) in docs) {
                read(name)?.let { q.liveWrite("$ASIDE$name:$nowMillis", it, nowMillis) }
                write(name, json, nowMillis)
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
        private val JSON = Json { encodeDefaults = true }
        const val ASIDE = "aside:"
        const val SAVE = "save"
        const val CARE = "care"
        const val META_LIVE = "live_store"
        const val META_LIVE_AT = "live_store_since"
        const val LIVE_DATABASE = "database"
    }
}
