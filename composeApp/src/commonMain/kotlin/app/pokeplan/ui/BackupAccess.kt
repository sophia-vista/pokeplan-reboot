package app.pokeplan.ui

/** One automatic backup the app took (once a day, the newest three are kept). */
data class BackupEntry(val millis: Long, val bytes: Long)

/** A backup file the player chose, already read and checked whole: what it would put back. Nothing has changed yet. */
data class StagedBackup(val createdAtMillis: Long, val documents: List<String>)

/**
 * Backups of the player's data, where the platform has them (Android). The app keeps its own daily backups; the player can also save one as a
 * file anywhere (the phone's file picker) and restore from one. Restoring is always staged and undoable: the file is checked whole first, then
 * swapped in one step, and the save it replaces is kept aside.
 */
interface BackupAccess {
    fun automatic(): List<BackupEntry>

    /** The phone is short of room: the last backup was skipped, or a save could not be written. The app says so calmly and carries on. */
    fun lowStorage(): Boolean

    /** Takes a backup now. Returns a short note on what happened. */
    fun backupNow(): String

    /** Puts the automatic backup taken at [millis] back. Returns a short note. */
    fun restoreAutomatic(millis: Long): String

    /** Saves a backup (the automatic one at [millis], else a fresh one) where the player chooses, as one file. [onDone] gets a short note. */
    fun saveCopy(millis: Long?, onDone: (String) -> Unit)

    /** Lets the player pick a backup file; it is read and checked whole. [onChosen] gets what it holds, or the reason it was refused. Nothing changes yet. */
    fun chooseFile(onChosen: (StagedBackup?, String?) -> Unit)

    /** Puts the chosen file back (what it replaces is kept aside). Returns a short note. */
    fun applyChosen(): String
}
