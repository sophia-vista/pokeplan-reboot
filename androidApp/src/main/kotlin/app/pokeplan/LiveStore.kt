package app.pokeplan

import android.content.Context
import android.util.Log
import app.pokeplan.app.storage.FileStorage
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Storage
import app.pokeplan.data.AndroidDatabase
import app.pokeplan.data.DataStore
import app.pokeplan.data.KeystoreBackupKey
import app.pokeplan.data.KeystoreHealKey
import app.pokeplan.data.KeystoreMintKey
import app.pokeplan.data.LiveBackup
import app.pokeplan.data.LiveDocuments
import app.pokeplan.data.NewerSchemaException
import java.io.File

/** A document of the live save, kept in the database one transaction at a time. */
class DatabaseStorage(private val live: LiveDocuments, private val name: String) : Storage {
    // The app, the widgets and the reminders each open their own store on this document, in one process.
    override val shareKey: Any get() = name

    override fun load(): String? = live.read(name)

    /**
     * One transaction. If the phone is out of storage the write is rolled back whole (nothing half saved), the app keeps working from memory
     * and says so ([LiveStore.writeFailed]); the next write that fits saves everything, since each save is the whole document.
     */
    override fun save(text: String) { saved(text) }

    override fun saved(text: String): Boolean =
        try {
            live.write(name, text, System.currentTimeMillis())
            LiveStore.writeFailed = false
            true
        } catch (e: android.database.sqlite.SQLiteFullException) {
            LiveStore.writeFailed = true
            false
        } catch (e: android.database.sqlite.SQLiteDiskIOException) {
            LiveStore.writeFailed = true
            false
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
 * written once a day (`backups/`), sealed with a Keystore key.
 *
 * If the database cannot be used the app never loses data silently: a corrupt file is moved aside whole and the newest
 * backup is restored; with no backup the player is asked (REP-026 c, [recoveryPending]) whether to use the old files, which are as old as the
 * cutover to the database, or to start fresh; a database written by a newer build is left alone. Only while the database has never been
 * active do the files stay the live store, so the two are never both written.
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
        // A damaged database with no backup waits for the player's answer: nothing opens, widgets and receivers get no store until it is given.
        val choice = recoveryFlag(app)
        if (choice == Recovery.PENDING) return null
        // Every file of a daily backup is sealed with a Keystore key (and no backup is taken without it). 20 hours, not 24, so the daily job never skips a day by being a little late.
        val backups = LiveBackup(
            File(app.filesDir, "backups"), everyMillis = 20 * 60 * 60 * 1000L,
            sealWith = runCatching { KeystoreBackupKey.cipher() }.onFailure { Log.w(TAG, "no backup key this run: ${it.javaClass.simpleName}") }.getOrNull(),
            requireSeal = true, offsetMillis = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() },
        )
        return try {
            val s = openOrRecover(app, backups, now, choice)
            s.live.normalizer = GameStore::normalizeDocument
            s.live.activate(legacy(app, choice, "save.json"), legacy(app, choice, "care.json"), now)
            File(app.filesDir, ACTIVE_FLAG).writeText("database") // from now on the files are never the live store again
            // Care's records move from their document row into Care's tables, once (REP-014). If it cannot (the read-back differed),
            // nothing changed and the document row stays the live Care save; it is tried again at the next start.
            runCatching { s.live.moveCareToTables(now) }.onFailure { Log.w(TAG, "Care tables not active this run: ${it.javaClass.simpleName}") }
            // The same for PLAN's part of the save (schema 5): its records move into PLAN's tables, the rest of the save stays a row.
            runCatching { s.live.movePlanToTables(now) }.onFailure { Log.w(TAG, "Plan tables not active this run: ${it.javaClass.simpleName}") }
            // HEAL lives in its own tables from the start (schema 7). A heal.json written while the database could not be opened is taken in once, so nothing logged then is lost.
            // Since 0.154.0 its rows are sealed with a Keystore key too: the first start with the key rewrites every row as sealed text (read back, one transaction) and
            // forgets what the clear rows left in the file. With no key the rows are used in clear for that run and sealed at the next start that has it; once sealed, no key means no access.
            useKeys(s)
            runCatching {
                legacy(app, choice, "heal.json")?.let { legacy -> if (!s.healTables.isActive() && s.live.read("heal") == null) s.live.write("heal", legacy, now) }
                s.live.moveHealToTables(now)
            }.onFailure { Log.w(TAG, "Heal tables not active this run: ${it.javaClass.simpleName}") }
            val healSealedNow = runCatching { s.healTables.seal(now).also { if (it) s.healTables.scrub() } }
                .onFailure { Log.w(TAG, "Heal rows not sealed this run: ${it.javaClass.simpleName}") }.getOrDefault(false)
            // HOME lives in its own tables from the start (schema 9), like HEAL and unsealed. A home.json written while the database could not be opened is taken in once.
            runCatching {
                legacy(app, choice, "home.json")?.let { legacy -> if (!s.baseTables.isActive() && s.live.read("home") == null) s.live.write("home", legacy, now) }
                s.live.moveBaseToTables(now)
            }.onFailure { Log.w(TAG, "Base tables not active this run: ${it.javaClass.simpleName}") }
            // LINK lives in its own tables from the start (schema 10), like HOME and unsealed. A link.json written while the database could not be opened is taken in once.
            runCatching {
                legacy(app, choice, "link.json")?.let { legacy -> if (!s.linkTables.isActive() && s.live.read("link") == null) s.live.write("link", legacy, now) }
                s.live.moveLinkToTables(now)
            }.onFailure { Log.w(TAG, "Link tables not active this run: ${it.javaClass.simpleName}") }
            // MINT lives in its own tables from the start (schema 8), every row sealed with a key that lives in the Android Keystore. If the Keystore cannot give
            // the key, Mint does not start this run (nothing of it is ever written in clear: `mintStorage` then keeps it in memory only), and it is tried again at the next start.
            runCatching { s.live.moveMintToTables(now) }.onFailure { s.mintCipher = null; Log.w(TAG, "Mint tables not active this run: ${it.javaClass.simpleName}") }
            // And the player's side (schema 6): the Pokémon, their places, the Bag, the Poké balance and its ledger, once PLAN's move has run.
            // The save is checked the way the app checks every save it loads, written as rows, read back and compared; any difference or a
            // save the tables cannot represent changes nothing, the rows stay the live save, and it is tried again at the next start.
            runCatching { s.live.movePokemonToTables(now) }.onFailure { Log.w(TAG, "Pokémon tables not active this run: ${it.javaClass.simpleName}") }
            // The daily copies made before Heal was sealed hold it in clear: one sealed copy replaces them, once all the moves above have run.
            if (healSealedNow) runCatching { backups.refreshAfterSealing(s.live, now) }.onFailure { Log.w(TAG, "backup refresh failed: ${it.javaClass.simpleName}") }
            runCatching { backups.takeIfDue(s.live, now) }.onFailure { Log.w(TAG, "backup failed: ${it.javaClass.simpleName}") }
            backupStore = backups
            store = s
            if (choice != null) File(app.filesDir, RECOVERY_FLAG).delete() // the answer has been used
            s
        } catch (e: RecoveryPending) {
            Log.w(TAG, "the database was damaged and there is no backup: waiting for the player's choice")
            null
        } catch (t: Throwable) {
            // No document text in the log (privacy): only what failed.
            Log.e(TAG, "the database is not used this run: ${t.javaClass.simpleName}")
            gaveUp = true
            null
        }
    }

    /**
     * What a process death does to this object, for the Android tests (REP-033): the connection is closed and the next [get] opens and recovers the database
     * again as a fresh start would. Never called by the app.
     */
    @Synchronized
    internal fun forgetForTests() {
        runCatching { store?.close() }
        store = null
        gaveUp = false
        backupStore = null
        writeFailed = false
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

    /** An old file, unless the player chose to start fresh. */
    private fun legacy(context: Context, choice: Recovery?, file: String): String? = if (choice == Recovery.FRESH) null else read(context, file)

    /** What a damaged database with no backup is waiting for or has been told (REP-026 c): a one-word file, so the answer survives a restart. */
    private enum class Recovery(val word: String) { PENDING("pending"), OLDER("older"), FRESH("fresh") }

    private fun recoveryFlag(context: Context): Recovery? =
        File(context.filesDir, RECOVERY_FLAG).takeIf { it.exists() }?.readText()?.trim()?.let { w -> Recovery.entries.firstOrNull { it.word == w } }

    private const val RECOVERY_FLAG = "recovery.choice"

    /** The database was damaged and nothing was restored: [get] gives no store until [resolveRecovery]. */
    private class RecoveryPending : RuntimeException()

    /**
     * True when the player must decide before anything opens (REP-026 c): the database was damaged, there is no backup to put back, and the old
     * `save.json` and `care.json` are still on the phone but are only as new as the cutover to the database. Opens the database to find out, so call
     * it before any store is built; it costs nothing when all is well.
     */
    fun recoveryPending(context: Context): Boolean { get(context); return recoveryFlag(context.applicationContext) == Recovery.PENDING }

    /** When the old files were last written, for the choice screen to say how old they are. */
    fun olderFilesMillis(context: Context): Long? =
        listOf("save.json", "care.json").mapNotNull { File(context.applicationContext.filesDir, it).takeIf(File::exists)?.lastModified() }.maxOrNull()

    /**
     * The player's answer: [useOlder] opens the database from the old files; otherwise it starts empty. The damaged database stays aside either way,
     * and the old files are never deleted here. Saved first, then used by the next [get].
     */
    @Synchronized
    fun resolveRecovery(context: Context, useOlder: Boolean) {
        val app = context.applicationContext
        if (recoveryFlag(app) != Recovery.PENDING) return
        File(app.filesDir, RECOVERY_FLAG).writeText((if (useOlder) Recovery.OLDER else Recovery.FRESH).word)
    }

    private fun openOrRecover(context: Context, backups: LiveBackup, now: Long, choice: Recovery?): DataStore {
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
        useKeys(fresh) // the newest backup holds Mint and Heal sealed: the keys must be there before it is put back, or they would be set aside as locked
        if (backups.restoreNewest(fresh.live, now)) {
            fresh.live.activate(null, null, now) // the restore is the live data; set the marker without reading the stale files
            Log.w(TAG, "the database was damaged; the newest backup was restored")
        } else if (choice == null && File(context.filesDir, ACTIVE_FLAG).exists() && listOf("save.json", "care.json").any { File(context.filesDir, it).exists() }) {
            // Nothing to restore. The old files are as old as the cutover, so using them silently would undo everything since: ask first.
            File(context.filesDir, RECOVERY_FLAG).writeText(Recovery.PENDING.word)
            runCatching { fresh.close() }
            throw RecoveryPending()
        }
        return fresh
    }

    /** Gives [s] the Keystore keys of Mint and of Heal, each only if it does not have it yet; a key the Keystore cannot give leaves that module without one (see their callers). */
    private fun useKeys(s: DataStore) {
        if (s.mintCipher == null) runCatching { s.mintCipher = KeystoreMintKey.cipher() }.onFailure { Log.w(TAG, "no Mint key this run: ${it.javaClass.simpleName}") }
        if (s.healCipher == null) runCatching { s.healCipher = KeystoreHealKey.cipher() }.onFailure { Log.w(TAG, "no Heal key this run: ${it.javaClass.simpleName}") }
    }

    private fun looksCorrupt(t: Throwable): Boolean = generateSequence(t) { it.cause }.any {
        it.javaClass.simpleName.contains("Corrupt") || (it.message ?: "").let { m -> "malformed" in m || "not a database" in m || m == "integrity" }
    }
}
