package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A calendar month a credit account's installments are billed in (MINT.md 5.6C). */
data class BillMonth(val year: Int, val month: Int) : Comparable<BillMonth> {
    override fun compareTo(other: BillMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
    fun contains(day: LocalDate): Boolean = day.year == year && day.monthNumber == month
    val first: LocalDate get() = LocalDate(year, month, 1)
    companion object {
        fun of(day: LocalDate) = BillMonth(day.year, day.monthNumber)
    }
}

/** One purchase's share of a monthly bill: installment [no] of [count] of the plan [planName]. */
data class BillLine(
    val planId: String,
    val planName: String,
    val no: Int,
    val count: Int,
    val dueDay: LocalDate,
    val dueMinor: Long,
    val paidMinor: Long,
    val state: InstallmentState,
) {
    val remainingMinor: Long get() = (dueMinor - paidMinor).coerceAtLeast(0)
}

/**
 * The one payable of a credit account for a month (MINT.md 5.6C): every purchase's installment that falls due in [month], added up. Derived from the plans, never
 * stored, so a purchase is recorded once and the bill follows. [dueDay] is the earliest due day of the lines still owed.
 */
data class MonthlyBill(val accountId: String, val currency: String, val month: BillMonth, val lines: List<BillLine>) {
    val dueDay: LocalDate get() = lines.filter { it.remainingMinor > 0 }.ifEmpty { lines }.minOf { it.dueDay }
    val totalMinor: Long get() = lines.sumOf { it.dueMinor }
    val paidMinor: Long get() = lines.sumOf { it.paidMinor }
    val remainingMinor: Long get() = lines.sumOf { it.remainingMinor }
    val settled: Boolean get() = remainingMinor == 0L

    /** Overdue when any line is, due when any is due today, partly paid when something was paid, else upcoming; paid once nothing is left. */
    val state: InstallmentState
        get() = when {
            lines.all { it.state == InstallmentState.CANCELLED } -> InstallmentState.CANCELLED
            settled -> InstallmentState.PAID
            lines.any { it.state == InstallmentState.OVERDUE } -> InstallmentState.OVERDUE
            lines.any { it.state == InstallmentState.DUE } -> InstallmentState.DUE
            paidMinor > 0 -> InstallmentState.PARTIAL
            else -> InstallmentState.UPCOMING
        }
}

object MonthlyBills {
    /**
     * The bills of [accountId] that still have something owed, earliest month first, overdue ones included. Cancelled and paid installments are left out, so a month
     * whose purchases are all paid has no bill.
     */
    fun open(data: MintData, accountId: String, today: LocalDate): List<MonthlyBill> =
        of(data, accountId, today).filter { !it.settled }

    /** Every bill of [accountId], settled ones too, earliest month first (the history of what was billed). */
    fun of(data: MintData, accountId: String, today: LocalDate): List<MonthlyBill> {
        val views = Installments.views(data, today).filter { it.plan.accountId == accountId }
        val lines = views.flatMap { v ->
            v.installments.filter { it.state != InstallmentState.CANCELLED }.map { i ->
                BillLine(v.plan.id, v.plan.name, i.no, v.installments.size, i.dueDay, i.dueMinor, i.paidMinor, i.state)
            }
        }
        val currency = data.accounts.firstOrNull { it.id == accountId }?.currency ?: views.firstOrNull()?.plan?.currency ?: return emptyList()
        return lines.groupBy { BillMonth.of(it.dueDay) }.toSortedMap()
            .map { (month, list) -> MonthlyBill(accountId, currency, month, list.sortedWith(compareBy({ it.dueDay }, { it.planName }, { it.no }))) }
    }

    /**
     * The first installment's due day for a purchase made on [purchaseDay] (MINT.md 5.6C), so the player does not type it. A monthly plan on an account with a due day
     * is billed on the first statement on or after the purchase (when the account has a statement day) and falls due on the next due day after it; otherwise a
     * month after the purchase, or one step for a weekly plan. The player can still change it.
     */
    fun firstDue(account: FinancialAccount, purchaseDay: LocalDate, every: PlanEvery): LocalDate {
        if (every != PlanEvery.MONTH || account.dueDay !in 1..31) return Installments.dueOn(purchaseDay, every, 1)
        val statement = if (account.statementDay in 1..31) dayOnOrAfter(account.statementDay, purchaseDay) else purchaseDay
        return dayOnOrAfter(account.dueDay, statement.plus(DatePeriod(days = 1)))
    }

    private fun daysIn(year: Int, month: Int): Int = LocalDate(year, month, 1).plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1)).dayOfMonth

    /** [day] of the month of [anchor], or that month's last day when it lacks [day]. */
    private fun inMonthOf(anchor: LocalDate, day: Int): LocalDate = LocalDate(anchor.year, anchor.monthNumber, minOf(day, daysIn(anchor.year, anchor.monthNumber)))

    /** The first date on or after [from] that falls on [day] of a month (a short month's last day when it lacks [day]). */
    fun dayOnOrAfter(day: Int, from: LocalDate): LocalDate {
        val here = inMonthOf(from, day)
        return if (here >= from) here else inMonthOf(from.plus(DatePeriod(months = 1)), day)
    }

    /** [day] moved to the account's due day in its own month (a short month's last day); [day] itself when the account has no due day. */
    fun onDueDay(account: FinancialAccount, day: LocalDate): LocalDate = if (account.dueDay !in 1..31) day else inMonthOf(day, account.dueDay)

    /**
     * Splits a repayment of [amountMinor] across the account's unpaid installments one by one, in due order and then in the order the plans were made, so it lands on
     * the **bill** and not on whichever purchase happens to sort first (MINT.md 5.6C). With [firstBill] that month's installments come first and the rest follow in
     * due order (paying a later bill early still settles it, and any extra goes to the earliest bills). Each row names its installment, so a plan is never paid
     * out of its own order against the player's choice of bill. What is left over beyond every scheduled balance is an ordinary repayment.
     */
    fun allocate(data: MintData, accountId: String, amountMinor: Long, today: LocalDate, firstBill: BillMonth? = null): List<PlanPaymentAllocation> {
        require(amountMinor >= 0)
        class Slot(val planId: String, val created: Long, val due: LocalDate, val no: Int, val remaining: Long)
        val slots = Installments.views(data, today).filter { it.plan.accountId == accountId }.flatMap { v ->
            v.installments.filter { it.remainingMinor > 0 && it.state != InstallmentState.CANCELLED }.map { Slot(v.plan.id, v.plan.createdMillis, it.dueDay, it.no, it.remainingMinor) }
        }.sortedWith(compareBy<Slot>({ if (firstBill != null && firstBill.contains(it.due)) 0 else 1 }, { it.due }, { it.created }, { it.planId }, { it.no }))
        var left = amountMinor
        val rows = mutableListOf<PlanPaymentAllocation>()
        for (s in slots) {
            if (left <= 0) break
            val applied = minOf(left, s.remaining)
            left -= applied
            rows += PlanPaymentAllocation(s.planId, applied, s.no)
        }
        return rows
    }
}
