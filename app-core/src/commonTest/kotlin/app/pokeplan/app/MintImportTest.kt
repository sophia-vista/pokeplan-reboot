package app.pokeplan.app

import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.StatementImport
import app.pokeplan.mint.TxSource
import app.pokeplan.mint.TxType
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A user-assisted statement import (INTEGRATIONS_AND_PRIVACY.md 7.2, 10.2): previewed, idempotent, provenance-keeping, removable on its own, and never a reward. */
class MintImportTest {
    private class FakeClock(var millis: Long) : Clock { override fun nowMillis() = millis }
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<MintEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun mint(event: MintEvent): List<Reward>? { events += event; return inner.mint(event) }
    }

    private val csv = "Date,Description,Amount\n2026-11-01,Coffee,-120.00\n2026-11-01,Coffee,-120.00\n2026-11-02,Salary,\"25,000.00\"\nnot a date,Rent,-5.00\n"

    private fun setup(): Triple<MintStore, Spy, String> {
        val clock = FakeClock(1_794_000_000_000L)
        val game = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
        val spy = Spy(game)
        val mint = MintStore(MemoryStorage(), clock, spy, TimeZone.UTC, rng = Random(3))
        val account = assertNotNull(mint.addAccount("Wallet", AccountType.EWALLET, "PHP").record).id
        return Triple(mint, spy, account)
    }

    @Test fun importsThePreviewedRowsOnceWithProvenanceAndPaysNothing() {
        val (mint, spy, account) = setup()
        val p = mint.previewStatement(csv, account, order = StatementImport.DateOrder.YEAR_FIRST)
        val preview = assertNotNull(p.preview)
        assertEquals(3, preview.new); assertEquals(1, preview.problems)
        val result = mint.importStatement(preview, preview.rows.filter { it.keptByDefault }.map { it.line }.toSet(), "wallet-nov.csv")
        assertEquals(3, result.added)
        val saved = mint.data.transactions
        assertEquals(3, saved.size)
        assertTrue(saved.all { it.source == TxSource.IMPORTED && it.importBatch == result.batch && it.importLabel == "wallet-nov.csv" && it.fingerprint != null && it.unreviewed })
        assertEquals(-12_000 * 2 + 2_500_000L, MintRules.balance(mint.data, mint.data.accounts.single()).postedMinor)
        assertTrue(spy.events.isEmpty(), "an import is not a reward event")
        // The same file again adds nothing: everything is recognised, even two identical coffees.
        val again = assertNotNull(mint.previewStatement(csv, account, order = StatementImport.DateOrder.YEAR_FIRST).preview)
        assertEquals(3, again.duplicates); assertEquals(0, again.new)
        assertEquals(0, mint.importStatement(again, again.rows.map { it.line }.toSet(), "wallet-nov.csv").added)
        assertEquals(3, mint.data.transactions.size)
    }

    @Test fun anInterruptedRetryOfTheSamePreviewDoesNotDuplicate() {
        val (mint, _, account) = setup()
        val preview = assertNotNull(mint.previewStatement(csv, account, order = StatementImport.DateOrder.YEAR_FIRST).preview)
        val keep = preview.rows.filter { it.keptByDefault }.map { it.line }.toSet()
        mint.importStatement(preview, keep, "a.csv")
        assertEquals(0, mint.importStatement(preview, keep, "a.csv").added)
        assertEquals(3, mint.data.transactions.size)
    }

    @Test fun removingABatchLeavesWhatThePlayerWrote() {
        val (mint, _, account) = setup()
        val mine = assertNotNull(mint.record(TxType.EXPENSE, 9_900, account, payee = "Lunch").record)
        val preview = assertNotNull(mint.previewStatement(csv, account, order = StatementImport.DateOrder.YEAR_FIRST).preview)
        val batch = mint.importStatement(preview, preview.rows.filter { it.keptByDefault }.map { it.line }.toSet(), "a.csv").batch!!
        assertEquals(1, mint.importBatches().size); assertEquals(3, mint.importBatches().single().unreviewed)
        mint.markBatchReviewed(batch)
        assertEquals(0, mint.importBatches().single().unreviewed)
        assertEquals(3, mint.removeImportBatch(batch))
        assertEquals(listOf(mine.id), mint.data.transactions.map { it.id })
        assertTrue(mint.importBatches().isEmpty())
    }

    @Test fun editingAnImportedRecordKeepsItsProvenance() {
        val (mint, _, account) = setup()
        val preview = assertNotNull(mint.previewStatement(csv, account, order = StatementImport.DateOrder.YEAR_FIRST).preview)
        mint.importStatement(preview, preview.rows.filter { it.keptByDefault }.map { it.line }.toSet(), "a.csv")
        val t = mint.data.transactions.first()
        val edited = assertNotNull(mint.updateTransaction(t.copy(payee = "Tea", importBatch = null, fingerprint = null, source = TxSource.MANUAL)).record)
        assertEquals(t.importBatch, edited.importBatch); assertEquals(t.fingerprint, edited.fingerprint); assertEquals(TxSource.IMPORTED, edited.source)
    }

    @Test fun anUnreadableFileIsRefusedInWordsAndSavesNothing() {
        val (mint, _, account) = setup()
        assertEquals("That file is empty.", mint.previewStatement("  ", account).refused)
        assertNull(mint.previewStatement("Date,Amount\n2026-11-01,5", "gone").preview)
        assertFalse(mint.previewStatement("Foo,Bar\nx,y", account).columns.usable)
        assertTrue(mint.data.transactions.isEmpty())
    }
}
