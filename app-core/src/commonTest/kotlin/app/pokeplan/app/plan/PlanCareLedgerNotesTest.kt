package app.pokeplan.app.plan

import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.plan.Rank
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

/** PLAN's and CARE's rewards leave a ledger note under their own event id (DATA_AND_MIGRATIONS.md section 7.3.0, step 2). */
class PlanCareLedgerNotesTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advance(ms: Long) { millis += ms }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 3, 9, 0).toInstant(utc).toEpochMilliseconds()

    private fun started(): Pair<GameStore, FakeClock> {
        val clock = FakeClock(start)
        return GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) } to clock
    }

    @Test fun `a finished task is noted as plan task complete with the real change and none of its words`() {
        val (s, clock) = started()
        val task = s.addTask("Secret errand", Rank.C)
        clock.advance(10 * 60_000L)
        val before = s.data.player
        s.completeTask(task.id)
        clock.advance(60 * 60_000L)
        s.tick()
        val note = s.data.ledgerNotes.single()
        assertEquals("plan:task:${task.id}:complete", note.eventId)
        assertEquals("PLAN", note.module)
        assertEquals(task.id, note.ref)
        assertEquals((s.data.player.coins - before.coins).toLong(), note.poke)
        assertTrue(!note.toString().contains("Secret"))
    }

    @Test fun `a focus session is noted once under its egg's id`() {
        val (s, clock) = started()
        s.startFocus(25, deepFocus = false)
        clock.advance(25 * 60_000L + 1)
        s.tickFocus()
        val notes = s.data.ledgerNotes.filter { it.module == "PLAN" }
        assertEquals(1, notes.size)
        assertTrue(notes.single().eventId.startsWith("plan:focus:"))
        s.tickFocus()
        assertEquals(1, s.data.ledgerNotes.count { it.module == "PLAN" })
    }

    @Test fun `a Care moment is noted by kind, day and count and a capped one leaves nothing`() {
        val (s, clock) = started()
        val first = s.care(CareEvent(CareKind.ROUTINE, clock.millis))
        val notes = s.data.ledgerNotes.filter { it.module == "CARE" }
        assertTrue(first != null && notes.isNotEmpty(), "the first routine of the day pays and is noted")
        assertEquals("care:routine:${s.today.toEpochDays()}:1", notes.first().eventId)
        repeat(30) { s.care(CareEvent(CareKind.ROUTINE, clock.millis)) }
        val ids = s.data.ledgerNotes.filter { it.module == "CARE" }.map { it.eventId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("care:routine:") })
    }
}
