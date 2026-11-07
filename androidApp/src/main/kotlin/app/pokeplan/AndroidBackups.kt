package app.pokeplan

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.data.BackupBundle
import app.pokeplan.data.DataStore
import app.pokeplan.data.Pbkdf2Sealer
import app.pokeplan.ui.BackupAccess
import app.pokeplan.ui.BackupEntry
import app.pokeplan.ui.StagedBackup

/**
 * Backups for the player (REP-008, DATA_AND_MIGRATIONS.md section 10): the app's daily copies, a copy saved anywhere as one file through the
 * phone's file picker, and restoring either. Created in `onCreate`, before the activity starts, because the pickers are registered here.
 *
 * A chosen file is read whole and checked ([BackupBundle.stage]) before the player is asked anything, then swapped in by one transaction that
 * keeps the documents it replaces ([app.pokeplan.data.LiveDocuments.replaceAll]); nothing is changed by a file that fails a check.
 */
class AndroidBackups(private val activity: ComponentActivity) : BackupAccess {
    private val tag = "AndroidBackups"
    private var pendingText: String? = null
    private var onSaved: ((String) -> Unit)? = null
    private var staged: BackupBundle.Staged.Ready? = null
    private var onChosen: ((StagedBackup?, String?) -> Unit)? = null

    private val create = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val text = pendingText; val done = onSaved; pendingText = null; onSaved = null
        if (uri == null || text == null) { done?.invoke("Nothing was saved."); return@registerForActivityResult }
        done?.invoke(runCatching { activity.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.encodeToByteArray()) }; "Saved." }.getOrElse { "Could not save the file." })
    }

    private val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val done = onChosen; onChosen = null; staged = null
        if (uri == null || done == null) return@registerForActivityResult
        val text = runCatching {
            activity.contentResolver.openInputStream(uri)!!.use { input ->
                val bytes = input.readNBytes(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) null else bytes.decodeToString()
            }
        }.getOrNull()
        if (text == null) { done(null, "That file is too big to be a backup, or could not be read."); return@registerForActivityResult }
        when (val result = BackupBundle.stage(text, DataStore.SCHEMA_VERSION)) {
            is BackupBundle.Staged.Ready -> { staged = result; done(StagedBackup(result.createdAtMillis, result.documents.keys.sorted(), result.needsPassphrase), null) }
            is BackupBundle.Staged.Refused -> done(null, result.reason)
        }
    }

    private val store: DataStore? get() = LiveStore.get(activity)
    private val backups get() = LiveStore.backups(activity)

    override fun automatic(): List<BackupEntry> = backups?.list()?.map { BackupEntry(it.millis, it.bytes) }.orEmpty()

    override fun lowStorage(): Boolean = backups?.lowStorage == true || LiveStore.writeFailed

    override fun backupNow(): String {
        val s = store ?: return "The data could not be opened, so nothing was backed up."
        val b = backups ?: return "No backup folder."
        return runCatching {
            if (b.take(s.live, System.currentTimeMillis()) != null) "Backed up." else if (b.lowStorage) "Not enough storage for a backup. Free some space and try again." else "Nothing to back up yet."
        }.getOrElse { Log.w(tag, "backup failed: ${it.javaClass.simpleName}"); "The backup could not be written." }
    }

    override fun restoreAutomatic(millis: Long): String {
        val s = store ?: return "The data could not be opened, so nothing was restored."
        return runCatching { if (backups?.restore(s.live, millis, System.currentTimeMillis()) == true) "Restored. What was here is kept aside." else "That backup is gone." }
            .getOrElse { Log.w(tag, "restore failed: ${it.javaClass.simpleName}"); "Could not restore it; nothing was changed." }
    }

    override fun saveCopy(millis: Long?, passphrase: String?, onDone: (String) -> Unit) {
        val s = store ?: return onDone("The data could not be opened.")
        val key = s.mintCipher
        val sealer = Pbkdf2Sealer()
        // Stretching a passphrase takes a moment on a phone, so it is done off the main thread; the picker opens when it is ready.
        Thread {
            val note = runCatching {
                val docs = (if (millis != null) backups?.documentsOf(millis) else s.live.snapshot()) ?: return@runCatching "That backup is gone." to null
                val made = if (passphrase == null) docs else {
                    app.pokeplan.data.PassphraseSealer.refusal(passphrase)?.let { return@runCatching it to null }
                    if (key == null) return@runCatching "Mint is not open on this run, so it cannot be sealed. Close the app and open it again." to null
                    BackupBundle.portable(docs, key, passphrase, sealer) ?: return@runCatching "Mint in that backup was sealed on another phone, so this phone cannot open it to re-seal it." to null
                }
                null to BackupBundle.encode(made, DataStore.SCHEMA_VERSION, millis ?: System.currentTimeMillis())
            }.getOrElse { Log.w(tag, "copy failed: ${it.javaClass.simpleName}"); "The copy could not be made." to null }
            activity.runOnUiThread {
                val text = note.second
                if (text == null) onDone(note.first ?: "The copy could not be made.")
                else { pendingText = text; onSaved = onDone; create.launch("pokelife-backup-${java.time.LocalDate.now()}.json") }
            }
        }.start()
    }

    override fun chooseFile(onChosen: (StagedBackup?, String?) -> Unit) {
        this.onChosen = onChosen
        open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
    }

    override fun applyChosen(passphrase: String?, onDone: (String) -> Unit) {
        val ready = staged ?: return onDone("Choose a file first.")
        val s = store ?: return onDone("The data could not be opened, so nothing was restored.")
        Thread {
            val note = runCatching {
                val docs = BackupBundle.unlock(ready, passphrase, Pbkdf2Sealer())
                if (docs == null) "That passphrase does not open this backup. Nothing was changed."
                else { s.live.replaceAll(docs, System.currentTimeMillis()); "Restored. What was here is kept aside." }
            }.getOrElse { Log.w(tag, "restore failed: ${it.javaClass.simpleName}"); "Could not restore it; nothing was changed." }
            activity.runOnUiThread { if (note.startsWith("Restored")) staged = null; onDone(note) }
        }.start()
    }

    private companion object { const val MAX_BYTES = 64 * 1024 * 1024 }
}
