package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.SharedKind
import app.pokeplan.mint.SharedRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared bills (MINT.md 5.8A): kept, settled, exported and erased on their own, and touching nothing else in Mint or the game. */
class MintSharedTest {
    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 11, 20)

    private fun stores(): Pair<MintStore, GameStore> {
        val clock = Clock { 1_800_000_000_000L }
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to game
    }

    @Test fun anEntryIsKeptAndSettledAndMovesNoAccountOrReward() {
        val (m, game) = stores()
        val before = game.data.player
        assertNull(m.addShared("Ana", SharedKind.I_PAID, 50_000, day, " dinner ", "PHP"))
        assertEquals("dinner", m.data.shared.single().note)
        val balance = SharedRules.balances(m.data.shared).single()
        m.settleShared(balance, day)
        assertEquals(emptyList(), SharedRules.balances(m.data.shared))
        assertEquals(2, m.data.shared.size, "a settlement is one more entry, nothing is rewritten")
        assertTrue(m.data.accounts.isEmpty() && m.data.transactions.isEmpty(), "no account or transaction was made")
        assertEquals(before, game.data.player, "nothing was paid")
    }

    @Test fun anEntryWithoutAWhoOrAnAmountIsRefusedWithAReason() {
        val (m, _) = stores()
        assertNotNull(m.addShared("", SharedKind.I_PAID, 100, day, "", "PHP"))
        assertNotNull(m.addShared("Ana", SharedKind.THEY_PAID, 0, day, "", "PHP"))
        assertTrue(m.data.shared.isEmpty())
    }

    @Test fun theyExportAndEraseAsAKindOfTheirOwn() {
        val (m, _) = stores()
        m.addShared("Ben", SharedKind.THEY_PAID, 30_000, day, "", "PHP")
        assertTrue("\"shared\"" in m.export(setOf(MintKinds.SHARED)))
        assertTrue("Ben" !in m.export(setOf(MintKinds.BUDGETS)))
        m.erase(MintKinds.SHARED)
        assertTrue(m.data.shared.isEmpty())
        m.addShared("Ben", SharedKind.THEY_PAID, 30_000, day, "", "PHP")
        m.eraseAll()
        assertTrue(m.data.shared.isEmpty())
    }

    @Test fun aFullResetBringsMintBackAsNew() {
        val (m, _) = stores()
        m.addShared("Ana", SharedKind.I_PAID, 100, day, "", "PHP")
        m.setGate(true)
        m.resetAll()
        assertTrue(m.data.shared.isEmpty())
        assertEquals(false, m.data.settings.gate)
        assertTrue(m.data.categories.isNotEmpty(), "the starting categories come back")
    }
}
