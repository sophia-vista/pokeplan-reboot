package app.pokeplan.huawei

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import app.pokeplan.app.heal.HuaweiHealth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HuaweiNotificationServiceTest {
    @Before @After fun forget() = HuaweiCapture.clear()

    @Suppress("DEPRECATION")
    private fun posted(pkg: String, title: String, text: String): StatusBarNotification {
        val n = Notification.Builder(ApplicationProvider.getApplicationContext(), "c").setContentTitle(title).setContentText(text).build()
        return StatusBarNotification(pkg, pkg, 1, null, 0, 0, 0, n, Process.myUserHandle(), 1_000L)
    }

    @Test fun onlyHuaweiHealthIsKept() {
        val service = Robolectric.setupService(HuaweiNotificationService::class.java)
        service.onNotificationPosted(posted("com.example.chat", "Ana", "8,000 steps together?"))
        assertTrue(HuaweiCapture.seen().isEmpty)
        service.onNotificationPosted(posted(HuaweiHealth.PACKAGE, "8,432 steps", "Goal 10,000"))
        assertEquals(1, HuaweiCapture.seen().notifications.size)
        assertEquals(8432, HuaweiCapture.seen().latestSteps)
    }
}
