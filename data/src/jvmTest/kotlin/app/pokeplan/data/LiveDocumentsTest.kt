package app.pokeplan.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDocumentsTest {
    @Test
    fun activationCopiesTheDocumentsOnceAndSetsTheMarker() {
        val s = JvmDatabase.open()
        assertFalse(s.live.isActive())
        val r = s.live.activate("{\"a\":1}", "{\"c\":2}", 10)
        assertEquals(Activation.Imported(listOf("save", "care")), r)
        assertTrue(s.live.isActive())
        assertEquals("{\"a\":1}", s.live.read("save"))
        assertEquals(Activation.AlreadyActive, s.live.activate("{\"a\":999}", null, 20))
        assertEquals("{\"a\":1}", s.live.read("save"))
    }

    @Test
    fun aFreshInstallActivatesWithNoDocuments() {
        val s = JvmDatabase.open()
        assertEquals(Activation.Imported(emptyList()), s.live.activate(null, null, 1))
        assertTrue(s.live.isActive())
        assertNull(s.live.read("save"))
    }

    @Test
    fun writesReplaceAndSurviveReopen() {
        val f = File.createTempFile("live", ".db").also { it.delete(); it.deleteOnExit() }
        JvmDatabase.open(f.path).let { s ->
            s.live.activate("{}", null, 1)
            s.live.write("save", "{\"v\":2}", 2)
            s.live.keepAside("save", "garbage", 3)
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.live.isActive())
        assertEquals("{\"v\":2}", again.live.read("save"))
        assertEquals("garbage", again.live.read("aside:save:3"))
        assertEquals(listOf("aside:save:3", "save"), again.live.names())
    }

    @Test
    fun aFailedActivationLeavesTheDatabaseAsItWas() {
        val s = JvmDatabase.open()
        s.live.write("save", "old", 1)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        runCatching { s.live.activate("new", null, 2) }
        assertFalse(s.live.isActive())
        assertEquals("old", s.live.read("save"), "the copy rolled back with the failed marker")
    }

    @Test
    fun anOlderVersionOneDatabaseMigratesAndKeepsItsRows() {
        val f = File.createTempFile("v1db", ".db").also { it.delete(); it.deleteOnExit() }
        val v1 = File("src/commonMain/sqldelight/databases/1.db")
        v1.copyTo(f, overwrite = true)
        java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { it.createStatement().execute("PRAGMA user_version = 1") }
        val s = JvmDatabase.open(f.path)
        s.live.write("save", "{}", 1)
        assertEquals("{}", s.live.read("save"))
    }
}

class LiveBackupTest {
    private fun dir() = java.nio.file.Files.createTempDirectory("bk").toFile().also { it.deleteOnExit() }

    @Test
    fun aBackupIsTakenWhenDueKeepsThreeAndRestoresTheNewestWithTheCurrentOneKeptAside() {
        val s = JvmDatabase.open()
        s.live.activate("{\"v\":1}", "{\"c\":1}", 0)
        val b = LiveBackup(dir(), keep = 3, everyMillis = 1000)
        assertTrue(b.takeIfDue(s.live, 1000) != null)
        assertEquals(null, b.takeIfDue(s.live, 1500), "not due yet")
        s.live.write("save", "{\"v\":2}", 2000)
        b.takeIfDue(s.live, 3000); b.takeIfDue(s.live, 5000); b.takeIfDue(s.live, 7000)
        assertEquals(3, b.newest()!!.parentFile.listFiles()!!.size, "only the newest three are kept")
        s.live.write("save", "{\"v\":3}", 8000)
        assertTrue(b.restoreNewest(s.live, 9000))
        assertEquals("{\"v\":2}", s.live.read("save"))
        assertEquals("{\"v\":3}", s.live.read("aside:save:9000"))
        assertEquals("{\"c\":1}", s.live.read("care"))
    }

    @Test
    fun aBackupCutShortIsNeverUsedAndAnEmptyDatabaseMakesNone() {
        val d = dir()
        java.io.File(d, "5000").mkdirs()
        val s = JvmDatabase.open()
        val b = LiveBackup(d)
        assertEquals(null, b.newest())
        assertEquals(false, b.restoreNewest(s.live, 1))
        assertEquals(null, b.take(s.live, 6000), "nothing to back up")
    }
}
