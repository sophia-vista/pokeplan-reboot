package app.pokeplan.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A folder the copies go to: a folder on the JVM in tests, a folder the player picked once on Android (the Storage Access Framework). Names only, no subfolders. */
interface BackupFolder {
    fun names(): List<String>

    /** Writes a whole new file called [name]; false if it could not. [name] never exists yet. */
    fun write(name: String, text: String): Boolean

    fun read(name: String): String?

    fun delete(name: String)
}

/**
 * Automatic backup off the phone (REP-034, `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 10.7): one [BackupBundle] file per copy in a [BackupFolder] the player
 * chose once (typically a folder a cloud app syncs), Mint and Heal sealed under the player's passphrase because the phone's Keystore keys do not travel.
 *
 * A copy is a new file each time (`pokelife-backup-<date>-<millis>.json`), so a copy that fails never touches an earlier one. It is read back and
 * staged before it counts: a file that fails the check is deleted and reported. Only after a copy is verified are the oldest beyond [keep] removed, and
 * only files with this app's own names are ever listed or deleted, so nothing else in the folder is touched.
 */
class OffPhoneBackup(private val folder: BackupFolder, private val keep: Int = KEEP, private val sealer: PassphraseSealer = Pbkdf2Sealer()) {
    sealed interface Result {
        data class Done(val millis: Long) : Result
        /** Not made; [why] is a calm sentence for the player. Earlier copies are untouched. */
        data class Failed(val why: String) : Result
    }

    /** One copy in the folder, when it was taken (from its name). */
    data class Copy(val millis: Long, val name: String)

    /** The copies in the folder, newest first. */
    fun copies(): List<Copy> = folder.names().mapNotNull(::parse).sortedByDescending { it.millis }

    fun lastMillis(): Long? = copies().firstOrNull()?.millis

    /** True when there is no copy yet or the newest is older than [everyMillis]. */
    fun due(nowMillis: Long, everyMillis: Long = EVERY): Boolean = lastMillis()?.let { nowMillis - it >= everyMillis } ?: true

    /**
     * Makes one copy of [live] and removes the oldest beyond [keep]. [devices] are the phone's Mint and Heal ciphers (their rows come out sealed to this phone and
     * are opened to be sealed again under [passphrase]); null from [BackupBundle.portable] means one cannot be opened here, and nothing is written.
     */
    fun run(live: LiveDocuments, devices: Map<String, RowCipher>, passphrase: String, schema: Int, nowMillis: Long): Result {
        PassphraseSealer.refusal(passphrase)?.let { return Result.Failed(it) }
        val docs = try { live.snapshot() } catch (e: Exception) { return Result.Failed("The data could not be read, so no copy was made.") }
        if (docs.isEmpty()) return Result.Failed("There is nothing to copy yet.")
        val portable = BackupBundle.portable(docs, devices, passphrase, sealer)
            ?: return Result.Failed("Mint or Heal could not be opened on this phone, so no copy was made.")
        val text = BackupBundle.encode(portable, schema, nowMillis)
        val name = nameOf(nowMillis)
        if (name in folder.names()) return Result.Done(nowMillis) // the same moment twice: the copy is already there
        val written = try { folder.write(name, text) } catch (e: Exception) { false }
        if (!written) { runCatching { folder.delete(name) }; return Result.Failed("The folder did not accept the copy. Check that it is still there and has room.") }
        // Read it back as a restore would: a file that is cut short or changed on the way never counts.
        val back = try { folder.read(name) } catch (e: Exception) { null }
        if (back != text || BackupBundle.stage(back, schema) !is BackupBundle.Staged.Ready) {
            runCatching { folder.delete(name) }
            return Result.Failed("The copy could not be checked after it was written, so it was removed. Earlier copies are untouched.")
        }
        copies().drop(keep).forEach { runCatching { folder.delete(it.name) } }
        return Result.Done(nowMillis)
    }

    /** The text of a copy, for a restore to stage; null if it is gone or unreadable. */
    fun read(copy: Copy): String? = try { folder.read(copy.name) } catch (e: Exception) { null }

    companion object {
        /** How many copies stay in the folder. */
        const val KEEP = 7
        /** A copy is made when the newest is this old (20 hours, like the phone's own daily backup, so a late job never skips a day). */
        const val EVERY = 20 * 60 * 60 * 1000L
        /** After this long without a copy the app says so, quietly. */
        const val STALE = 3 * 24 * 60 * 60 * 1000L

        private const val PREFIX = "pokelife-backup-"
        private const val SUFFIX = ".json"
        private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

        fun nameOf(millis: Long): String = PREFIX + day.format(Instant.ofEpochMilli(millis)) + "-" + millis + SUFFIX

        /** Only names this app wrote: the folder may hold anything else. */
        fun parse(name: String): Copy? {
            if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
            val millis = name.removePrefix(PREFIX).removeSuffix(SUFFIX).substringAfterLast('-').toLongOrNull() ?: return null
            return Copy(millis, name).takeIf { nameOf(millis) == name }
        }
    }
}
