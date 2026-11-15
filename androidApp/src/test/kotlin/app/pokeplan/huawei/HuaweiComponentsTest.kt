package app.pokeplan.huawei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** REP-055: the two reader services are components that are enabled only while the reader is switched on or being set up. */
@RunWith(RobolectricTestRunner::class)
class HuaweiComponentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun setupEnablesThenSyncDisablesWhenNotSwitchedOn() {
        HuaweiComponents.enableScreen(context)
        HuaweiComponents.enableNotification(context)
        assertTrue(HuaweiComponents.screenEnabled(context))
        assertTrue(HuaweiComponents.notificationEnabled(context))
        // Nothing is switched on in Android's settings: both go back to disabled.
        HuaweiComponents.sync(context)
        assertFalse(HuaweiComponents.screenEnabled(context))
        assertFalse(HuaweiComponents.notificationEnabled(context))
    }

    @Test fun syncKeepsScreenReaderWhenItCanSwitchItself() {
        HuaweiComponents.sync(context, canSwitch = true)
        assertTrue(HuaweiComponents.screenEnabled(context))
        assertFalse(HuaweiComponents.notificationEnabled(context))
    }
}
