package app.pokeplan.ui

data class BackupEntry(val millis: Long, val bytes: Long)

data class StagedBackup(val createdAtMillis: Long, val documents: List<String>, val needsPassphrase: Boolean = false)

data class OffPhoneState(val folderName: String, val lastCopyMillis: Long?, val problem: String?)

interface BackupAccess {
    val minPassphrase: Int get() = 10

    fun automatic(): List<BackupEntry>

    fun lowStorage(): Boolean

    fun backupNow(): String

    fun restoreAutomatic(millis: Long): String

    fun saveCopy(millis: Long?, passphrase: String? = null, onDone: (String) -> Unit)

    fun eraseLeftovers(): String = ""

    fun chooseFile(onChosen: (StagedBackup?, String?) -> Unit)

    fun applyChosen(passphrase: String? = null, onDone: (String) -> Unit)

    val hasOffPhone: Boolean get() = false

    fun offPhone(): OffPhoneState? = null

    fun setUpOffPhone(passphrase: String, onDone: (String) -> Unit) = onDone("")

    fun backUpOffPhoneNow(onDone: (String) -> Unit) = onDone("")

    fun stopOffPhone(): String = ""

    fun chooseOffPhoneCopy(onChosen: (StagedBackup?, String?) -> Unit) = onChosen(null, "")
}
