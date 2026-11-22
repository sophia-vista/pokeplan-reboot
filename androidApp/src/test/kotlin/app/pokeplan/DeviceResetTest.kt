package app.pokeplan

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeviceResetTest : AndroidTestBase() {
    private val names = listOf("launcher", "weather", "offphone", "shared_reminders", "task_reminders", "reminder_migration", "crash_guard", "buddy_widget", "a_store_added_later")

    private fun fill() = names.forEach { n -> context.getSharedPreferences(n, Context.MODE_PRIVATE).edit().putString("k", "v").putLong("n", 7).commit() }

    @Test fun everyPreferencesFileIsEmptyAfterTheReset() {
        fill()
        File(files, "save.json").writeText("{}")
        File(files, "care.json").writeText("{}")
        assertTrue(DeviceReset.preferenceNames(context).containsAll(names), "the files exist before")

        DeviceReset.clear(context)

        DeviceReset.preferenceNames(context).forEach { n ->
            assertEquals(emptyMap(), context.getSharedPreferences(n, Context.MODE_PRIVATE).all, "$n is empty after the reset")
        }
        assertFalse(File(files, "save.json").exists())
        assertFalse(File(files, "care.json").exists())
    }

    @Test fun theOffPhoneBackupSetupIsGone() {
        val tree = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABackups")
        assertTrue(OffPhone.configure(context, tree, "correct horse battery"))
        assertTrue(OffPhone.isSetUp(context))
        DeviceReset.clear(context)
        assertFalse(OffPhone.isSetUp(context))
    }
}
