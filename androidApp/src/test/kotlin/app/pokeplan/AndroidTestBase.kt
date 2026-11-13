package app.pokeplan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import java.io.File

/**
 * The common start of the Android tests (REP-033): a fresh app-private folder (Robolectric gives each test its own), the test Keystore, and [LiveStore]
 * forgotten before and after, so each test starts like a newly installed app and [processDeath] can end one "process" and start the next.
 */
abstract class AndroidTestBase {
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    protected val files: File get() = context.filesDir

    @Before fun startFresh() {
        FakeAndroidKeyStore.install()
        FakeAndroidKeyStore.wipe()
        LiveStore.forgetForTests()
    }

    @After fun closeDatabase() {
        LiveStore.forgetForTests()
        FakeAndroidKeyStore.available = true
    }

    /** The process is killed: the open database is dropped without any goodbye, and the next store opens it again from the disk. */
    protected fun processDeath() = LiveStore.forgetForTests()

    /** One of the save fixtures of 0.123.0 that `SaveFixtureTest` checks in app-core, read from that module's test resources. */
    protected fun fixture(name: String): String = File(rootDir(), "app-core/src/jvmTest/resources/fixtures/0.123.0/$name").readText()

    /** Every byte of the database and its write-ahead log, as text, to look for a word that must be sealed. */
    protected fun databaseBytes(): String {
        val db = context.getDatabasePath(app.pokeplan.data.AndroidDatabase.FILE_NAME)
        return listOf("", "-wal", "-journal").map { File(db.path + it) }.filter { it.exists() }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
    }

    private fun rootDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "VERSION").exists() }
}
