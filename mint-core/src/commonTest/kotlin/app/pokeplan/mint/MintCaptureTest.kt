package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MintCaptureTest {
    private val day = LocalDate(2026, 11, 25)

    private fun sms(source: CaptureSource, text: String, title: String = "") =
        MintCapture.parse(CaptureText(CaptureChannel.MESSAGE, source, 1_763_000_000_000, day, title, text))

    private fun notification(source: CaptureSource, title: String, text: String) =
        MintCapture.parse(CaptureText(CaptureChannel.NOTIFICATION, source, 1_763_000_000_000, day, title, text))

    private fun ok(c: CaptureCandidate?): CaptureCandidate = assertNotNull(c, "the alert should be read")

    @Test
    fun aGcashPaymentIsAnExpenseAtTheMerchant() {
        val c = ok(sms(CaptureSource.GCASH, "You have paid PHP 250.00 to SM CITY via GCash. Ref No. 1234567890123. If this was not you, call 2882."))
        assertEquals(TxType.EXPENSE, c.type)
        assertEquals(25_000L, c.amountMinor)
        assertEquals("SM CITY", c.payee)
        assertEquals("GCash", c.capturedFromLabel())
        assertEquals(day, c.day)
    }

    @Test
    fun theNewBalanceInTheSameMessageIsNeverTheAmount() {
        val c = ok(sms(CaptureSource.MAYA, "You paid PHP 1,234.56 to JOLLIBEE. Your new balance is PHP 8,765.44."))
        assertEquals(TxType.EXPENSE, c.type)
        assertEquals(123_456L, c.amountMinor)
        assertEquals("JOLLIBEE", c.payee)
    }

    @Test
    fun theSecurityBoilerplateDoesNotHideARealTransaction() {
        val bpi = ok(sms(CaptureSource.BPI, "BPI: Your account ending 1234 was debited PHP 500.00 on 11/25/26. Ref: ABC123. Never share your OTP."))
        assertEquals(TxType.EXPENSE, bpi.type)
        assertEquals(50_000L, bpi.amountMinor)
        assertEquals("1234", bpi.lastDigits)
        val bdo = ok(sms(CaptureSource.BDO, "BDO: You sent PHP250.00 to JUAN DELA CRUZ (09171234567) on 11-25-26 via InstaPay. Ref No. 9876543210. Never share your OTP, PIN or code."))
        assertEquals(25_000L, bdo.amountMinor)
        assertEquals(TxType.EXPENSE, bdo.type)
    }

    @Test
    fun aCreditIsIncomeFromTheSender() {
        val c = ok(sms(CaptureSource.GCASH, "You have received PHP 1,000.00 from JUAN DELA CRUZ. Ref No. 1112223334445."))
        assertEquals(TxType.INCOME, c.type)
        assertEquals(100_000L, c.amountMinor)
        assertEquals("JUAN DELA CRUZ", c.payee)
    }

    @Test
    fun aCreditWithoutANameFallsBackToTheSourcesLabel() {
        val c = ok(sms(CaptureSource.MAYA, "PHP 3,000.00 was credited to your account ending 5678."))
        assertEquals(TxType.INCOME, c.type)
        assertEquals(300_000L, c.amountMinor)
        assertEquals("5678", c.lastDigits)
        assertEquals("Maya", c.capturedFromLabel())
    }

    @Test
    fun aMaskedCardNumberGivesTheAccountItsSuffix() {
        val c = ok(notification(CaptureSource.BPI, "BPI", "Your BPI credit card ****4321 was charged PHP 899.00 at NETFLIX."))
        assertEquals(TxType.EXPENSE, c.type)
        assertEquals(89_900L, c.amountMinor)
        assertEquals("4321", c.lastDigits)
        assertEquals("NETFLIX", c.payee)
    }

    @Test
    fun aMerchantNamedWithMerchantColonIsRead() {
        val c = ok(sms(CaptureSource.UNIONBANK, "UnionBank: PHP 2,000.00 was debited from your account ending 5678. Merchant: LAZADA."))
        assertEquals(200_000L, c.amountMinor)
        assertEquals("LAZADA", c.payee)
        assertEquals("5678", c.lastDigits)
    }

    @Test
    fun pesosInAnyOfTheUsualFormsAreReadExactly() {
        val gotyme = ok(sms(CaptureSource.GOTYME, "Your GoTyme account was charged \u20B1150.00 at STARBUCKS."))
        assertEquals(15_000L, gotyme.amountMinor)
        val cashIn = ok(sms(CaptureSource.GCASH, "Cash in of PHP 200.00 was added to your GCash wallet."))
        assertEquals(TxType.INCOME, cashIn.type)
        assertEquals(20_000L, cashIn.amountMinor)
        val amountAfter = ok(sms(CaptureSource.MAYA, "You were charged 1,234.50 PHP at PUREGOLD."))
        assertEquals(123_450L, amountAfter.amountMinor)
    }

    @Test
    fun aOneTimeCodeIsNeverATransactionEvenWhenAnAmountIsMentioned() {
        assertNull(sms(CaptureSource.GCASH, "Your GCash OTP is 123456. Do not share it with anyone."))
        assertNull(sms(CaptureSource.BPI, "BPI: Your one-time password is 654321 for your payment of PHP 500. Never share this code."))
        assertNull(sms(CaptureSource.MAYA, "Maya verification code: 778899. Do not share your code."))
    }

    @Test
    fun promosAndVouchersAreNotTransactions() {
        assertNull(sms(CaptureSource.MAYA, "Get 20% cashback when you pay with Maya. Promo until Nov 30."))
        assertNull(sms(CaptureSource.GCASH, "Your voucher code is SAVE20. Enjoy PHP 100.00 off your next purchase."))
    }

    @Test
    fun aFailedOrUpcomingChargeIsNotATransaction() {
        assertNull(sms(CaptureSource.MAYA, "Your payment of PHP 500.00 to SM was declined. Please try again."))
        assertNull(sms(CaptureSource.BDO, "Reminder: your BPI credit card bill of PHP 2,500.00 will be debited on Nov 30."))
        assertNull(sms(CaptureSource.UNIONBANK, "A charge of PHP 300.00 was reversed to your account."))
    }

    @Test
    fun aBalanceOrAPointsMessageIsNotATransaction() {
        assertNull(sms(CaptureSource.GCASH, "Your GCash balance is PHP 1,234.56 as of Nov 25."))
        assertNull(sms(CaptureSource.MAYA, "You have 2,500 Maya points. Spend them before Dec 31."))
    }

    @Test
    fun anAmountMINTCannotReadWithConfidenceIsRefused() {
        assertNull(sms(CaptureSource.GCASH, "You have paid your bill. Thank you."))
        assertNull(sms(CaptureSource.BPI, "Your account was debited today. See the app for details."))
        assertNull(sms(CaptureSource.MAYA, ""))
        assertNull(notification(CaptureSource.GCASH, "", ""))
    }

    @Test
    fun aTextThatLooksBothWaysIsRefusedRatherThanGuessed() {
        assertNull(sms(CaptureSource.GCASH, "You sent PHP 100.00 to Ana and received PHP 50.00 from Bea."))
    }

    @Test
    fun aLongReferenceNumberIsNeverMistakenForTheAmount() {
        val c = ok(sms(CaptureSource.BDO, "Ref 1234567890123456. You paid PHP 250.00 to SM CITY."))
        assertEquals(25_000L, c.amountMinor)
    }

    @Test
    fun theSameAlertIsTheSameProposalHoweverItArrives() {
        val text = "You have paid PHP 250.00 to SM CITY. Ref No. 1234567890123."
        val bySms = ok(sms(CaptureSource.GCASH, text))
        val again = ok(sms(CaptureSource.GCASH, text))
        val byNotification = ok(notification(CaptureSource.GCASH, "GCash", text))
        assertEquals(bySms.ref, again.ref)
        assertEquals(bySms.ref, byNotification.ref, "one purchase, one proposal, whichever way it arrived")
        assertEquals(bySms.ref, ok(sms(CaptureSource.GCASH, text.replace("1234567890123", "9999999999999"))).ref, "a new reference number does not make a new purchase")
    }

    @Test
    fun aDifferentAmountOrMerchantIsADifferentProposal() {
        val a = ok(sms(CaptureSource.GCASH, "You have paid PHP 250.00 to SM CITY."))
        val b = ok(sms(CaptureSource.GCASH, "You have paid PHP 260.00 to SM CITY."))
        val c = ok(sms(CaptureSource.GCASH, "You have paid PHP 250.00 to PUREGOLD."))
        assertEquals(3, setOf(a.ref, b.ref, c.ref).size)
    }

    @Test
    fun thePayeeIsShortAndFreeOfTheMessageFurniture() {
        val c = ok(sms(CaptureSource.MAYA, "You paid PHP 500.00 to JOLLIBEE - SM NORTH via Maya on 11/25/26. Ref: 123."))
        assertEquals("JOLLIBEE - SM NORTH", c.payee)
        assertEquals("From Maya", c.note)
    }

    @Test
    fun aLongPayeeIsCappedToWhatARecordHolds() {
        val c = ok(sms(CaptureSource.MAYA, "You paid PHP 5.00 to " + "A".repeat(200) + "."))
        assertEquals(MintRules.NAME_MAX * 2, c.payee.length)
    }

    @Test
    fun theChannelIsKeptButTheRawTextIsNot() {
        val c = ok(notification(CaptureSource.BPI, "BPI", "BPI: You paid PHP 100.00 at 7-ELEVEN."))
        assertEquals(CaptureChannel.NOTIFICATION, c.channel)
        assertEquals("BPI", c.source)
        assertEquals("From BPI", c.note)
        assertEquals(day, c.day)
    }

    @Test
    fun onlyTheBanksOnTheListAreEverLookedAt() {
        assertEquals(CaptureSource.GCASH, CaptureSource.ofPackage("com.globe.gcash.android"))
        assertEquals(CaptureSource.MAYA, CaptureSource.ofPackage("com.paymaya"))
        assertEquals(null, CaptureSource.ofPackage("com.facebook.katana"))
        assertEquals(null, CaptureSource.ofPackage("com.android.mms"))
        assertEquals(null, CaptureSource.ofPackage(""))
        assertEquals(CaptureSource.GCASH, CaptureSource.ofSender("GCASH"))
        assertEquals(CaptureSource.GCASH, CaptureSource.ofSender(" gcash "))
        assertEquals(CaptureSource.GCASH, CaptureSource.ofSender("GLOBE-GCASH"))
        assertEquals(null, CaptureSource.ofSender("09171234567"), "a personal number is never a bank")
        assertEquals(null, CaptureSource.ofSender(""))
        assertEquals(CaptureSource.BPI, CaptureSource.ofSender("BPIEXPRESS"))
    }

    @Test
    fun aMessageLongerThanABankAlertIsNotParsedAtAll() {
        val body = "You paid PHP 250.00 to SM CITY. " + "x".repeat(MintCapture.TEXT_MAX)
        assertNull(sms(CaptureSource.GCASH, body))
    }

    @Test
    fun theAllowedSourcesAreTheElevenOnTheSpecsList() {
        assertEquals(
            listOf("GCash", "Maya", "MariBank", "GoTyme", "UnionBank", "BDO", "BPI", "Metrobank", "LandBank", "CIMB", "SeaBank"),
            CaptureSource.entries.map { it.label },
        )
        assertEquals(11, CaptureSource.entries.size)
    }
    private fun CaptureCandidate.capturedFromLabel(): String = CaptureSource.ofName(source)?.label ?: source
}
