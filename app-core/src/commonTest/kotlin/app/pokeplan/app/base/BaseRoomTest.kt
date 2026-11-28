package app.pokeplan.app.base

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.World

import app.pokeplan.base.BaseKinds
import app.pokeplan.base.Decorations
import app.pokeplan.base.Placement
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.Reward
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseRoomTest {

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Bridge(val inner: GameStore) : World {
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun base(event: BaseEvent): List<Reward>? = inner.base(event)
        override fun ownedDecorations(): Set<String> = inner.ownedDecorations()
    }

    private class Setup(val game: GameStore, val home: BaseStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        game.start("Sophia", 25)
        return Setup(game, BaseStore(MemoryStorage(), clock, Bridge(game), utc, rng = Random(3)), clock)
    }

    private fun Setup.doChore(title: String): List<Reward> {
        val c = assertNotNull(home.addChore(title, app.pokeplan.base.Recurrence(app.pokeplan.base.RepeatKind.DAILY)).record)
        return home.complete(c.id).rewards
    }

    private fun List<Reward>.decorations() = filterIsInstance<Reward.Decoration>()

    @Test
    fun aNewSaveStartsWithThreeDecorationsAndAnEmptyRoom() {
        val s = setup()
        assertEquals(Decorations.STARTERS, s.game.data.decorations)
        assertTrue(s.home.data.room.isEmpty())
        assertEquals(Decorations.STARTERS.toSet(), s.game.ownedDecorations())
        assertEquals(Decorations.STARTERS, s.home.inBox().map { it.id }.sortedBy { Decorations.STARTERS.indexOf(it) })
    }

    @Test
    fun fiveChoresFindOneDecorationNotOwnedAndTheMeterStartsAgain() {
        val s = setup()
        val found = (1..4).map { s.doChore("chore $it").decorations() }
        assertTrue(found.all { it.isEmpty() }, "four points find nothing")
        assertEquals(4, s.game.decorationMeter())
        val fifth = s.doChore("chore 5").decorations()
        val one = fifth.single()
        assertTrue(one.decorationId !in Decorations.STARTERS && Decorations.get(one.decorationId) != null)
        assertEquals(4, s.game.data.decorations.size)
        assertEquals(0, s.game.decorationMeter())
    }

    @Test
    fun maintenanceIsTwoPointsAndTheRestCarriesOver() {
        val s = setup()
        s.doChore("a"); s.doChore("b"); s.doChore("c")
        assertEquals(3, s.game.decorationMeter())
        val sch = assertNotNull(s.home.addSchedule("Service the boiler", s.home.today).record)
        val out = s.home.completeMaintenance(sch.id)
        assertEquals(1, out.rewards.decorations().size, "3 + 2 = 5 points")
        assertEquals(0, s.game.decorationMeter())
        val sch2 = assertNotNull(s.home.addSchedule("Check the smoke alarm", s.home.today).record)
        s.home.completeMaintenance(sch2.id)
        assertEquals(2, s.game.decorationMeter())
    }

    @Test
    fun undoAndDoneAgainPaysTheDecorationOnceAndNothingIsTakenBack() {
        val s = setup()
        repeat(4) { s.doChore("chore $it") }
        val c = assertNotNull(s.home.addChore("last", app.pokeplan.base.Recurrence(app.pokeplan.base.RepeatKind.DAILY)).record)
        val entry = assertNotNull(s.home.complete(c.id).record)
        assertEquals(4, s.game.data.decorations.size)
        s.home.undo(entry.id)
        assertEquals(4, s.game.data.decorations.size, "an undo takes nothing back")
        assertTrue(s.home.complete(c.id).rewards.decorations().isEmpty(), "the same occurrence pays once")
        assertEquals(4, s.game.data.decorations.size)
        assertEquals(0, s.game.decorationMeter())
    }

    @Test
    fun theDailyCapBoundsTheMeterAndTurningDiscoveryOffStopsIt() {
        val s = setup()
        repeat(7) { s.doChore("chore $it") }
        assertEquals(1, s.game.data.decorations.size - Decorations.STARTERS.size)
        assertEquals(0, s.game.decorationMeter())
        val t = setup()
        t.home.setProgression(false)
        repeat(6) { t.doChore("chore $it") }
        assertEquals(Decorations.STARTERS, t.game.data.decorations)
        assertEquals(0, t.game.decorationMeter())
    }

    @Test
    fun anEventWithTheSameIdFindsTheSameDecorationWhoeverAsks() {
        val a = Decorations.pick(Decorations.STARTERS, "home:chore:x:2026-11-07")
        val b = Decorations.pick(Decorations.STARTERS, "home:chore:x:2026-11-07")
        assertEquals(a, b)
    }

    @Test
    fun placingNeedsAnOwnedDecorationAnEmptyCellAndSurvivesAReopen() {
        val s = setup()
        assertNull(s.home.placeDecoration("snorlax_doll", 4, 4))
        assertNotNull(s.home.placeDecoration("tire", 5, 4), "not owned")
        assertNotNull(s.home.placeDecoration("thunder_mat", 4, 4), "the cell is taken")
        assertNull(s.home.placeDecoration("thunder_mat", 5, 4))
        assertEquals(listOf("big_plant"), s.home.inBox().map { it.id })
        assertNull(s.home.moveDecoration("snorlax_doll", 6, 5))
        assertEquals(setOf(Placement("snorlax_doll", 6, 5), Placement("thunder_mat", 5, 4)), s.home.room.toSet())
        assertNull(s.home.removeDecoration("snorlax_doll"))
        assertTrue("snorlax_doll" in s.home.inBox().map { it.id } && "snorlax_doll" in s.game.ownedDecorations(), "removing never loses it")
        s.home.erase(BaseKinds.ROOM)
        assertTrue(s.home.data.room.isEmpty())
        assertEquals(Decorations.STARTERS.toSet(), s.game.ownedDecorations(), "erasing the room keeps what is owned")
    }

    @Test
    fun theStartersAreGivenOnceEvenAfterTheRoomIsEmptied() {
        val clock = FakeClock(saturday)
        val storage = MemoryStorage()
        val game = GameStore(storage, clock, utc, Random(7))
        game.start("Sophia", 25)
        assertEquals(3, game.data.decorations.size)
        val again = GameStore(storage, clock, utc, Random(8))
        assertEquals(3, again.data.decorations.size, "a reopened save is not given them twice")
        assertTrue(again.data.decorationStarters)
    }
}
