package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** IOUs as part of a transaction (MINT.md 5.8A): the player's own share in spending, a repayment that is no income, and no repayments left behind. */
class MintIouTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private val day = LocalDateTime(2026, 11, 21, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class SpyWorld(val inner: GameStore) : app.pokeplan.app.World by inner

    private fun mint(): MintStore {
        val clock = FakeClock(day)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, SpyWorld(game), utc, rng = Random(3))
    }

    @Test
    fun anExpenseWithAnIouCountsOnlyThePlayerSHareInSpending() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        assertEquals(400_000, MintRules.balances(m.data).single().postedMinor, "the money really left the wallet (500,000 less the dinner)")

        assertNull(m.setIou(dinner.id, 75_000, "  Alex  "))

        val saved = m.data.transactions.first { it.id == dinner.id }
        assertEquals(75_000, saved.iouMinor)
        assertEquals("Alex", saved.iouFor)
        val today = m.today
        assertEquals(25_000, MintRules.spent(m.data, today, today.plus(1, DateTimeUnit.DAY), "PHP"), "only the player's own share is spending")
        assertEquals(400_000, MintRules.balances(m.data).single().postedMinor, "the IOU changes no balance")
        assertEquals(75_000, MintRules.iouOwed(m.data, saved))
        assertEquals(1, MintRules.openIous(m.data, "PHP").size)
    }

    @Test
    fun aRepaymentRaisesTheBalanceAndIsNoIncomeNorAReward() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        m.setIou(dinner.id, 75_000, "Alex")

        val back = m.recordRepayment(dinner.id, 75_000, wallet, day)

        val repayment = assertNotNull(back.record)
        assertEquals(TxType.INCOME, repayment.type)
        assertEquals(dinner.id, repayment.repaymentOf)
        assertTrue(back.rewards.isEmpty(), "a repayment is the player's own money coming back, so Ledger Research does not pay")
        assertEquals(475_000, MintRules.balances(m.data).single().postedMinor, "the repayment came back into the wallet")
        val today = m.today
        assertEquals(0, MintRules.earned(m.data, today, today.plus(1, DateTimeUnit.DAY), "PHP"), "a repayment is not income")
        assertEquals(25_000, MintRules.spent(m.data, today, today.plus(1, DateTimeUnit.DAY), "PHP"))
        assertEquals(0, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }))
        assertTrue(MintRules.openIous(m.data, "PHP").isEmpty())
    }

    @Test
    fun aRepaymentCannotOvershootAndPartPaymentsAreAllowed() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        m.setIou(dinner.id, 75_000, "Alex")

        assertNotNull(m.recordRepayment(dinner.id, 40_000, wallet, day).record, "a part payment is allowed")
        assertEquals(35_000, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }))
        val tooMuch = m.recordRepayment(dinner.id, 40_000, wallet, day)
        assertNull(tooMuch.record)
        assertNotNull(tooMuch.refused)
        assertEquals(35_000, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }), "a refused repayment changes nothing")
    }

    @Test
    fun clearingAnIouTakesItsRepaymentsWithItAndDeletingTheExpenseDoesToo() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        m.setIou(dinner.id, 75_000, "Alex")
        m.recordRepayment(dinner.id, 50_000, wallet, day)
        assertEquals(2, m.data.transactions.size)

        assertNull(m.setIou(dinner.id, 0, ""))
        assertEquals(1, m.data.transactions.size, "a repayment without an IOU would be money from nowhere")

        m.setIou(dinner.id, 75_000, "Alex")
        m.recordRepayment(dinner.id, 50_000, wallet, day)
        assertTrue(m.deleteTransaction(dinner.id))
        assertEquals(0, m.data.transactions.size)
    }

    @Test
    fun anIouCannotBeAddedToASplitOrOvershootTheExpense() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)

        assertNotNull(m.setIou(dinner.id, 150_000, "Alex"), "more than the expense is refused")
        assertEquals(0, m.data.transactions.first { it.id == dinner.id }.iouMinor)

        val split = assertNotNull(
            m.record(
                TxType.EXPENSE, 100_000, wallet, payee = "Market", atMillis = day,
                splits = listOf(app.pokeplan.mint.TransactionSplit("cat-food", 60_000), app.pokeplan.mint.TransactionSplit(null, 40_000)),
            ).record,
        )
        assertNotNull(m.setIou(split.id, 40_000, "Alex"), "a split record cannot carry an IOU")

        val income = assertNotNull(m.record(TxType.INCOME, 100_000, wallet, payee = "Salary", atMillis = day).record)
        assertNotNull(m.setIou(income.id, 10_000, "Alex"), "only an expense carries an IOU")
    }

    @Test
    fun aVoidedRepaymentReopensTheIou() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        m.setIou(dinner.id, 75_000, "Alex")
        val back = assertNotNull(m.recordRepayment(dinner.id, 75_000, wallet, day).record)
        assertEquals(0, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }))

        m.setTransactionStatus(back.id, TxStatus.VOIDED)

        assertEquals(75_000, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }), "a voided repayment no longer settles the IOU")
        assertNotEquals(0, MintRules.openIous(m.data, "PHP").size)
    }

    @Test
    fun anEditChangesTheAmountAndTheIouTogetherAndARefusalLeavesTheRecordAsItWas() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day, iouMinor = 75_000, iouFor = "Alex").record)

        // Lowering the expense below the old IOU is fine when the IOU comes down with it.
        assertNotNull(m.updateTransaction(dinner.copy(amountMinor = 60_000, iouMinor = 40_000)).record)
        assertEquals(60_000L to 40_000L, m.data.transactions.single().let { it.amountMinor to it.iouMinor })

        // Part of it paid back: the IOU cannot go under that, and nothing else in the edit is saved either.
        assertNotNull(m.recordRepayment(dinner.id, 30_000, wallet, day).record)
        val before = m.data
        val refused = m.updateTransaction(m.data.transactions.first { it.id == dinner.id }.copy(payee = "Lunch", iouMinor = 20_000))
        assertNull(refused.record)
        assertNotNull(refused.refused)
        assertEquals(before, m.data, "a refused edit changes nothing")

        // An IOU cannot be put on a record split across categories, and the refusal changes nothing.
        val split = m.data.transactions.first { it.id == dinner.id }
        assertNull(m.updateTransaction(split.copy(splits = listOf(app.pokeplan.mint.TransactionSplit(null, 30_000), app.pokeplan.mint.TransactionSplit(null, 30_000)))).record)
        assertEquals(before, m.data)

        // Clearing the IOU in an edit takes its repayments too.
        assertNotNull(m.updateTransaction(split.copy(iouMinor = 0, iouFor = "")).record)
        assertEquals(1, m.data.transactions.size)
    }

    @Test
    fun aRepaymentCannotBeEditedPastWhatIsOwedOrIntoAnotherCurrency() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val usd = assertNotNull(m.addAccount("Dollars", AccountType.CASH, "USD", 10_000).record).id
        val dinner = assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day, iouMinor = 75_000, iouFor = "Alex").record)
        val back = assertNotNull(m.recordRepayment(dinner.id, 50_000, wallet, day).record)

        assertNull(m.updateTransaction(back.copy(amountMinor = 80_000)).record, "more than the IOU")
        assertNull(m.updateTransaction(back.copy(accountId = usd)).record, "another currency")
        assertNotNull(m.updateTransaction(back.copy(amountMinor = 75_000)).record, "up to what is owed is fine")
        assertEquals(0, MintRules.iouOwed(m.data, m.data.transactions.first { it.id == dinner.id }))
    }
}
