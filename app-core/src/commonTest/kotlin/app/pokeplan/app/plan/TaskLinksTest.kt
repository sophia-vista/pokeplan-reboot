package app.pokeplan.app.plan

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.base.Recurrence
import app.pokeplan.base.RepeatKind
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.World
import app.pokeplan.app.base.BaseStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class TaskLinksTest {

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<BaseEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun base(event: BaseEvent): List<Reward>? { events += event; return inner.base(event) }
    }

    private class Setup(val game: GameStore, val home: BaseStore, val spy: Spy, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val spy = Spy(game)
        val home = BaseStore(MemoryStorage(), clock, spy, utc, rng = Random(3))
        game.registerLinkOwner(BaseLinkOwner(home))
        home.observe { game.syncLinks() }
        return Setup(game, home, spy, clock)
    }

    private val Setup.tasks get() = game.data.tasks.filter { !it.tutorial }

    private fun Setup.weeklyChore() = home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 7)).record!!

    @Test fun `linking a chore makes one task that follows the chore and is idempotent`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = assertNotNull(s.game.linkChore(chore.id))
        assertEquals("Vacuum", task.title)
        assertEquals(LinkKind.HOME_CHORE, task.link?.kind)
        assertEquals(chore.id, task.link?.ref)
        assertEquals(task.id, s.game.linkChore(chore.id)?.id)
        assertEquals(1, s.tasks.size)
        s.home.updateChore(chore.copy(title = "Vacuum the stairs"))
        assertEquals("Vacuum the stairs", s.tasks.single().title)
    }

    @Test fun `completing the linked task completes the chore once and PLAN pays nothing`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        val before = s.game.data.player.coins to s.game.data.activeDays
        val pending = s.game.completeTask(task.id)
        assertFalse(pending.pays)
        s.clock.advanceMinutes(1)
        s.game.tick()
        assertEquals(1, s.spy.events.size)
        assertEquals(1, s.home.data.entries.count { it.choreId == chore.id })
        assertTrue(s.home.data.entries.single().paid)
        assertEquals(TaskState.DONE, s.tasks.single().state)
        assertEquals(before.second, s.game.data.activeDays)
        assertTrue(s.game.data.rewardLog.none { it.source.startsWith("Task:") })
    }

    @Test fun `completing in HOME shows the task done and still pays once`() {
        val s = setup()
        val chore = s.weeklyChore()
        s.game.linkChore(chore.id)
        s.home.complete(chore.id)
        assertEquals(TaskState.DONE, s.tasks.single().state)
        assertEquals(1, s.spy.events.size)
        s.game.completeTask(s.tasks.single().id)
        assertEquals(1, s.spy.events.size)
    }

    @Test fun `a module reward leaves a ledger note with the event's own id and the real change`() {
        val s = setup()
        val chore = s.weeklyChore()
        val coins = s.game.data.player.coins
        val candies = s.game.data.player.inventory.counts.values.sum()
        val task = s.game.linkChore(chore.id)!!
        s.game.completeTask(task.id)
        val note = s.game.data.ledgerNotes.single()
        assertEquals(s.spy.events.single().id, note.eventId)
        assertEquals("HOME", note.module)
        assertEquals("chore", note.kind)
        assertEquals(s.game.data.player.coins - coins, note.poke.toInt())
        assertEquals((s.game.data.player.inventory.counts.values.sum() - candies).toLong(), note.items.values.sum())
        s.game.undoCompletion(task.id)
        s.game.completeTask(task.id)
        assertEquals(1, s.game.data.ledgerNotes.size)
        assertTrue(!note.toString().contains("Vacuum"))
    }

    @Test fun `undo and a second tick pay the occurrence once`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        s.game.completeTask(task.id)
        assertTrue(s.game.undoCompletion(task.id))
        assertEquals(TaskState.OPEN, s.tasks.single().state)
        assertTrue(s.home.data.entries.isEmpty())
        s.game.completeTask(task.id)
        s.clock.advanceMinutes(1); s.game.tick()
        assertEquals(1, s.game.data.basePaid.size)
    }

    @Test fun `reopening a linked task takes back HOME's completion and won't do is HOME's skip`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        s.clock.advanceMinutes(1)
        s.game.completeTask(task.id)
        s.clock.advanceMinutes(1); s.game.tick()
        s.game.reopen(task.id)
        assertTrue(s.home.data.entries.isEmpty())
        assertEquals(TaskState.OPEN, s.tasks.single().state)
        s.game.wontDo(task.id)
        assertEquals(app.pokeplan.base.EntryStatus.SKIPPED, s.home.data.entries.single().status)
        assertEquals(TaskState.WONT_DO, s.tasks.single().state)
        assertEquals(0, s.spy.events.size - 1, "only the first completion was paid")
    }

    @Test fun `a repeating chore's task moves on to the next occurrence`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        s.game.completeTask(task.id)
        s.clock.advanceDays(7); s.game.refresh()
        val next = s.tasks.single()
        assertEquals(task.id, next.id)
        assertEquals(TaskState.OPEN, next.state)
        assertEquals(LocalDate(2026, 11, 14), next.planned)
        assertNotEquals(task.link?.occurrence, next.link?.occurrence)
        s.game.completeTask(next.id)
        s.clock.advanceMinutes(1); s.game.tick()
        assertEquals(2, s.spy.events.size)
        assertEquals(2, s.spy.events.map { it.id }.toSet().size)
    }

    @Test fun `unlinking removes the projection and leaves the chore alone`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        s.game.unlinkTask(task.id)
        assertEquals(TaskState.DELETED, s.tasks.single().state)
        assertNull(s.game.linkedTask(LinkKind.HOME_CHORE, chore.id))
        assertEquals(1, s.home.data.chores.size)
        assertNotNull(s.game.linkChore(chore.id))
    }

    @Test fun `deleting the chore removes its projection and a paused chore is not waiting`() {
        val s = setup()
        val chore = s.weeklyChore()
        s.game.linkChore(chore.id)
        s.home.setChoreStatus(chore.id, app.pokeplan.base.RecordStatus.PAUSED)
        assertEquals(TaskState.WONT_DO, s.tasks.single().state)
        s.home.setChoreStatus(chore.id, app.pokeplan.base.RecordStatus.ACTIVE)
        assertEquals(TaskState.OPEN, s.tasks.single().state)
        s.home.deleteChore(chore.id)
        assertEquals(TaskState.DELETED, s.tasks.single().state)
    }

    @Test fun `a linked task never repeats on its own and its steps pay nothing`() {
        val s = setup()
        val chore = s.weeklyChore()
        val task = s.game.linkChore(chore.id)!!
        assertFalse(s.game.makeRepeating(task.id, app.pokeplan.plan.Repeat.Daily()))
        s.game.addSubtask(task.id, "Get the vacuum")
        s.clock.advanceDays(1)
        val sub = s.tasks.single().subtasks.single()
        assertFalse(s.game.completeSubtask(task.id, sub.id))
    }

    @Test fun `without its owner a linked task is not completed by PLAN`() {
        val clock = FakeClock(saturday)
        val shared = MemoryStorage()
        val game = GameStore(shared, clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val home = BaseStore(MemoryStorage(), clock, game, utc, rng = Random(3))
        val chore = home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 7)).record!!
        game.registerLinkOwner(BaseLinkOwner(home))
        val task = game.linkChore(chore.id)!!
        val widget = GameStore(shared, clock, utc, Random(1))
        widget.completeTask(task.id)
        assertEquals(TaskState.OPEN, widget.data.tasks.filter { !it.tutorial }.single().state)
        assertTrue(home.data.entries.isEmpty())
    }

    @Test fun `a converted follow-up is a PLAN task that pays once by PLAN's rules and LINK sees its status`() {
        val s = setup()
        val task = assertNotNull(s.game.convertFollowUp("fu-1", "Send the photos", planned = LocalDate(2026, 11, 9)))
        assertEquals(LinkKind.LINK_FOLLOW_UP, task.link?.kind)
        assertFalse(task.ownedElsewhere)
        assertEquals(task.id, s.game.convertFollowUp("fu-1", "Send the photos")?.id)
        assertEquals(1, s.tasks.size)
        assertEquals(TaskState.OPEN, s.game.linkedTask(LinkKind.LINK_FOLLOW_UP, "fu-1")?.state)
        s.clock.advanceMinutes(30)
        val pending = s.game.completeTask(task.id)
        assertTrue(pending.pays)
        s.clock.advanceMinutes(1); s.game.tick()
        assertEquals(TaskState.DONE, s.game.linkedTask(LinkKind.LINK_FOLLOW_UP, "fu-1")?.state)
        s.game.reopen(task.id)
        s.clock.advanceMinutes(30)
        assertFalse(s.game.completeTask(task.id).pays)
        s.game.unlinkTask(task.id)
        assertNull(s.tasks.single().link)
        assertTrue(s.tasks.single().paid)
    }

    @Test fun `a blank follow-up is not converted`() {
        val s = setup()
        assertNull(s.game.convertFollowUp("fu-2", "  "))
        assertTrue(s.tasks.isEmpty())
    }
}
