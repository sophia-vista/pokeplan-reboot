package app.pokeplan.capture

import android.content.Context
import android.util.Log
import app.pokeplan.data.KeystoreMintKey
import app.pokeplan.mint.CaptureCandidate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The few alerts the phone read and the player has not looked at yet (MINT.md 10.4). They are kept sealed with MINT's
 * own key, never next to the raw message, only until the MINT screen takes them; without the key nothing is kept at all.
 */
internal object MintCaptureBox {
    private const val TAG = "MintCapture"
    private const val FILE = "mint-capture.sealed"
    private const val LIMIT = 40
    private const val KEEP_MILLIS = 7L * 86_400_000L
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Box(val items: List<CaptureCandidate> = emptyList())

    /** Adds what the phone read, dropping an alert it already holds and anything older than a week; how many now wait. */
    @Synchronized
    fun add(context: Context, found: List<CaptureCandidate>): Int {
        if (found.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val kept = (read(context) + found).filter { it.atMillis >= now - KEEP_MILLIS }.distinctBy { it.ref }.takeLast(LIMIT)
        return if (write(context, kept)) kept.size else 0
    }

    /** Takes every waiting alert out of the box, oldest first, and clears it. */
    @Synchronized
    fun take(context: Context): List<CaptureCandidate> {
        val items = read(context)
        file(context).delete()
        return items
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun read(context: Context): List<CaptureCandidate> = try {
        val f = file(context)
        if (!f.isFile) emptyList()
        else {
            val plain = KeystoreMintKey.cipher().open(f.readText())
            if (plain == null) emptyList() else json.decodeFromString(Box.serializer(), plain).items
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(context: Context, items: List<CaptureCandidate>): Boolean {
        val f = file(context)
        val sealed = try {
            if (items.isEmpty()) null else KeystoreMintKey.cipher().seal(json.encodeToString(Box.serializer(), Box(items)))
        } catch (e: Exception) {
            null
        }
        if (sealed == null) {
            f.delete() // fail closed: with no key nothing is written in the clear
            return false
        }
        return try {
            val temp = File(f.parentFile, "$FILE.tmp")
            temp.writeText(sealed)
            if (!temp.renameTo(f)) {
                f.delete()
                temp.renameTo(f)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "the alert box could not be written: ${e.javaClass.simpleName}")
            false
        }
    }
}
