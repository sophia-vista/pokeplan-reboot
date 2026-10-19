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

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PokéPlan") {
        App(FileStorage(File(System.getProperty("user.home"), ".pokeplan/save.json")), focusTab = false)
    }
}
