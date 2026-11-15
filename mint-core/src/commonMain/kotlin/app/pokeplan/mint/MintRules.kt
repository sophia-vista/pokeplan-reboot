package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Why a record was not accepted, in words for the screen. */
sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

/** What is in the account today, split so nothing is hidden: the recorded balance (posted only), and what is still pending. */
data class AccountBalance(val account: FinancialAccount, val postedMinor: Long, val pendingMinor: Long)

/** How much of a budget is used. [percent] is null when nothing is planned. Going over is information, never a failure. */
data class BudgetProgress(val budget: Budget, val start: LocalDate, val endExclusive: LocalDate, val spentMinor: Long, val remainingMinor: Long, val percent: Int?)

/** The estimate of what can be spent, with its basis shown (MINT.md section 4.5): liquid accounts less bills still to pay soon. Never a guarantee. */
data class Available(val currency: String, val liquidMinor: Long, val billsMinor: Long, val billsCount: Int, val availableMinor: Long)

/**
 * MINT's rules, all pure. Principles (MINT.md sections 1.1, 5, 15.2): **exact arithmetic** (minor units), **one canonical owner** of each fact
 * (a balance is derived from the opening balance and the transactions, never stored twice), **no silent conversion** (an amount keeps its
 * currency; nothing is added across currencies), **nothing judged** (going over a budget is information), and **only abstract organisation can
 * ever earn** (what pays is decided here and carries no figure).
 */
object MintRules {
    const val NAME_MAX = 40
    /** The most characters of base64 an account's receive-money QR image may take (about 192 KB of PNG), MINT.md 4.2. */
    const val QR_MAX_CHARS = 262_144

    /** Whether [base64] can be kept as an account's QR: empty (none) or standard base64 of a PNG, small enough. Nothing is decoded or read for its contents. */
    fun checkReceiveQr(base64: String): Check = when {
        base64.isEmpty() -> Check.Ok
        base64.length > QR_MAX_CHARS -> Check.Refused("That image is too big. Pick one under 190 KB.")
        !base64.startsWith(PNG_BASE64_PREFIX) || !base64.all { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' || it == '=' } -> Check.Refused("That is not a picture Mint can keep.")
        else -> Check.Ok
    }

    /** The first characters of any base64-encoded PNG (its 8-byte signature). */
    const val PNG_BASE64_PREFIX = "iVBORw0KGgo"
    const val NOTE_MAX = 500
    const val SUFFIX_MAX = 4

    // --- what the app accepts ---------------------------------------------------------------------------------------------

    fun checkAmount(minor: Long): Check = when {
        minor <= 0 -> Check.Refused("Enter an amount above zero.")
        minor > Money.MAX_MINOR -> Check.Refused("That amount looks wrong.")
        else -> Check.Ok
    }

    fun checkCurrency(code: String): Check =
        if (code.length == 3 && code.all { it in 'A'..'Z' }) Check.Ok else Check.Refused("A currency is three capital letters, like PHP.")

    fun checkAccount(name: String, currency: String, openingMinor: Long, suffix: String): Check = when {
        name.isBlank() -> Check.Refused("Give the account a name.")
        name.trim().length > NAME_MAX -> Check.Refused("Keep the name under $NAME_MAX letters.")
        checkCurrency(currency) is Check.Refused -> checkCurrency(currency)
        openingMinor < 0 || openingMinor > Money.MAX_MINOR -> Check.Refused("That opening balance looks wrong.")
        suffix.length > SUFFIX_MAX || !suffix.all { it.isLetterOrDigit() } -> Check.Refused("Only the last $SUFFIX_MAX letters or digits of a number are kept.")
        else -> Check.Ok
    }

    /** Whether [tx] can be saved as the data stands: the accounts exist and are open, the currencies agree, a transfer has two different legs. */
    fun checkTransaction(data: MintData, tx: Transaction): Check {
        (checkAmount(tx.amountMinor) as? Check.Refused)?.let { return it }
        if (tx.note.length > NOTE_MAX || tx.payee.length > NAME_MAX * 2) return Check.Refused("That text is too long.")
        val from = data.accounts.firstOrNull { it.id == tx.accountId } ?: return Check.Refused("Choose an account.")
        if (from.status != AccountStatus.ACTIVE) return Check.Refused("That account is not open.")
        if (from.currency != tx.currency) return Check.Refused("The amount has to be in the account's currency, ${from.currency}.")
        if (tx.type == TxType.TRANSFER) {
            val to = data.accounts.firstOrNull { it.id == tx.toAccountId } ?: return Check.Refused("Choose where the money goes.")
            if (to.id == from.id) return Check.Refused("Choose two different accounts.")
            if (to.status != AccountStatus.ACTIVE) return Check.Refused("That account is not open.")
            if (to.currency != from.currency) return Check.Refused("Transfers between currencies are not supported yet.")
        } else if (tx.toAccountId != null) return Check.Refused("Only a transfer has a second account.")
        tx.categoryId?.let { id ->
            val c = data.categories.firstOrNull { it.id == id } ?: return Check.Refused("That category is gone.")
            val wanted = when (tx.type) { TxType.INCOME -> CategoryKind.INCOME; TxType.EXPENSE, TxType.FEE, TxType.REFUND -> CategoryKind.EXPENSE; else -> null }
            if (wanted != null && c.kind != wanted) return Check.Refused("That category does not fit ${tx.type.label.lowercase()}.")
        }
        return Check.Ok
    }

    // --- balances ----------------------------------------------------------------------------------------------------------

    /**
     * What [tx] does to [account]'s balance, or null when it does not touch it. An asset's balance is money held (in raises it, out lowers it);
     * a liability's balance is money owed (out raises it, in lowers it), so a card purchase and a card payment are each one record that moves the
     * right way once (MINT.md section 5.6).
     */
    fun effect(tx: Transaction, account: FinancialAccount): Long? {
        val flow: Long = when (tx.type) {
            TxType.EXPENSE, TxType.FEE -> if (tx.accountId == account.id) -tx.amountMinor else return null
            TxType.INCOME, TxType.REFUND -> if (tx.accountId == account.id) tx.amountMinor else return null
            TxType.ADJUSTMENT -> if (tx.accountId == account.id) (if (tx.up) tx.amountMinor else -tx.amountMinor) else return null
            TxType.TRANSFER -> when (account.id) { tx.accountId -> -tx.amountMinor; tx.toAccountId -> tx.amountMinor; else -> return null }
        }
        if (tx.type == TxType.ADJUSTMENT) return flow // an adjustment moves the displayed balance in the direction given, for either kind
        return if (account.type.liability) -flow else flow
    }

    fun balance(data: MintData, account: FinancialAccount): AccountBalance {
        var posted = account.openingMinor
        var pending = 0L
        for (t in data.transactions) {
            val e = effect(t, account) ?: continue
            when (t.status) { TxStatus.POSTED -> posted += e; TxStatus.PENDING -> pending += e; else -> {} }
        }
        return AccountBalance(account, posted, pending)
    }

    /** Every account's balance in one pass over the records (the same figures as [balance] for each), so a long history costs one walk, not one per account. */
    fun balances(data: MintData): List<AccountBalance> {
        val byId = data.accounts.associateBy { it.id }
        val posted = HashMap<String, Long>()
        val pending = HashMap<String, Long>()
        for (t in data.transactions) {
            val into = when (t.status) { TxStatus.POSTED -> posted; TxStatus.PENDING -> pending; else -> continue }
            byId[t.accountId]?.let { a -> effect(t, a)?.let { into[a.id] = (into[a.id] ?: 0L) + it } }
            if (t.toAccountId != null && t.toAccountId != t.accountId) byId[t.toAccountId]?.let { a -> effect(t, a)?.let { into[a.id] = (into[a.id] ?: 0L) + it } }
        }
        return data.accounts.map { AccountBalance(it, it.openingMinor + (posted[it.id] ?: 0L), pending[it.id] ?: 0L) }
    }

    /** Assets less liabilities, per currency, over active accounts. Never converted across currencies: each currency has its own line. */
    fun netPosition(data: MintData): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        for (b in balances(data)) {
            if (b.account.status != AccountStatus.ACTIVE) continue
            val signed = if (b.account.type.liability) -b.postedMinor else b.postedMinor
            result[b.account.currency] = (result[b.account.currency] ?: 0L) + signed
        }
        return result
    }

    /** Money in liquid accounts of [currency], less the bills with a known amount still unpaid in the next [withinDays] days. Credit limits never count. */
    fun available(data: MintData, currency: String, today: LocalDate, withinDays: Int = 14): Available {
        val liquid = balances(data).filter { it.account.status == AccountStatus.ACTIVE && it.account.type.liquid && it.account.currency == currency }.sumOf { it.postedMinor }
        val due = upcoming(data, today, withinDays).filter { it.bill.currency == currency && it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }
        val bills = due.sumOf { it.remainingMinor ?: 0L }
        return Available(currency, liquid, bills, due.size, liquid - bills)
    }

    // --- spending, cash flow and budgets -----------------------------------------------------------------------------------

    private fun counts(t: Transaction) = t.status == TxStatus.POSTED

    /**
     * What was spent in `[from, endExclusive)` in [currency] (overall, or in one [categoryId]): posted expenses and fees, less refunds. A transfer
     * is never spending; an excluded, voided or pending record is left out (MINT.md sections 5.4, 7.1). May be negative if refunds exceed spending.
     */
    fun spent(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String, categoryId: String? = null): Long =
        data.transactions.filter { counts(it) && it.currency == currency && it.day >= from && it.day < endExclusive && (categoryId == null || it.categoryId == categoryId) }
            .sumOf { when (it.type) { TxType.EXPENSE, TxType.FEE -> it.amountMinor; TxType.REFUND -> -it.amountMinor; else -> 0L } }

    /** Money received in the period (income only: a refund is not income, a transfer is not income). */
    fun earned(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String): Long =
        data.transactions.filter { counts(it) && it.currency == currency && it.type == TxType.INCOME && it.day >= from && it.day < endExclusive }.sumOf { it.amountMinor }

    /** Spending by category over the period, largest first; records with no category are one line with a null id. */
    fun spendingByCategory(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String): List<Pair<String?, Long>> =
        data.transactions.filter { counts(it) && it.currency == currency && it.day >= from && it.day < endExclusive && it.type in setOf(TxType.EXPENSE, TxType.FEE, TxType.REFUND) }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { if (it.type == TxType.REFUND) -it.amountMinor else it.amountMinor } }
            .filterValues { it != 0L }
            .toList().sortedByDescending { it.second }

    fun monthStart(day: LocalDate): LocalDate = LocalDate(day.year, day.monthNumber, 1)

    fun weekStartOf(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        val back = (day.dayOfWeek.ordinal - first.ordinal + 7) % 7
        return day.minus(DatePeriod(days = back))
    }

    /** The period [period] that contains [day]: its first day and the day after its last (months follow the calendar, so month length is exact). */
    fun periodOf(period: BudgetPeriod, day: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> = when (period) {
        BudgetPeriod.MONTHLY -> monthStart(day).let { it to it.plus(1, DateTimeUnit.MONTH) }
        BudgetPeriod.WEEKLY -> weekStartOf(day, weekStart).let { it to it.plus(7, DateTimeUnit.DAY) }
    }

    fun progress(data: MintData, budget: Budget, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): BudgetProgress {
        val (start, end) = periodOf(budget.period, today, weekStart)
        val spent = spent(data, start, end, budget.currency, budget.categoryId).coerceAtLeast(0)
        val percent = if (budget.plannedMinor <= 0) null else (spent * 100 / budget.plannedMinor).coerceAtMost(9_999).toInt()
        return BudgetProgress(budget, start, end, spent, budget.plannedMinor - spent, percent)
    }

    // --- bills -------------------------------------------------------------------------------------------------------------

    private fun daysInMonth(year: Int, month: Int): Int = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

    /**
     * The due date of occurrence [index] (0 is [Bill.firstDue]), counted from the first date so nothing drifts: a bill first due on the 31st
     * is due on the last day of a short month and on the 31st again in a long one; one due on 29 February is due on 28 February in other years.
     */
    fun dueDay(bill: Bill, index: Int): LocalDate {
        val first = bill.firstDue
        return when (bill.frequency) {
            Frequency.ONCE -> first
            Frequency.WEEKLY -> first.plus(7 * index, DateTimeUnit.DAY)
            Frequency.MONTHLY -> {
                val total = first.year * 12 + (first.monthNumber - 1) + index
                val year = total.floorDiv(12)
                val month = total.mod(12) + 1
                LocalDate(year, month, minOf(first.dayOfMonth, daysInMonth(year, month)))
            }
            Frequency.YEARLY -> {
                val year = first.year + index
                LocalDate(year, first.monthNumber, minOf(first.dayOfMonth, daysInMonth(year, first.monthNumber)))
            }
        }
    }

    /** Occurrences of [bill] due in `[from, to]`, in order. A paused or archived bill makes none; settled history is read from [MintData.billPayments]. */
    fun occurrences(data: MintData, bill: Bill, from: LocalDate, to: LocalDate, today: LocalDate): List<Occurrence> {
        if (bill.status != PlanStatus.ACTIVE || to < from) return emptyList()
        val out = mutableListOf<Occurrence>()
        var index = 0
        while (index <= MAX_OCCURRENCES) {
            val due = dueDay(bill, index)
            if (due > to) break
            if (due >= from) out += occurrence(data, bill, due, today)
            if (bill.frequency == Frequency.ONCE) break
            index++
        }
        return out
    }

    fun occurrence(data: MintData, bill: Bill, due: LocalDate, today: LocalDate): Occurrence {
        val payment = data.billPayments.firstOrNull { it.billId == bill.id && it.dueDay == due }
        val state = when (payment?.status) {
            SettleStatus.PAID -> OccurrenceState.PAID
            SettleStatus.PARTIAL -> OccurrenceState.PARTIAL
            SettleStatus.SKIPPED -> OccurrenceState.SKIPPED
            null -> when { due < today -> OccurrenceState.OVERDUE; due == today -> OccurrenceState.DUE; else -> OccurrenceState.UPCOMING }
        }
        return Occurrence(bill, due, state, payment)
    }

    /** How far back an unsettled occurrence is still shown as overdue; older ones are history, not a standing reproach. */
    const val OVERDUE_DAYS = 60
    private const val MAX_OCCURRENCES = 5_000

    /** What needs attention: unsettled occurrences from [OVERDUE_DAYS] ago to [withinDays] ahead, soonest first, plus partly paid ones. Settled ones are left out. */
    fun upcoming(data: MintData, today: LocalDate, withinDays: Int = 30): List<Occurrence> =
        data.bills.flatMap { occurrences(data, it, today.minus(DatePeriod(days = OVERDUE_DAYS)), today.plus(withinDays, DateTimeUnit.DAY), today) }
            .filter { it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }
            .sortedBy { it.dueDay }

    /**
     * Occurrences settled as paid or skipped and due in the last [OVERDUE_DAYS] days or ahead, latest first: the ones [upcoming] leaves out, so a wrong
     * amount or a slip of the finger can still be settled again or taken back. Partly paid ones are in [upcoming] already.
     */
    fun settled(data: MintData, today: LocalDate): List<Occurrence> {
        val from = today.minus(DatePeriod(days = OVERDUE_DAYS))
        return data.billPayments.filter { it.dueDay >= from && it.status != SettleStatus.PARTIAL }
            .mapNotNull { p -> data.bills.firstOrNull { it.id == p.billId }?.let { occurrence(data, it, p.dueDay, today) } }
            .sortedByDescending { it.dueDay }
    }

    // --- reconciliation ----------------------------------------------------------------------------------------------------

    /** The difference the user would be accepting: what the statement says minus what Mint's records say (positive means Mint is short). */
    fun reconcileDifference(statementMinor: Long, bookMinor: Long): Long = statementMinor - bookMinor

    // --- Ledger Research: what may pay (MINT.md section 16) -------------------------------------------------------------------

    /** Reconciled accounts paid in a day; the rest are still kept. *(tune)* */
    const val RECONCILES_PAID_PER_DAY = 2

    /** A review pays at most once a week. *(tune)* */
    const val REVIEWS_PAID_PER_WEEK = 1

    /** Whether Mint has anything worth organising: an open account and at least one transaction. Empty records are never rewarded (section 16.2). */
    fun hasSubstance(data: MintData): Boolean =
        data.accounts.any { it.status == AccountStatus.ACTIVE } && data.transactions.any { it.status == TxStatus.POSTED }

    /** Whether a reconciliation of [accountId] on [day] may be paid: Ledger Research is on, the account has real history, and the day's share is not used. */
    fun reconcilePays(data: MintData, accountId: String, day: LocalDate): Boolean {
        if (!data.settings.progression) return false
        if (data.transactions.none { it.status == TxStatus.POSTED && (it.accountId == accountId || it.toAccountId == accountId) && it.source == TxSource.MANUAL }) return false
        val today = data.reconciliations.filter { it.day == day && it.paid }
        return today.none { it.accountId == accountId } && today.size < RECONCILES_PAID_PER_DAY
    }

    /** Whether a finished review on [day] may be paid: Ledger Research is on, there is something to review, and this week's review has not paid yet. */
    fun reviewPays(data: MintData, day: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Boolean {
        if (!data.settings.progression || !hasSubstance(data)) return false
        val start = weekStartOf(day, weekStart)
        return data.reviews.count { it.paid && weekStartOf(it.day, weekStart) == start } < REVIEWS_PAID_PER_WEEK
    }

    // --- search and the starting categories ----------------------------------------------------------------------------------

    /** Transactions matching a text (payee, note, category name, account name or amount) and optional filters, newest first. */
    fun search(
        data: MintData,
        text: String = "",
        type: TxType? = null,
        accountId: String? = null,
        categoryId: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): List<Transaction> {
        val q = text.trim().lowercase()
        return data.transactions.filter { t ->
            (type == null || t.type == type) && (accountId == null || t.accountId == accountId || t.toAccountId == accountId) &&
                (categoryId == null || t.categoryId == categoryId) && (from == null || t.day >= from) && (to == null || t.day <= to) &&
                (q.isEmpty() || matches(data, t, q))
        }.sortedWith(compareByDescending<Transaction> { it.atMillis }.thenByDescending { it.createdMillis })
    }

    private fun matches(data: MintData, t: Transaction, q: String): Boolean =
        t.payee.lowercase().contains(q) || t.note.lowercase().contains(q) ||
            data.categories.firstOrNull { it.id == t.categoryId }?.name?.lowercase()?.contains(q) == true ||
            data.accounts.firstOrNull { it.id == t.accountId }?.name?.lowercase()?.contains(q) == true ||
            Money.plain(t.amountMinor, t.currency).contains(q.replace(",", ""))

    /** A compact set for Philippine use, with stable ids; every one can be renamed or archived. */
    fun seedCategories(): List<Category> = listOf(
        "food" to "Groceries", "dining" to "Eating out", "transport" to "Transport", "bills" to "Bills and utilities", "rent" to "Rent and housing",
        "health" to "Health", "shopping" to "Shopping", "school" to "School", "fun" to "Fun", "family" to "Family and gifts", "care" to "Personal care", "other" to "Other",
    ).map { (key, name) -> Category("cat-$key", name, CategoryKind.EXPENSE) } + listOf(
        "salary" to "Salary", "business" to "Business", "gift-in" to "Gifts received", "other-in" to "Other income",
    ).map { (key, name) -> Category("cat-$key", name, CategoryKind.INCOME) }
}
