package app.pokeplan

import android.content.Context
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage
import java.io.File

/** The save is one JSON file in the app's private storage; the app and the widgets share it. */
class FileStorage(private val file: File) : Storage {
    override fun load(): String? = if (file.exists()) file.readText() else null
    override fun save(text: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        temp.renameTo(file)
    }
}

fun saveStorage(context: Context): Storage = FileStorage(File(context.filesDir, "save.json"))

/** The Care module's own save, apart from Plan's (docs/ecosystem.md, section 6). */
fun careStorage(context: Context): Storage = FileStorage(File(context.filesDir, "care.json"))

/** A store for the widgets: reads the same save as the app and writes back what a tap changes. */
fun widgetStore(context: Context): GameStore =
    GameStore(saveStorage(context), Clock { System.currentTimeMillis() })
