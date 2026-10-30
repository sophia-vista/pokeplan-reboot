package app.pokeplan.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.pokeplan.app.FileStorage
import java.io.File

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
    Window(onCloseRequest = ::exitApplication, title = "PokéLife") {
        val folder = File(System.getProperty("user.home"), ".pokeplan")
        App(FileStorage(File(folder, "save.json")), careStorage = FileStorage(File(folder, "care.json")), focusTab = false, files = DownloadsFileSaver())
    }
}
