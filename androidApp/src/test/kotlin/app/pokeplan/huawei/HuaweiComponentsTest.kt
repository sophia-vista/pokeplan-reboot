package app.pokeplan.huawei

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** REP-055: the listener is rebound only while notification access is granted; the components themselves are never disabled. */
@RunWith(RobolectricTestRunner::class)
class HuaweiComponentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun noRebindWhenAccessIsNotGranted() {
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
        assertFalse(HuaweiComponents.rebind(context))
    }

    @Test fun rebindWhenAccessIsGranted() {
        val me = android.content.ComponentName(context, HuaweiNotificationService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", me)
        assertTrue(HuaweiComponents.rebind(context))
    }
}
