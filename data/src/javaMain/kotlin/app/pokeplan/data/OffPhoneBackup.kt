package app.pokeplan.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface BackupFolder {
    fun names(): List<String>

    fun write(name: String, text: String): Boolean

    fun read(name: String): String?

    fun delete(name: String)
}

class OffPhoneBackup(private val folder: BackupFolder, private val keep: Int = KEEP, private val sealer: PassphraseSealer = Pbkdf2Sealer()) {
    sealed interface Result {
        data class Done(val millis: Long) : Result
        data class Failed(val why: String) : Result
    }

    data class Copy(val millis: Long, val name: String)

    fun copies(): List<Copy> = folder.names().mapNotNull(::parse).sortedByDescending { it.millis }

    fun lastMillis(): Long? = copies().firstOrNull()?.millis

    fun due(nowMillis: Long, everyMillis: Long = EVERY): Boolean = lastMillis()?.let { nowMillis - it >= everyMillis } ?: true

    fun run(live: LiveDocuments, devices: Map<String, RowCipher>, passphrase: String, schema: Int, nowMillis: Long): Result {
        PassphraseSealer.refusal(passphrase)?.let { return Result.Failed(it) }
        val docs = try { live.snapshot() } catch (e: Exception) { return Result.Failed("The data could not be read, so no copy was made.") }
        if (docs.isEmpty()) return Result.Failed("There is nothing to copy yet.")
        val portable = BackupBundle.portable(docs, devices, passphrase, sealer)
            ?: return Result.Failed("Mint or Heal could not be opened on this phone, so no copy was made.")
        val text = BackupBundle.encode(portable, schema, nowMillis)
        val name = nameOf(nowMillis)
        if (name in folder.names()) return Result.Done(nowMillis)
        val written = try { folder.write(name, text) } catch (e: Exception) { false }
        if (!written) { runCatching { folder.delete(name) }; return Result.Failed("The folder did not accept the copy. Check that it is still there and has room.") }
        val back = try { folder.read(name) } catch (e: Exception) { null }
        if (back != text || BackupBundle.stage(back, schema) !is BackupBundle.Staged.Ready) {
            runCatching { folder.delete(name) }
            return Result.Failed("The copy could not be checked after it was written, so it was removed. Earlier copies are untouched.")
        }
        copies().drop(keep).forEach { runCatching { folder.delete(it.name) } }
        return Result.Done(nowMillis)
    }

    fun read(copy: Copy): String? = try { folder.read(copy.name) } catch (e: Exception) { null }

    companion object {
        const val KEEP = 7
        const val EVERY = 20 * 60 * 60 * 1000L
        const val STALE = 3 * 24 * 60 * 60 * 1000L

        private const val PREFIX = "pokelife-backup-"
        private const val SUFFIX = ".json"
        private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

        fun nameOf(millis: Long): String = PREFIX + day.format(Instant.ofEpochMilli(millis)) + "-" + millis + SUFFIX

        fun parse(name: String): Copy? {
            if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
            val millis = name.removePrefix(PREFIX).removeSuffix(SUFFIX).substringAfterLast('-').toLongOrNull() ?: return null
            return Copy(millis, name).takeIf { nameOf(millis) == name }
        }
    }
}
