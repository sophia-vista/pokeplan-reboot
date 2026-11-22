package app.pokeplan.mint

import kotlinx.datetime.LocalDate

data class BreakdownLine(val accountId: String, val name: String, val group: String, val balanceMinor: Long, val counted: Boolean)

data class CreditLineInfo(val accountId: String, val name: String, val limitMinor: Long, val owedMinor: Long, val leftMinor: Long)

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

fun MintRules.breakdown(data: MintData, currency: String, today: LocalDate, withinDays: Int = OverviewCounts.days(data, today)): OverviewBreakdown {
    val rows = balances(data).filter { it.account.status == AccountStatus.ACTIVE && it.account.currency == currency }
    fun line(b: AccountBalance) = BreakdownLine(
        b.account.id, b.account.name, b.account.group,
        if (b.account.type.liability) OverviewCounts.liabilityCounted(data, b.account, b.postedMinor, today) else b.postedMinor,
        !b.account.hideFromTotal,
    )
    val assets = rows.filter { !it.account.type.liability }.map(::line)
    val liabilities = rows.filter { it.account.type.liability }.map(::line)
    val assetsSum = assets.filter { it.counted }.sumOf { it.balanceMinor }
    val liabilitiesSum = liabilities.filter { it.counted }.sumOf { it.balanceMinor }
    val av = available(data, currency, today, withinDays)
    val credit = rows.filter { Installments.allowedFor(it.account.type) && it.account.limitMinor > 0 }
        .map { CreditLineInfo(it.account.id, it.account.name, it.account.limitMinor, it.postedMinor, leftToSpend(it.account, it.postedMinor) ?: 0L) }
    return OverviewBreakdown(currency, assets, liabilities, assetsSum, liabilitiesSum, assetsSum - liabilitiesSum, av.liquidMinor, av.billsMinor, av.billsCount, av.installmentsMinor, av.installmentsCount, av.availableMinor, credit)
}
