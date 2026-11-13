package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.link.LinkData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** LINK's records in their own tables from the start (REP-014): round trip, only changed rows written, one kind erased alone, backup and activation. */
class LinkTablesTest {
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate(null, null, 1); it.live.moveLinkToTables(2) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val tables = listOf("link_person", "link_note", "link_date", "link_followup")

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in tables + "link_state") {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    /** A realistic LINK document: every kind of record once, the settings, and a field a newer build might add. */
    private val doc = """{
        "people":[{"id":"p1","name":"Zorblax Quux","preferred":"Zo","phone":"+63 917 000 1234","email":"zorblax@example.com","organization":"Quuxville","category":"Friend","tags":["zorblax-tag"],"favorite":true,"createdMillis":10,"updatedMillis":10}],
        "notes":[{"id":"n1","personId":"p1","text":"Zorblax-note-77","atMillis":20}],
        "dates":[{"id":"d1","kind":"BIRTHDAY","title":"Birthday","month":2,"day":29,"year":1996,"annual":true,"personId":"p1","createdMillis":30}],
        "followUps":[{"id":"f1","title":"Zorblax-follow-55","personId":"p1","due":"2026-11-20","status":"COMPLETED","createdMillis":40,"updatedMillis":41,"completedMillis":41,"completedDay":"2026-11-07","paid":true}],
        "settings":{"postcardTrail":true,"tutorialSeen":true,"categories":["Neighbour"]},
        "future":{"x":1}
    }""".trimIndent()

    @Test
    fun aFreshStartActivatesWithNothingToMoveAndSurvivesRestarts() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        assertFalse(s.linkTables.isActive())
        s.live.moveLinkToTables(5)
        assertTrue(s.linkTables.isActive())
        assertNull(s.live.read("link"))
        assertFalse("link" in s.live.names())
        s.live.moveLinkToTables(9) // runs on every start
        assertEquals("5", s.db.dataQueries.meta(LinkTables.META_LINK_TABLES_AT).executeAsOne(), "the first activation time is kept")
    }

    @Test
    fun aDocumentRestoredBeforeActivationIsImportedReadBackEqualAndKeptAside() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("link", doc, 2) // not in tables yet: a plain row
        s.live.moveLinkToTables(3)
        assertEquals(obj(doc), obj(s.live.read("link")!!))
        assertEquals(doc, s.live.read("aside:link:tables:3"))
        for (t in tables) assertEquals(1, count(s, t), t)
        assertEquals(2L, count(s, "link_state"), "the settings and the unknown field")
    }

    @Test
    fun anActivationThatCannotFinishLeavesTheDatabaseAsItWas() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("link", doc, 2)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta WHEN NEW.key = 'link_store' BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        assertFailsWith<Exception> { s.live.moveLinkToTables(3) }
        assertFalse(s.linkTables.isActive())
        assertEquals(0, count(s, "link_person"), "the rows written before the failure were rolled back")
        assertTrue(s.live.read("link")!!.contains("p1"), "the document row is still the live save")
    }

    @Test
    fun linkRunsOnTablesAndEverythingComesBackAfterReopeningTheFile() {
        val f = File.createTempFile("link", ".db").also { it.delete(); it.deleteOnExit() }
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1); s.live.moveLinkToTables(2)
            s.live.write("link", doc, 3)
            assertEquals(1L, count(s, "link_person")); assertEquals(1L, count(s, "link_date")); assertEquals(1L, count(s, "link_followup"))
            assertEquals("2026-11-20", s.driver.executeQuery(null, "SELECT day FROM link_followup", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value, "a follow-up's day column is its due date")
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.linkTables.isActive())
        assertEquals(obj(doc), obj(again.live.read("link")!!), "everything LINK kept is back, field for field")
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        s.live.write("link", doc, 3)
        countWrites(s)
        s.live.write("link", doc, 4) // nothing changed
        assertEquals(0L, writes(s))
        val more = obj(doc).toMutableMap()
        more["notes"] = JsonArray((more.getValue("notes") as JsonArray) + obj("""{"id":"n2","personId":"p1","text":"more","atMillis":21}"""))
        s.live.write("link", Json.encodeToString(JsonObject.serializer(), JsonObject(more)), 5) // one new row in one table
        assertEquals(1L, writes(s))
        more["settings"] = obj("""{"postcardTrail":false,"tutorialSeen":true,"categories":["Neighbour"]}""")
        s.live.write("link", Json.encodeToString(JsonObject.serializer(), JsonObject(more)), 6) // the settings row
        assertEquals(2L, writes(s), "only the settings row was replaced")
    }

    @Test
    fun erasingOneKindClearsItsFamilyAndNothingElse() {
        val s = live()
        s.live.write("link", doc, 3)
        s.live.write("save", """{"player":{"name":"x"}}""", 3)
        val saveBefore = s.live.read("save")
        s.linkTables.erase("followUps")
        assertEquals(0, count(s, "link_followup"))
        for (t in tables - "link_followup") assertEquals(1, count(s, t), t)
        assertEquals(saveBefore, s.live.read("save"), "the game's save is untouched")
        assertEquals("[]", s.linkTables.readKind("followUps"))
        assertTrue(s.linkTables.readKind("people").contains("Zorblax Quux"))
        assertEquals(listOf("people", "notes", "dates", "followUps"), LinkTables.KINDS)
        assertFailsWith<IllegalStateException> { s.linkTables.erase("nonsense") }
        s.linkTables.clear()
        assertEquals(0L, tables.sumOf { count(s, it) } + count(s, "link_state"))
        assertEquals(saveBefore, s.live.read("save"))
        assertNull(s.live.read("link"))
    }

    @Test
    fun noSharedTableEverHoldsLinksWords() {
        val s = live()
        s.live.write("link", doc, 3)
        s.live.write("save", """{"player":{"name":"x"}}""", 4)
        for (t in listOf("live_document", "app_meta")) {
            val all = s.driver.executeQuery(null, "SELECT * FROM $t", { c ->
                val sb = StringBuilder()
                while (c.next().value) for (i in 0 until 3) runCatching { c.getString(i) }.getOrNull()?.let { sb.append(it) }
                QueryResult.Value(sb.toString())
            }, 0).value!!
            for (secret in listOf("Zorblax", "zorblax@example.com", "+63 917", "Quuxville", "zorblax-tag")) assertFalse(all.contains(secret), "$t holds $secret")
        }
    }

    @Test
    fun theBackupSnapshotCarriesLinkAndARestoreOfAnOlderBackupLeavesItAlone() {
        val s = live()
        s.live.write("save", """{"player":{"name":"x"}}""", 3)
        s.live.write("link", doc, 3)
        val snap = s.live.snapshot()
        assertTrue("link" in snap && snap.getValue("link").contains("Zorblax Quux"))
        assertTrue("link" in s.live.names())
        // A backup made before LINK existed holds no link document: restoring it must not wipe the records.
        s.live.replaceAll(mapOf("save" to snap.getValue("save")), 9)
        assertEquals(1, count(s, "link_person"))
        // A backup with a link document brings it back, and the replaced one is kept aside.
        val changed = obj(doc).toMutableMap().also { it["notes"] = JsonArray(emptyList()) }
        s.live.write("link", Json.encodeToString(JsonObject.serializer(), JsonObject(changed)), 10)
        assertEquals(0, count(s, "link_note"))
        s.live.replaceAll(snap, 11)
        assertEquals(1, count(s, "link_note"))
        assertEquals(obj(doc), obj(s.live.read("link")!!))
        assertTrue(s.live.read("aside:link:11")!!.contains("Zorblax Quux"), "the replaced document is kept aside")
    }

    @Test
    fun theBundleKnowsLinkAndRefusesAnythingElse() {
        val docs = mapOf("save" to "{}", "link" to """{"people":[]}""")
        val text = BackupBundle.encode(docs, DataStore.SCHEMA_VERSION, 7)
        val staged = BackupBundle.stage(text, DataStore.SCHEMA_VERSION)
        assertTrue(staged is BackupBundle.Staged.Ready && staged.documents.keys == setOf("save", "link"))
        assertEquals(10, DataStore.SCHEMA_VERSION)
        val bad = BackupBundle.encode(mapOf("save" to "{}"), 10, 7).replace("\"save\"", "\"contacts\"")
        assertTrue(BackupBundle.stage(bad, 10) is BackupBundle.Staged.Refused)
    }
}
