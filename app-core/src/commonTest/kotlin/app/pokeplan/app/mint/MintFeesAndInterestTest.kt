package app.pokeplan.app.mint

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.EarnSchedule
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MintFeesAndInterestTest {

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    @Test
    fun aTransferFeeComesOutOfTheSenderOnlyAndCountsAsSpendingNotTheAmount() {
        val (m, _) = store()
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 0).record)
        val tx = assertNotNull(m.record(TxType.TRANSFER, 20_000, bank.id, toAccountId = wallet.id, feeMinor = 1_500).record)
        assertEquals(78_500L, MintRules.balance(m.data, bank).postedMinor)
        assertEquals(20_000L, MintRules.balance(m.data, wallet).postedMinor)
        val day = tx.day
        val next = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        assertEquals(1_500L, MintRules.spent(m.data, day, next, "PHP"), "only the fee is spending")
        assertEquals(listOf<Pair<String?, Long>>(null to 1_500L), MintRules.spendingByCategory(m.data, day, next, "PHP"))
        assertEquals(1_500L, app.pokeplan.mint.Installments.cashFlow(m.data, day, next, "PHP").outflowMinor, "the fee really leaves the liquid accounts")
        assertNull(m.record(TxType.EXPENSE, 100, bank.id, feeMinor = 5).record, "only a transfer has a fee")
        assertNull(m.record(TxType.TRANSFER, 100, bank.id, toAccountId = wallet.id, feeMinor = -1).record)
    }

    @Test
    fun aFeeCanBeEditedAndDeletedWithItsTransferBecauseItIsOneRecord() {
        val (m, _) = store()
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 0).record)
        val tx = assertNotNull(m.record(TxType.TRANSFER, 20_000, bank.id, toAccountId = wallet.id, feeMinor = 1_500).record)
        assertNotNull(m.updateTransaction(tx.copy(feeMinor = 2_500)).record)
        assertEquals(77_500L, MintRules.balance(m.data, bank).postedMinor)
        assertTrue(m.deleteTransaction(tx.id))
        assertEquals(100_000L, MintRules.balance(m.data, bank).postedMinor)
    }

    @Test
    fun aDailySavingsScheduleMakesOnePendingEstimateAfterDaysAwayAndTaxIsWithheld() {
        val (m, clock) = store()
        val acct = assertNotNull(m.addAccount("Maya Savings", AccountType.DIGITAL_BANK, "PHP", 1_000_000, earnRateBasisPoints = 365, earnSchedule = EarnSchedule.DAILY, earnTaxBasisPoints = 2_000).record)
        assertEquals(0, m.accrueInterest(), "nothing is estimated for the day it was switched on")
        clock.advanceDays(3)
        assertEquals(1, m.accrueInterest())
        assertEquals(0, m.accrueInterest(), "the same days are never estimated twice")
        val pending = m.interestToReview().single()
        assertEquals(3 * 80L, pending.amountMinor)
        assertEquals(TxStatus.PENDING, pending.status)
        assertEquals(1_000_000L, MintRules.balance(m.data, acct).postedMinor, "an estimate waiting for review is not in the balance")
        assertEquals(250L, m.confirmInterest(pending.id, 250L).record?.amountMinor, "adjusting to the bank's figure")
        assertEquals(1_000_250L, MintRules.balance(m.data, acct).postedMinor)
        assertTrue(m.interestToReview().isEmpty())
        assertTrue(m.data.categories.any { it.id == MintRules.INTEREST_CATEGORY })
    }

    @Test
    fun aSkippedEstimateIsNotMadeAgainAndAMonthlyScheduleWaitsForTheMonthToEnd() {
        val (m, clock) = store()
        assertNotNull(m.addAccount("GoTyme", AccountType.DIGITAL_BANK, "PHP", 1_000_000, earnRateBasisPoints = 365, earnSchedule = EarnSchedule.MONTHLY, earnAutoPost = true).record)
        clock.advanceDays(10)
        assertEquals(0, m.accrueInterest())
        clock.advanceDays(14)
        assertEquals(1, m.accrueInterest())
        val made = m.data.transactions.single { it.interestThrough != null }
        assertEquals(TxStatus.POSTED, made.status, "post without review")
        assertEquals(24 * 100L, made.amountMinor, "7 to 30 November is 24 days")
        assertEquals(LocalDate(2026, 11, 30), made.interestThrough)
        assertEquals(0, m.accrueInterest())
    }

    @Test
    fun cashAndCreditAccountsCannotEarnInterest() {
        val (m, _) = store()
        assertNull(m.addAccount("Wallet", AccountType.CASH, "PHP", earnRateBasisPoints = 100, earnSchedule = EarnSchedule.DAILY).record)
        assertNull(m.addAccount("Visa", AccountType.CREDIT_CARD, "PHP", earnRateBasisPoints = 100).record)
        assertNull(m.addAccount("Bank", AccountType.BANK, "PHP", earnSchedule = EarnSchedule.DAILY).record, "a schedule needs a rate")
    }
}
