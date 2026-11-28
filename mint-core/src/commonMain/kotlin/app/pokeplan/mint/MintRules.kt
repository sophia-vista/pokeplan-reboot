package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

data class AccountBalance(val account: FinancialAccount, val postedMinor: Long, val pendingMinor: Long)

data class BudgetProgress(val budget: Budget, val start: LocalDate, val endExclusive: LocalDate, val spentMinor: Long, val remainingMinor: Long, val percent: Int?, val carriedMinor: Long = 0)
data class SavingsProgress(val target: SavingsTarget, val savedMinor: Long, val remainingMinor: Long, val percent: Int)

data class Available(val currency: String, val liquidMinor: Long, val billsMinor: Long, val billsCount: Int, val availableMinor: Long, val installmentsMinor: Long = 0, val installmentsCount: Int = 0)

object MintRules {
    const val NAME_MAX = 40
    const val QR_MAX_CHARS = 1_400_000
    const val MAX_APR_BASIS_POINTS = 100_000
    const val MAX_GRACE_DAYS = 365

    fun checkReceiveQr(base64: String): Check = when {
        base64.isEmpty() -> Check.Ok
        base64.length > QR_MAX_CHARS -> Check.Refused("That picture is too big to keep (1 MB at most).")
        IMAGE_BASE64_PREFIXES.none { base64.startsWith(it) } || !base64.all { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' || it == '=' } -> Check.Refused("That is not a picture Mint can keep.")
        else -> Check.Ok
    }

    const val PACKAGE_MAX = 150

    fun cleanPackage(raw: String): String {
        val t = raw.trim()
        return if (t.length in 3..PACKAGE_MAX && t.contains('.') && t.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' }) t else ""
    }

    const val PNG_BASE64_PREFIX = "iVBORw0KGgo"

    val IMAGE_BASE64_PREFIXES = listOf(PNG_BASE64_PREFIX, "/9j/", "UklGR")

    fun cardId(raw: String): String {
        val t = raw.trim()
        if (t.length == 7 && t[0] == '#' && t.drop(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return t.uppercase()
        return CardStyle.of(t).id
    }

    const val NOTE_MAX = 500
    const val SUFFIX_MAX = 4

    fun checkAmount(minor: Long): Check = when {
        minor <= 0 -> Check.Refused("Enter an amount above zero.")
        minor > Money.MAX_MINOR -> Check.Refused("That amount looks wrong.")
        else -> Check.Ok
    }

    fun checkCurrency(code: String): Check =
        if (code.length == 3 && code.all { it in 'A'..'Z' }) Check.Ok else Check.Refused("A currency is three capital letters, like PHP.")

    fun checkLimit(type: AccountType, limitMinor: Long): Check = when {
        limitMinor == 0L -> Check.Ok
        !Installments.allowedFor(type) -> Check.Refused("Only a credit card or a pay later account has a limit.")
        limitMinor < 0 || limitMinor > Money.MAX_MINOR -> Check.Refused("That limit looks wrong.")
        else -> Check.Ok
    }

    fun checkCardDay(type: AccountType, day: Int): Check = when {
        day == 0 -> Check.Ok
        !Installments.allowedFor(type) -> Check.Refused("Only a credit card or a pay later account has a statement or due day.")
        day !in 1..31 -> Check.Refused("Choose a day from 1 to 31.")
        else -> Check.Ok
    }

    fun nextOnDay(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        fun inMonth(y: Int, m: Int): LocalDate {
            val last = LocalDate(y, m, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
            return LocalDate(y, m, minOf(day, last))
        }
        val here = inMonth(today.year, today.monthNumber)
        if (here >= today) return here
        val next = LocalDate(today.year, today.monthNumber, 1).plus(DatePeriod(months = 1))
        return inMonth(next.year, next.monthNumber)
    }

    fun leftToSpend(account: FinancialAccount, owedMinor: Long): Long? = account.limitMinor.takeIf { it > 0 }?.let { (it - owedMinor).coerceAtLeast(0) }

    fun checkAccount(name: String, currency: String, openingMinor: Long, suffix: String): Check = when {
        name.isBlank() -> Check.Refused("Give the account a name.")
        name.trim().length > NAME_MAX -> Check.Refused("Keep the name under $NAME_MAX letters.")
        checkCurrency(currency) is Check.Refused -> checkCurrency(currency)
        openingMinor < 0 || openingMinor > Money.MAX_MINOR -> Check.Refused("That opening balance looks wrong.")
        suffix.length > SUFFIX_MAX || !suffix.all { it.isLetterOrDigit() } -> Check.Refused("Only the last $SUFFIX_MAX letters or digits of a number are kept.")
        else -> Check.Ok
    }

    fun checkTransaction(data: MintData, tx: Transaction): Check {
        (checkAmount(tx.amountMinor) as? Check.Refused)?.let { return it }
        if (tx.note.length > NOTE_MAX || tx.payee.length > NAME_MAX * 2) return Check.Refused("That text is too long.")
        if (tx.receiptImage.isNotEmpty()) (checkReceiveQr(tx.receiptImage) as? Check.Refused)?.let {
            return Check.Refused(it.reason.replace("picture", "receipt image"))
        }
        val from = data.accounts.firstOrNull { it.id == tx.accountId } ?: return Check.Refused("Choose an account.")
        if (from.status != AccountStatus.ACTIVE) return Check.Refused("That account is not open.")
        if (from.currency != tx.currency) return Check.Refused("The amount has to be in the account's currency, ${from.currency}.")
        if (tx.type == TxType.TRANSFER) {
            val to = data.accounts.firstOrNull { it.id == tx.toAccountId } ?: return Check.Refused("Choose where the money goes.")
            if (to.id == from.id) return Check.Refused("Choose two different accounts.")
            if (to.status != AccountStatus.ACTIVE) return Check.Refused("That account is not open.")
            if (to.currency != from.currency) return Check.Refused("Transfers between currencies are not supported yet.")
            if (tx.planAllocations.isNotEmpty()) {
                if (tx.planId != null) return Check.Refused("Choose one way to link this repayment.")
                var allocated = 0L
                val seen = mutableSetOf<Pair<String, Int?>>()
                for (allocation in tx.planAllocations) {
                    if (allocation.amountMinor <= 0 || allocation.amountMinor > tx.amountMinor - allocated || !seen.add(allocation.planId to allocation.no))
                        return Check.Refused("That repayment allocation looks wrong.")
                    val plan = data.plans.firstOrNull { it.id == allocation.planId } ?: return Check.Refused("An installment plan is missing.")
                    if (plan.accountId != to.id) return Check.Refused("That plan belongs to another account.")
                    if (allocation.no != null && plan.installments.none { it.no == allocation.no }) return Check.Refused("That installment is missing.")
                    allocated += allocation.amountMinor
                }
            }
        } else if (tx.toAccountId != null) return Check.Refused("Only a transfer has a second account.")
        if (tx.planAllocations.isNotEmpty() && tx.type != TxType.TRANSFER)
            return Check.Refused("Only a repayment can be allocated to installment plans.")
        if (tx.creditStatementId != null &&
            (tx.type != TxType.FEE || tx.feeKind != FeeKind.INTEREST || data.creditStatements.none { it.id == tx.creditStatementId }))
            return Check.Refused("That estimated-interest record is not linked to a statement.")
        if (tx.feeMinor != 0L && (tx.type != TxType.TRANSFER || tx.feeMinor < 0 || tx.feeMinor > Money.MAX_MINOR)) return Check.Refused("Only a transfer has a fee, and it has to be positive.")
        if (tx.type == TxType.TRANSFER && tx.categoryId != null && data.categories.firstOrNull { it.id == tx.categoryId }?.kind != CategoryKind.EXPENSE)
            return Check.Refused("A transfer's category is for its fee, and it has to be a spending category.")
        tx.categoryId?.let { id ->
            val c = data.categories.firstOrNull { it.id == id } ?: return Check.Refused("That category is gone.")
            val wanted = when (tx.type) { TxType.INCOME -> if (tx.iou) CategoryKind.EXPENSE else CategoryKind.INCOME; TxType.EXPENSE, TxType.FEE, TxType.REFUND -> CategoryKind.EXPENSE; else -> null }
            if (wanted != null && c.kind != wanted) return Check.Refused("That category does not fit ${tx.type.label.lowercase()}.")
        }
        if (tx.splits.isNotEmpty()) {
            if (tx.type !in setOf(TxType.EXPENSE, TxType.INCOME, TxType.REFUND, TxType.FEE))
                return Check.Refused("Only income, spending or refund records can be split.")
            if (tx.categoryId != null || tx.splits.size < 2)
                return Check.Refused("Choose at least two categories for a split.")
            var splitTotal = 0L
            val wanted = if (tx.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            for (part in tx.splits) {
                if (part.amountMinor <= 0 || part.amountMinor > tx.amountMinor - splitTotal)
                    return Check.Refused("Split amounts must be positive and add up to the transaction.")
                part.categoryId?.let { id ->
                    val category = data.categories.firstOrNull { it.id == id } ?: return Check.Refused("A split category is gone.")
                    if (category.kind != wanted) return Check.Refused("A split category does not fit ${tx.type.label.lowercase()}.")
                }
                splitTotal += part.amountMinor
            }
            if (splitTotal != tx.amountMinor)
                return Check.Refused("Split amounts must add up to the transaction.")
        }
        if (tx.iou) {
            if (tx.type != TxType.INCOME) return Check.Refused("Only money received can be an IOU.")
            if (tx.splits.isNotEmpty()) return Check.Refused("An IOU cannot be split across categories.")
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
            TxType.TRANSFER -> when (account.id) { tx.accountId -> -(tx.amountMinor + tx.feeMinor); tx.toAccountId -> tx.amountMinor; else -> return null }
        }
        if (tx.type == TxType.ADJUSTMENT) return flow
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

    fun simpleInterestEstimate(data: MintData, account: FinancialAccount, from: LocalDate, through: LocalDate): Long {
        if (!account.type.liability) return 0
        return dailyInterest(data, account, account.annualAprBasisPoints, from, through)
    }

    private fun dailyInterest(data: MintData, account: FinancialAccount, apr: Int, from: LocalDate, through: LocalDate): Long {
        if (apr <= 0 || apr > MAX_APR_BASIS_POINTS || from > through) return 0
        val denominator = 3_650_000L
        val relevant = data.transactions.asSequence()
            .filter { it.status == TxStatus.POSTED && it.day >= account.openingDay && it.day <= through }
            .sortedBy { it.day }
            .toList()
        var balance = if (account.openingDay <= from) account.openingMinor else 0L
        relevant.filter { it.day < from }.forEach { tx -> effect(tx, account)?.let { balance += it } }
        var whole = 0L
        var fraction = 0L
        var day = from
        var index = relevant.indexOfFirst { it.day >= from }.let { if (it < 0) relevant.size else it }
        while (day <= through) {
            if (day == account.openingDay && from < account.openingDay) balance = account.openingMinor
            while (index < relevant.size && relevant[index].day == day) {
                effect(relevant[index], account)?.let { balance += it }
                index++
            }
            val owed = balance.coerceAtLeast(0)
            val numerator = (owed / denominator) * apr + (owed % denominator) * apr
            whole += numerator / denominator
            fraction += numerator % denominator
            whole += fraction / denominator
            fraction %= denominator
            day = day.plus(DatePeriod(days = 1))
        }
        val rounded = whole + if (fraction * 2 >= denominator) 1 else 0
        return rounded.coerceAtMost(Money.MAX_MINOR)
    }

    const val MAX_TAX_BASIS_POINTS = 5_000

    const val INTEREST_CATEGORY = "cat-interest"

    fun checkEarn(type: AccountType, rateBasisPoints: Int, schedule: EarnSchedule, taxBasisPoints: Int): Check {
        val any = rateBasisPoints != 0 || schedule != EarnSchedule.OFF || taxBasisPoints != 0
        if (any && (type.liability || type == AccountType.CASH)) return Check.Refused("Interest earned is for bank, wallet and other deposit accounts.")
        if (rateBasisPoints !in 0..MAX_APR_BASIS_POINTS) return Check.Refused("Enter a yearly rate from 0 to 1000 percent.")
        if (taxBasisPoints !in 0..MAX_TAX_BASIS_POINTS) return Check.Refused("Enter a tax from 0 to 50 percent.")
        if (schedule != EarnSchedule.OFF && rateBasisPoints == 0) return Check.Refused("Enter a yearly rate to estimate interest.")
        return Check.Ok
    }
    private const val MAX_ACCRUAL_PERIODS = 12
    private const val MAX_ACCRUAL_DAYS = 366

    data class InterestPeriod(val from: LocalDate, val through: LocalDate)

    fun earnsInterest(account: FinancialAccount): Boolean =
        !account.type.liability && account.type != AccountType.CASH && account.earnSchedule != EarnSchedule.OFF && account.earnRateBasisPoints > 0 && account.status == AccountStatus.ACTIVE

    fun earnedInterest(data: MintData, account: FinancialAccount, from: LocalDate, through: LocalDate): Long {
        if (account.type.liability) return 0
        val gross = dailyInterest(data, account, account.earnRateBasisPoints, from, through)
        return gross - (gross * account.earnTaxBasisPoints.coerceIn(0, MAX_TAX_BASIS_POINTS) + 5_000) / 10_000
    }

    fun interestPeriods(data: MintData, account: FinancialAccount, today: LocalDate): List<InterestPeriod> {
        if (!earnsInterest(account)) return emptyList()
        val last = data.transactions.filter { it.accountId == account.id && it.interestThrough != null }.maxOfOrNull { it.interestThrough!! }
        val firstDay = maxOf(account.earnFrom ?: today, account.openingDay)
        var next = if (last == null) firstDay else last.plus(DatePeriod(days = 1))
        val yesterday = today.minus(DatePeriod(days = 1))
        if (next > yesterday) return emptyList()
        if (account.earnSchedule == EarnSchedule.DAILY) {
            val start = maxOf(next, yesterday.minus(DatePeriod(days = MAX_ACCRUAL_DAYS - 1)))
            return listOf(InterestPeriod(start, yesterday))
        }
        val periods = mutableListOf<InterestPeriod>()
        while (periods.size < MAX_ACCRUAL_PERIODS) {
            val monthEnd = monthStart(next).plus(1, DateTimeUnit.MONTH).minus(DatePeriod(days = 1))
            if (monthEnd > yesterday) break
            periods += InterestPeriod(next, monthEnd)
            next = monthEnd.plus(DatePeriod(days = 1))
        }
        return periods
    }

    fun netPosition(data: MintData, today: LocalDate? = null): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        for (b in balances(data)) {
            if (b.account.status != AccountStatus.ACTIVE || b.account.hideFromTotal) continue
            val owed = if (b.account.type.liability && today != null) OverviewCounts.liabilityCounted(data, b.account, b.postedMinor, today) else b.postedMinor
            val signed = if (b.account.type.liability) -owed else owed
            result[b.account.currency] = (result[b.account.currency] ?: 0L) + signed
        }
        return result
    }

    fun available(data: MintData, currency: String, today: LocalDate, withinDays: Int = 14): Available {
        val liquid = balances(data).filter { it.account.status == AccountStatus.ACTIVE && !it.account.hideFromTotal && it.account.type.liquid && it.account.currency == currency }.sumOf { it.postedMinor }
        val due = upcoming(data, today, withinDays).filter { !it.bill.income && it.bill.currency == currency && it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }
        val bills = due.sumOf { it.remainingMinor ?: 0L }
        val (plans, plansCount) = Installments.dueWithin(data, currency, today, withinDays)
        return Available(currency, liquid, bills, due.size, liquid - bills - plans, plans, plansCount)
    }

    private fun counts(t: Transaction) = t.status == TxStatus.POSTED

    /** What spending reads: every posted record, and an IOU still owed too (it already takes its amount off the spending it was for). */
    private fun countsForSpending(t: Transaction) = counts(t) || (t.iou && t.status == TxStatus.PENDING)

    fun spent(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String, categoryId: String? = null): Long =
        data.transactions.filter { countsForSpending(it) && it.currency == currency && it.day >= from && it.day < endExclusive }
            .sumOf { tx ->
                val direction = when (tx.type) { TxType.EXPENSE, TxType.FEE -> 1; TxType.REFUND -> -1; TxType.INCOME -> if (tx.iou) -1 else 0; TxType.TRANSFER -> if (tx.feeMinor > 0) 1 else 0; else -> 0 }
                if (direction == 0) 0L
                else {
                    // A transfer's own amount is never spending; only the fee it was charged is (MINT.md 5.5).
                    val whole = if (tx.type == TxType.TRANSFER) tx.feeMinor else tx.amountMinor
                    val amount = if (categoryId == null) whole
                    else if (tx.splits.isNotEmpty()) tx.splits.filter { it.categoryId == categoryId }.sumOf { it.amountMinor }
                    else if (tx.categoryId == categoryId) whole else 0L
                    direction * amount
                }
            }

    /** Money received in the period (income only: a refund is not income, a transfer is not income, and a repayment of an IOU is the player's own money coming back). */
    fun earned(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String): Long =
        data.transactions.filter { counts(it) && it.currency == currency && it.type == TxType.INCOME && !it.iou && it.day >= from && it.day < endExclusive }.sumOf { it.amountMinor }

    fun spendingByCategory(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String): List<Pair<String?, Long>> {
        val totals = mutableMapOf<String?, Long>()
        data.transactions.filter { countsForSpending(it) && it.currency == currency && it.day >= from && it.day < endExclusive && (it.type in setOf(TxType.EXPENSE, TxType.FEE, TxType.REFUND) || it.iou || (it.type == TxType.TRANSFER && it.feeMinor > 0)) }
            .forEach { tx ->
                val direction = if (tx.type == TxType.REFUND || tx.iou) -1 else 1
                val parts = tx.splits.ifEmpty { listOf(TransactionSplit(tx.categoryId, if (tx.type == TxType.TRANSFER) tx.feeMinor else tx.amountMinor)) }
                parts.forEach { part -> totals[part.categoryId] = (totals[part.categoryId] ?: 0L) + direction * part.amountMinor }
            }
        return totals.filterValues { it != 0L }.toList().sortedByDescending { it.second }
    }

    fun monthStart(day: LocalDate): LocalDate = app.pokeplan.kit.Periods.monthStart(day)

    fun weekStartOf(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate = app.pokeplan.kit.Periods.weekStartOf(day, first)

    fun periodOf(period: BudgetPeriod, day: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> = when (period) {
        BudgetPeriod.MONTHLY -> app.pokeplan.kit.Periods.monthRange(day)
        BudgetPeriod.WEEKLY -> app.pokeplan.kit.Periods.weekRange(day, weekStart)
    }

    fun progress(data: MintData, budget: Budget, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): BudgetProgress {
        val (start, end) = periodOf(budget.period, today, weekStart)
        val spent = spent(data, start, end, budget.currency, budget.categoryId).coerceAtLeast(0)
        val carried = budgetCarry(data, budget, today, weekStart)
        val planned = budget.plannedMinor + carried
        val percent = if (planned <= 0) null else (spent * 100 / planned).coerceAtMost(9_999).toInt()
        return BudgetProgress(budget, start, end, spent, planned - spent, percent, carried)
    }

    /**
     * What a rollover budget carries into this period: what the period before planned but did not spend, less what it
     * overspent. One period only, so a long quiet month does not make a budget grow without bound (MINT.md 7.3).
     * A budget without rollover carries nothing.
     */
    fun budgetCarry(data: MintData, budget: Budget, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Long {
        if (!budget.rollover) return 0
        val anchor = periodOf(budget.period, today, weekStart).first.minus(DatePeriod(days = 1))
        val (start, end) = periodOf(budget.period, anchor, weekStart)
        // A budget made partway through the period before had no full period to carry from.
        if (budget.createdDay != null && budget.createdDay > start) return 0
        val spent = spent(data, start, end, budget.currency, budget.categoryId).coerceAtLeast(0)
        return budget.plannedMinor - spent
    }

    /**
     * Where the period is heading at this pace: the spending so far spread over the whole period. Null on its first day,
     * when there is no pace yet.
     */
    fun pace(progress: BudgetProgress, today: LocalDate): Long? {
        val elapsed = today.toEpochDays() - progress.start.toEpochDays() + 1
        val total = progress.endExclusive.toEpochDays() - progress.start.toEpochDays()
        if (elapsed <= 0 || total <= 0) return null
        return progress.spentMinor * total / elapsed
    }

    /** What the player usually spends a month in a category, for a suggested starting budget (MINT.md 7.1, P3 list). */
    fun usualMonthlySpending(data: MintData, categoryId: String?, currency: String, today: LocalDate, months: Int = 3): Long {
        var total = 0L
        var anchor = today
        repeat(months.coerceIn(1, 12)) {
            val (start, end) = periodOf(BudgetPeriod.MONTHLY, anchor)
            total += spent(data, start, end, currency, categoryId).coerceAtLeast(0)
            anchor = start.minus(DatePeriod(days = 1))
        }
        return total / months
    }

    fun savingsProgress(data: MintData, target: SavingsTarget): SavingsProgress {
        val accounts = data.accounts.associateBy { it.id }
        val saved = target.accountIds.distinct().fold(0L) { total, id ->
            val account = accounts[id]
            val balance = if (account != null && account.currency == target.currency && !account.type.liability) balance(data, account).postedMinor.coerceAtLeast(0) else 0L
            total + minOf(balance, Money.MAX_MINOR - total)
        }
        return SavingsProgress(target, saved, (target.targetMinor - saved).coerceAtLeast(0), (saved * 100 / target.targetMinor).coerceAtMost(9_999).toInt())
    }

    private fun daysInMonth(year: Int, month: Int): Int = app.pokeplan.kit.Calendars.daysInMonth(year, month)

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
            Frequency.SEMI_ANNUAL -> {
                val total = first.year * 12 + (first.monthNumber - 1) + 6 * index
                val year = total.floorDiv(12)
                val month = total.mod(12) + 1
                LocalDate(year, month, minOf(first.dayOfMonth, daysInMonth(year, month)))
            }
            Frequency.YEARLY -> {
                val year = first.year + index
                LocalDate(year, first.monthNumber, minOf(first.dayOfMonth, daysInMonth(year, first.monthNumber)))
            }
            Frequency.SEMI_MONTHLY -> {
                val days = semiDays(bill)
                fun at(k: Int): LocalDate {
                    val total = first.year * 12 + (first.monthNumber - 1) + k / days.size
                    val year = total.floorDiv(12)
                    val month = total.mod(12) + 1
                    return LocalDate(year, month, minOf(days[k % days.size], daysInMonth(year, month)))
                }
                val skip = (0 until days.size).count { at(it) < first }
                at(index + skip)
            }
        }
    }

    fun semiDays(bill: Bill): List<Int> = bill.days.filter { it in 1..31 }.distinct().sorted().take(4).ifEmpty { listOf(15, 30) }

    fun payDay(data: MintData, bill: Bill, due: LocalDate): LocalDate = if (bill.workday) Payday.onOrBefore(due, data.settings) else due

    fun occurrences(data: MintData, bill: Bill, from: LocalDate, to: LocalDate, today: LocalDate): List<Occurrence> {
        if (bill.status != PlanStatus.ACTIVE || to < from) return emptyList()
        val out = mutableListOf<Occurrence>()
        // A bill can be years old; walking from its first occurrence made every call O(days since then). Start at the
        // arithmetic index for the window, one step early to be safe around month-end clamping and workday shifts.
        val safeFrom = from.minus(7, DateTimeUnit.DAY)
        val first = bill.firstDue
        val months = (safeFrom.year - first.year) * 12 + (safeFrom.monthNumber - first.monthNumber)
        val start = when (bill.frequency) {
            Frequency.ONCE -> 0
            Frequency.WEEKLY -> (first.daysUntil(safeFrom) / 7 - 1).coerceAtLeast(0)
            Frequency.MONTHLY -> (months - 1).coerceAtLeast(0)
            Frequency.SEMI_ANNUAL -> (months / 6 - 1).coerceAtLeast(0)
            Frequency.YEARLY -> (safeFrom.year - first.year - 1).coerceAtLeast(0)
            Frequency.SEMI_MONTHLY -> (months * semiDays(bill).size - semiDays(bill).size).coerceAtLeast(0)
        }
        val payments = data.billPayments.filter { it.billId == bill.id }.associateBy { it.dueDay }
        var index = start
        while (index <= MAX_OCCURRENCES) {
            val due = dueDay(bill, index)
            if (due > to.plus(7, DateTimeUnit.DAY)) break
            val pay = payDay(data, bill, due)
            if ((due >= from && due <= to) || (pay >= from && pay <= to)) out += occurrence(data, bill, due, today, payments[due])
            if (bill.frequency == Frequency.ONCE) break
            index++
        }
        return out
    }

    fun occurrence(data: MintData, bill: Bill, due: LocalDate, today: LocalDate): Occurrence =
        occurrence(data, bill, due, today, data.billPayments.firstOrNull { it.billId == bill.id && it.dueDay == due })

    private fun occurrence(data: MintData, bill: Bill, due: LocalDate, today: LocalDate, payment: BillPayment?): Occurrence {
        val pay = payDay(data, bill, due)
        val state = when (payment?.status) {
            SettleStatus.PAID -> OccurrenceState.PAID
            SettleStatus.PARTIAL -> OccurrenceState.PARTIAL
            SettleStatus.SKIPPED -> OccurrenceState.SKIPPED
            null -> when { pay < today -> OccurrenceState.OVERDUE; pay == today -> OccurrenceState.DUE; else -> OccurrenceState.UPCOMING }
        }
        return Occurrence(bill, due, state, payment, pay)
    }

    const val OVERDUE_DAYS = 60
    private const val MAX_OCCURRENCES = 5_000

    fun upcoming(data: MintData, today: LocalDate, withinDays: Int = 30): List<Occurrence> =
        data.bills.flatMap { occurrences(data, it, today.minus(DatePeriod(days = OVERDUE_DAYS)), today.plus(withinDays, DateTimeUnit.DAY), today) }
            .filter { it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }
            .sortedBy { it.payDay }

    fun settled(data: MintData, today: LocalDate): List<Occurrence> {
        val from = today.minus(DatePeriod(days = OVERDUE_DAYS))
        return data.billPayments.filter { it.dueDay >= from && it.status != SettleStatus.PARTIAL }
            .mapNotNull { p -> data.bills.firstOrNull { it.id == p.billId }?.let { occurrence(data, it, p.dueDay, today) } }
            .sortedByDescending { it.payDay }
    }

    fun reconcileDifference(statementMinor: Long, bookMinor: Long): Long = statementMinor - bookMinor

    const val RECONCILES_PAID_PER_DAY = 2

    const val RECORDS_PAID_PER_DAY = Int.MAX_VALUE

    fun logPays(data: MintData, tx: Transaction, today: LocalDate): Boolean =
        data.settings.progression && (tx.type == TxType.EXPENSE || tx.type == TxType.TRANSFER) && tx.status == TxStatus.POSTED && tx.source == TxSource.MANUAL &&
            tx.importBatch == null && tx.day == today

    const val REVIEWS_PAID_PER_WEEK = 1

    fun hasSubstance(data: MintData): Boolean =
        data.accounts.any { it.status == AccountStatus.ACTIVE } && data.transactions.any { it.status == TxStatus.POSTED }

    fun reconcilePays(data: MintData, accountId: String, day: LocalDate): Boolean {
        if (!data.settings.progression) return false
        if (data.transactions.none { it.status == TxStatus.POSTED && (it.accountId == accountId || it.toAccountId == accountId) && it.source == TxSource.MANUAL }) return false
        val today = data.reconciliations.filter { it.day == day && it.paid }
        return today.none { it.accountId == accountId } && today.size < RECONCILES_PAID_PER_DAY
    }

    fun reviewPays(data: MintData, day: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Boolean {
        if (!data.settings.progression || !hasSubstance(data)) return false
        val start = weekStartOf(day, weekStart)
        return data.reviews.count { it.paid && weekStartOf(it.day, weekStart) == start } < REVIEWS_PAID_PER_WEEK
    }

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
            t.splits.any { split -> data.categories.firstOrNull { it.id == split.categoryId }?.name?.lowercase()?.contains(q) == true } ||
            data.accounts.firstOrNull { it.id == t.accountId }?.name?.lowercase()?.contains(q) == true ||
            data.accounts.firstOrNull { it.id == t.toAccountId }?.name?.lowercase()?.contains(q) == true ||
            Money.plain(t.amountMinor, t.currency).contains(q.replace(",", "")) ||
            (t.feeMinor > 0 && Money.plain(t.feeMinor, t.currency).contains(q.replace(",", "")))

    fun seedCategories(): List<Category> = listOf(
        "food" to "Groceries", "dining" to "Eating out", "transport" to "Transport", "bills" to "Bills and utilities", "rent" to "Rent and housing",
        "health" to "Health", "shopping" to "Shopping", "school" to "School", "fun" to "Fun", "family" to "Family and gifts", "care" to "Personal care", "other" to "Other",
    ).map { (key, name) -> Category("cat-$key", name, CategoryKind.EXPENSE) } + listOf(
        "salary" to "Salary", "business" to "Business", "gift-in" to "Gifts received", "interest" to "Interest", "other-in" to "Other income",
    ).map { (key, name) -> Category("cat-$key", name, CategoryKind.INCOME) }
}
