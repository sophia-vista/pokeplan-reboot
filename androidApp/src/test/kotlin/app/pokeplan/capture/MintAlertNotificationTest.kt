package app.pokeplan.capture

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.pokeplan.AndroidTestBase
import app.pokeplan.reminders.Channels
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The push notification that says a captured transaction is waiting (MINT.md 10.4). */
@RunWith(RobolectricTestRunner::class)
class MintAlertNotificationTest : AndroidTestBase() {
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val posted get() = manager.activeNotifications.filter { it.id == MintAlertNotification.ID }

    @Before fun allowPosting() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        MintAlertNotification.cancel(context)
    }

    @Test
    fun oneWaitingTransactionPostsOneGenericNotificationOnItsOwnChannel() {
        MintAlertNotification.show(context, 1)
        val notification = assertNotNull(posted.singleOrNull(), "one notification, not a stack").notification
        assertEquals(Channels.MINT_ALERTS, notification.channelId)
        assertEquals(MintAlertNotification.PUBLIC_TEXT, notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(text.contains("confirm"), "it says what to do: $text")
    }

    @Test
    fun itNeverCarriesTheAlertItself() {
        MintAlertNotification.show(context, 2)
        val notification = assertNotNull(posted.singleOrNull()).notification
        val everything = listOf(
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            notification.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            notification.tickerText?.toString(),
        ).joinToString(" ").lowercase()
        listOf("gcash", "maya", "bpi", "bdo", "unibank", "sm city", "\u20b1", "php", "250", "amount").forEach { private ->
            assertFalse(everything.contains(private), "the notification must not say \"$private\"")
        }
    }

    @Test
    fun theLockScreenVersionSaysOnlyThatOneIsWaiting() {
        MintAlertNotification.show(context, 3)
        val public = assertNotNull(posted.singleOrNull()).notification.publicVersion
        assertNotNull(public)
        assertEquals(MintAlertNotification.PUBLIC_TEXT, public.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(null, public.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun moreWaitingTransactionsUpdateTheOneNotificationInsteadOfStacking() {
        MintAlertNotification.show(context, 1)
        MintAlertNotification.show(context, 3)
        assertEquals(1, posted.size)
        val title = posted.single().notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue(title.contains("3"), "the title counts them: $title")
        val text = posted.single().notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(text.contains("them"), "and speaks of more than one: $text")
    }

    @Test
    fun readingOrErasingTheBoxTakesTheNotificationAway() {
        MintAlertNotification.show(context, 2)
        assertEquals(1, posted.size)
        MintAlertNotification.cancel(context)
        assertTrue(posted.isEmpty())
    }
}
