package app.pokeplan.capture

import android.content.Context
import app.pokeplan.mint.CaptureChannel
import app.pokeplan.mint.CaptureSource
import app.pokeplan.mint.CaptureText
import app.pokeplan.mint.MintCapture
import app.pokeplan.mintStore
import app.pokeplan.widgetStore
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.Executors

/**
 * Turns one alert the phone saw into a proposal (MINT.md 10.4), off the main thread. The raw text is used here and
 * nowhere else: only the parsed fields reach the sealed box. The MINT setting is checked again here, so an alert on a
 * component that was left on is still dropped when the player turned the switch off.
 */
internal object AlertReader {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mint-capture").apply { isDaemon = true } }

    /** Reads [text] in the background; the caller keeps nothing. */
    fun read(context: Context, source: CaptureSource, channel: CaptureChannel, title: String, text: String, atMillis: Long) {
        val app = context.applicationContext
        worker.execute { readNow(app, source, channel, title, text, atMillis) }
    }

    /** Reads on the caller's thread, for a receiver that holds the broadcast open until it is done. */
    fun readNow(context: Context, source: CaptureSource, channel: CaptureChannel, title: String, text: String, atMillis: Long) {
        try {
            if (title.isBlank() && text.isBlank()) return
            if (text.length > RAW_LIMIT || title.length > RAW_LIMIT) return
            val mint = runCatching { mintStore(context, widgetStore(context)) }.getOrNull() ?: return
            val settings = mint.data.settings
            val wanted = when (channel) {
                CaptureChannel.NOTIFICATION -> settings.captureNotifications
                CaptureChannel.MESSAGE -> settings.captureMessages
            }
            if (!wanted) return
            val day = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val candidate = MintCapture.parse(CaptureText(channel, source, atMillis, day, title, text), settings.currency) ?: return
            // One generic notification tells the player a transaction is waiting; it carries nothing of the alert itself.
            val waiting = MintCaptureBox.add(context, listOf(candidate))
            if (waiting > 0) MintAlertNotification.show(context, waiting)
        } catch (e: Exception) {
            // Nothing about the alert is ever logged: at most the failure's kind, which is not its content.
        }
    }

    /** A message longer than this is not a bank alert and is not even parsed. */
    private const val RAW_LIMIT = 4_000
}
