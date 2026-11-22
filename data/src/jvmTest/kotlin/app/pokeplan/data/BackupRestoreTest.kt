package app.pokeplan.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRestoreTest {
    private fun dir() = File.createTempFile("bkp", "").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate("""{"player":{"coins":1},"tasks":[{"id":"t","title":"first"}]}""", """{"journalDraft":"c"}""", 1); it.live.moveCareToTables(2); it.live.movePlanToTables(3) }

    @Test
    fun theAutomaticBackupsAreListedAndAnyOneCanBeRestoredOrSavedAsAFile() {
        val s = live()
        val b = LiveBackup(dir(), keep = 5, everyMillis = 1)
        b.take(s.live, 1000)
        s.live.write("save", """{"player":{"coins":9},"tasks":[{"id":"t","title":"second"}]}""", 2)
        b.take(s.live, 2000)
        assertEquals(listOf(2000L, 1000L), b.list().map { it.millis })
        assertEquals(listOf("care", "save"), b.list().first().documents)
        assertTrue(b.restore(s.live, 1000, 3000))
        assertTrue(s.live.read("save")!!.contains("first") && s.live.read("save")!!.contains("\"coins\":1"))
        assertTrue(s.live.read("aside:save:3000")!!.contains("second"))
        assertFalse(b.restore(s.live, 999, 4000), "no such backup")
        val bundle = assertNotNull(b.bundleOf(2000, 5))
        assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(bundle, 5))
    }

    @Test
    fun aChosenFileIsCheckedWholeBeforeAnythingIsChanged() {
        val docs = mapOf("save" to """{"player":{"coins":3}}""", "care" to """{"journalDraft":"x"}""")
        val good = BackupBundle.encode(docs, 5, 77)
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(good, 5))
        assertEquals(docs, ready.documents); assertEquals(77L, ready.createdAtMillis)
        fun refused(text: String, supported: Int = 5) = assertIs<BackupBundle.Staged.Refused>(BackupBundle.stage(text, supported)).reason
        assertTrue(refused(good.take(good.length / 2)).contains("cut short"))
        assertTrue(refused("not json at all").contains("not a PokéLife backup"))
        assertTrue(refused("""{"format":"something-else"}""").contains("not a PokéLife backup"))
        assertTrue(refused(good, supported = 4).contains("newer"))
        assertTrue(refused(good.replace("coins\\\":3", "coins\\\":4")).contains("checksum"), "an edited document does not match its checksum")
        assertTrue(refused(BackupBundle.encode(mapOf("save" to "{not json"), 5, 1)).contains("damaged"))
        assertTrue(refused(BackupBundle.encode(emptyMap(), 5, 1)).contains("no data"))
        assertTrue(refused(good.replace("\"version\":1", "\"version\":2")).contains("newer"))
    }

    @Test
    fun aStagedBundleSwapsInOneTransactionAndAFailureHalfwayChangesNothing() {
        val s = live()
        val before = s.live.snapshot()
        val bundle = BackupBundle.encode(mapOf("save" to """{"player":{"coins":50},"tasks":[{"id":"n","title":"imported"}]}""", "care" to """{"journalDraft":"imported"}"""), 5, 9)
        s.driver.execute(null, "CREATE TRIGGER stop BEFORE INSERT ON plan_task WHEN NEW.key = 'n' BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(bundle, 5))
        runCatching { s.live.replaceAll(ready.documents, 10) }
        assertEquals(before, s.live.snapshot(), "nothing changed")
        assertNull(s.live.read("aside:save:10"), "and nothing was kept aside by a swap that did not happen")
        s.driver.execute(null, "DROP TRIGGER stop", 0)
        s.live.replaceAll(ready.documents, 11)
        assertTrue(s.live.read("save")!!.contains("imported"))
        assertTrue(s.live.read("aside:save:11")!!.contains("first"), "the save it replaced can be brought back")
        assertTrue(s.integrityIssues().isEmpty())
    }

    @Test
    fun noRoomMeansNoBackupAndNoHalfWrittenFile() {
        val s = live()
        val b = LiveBackup(dir())
        assertTrue(b.hasRoomFor(1000))
        assertFalse(b.hasRoomFor(Long.MAX_VALUE / 4), "a backup that cannot fit is not started")
        assertNotNull(b.take(s.live, 5)); assertFalse(b.lowStorage)
    }

    @Test
    fun aWriteCutShortLeavesTheLastSavedStateWholeAcrossAllTables() {
        val f = File.createTempFile("kill", ".db").also { it.delete(); it.deleteOnExit() }
        val first = JvmDatabase.open(f.path)
        first.live.activate(null, null, 1); first.live.moveCareToTables(2); first.live.movePlanToTables(3)
        first.live.write("save", """{"player":{"coins":1},"tasks":[{"id":"a","title":"saved"}],"restDays":["2026-11-01"],"focus":{"startedAtMillis":1}}""", 4)
        first.live.write("care", """{"journalDraft":"saved","routines":[{"id":"r","title":"R","createdOn":"2026-11-01","completions":["2026-11-01"]}]}""", 5)
        val saved = first.live.snapshot()
        runCatching {
            first.transaction {
                first.live.write("save", """{"player":{"coins":99},"tasks":[{"id":"a","title":"lost"},{"id":"b","title":"lost too"}],"restDays":[],"focus":null}""", 6)
                first.live.write("care", """{"journalDraft":"lost"}""", 6)
                first.close()
                error("the process was killed")
            }
        }
        val again = JvmDatabase.open(f.path)
        assertEquals(saved, again.live.snapshot(), "every table is at the last committed state, none half way")
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.planTables.isActive() && again.careTables.isActive())
    }
}
