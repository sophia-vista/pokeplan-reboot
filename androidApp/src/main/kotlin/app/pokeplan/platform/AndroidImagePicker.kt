package app.pokeplan.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.ui.ImageChooser
import java.io.ByteArrayOutputStream

/** The sides of a picture scaled so its longest side is at most [max], keeping the aspect ratio (never enlarged, never below 1 px). */
fun fitSides(w: Int, h: Int, max: Int): Pair<Int, Int> {
    val longest = maxOf(w, h)
    if (longest <= max || longest <= 0) return w to h
    val scale = max.toDouble() / longest
    return maxOf(1, Math.round(w * scale).toInt()) to maxOf(1, Math.round(h * scale).toInt())
}

/**
 * Lets the player pick one picture through Android's Photo Picker (no storage permission), shrinks it and hands back PNG bytes small enough to keep.
 * The launcher is registered in `onCreate`.
 */
class AndroidImagePicker(private val activity: ComponentActivity) : ImageChooser {
    private var waiting: ((ByteArray?, String?) -> Unit)? = null

    private val pick = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val done = waiting; waiting = null
        if (done == null) return@registerForActivityResult
        if (uri == null) return@registerForActivityResult done(null, null)
        Thread {
            val result = read(uri)
            activity.runOnUiThread { done(result.first, result.second) }
        }.start()
    }

    override fun choose(done: (png: ByteArray?, problem: String?) -> Unit) {
        if (waiting != null) return done(null, "A picture is already being chosen.")
        waiting = done
        runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { waiting = null; done(null, "This phone has no picture picker.") }
    }

    private fun read(uri: Uri): Pair<ByteArray?, String?> = try {
        val limit = MAX_INPUT + 1
        val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readNBytes(limit) }
            ?: return null to "That picture could not be opened."
        if (bytes.size >= limit) return null to "That picture is too big."
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null to NOT_A_PICTURE
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null to NOT_A_PICTURE
        var found: ByteArray? = null
        for (side in SIDES) {
            val (w, h) = fitSides(decoded.width, decoded.height, side)
            val scaled = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= MAX_PNG) { found = out.toByteArray(); break }
        }
        if (found != null) found to null
        else null to "That picture is too detailed to keep. Crop it to just the QR code and try again."
    } catch (e: Exception) {
        null to "That picture could not be read."
    }

    private companion object {
        const val MAX_INPUT = 20 * 1024 * 1024
        const val DECODE_SIDE = 2400
        const val MAX_PNG = 192000
        val SIDES = intArrayOf(1080, 900, 720, 540, 360)
        const val NOT_A_PICTURE = "That file is not a picture."
    }
}
