package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.pokemon.Items
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Logging a purchase, a payment or a transfer pays one flat reward that never depends on the amount (MINT.md 16.2). */
class MintLoggingRewardTest {
    private class FakeClock(var millis: Long) : Clock { override fun nowMillis() = millis }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val mint: MintStore) {
        val candies get() = game.data.player.inventory[Items.RARE_CANDY]
    }

    private fun setup(): Setup {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return Setup(game, MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)))
    }

    @Test
    fun aPurchaseAndATransferPayTheSameWhateverTheAmount() {
        val s = setup()
        val bank = assertNotNull(s.mint.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000_000).record)
        val wallet = assertNotNull(s.mint.addAccount("GCash", AccountType.EWALLET, "PHP").record)
        val before = s.candies
        val tiny = s.mint.record(TxType.EXPENSE, 1, bank.id)
        assertTrue(tiny.rewards.isNotEmpty(), "a one-centavo purchase pays")
        val afterTiny = s.candies
        val huge = s.mint.record(TxType.EXPENSE, 90_000_000, bank.id)
        assertTrue(huge.rewards.isNotEmpty())
        assertEquals(afterTiny - before, s.candies - afterTiny, "the same Rare Candies for any amount")
        assertTrue(s.mint.record(TxType.TRANSFER, 500, bank.id, toAccountId = wallet.id).rewards.isNotEmpty(), "a transfer pays")
    }

    @Test
    fun everyLoggedRecordPaysWithNoDailyLimitAndIncomeBackdatedAndOffNeverPay() {
        val s = setup()
        val bank = assertNotNull(s.mint.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        repeat(12) { assertTrue(s.mint.record(TxType.EXPENSE, 100, bank.id).rewards.isNotEmpty(), "record ${it + 1} pays") }
        assertEquals(12, s.mint.data.transactions.count { it.paid })
        assertTrue(s.game.mintEggMeter() >= 0 && s.game.data.player.partyOf(app.pokeplan.core.pokemon.PartyIds.MINT).size >= 3, "twelve records found at least three Ledger Eggs")

        val fresh = setup()
        val acct = assertNotNull(fresh.mint.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        assertTrue(fresh.mint.record(TxType.INCOME, 100, acct.id).rewards.isEmpty(), "income does not pay")
        assertTrue(fresh.mint.record(TxType.EXPENSE, 100, acct.id, atMillis = start - 3 * 86_400_000L).rewards.isEmpty(), "a backdated entry does not pay")
        fresh.mint.setProgression(false)
        assertTrue(fresh.mint.record(TxType.EXPENSE, 100, acct.id).rewards.isEmpty(), "Ledger Research off pays nothing")
    }
}
