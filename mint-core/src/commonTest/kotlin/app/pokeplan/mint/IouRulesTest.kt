package app.pokeplan.mint

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure IOU rules (MINT.md 5.8A): the player's own share is spending, a repayment is no income, and an IOU cannot be nonsense. */
class IouRulesTest {
    private val day = LocalDate(2026, 11, 21)
    private val account = FinancialAccount("w", "Wallet", AccountType.CASH, "PHP", openingDay = day)

    private fun tx(
        id: String, amount: Long = 100_000, type: TxType = TxType.EXPENSE, iou: Long = 0, payer: String = "",
        repaymentOf: String? = null, status: TxStatus = TxStatus.POSTED, splits: List<TransactionSplit> = emptyList(),
    ) = Transaction(
        id = id, type = type, amountMinor = amount, currency = "PHP", atMillis = 0, day = day, accountId = "w",
        iouMinor = iou, iouFor = payer, repaymentOf = repaymentOf, status = status, splits = splits,
    )

    private fun data(vararg transactions: Transaction) = MintData(accounts = listOf(account), transactions = transactions.toList())

    @Test
    fun thePlayersOwnShareIsTheExpenseLessTheIou() {
        assertEquals(25_000, MintRules.myShare(tx("d", iou = 75_000)))
        assertEquals(100_000, MintRules.myShare(tx("d")))
        assertEquals(100_000, MintRules.myShare(tx("i", type = TxType.INCOME)), "only an expense is shared")
    }

    @Test
    fun spendingCountsTheShareAndARepaymentIsNoIncome() {
        val dinner = tx("d", iou = 75_000)
        val back = tx("r", amount = 75_000, type = TxType.INCOME, repaymentOf = "d")
        val d = data(dinner, back)

        assertEquals(25_000, MintRules.spent(d, day, day.plus(1, DateTimeUnit.DAY), "PHP"))
        assertEquals(0, MintRules.earned(d, day, day.plus(1, DateTimeUnit.DAY), "PHP"))
        assertEquals(25_000, MintRules.spendingByCategory(d, day, day.plus(1, DateTimeUnit.DAY), "PHP").sumOf { it.second })
    }

    @Test
    fun whatIsOwedIsTheIouLessItsRepaymentsAndAVoidedOneDoesNotCount() {
        val dinner = tx("d", iou = 75_000, payer = "Alex")
        assertEquals(75_000, MintRules.iouOwed(data(dinner), dinner))
        assertEquals(35_000, MintRules.iouOwed(data(dinner, tx("r1", 40_000, TxType.INCOME, repaymentOf = "d")), dinner))
        assertEquals(75_000, MintRules.iouOwed(data(dinner, tx("r1", 40_000, TxType.INCOME, repaymentOf = "d", status = TxStatus.VOIDED)), dinner), "a voided repayment does not settle anything")
        assertEquals(0, MintRules.iouOwed(data(dinner, tx("r1", 80_000, TxType.INCOME, repaymentOf = "d")), dinner), "never below zero")
        assertEquals(0, MintRules.iouOwed(data(dinner), tx("plain")), "an expense with no IOU owes nothing")
    }

    @Test
    fun openIousAreThePostedOnesWithSomethingLeft() {
        val d = data(tx("a", iou = 10_000), tx("b", iou = 10_000, status = TxStatus.VOIDED), tx("c"), tx("e", amount = 10_000, type = TxType.INCOME, iou = 10_000))
        assertEquals(listOf("a"), MintRules.openIous(d, "PHP").map { it.id }, "an income record with a stray IOU is never an open one")
        assertTrue(MintRules.openIous(d, "USD").isEmpty(), "nothing is netted across currencies")
    }

    @Test
    fun anIouIsRefusedWhenItIsNotAnExpensePartOfOne() {
        val d = data(tx("d"))
        assertTrue(MintRules.checkTransaction(d, tx("x", iou = 150_000)) is Check.Refused, "more than the expense")
        assertTrue(MintRules.checkTransaction(d, tx("x", iou = -1)) is Check.Refused, "negative")
        assertTrue(MintRules.checkTransaction(d, tx("x", type = TxType.INCOME, iou = 1_000)) is Check.Refused, "only an expense")
        assertTrue(
            MintRules.checkTransaction(d, tx("x", iou = 1_000, splits = listOf(TransactionSplit(null, 100_000)))) is Check.Refused,
            "a split record cannot carry an IOU",
        )
        assertEquals(Check.Ok, MintRules.checkTransaction(d, tx("x", iou = 1_000, payer = "Alex")))
    }

    @Test
    fun aRepaymentNeedsAnExpenseWithAnIou() {
        val d = data(tx("d"))
        assertTrue(MintRules.checkTransaction(d, tx("r", 1_000, TxType.INCOME, repaymentOf = "d")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(d, tx("r", 1_000, TxType.INCOME, repaymentOf = "gone")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(d, tx("r", 1_000, type = TxType.EXPENSE, repaymentOf = "d")) is Check.Refused, "a repayment is money received")
    }
}
