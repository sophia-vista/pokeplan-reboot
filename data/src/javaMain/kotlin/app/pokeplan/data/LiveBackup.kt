package app.pokeplan.data

import java.io.File

class LiveBackup(
    private val dir: File,
    private val keep: Int? = null,
    private val everyMillis: Long = DAY,
    private val sealWith: RowCipher? = null,
    private val requireSeal: Boolean = false,
    private val offsetMillis: () -> Long = { 0L },
) {
    fun takeIfDue(live: LiveDocuments, nowMillis: Long): File? {
        val last = complete().maxOfOrNull { it.name.toLong() }
        if (last != null && nowMillis - last < everyMillis) return null
        return take(live, nowMillis)
    }

    data class Info(val millis: Long, val documents: List<String>, val bytes: Long)

    fun list(): List<Info> = complete().sortedByDescending { it.name.toLong() }.map { f ->
        Info(f.name.toLong(), File(f, DONE).readText().split(",").filter { it.isNotEmpty() }, f.listFiles()?.filter { it.name.endsWith(".json") }?.sumOf { it.length() } ?: 0L)
    }

    fun hasRoomFor(bytes: Long): Boolean {
        dir.mkdirs()
        return dir.usableSpace >= bytes * 2 + MARGIN
    }

    @Volatile var lowStorage: Boolean = false
        private set

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

    fun prune() {
        val all = complete().associateBy { it.name.toLong() }
        val stay = if (keep != null) all.keys.sortedDescending().take(keep).toSet() else BackupRetention.keep(all.keys, offsetMillis())
        all.filterKeys { it !in stay }.values.forEach { it.deleteRecursively() }
    }

    private fun read(file: File): String? {
        val text = file.readText()
        if (!text.startsWith(SEALED)) return text
        return sealWith?.open(text.removePrefix(SEALED))
    }

    fun documentsOf(millis: Long): Map<String, String>? {
        val folder = complete().firstOrNull { it.name.toLong() == millis } ?: return null
        val names = File(folder, DONE).readText().split(",").filter { it.isNotEmpty() }
        val docs = names.associateWith { read(File(folder, "$it.json")) }
        return if (docs.values.any { it == null }) null else docs.mapValues { it.value!! }
    }

    fun bundleOf(millis: Long, schema: Int): String? = documentsOf(millis)?.let { BackupBundle.encode(it, schema, millis) }

    var leftByLastRestore: List<String> = emptyList()
        private set

    fun restore(live: LiveDocuments, millis: Long, nowMillis: Long): Boolean {
        val docs = documentsOf(millis) ?: return false
        leftByLastRestore = live.replaceAll(docs, nowMillis)
        return true
    }

    fun refreshAfterSealing(live: LiveDocuments, nowMillis: Long): Boolean {
        val fresh = take(live, nowMillis) ?: return false
        for (f in complete()) {
            if (f == fresh) continue
            val heal = File(f, "heal.json")
            if (heal.exists() && read(heal)?.let { !RowCipher.isSealed(it) } == true) f.deleteRecursively()
        }
        return true
    }

    fun deleteAll(): Int = (dir.listFiles()?.toList() ?: emptyList()).count { it.deleteRecursively() }

    fun newest(): File? = complete().maxByOrNull { it.name.toLong() }

    fun restoreNewest(live: LiveDocuments, nowMillis: Long): Boolean {
        val docs = complete().sortedByDescending { it.name.toLong() }.firstNotNullOfOrNull { documentsOf(it.name.toLong()) } ?: return false
        live.replaceAll(docs, nowMillis)
        return true
    }

    private fun complete(): List<File> =
        dir.listFiles { f -> f.isDirectory && f.name.toLongOrNull() != null && File(f, DONE).exists() }?.toList() ?: emptyList()

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        private const val DONE = "DONE"

        const val SEALED = "BK1:"

        const val MARGIN = 5L * 1024 * 1024
    }
}
