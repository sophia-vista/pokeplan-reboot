package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.CategoryKind
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An IOU is income someone owes the player back (MINT.md 5.8A): it takes its amount off the spending it was for, is no income, pays nothing, and moves the balance only once received. */
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
    fun anIouStillOwedTakesItsAmountOffSpendingWithoutMovingTheBalance() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val food = assertNotNull(m.addCategory("Food", CategoryKind.EXPENSE)).id
        assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, categoryId = food, payee = "Dinner", atMillis = day).record)
        val iou = assertNotNull(m.record(TxType.INCOME, 75_000, wallet, categoryId = food, payee = "Alex", atMillis = day, status = TxStatus.PENDING, iou = true).record)

        val today = m.today
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        assertEquals(25_000, MintRules.spent(m.data, today, tomorrow, "PHP"), "the dinner less what Alex owes")
        assertEquals(25_000, MintRules.spent(m.data, today, tomorrow, "PHP", food), "and in its own category, so a budget on it shrinks too")
        assertEquals(listOf<Pair<String?, Long>>(food to 25_000L), MintRules.spendingByCategory(m.data, today, tomorrow, "PHP"))
        assertEquals(0, MintRules.earned(m.data, today, tomorrow, "PHP"), "an IOU is not income")
        val balance = MintRules.balances(m.data).single()
        assertEquals(400_000, balance.postedMinor, "nothing has come back yet")
        assertEquals(75_000, balance.pendingMinor, "it is expected")
        assertTrue(iou.iou)
    }

    @Test
    fun receivingAnIouRaisesTheBalanceAndChangesNoSpendingOrIncome() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        val iou = assertNotNull(m.record(TxType.INCOME, 75_000, wallet, payee = "Alex", atMillis = day, status = TxStatus.PENDING, iou = true).record)
        val today = m.today
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val before = MintRules.spent(m.data, today, tomorrow, "PHP")

        assertTrue(m.setTransactionStatus(iou.id, TxStatus.POSTED))

        assertEquals(475_000, MintRules.balances(m.data).single().postedMinor, "the money is back in the wallet")
        assertEquals(before, MintRules.spent(m.data, today, tomorrow, "PHP"), "spending was already reduced while it was owed")
        assertEquals(0, MintRules.earned(m.data, today, tomorrow, "PHP"), "still not income")
    }

    @Test
    fun anIouIsAlreadyReceivedWhenSavedThatWay_PaysNoRewardAndOnlyIncomeCanBeOne() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        val received = m.record(TxType.INCOME, 50_000, wallet, payee = "Ana", atMillis = day, iou = true)
        assertNotNull(received.record, received.refused)
        assertTrue(received.rewards.isEmpty(), "money coming back pays no reward")
        assertEquals(550_000, MintRules.balances(m.data).single().postedMinor)

        assertNull(m.record(TxType.EXPENSE, 50_000, wallet, payee = "Taxi", atMillis = day, iou = true).record, "an expense cannot be an IOU")
        val income = assertNotNull(m.addCategory("Side gigs", CategoryKind.INCOME)).id
        assertNull(m.record(TxType.INCOME, 50_000, wallet, categoryId = income, payee = "Ana", atMillis = day, iou = true).record, "an IOU's category is a spending one")
    }

    @Test
    fun deletingAnIouTakesItOutOfSpendingAndAnEditKeepsItAnIou() {
        val m = mint()
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.CASH, "PHP", 500_000).record).id
        assertNotNull(m.record(TxType.EXPENSE, 100_000, wallet, payee = "Dinner", atMillis = day).record)
        val iou = assertNotNull(m.record(TxType.INCOME, 75_000, wallet, payee = "Alex", atMillis = day, status = TxStatus.PENDING, iou = true).record)
        val edited = assertNotNull(m.updateTransaction(iou.copy(amountMinor = 60_000, iou = false)).record)
        assertTrue(edited.iou, "an edit does not turn an IOU into plain income")
        assertEquals(60_000, m.data.transactions.first { it.id == iou.id }.amountMinor)

        assertTrue(m.deleteTransaction(iou.id))
        val today = m.today
        assertEquals(100_000, MintRules.spent(m.data, today, today.plus(1, DateTimeUnit.DAY), "PHP"))
    }
}
