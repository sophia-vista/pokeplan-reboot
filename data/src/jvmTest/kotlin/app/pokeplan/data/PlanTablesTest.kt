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

class PlanTablesTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/0.123.0/$name")!!.readText()
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate(null, null, 1) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val planTables = listOf("plan_task", "plan_list", "plan_event", "plan_series", "plan_goal", "plan_review", "plan_focus_record", "plan_day", "plan_state")

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in planTables) {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    private fun same(src: JsonObject, back: JsonObject, label: String) {
        val emptyOk = setOf("tasks", "lists", "events", "series", "goals", "reviews", "focusHistory", "restDays", "activeDays")
        for ((k, v) in src) {
            val got = back[k]
            if (v is JsonArray && v.isEmpty() && k in emptyOk) { assertTrue(got == null || (got as JsonArray).isEmpty(), "$label: $k"); continue }
            if ((k == "restDays" || k == "activeDays") && v is JsonArray) { assertEquals(v.toSet(), (got as JsonArray).toSet(), "$label: $k"); continue }
            assertEquals(v, got, "$label: $k")
        }
        assertEquals(src.keys.filter { k -> !(src[k].let { it is JsonArray && it.isEmpty() } && k in emptyOk) }.toSet(), back.keys.toSet(), "$label: the fields")
    }

    @Test
    fun theSaveFixturesMoveIntoTablesAndReadBackEqual() {
        for (name in listOf("main-empty.json", "main-typical-week.json", "main-heavy-70-days.json", "main-focus-running-and-pending.json")) {
            val s = JvmDatabase.open()
            s.live.activate(fixture(name), null, 5)
            s.live.movePlanToTables(6)
            assertTrue(s.planTables.isActive())
            same(obj(fixture(name)), obj(s.live.read("save")!!), name)
            assertEquals(fixture(name), s.live.read("aside:save:plan:6"))
            same(obj(fixture(name)), obj(s.live.snapshot().getValue("save")), "$name snapshot")
            val row = obj(s.driver.executeQuery(null, "SELECT json FROM live_document WHERE name = 'save'", { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value!!)
            assertTrue(row.keys.none { it in PlanTables.PLAN_FIELDS }, "$name: the row holds only the rest")
            assertTrue("player" in row)
        }
    }

    @Test
    fun theTypicalWeekHasItsTasksAndTheRunningFocusInTheirTables() {
        val s = JvmDatabase.open()
        s.live.activate(fixture("main-focus-running-and-pending.json"), null, 5)
        s.live.movePlanToTables(6)
        val src = obj(fixture("main-focus-running-and-pending.json"))
        assertEquals((src["tasks"] as JsonArray).size.toLong(), count(s, "plan_task"))
        assertEquals(1L, count(s, "plan_state").coerceAtMost(count(s, "plan_state")).let { if (it >= 1) 1 else 0 })
        assertNotNull(obj(s.live.read("save")!!)["focus"])
    }

    @Test
    fun aDocumentWithEveryKindOfRecordRoundTripsAndKeepsFieldsItDoesNotKnow() {
        val s = live()
        val doc = """{"trainerName":"A","player":{"coins":4},"tasks":[{"id":"t1","title":"One","planned":"2026-11-03","future":{"a":[1]}},{"id":"t2","title":"Two"}],
            "lists":[{"id":"l1","name":"Home"}],"events":[{"id":"e1","title":"Gym","day":"2026-11-04"}],
            "series":[{"template":{"id":"s1","title":"Water","seriesId":"s1"},"recurrence":{"start":"2026-11-01"}}],
            "goals":[{"id":"g1","title":"Guitar","createdAtMillis":1}],"reviews":[{"weekStart":"2026-11-02","completedAtMillis":5}],
            "focusHistory":[{"startedAtMillis":10,"minutes":25,"completed":true},{"startedAtMillis":20,"minutes":5,"completed":false}],
            "restDays":["2026-11-02","2026-11-01"],"activeDays":["2026-11-03"],"focus":{"startedAtMillis":9},"pickedTaskId":"t1","emergencyPausesUsed":1,
            "someNewPlanLessField":{"x":1}}"""
        s.live.write("save", doc, 2)
        s.live.movePlanToTables(3)
        val back = obj(s.live.read("save")!!)
        same(obj(doc), back, "round trip")
        assertEquals(2, count(s, "plan_task")); assertEquals(1, count(s, "plan_series")); assertEquals(2, count(s, "plan_focus_record"))
        assertEquals(3, count(s, "plan_day")); assertEquals(3, count(s, "plan_state"))
        assertEquals(obj(doc)["someNewPlanLessField"], back["someNewPlanLessField"], "a top-level field PLAN does not own stays in the row")
        assertEquals(obj(doc)["tasks"], back["tasks"], "an unknown field of a record stays in it")
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        val base = obj("""{"player":{"coins":1},"tasks":[{"id":"t1","title":"One"},{"id":"t2","title":"Two"}],"restDays":["2026-11-01"],"focusHistory":[{"startedAtMillis":1,"minutes":25,"completed":true}]}""")
        s.live.write("save", base.toString(), 1); s.live.movePlanToTables(2)
        countWrites(s)
        s.live.write("save", s.live.read("save")!!, 3)
        assertEquals(0, writes(s), "nothing changed, nothing written")
        s.live.write("save", JsonObject(base + ("player" to obj("""{"coins":9}"""))).toString(), 4)
        assertEquals(0, writes(s))
        val tasks = base["tasks"] as JsonArray
        val ticked = JsonArray(listOf(tasks[0], JsonObject((tasks[1] as JsonObject) + ("state" to JsonPrimitive("DONE")))))
        s.live.write("save", JsonObject(base + ("tasks" to ticked)).toString(), 5)
        assertEquals(1, writes(s))
        s.live.write("save", JsonObject(base + ("tasks" to ticked) + ("focus" to obj("""{"startedAtMillis":5}"""))).toString(), 6)
        assertEquals(2, writes(s))
        s.live.write("save", JsonObject(base + ("tasks" to ticked) + ("focus" to obj("""{"startedAtMillis":5}""")) + ("restDays" to JsonArray(listOf(JsonPrimitive("2026-11-01"), JsonPrimitive("2026-11-02"))))).toString(), 7)
        assertEquals(3, writes(s))
        s.live.write("save", JsonObject(base + ("tasks" to JsonArray(listOf(ticked[1])))).toString(), 8)
        assertEquals(3 + 4, writes(s))
    }

    @Test
    fun aFreshInstallStartsEmptyAndSavesFromTheFirstWrite() {
        val s = live()
        s.live.movePlanToTables(2)
        assertTrue(s.planTables.isActive())
        assertNull(s.live.read("save"))
        s.live.write("save", """{"tasks":[{"id":"t","title":"x"}],"player":{"coins":2}}""", 3)
        assertTrue("save" in s.live.names())
        assertEquals(setOf("tasks", "player"), obj(s.live.read("save")!!).keys)
    }

    @Test
    fun aMoveThatDoesNotReadBackEqualLeavesTheDocumentAsTheLiveSave() {
        val s = live()
        s.live.write("save", """{"tasks":[{"id":"t","title":"x"}],"player":{"coins":2}}""", 1)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        runCatching { s.live.movePlanToTables(2) }
        assertFalse(s.planTables.isActive())
        assertEquals(0, count(s, "plan_task"), "the rows rolled back with the marker")
        assertTrue(s.live.read("save")!!.contains("\"tasks\""))
    }

    @Test
    fun aBackupOfTheSnapshotRestoresIntoTablesAndTheReplacedSaveIsKeptAside() {
        val s = live()
        s.live.write("save", """{"tasks":[{"id":"t","title":"first"}],"player":{"coins":1}}""", 1)
        s.live.movePlanToTables(2)
        val snap = s.live.snapshot()
        s.live.write("save", """{"tasks":[{"id":"t","title":"changed"}],"player":{"coins":5}}""", 3)
        s.live.replaceAll(snap, 9)
        val back = obj(s.live.read("save")!!)
        assertEquals("first", (((back["tasks"] as JsonArray)[0] as JsonObject)["title"] as JsonPrimitive).content)
        assertEquals("1", (((back["player"]) as JsonObject)["coins"] as JsonPrimitive).content)
        assertTrue(s.live.read("aside:save:9")!!.contains("changed"), "a restore can be undone")
    }

    @Test
    fun theTablesSurviveReopeningTheFile() {
        val f = File.createTempFile("plan", ".db").also { it.delete(); it.deleteOnExit() }
        val doc = obj("""{"player":{"coins":3},"tasks":[{"id":"t","title":"x"}],"activeDays":["2026-11-01"],"focus":{"startedAtMillis":1}}""")
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1)
            s.live.movePlanToTables(2)
            s.live.write("save", doc.toString(), 3)
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.planTables.isActive())
        assertEquals(doc, obj(again.live.read("save")!!))
        again.live.movePlanToTables(99)
        assertEquals(1, count(again, "plan_task"))
        assertTrue(again.integrityIssues().isEmpty())
    }

    @Suppress("unused") private fun unused(e: JsonElement) = e
}
