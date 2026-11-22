package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
enum class OverviewWindow(val label: String) {
    DAYS_14("Next 14 days"),
    DAYS_30("Next 30 days"),
    THIS_MONTH("Rest of this month"),
    NEXT_MONTH("Until end of next month");

    fun days(today: LocalDate): Int {
        val monthsAhead = when (this) {
            DAYS_14 -> return 14
            DAYS_30 -> return 30
            THIS_MONTH -> 0
            NEXT_MONTH -> 1
        }
        val firstOfMonth = LocalDate(today.year, today.month, 1)
        val lastDay = firstOfMonth.plus(DatePeriod(months = monthsAhead + 1)).minus(DatePeriod(days = 1))
        return (lastDay.toEpochDays() - today.toEpochDays()).toInt().coerceAtLeast(0)
    }
}

@Serializable
enum class PayLaterCount(val label: String) {
    WHOLE("Whole balance"),
    DUE("Only what is due"),
    NONE("Not counted");
}

object OverviewCounts {
    fun days(data: MintData, today: LocalDate): Int = data.settings.overviewWindow.days(today)

    fun liabilityCounted(data: MintData, account: FinancialAccount, owedMinor: Long, today: LocalDate): Long {
        if (account.type != AccountType.PAY_LATER || owedMinor <= 0L) return owedMinor
        return when (data.settings.overviewPayLater) {
            PayLaterCount.WHOLE -> owedMinor
            PayLaterCount.NONE -> 0L
            PayLaterCount.DUE -> minOf(owedMinor, dueOn(data, account, today, days(data, today)))
        }
    }

    fun dueOn(data: MintData, account: FinancialAccount, today: LocalDate, withinDays: Int): Long {
        val until = today.plus(DatePeriod(days = withinDays))
        return Installments.views(data, today).filter { it.plan.accountId == account.id }
            .sumOf { v -> v.installments.filter { it.remainingMinor > 0 && it.dueDay <= until }.sumOf { it.remainingMinor } }
    }
}

data class DueItem(val name: String, val day: LocalDate, val overdue: Boolean, val amountMinor: Long?, val installment: Boolean)

data class OverviewSummary(
    val currency: String,
    val availableMinor: Long,
    val netMinor: Long,
    val dueMinor: Long,
    val dueCount: Int,
    val windowDays: Int,
    val next: List<DueItem>,
)

fun OverviewCounts.summary(data: MintData, today: LocalDate, limit: Int = 5): OverviewSummary {
    val currency = data.settings.currency
    val days = days(data, today)
    val available = MintRules.available(data, currency, today, days)
    val net = MintRules.netPosition(data, today)[currency] ?: 0L
    val until = today.plus(DatePeriod(days = days))
    val bills = MintRules.upcoming(data, today, days).filter { !it.bill.income && it.bill.currency == currency }.distinctBy { it.bill.id }
        .map { DueItem(it.bill.name, it.payDay, it.state == OccurrenceState.OVERDUE, it.remainingMinor, installment = false) }
    val plans = data.accounts.filter { Installments.allowedFor(it.type) && it.currency == currency }.flatMap { account ->
        MonthlyBills.open(data, account.id, today).mapNotNull { bill ->
            val inWindow = bill.lines.filter { it.remainingMinor > 0 && it.dueDay <= until }
            if (inWindow.isEmpty()) null else DueItem(account.name, inWindow.minOf { it.dueDay }, inWindow.any { it.dueDay < today }, inWindow.sumOf { it.remainingMinor }, installment = true)
        }
    }
    return OverviewSummary(
        currency, available.availableMinor, net, available.billsMinor + available.installmentsMinor, available.billsCount + available.installmentsCount, days,
        (bills + plans).sortedBy { it.day }.take(limit),
    )
}
