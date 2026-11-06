package app.pokeplan

import android.content.Context
import android.util.Log
import app.pokeplan.app.FileStorage
import app.pokeplan.app.Storage
import app.pokeplan.data.AndroidDatabase
import app.pokeplan.data.DataStore
import app.pokeplan.data.LiveBackup
import app.pokeplan.data.LiveDocuments
import app.pokeplan.data.NewerSchemaException
import java.io.File

/** A document of the live save, kept in the database one transaction at a time. */
class DatabaseStorage(private val live: LiveDocuments, private val name: String) : Storage {
    override fun load(): String? = live.read(name)
    override fun save(text: String) = live.write(name, text, System.currentTimeMillis())
    override fun keepAside(text: String) = live.keepAside(name, text, System.currentTimeMillis())
}

/**
 * The cutover (DATA_AND_MIGRATIONS.md section 12.6). The app and the widgets in this process share one connection. On the
 * first start with this build, `save.json` and `care.json` are copied into the database in one transaction, read back and
 * the marker set; from then on the database is the only live store and the files are never written again (they stay as the
 * rollback copy). Care's records then move into Care's own tables (schema 4, `CareTables`); `live.read`, `write` and the backup
 * snapshot put them back together as `care`, so the app and the backup see the same document. A backup of the live documents is
 * written once a day (`backups/`).
 *
 * If the database cannot be used the app never loses data silently: a corrupt file is moved aside whole and the newest
 * backup is restored, else the untouched legacy files are used; a database written by a newer build is left alone. Only
 * while the database has never been active do the files stay the live store, so the two are never both written.
 */
object LiveStore {
    private const val TAG = "LiveStore"
    private var store: DataStore? = null
    private var gaveUp = false

    @Synchronized
    fun get(context: Context): DataStore? {
        store?.let { return it }
        if (gaveUp) return null
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val backups = LiveBackup(File(app.filesDir, "backups"))
        return try {
            val s = openOrRecover(app, backups, now)
            s.live.activate(read(app, "save.json"), read(app, "care.json"), now)
            File(app.filesDir, ACTIVE_FLAG).writeText("database") // from now on the files are never the live store again
            // Care's records move from their document row into Care's tables, once (REP-014). If it cannot (the read-back differed),
            // nothing changed and the document row stays the live Care save; it is tried again at the next start.
            runCatching { s.live.moveCareToTables(now) }.onFailure { Log.w(TAG, "Care tables not active this run: ${it.javaClass.simpleName}") }
            runCatching { backups.takeIfDue(s.live, now) }.onFailure { Log.w(TAG, "backup failed: ${it.javaClass.simpleName}") }
            store = s
            s
        } catch (t: Throwable) {
            // No document text in the log (privacy): only what failed.
            Log.e(TAG, "the database is not used this run: ${t.javaClass.simpleName}")
            gaveUp = true
            null
        }
    }

    /** The storage for one document; the file only when the database was never active and cannot be opened. */
    fun storage(context: Context, name: String, legacyFile: String): Storage {
        val s = get(context)
        if (s != null) return DatabaseStorage(s.live, name)
        val files = context.applicationContext.filesDir
        // Once the database has been live, the files are stale: using them would lose everything since the cutover and then
        // diverge. Refuse instead; nothing is lost and the database is untouched.
        if (File(files, ACTIVE_FLAG).exists()) return UnavailableStorage
        return FileStorage(File(files, legacyFile))
    }

    private const val ACTIVE_FLAG = "live.active"

    private object UnavailableStorage : Storage {
        private fun fail(): Nothing = throw IllegalStateException("The PokéLife database could not be opened; your data is untouched. Restart the phone or free some storage.")
        override fun load(): String? = fail()
        override fun save(text: String) = fail()
        override fun keepAside(text: String) = fail()
    }

    private fun read(context: Context, file: String): String? = File(context.filesDir, file).takeIf { it.exists() }?.readText()

    private fun openOrRecover(context: Context, backups: LiveBackup, now: Long): DataStore {
        val first = runCatching { AndroidDatabase.open(context).also { check(it.integrityIssues().isEmpty()) { "integrity" } } }
        first.getOrNull()?.let { return it }
        val error = first.exceptionOrNull()!!
        if (error is NewerSchemaException) throw error // a newer build's database: untouched
        if (!looksCorrupt(error)) throw error
        // Move the whole damaged database aside (never delete it), then restore the newest backup or start from the files.
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            File(db.path + suffix).takeIf { it.exists() }?.renameTo(File(db.path + suffix + ".corrupt-$now"))
        }
        val fresh = AndroidDatabase.open(context)
        if (backups.restoreNewest(fresh.live, now)) {
            fresh.live.activate(null, null, now) // the restore is the live data; set the marker without reading the stale files
            Log.w(TAG, "the database was damaged; the newest backup was restored")
        }
        return fresh
    }

    private fun looksCorrupt(t: Throwable): Boolean = generateSequence(t) { it.cause }.any {
        it.javaClass.simpleName.contains("Corrupt") || (it.message ?: "").let { m -> "malformed" in m || "not a database" in m || m == "integrity" }
    }
}
