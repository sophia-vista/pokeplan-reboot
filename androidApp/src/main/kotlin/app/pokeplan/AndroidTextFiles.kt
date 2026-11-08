package app.pokeplan

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.ui.TextFileChooser
import java.nio.charset.CodingErrorAction

/**
 * Lets the player pick one statement file through Android's own document picker (Storage Access Framework, INTEGRATIONS_AND_PRIVACY.md 8.2): no storage permission, only the
 * file chosen, read once as UTF-8 text with a size limit, and not copied anywhere. The launcher is registered in `onCreate`.
 */
class AndroidTextFiles(private val activity: ComponentActivity) : TextFileChooser {
    private var waiting: ((String?, String?, String?) -> Unit)? = null

    private val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val done = waiting; waiting = null
        if (done == null) return@registerForActivityResult
        if (uri == null) return@registerForActivityResult done(null, null, null)
        Thread {
            val result = read(uri)
            activity.runOnUiThread { done(result.first, result.second, result.third) }
        }.start()
    }

    override fun choose(done: (name: String?, text: String?, problem: String?) -> Unit) {
        if (waiting != null) return done(null, null, "A file is already being chosen.")
        waiting = done
        runCatching { open.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }
            .onFailure { waiting = null; done(null, null, "This phone has no file picker.") }
    }

    private fun read(uri: Uri): Triple<String?, String?, String?> = try {
        val name = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        val limit = app.pokeplan.mint.StatementImport.MAX_CHARS * 2 + 1
        val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readNBytes(limit) } ?: return Triple(null, null, "That file could not be opened.")
        if (bytes.size >= limit) Triple(name, null, "That file is too big to import.")
        else {
            // Strict decoding: a file that is not UTF-8 text is refused, not guessed at.
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            Triple(name, decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString(), null)
        }
    } catch (e: java.nio.charset.CharacterCodingException) {
        Triple(null, null, "That is not a plain text (CSV) file. In your spreadsheet app, save it as CSV (UTF-8) first.")
    } catch (e: Exception) {
        Triple(null, null, "That file could not be read.")
    }
}
