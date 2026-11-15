package app.pokeplan.ui

/** One automatic backup the app took (once a day, the newest three are kept). */
data class BackupEntry(val millis: Long, val bytes: Long)

/** A backup file the player chose, already read and checked whole: what it would put back. Nothing has changed yet. */
data class StagedBackup(val createdAtMillis: Long, val documents: List<String>, val needsPassphrase: Boolean = false)

/** The automatic copy to a folder the player chose (REP-034): which folder, when the last good copy was made, and the last thing that went wrong, if anything. */
data class OffPhoneState(val folderName: String, val lastCopyMillis: Long?, val problem: String?)

/**
 * Backups of the player's data, where the platform has them (Android). The app keeps its own daily backups; the player can also save one as a
 * file anywhere (the phone's file picker) and restore from one. Restoring is always staged and undoable: the file is checked whole first, then
 * swapped in one step, and the save it replaces is kept aside.
 */
interface BackupAccess {
    /** The fewest characters a backup passphrase may have. */
    val minPassphrase: Int get() = 10

    fun automatic(): List<BackupEntry>

    /** The phone is short of room: the last backup was skipped, or a save could not be written. The app says so calmly and carries on. */
    fun lowStorage(): Boolean

    /** Takes a backup now. Returns a short note on what happened. */
    fun backupNow(): String

    /** Puts the automatic backup taken at [millis] back. Returns a short note. */
    fun restoreAutomatic(millis: Long): String

    /**
     * Saves a backup (the automatic one at [millis], else a fresh one) where the player chooses, as one file. [onDone] gets a short note.
     * With a [passphrase] Mint and Heal travel sealed under it, so the file opens on any phone that is given the passphrase; without one they are sealed to this
     * phone's keys and the file opens only here. The passphrase is used once and kept nowhere.
     */
    fun saveCopy(millis: Long?, passphrase: String? = null, onDone: (String) -> Unit)

    /**
     * What a full-device erase must also remove, beyond what the stores write blank: the daily backups, the copies a migration or a restore kept
     * aside, held reminders, and the old `save.json` and `care.json` rollback files. Returns a short note. Nothing to do where the platform has none.
     */
    fun eraseLeftovers(): String = ""

    /** Lets the player pick a backup file; it is read and checked whole. [onChosen] gets what it holds, or the reason it was refused. Nothing changes yet. */
    fun chooseFile(onChosen: (StagedBackup?, String?) -> Unit)

    /** Puts the chosen file back (what it replaces is kept aside); [passphrase] opens a portable Mint and Heal. [onDone] gets a short note; a wrong passphrase changes nothing. */
    fun applyChosen(passphrase: String? = null, onDone: (String) -> Unit)

    /** Automatic copies to a folder (Android only): true where the platform offers them. */
    val hasOffPhone: Boolean get() = false

    /** The automatic copy to a folder, or null when it is not set up. */
    fun offPhone(): OffPhoneState? = null

    /**
     * Sets the copies up: opens the phone's folder picker, then keeps the folder and the [passphrase] (sealed on this phone only; Mint and Heal in every copy are sealed
     * under it) and makes the first copy. [onDone] gets a short note. The passphrase is what opens the copies on another phone, so the screen says to remember it.
     */
    fun setUpOffPhone(passphrase: String, onDone: (String) -> Unit) = onDone("")

    /** Makes a copy to the folder now. [onDone] gets a short note. */
    fun backUpOffPhoneNow(onDone: (String) -> Unit) = onDone("")

    /** Stops the copies and forgets the folder and passphrase on this phone; what is already in the folder stays. */
    fun stopOffPhone(): String = ""

    /** Reads and checks the newest copy in the folder, like [chooseFile]; [applyChosen] then puts it back. Nothing changes yet. */
    fun chooseOffPhoneCopy(onChosen: (StagedBackup?, String?) -> Unit) = onChosen(null, "")
}
