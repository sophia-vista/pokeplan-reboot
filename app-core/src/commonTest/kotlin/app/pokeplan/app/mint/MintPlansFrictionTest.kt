package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.Installment
import app.pokeplan.mint.Installments
import app.pokeplan.mint.PlanEvery
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

/** The friction fixes on pay-later purchases and installment schedules (MINT.md 5.6A, 5.6C). */
class MintPlansFrictionTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 25, 18, 0).toInstant(utc).toEpochMilliseconds()

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    private fun purchase(m: MintStore, accountId: String, name: String = "Laptop", total: Long = 300_000, count: Int = 3, fee: FeeRule = FeeRule.None) =
        assertNotNull(m.addPlanPurchase(accountId, name, total, null, count, PlanEvery.MONTH, null, fee).record)

    @Test
    fun aPurchaseLeftUnnamedGetsTheFixedDefaultName() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        for (blank in listOf("", "   ")) {
            val plan = assertNotNull(m.addPlanPurchase(card.id, blank, 300_000, null, 3, PlanEvery.MONTH, null).record)
            assertEquals(Installments.DEFAULT_PURCHASE_NAME, plan.name)
            val record = assertNotNull(m.data.transactions.singleOrNull { it.id == plan.purchaseTxId })
            assertEquals(Installments.DEFAULT_PURCHASE_NAME, record.payee)
        }
    }

    @Test
    fun anExistingScheduleLeftUnnamedGetsItsOwnDefaultName() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val rows = listOf(Installment(1, LocalDate(2026, 12, 5), 100_000), Installment(2, LocalDate(2027, 1, 5), 100_000))
        val plan = assertNotNull(m.addExistingPlan(card.id, "", rows).record)
        assertEquals(Installments.DEFAULT_SCHEDULE_NAME, plan.name)
    }

    @Test
    fun aScheduleSaysWhatBlocksAChangeToItsTerms() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        val plan = purchase(m, card.id)
        assertNull(m.planLockReason(plan.id), "nothing recorded yet: the schedule can still change")
        assertTrue(m.canEditPlanTerms(plan.id))

        assertNotNull(m.payPlan(plan.id, 100_000, bank.id).record)
        val afterPayment = assertNotNull(m.planLockReason(plan.id)).lowercase()
        assertTrue(afterPayment.contains("payment"), afterPayment)
        assertTrue(!m.canEditPlanTerms(plan.id))

        val withFee = purchase(m, card.id, name = "Phone", total = 100_000, fee = FeeRule.PerInstallment(2_000))
        assertNotNull(m.billInstallmentFee(withFee.id, 1).record)
        val afterFee = assertNotNull(m.planLockReason(withFee.id)).lowercase()
        assertTrue(afterFee.contains("fee"), afterFee)

        val refunded = purchase(m, card.id, name = "Chair", total = 100_000)
        assertNotNull(m.refundPlan(refunded.id, 10_000).record)
        val afterRefund = assertNotNull(m.planLockReason(refunded.id)).lowercase()
        assertTrue(afterRefund.contains("refund"), afterRefund)
    }

    @Test
    fun anInstallmentSaysWhyItCanNoLongerBeEdited() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        val plan = purchase(m, card.id, total = 60_000, count = 3)
        assertNull(m.installmentLockReason(plan.id, 2), "nothing recorded on it yet")
        assertNotNull(m.payPlan(plan.id, 20_000, bank.id).record)
        assertTrue(assertNotNull(m.installmentLockReason(plan.id, 1)).lowercase().contains("payment"))
        assertNull(m.installmentLockReason(plan.id, 3), "a later installment is still free")
    }

    @Test
    fun theDeletionPreviewSaysWhatWouldGoAndWhatWouldStay() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        val plan = purchase(m, card.id, total = 90_000, count = 3)
        val fresh = assertNotNull(m.planDeletion(plan.id))
        assertTrue(fresh.canDelete)
        assertEquals(3, fresh.installments)
        assertEquals(90_000L, fresh.remainingMinor)
        assertNull(fresh.blockedReason)

        assertNotNull(m.payPlan(plan.id, 30_000, bank.id).record)
        val used = assertNotNull(m.planDeletion(plan.id))
        assertTrue(!used.canDelete)
        assertEquals(1, used.payments)
        assertEquals(30_000L, used.paidMinor)
        assertTrue(assertNotNull(used.blockedReason).lowercase().contains("payment"))
    }

    @Test
    fun deletingAPurchaseAndItsScheduleOnlyWorksWhileNothingIsRecorded() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        val fresh = purchase(m, card.id, total = 90_000, count = 3)
        assertEquals(true, m.deletePlanPurchase(fresh.id).record)
        assertTrue(m.data.plans.isEmpty())
        assertTrue(m.data.transactions.isEmpty(), "its purchase record goes with it")

        val used = purchase(m, card.id, total = 90_000, count = 3)
        assertNotNull(m.payPlan(used.id, 30_000, bank.id).record)
        assertNull(m.deletePlanPurchase(used.id).record, "a payment stops the deletion")
        assertTrue(m.data.plans.any { it.id == used.id })
        assertTrue(m.data.transactions.any { it.id == used.purchaseTxId })
    }

    @Test
    fun theScheduleCanStillBeRemovedWhenThePurchaseStays() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val bank = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record)
        val plan = purchase(m, card.id, total = 90_000, count = 3)
        assertNotNull(m.payPlan(plan.id, 30_000, bank.id).record)
        m.deletePlan(plan.id)
        assertTrue(m.data.plans.isEmpty())
        val purchaseRecord = assertNotNull(m.data.transactions.firstOrNull { it.id == plan.purchaseTxId })
        assertNull(purchaseRecord.planId, "the purchase stays, unlinked")
        assertTrue(m.data.transactions.any { it.planId == null && it.planAllocations.isEmpty() && it.type == TxType.TRANSFER }, "the payment stays as history")
    }

    @Test
    fun theDescriptiveFieldsOfAPlanPurchaseCanBeEditedButNotItsMoney() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val plan = purchase(m, card.id, name = "Laptop")
        val record = assertNotNull(m.data.transactions.first { it.id == plan.purchaseTxId })
        val edited = assertNotNull(m.updateTransaction(record.copy(payee = "Laptop Pro", note = "Bought at Greenhills")).record)
        assertEquals("Laptop Pro", edited.payee)
        assertEquals("Bought at Greenhills", edited.note)
        assertEquals("Laptop Pro", m.data.plans.single { it.id == plan.id }.name, "the schedule is named after the purchase")
        assertNull(m.updateTransaction(edited.copy(amountMinor = 999_999)).record, "its money still belongs to the plan")
    }

    @Test
    fun clearingTheNameOfAPlanPurchaseFallsBackToTheSchedulesName() {
        val (m, _) = store()
        val card = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0).record)
        val plan = purchase(m, card.id, name = "Laptop")
        val record = assertNotNull(m.data.transactions.first { it.id == plan.purchaseTxId })
        val edited = assertNotNull(m.updateTransaction(record.copy(payee = "  ")).record)
        assertEquals("Laptop", edited.payee)
    }
}
