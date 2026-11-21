package app.pokeplan.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.pokeplan.ui.FileSaver
import java.io.File

class AndroidFileSaver(private val context: Context) : FileSaver {
    override fun save(name: String, text: String): String = try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Save your PokéLife data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Choose where to keep the file."
    } catch (e: Exception) {
        "Could not export: ${e.message}"
    }
}
