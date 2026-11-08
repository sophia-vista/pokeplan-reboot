package app.pokeplan.data

import java.io.File

/**
 * Backups of the live documents as plain files, one folder per backup (`<millis>/save.json`, `care.json`, then a `DONE`
 * marker written last, so a folder cut short is never used). The files are the same JSON the app has always saved, so the
 * newest backup is also the rollback copy for an older build: put its `save.json` and `care.json` back in the app's files.
 * Each backup reads the database in one transaction, so it is consistent however the app is writing at the time.
 */
class LiveBackup(private val dir: File, private val keep: Int = 3, private val everyMillis: Long = DAY) {
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
        val docs = live.snapshot()
        if (docs.isEmpty()) return null
        lowStorage = !hasRoomFor(docs.values.sumOf { it.length.toLong() })
        if (lowStorage) return null
        val folder = File(dir, nowMillis.toString())
        folder.mkdirs()
        for ((name, json) in docs) {
            val temp = File(folder, "$name.json.tmp")
            temp.writeText(json)
            check(temp.renameTo(File(folder, "$name.json"))) { "could not finish the backup of '$name'" }
        }
        File(folder, DONE).writeText(docs.keys.sorted().joinToString(","))
        complete().sortedBy { it.name.toLong() }.dropLast(keep).forEach { it.deleteRecursively() }
        return folder
    }

    /** The documents of the backup taken at [millis], exactly as stored (Mint sealed with this phone's key); null when it is not there. */
    fun documentsOf(millis: Long): Map<String, String>? {
        val folder = complete().firstOrNull { it.name.toLong() == millis } ?: return null
        val names = File(folder, DONE).readText().split(",").filter { it.isNotEmpty() }
        return names.associateWith { File(folder, "$it.json").readText() }
    }

    /** The backup taken at [millis] as one bundle file's text ([BackupBundle]), for the player to save anywhere; null when it is not there. */
    fun bundleOf(millis: Long, schema: Int): String? = documentsOf(millis)?.let { BackupBundle.encode(it, schema, millis) }

    /** Puts the backup taken at [millis] back (the documents it replaces are kept aside first). False when there is no such backup. */
    fun restore(live: LiveDocuments, millis: Long, nowMillis: Long): Boolean {
        val folder = complete().firstOrNull { it.name.toLong() == millis } ?: return false
        val names = File(folder, DONE).readText().split(",").filter { it.isNotEmpty() }
        live.replaceAll(names.associateWith { File(folder, "$it.json").readText() }, nowMillis)
        return true
    }

    /** The newest finished backup folder, or null. */
    /** Deletes every daily backup (a full-device erase). Returns how many were removed. */
    fun deleteAll(): Int = (dir.listFiles()?.toList() ?: emptyList()).count { it.deleteRecursively() }

    fun newest(): File? = complete().maxByOrNull { it.name.toLong() }

    /** Puts the newest backup back (the documents it replaces are kept aside first). False when there is no backup. */
    fun restoreNewest(live: LiveDocuments, nowMillis: Long): Boolean {
        val folder = newest() ?: return false
        val names = File(folder, DONE).readText().split(",").filter { it.isNotEmpty() }
        live.replaceAll(names.associateWith { File(folder, "$it.json").readText() }, nowMillis)
        return true
    }

    private fun complete(): List<File> =
        dir.listFiles { f -> f.isDirectory && f.name.toLongOrNull() != null && File(f, DONE).exists() }?.toList() ?: emptyList()

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        private const val DONE = "DONE"

        /** Free space kept beyond a backup itself. */
        const val MARGIN = 5L * 1024 * 1024
    }
}
