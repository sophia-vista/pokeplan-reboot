package app.pokeplan.app

import app.pokeplan.plan.Rank
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import app.pokeplan.app.care.CareStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class SaveFixtureTest {
    private class Clock(var millis: Long) : app.pokeplan.app.storage.Clock { override fun nowMillis() = millis }

    private val folder = File("src/jvmTest/resources/fixtures/0.123.0")
    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_793_491_200_000L

    private fun newStore(clock: Clock, text: String? = null) = GameStore(MemoryStorage(text), clock, TimeZone.UTC, Random(7))

    private fun encode(store: GameStore) = GameStore.json.encodeToString(AppData.serializer(), store.data)

    private fun useADay(store: GameStore, clock: Clock, n: Int) {
        val tasks = (1..3).map { store.addTask("Task $n.$it", Rank.entries[it % Rank.entries.size]) }
        tasks.take(2).forEach { store.completeTask(it.id) }
        clock.millis += 61_000
        store.tick()
        store.startFocus(25, deepFocus = n % 3 == 0)
        clock.millis += 26 * 60_000
        store.tickFocus()
        clock.millis += day - 87_000
    }

    private fun scenarios(): Map<String, String> {
        val out = linkedMapOf<String, String>()

        out["main-empty"] = encode(newStore(Clock(start)))

        val clock = Clock(start)
        val store = newStore(clock)
        store.addRecurring(app.pokeplan.plan.Task(id = "", title = "Stretch", createdAtMillis = start), app.pokeplan.plan.Recurrence(app.pokeplan.plan.Repeat.Daily(1), store.today))
        store.addGoal("Read a book")
        repeat(7) { useADay(store, clock, it) }
        store.addTask("Still to do", Rank.B)
        out["main-typical-week"] = encode(store)

        repeat(63) { useADay(store, clock, it + 7) }
        out["main-heavy-70-days"] = encode(store)

        val c2 = Clock(start)
        val s2 = newStore(c2)
        val t = s2.addTask("Widget task", Rank.C)
        s2.completeTask(t.id)
        s2.startFocus(45, deepFocus = false)
        c2.millis += 5 * 60_000
        out["main-focus-running-and-pending"] = encode(s2)

        val c3 = Clock(start)
        val main = newStore(c3)
        val care = CareStore(MemoryStorage(), c3, main, TimeZone.UTC, rng = Random(7))
        val routine = care.addRoutine("Water", days = emptySet())
        repeat(7) { care.completeRoutine(routine.id); c3.millis += day }
        out["care-week"] = GameStore.json.encodeToString(app.pokeplan.care.CareData.serializer(), care.data)
        return out
    }

    @Test
    fun writeTheBaselineWhenAskedTo() {
        if (System.getenv("POKEPLAN_WRITE_FIXTURES") != "1") return
        folder.mkdirs()
        scenarios().forEach { (name, text) -> File(folder, "$name.json").writeText(text) }
    }

    @Test
    fun everyMainFixtureLoadsUntouchedAndKeepsWhatItHolds() {
        val files = folder.listFiles { f -> f.name.startsWith("main-") && f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue(files.size >= 4, "the fixtures are missing in ${folder.absolutePath}")
        for (file in files) {
            val text = file.readText()
            val storage = MemoryStorage(text)
            val store = GameStore(storage, Clock(start), TimeZone.UTC, Random(7))
            assertNull(storage.keptAside, "${file.name} was set aside as damaged")
            val written = GameStore.json.decodeFromString(AppData.serializer(), text)
            assertEquals(written.tasks, store.data.tasks, "${file.name}: tasks changed on load")
            assertEquals(written.player.pokemon.size, store.data.player.pokemon.size, "${file.name}: Pokémon changed on load")
            assertEquals(written.rewardLog.size, store.data.rewardLog.size, "${file.name}: reward log changed on load")
            assertEquals(AppData.SCHEMA, store.data.schema)
        }
    }

    @Test
    fun everyCareFixtureLoadsUntouched() {
        val files = folder.listFiles { f -> f.name.startsWith("care-") && f.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty(), "the Care fixtures are missing")
        for (file in files) {
            val storage = MemoryStorage(file.readText())
            val care = CareStore(storage, Clock(start), newStore(Clock(start)), TimeZone.UTC)
            assertNull(storage.keptAside, "${file.name} was set aside as damaged")
            assertTrue(care.data.routines.isNotEmpty(), "${file.name} lost its routines")
        }
    }
}
