package app.pokeplan.data

import java.io.File

/**
 * Backups of the live documents as plain files, one folder per backup (`<millis>/save.json`, `care.json`, then a `DONE`
 * marker written last, so a folder cut short is never used). The files are the same JSON the app has always saved, so the
 * newest backup is also the rollback copy for an older build: put its `save.json` and `care.json` back in the app's files.
 * Each backup reads the database in one transaction, so it is consistent however the app is writing at the time.
 *
 * Which backups stay is [BackupRetention] (seven daily and four weekly), or simply the newest [keep] when that is given. With [sealWith] every
 * document file is written as [SEALED]`<sealed text>` (AES-GCM with a key that never leaves the phone), so a copy of the backup folder opens
 * for nobody else; the readers open it again and still read the plain files of earlier builds. With [requireSeal] a backup is not taken at all
 * while there is no cipher, rather than written in clear.
 */
class LiveBackup(
    private val dir: File,
    private val keep: Int? = null,
    private val everyMillis: Long = DAY,
    private val sealWith: RowCipher? = null,
    private val requireSeal: Boolean = false,
    private val offsetMillis: () -> Long = { 0L },
) {
    /** Writes a backup when the newest is older than the interval (or there is none); returns its folder, else null. */
    fun takeIfDue(live: LiveDocuments, nowMillis: Long): File? {
        val last = complete().maxOfOrNull { it.name.toLong() }
        if (last != null && nowMillis - last < everyMillis) return null
        return take(live, nowMillis)
    }

    /** One finished automatic backup: when it was taken and which documents it holds. */
    data class Info(val millis: Long, val documents: List<String>, val bytes: Long)

    /** The finished backups, newest first. */
    fun list(): List<Info> = complete().sortedByDescending { it.name.toLong() }.map { f ->
        Info(f.name.toLong(), File(f, DONE).readText().split(",").filter { it.isNotEmpty() }, f.listFiles()?.filter { it.name.endsWith(".json") }?.sumOf { it.length() } ?: 0L)
    }

    /**
     * Whether there is room to write a backup of [bytes] (and a margin): twice the size, because a file is written beside the copy it
     * replaces, plus [MARGIN]. A backup is skipped, never half written, when there is not.
     */
    fun hasRoomFor(bytes: Long): Boolean {
        dir.mkdirs()
        return dir.usableSpace >= bytes * 2 + MARGIN
    }

    /** True when the last backup was skipped for lack of room; the app tells the player calmly and tries again later. */
    @Volatile var lowStorage: Boolean = false
        private set

    /** Takes a backup of the live documents, or returns null when there is nothing to back up or no room ([lowStorage] says which). */
    fun take(live: LiveDocuments, nowMillis: Long): File? {
        if (requireSeal && sealWith == null) return null
        val docs = live.snapshot()
        if (docs.isEmpty()) return null
        lowStorage = !hasRoomFor(docs.values.sumOf { it.length.toLong() })
        if (lowStorage) return null
        val folder = File(dir, nowMillis.toString())
        folder.mkdirs()
        for ((name, json) in docs) {
            val temp = File(folder, "$name.json.tmp")
            temp.writeText(sealWith?.let { SEALED + it.seal(json) } ?: json)
            check(temp.renameTo(File(folder, "$name.json"))) { "could not finish the backup of '$name'" }
        }
        File(folder, DONE).writeText(docs.keys.sorted().joinToString(","))
        prune()
        return folder
    }

    /** Deletes the finished backups that [BackupRetention] (or [keep]) no longer keeps. */
    fun prune() {
        val all = complete().associateBy { it.name.toLong() }
        val stay = if (keep != null) all.keys.sortedDescending().take(keep).toSet() else BackupRetention.keep(all.keys, offsetMillis())
        all.filterKeys { it !in stay }.values.forEach { it.deleteRecursively() }
    }

    /** When the newest finished backup was taken, or null if there is none. */
    fun lastBackupMillis(): Long? = complete().maxOfOrNull { it.name.toLong() }

    /** A document file as the app reads it: opened if it was sealed. Null when it is sealed and cannot be opened (another phone's key, or none). */
    private fun read(file: File): String? {
        val text = file.readText()
        if (!text.startsWith(SEALED)) return text
        return sealWith?.open(text.removePrefix(SEALED))
    }

    /** The documents of the backup taken at [millis] (Mint and Heal still sealed with their own keys); null when it is not there or cannot be opened. */
    fun documentsOf(millis: Long): Map<String, String>? {
        val folder = complete().firstOrNull { it.name.toLong() == millis } ?: return null
        val names = File(folder, DONE).readText().split(",").filter { it.isNotEmpty() }
        val docs = names.associateWith { read(File(folder, "$it.json")) }
        return if (docs.values.any { it == null }) null else docs.mapValues { it.value!! }
    }

    /** The backup taken at [millis] as one bundle file's text ([BackupBundle]), for the player to save anywhere; null when it is not there. */
    fun bundleOf(millis: Long, schema: Int): String? = documentsOf(millis)?.let { BackupBundle.encode(it, schema, millis) }

    /** Puts the backup taken at [millis] back (the documents it replaces are kept aside first). False when there is no such backup. */
    fun restore(live: LiveDocuments, millis: Long, nowMillis: Long): Boolean {
        val docs = documentsOf(millis) ?: return false
        live.replaceAll(docs, nowMillis)
        return true
    }

    /**
     * After Heal's rows were sealed (0.154.0): takes a fresh backup now (Heal sealed in it) and deletes the older ones that still hold Heal in clear, so no daily copy
     * outlives the sealing with a plaintext measurement in it. Older copies stay when the new one could not be written (no room): they rotate out as usual.
     */
    fun refreshAfterSealing(live: LiveDocuments, nowMillis: Long): Boolean {
        val fresh = take(live, nowMillis) ?: return false
        for (f in complete()) {
            if (f == fresh) continue
            val heal = File(f, "heal.json")
            if (heal.exists() && read(heal)?.let { !RowCipher.isSealed(it) } == true) f.deleteRecursively()
        }
        return true
    }

    /** Deletes every daily backup (a full-device erase). Returns how many were removed. */
    fun deleteAll(): Int = (dir.listFiles()?.toList() ?: emptyList()).count { it.deleteRecursively() }

    /** The newest finished backup folder, or null. */
    fun newest(): File? = complete().maxByOrNull { it.name.toLong() }

    /** Puts the newest backup back (the documents it replaces are kept aside first). False when there is no backup. */
    fun restoreNewest(live: LiveDocuments, nowMillis: Long): Boolean {
        // The newest one that can be opened: a backup sealed with a key this phone no longer has is passed over for the one before it.
        val docs = complete().sortedByDescending { it.name.toLong() }.firstNotNullOfOrNull { documentsOf(it.name.toLong()) } ?: return false
        live.replaceAll(docs, nowMillis)
        return true
    }

    private fun complete(): List<File> =
        dir.listFiles { f -> f.isDirectory && f.name.toLongOrNull() != null && File(f, DONE).exists() }?.toList() ?: emptyList()

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        private const val DONE = "DONE"

        /** Starts a document file that is sealed, so it can never be mistaken for JSON or for a module's own sealed text ([RowCipher.PREFIX]). */
        const val SEALED = "BK1:"

        /** Free space kept beyond a backup itself. */
        const val MARGIN = 5L * 1024 * 1024
    }
}
