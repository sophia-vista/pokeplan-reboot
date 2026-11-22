package app.pokeplan

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.data.BackupBundle
import app.pokeplan.data.DataStore
import app.pokeplan.data.LiveDocuments
import app.pokeplan.data.PassphraseSealer
import app.pokeplan.data.Pbkdf2Sealer
import app.pokeplan.ui.BackupAccess
import app.pokeplan.ui.BackupEntry
import app.pokeplan.ui.OffPhoneState
import app.pokeplan.ui.StagedBackup

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
        stage(text, done)
    }

    private fun stage(text: String, done: (StagedBackup?, String?) -> Unit) {
        when (val result = BackupBundle.stage(text, DataStore.SCHEMA_VERSION)) {
            is BackupBundle.Staged.Ready -> { staged = result; done(StagedBackup(result.createdAtMillis, result.documents.keys.sorted(), result.needsPassphrase), null) }
            is BackupBundle.Staged.Refused -> done(null, result.reason)
        }
    }

    private var passphraseForFolder: String? = null
    private var onFolder: ((String) -> Unit)? = null

    private val pickFolder = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val pass = passphraseForFolder; val done = onFolder; passphraseForFolder = null; onFolder = null
        if (done == null) return@registerForActivityResult
        if (uri == null || pass == null) { done("No folder was chosen."); return@registerForActivityResult }
        runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure { done("That folder cannot be kept for later. Choose another one."); return@registerForActivityResult }
        val previous = OffPhone.treeOf(activity)
        Thread {
            val note = if (!OffPhone.configure(activity, uri, pass)) "This phone could not keep the passphrase safely, so the copies were not set up." else OffPhone.run(activity, force = true)
            if (previous != null && previous != uri) runCatching { activity.contentResolver.releasePersistableUriPermission(previous, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            activity.runOnUiThread { done(note) }
        }.start()
    }

    override val hasOffPhone: Boolean get() = true

    override fun offPhone(): OffPhoneState? = OffPhone.status(activity)?.let { OffPhoneState(it.folderName, it.lastMillis, it.problem) }

    override fun setUpOffPhone(passphrase: String, onDone: (String) -> Unit) {
        PassphraseSealer.refusal(passphrase)?.let { return onDone(it) }
        passphraseForFolder = passphrase; onFolder = onDone
        pickFolder.launch(null)
    }

    override fun backUpOffPhoneNow(onDone: (String) -> Unit) {
        Thread { val note = OffPhone.run(activity, force = true); activity.runOnUiThread { onDone(note) } }.start()
    }

    override fun stopOffPhone(): String { OffPhone.forget(activity); return "Stopped. The copies already in the folder stay there." }

    override fun chooseOffPhoneCopy(onChosen: (StagedBackup?, String?) -> Unit) {
        staged = null
        Thread {
            val (text, why) = OffPhone.newestCopy(activity)
            activity.runOnUiThread { if (text == null) onChosen(null, why) else stage(text, onChosen) }
        }.start()
    }

    private val store: DataStore? get() = LiveStore.get(activity)
    private val backups get() = LiveStore.backups(activity)

    override fun automatic(): List<BackupEntry> = backups?.list()?.map { BackupEntry(it.millis, it.bytes) }.orEmpty()

    override fun eraseLeftovers(): String = runCatching {
        store?.live?.eraseKeptCopiesAndHolds()
        backups?.deleteAll()
        DeviceReset.clear(activity)
        "The daily backups, kept-aside copies, old save files, launcher layout and reminders were removed too."
    }.getOrElse { Log.w(tag, "erase leftovers failed: ${it.javaClass.simpleName}"); "Some backups could not be removed." }

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
        return runCatching { if (backups?.restore(s.live, millis, System.currentTimeMillis()) == true) restoredNote(backups?.leftByLastRestore.orEmpty()) else "That backup is gone." }
            .getOrElse { Log.w(tag, "restore failed: ${it.javaClass.simpleName}"); "Could not restore it; nothing was changed." }
    }

    override fun saveCopy(millis: Long?, passphrase: String?, onDone: (String) -> Unit) {
        val s = store ?: return onDone("The data could not be opened.")
        val keys = listOfNotNull(s.mintCipher?.let { LiveDocuments.MINT to it }, s.healCipher?.let { LiveDocuments.HEAL to it }).toMap()
        val sealer = Pbkdf2Sealer()
        Thread {
            val note = runCatching {
                val docs = (if (millis != null) backups?.documentsOf(millis) else s.live.snapshot()) ?: return@runCatching "That backup is gone." to null
                val made = if (passphrase == null) docs else {
                    app.pokeplan.data.PassphraseSealer.refusal(passphrase)?.let { return@runCatching it to null }
                    BackupBundle.portable(docs, keys, passphrase, sealer) ?: return@runCatching "Mint or Heal in that backup was sealed on another phone, or its key is not open on this run, so this phone cannot open it to re-seal it." to null
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
                else restoredNote(s.live.replaceAll(docs, System.currentTimeMillis()))
            }.getOrElse { Log.w(tag, "restore failed: ${it.javaClass.simpleName}"); "Could not restore it; nothing was changed." }
            activity.runOnUiThread { if (note.startsWith("Restored")) staged = null; onDone(note) }
        }.start()
    }

    private fun restoredNote(left: List<String>): String {
        val names = left.map { if (it == LiveDocuments.SAVE) "Plan and Pokémon" else it.replaceFirstChar(Char::uppercase) }
        return "Restored. What was here is kept aside." + if (names.isEmpty()) "" else " Not in this backup, so left as ${if (names.size == 1) "it was" else "they were"}: ${names.joinToString(", ")}."
    }

    private companion object { const val MAX_BYTES = 64 * 1024 * 1024 }
}
