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

    fun take(live: LiveDocuments, nowMillis: Long): File? {
        val docs = live.snapshot()
        if (docs.isEmpty()) return null
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

    /** The newest finished backup folder, or null. */
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
    }
}
