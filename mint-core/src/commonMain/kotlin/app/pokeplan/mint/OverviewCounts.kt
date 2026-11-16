package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** How far ahead the Overview, the breakdown and the Mint widgets look for bills and installments that are due (MINT.md 3.2B). */
@Serializable
enum class OverviewWindow(val label: String) {
    DAYS_14("Next 14 days"),
    DAYS_30("Next 30 days"),
    THIS_MONTH("Rest of this month"),
    NEXT_MONTH("Until end of next month");

    /** Days from [today] to the last day of the window, at least 0. */
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

/** How much of a Pay later account (SPayLater and similar) the Overview's net position holds against the player (MINT.md 3.2B). Cards always count in full. */
@Serializable
enum class PayLaterCount(val label: String) {
    WHOLE("Whole balance"),
    DUE("Only what is due"),
    NONE("Not counted");
}

/** The two choices Mint Settings gives the Overview, applied the same way by the screen, the breakdown and the widgets. */
object OverviewCounts {
    /** The days ahead the Overview looks, from the setting. */
    fun days(data: MintData, today: LocalDate): Int = data.settings.overviewWindow.days(today)

    /**
     * What an account's [owedMinor] counts as in net position: all of it, except for a Pay later account, where the setting can keep only the unpaid
     * installments due in the window (never more than is owed), or nothing.
     */
    fun liabilityCounted(data: MintData, account: FinancialAccount, owedMinor: Long, today: LocalDate): Long {
        if (account.type != AccountType.PAY_LATER || owedMinor <= 0L) return owedMinor
        return when (data.settings.overviewPayLater) {
            PayLaterCount.WHOLE -> owedMinor
            PayLaterCount.NONE -> 0L
            PayLaterCount.DUE -> minOf(owedMinor, dueOn(data, account, today, days(data, today)))
        }
    }

    /** The unpaid installments of [account]'s plans that fall due by [today] plus [withinDays] (overdue ones included). */
    fun dueOn(data: MintData, account: FinancialAccount, today: LocalDate, withinDays: Int): Long {
        val until = today.plus(DatePeriod(days = withinDays))
        return Installments.views(data, today).filter { it.plan.accountId == account.id }
            .sumOf { v -> v.installments.filter { it.remainingMinor > 0 && it.dueDay <= until }.sumOf { it.remainingMinor } }
    }
}

/** One thing that is due in the window: a bill's next occurrence or a plan's next unpaid installment. [amountMinor] is null for a bill whose amount varies. */
data class DueItem(val name: String, val day: LocalDate, val overdue: Boolean, val amountMinor: Long?, val installment: Boolean)

/**
 * What the Overview and the Mint widgets show (MINT.md 3.2B), derived from the records and the settings, stored nowhere. [dueMinor] and [dueCount] cover
 * everything due in the window; [next] is the soonest few, one line per bill or plan.
 */
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
    val bills = MintRules.upcoming(data, today, days).filter { it.bill.currency == currency }.distinctBy { it.bill.id }
        .map { DueItem(it.bill.name, it.payDay, it.state == OccurrenceState.OVERDUE, it.remainingMinor, installment = false) }
    val plans = Installments.views(data, today).filter { it.plan.currency == currency }.mapNotNull { v ->
        v.installments.filter { it.remainingMinor > 0 && it.dueDay <= until }.minByOrNull { it.dueDay }
            ?.let { DueItem(v.plan.name, it.dueDay, it.dueDay < today, it.remainingMinor, installment = true) }
    }
    return OverviewSummary(
        currency, available.availableMinor, net, available.billsMinor + available.installmentsMinor, available.billsCount + available.installmentsCount, days,
        (bills + plans).sortedBy { it.day }.take(limit),
    )
}
