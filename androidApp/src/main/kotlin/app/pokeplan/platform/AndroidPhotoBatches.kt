package app.pokeplan.platform

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.ui.PhotoBatchChooser

/** Reads every picked picture off the UI thread; a picture that cannot be read does not lose the others. */
private fun readAll(activity: ComponentActivity, uris: List<Uri>, limit: Int, done: (List<ByteArray>?, String?) -> Unit) {
    if (uris.isEmpty()) return done(null, null)
    Thread {
        val read = mutableListOf<ByteArray>()
        var problem: String? = null
        for (uri in uris.take(limit)) {
            val (bytes, why) = readPicture(activity.contentResolver, uri)
            if (bytes != null) read += bytes else if (problem == null) problem = why
        }
        activity.runOnUiThread {
            if (read.isEmpty()) done(null, problem ?: "Those pictures could not be read.") else done(read, null)
        }
    }.start()
}

/** Several photos at once from Android's photo picker (the system gallery), no storage permission. */
class AndroidGalleryPhotos(private val activity: ComponentActivity) : PhotoBatchChooser {
    private var waiting: ((List<ByteArray>?, String?) -> Unit)? = null
    private var limit = MAX_PICK

    private val pick = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris: List<Uri> ->
        val done = waiting ?: return@registerForActivityResult
        waiting = null
        readAll(activity, uris, limit, done)
    }

    override fun choose(max: Int, done: (pngs: List<ByteArray>?, problem: String?) -> Unit) {
        if (waiting != null) return done(null, "A picture is already being chosen.")
        waiting = done
        limit = max.coerceIn(1, MAX_PICK)
        runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { waiting = null; done(null, "This phone has no picture picker.") }
    }

    private companion object { const val MAX_PICK = 20 }
}

/**
 * Several photos at once from the system file picker (OpenMultipleDocuments): any documents provider the phone has,
 * including a cloud app's folder such as Google Drive, with no account or sign-in inside PokéLife.
 */
class AndroidFilePhotos(private val activity: ComponentActivity) : PhotoBatchChooser {
    private var waiting: ((List<ByteArray>?, String?) -> Unit)? = null
    private var limit = MAX_PICK

    private val pick = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val done = waiting ?: return@registerForActivityResult
        waiting = null
        readAll(activity, uris, limit, done)
    }

    override fun choose(max: Int, done: (pngs: List<ByteArray>?, problem: String?) -> Unit) {
        if (waiting != null) return done(null, "A picture is already being chosen.")
        waiting = done
        limit = max.coerceIn(1, MAX_PICK)
        runCatching { pick.launch(arrayOf("image/*")) }
            .onFailure { waiting = null; done(null, "This phone has no file picker.") }
    }

    private companion object { const val MAX_PICK = 20 }
}
