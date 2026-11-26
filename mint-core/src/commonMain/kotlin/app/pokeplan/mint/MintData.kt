package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType(val label: String, val liability: Boolean = false, val liquid: Boolean = true) {
    BANK("Bank account"),
    EWALLET("E-wallet"),
    DIGITAL_BANK("Digital bank"),
    CASH("Cash"),
    CREDIT_CARD("Credit card", liability = true, liquid = false),
    PAY_LATER("Pay later", liability = true, liquid = false),
    LOAN("Loan", liability = true, liquid = false),
    OTHER_ASSET("Other asset", liquid = false),
    OTHER_LIABILITY("Other liability", liability = true, liquid = false),
}

@Serializable
enum class AccountStatus { ACTIVE, ARCHIVED, CLOSED }

@Serializable
data class FinancialAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingMinor: Long = 0,
    val openingDay: LocalDate,
    val institution: String = "",
    val suffix: String = "",
    val group: String = "",
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdMillis: Long = 0,
    val card: String = "",
    val receiveQr: String = "",
    val hideFromTotal: Boolean = false,
    val appPackage: String = "",
    val limitMinor: Long = 0,
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    val annualAprBasisPoints: Int = 0,
    val interestGraceDays: Int = 0,
    val earnRateBasisPoints: Int = 0,
    val earnSchedule: EarnSchedule = EarnSchedule.OFF,
    val earnTaxBasisPoints: Int = 0,
    val earnAutoPost: Boolean = false,
    val earnFrom: LocalDate? = null,
)

@Serializable
enum class EarnSchedule(val label: String) { OFF("Off"), DAILY("Every day"), MONTHLY("Every month") }

enum class CardStyle(val id: String, val label: String) {
    PLAIN("", "Plain"), BLUE("blue", "Blue"), GREEN("green", "Green"), GOLD("gold", "Gold"), VIOLET("violet", "Violet"), SLATE("slate", "Slate");

    companion object {
        fun of(id: String): CardStyle = entries.firstOrNull { it.id == id } ?: PLAIN

        fun customRgb(card: String): Int? = if (card.length == 7 && card[0] == '#') card.drop(1).toIntOrNull(16) else null
    }
}

@Serializable
enum class CategoryKind { EXPENSE, INCOME }

@Serializable
data class Category(val id: String, val name: String, val kind: CategoryKind, val archived: Boolean = false)

@Serializable
enum class TxType(val label: String) {
    EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), REFUND("Refund"), FEE("Fee or interest"), ADJUSTMENT("Balance adjustment"),
}

@Serializable
enum class TxStatus { PENDING, POSTED, EXCLUDED, VOIDED }

@Serializable
enum class TxSource { MANUAL, ADJUSTMENT, IMPORTED, CAPTURED }

@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val amountMinor: Long,
    val currency: String,
    val atMillis: Long,
    val day: LocalDate,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val payee: String = "",
    val note: String = "",
    val status: TxStatus = TxStatus.POSTED,
    val source: TxSource = TxSource.MANUAL,
    val up: Boolean = true,
    val refundOf: String? = null,
    val billId: String? = null,
    val billDueDay: LocalDate? = null,
    val createdMillis: Long = atMillis,
    val importBatch: String? = null,
    val importLabel: String = "",
    val fingerprint: String? = null,
    val unreviewed: Boolean = false,
    val planId: String? = null,
    val planNo: Int? = null,
    val feeKind: FeeKind? = null,
    val planAllocations: List<PlanPaymentAllocation> = emptyList(),
    val splits: List<TransactionSplit> = emptyList(),
    val creditStatementId: String? = null,
    val feeMinor: Long = 0,
    val paid: Boolean = false,
    val interestThrough: LocalDate? = null,
    val receiptImage: String = "",
    /**
     * An IOU (MINT.md 5.8A): money received that someone owes the player back for something the player already paid. It is an [TxType.INCOME]
     * record that counts as no income and pays no reward; the person is its [payee], and its category (a spending one) is the spending it takes
     * away from, like a refund. It is [TxStatus.PENDING] while still owed (no balance change) and [TxStatus.POSTED] once received.
     */
    val iou: Boolean = false,
    /**
     * A record proposed from a bank or wallet alert (MINT.md 10.4): the [CaptureSource.label] it came from, such as
     * "GCash". It is [TxStatus.PENDING] while the player has not confirmed it, and null on every record the player wrote.
     */
    val capturedFrom: String? = null,
    /** The parsed alert's fingerprint, so the same alert arriving twice is one proposal; null on other records. */
    val captureRef: String? = null,
)

@Serializable
data class PlanPaymentAllocation(val planId: String, val amountMinor: Long, val no: Int? = null)

@Serializable
data class TransactionSplit(val categoryId: String?, val amountMinor: Long)

@Serializable
enum class BudgetPeriod(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly") }

@Serializable
enum class PlanStatus { ACTIVE, PAUSED, ARCHIVED }

@Serializable
data class Budget(
    val id: String,
    val plannedMinor: Long,
    val currency: String,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val categoryId: String? = null,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdMillis: Long = 0,
)

@Serializable
data class SavingsTarget(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val accountIds: List<String>,
    val targetDay: LocalDate? = null,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdMillis: Long = 0,
)

@Serializable
data class CreditStatement(
    val id: String,
    val accountId: String,
    val statementDay: LocalDate,
    val statementBalanceMinor: Long,
    val minimumPaymentMinor: Long,
    val dueDay: LocalDate,
    val estimatedInterestTxId: String? = null,
    val createdMillis: Long = 0,
)

@Serializable
enum class Frequency(val label: String) { ONCE("One time"), WEEKLY("Every week"), MONTHLY("Every month"), SEMI_ANNUAL("Every six months"), YEARLY("Every year"), SEMI_MONTHLY("Twice a month") }

@Serializable
data class Bill(
    val id: String,
    val name: String,
    val currency: String,
    val frequency: Frequency,
    val firstDue: LocalDate,
    val expectedMinor: Long? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdMillis: Long = 0,
    val remind: Boolean = true,
    val income: Boolean = false,
    val days: List<Int> = emptyList(),
    val workday: Boolean = false,
    val estimate: Boolean = false,
)

@Serializable
enum class SettleStatus { PAID, PARTIAL, SKIPPED }

@Serializable
data class BillPayment(
    val id: String,
    val billId: String,
    val dueDay: LocalDate,
    val status: SettleStatus,
    val paidMinor: Long = 0,
    val settledDay: LocalDate,
    val txId: String? = null,
    val createdMillis: Long = 0,
)

enum class OccurrenceState(val label: String) { UPCOMING("Upcoming"), DUE("Due today"), OVERDUE("Overdue"), PARTIAL("Partly paid"), PAID("Paid"), SKIPPED("Skipped") }

data class Occurrence(val bill: Bill, val dueDay: LocalDate, val state: OccurrenceState, val payment: BillPayment?, val payDay: LocalDate = dueDay) {
    val remainingMinor: Long? get() = bill.expectedMinor?.let { (it - (payment?.paidMinor ?: 0)).coerceAtLeast(0) }
}

@Serializable
data class Reconciliation(
    val id: String,
    val accountId: String,
    val day: LocalDate,
    /** What the player's statement (or the bank's own app) reported, in the account's currency. */
    val statementMinor: Long,
    /** What Mint held for the same account at the time of the check. */
    val bookMinor: Long,
    val adjustmentTxId: String? = null,
    /** Why the player accepted a difference, in their words; blank when the two matched (MINT.md 5.8). */
    val reason: String = "",
    val createdMillis: Long = 0,
    val paid: Boolean = false,
)

@Serializable
data class ReviewRecord(val id: String, val day: LocalDate, val createdMillis: Long = 0, val paid: Boolean = false)

@Serializable
data class MintSettings(
    val progression: Boolean = true,
    val hideValues: Boolean = false,
    val currency: String = "PHP",
    val tutorialSeen: Boolean = false,
    val billReminders: Boolean = false,
    val billReminderDays: Int = 1,
    val billReminderHour: Int = 9,
    val billReminderNames: Boolean = false,
    val gate: Boolean = false,
    val builtInHolidays: Boolean = true,
    val holidaysIgnored: List<LocalDate> = emptyList(),
    val daysOff: List<LocalDate> = emptyList(),
    val overviewWindow: OverviewWindow = OverviewWindow.DAYS_14,
    val overviewPayLater: PayLaterCount = PayLaterCount.WHOLE,
    /** Reads bank and wallet notifications into MINT's pending review (MINT.md 10.4); off until the player turns it on. */
    val captureNotifications: Boolean = false,
    /** Reads bank and wallet text messages into MINT's pending review (MINT.md 10.4, 7.3); off until the player turns it on. */
    val captureMessages: Boolean = false,
)

/** A review item the player put off, and the day it comes back (MINT.md 9.1). */
@Serializable
data class ReviewSnooze(val id: String, val until: LocalDate)

/** One thing that happened to a record after it was saved, kept as its short history (MINT.md 5.7, 5.8). */
@Serializable
data class RecordChange(
    val id: String,
    val txId: String,
    val atMillis: Long,
    /** "edited", "status", "imported" or "adjusted". */
    val kind: String,
    /** What changed, in a few words; never a private word from another module. */
    val detail: String = "",
)

@Serializable
data class MintData(
    val accounts: List<FinancialAccount> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val savingsTargets: List<SavingsTarget> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val billPayments: List<BillPayment> = emptyList(),
    val reconciliations: List<Reconciliation> = emptyList(),
    val reviews: List<ReviewRecord> = emptyList(),
    val plans: List<InstallmentPlan> = emptyList(),
    val creditStatements: List<CreditStatement> = emptyList(),
    val settings: MintSettings = MintSettings(),

    /** The short history of edits, status changes, imports and adjustments; bounded by the store (MINT.md 5.7). */
    val recordChanges: List<RecordChange> = emptyList(),

    /** The review items the player put off, with the day each comes back (MINT.md 9.1). */
    val reviewSnoozes: List<ReviewSnooze> = emptyList(),
)

enum class MintKinds(val label: String) {
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
    TRANSACTIONS("Transactions"),
    BUDGETS("Budgets"),
    BILLS("Bills"),
    REVIEWS("Reconciliations and reviews"),
}
