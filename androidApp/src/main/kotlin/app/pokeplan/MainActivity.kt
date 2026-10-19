package app.pokeplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.pokeplan.app.Storage
import app.pokeplan.ui.App
import java.io.File

/** The save is one JSON file in the app's private storage. */
private class FileStorage(private val file: File) : Storage {
    override fun load(): String? = if (file.exists()) file.readText() else null
    override fun save(text: String) {
        file.writeText(text)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = FileStorage(File(filesDir, "save.json"))
        setContent { App(storage, focusTab = true) }
    }
}
