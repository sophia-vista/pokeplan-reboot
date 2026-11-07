package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.HealExport
import app.pokeplan.app.HealStore
import app.pokeplan.app.MemoryStorage
import app.pokeplan.app.Storage
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.HealKinds
import app.pokeplan.heal.MeasureType
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HEAL's records in their own tables from the start (REP-014): round trip, only changed rows written, one kind erased alone, backup and activation. */
class HealTablesTest {
    private val start = 1_793_000_000_000L
    private class Tables(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("heal")
        override fun save(text: String) = live.write("heal", text, 1)
        override fun keepAside(text: String) = live.keepAside("heal", text, 1)
    }

    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.live.activate(null, null, 1); it.live.moveHealToTables(2) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in listOf("heal_activity", "heal_hydration", "heal_sleep", "heal_measurement", "heal_goal", "heal_state")) {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    private val clock = object : Clock { var millis = start; override fun nowMillis() = millis }
    private fun game() = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
    private fun healOn(store: DataStore, game: GameStore) = HealStore(Tables(store.live), clock, game, TimeZone.UTC, rng = Random(3))

    @Test
    fun aFreshStartActivatesWithNothingToMoveAndSurvivesRestarts() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        assertFalse(s.healTables.isActive())
        s.live.moveHealToTables(5)
        assertTrue(s.healTables.isActive())
        assertNull(s.live.read("heal"))
        assertFalse("heal" in s.live.names())
        s.live.moveHealToTables(9) // runs on every start
        assertEquals("5", s.db.dataQueries.meta(HealTables.META_HEAL_TABLES_AT).executeAsOne(), "the first activation time is kept")
    }

    @Test
    fun aDocumentRestoredBeforeActivationIsImportedReadBackEqualAndKeptAside() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        val doc = """{"activities":[{"id":"a1","type":"WALK","startMillis":5,"minutes":30,"day":"2026-11-07"}],"settings":{"fieldResearch":true},"future":{"x":1}}"""
        s.live.write("heal", doc, 2) // not in tables yet: a plain row
        s.live.moveHealToTables(3)
        assertEquals(obj(doc), obj(s.live.read("heal")!!))
        assertEquals(doc, s.live.read("aside:heal:tables:3"))
        assertEquals(1, count(s, "heal_activity"))
    }

    @Test
    fun anActivationThatCannotFinishLeavesTheDatabaseAsItWas() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("heal", """{"activities":[{"id":"a1","type":"WALK","startMillis":5,"minutes":30,"day":"2026-11-07"}]}""", 2)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta WHEN NEW.key = 'heal_store' BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        assertFailsWith<Exception> { s.live.moveHealToTables(3) }
        assertFalse(s.healTables.isActive())
        assertEquals(0, count(s, "heal_activity"), "the rows written before the failure were rolled back")
        assertTrue(s.live.read("heal")!!.contains("a1"), "the document row is still the live save")
    }

    @Test
    fun healRunsOnTablesAndEverythingComesBackAfterReopeningTheFile() {
        val f = File.createTempFile("heal", ".db").also { it.delete(); it.deleteOnExit() }
        val g = game()
        g.heal(app.pokeplan.core.reward.HealEvent("x", app.pokeplan.core.reward.HealKind.ACTIVITY, start, "x")) // some game history to leave alone
        val before: app.pokeplan.heal.HealData
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1); s.live.moveHealToTables(2)
            val heal = healOn(s, g)
            heal.setFieldResearch(true)
            heal.logActivity(ActivityType.RUN, 40, distanceMeters = 6500, note = "morning")
            heal.logDrink(300, "tea")
            heal.logSleep(start - 8 * 3_600_000L, start)
            heal.logMeasurement(MeasureType.WEIGHT, 70.5, "kg")
            heal.addGoal(app.pokeplan.heal.GoalKind.WEEKLY_ACTIVE_MINUTES, 150)
            heal.setHydrationReminders(listOf(kotlinx.datetime.LocalTime(10, 0)))
            before = heal.data
            assertEquals(1L, count(s, "heal_activity")); assertEquals(1L, count(s, "heal_sleep")); assertEquals(1L, count(s, "heal_measurement"))
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.healTables.isActive())
        assertEquals(before, healOn(again, g).data, "everything HEAL kept is back, field for field")
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        val heal = healOn(s, game())
        heal.logActivity(ActivityType.WALK, 30)
        heal.logDrink(250)
        heal.logSleep(start - 8 * 3_600_000L, start)
        countWrites(s)
        heal.logDrink(250) // one new row in one table, nothing else rewritten
        assertEquals(1L, writes(s))
        heal.setShowMeasurementsOnToday(true) // the settings row
        assertEquals(2L, writes(s))
        val a = heal.data.activities.single()
        heal.updateActivity(a.copy(note = "x"))
        assertEquals(3L, writes(s), "only the changed activity row was replaced")
    }

    @Test
    fun eraseingOneKindClearsItsFamilyAndNothingElse() {
        val s = live()
        val g = game()
        val heal = healOn(s, g)
        heal.logActivity(ActivityType.WALK, 30)
        heal.logDrink(250)
        heal.logSleep(start - 8 * 3_600_000L, start)
        heal.logMeasurement(MeasureType.WEIGHT, 70.0, "kg")
        s.live.write("save", GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), g.data), 3)
        val saveBefore = s.live.read("save")
        s.healTables.erase("sleep")
        assertEquals(0, count(s, "heal_sleep"))
        assertEquals(1, count(s, "heal_activity")); assertEquals(1, count(s, "heal_hydration")); assertEquals(1, count(s, "heal_measurement"))
        assertEquals(saveBefore, s.live.read("save"), "the game's save is untouched")
        // The same through the store's own erase, then read back through the tables.
        val fresh = healOn(s, g)
        fresh.erase(HealKinds.MEASUREMENTS)
        assertEquals(0, count(s, "heal_measurement"))
        assertEquals(1, count(s, "heal_activity"))
        assertEquals("[]", s.healTables.readKind("sleep"))
        assertTrue(s.healTables.readKind("activities").contains("WALK"))
        assertFailsWith<IllegalStateException> { s.healTables.erase("nonsense") }
        s.healTables.clear()
        assertEquals(0L, KINDS.sumOf { count(s, it) } + count(s, "heal_state"))
        assertEquals(saveBefore, s.live.read("save"))
        assertNull(s.live.read("heal"))
    }

    private val KINDS = listOf("heal_activity", "heal_hydration", "heal_sleep", "heal_measurement", "heal_goal")

    @Test
    fun theBackupSnapshotCarriesHealAndARestoreOfAnOlderBackupLeavesItAlone() {
        val s = live()
        val g = game()
        val heal = healOn(s, g)
        s.live.write("save", GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), g.data), 3)
        heal.logActivity(ActivityType.WALK, 30, note = "kept")
        val snap = s.live.snapshot()
        assertTrue("heal" in snap && snap.getValue("heal").contains("kept"))
        assertTrue("heal" in s.live.names())
        // A backup made before HEAL existed holds no heal document: restoring it must not wipe health data.
        s.live.replaceAll(mapOf("save" to snap.getValue("save")), 9)
        assertEquals(1, healOn(s, g).data.activities.size)
        // A backup with a heal document brings it back, and the replaced one is kept aside.
        heal.logDrink(500)
        s.live.replaceAll(snap, 11)
        assertEquals(0, healOn(s, g).data.hydration.size)
        assertTrue(s.live.read("aside:heal:11")!!.contains("hydration"))
    }

    @Test
    fun theBundleKnowsHealAndRefusesAnythingElse() {
        val docs = mapOf("save" to "{}", "heal" to """{"activities":[]}""")
        val text = BackupBundle.encode(docs, DataStore.SCHEMA_VERSION, 7)
        val staged = BackupBundle.stage(text, DataStore.SCHEMA_VERSION)
        assertTrue(staged is BackupBundle.Staged.Ready && staged.documents.keys == setOf("save", "heal"))
        assertEquals(8, DataStore.SCHEMA_VERSION) // Heal made it 7 (0.136.0), Mint's tables 8 (0.141.0)
        val bad = BackupBundle.encode(mapOf("save" to "{}"), 7, 7).replace("\"save\"", "\"health\"")
        assertTrue(BackupBundle.stage(bad, 7) is BackupBundle.Staged.Refused)
    }

    @Test
    fun theExportOfOneKindIsTheRowsOfThatKindAndNothingElse() {
        val s = live()
        val heal = healOn(s, game())
        heal.logActivity(ActivityType.WALK, 30, note = "park")
        heal.logMeasurement(MeasureType.WEIGHT, 70.0, "kg")
        val exported = obj(HealExport.json(heal.data, setOf(HealKinds.ACTIVITY)))
        assertEquals(setOf("format", "activities"), exported.keys)
        assertEquals(1, (exported["activities"] as JsonArray).size)
        assertEquals(obj(s.healTables.readKind("activities").let { """{"a":$it}""" })["a"], exported["activities"] , "the table family and the export agree")
    }

    @Test
    fun healsPartyAndLeadSurviveTheSharedPokemonTablesAndStayOnePlaceEach() {
        val g = game()
        g.completeTutorial(app.pokeplan.app.Tutorials.HEAL_WALK)
        val second = g.data.player.buddyId!!
        assertTrue(g.setInParty(second, true, app.pokeplan.core.pokemon.PartyIds.HEAL))
        assertTrue(g.setHealLead(second))
        val doc = GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), g.data)
        val s = JvmDatabase.open()
        s.live.normalizer = GameStore::normalizeDocument
        s.live.activate(doc, null, 5)
        s.live.movePlanToTables(6)
        s.live.movePokemonToTables(7)
        val back = GameStore.json.decodeFromString(app.pokeplan.app.AppData.serializer(), s.live.read("save")!!)
        assertEquals(g.data.player.partyOf("heal").map { it.id }.toSet(), back.player.partyOf("heal").map { it.id }.toSet())
        assertEquals(second, back.player.healLead?.id, "the chosen lead leads, whatever order the members joined in")
        assertEquals(2, back.player.partyOf("heal").size)
        // The Heal tables and the shared tables are separate: the player's side can be erased without touching a Heal record.
        val heal = healOn(s, g)
        heal.logActivity(ActivityType.WALK, 30)
        s.live.movePokemonToTables(8)
        assertEquals(1, healOn(s, g).data.activities.size)
    }
}
