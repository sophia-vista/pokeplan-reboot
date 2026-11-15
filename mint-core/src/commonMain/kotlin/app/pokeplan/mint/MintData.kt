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
)

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
)

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
enum class Frequency(val label: String) { ONCE("One time"), WEEKLY("Every week"), MONTHLY("Every month"), YEARLY("Every year") }

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

data class Occurrence(val bill: Bill, val dueDay: LocalDate, val state: OccurrenceState, val payment: BillPayment?) {
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
    val bills: List<Bill> = emptyList(),
    val billPayments: List<BillPayment> = emptyList(),
    val reconciliations: List<Reconciliation> = emptyList(),
    val reviews: List<ReviewRecord> = emptyList(),
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
