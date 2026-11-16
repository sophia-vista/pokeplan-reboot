package app.pokeplan.mint

import kotlinx.datetime.LocalDate

/** One account in the breakdown: its balance (what is owed for a liability) and whether it counts in the totals. */
data class BreakdownLine(val accountId: String, val name: String, val group: String, val balanceMinor: Long, val counted: Boolean)

/** A credit line shown for information: its limit, what is owed and what is left (limits are in no total). */
data class CreditLineInfo(val accountId: String, val name: String, val limitMinor: Long, val owedMinor: Long, val leftMinor: Long)

/**
 * The parts of the Overview figures for one currency (MINT.md 3.2A): every number in it is derived from the records, so net position is exactly assets less liabilities
 * of the counted accounts and available money is exactly liquid less bills and installments due. Nothing here is stored.
 */
data class OverviewBreakdown(
    val currency: String,
    val assets: List<BreakdownLine>,
    val liabilities: List<BreakdownLine>,
    val assetsMinor: Long,
    val liabilitiesMinor: Long,
    val netMinor: Long,
    val liquidMinor: Long,
    val billsMinor: Long,
    val billsCount: Int,
    val installmentsMinor: Long,
    val installmentsCount: Int,
    val availableMinor: Long,
    val creditLines: List<CreditLineInfo>,
)

fun MintRules.breakdown(data: MintData, currency: String, today: LocalDate, withinDays: Int = 14): OverviewBreakdown {
    val rows = balances(data).filter { it.account.status == AccountStatus.ACTIVE && it.account.currency == currency }
    fun line(b: AccountBalance) = BreakdownLine(b.account.id, b.account.name, b.account.group, b.postedMinor, !b.account.hideFromTotal)
    val assets = rows.filter { !it.account.type.liability }.map(::line)
    val liabilities = rows.filter { it.account.type.liability }.map(::line)
    val assetsSum = assets.filter { it.counted }.sumOf { it.balanceMinor }
    val liabilitiesSum = liabilities.filter { it.counted }.sumOf { it.balanceMinor }
    val av = available(data, currency, today, withinDays)
    val credit = rows.filter { Installments.allowedFor(it.account.type) && it.account.limitMinor > 0 }
        .map { CreditLineInfo(it.account.id, it.account.name, it.account.limitMinor, it.postedMinor, leftToSpend(it.account, it.postedMinor) ?: 0L) }
    return OverviewBreakdown(currency, assets, liabilities, assetsSum, liabilitiesSum, assetsSum - liabilitiesSum, av.liquidMinor, av.billsMinor, av.billsCount, av.installmentsMinor, av.installmentsCount, av.availableMinor, credit)
}
