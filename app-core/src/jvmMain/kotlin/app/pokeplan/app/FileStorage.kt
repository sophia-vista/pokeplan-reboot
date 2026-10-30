package app.pokeplan.app

import java.io.File

/**
 * The save as one JSON file (the app's private storage on Android, the home folder on desktop). A write goes to a
 * temporary file first and replaces the save in one step, so a reader (a widget) never sees half of it.
 */
class FileStorage(private val file: File) : Storage {
    override fun load(): String? = if (file.exists()) file.readText() else null

    override fun save(text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    override fun keepAside(text: String) {
        file.parentFile?.mkdirs()
        File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}").writeText(text)
    }
}
