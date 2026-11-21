package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything Mint keeps is money in **integer minor units** of one currency (centavos for PHP, cents for USD, whole yen for JPY): never a
 * floating-point number (MINT.md section 5.2). An amount is always positive; its direction comes from the transaction type.
 */
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

/**
 * One place money is held or owed (MINT.md section 4). For a liability the balance is the amount owed. Only a short, optional [suffix] of an
 * identifier is kept for recognition: never a full number, a PIN, a code or a password (section 4.2).
 */
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
    /** A [CardStyle] id or a custom colour `#RRGGBB` (see [MintRules.cardId]): how the account's card looks. Cosmetic only; it never changes a balance, a rule or a report. Empty is the plain card. */
    val card: String = "",
    /**
     * The account's own "scan to send money to me" QR (MINT.md 4.2, 15 November 2026): one picture (PNG, JPEG or WebP, usually the wallet's whole screenshot), base64, at most [MintRules.QR_MAX_CHARS] characters, kept as picked. It lives in this sealed row, is never uploaded or read for its contents, and changes no balance. Empty is none.
     */
    val receiveQr: String = "",
    /** Left out of net position, available money and group subtotals (MINT.md 4.3B). It still shows its own balance and records; changes no record. */
    val hideFromTotal: Boolean = false,
    /** The Android package of the account's own app (MINT.md 4.3C), picked from the installed apps; empty is none. Only a package name, never a credential. */
    val appPackage: String = "",
    /** A credit card's limit in minor units of [currency] (MINT.md 4.3D); 0 is none. Information only: never an asset, never cash, in no total. */
    val limitMinor: Long = 0,
    /** A credit card's statement day and payment due day of the month, 1 to 31; 0 is none (MINT.md 4.3D). Information only: no reminder or record is made from them. */
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    /** Optional annual rate for simple estimated interest, in basis points; zero disables estimation. */
    val annualAprBasisPoints: Int = 0,
    /** Days after the latest statement's due date before estimated interest starts. */
    val interestGraceDays: Int = 0,
    /** Interest this deposit account earns (MINT.md 4.3E): the yearly rate in basis points, how often the bank credits it, the tax it withholds and whether estimates post without review. Liabilities keep these off. */
    val earnRateBasisPoints: Int = 0,
    val earnSchedule: EarnSchedule = EarnSchedule.OFF,
    val earnTaxBasisPoints: Int = 0,
    val earnAutoPost: Boolean = false,
    /** The first day interest is estimated for: the day the schedule was switched on, so nothing is back-filled. */
    val earnFrom: LocalDate? = null,
)

/** How often a deposit account is credited interest: [DAILY] (Maya, MariBank, GoTyme style) or [MONTHLY]. */
@Serializable
enum class EarnSchedule(val label: String) { OFF("Off"), DAILY("Every day"), MONTHLY("Every month") }

/**
 * The look of an account's card (REP-056, cosmetic). The ids are saved; the colour for each is chosen in the UI, so a palette change never touches the data.
 * An unknown id (from a newer build) reads as [PLAIN].
 */
enum class CardStyle(val id: String, val label: String) {
    PLAIN("", "Plain"), BLUE("blue", "Blue"), GREEN("green", "Green"), GOLD("gold", "Gold"), VIOLET("violet", "Violet"), SLATE("slate", "Slate");

    companion object {
        fun of(id: String): CardStyle = entries.firstOrNull { it.id == id } ?: PLAIN

        /** The 0xRRGGBB of a custom card colour `#RRGGBB`, or null when [card] is not one (a style id or empty). */
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

/** Pending and posted are states of a transaction, not types; a balance counts posted ones only (MINT.md section 5.1). */
@Serializable
enum class TxStatus { PENDING, POSTED, EXCLUDED, VOIDED }

/** How a record got here: [MANUAL] typed by the player; [ADJUSTMENT] made by a reconciliation; [IMPORTED] from a statement file the player chose (a user-assisted import; no provider is connected). */
@Serializable
enum class TxSource { MANUAL, ADJUSTMENT, IMPORTED }

/**
 * One money event (MINT.md section 5). A transfer is **one record with two legs** ([accountId] to [toAccountId]), so it can never be saved
 * one-sided (section 5.5). [amountMinor] is positive and in the currency of the account(s). A balance adjustment moves the balance up or
 * down by its amount ([up]); it is only ever made explicitly, by the user or by a reconciliation they accept (section 15.2, item 8).
 */
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
    /** Only for [TxType.ADJUSTMENT]: whether the balance goes up (true) or down. */
    val up: Boolean = true,
    /** Optionally the expense a refund gives back. */
    val refundOf: String? = null,
    /** The bill occurrence this payment is linked to (`billId` and due day), only when the user chose to link it. */
    val billId: String? = null,
    val billDueDay: LocalDate? = null,
    val createdMillis: Long = atMillis,
    /**
     * Provenance of an imported record (INTEGRATIONS_AND_PRIVACY.md 10.2, MINT.md 5.3): the batch it came in with (`csv:<millis>`), the file's name as the player knew it,
     * and the fingerprint that lets the same row be recognised on a second import. All null for a record the player wrote. [unreviewed] is true until the player has
     * looked at an imported record (MINT.md 9.1), which changes nothing about it.
     */
    val importBatch: String? = null,
    val importLabel: String = "",
    val fingerprint: String? = null,
    val unreviewed: Boolean = false,
    /** The installment plan this record belongs to (MINT.md 5.6A): its purchase, a repayment (a transfer to the plan's account), a fee or late fee, or a refund. */
    val planId: String? = null,
    /** Which installment of the plan a fee or late fee is for (null for the purchase, a repayment or a refund). */
    val planNo: Int? = null,
    /** Only for [TxType.FEE]: a plain fee, interest or a late fee. Null reads as a plain fee. */
    val feeKind: FeeKind? = null,
    /** A transfer may be allocated across several installment plans on its destination account. */
    val planAllocations: List<PlanPaymentAllocation> = emptyList(),
    /** Optional category allocation; when present, its parts must add up to this transaction's amount. */
    val splits: List<TransactionSplit> = emptyList(),
    /** The statement whose estimated interest this transaction records, null for ordinary fees. */
    val creditStatementId: String? = null,
    /** Only for [TxType.TRANSFER]: what the sending side charged on top of [amountMinor] (MINT.md 5.5). The source falls by both; the destination gets only the amount; the fee counts as a fee in spending. */
    val feeMinor: Long = 0,
    /** Whether logging this record paid Ledger Research's flat reward (MINT.md 16.2). It only counts the day's paid records; it holds no figure. */
    val paid: Boolean = false,
    /** Only for earned interest (an [TxType.INCOME] record made by [MintRules.accrue]): the last day this estimate covers. The latest one per account is where the next estimate starts. */
    val interestThrough: LocalDate? = null,
    /** One optional receipt image as base64 PNG, kept inside this transaction's sealed record. */
    val receiptImage: String = "",
    /**
     * An IOU started by this expense (MINT.md 5.8A): the part of [amountMinor] someone else owes the player back, above zero and never more than
     * the amount. It changes no balance; spending counts only the player's own share, [amountMinor] less this. Splits and an IOU never meet on one record.
     */
    val iouMinor: Long = 0,
    /** Who owes an IOU, a plain name as the player keeps it (at most [MintRules.NAME_MAX]); empty when there is no IOU. */
    val iouFor: String = "",
    /** On a repayment (an [TxType.INCOME] record): the expense whose IOU it settles. It counts as no income and pays no reward. */
    val repaymentOf: String? = null,
)

/** How much of a repayment went to a plan; with [no] it went to that installment (a monthly bill's line), without it to the plan's earliest installments first. */
@Serializable
data class PlanPaymentAllocation(val planId: String, val amountMinor: Long, val no: Int? = null)

@Serializable
data class TransactionSplit(val categoryId: String?, val amountMinor: Long)

@Serializable
enum class BudgetPeriod(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly") }

@Serializable
enum class PlanStatus { ACTIVE, PAUSED, ARCHIVED }

/**
 * A planned amount for a period. [categoryId] null is the overall spending budget. What was actually spent is derived from posted
 * transactions and never stored (MINT.md section 7.1, invariant 9). Exceeding a budget is information, not a failure.
 */
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

/** A bill (MINT.md section 8), or a recurring income when [income] (8.3A): what is expected and when. Its occurrences are derived from [firstDue] and [frequency]; only settled ones are stored. */
@Serializable
data class Bill(
    val id: String,
    val name: String,
    val currency: String,
    val frequency: Frequency,
    val firstDue: LocalDate,
    /** The expected amount, or null for a variable bill whose amount is entered when it is known. */
    val expectedMinor: Long? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdMillis: Long = 0,
    /** Whether this bill may ask for a reminder at all (it still needs [MintSettings.billReminders]); the user can switch one bill off. */
    val remind: Boolean = true,
    /** A recurring income (a salary, an allowance) rather than something to pay (MINT.md 8.3A): same schedule, and "Received, record it" writes an income. */
    val income: Boolean = false,
    /** For [Frequency.SEMI_MONTHLY] the days of the month it falls on (for example 15 and 30; a day a month lacks is its last day); empty reads as 15 and 30 (MINT.md 8.3B). */
    val days: List<Int> = emptyList(),
    /** Moves each date to the nearest working day on or before it: not a weekend, not a holiday the player counts (MINT.md 8.3B). */
    val workday: Boolean = false,
    /** The expected amount is an estimate, not final: shown with "about", and the real amount is always entered when it is recorded. */
    val estimate: Boolean = false,
)

/** What the user settled for one occurrence. Marking an occurrence paid never creates a transaction; [txId] only links one the user chose. */
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

/** The state of one occurrence for today: derived, except the three the user settled. */
enum class OccurrenceState(val label: String) { UPCOMING("Upcoming"), DUE("Due today"), OVERDUE("Overdue"), PARTIAL("Partly paid"), PAID("Paid"), SKIPPED("Skipped") }

/** [dueDay] is the scheduled date and what a settlement is keyed by; [payDay] is the date it is really due or paid, moved to a working day when the bill asks (MINT.md 8.3B). */
data class Occurrence(val bill: Bill, val dueDay: LocalDate, val state: OccurrenceState, val payment: BillPayment?, val payDay: LocalDate = dueDay) {
    /** What is still to pay: the expected amount less what was paid, or null when the bill's amount is variable. */
    val remainingMinor: Long? get() = bill.expectedMinor?.let { (it - (payment?.paidMinor ?: 0)).coerceAtLeast(0) }
}

/**
 * An account reconciled against a statement or a figure the user read off (MINT.md section 5.8). Append-only: it keeps what the statement
 * said, what Mint's records said, and the adjustment (if the user accepted one). [paid] is set once when Ledger Research paid for it.
 */
@Serializable
data class Reconciliation(
    val id: String,
    val accountId: String,
    val day: LocalDate,
    val statementMinor: Long,
    val bookMinor: Long,
    val adjustmentTxId: String? = null,
    val createdMillis: Long = 0,
    val paid: Boolean = false,
)

/** A finished periodic financial review (MINT.md section 9.1): when it was done and whether it paid. It holds no figure. */
@Serializable
data class ReviewRecord(val id: String, val day: LocalDate, val createdMillis: Long = 0, val paid: Boolean = false)

/**
 * Mint's preferences. [progression] is Ledger Research (the reward): on by default because only abstract administrative events leave Mint, and
 * off at any time without touching a record (section 16.4). [hideValues] hides every amount across Mint (section 3.3).
 */
@Serializable
data class MintSettings(
    val progression: Boolean = true,
    val hideValues: Boolean = false,
    val currency: String = "PHP",
    val tutorialSeen: Boolean = false,
    /** Bill reminders (MINT.md section 12): **off until the user turns them on**, one per bill occurrence, never a daily repeat for an overdue one. */
    val billReminders: Boolean = false,
    /** How many days before the due day the reminder is for: 0 is the day itself. */
    val billReminderDays: Int = 1,
    /** The hour of the day it goes off (the shared quiet hours and a focus session can still hold it). */
    val billReminderHour: Int = 9,
    /** Whether the notification names the bill. Off by default: it says only that a bill is coming up. Never an amount, either way. */
    val billReminderNames: Boolean = false,
    /** The optional gate (MINT.md 13.2): Mint asks the phone's own screen lock before it shows anything. Only a preference; no key depends on it. */
    val gate: Boolean = false,
    /** Whether the built-in Philippine holidays count as days off for paydays (MINT.md 8.3B). */
    val builtInHolidays: Boolean = true,
    /** Built-in holidays the player turned off, and days off they added (Eid, a proclaimed day). */
    val holidaysIgnored: List<LocalDate> = emptyList(),
    val daysOff: List<LocalDate> = emptyList(),
    /** How far ahead the Overview and the widgets look for what is due (MINT.md 3.2B). */
    val overviewWindow: OverviewWindow = OverviewWindow.DAYS_14,
    /** How much of a Pay later account the Overview's net position counts (MINT.md 3.2B). */
    val overviewPayLater: PayLaterCount = PayLaterCount.WHOLE,
)

/**
 * Everything MINT keeps on this device, as one document that `MintTables` takes apart into one table family per kind of record. It never holds
 * anything about Pokémon or rewards.
 */
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
    /** Installment plans (MINT.md 5.6A). Kept as one sealed list in `mint_state`, not a table of their own. */
    val plans: List<InstallmentPlan> = emptyList(),
    val creditStatements: List<CreditStatement> = emptyList(),
    val settings: MintSettings = MintSettings(),
)

/** The kinds of private record, each kept in its own table family (`mint_account`, ...); the names are stable. */
enum class MintKinds(val label: String) {
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
    TRANSACTIONS("Transactions"),
    BUDGETS("Budgets"),
    BILLS("Bills"),
    REVIEWS("Reconciliations and reviews"),
}
