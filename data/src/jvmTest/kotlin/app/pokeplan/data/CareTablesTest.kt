package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Care's records in their own tables (REP-014): the document goes in as rows and comes back equal, and a write changes only what changed. */
class CareTablesTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/0.123.0/$name")!!.readText()
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate(null, null, 1) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    /** Counts every row written to the Care tables from now on, so a test can tell which writes a save made. */
    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in listOf("care_routine", "care_routine_completion", "care_diary_card", "care_journal_entry", "care_state", "care_visitor")) {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    @Test
    fun theFiveFixturesMoveIntoTablesAndReadBackEqual() {
        for (name in listOf("care-week.json")) {
            val s = JvmDatabase.open()
            s.live.activate("{}", fixture(name), 5)
            s.live.moveCareToTables(6)
            assertTrue(s.careTables.isActive())
            val back = obj(s.live.read("care")!!)
            val src = obj(fixture(name))
            // Empty lists are not stored, and come back as nothing, which the app reads as the same empty list.
            for ((k, v) in src) if (!(v is JsonArray && v.isEmpty() && k != "met" && k != "rhythmWeeks")) assertEquals(v, back[k] ?: JsonArray(emptyList()).takeIf { v is JsonArray && v.isEmpty() }, "$name: $k")
            // The old row is kept aside, never deleted, and the snapshot still has Care as 'care'.
            assertEquals(fixture(name), s.live.read("aside:care:tables:6"))
            assertEquals(back, obj(s.live.snapshot().getValue("care")))
        }
    }

    @Test
    fun aDocumentWithEveryKindOfRecordRoundTripsAndKeepsFieldsItDoesNotKnow() {
        val s = live()
        val doc = """{"routines":[{"id":"r1","title":"Water","createdOn":"2026-11-01","completions":["2026-11-02","2026-11-01"],"future":{"a":[1,2]}},{"id":"r2","title":"Walk","createdOn":"2026-11-02","completions":[]}],
            "diary":[{"id":"d1","day":"2026-11-03","atMillis":5,"note":"private"}],"breaks":[{"atMillis":1,"day":"2026-11-03","seconds":60},{"atMillis":2,"day":"2026-11-04","seconds":90}],
            "journal":[{"id":"j1","atMillis":9,"day":"2026-11-05","text":"hello"}],"visitors":[{"id":"v1","speciesId":25,"arrivedDay":"2026-11-05"}],
            "settings":{"reminders":true},"journalDraft":"half","skippedPrompts":{"2026-11-05":1},"someNewField":{"x":1}}"""
        s.live.write("care", doc, 2) // not in tables yet: a plain row
        s.live.moveCareToTables(3)
        val back = obj(s.live.read("care")!!)
        val src = obj(doc)
        assertEquals(src["diary"], back["diary"]); assertEquals(src["breaks"], back["breaks"]); assertEquals(src["journal"], back["journal"])
        assertEquals(src["visitors"], back["visitors"]); assertEquals(src["settings"], back["settings"]); assertEquals(src["someNewField"], back["someNewField"])
        assertEquals(src["journalDraft"], back["journalDraft"]); assertEquals(src["skippedPrompts"], back["skippedPrompts"])
        val routines = back["routines"] as JsonArray
        assertEquals(2, routines.size)
        val r1 = routines[0] as JsonObject
        assertEquals((r1["completions"] as JsonArray).toSet(), setOf<JsonElement>(JsonPrimitive("2026-11-01"), JsonPrimitive("2026-11-02")))
        assertEquals(src.getValue("routines").let { (it as JsonArray)[0] as JsonObject }["future"], r1["future"], "an unknown field of a record stays in it")
        assertEquals(2, count(s, "care_calm_break")); assertEquals(1, count(s, "care_journal_entry")); assertEquals(2, count(s, "care_routine_completion"))
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        val base = """{"routines":[{"id":"r1","title":"Water","createdOn":"2026-11-01","completions":["2026-11-01"]}],"journal":[{"id":"j1","atMillis":1,"day":"2026-11-01","text":"one"}],"journalDraft":"","settings":{"reminders":true}}"""
        s.live.write("care", base, 1); s.live.moveCareToTables(2)
        countWrites(s)
        // Nothing changed: nothing written.
        s.live.write("care", s.live.read("care")!!, 3)
        assertEquals(0, writes(s))
        // A routine ticked on a new day: one completion row, nothing else.
        val ticked = obj(s.live.read("care")!!).let { d ->
            val r = (d["routines"] as JsonArray)[0] as JsonObject
            JsonObject(d + ("routines" to JsonArray(listOf(JsonObject(r + ("completions" to JsonArray(listOf(JsonPrimitive("2026-11-01"), JsonPrimitive("2026-11-02")))))))))
        }
        s.live.write("care", ticked.toString(), 4)
        assertEquals(1, writes(s))
        // A draft typed: one state row, not the journal.
        s.live.write("care", JsonObject(ticked + ("journalDraft" to JsonPrimitive("typing"))).toString(), 5)
        assertEquals(2, writes(s))
        // An entry written (and the draft cleared): one journal row and one state row.
        val journal = JsonArray((ticked["journal"] as JsonArray) + obj("""{"id":"j2","atMillis":2,"day":"2026-11-02","text":"two"}"""))
        s.live.write("care", JsonObject(ticked + ("journal" to journal)).toString(), 6)
        assertEquals(2 + 2, writes(s))
        // An entry deleted: one row.
        s.live.write("care", ticked.toString(), 7)
        assertEquals(2 + 2 + 1, writes(s)) // the entry's row only
    }

    @Test
    fun aFreshInstallStartsEmptyAndSavesFromTheFirstWrite() {
        val s = live()
        s.live.moveCareToTables(2)
        assertTrue(s.careTables.isActive())
        assertNull(s.live.read("care"))
        assertFalse("care" in s.live.names())
        s.live.write("care", """{"routines":[],"journal":[{"id":"j","atMillis":1,"day":"2026-11-01","text":"x"}],"met":[7]}""", 3)
        assertTrue("care" in s.live.names())
        assertEquals(setOf("journal", "met"), obj(s.live.read("care")!!).keys)
    }

    @Test
    fun aMoveThatDoesNotReadBackEqualLeavesTheDocumentAsTheLiveSave() {
        val s = live()
        s.live.write("care", """{"routines":[],"journal":[{"id":"j","atMillis":1,"day":"2026-11-01","text":"x"}]}""", 1)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        runCatching { s.live.moveCareToTables(2) }
        assertFalse(s.careTables.isActive())
        assertEquals(0, count(s, "care_journal_entry"), "the rows rolled back with the marker")
        assertNotNull(s.live.read("care"))
        assertEquals(listOf("care"), s.live.names())
    }

    @Test
    fun aBackupOfTheSnapshotRestoresIntoTablesAndAKeptAsideCopyIsKept() {
        val s = live()
        s.live.write("care", """{"journal":[{"id":"j","atMillis":1,"day":"2026-11-01","text":"first"}],"settings":{"reminders":true}}""", 1)
        s.live.moveCareToTables(2)
        val snap = s.live.snapshot()
        s.live.write("care", """{"journal":[{"id":"j","atMillis":1,"day":"2026-11-01","text":"changed"}],"settings":{"reminders":false}}""", 3)
        s.live.replaceAll(snap, 9)
        assertEquals("first", (((obj(s.live.read("care")!!)["journal"]) as JsonArray)[0] as JsonObject)["text"]!!.let { (it as JsonPrimitive).content })
        assertTrue(s.live.read("aside:care:9")!!.contains("changed"), "the Care save being replaced is kept aside, so a restore can be undone")
    }

    @Test
    fun theTablesSurviveReopeningTheFile() {
        val f = File.createTempFile("care", ".db").also { it.delete(); it.deleteOnExit() }
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1)
            s.live.moveCareToTables(2)
            s.live.write("care", """{"routines":[{"id":"r","title":"Stretch","createdOn":"2026-11-01","completions":["2026-11-01"]}],"journalDraft":"d"}""", 3)
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.careTables.isActive())
        assertEquals(obj("""{"routines":[{"id":"r","title":"Stretch","createdOn":"2026-11-01","completions":["2026-11-01"]}],"journalDraft":"d"}"""), obj(again.live.read("care")!!))
        // The marker is set, so another start does nothing.
        again.live.moveCareToTables(99)
        assertEquals(1, count(again, "care_routine"))
        assertTrue(again.integrityIssues().isEmpty())
    }
}
