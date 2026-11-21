package app.pokeplan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import java.io.File

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

    protected fun processDeath() = LiveStore.forgetForTests()

    protected fun fixture(name: String): String = File(rootDir(), "app-core/src/jvmTest/resources/fixtures/0.123.0/$name").readText()

    protected fun databaseBytes(): String {
        val db = context.getDatabasePath(app.pokeplan.data.AndroidDatabase.FILE_NAME)
        return listOf("", "-wal", "-journal").map { File(db.path + it) }.filter { it.exists() }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
    }

    private fun rootDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "VERSION").exists() }
}
