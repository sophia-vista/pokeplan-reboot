package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.base.BaseData
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

/** HOME's records in their own tables from the start (REP-014): round trip, only changed rows written, one kind erased alone, backup and activation. */
class BaseTablesTest {
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate(null, null, 1); it.live.moveBaseToTables(2) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val tables = listOf(
        "home_area", "home_chore", "home_entry", "home_supply", "home_adjustment", "home_restock",
        "home_asset", "home_schedule", "home_maintenance", "home_warranty", "home_reference", "home_room",
    )

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in tables + "home_state") {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    /** A realistic HOME document: every kind of record once, the settings, and a field a newer build might add. */
    private val doc = """{
        "areas":[{"id":"ar1","name":"Kitchen"}],
        "chores":[{"id":"c1","title":"Vacuum","areaId":"ar1","recurrence":{"kind":"WEEKLY","every":1,"weekdays":[6]},"firstDue":"2026-11-07","minutes":20,"status":"ACTIVE","createdMillis":10,"updatedMillis":10}],
        "entries":[{"id":"e1","choreId":"c1","dueDay":"2026-11-07","status":"DONE","day":"2026-11-07","atMillis":99,"note":"quick","paid":true}],
        "supplies":[{"id":"s1","name":"Rice","category":"Pantry","quantityMilli":1500,"unit":"kg","lowMilli":500,"areaId":"ar1","createdMillis":10,"updatedMillis":10}],
        "adjustments":[{"id":"j1","supplyId":"s1","beforeMilli":2000,"afterMilli":1500,"reason":"cooked","atMillis":50}],
        "restock":[{"id":"r1","name":"Salt","createdMillis":11}],
        "assets":[{"id":"a1","name":"Washing machine","brand":"Acme","model":"W1","serial":"SN-123","purchaseDay":"2024-03-01","areaId":"ar1","createdMillis":1,"updatedMillis":1}],
        "schedules":[{"id":"m1","title":"Descale","assetId":"a1","recurrence":{"kind":"MONTHLY","every":3},"policy":"AFTER_DONE","firstDue":"2026-09-01","nextDue":"2026-12-01","createdMillis":2}],
        "maintenance":[{"id":"mr1","title":"Descale","assetId":"a1","scheduleId":"m1","day":"2026-09-01","cost":"12","atMillis":3,"paid":true}],
        "warranties":[{"id":"w1","assetId":"a1","provider":"Acme","startDay":"2024-03-01","expiryDay":"2027-03-01","reference":"WARR-9","createdMillis":4}],
        "references":[{"id":"n1","title":"Boiler","text":"Reset: hold 5 s","areaId":"ar1","assetId":"a1","createdMillis":5,"updatedMillis":5}],
        "settings":{"progression":true,"tutorialSeen":true,"units":["kg","pcs"],"hidden":["supplies"]},
        "future":{"x":1}
    }""".trimIndent()

    @Test
    fun aFreshStartActivatesWithNothingToMoveAndSurvivesRestarts() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        assertFalse(s.baseTables.isActive())
        s.live.moveBaseToTables(5)
        assertTrue(s.baseTables.isActive())
        assertNull(s.live.read("home"))
        assertFalse("home" in s.live.names())
        s.live.moveBaseToTables(9) // runs on every start
        assertEquals("5", s.db.dataQueries.meta(BaseTables.META_HOME_TABLES_AT).executeAsOne(), "the first activation time is kept")
    }

    @Test
    fun aDocumentRestoredBeforeActivationIsImportedReadBackEqualAndKeptAside() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("home", doc, 2) // not in tables yet: a plain row
        s.live.moveBaseToTables(3)
        assertEquals(obj(doc), obj(s.live.read("home")!!))
        assertEquals(doc, s.live.read("aside:home:tables:3"))
        for (t in tables) assertEquals(1, count(s, t), t)
        assertEquals(2L, count(s, "home_state"), "the settings and the unknown field")
    }

    @Test
    fun anActivationThatCannotFinishLeavesTheDatabaseAsItWas() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("home", doc, 2)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta WHEN NEW.key = 'home_store' BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        assertFailsWith<Exception> { s.live.moveBaseToTables(3) }
        assertFalse(s.baseTables.isActive())
        assertEquals(0, count(s, "home_chore"), "the rows written before the failure were rolled back")
        assertTrue(s.live.read("home")!!.contains("c1"), "the document row is still the live save")
    }

    @Test
    fun homeRunsOnTablesAndEverythingComesBackAfterReopeningTheFile() {
        val f = File.createTempFile("home", ".db").also { it.delete(); it.deleteOnExit() }
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1); s.live.moveBaseToTables(2)
            s.live.write("home", doc, 3)
            assertEquals(1L, count(s, "home_chore")); assertEquals(1L, count(s, "home_asset")); assertEquals(1L, count(s, "home_warranty"))
            assertEquals("2026-11-07", s.driver.executeQuery(null, "SELECT day FROM home_chore", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value, "a chore's day column is its first due date")
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.baseTables.isActive())
        assertEquals(obj(doc), obj(again.live.read("home")!!), "everything HOME kept is back, field for field")
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        s.live.write("home", doc, 3)
        countWrites(s)
        s.live.write("home", doc, 4) // nothing changed
        assertEquals(0L, writes(s))
        val more = obj(doc).toMutableMap()
        more["restock"] = JsonArray((more.getValue("restock") as JsonArray) + obj("""{"id":"r2","name":"Soap","createdMillis":12}"""))
        s.live.write("home", Json.encodeToString(JsonObject.serializer(), JsonObject(more)), 5) // one new row in one table
        assertEquals(1L, writes(s))
        more["settings"] = obj("""{"progression":false,"tutorialSeen":true,"units":["kg","pcs"],"hidden":[]}""")
        s.live.write("home", Json.encodeToString(JsonObject.serializer(), JsonObject(more)), 6) // the settings row
        assertEquals(2L, writes(s), "only the settings row was replaced")
    }

    @Test
    fun erasingOneKindClearsItsFamilyAndNothingElse() {
        val s = live()
        s.live.write("home", doc, 3)
        s.live.write("save", """{"player":{"name":"x"}}""", 3)
        val saveBefore = s.live.read("save")
        s.baseTables.erase("supplies")
        assertEquals(0, count(s, "home_supply"))
        for (t in tables - "home_supply") assertEquals(1, count(s, t), t)
        assertEquals(saveBefore, s.live.read("save"), "the game's save is untouched")
        assertEquals("[]", s.baseTables.readKind("supplies"))
        assertTrue(s.baseTables.readKind("assets").contains("SN-123"))
        assertEquals(listOf("areas", "chores", "entries", "supplies", "adjustments", "restock", "assets", "schedules", "maintenance", "warranties", "references"), BaseTables.KINDS)
        assertFailsWith<IllegalStateException> { s.baseTables.erase("nonsense") }
        s.baseTables.clear()
        assertEquals(0L, tables.sumOf { count(s, it) } + count(s, "home_state"))
        assertEquals(saveBefore, s.live.read("save"))
        assertNull(s.live.read("home"))
    }

    @Test
    fun noSharedTableEverHoldsBasesWords() {
        val s = live()
        s.live.write("home", doc, 3)
        s.live.write("save", """{"player":{"name":"x"}}""", 4)
        for (t in listOf("live_document", "app_meta")) {
            val all = s.driver.executeQuery(null, "SELECT * FROM $t", { c ->
                val sb = StringBuilder()
                while (c.next().value) for (i in 0 until 3) runCatching { c.getString(i) }.getOrNull()?.let { sb.append(it) }
                QueryResult.Value(sb.toString())
            }, 0).value!!
            for (secret in listOf("SN-123", "WARR-9", "Washing machine", "Boiler", "Reset: hold")) assertFalse(all.contains(secret), "$t holds $secret")
        }
    }

    @Test
    fun theBackupSnapshotCarriesHomeAndARestoreOfAnOlderBackupLeavesItAlone() {
        val s = live()
        s.live.write("save", """{"player":{"name":"x"}}""", 3)
        s.live.write("home", doc, 3)
        val snap = s.live.snapshot()
        assertTrue("home" in snap && snap.getValue("home").contains("Washing machine"))
        assertTrue("home" in s.live.names())
        // A backup made before HOME existed holds no home document: restoring it must not wipe household records.
        val left = s.live.replaceAll(mapOf("save" to snap.getValue("save")), 9)
        assertEquals(1, count(s, "home_chore"))
        assertTrue("home" in left && "save" !in left, "and the restore says what it left as it was")
        // A backup with a home document brings it back, and the replaced one is kept aside.
        val changed = obj(doc).toMutableMap().also { it["references"] = JsonArray(emptyList()) }
        s.live.write("home", Json.encodeToString(JsonObject.serializer(), JsonObject(changed)), 10)
        assertEquals(0, count(s, "home_reference"))
        s.live.replaceAll(snap, 11)
        assertEquals(1, count(s, "home_reference"))
        assertEquals(obj(doc), obj(s.live.read("home")!!))
        assertTrue(s.live.read("aside:home:11")!!.contains("Washing machine"), "the replaced document is kept aside")
    }

    @Test
    fun theBundleKnowsHomeAndRefusesAnythingElse() {
        val docs = mapOf("save" to "{}", "home" to """{"chores":[]}""")
        val text = BackupBundle.encode(docs, DataStore.SCHEMA_VERSION, 7)
        val staged = BackupBundle.stage(text, DataStore.SCHEMA_VERSION)
        assertTrue(staged is BackupBundle.Staged.Ready && staged.documents.keys == setOf("save", "home"))
        assertEquals(12, DataStore.SCHEMA_VERSION)
        val bad = BackupBundle.encode(mapOf("save" to "{}"), 9, 7).replace("\"save\"", "\"household\"")
        assertTrue(BackupBundle.stage(bad, 9) is BackupBundle.Staged.Refused)
    }
}
