package app.pokeplan.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.pokeplan.mint.CaptureChannel
import app.pokeplan.mint.CaptureSource

/**
 * Reads a bank's text message, and nothing else (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3).
 *
 * The component is declared off and is enabled only while the player has bank messages switched on, so Android never
 * wakes the app for a message while it is off. The sender is checked first, before the body is touched; a sender that is
 * not on [CaptureSource.senders] is dropped there and then.
 */
class MintSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (parts.isEmpty()) return
        val sender = parts.firstOrNull()?.originatingAddress.orEmpty()
        val source = CaptureSource.ofSender(sender) ?: return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        val at = parts.firstOrNull()?.timestampMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()
        val pending = goAsync()
        // Opening MINT's store is IO and CPU, so it never runs on the main thread the broadcast arrives on.
        Thread {
            try {
                AlertReader.readNow(context.applicationContext, source, CaptureChannel.MESSAGE, "", body, at)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
