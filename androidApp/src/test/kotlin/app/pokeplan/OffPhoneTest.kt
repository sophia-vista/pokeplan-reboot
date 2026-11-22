package app.pokeplan

import android.net.Uri
import app.pokeplan.data.BackupBundle
import app.pokeplan.data.BackupFolder
import app.pokeplan.data.DataStore
import app.pokeplan.data.OffPhoneBackup
import app.pokeplan.heal.ActivityType
import app.pokeplan.mint.AccountType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OffPhoneTest : AndroidTestBase() {
    private val phrase = "correct horse battery"
    private val tree: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABackups")

    private class DiskFolder(val dir: File) : BackupFolder {
        override fun names() = dir.list()?.toList().orEmpty()
        override fun write(name: String, text: String): Boolean { File(dir, name).writeText(text); return true }
        override fun read(name: String) = File(dir, name).takeIf { it.exists() }?.readText()
        override fun delete(name: String) { File(dir, name).delete() }
    }

    private fun folder() = DiskFolder(File(files, "offphone-test").also { it.mkdirs() })

    private fun prefsText(): String =
        File(files.parentFile, "shared_prefs/offphone.xml").takeIf { it.exists() }?.readText().orEmpty()

    @Test fun thePassphraseIsKeptSealedAndIsNotInTheClearOnTheDisk() {
        assertFalse(OffPhone.isSetUp(context))
        assertTrue(OffPhone.configure(context, tree, phrase))
        assertTrue(OffPhone.isSetUp(context))
        assertFalse(phrase in prefsText(), "the passphrase is not in the preferences file")
        assertTrue("m1." in prefsText(), "it is the Keystore-sealed form")
        OffPhone.forget(context)
        assertFalse(OffPhone.isSetUp(context))
        assertFalse(phrase in prefsText())
    }

    @Test fun withoutTheKeyNothingIsKept() {
        FakeAndroidKeyStore.available = false
        assertFalse(OffPhone.configure(context, tree, phrase), "a passphrase that cannot be sealed is not stored in clear instead")
        assertFalse(OffPhone.isSetUp(context))
        assertFalse(phrase in prefsText())
    }

    @Test fun aCopyOfTheLiveStoreHoldsNoMintOrHealInClearAndOpensWithThePassphrase() {
        val game = widgetStore(context)
        healStore(context, game).logActivity(ActivityType.entries.first(), 30, note = "Zebracrossingnote")
        mintStore(context, game).addAccount("Quokkabank", AccountType.entries.first(), "PHP")
        assertTrue(OffPhone.configure(context, tree, phrase))
        val f = folder()
        assertEquals("Copied to the folder.", OffPhone.run(context, force = true, folder = f))
        val name = f.names().single()
        val text = assertNotNull(f.read(name))
        assertFalse("Zebracrossingnote" in text || "Quokkabank" in text, "the copy holds no Mint or Heal in clear")
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(text, DataStore.SCHEMA_VERSION))
        assertTrue(ready.needsPassphrase)
        assertNull(BackupBundle.unlock(ready, "not the passphrase", app.pokeplan.data.Pbkdf2Sealer(iterations = 1_000)))
        assertEquals("A recent copy is already there.", OffPhone.run(context, force = false, folder = f), "not twice in a day")
    }

    @Test fun aFolderThatIsGoneIsReportedAndNothingIsWritten() {
        val game = widgetStore(context)
        game.addTask("x")
        assertTrue(OffPhone.configure(context, tree, phrase))
        val note = OffPhone.run(context, force = true)
        assertTrue("not available" in note, note)
        assertTrue("not available" in OffPhone.status(context)!!.problem.orEmpty())
        assertEquals(null, OffPhone.status(context)!!.lastMillis)
    }

    @Test fun runDoesNothingWhenNotSetUp() {
        assertEquals("Backup off the phone is not set up.", OffPhone.run(context, force = true, folder = folder()))
        assertTrue(folder().names().isEmpty())
    }
}
