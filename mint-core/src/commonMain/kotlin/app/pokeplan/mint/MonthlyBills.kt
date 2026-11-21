package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class BillMonth(val year: Int, val month: Int) : Comparable<BillMonth> {
    override fun compareTo(other: BillMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
    fun contains(day: LocalDate): Boolean = day.year == year && day.monthNumber == month
    val first: LocalDate get() = LocalDate(year, month, 1)
    companion object {
        fun of(day: LocalDate) = BillMonth(day.year, day.monthNumber)
    }
}

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

data class MonthlyBill(val accountId: String, val currency: String, val month: BillMonth, val lines: List<BillLine>) {
    val dueDay: LocalDate get() = lines.filter { it.remainingMinor > 0 }.ifEmpty { lines }.minOf { it.dueDay }
    val totalMinor: Long get() = lines.sumOf { it.dueMinor }
    val paidMinor: Long get() = lines.sumOf { it.paidMinor }
    val remainingMinor: Long get() = lines.sumOf { it.remainingMinor }
    val settled: Boolean get() = remainingMinor == 0L

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
    fun open(data: MintData, accountId: String, today: LocalDate): List<MonthlyBill> =
        of(data, accountId, today).filter { !it.settled }

    fun of(data: MintData, accountId: String, today: LocalDate): List<MonthlyBill> {
        val views = Installments.views(data, today).filter { it.plan.accountId == accountId }
        val lines = views.flatMap { v ->
            v.installments.filter { it.state != InstallmentState.CANCELLED }.map { i ->
                BillLine(v.plan.id, v.plan.name, i.no, v.installments.size, i.dueDay, i.dueMinor, i.paidMinor, i.state)
            }
        }
        val currency = data.accounts.firstOrNull { it.id == accountId }?.currency ?: views.firstOrNull()?.plan?.currency ?: return emptyList()
        return lines.groupBy { BillMonth.of(it.dueDay) }.entries.sortedBy { it.key }
            .map { (month, list) -> MonthlyBill(accountId, currency, month, list.sortedWith(compareBy({ it.dueDay }, { it.planName }, { it.no }))) }
    }

    fun firstDue(account: FinancialAccount, purchaseDay: LocalDate, every: PlanEvery): LocalDate {
        if (every != PlanEvery.MONTH || account.dueDay !in 1..31) return Installments.dueOn(purchaseDay, every, 1)
        val statement = if (account.statementDay in 1..31) dayOnOrAfter(account.statementDay, purchaseDay) else purchaseDay
        return dayOnOrAfter(account.dueDay, statement.plus(DatePeriod(days = 1)))
    }

    private fun daysIn(year: Int, month: Int): Int = LocalDate(year, month, 1).plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1)).dayOfMonth

    private fun inMonthOf(anchor: LocalDate, day: Int): LocalDate = LocalDate(anchor.year, anchor.monthNumber, minOf(day, daysIn(anchor.year, anchor.monthNumber)))

    fun dayOnOrAfter(day: Int, from: LocalDate): LocalDate {
        val here = inMonthOf(from, day)
        return if (here >= from) here else inMonthOf(from.plus(DatePeriod(months = 1)), day)
    }

    fun onDueDay(account: FinancialAccount, day: LocalDate): LocalDate = if (account.dueDay !in 1..31) day else inMonthOf(day, account.dueDay)

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
