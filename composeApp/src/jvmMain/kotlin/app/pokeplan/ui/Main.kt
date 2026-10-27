package app.pokeplan.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.pokeplan.app.Storage
import java.io.File

/** The save is one JSON file in the user's home folder. */
class FileStorage(private val file: File) : Storage {
    override fun load(): String? = if (file.exists()) file.readText() else null
    override fun save(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}

/** Writes the data export to the Downloads folder (the home folder if there is none). */
class DownloadsFileSaver : FileSaver {
    override fun save(name: String, text: String): String = try {
        val home = File(System.getProperty("user.home"))
        val folder = File(home, "Downloads").takeIf { it.isDirectory } ?: home
        val file = File(folder, name).apply { writeText(text) }
        "Saved to ${file.path}"
    } catch (e: Exception) {
        "Could not export: ${e.message}"
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PokéPlan") {
        val folder = File(System.getProperty("user.home"), ".pokeplan")
        App(FileStorage(File(folder, "save.json")), careStorage = FileStorage(File(folder, "care.json")), focusTab = false, files = DownloadsFileSaver())
    }
}
