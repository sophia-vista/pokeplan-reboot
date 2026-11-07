package app.pokeplan

import android.content.Context
import android.util.Log
import app.pokeplan.app.FileStorage
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage
import app.pokeplan.data.AndroidDatabase
import app.pokeplan.data.DataStore
import app.pokeplan.data.KeystoreMintKey
import app.pokeplan.data.LiveBackup
import app.pokeplan.data.LiveDocuments
import app.pokeplan.data.NewerSchemaException
import java.io.File

/** A document of the live save, kept in the database one transaction at a time. */
class DatabaseStorage(private val live: LiveDocuments, private val name: String) : Storage {
    override fun load(): String? = live.read(name)

    /**
     * One transaction. If the phone is out of storage the write is rolled back whole (nothing half saved), the app keeps working from memory
     * and says so ([LiveStore.writeFailed]); the next write that fits saves everything, since each save is the whole document.
     */
    override fun save(text: String) {
        try {
            live.write(name, text, System.currentTimeMillis())
            LiveStore.writeFailed = false
        } catch (e: android.database.sqlite.SQLiteFullException) {
            LiveStore.writeFailed = true
        } catch (e: android.database.sqlite.SQLiteDiskIOException) {
            LiveStore.writeFailed = true
        }
    }
    override fun keepAside(text: String) = live.keepAside(name, text, System.currentTimeMillis())
}

/**
 * The cutover (DATA_AND_MIGRATIONS.md section 12.6). The app and the widgets in this process share one connection. On the
 * first start with this build, `save.json` and `care.json` are copied into the database in one transaction, read back and
 * the marker set; from then on the database is the only live store and the files are never written again (they stay as the
 * rollback copy). Care's records then move into Care's own tables (schema 4, `CareTables`), PLAN's part of the save into PLAN's (schema 5,
 * `PlanTables`) and the player's side (Pokémon, places, Bag, Poké and its ledger) into the shared tables (schema 6, `PokemonTables`); `live.read`, `write` and the backup snapshot put them back together as `care` and `save`, so the app and the backup
 * see the same documents. A backup of the live documents is
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
    private var backupStore: LiveBackup? = null

    /** The last save could not be written because the phone is out of storage; cleared by the next one that is. */
    @Volatile var writeFailed: Boolean = false

    /** The daily backups (the same folder the app writes), once the database is open. */
    fun backups(context: Context): LiveBackup? = backupStore ?: get(context)?.let { backupStore }

    @Synchronized
    fun get(context: Context): DataStore? {
        store?.let { return it }
        if (gaveUp) return null
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val backups = LiveBackup(File(app.filesDir, "backups"))
        return try {
            val s = openOrRecover(app, backups, now)
            s.live.normalizer = GameStore::normalizeDocument
            s.live.activate(read(app, "save.json"), read(app, "care.json"), now)
            File(app.filesDir, ACTIVE_FLAG).writeText("database") // from now on the files are never the live store again
            // Care's records move from their document row into Care's tables, once (REP-014). If it cannot (the read-back differed),
            // nothing changed and the document row stays the live Care save; it is tried again at the next start.
            runCatching { s.live.moveCareToTables(now) }.onFailure { Log.w(TAG, "Care tables not active this run: ${it.javaClass.simpleName}") }
            // The same for PLAN's part of the save (schema 5): its records move into PLAN's tables, the rest of the save stays a row.
            runCatching { s.live.movePlanToTables(now) }.onFailure { Log.w(TAG, "Plan tables not active this run: ${it.javaClass.simpleName}") }
            // HEAL lives in its own tables from the start (schema 7). A heal.json written while the database could not be opened is taken in once, so nothing logged then is lost.
            runCatching {
                read(app, "heal.json")?.let { legacy -> if (!s.healTables.isActive() && s.live.read("heal") == null) s.live.write("heal", legacy, now) }
                s.live.moveHealToTables(now)
            }.onFailure { Log.w(TAG, "Heal tables not active this run: ${it.javaClass.simpleName}") }
            // HOME lives in its own tables from the start (schema 9), like HEAL and unsealed. A home.json written while the database could not be opened is taken in once.
            runCatching {
                read(app, "home.json")?.let { legacy -> if (!s.homeTables.isActive() && s.live.read("home") == null) s.live.write("home", legacy, now) }
                s.live.moveHomeToTables(now)
            }.onFailure { Log.w(TAG, "Home tables not active this run: ${it.javaClass.simpleName}") }
            // MINT lives in its own tables from the start (schema 8), every row sealed with a key that lives in the Android Keystore. If the Keystore cannot give
            // the key, Mint does not start this run (nothing of it is ever written in clear: `mintStorage` then keeps it in memory only), and it is tried again at the next start.
            runCatching {
                s.mintCipher = KeystoreMintKey.cipher()
                s.live.moveMintToTables(now)
            }.onFailure { s.mintCipher = null; Log.w(TAG, "Mint tables not active this run: ${it.javaClass.simpleName}") }
            // And the player's side (schema 6): the Pokémon, their places, the Bag, the Poké balance and its ledger, once PLAN's move has run.
            // The save is checked the way the app checks every save it loads, written as rows, read back and compared; any difference or a
            // save the tables cannot represent changes nothing, the rows stay the live save, and it is tried again at the next start.
            runCatching { s.live.movePokemonToTables(now) }.onFailure { Log.w(TAG, "Pokémon tables not active this run: ${it.javaClass.simpleName}") }
            runCatching { backups.takeIfDue(s.live, now) }.onFailure { Log.w(TAG, "backup failed: ${it.javaClass.simpleName}") }
            backupStore = backups
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
        fresh.live.normalizer = GameStore::normalizeDocument
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
