package app.pokeplan.data

import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.MemoryStorage
import app.pokeplan.app.Storage
import app.pokeplan.care.CareAction
import app.pokeplan.care.CareData
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The real Care rules (CareStore) running on Care's tables through the live documents, as the Android app runs them. */
class CareStoreOnTablesTest {
    private class TablesStorage(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("care")
        override fun save(text: String) = live.write("care", text, 1)
        override fun keepAside(text: String) = live.keepAside("care", text, 1)
    }

    private fun fixture(name: String) = javaClass.getResource("/fixtures/0.123.0/$name")!!.readText()
    private val start = 1_793_000_000_000L // 2026-10-26

    private fun careOn(store: DataStore, clock: Clock, game: GameStore) = CareStore(TablesStorage(store.live), clock, game, TimeZone.UTC, rng = Random(3))

    @Test
    fun theRealCareFixtureLoadsThroughTheTablesAsTheSameData() {
        val s = JvmDatabase.open()
        s.live.activate(null, fixture("care-week.json"), 1)
        s.live.moveCareToTables(2)
        assertEquals(CareStore.json.decodeFromString(CareData.serializer(), fixture("care-week.json")), CareStore.json.decodeFromString(CareData.serializer(), s.live.read("care")!!))
        assertEquals(1, s.driver.executeQuery(null, "SELECT count(*) FROM care_routine", { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value)
    }

    @Test
    fun careRunsOnTablesAndEverythingComesBackAfterReopeningTheFile() {
        val f = File.createTempFile("care", ".db").also { it.delete(); it.deleteOnExit() }
        val clock = object : Clock { var millis = start; override fun nowMillis() = millis }
        val game = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
        val before: CareData
        JvmDatabase.open(f.path).let { s ->
            s.live.activate(null, null, 1)
            s.live.moveCareToTables(2)
            val care = careOn(s, clock, game)
            care.completeRoutine(care.addRoutine("Stretch").id)
            care.saveDiary(emptyMap(), note = "private")
            care.write("a journal line")
            val visitor = care.visitors().first()
            care.startVisit(visitor.speciesId)
            care.visitAct(visitor.favorite)
            care.leaveVisit()
            care.dismiss(care.visitors().last().speciesId)
            before = care.data
            assertTrue(before.visitors.isNotEmpty() && before.dismissed.size == 1 && before.visitors.any { it.progress > 0 })
            s.close()
        }
        val again = JvmDatabase.open(f.path)
        assertTrue(again.integrityIssues().isEmpty())
        assertEquals(before, careOn(again, clock, game).data, "everything Care kept is back, field for field")
        // Private records are rows of their own: erasing the journal does not touch the diary or the routines.
        again.driver.execute(null, "DELETE FROM care_journal_entry", 0)
        val after = careOn(again, clock, game).data
        assertTrue(after.journal.isEmpty() && after.diary.size == 1 && after.routines.size == 1)
        assertNotNull(after.visitors.firstOrNull { it.progress > 0 })
    }
}
