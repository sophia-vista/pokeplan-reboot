package app.pokeplan.ui.mint

import app.pokeplan.mint.CaptureCandidate

/**
 * What the phone can do for MINT's capture of bank alerts (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3). Null where
 * there is nothing to read (desktop and web): the settings box then draws nothing, and no alert is ever taken.
 */
interface MessageCapture {
    /** Whether Android has given this app the notification access it needs. */
    fun notificationsGranted(): Boolean

    /** Opens Android's own notification-access page, where the player grants or revokes it. */
    fun openNotificationSettings()

    /** Whether the player has allowed reading text messages. */
    fun messagesGranted(): Boolean

    /** Asks for the text-message permission and reports whether it was granted. */
    fun requestMessages(done: (Boolean) -> Unit)

    /**
     * Switches the Android parts that read alerts on or off. Off means they are disabled components: Android never
     * binds the listener and never wakes the receiver, so nothing is read at all.
     */
    fun watch(notifications: Boolean, messages: Boolean)

    /** Takes every proposal waiting on the phone, oldest first, and clears them from it; the file work is off-thread. */
    suspend fun take(): List<CaptureCandidate>

    /** Throws away every waiting proposal. */
    suspend fun erase()
}
