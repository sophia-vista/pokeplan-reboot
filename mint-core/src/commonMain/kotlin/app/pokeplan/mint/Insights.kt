package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * What the records say when they are read together (MINT.md 9, 7): what looks like a subscription, what changed since
 * the period before, where the money usually goes, and what the next month is likely to need. It is all derived, said in
 * plain words, and never a prediction that is presented as a fact.
 */
object Insights {
    /** A payee charged again and again, about the same amount, about once a month: probably a subscription. */
    data class Recurring(val payee: String, val count: Int, val averageMinor: Long, val lastDay: LocalDate, val everyDays: Int)

    /** One category this period against the one before. */
    data class Change(val categoryId: String?, val nowMinor: Long, val beforeMinor: Long) {
        val differenceMinor: Long get() = nowMinor - beforeMinor
        val percent: Int? get() = if (beforeMinor <= 0) null else (differenceMinor * 100 / beforeMinor).toInt()
    }

    data class Merchant(val payee: String, val count: Int, val totalMinor: Long)

    /** A category's spending this month against last month. */
    data class Trend(val categoryId: String?, val nowMinor: Long, val beforeMinor: Long) {
        val differenceMinor: Long get() = nowMinor - beforeMinor
    }

    /** What the next [days] days are likely to ask for, and what is expected in. */
    data class Forecast(val days: Int, val comingOutMinor: Long, val comingInMinor: Long, val usualSpendingMinor: Long) {
        val netMinor: Long get() = comingInMinor - comingOutMinor - usualSpendingMinor
    }

    private fun normalized(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    /**
     * Payees that look like a subscription or a regular bill: at least three posted expenses, amounts within a quarter of
     * the usual one, spread over at least two months and about a month apart on average. It is a hint, not a verdict.
     */
    fun recurring(data: MintData, today: LocalDate, months: Int = 6): List<Recurring> {
        val from = today.minus(DatePeriod(months = months.coerceIn(1, 24)))
        return data.transactions
            .filter { it.type == TxType.EXPENSE && it.status == TxStatus.POSTED && it.payee.isNotBlank() && it.day >= from }
            .groupBy { normalized(it.payee) }
            .mapNotNull { (payee, rows) ->
                if (rows.size < 3) return@mapNotNull null
                val sorted = rows.sortedBy { it.day }
                val amounts = sorted.map { it.amountMinor }
                val usual = median(amounts)
                if (usual <= 0 || amounts.any { it > usual + usual / 4 || it < usual - usual / 4 }) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> b.day.toEpochDays() - a.day.toEpochDays() }
                val averageGap = gaps.sum() / gaps.size
                if (averageGap !in 20L..40L) return@mapNotNull null
                Recurring(sorted.first().payee.trim(), sorted.size, usual, sorted.last().day, averageGap.toInt())
            }
            .sortedBy { it.lastDay }
    }

    /** Spending by category this period against the one before, biggest change first. */
    fun changes(data: MintData, period: BudgetPeriod, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY, currency: String = data.settings.currency): List<Change> {
        val (from, end) = MintRules.periodOf(period, today, weekStart)
        val (beforeFrom, beforeEnd) = MintRules.periodOf(period, from.minus(DatePeriod(days = 1)), weekStart)
        val now = MintRules.spendingByCategory(data, from, end, currency).toMap()
        val before = MintRules.spendingByCategory(data, beforeFrom, beforeEnd, currency).toMap()
        return (now.keys + before.keys).map { id -> Change(id, now[id] ?: 0L, before[id] ?: 0L) }
            .filter { it.nowMinor != 0L || it.beforeMinor != 0L }
            .sortedByDescending { kotlin.math.abs(it.differenceMinor) }
    }

    /** Where the money goes most often and in the largest amounts, in the last [months] months. */
    fun merchants(data: MintData, today: LocalDate, months: Int = 3): List<Merchant> {
        val from = today.minus(DatePeriod(months = months.coerceIn(1, 24)))
        return data.transactions
            .filter { it.type == TxType.EXPENSE && it.status == TxStatus.POSTED && it.payee.isNotBlank() && it.day >= from }
            .groupBy { normalized(it.payee) }
            .map { (_, rows) -> Merchant(rows.first().payee.trim(), rows.size, rows.sumOf { it.amountMinor }) }
            .sortedByDescending { it.totalMinor }
            .take(5)
    }

    /** Category spending by month for the last [months] months, this month's biggest first. */
    fun trends(data: MintData, today: LocalDate, months: Int = 3, currency: String = data.settings.currency): List<Trend> {
        val (from, end) = MintRules.periodOf(BudgetPeriod.MONTHLY, today)
        val (beforeFrom, beforeEnd) = MintRules.periodOf(BudgetPeriod.MONTHLY, from.minus(DatePeriod(days = 1)))
        val now = MintRules.spendingByCategory(data, from, end, currency).toMap()
        val before = MintRules.spendingByCategory(data, beforeFrom, beforeEnd, currency).toMap()
        return now.keys.map { id -> Trend(id, now[id] ?: 0L, before[id] ?: 0L) }.sortedByDescending { it.nowMinor }.take(6)
    }

    /**
     * The next [days] days: what the known obligations ask for, what known income brings, and what ordinary spending at
     * the recent pace would add. Ordinary spending is the daily average of the last 90 days, so it moves slowly.
     */
    fun forecast(data: MintData, today: LocalDate, days: Int = 30, currency: String = data.settings.currency): Forecast {
        val window = days.coerceIn(1, 365)
        val bills = MintRules.upcoming(data, today, window).filter { !it.bill.income }
        val comingOut = bills.sumOf { it.remainingMinor ?: it.bill.expectedMinor ?: 0L } +
            Installments.dueWithin(data, currency, today, window).first +
            CardCycles.dueWithin(data, today, window).sumOf { (_, c) -> c.remainingMinor }
        val comingIn = MintRules.upcoming(data, today, window).filter { it.bill.income }
            .sumOf { it.remainingMinor ?: it.bill.expectedMinor ?: 0L }
        val usual = usualDailySpending(data, today, currency) * window
        return Forecast(window, comingOut, comingIn, usual)
    }

    /** The average spent each day over the last 90 days, transfers and adjustments left out. */
    fun usualDailySpending(data: MintData, today: LocalDate, currency: String = data.settings.currency): Long {
        val from = today.minus(DatePeriod(days = 90))
        val total = data.transactions.filter {
            it.status == TxStatus.POSTED && it.currency == currency && it.day > from && it.day <= today &&
                it.type in setOf(TxType.EXPENSE, TxType.FEE, TxType.REFUND) && it.iou.not()
        }.sumOf { t -> when (t.type) { TxType.REFUND -> -t.amountMinor; else -> t.amountMinor } }
        return (total.coerceAtLeast(0) / 90)
    }

    /** The available-money figure, spelled out as the calculation it is (MINT.md 3.2, 4.5). */
    fun availableLines(data: MintData, currency: String, today: LocalDate, days: Int): List<String> {
        val available = MintRules.available(data, currency, today, days)
        return buildList {
            add("Money in the wallets and banks: " + Money.format(available.liquidMinor, currency))
            if (available.billsCount > 0) add("Less the bills due in $days days: " + Money.format(available.billsMinor, currency) + " (" + available.billsCount + ")")
            if (available.installmentsCount > 0) add("Less installments due: " + Money.format(available.installmentsMinor, currency) + " (" + available.installmentsCount + ")")
            add("Available: " + Money.format(available.availableMinor, currency))
            add("It counts what is posted, not a bank balance, and it never promises the money is there.")
        }
    }
}
