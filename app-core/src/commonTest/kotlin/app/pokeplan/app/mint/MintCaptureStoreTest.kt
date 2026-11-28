package app.pokeplan.app.mint

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.CaptureChannel
import app.pokeplan.mint.CaptureCandidate
import app.pokeplan.mint.EarnSchedule
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.Transaction
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

class MintCaptureStoreTest {

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 25, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val day = LocalDate(2026, 11, 25)

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    private fun alert(
        source: String = "GCASH", type: TxType = TxType.EXPENSE, amount: Long = 25_000, ref: String = "gcash|EXPENSE|25000|2026-11-25|sm city|",
        lastDigits: String? = null, currency: String = "PHP", payee: String = "SM CITY", atMillis: Long = start, day: LocalDate = this.day,
    ) = CaptureCandidate(
        ref = ref, source = source, channel = CaptureChannel.NOTIFICATION, type = type, amountMinor = amount, currency = currency,
        payee = payee, note = "From GCash", day = day, atMillis = atMillis, lastDigits = lastDigits,
    )

    @Test
    fun anAlertBecomesAPendingRecordThatMovesNothingUntilItIsConfirmed() {
        val (m, _) = store()
        val gcash = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val result = m.capture(alert())
        val tx = assertNotNull(result.record, result.reason)
        assertEquals(TxStatus.PENDING, tx.status)
        assertEquals(TxType.EXPENSE, tx.type)
        assertEquals("GCash", tx.capturedFrom)
        assertEquals("gcash|EXPENSE|25000|2026-11-25|sm city|", tx.captureRef)
        assertEquals(listOf(tx.id), m.toReview().map { it.id }, "it waits in the same box as an interest estimate")
        assertEquals(100_000L, MintRules.balance(m.data, gcash).postedMinor, "a pending record is not in the balance")
        assertEquals(0L, MintRules.spent(m.data, day, day.plusDays(1), "PHP"), "and it is not spending yet")

        val opened = assertNotNull(m.confirmPending(tx.id).record)
        assertEquals(TxStatus.POSTED, opened.status)
        assertEquals(75_000L, MintRules.balance(m.data, gcash).postedMinor)
        assertEquals(25_000L, MintRules.spent(m.data, day, day.plusDays(1), "PHP"))
        assertTrue(m.toReview().isEmpty(), "a confirmed record leaves the box")
    }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

    @Test
    fun adjustingTheAmountPostsTheAmountThePlayerTyped() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val tx = assertNotNull(m.capture(alert()).record)
        val adjusted = assertNotNull(m.confirmPending(tx.id, 24_900L).record)
        assertEquals(24_900L, adjusted.amountMinor)
        assertEquals(TxStatus.POSTED, adjusted.status)
        assertNull(m.confirmPending(tx.id, 1L).record, "it is no longer waiting")
    }

    @Test
    fun skippingKeepsTheRecordVoidedAndOutOfTheBox() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val tx = assertNotNull(m.capture(alert()).record)
        assertTrue(m.skipPending(tx.id))
        assertEquals(TxStatus.VOIDED, m.data.transactions.single { it.id == tx.id }.status)
        assertTrue(m.toReview().isEmpty())
        assertEquals(0L, MintRules.spent(m.data, day, day.plusDays(1), "PHP"))
    }

    @Test
    fun theSameAlertTwiceIsOneProposal() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        assertNotNull(m.capture(alert()).record)
        val again = m.capture(alert())
        assertNull(again.record)
        assertEquals("Already recorded.", again.reason)
        assertEquals(1, m.data.transactions.count { it.captureRef != null })
    }

    @Test
    fun theAccountIsTheOneTheAlertNamesTheOneWithThatNameOrTheOnlyOne() {
        val (m, _) = store()
        val gcash = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 0).record)
        val bpi = assertNotNull(m.addAccount("BPI Savings", AccountType.BANK, "PHP", 0, institution = "BPI", suffix = "1234").record)
        assertEquals(bpi.id, assertNotNull(m.capture(alert(lastDigits = "1234")).record).accountId, "the card it names wins")
        assertEquals(gcash.id, assertNotNull(m.capture(alert(ref = "by-name")).record).accountId, "then one named after the source")
        val (only, _) = store()
        val solo = assertNotNull(only.addAccount("Wallet", AccountType.EWALLET, "PHP", 0).record)
        assertEquals(solo.id, assertNotNull(only.capture(alert(source = "MAYA", ref = "solo")).record).accountId, "then the only open one")
        val (none, _) = store()
        assertNull(none.capture(alert()).record, "with no account at all it cannot propose one")
    }

    @Test
    fun anAlertItCannotPlaceIsRefusedWithAReasonAndStoredNowhere() {
        val (m, _) = store()
        assertNotNull(m.addAccount("Maya", AccountType.EWALLET, "PHP", 0).record)
        assertNull(m.capture(alert(type = TxType.TRANSFER)).record)
        assertNull(m.capture(alert(amount = 0)).record)
        assertNull(m.capture(alert(currency = "USD", ref = "usd")).record, "a dollar alert never becomes pesos")
        assertTrue(m.data.transactions.isEmpty())
    }

    @Test
    fun deletingAndErasingRemoveOnlyWaitingCapturedRecords() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val deleted = assertNotNull(m.capture(alert(ref = "deleted", payee = "One")).record)
        assertNotNull(m.capture(alert(ref = "erased", payee = "Two")).record)
        val kept = assertNotNull(m.capture(alert(ref = "kept", payee = "Three")).record)
        assertNotNull(m.confirmPending(kept.id).record)
        assertTrue(m.deleteCaptured(deleted.id), "a captured record that is still waiting can be deleted")
        assertEquals(1, m.eraseCaptured(), "and erasing takes the rest of the waiting ones")
        assertEquals(kept.id, m.data.transactions.single().id, "a record the player had already confirmed stays")
        assertEquals(0, m.eraseCaptured())
        assertTrue(!m.deleteCaptured(kept.id), "a posted record is not a waiting capture any more")
    }

    @Test
    fun theBoxListsAnInterestEstimateAndACapturedAlertTogetherOldestFirst() {
        val (m, clock) = store()
        val savings = assertNotNull(m.addAccount("Maya Savings", AccountType.DIGITAL_BANK, "PHP", 1_000_000, earnRateBasisPoints = 365, earnSchedule = EarnSchedule.DAILY).record)
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        assertEquals(0, m.accrueInterest(), "nothing is estimated for the day it was switched on")
        clock.millis += 3 * 86_400_000L
        assertTrue(m.accrueInterest() > 0)
        val estimate = m.interestToReview().single()
        val later = LocalDate(2026, 11, 29)
        val captured = assertNotNull(m.capture(alert(day = later, atMillis = start + 4 * 86_400_000L, ref = "later")).record)
        val box = m.toReview()
        assertEquals(2, box.size)
        assertEquals(listOf(estimate.id, captured.id), box.map { it.id })
        assertEquals(savings.id, box.first().accountId)
        assertNotNull(m.confirmPending(estimate.id).record, "the generic confirm still posts an estimate")
        assertEquals(TxStatus.POSTED, m.data.transactions.single { it.id == estimate.id }.status)
    }

    @Test
    fun aCapturedRecordKeepsItsProvenanceWhenThePlayerEditsIt() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val tx = assertNotNull(m.capture(alert()).record)
        val edited = assertNotNull(m.updateTransaction(tx.copy(payee = "SM CITY NORTH", amountMinor = 26_000)).record)
        assertEquals("GCash", edited.capturedFrom)
        assertEquals(tx.captureRef, edited.captureRef)
    }

    @Test
    fun aCapturedCreditPostsAsIncomeOnTheAccount() {
        val (m, _) = store()
        val gcash = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        val tx = assertNotNull(
            m.capture(alert(type = TxType.INCOME, amount = 150_000, ref = "in", payee = "ANA")).record,
        )
        assertEquals(TxType.INCOME, tx.type)
        assertNull(m.confirmPending(tx.id, null).record?.takeIf { it.status != TxStatus.POSTED })
        assertEquals(250_000L, MintRules.balance(m.data, gcash).postedMinor, "a confirmed credit raises the account")
    }

    @Test
    fun aCapturePaysNoRewardAndTheSettingsStayOffUntilTurnedOn() {
        val (m, _) = store()
        assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record)
        assertTrue(!m.data.settings.captureNotifications && !m.data.settings.captureMessages)
        m.setCaptureNotifications(true)
        m.setCaptureMessages(true)
        assertTrue(m.data.settings.captureNotifications && m.data.settings.captureMessages)
        val before = m.data.transactions.size
        assertNotNull(m.capture(alert()).record)
        assertEquals(before + 1, m.data.transactions.size, "one record, nothing else recorded")
    }
}
