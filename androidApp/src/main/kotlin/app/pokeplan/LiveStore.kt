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

class DatabaseStorage(private val live: LiveDocuments, private val name: String) : Storage {
    override val shareKey: Any get() = name

    override fun load(): String? = live.read(name)

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

object LiveStore {
    private const val TAG = "LiveStore"
    private var store: DataStore? = null
    private var gaveUp = false
    private var backupStore: LiveBackup? = null

    @Volatile var writeFailed: Boolean = false

    fun backups(context: Context): LiveBackup? = backupStore ?: get(context)?.let { backupStore }

    @Synchronized
    fun get(context: Context): DataStore? {
        store?.let { return it }
        if (gaveUp) return null
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val choice = recoveryFlag(app)
        if (choice == Recovery.PENDING) return null
        val backups = LiveBackup(
            File(app.filesDir, "backups"), everyMillis = 20 * 60 * 60 * 1000L,
            sealWith = runCatching { KeystoreBackupKey.cipher() }.onFailure { Log.w(TAG, "no backup key this run: ${it.javaClass.simpleName}") }.getOrNull(),
            requireSeal = true, offsetMillis = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() },
        )
        return try {
            val s = openOrRecover(app, backups, now, choice)
            s.live.normalizer = GameStore::normalizeDocument
            s.live.activate(legacy(app, choice, "save.json"), legacy(app, choice, "care.json"), now)
            File(app.filesDir, ACTIVE_FLAG).writeText("database")
            runCatching { s.live.moveCareToTables(now) }.onFailure { Log.w(TAG, "Care tables not active this run: ${it.javaClass.simpleName}") }
            runCatching { s.live.movePlanToTables(now) }.onFailure { Log.w(TAG, "Plan tables not active this run: ${it.javaClass.simpleName}") }
            useKeys(s)
            runCatching {
                legacy(app, choice, "heal.json")?.let { legacy -> if (!s.healTables.isActive() && s.live.read("heal") == null) s.live.write("heal", legacy, now) }
                s.live.moveHealToTables(now)
            }.onFailure { Log.w(TAG, "Heal tables not active this run: ${it.javaClass.simpleName}") }
            val healSealedNow = runCatching { s.healTables.seal(now).also { if (it) s.healTables.scrub() } }
                .onFailure { Log.w(TAG, "Heal rows not sealed this run: ${it.javaClass.simpleName}") }.getOrDefault(false)
            runCatching {
                legacy(app, choice, "home.json")?.let { legacy -> if (!s.baseTables.isActive() && s.live.read("home") == null) s.live.write("home", legacy, now) }
                s.live.moveBaseToTables(now)
            }.onFailure { Log.w(TAG, "Base tables not active this run: ${it.javaClass.simpleName}") }
            runCatching {
                legacy(app, choice, "link.json")?.let { legacy -> if (!s.linkTables.isActive() && s.live.read("link") == null) s.live.write("link", legacy, now) }
                s.live.moveLinkToTables(now)
            }.onFailure { Log.w(TAG, "Link tables not active this run: ${it.javaClass.simpleName}") }
            runCatching { s.live.moveMintToTables(now) }.onFailure { s.mintCipher = null; Log.w(TAG, "Mint tables not active this run: ${it.javaClass.simpleName}") }
            runCatching { s.live.movePokemonToTables(now) }.onFailure { Log.w(TAG, "Pokémon tables not active this run: ${it.javaClass.simpleName}") }
            if (healSealedNow) runCatching { backups.refreshAfterSealing(s.live, now) }.onFailure { Log.w(TAG, "backup refresh failed: ${it.javaClass.simpleName}") }
            // The daily copy is WorkManager's job (BackupWorker.runSoon at start, and its daily periodic run): taking it here
            // would put AES sealing of every document on whichever thread first opened the store, often the main one.
            backupStore = backups
            store = s
            if (choice != null) File(app.filesDir, RECOVERY_FLAG).delete()
            s
        } catch (e: RecoveryPending) {
            Log.w(TAG, "the database was damaged and there is no backup: waiting for the player's choice")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "the database is not used this run: ${t.javaClass.simpleName}")
            gaveUp = true
            null
        }
    }

    @Synchronized
    internal fun forgetForTests() {
        runCatching { store?.close() }
        store = null
        gaveUp = false
        backupStore = null
        writeFailed = false
    }

    fun storage(context: Context, name: String, legacyFile: String): Storage {
        val s = get(context)
        if (s != null) return DatabaseStorage(s.live, name)
        val files = context.applicationContext.filesDir
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

    private fun legacy(context: Context, choice: Recovery?, file: String): String? = if (choice == Recovery.FRESH) null else read(context, file)

    private enum class Recovery(val word: String) { PENDING("pending"), OLDER("older"), FRESH("fresh") }

    private fun recoveryFlag(context: Context): Recovery? =
        File(context.filesDir, RECOVERY_FLAG).takeIf { it.exists() }?.readText()?.trim()?.let { w -> Recovery.entries.firstOrNull { it.word == w } }

    private const val RECOVERY_FLAG = "recovery.choice"

    private class RecoveryPending : RuntimeException()

    fun recoveryPending(context: Context): Boolean { get(context); return recoveryFlag(context.applicationContext) == Recovery.PENDING }

    fun olderFilesMillis(context: Context): Long? =
        listOf("save.json", "care.json").mapNotNull { File(context.applicationContext.filesDir, it).takeIf(File::exists)?.lastModified() }.maxOrNull()

    @Synchronized
    fun resolveRecovery(context: Context, useOlder: Boolean) {
        val app = context.applicationContext
        if (recoveryFlag(app) != Recovery.PENDING) return
        File(app.filesDir, RECOVERY_FLAG).writeText((if (useOlder) Recovery.OLDER else Recovery.FRESH).word)
    }

    private fun openOrRecover(context: Context, backups: LiveBackup, now: Long, choice: Recovery?): DataStore {
        val first = runCatching { AndroidDatabase.open(context).also { check(it.integrityIssues(full = false).isEmpty()) { "integrity" } } }
        first.getOrNull()?.let { return it }
        val error = first.exceptionOrNull()!!
        if (error is NewerSchemaException) throw error
        if (!looksCorrupt(error)) throw error
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            File(db.path + suffix).takeIf { it.exists() }?.renameTo(File(db.path + suffix + ".corrupt-$now"))
        }
        val fresh = AndroidDatabase.open(context)
        fresh.live.normalizer = GameStore::normalizeDocument
        useKeys(fresh)
        if (backups.restoreNewest(fresh.live, now)) {
            fresh.live.activate(null, null, now)
            Log.w(TAG, "the database was damaged; the newest backup was restored")
        } else if (choice == null && File(context.filesDir, ACTIVE_FLAG).exists() && listOf("save.json", "care.json").any { File(context.filesDir, it).exists() }) {
            File(context.filesDir, RECOVERY_FLAG).writeText(Recovery.PENDING.word)
            runCatching { fresh.close() }
            throw RecoveryPending()
        }
        return fresh
    }

    private fun useKeys(s: DataStore) {
        if (s.mintCipher == null) runCatching { s.mintCipher = KeystoreMintKey.cipher() }.onFailure { Log.w(TAG, "no Mint key this run: ${it.javaClass.simpleName}") }
        if (s.healCipher == null) runCatching { s.healCipher = KeystoreHealKey.cipher() }.onFailure { Log.w(TAG, "no Heal key this run: ${it.javaClass.simpleName}") }
    }

    private fun looksCorrupt(t: Throwable): Boolean = generateSequence(t) { it.cause }.any {
        it.javaClass.simpleName.contains("Corrupt") || (it.message ?: "").let { m -> "malformed" in m || "not a database" in m || m == "integrity" }
    }
}
