package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** How often installments fall due (MINT.md 5.6A). A monthly day the month lacks falls on its last day. */
@Serializable
enum class PlanEvery(val label: String) { WEEK("Every week"), TWO_WEEKS("Every 2 weeks"), MONTH("Every month") }

/** What a `FEE` record is: a plain fee, interest, or a late fee (MINT.md 5.6A). Only a `FEE` record has one. */
@Serializable
enum class FeeKind(val label: String) { FEE("Fee"), INTEREST("Interest"), LATE("Late fee") }

/** One scheduled installment: its due day and what it asks (principal and fee). What was paid is derived from the plan's repayments, never stored. */
@Serializable
data class Installment(val no: Int, val dueDay: LocalDate, val principalMinor: Long, val feeMinor: Long = 0)

/**
 * A buy-now-pay-later or card installment purchase (MINT.md 5.6A). [purchaseTxId] is the one `EXPENSE` at the full [totalMinor]; the plan holds only the generated
 * [installments]. Repayments, fees, late fees and refunds are ordinary records linked by `Transaction.planId`, so nothing here can drift from the ledger.
 */
@Serializable
data class InstallmentPlan(
    val id: String,
    val accountId: String,
    val name: String,
    val currency: String,
    val purchaseTxId: String,
    val totalMinor: Long,
    val startDay: LocalDate,
    val every: PlanEvery,
    /** Whether the installments' fee is called a fee or interest (the late fees are always [FeeKind.LATE]). */
    val feeKind: FeeKind = FeeKind.FEE,
    val installments: List<Installment>,
    val createdMillis: Long = 0,
)

/** How the fee of each installment is worked out when a schedule is generated. */
sealed interface FeeRule {
    data object None : FeeRule

    /** The same fee on every installment. */
    data class PerInstallment(val minor: Long) : FeeRule

    /** One total spread over the installments, the leftover minor units one each to the first. */
    data class Total(val minor: Long) : FeeRule

    /** [basisPoints] (1% is 100) of the principal still unpaid before each installment, rounded half up: interest that shrinks as the plan is paid. */
    data class RatePerPeriod(val basisPoints: Int) : FeeRule
}

enum class InstallmentState(val label: String) { UPCOMING("Upcoming"), DUE("Due today"), OVERDUE("Overdue"), PARTIAL("Partly paid"), PAID("Paid"), CANCELLED("Cancelled") }

/** One installment as it stands today: what it asks, what has been applied to it, what is left, and its state. */
data class InstallmentView(
    val no: Int,
    val dueDay: LocalDate,
    val principalMinor: Long,
    val feeMinor: Long,
    val lateMinor: Long,
    val paidMinor: Long,
    val state: InstallmentState,
    /** Whether this installment's fee has been recorded as a `FEE` record (so it is part of the outstanding). */
    val feeRecorded: Boolean,
) {
    val dueMinor: Long get() = principalMinor + feeMinor + lateMinor
    val remainingMinor: Long get() = (dueMinor - paidMinor).coerceAtLeast(0)
}

/** A plan as it stands today (derived from the ledger). [overpaidMinor] is what repayments linked to the plan exceed its total. */
data class PlanView(val plan: InstallmentPlan, val installments: List<InstallmentView>, val refundedMinor: Long, val overpaidMinor: Long) {
    val remainingMinor: Long get() = installments.sumOf { it.remainingMinor }
    val paidMinor: Long get() = installments.sumOf { it.paidMinor }
    val finished: Boolean get() = installments.all { it.state == InstallmentState.PAID || it.state == InstallmentState.CANCELLED }
    val next: InstallmentView? get() = installments.firstOrNull { it.remainingMinor > 0 }
}

/** What a credit account did over a period (MINT.md 5.6A): kept apart so the limit, the outstanding and the payments are never conflated. */
data class CreditActivity(
    val purchasesMinor: Long,
    val feesMinor: Long,
    val interestMinor: Long,
    val lateFeesMinor: Long,
    val refundsMinor: Long,
    val repaymentsMinor: Long,
)

/** Cash that really moved through the liquid accounts over a period. A repayment to a credit account is [outflowMinor]; a purchase on credit is not cash flow. */
data class CashFlow(val inflowMinor: Long, val outflowMinor: Long) {
    val netMinor: Long get() = inflowMinor - outflowMinor
}

object Installments {
    const val MAX_COUNT = 36

    /** The account types an installment plan may belong to. */
    fun allowedFor(type: AccountType): Boolean = type == AccountType.CREDIT_CARD || type == AccountType.PAY_LATER

    /** [first] plus [index] steps of [every]; a monthly step is counted from the first day, so 31 January, 28 February, 31 March. */
    fun dueOn(first: LocalDate, every: PlanEvery, index: Int): LocalDate = when (every) {
        PlanEvery.WEEK -> first.plus(DatePeriod(days = 7 * index))
        PlanEvery.TWO_WEEKS -> first.plus(DatePeriod(days = 14 * index))
        PlanEvery.MONTH -> first.plus(DatePeriod(months = index))
    }

    /** Splits [total] over [count] parts that add up exactly; the leftover minor units go one each to the first parts. */
    fun split(total: Long, count: Int): List<Long> {
        val base = total / count
        val extra = (total % count).toInt()
        return List(count) { base + if (it < extra) 1 else 0 }
    }

    /** The schedule for a purchase of [totalMinor] in [count] installments (MINT.md 5.6A). Principal adds up to the price exactly. */
    fun generate(totalMinor: Long, count: Int, every: PlanEvery, firstDue: LocalDate, fee: FeeRule): List<Installment> {
        require(count in 1..MAX_COUNT && totalMinor > 0)
        val principal = split(totalMinor, count)
        var remaining = totalMinor
        val fees = when (fee) {
            FeeRule.None -> List(count) { 0L }
            is FeeRule.PerInstallment -> List(count) { fee.minor }
            is FeeRule.Total -> split(fee.minor, count)
            is FeeRule.RatePerPeriod -> principal.map { p ->
                val f = (remaining * fee.basisPoints + 5_000) / 10_000
                remaining -= p
                f
            }
        }
        return List(count) { Installment(it + 1, dueOn(firstDue, every, it), principal[it], fees[it]) }
    }

    fun checkRequest(type: AccountType, totalMinor: Long, count: Int, fee: FeeRule): Check = when {
        !allowedFor(type) -> Check.Refused("Installments belong to a credit card or a pay later account.")
        MintRules.checkAmount(totalMinor) is Check.Refused -> MintRules.checkAmount(totalMinor)
        count !in 1..MAX_COUNT -> Check.Refused("Choose 1 to $MAX_COUNT installments.")
        fee is FeeRule.PerInstallment && (fee.minor < 0 || fee.minor > Money.MAX_MINOR) -> Check.Refused("That fee looks wrong.")
        fee is FeeRule.Total && (fee.minor < 0 || fee.minor > Money.MAX_MINOR) -> Check.Refused("That fee looks wrong.")
        fee is FeeRule.RatePerPeriod && (fee.basisPoints < 0 || fee.basisPoints > 10_000) -> Check.Refused("Choose a rate from 0% to 100% a period.")
        else -> Check.Ok
    }

    private fun counts(t: Transaction) = t.status == TxStatus.POSTED

    /** Whether the fee of installment [no] has a live `FEE` record (voiding or deleting it lets it be recorded again). */
    fun feeRecorded(data: MintData, plan: InstallmentPlan, no: Int): Boolean =
        data.transactions.any { it.planId == plan.id && it.planNo == no && it.type == TxType.FEE && it.feeKind != FeeKind.LATE && counts(it) }

    /**
     * The plan as it stands [today], derived from the ledger: refunds cancel the unpaid installments from the last backwards; late fees add to their installment;
     * repayments (posted transfers linked to the plan) are applied in date order to the earliest installment first, the overflow to the next.
     */
    fun view(data: MintData, plan: InstallmentPlan, today: LocalDate): PlanView {
        val linked = data.transactions.filter { it.planId == plan.id && counts(it) }
        val refunded = linked.filter { it.type == TxType.REFUND }.sumOf { it.amountMinor }
        // Refunds take principal off the last installments first.
        var toCancel = refunded
        val principal = plan.installments.map { it.principalMinor }.toMutableList()
        val fee = plan.installments.map { it.feeMinor }.toMutableList()
        for (i in plan.installments.indices.reversed()) {
            if (toCancel <= 0) break
            val cut = minOf(principal[i], toCancel)
            principal[i] -= cut
            toCancel -= cut
            if (principal[i] == 0L) fee[i] = 0
        }
        val late = plan.installments.map { inst -> linked.filter { it.type == TxType.FEE && it.feeKind == FeeKind.LATE && it.planNo == inst.no }.sumOf { it.amountMinor } }
        var pool = linked.filter { it.type == TxType.TRANSFER && it.toAccountId == plan.accountId }
            .sortedWith(compareBy({ it.atMillis }, { it.createdMillis })).sumOf { it.amountMinor }
        val views = plan.installments.mapIndexed { i, inst ->
            val due = principal[i] + fee[i] + late[i]
            val paid = minOf(pool, due)
            pool -= paid
            val state = when {
                due == 0L -> InstallmentState.CANCELLED
                paid >= due -> InstallmentState.PAID
                paid > 0 -> InstallmentState.PARTIAL
                inst.dueDay < today -> InstallmentState.OVERDUE
                inst.dueDay == today -> InstallmentState.DUE
                else -> InstallmentState.UPCOMING
            }
            InstallmentView(inst.no, inst.dueDay, principal[i], fee[i], late[i], paid, state, feeRecorded(data, plan, inst.no))
        }
        return PlanView(plan, views, refunded, pool)
    }

    fun views(data: MintData, today: LocalDate): List<PlanView> = data.plans.map { view(data, it, today) }

    /** What the installments still unpaid and due on or before [today] plus [withinDays] ask in [currency] (overdue ones included). */
    fun dueWithin(data: MintData, currency: String, today: LocalDate, withinDays: Int): Pair<Long, Int> {
        val until = today.plus(DatePeriod(days = withinDays))
        var sum = 0L
        var n = 0
        for (v in views(data, today)) {
            if (v.plan.currency != currency) continue
            for (i in v.installments) if (i.remainingMinor > 0 && i.dueDay <= until) { sum += i.remainingMinor; n++ }
        }
        return sum to n
    }

    /** What an account's records did in `[from, endExclusive)` (MINT.md 5.6A), posted records only. */
    fun activity(data: MintData, accountId: String, from: LocalDate, endExclusive: LocalDate): CreditActivity {
        val txs = data.transactions.filter { counts(it) && it.day >= from && it.day < endExclusive }
        fun sum(f: (Transaction) -> Boolean) = txs.filter(f).sumOf { it.amountMinor }
        return CreditActivity(
            purchasesMinor = sum { it.type == TxType.EXPENSE && it.accountId == accountId },
            feesMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind != FeeKind.INTEREST && it.feeKind != FeeKind.LATE },
            interestMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind == FeeKind.INTEREST },
            lateFeesMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind == FeeKind.LATE },
            refundsMinor = sum { it.type == TxType.REFUND && it.accountId == accountId },
            repaymentsMinor = sum { it.type == TxType.TRANSFER && it.toAccountId == accountId },
        )
    }

    /** Cash that moved through liquid accounts in `[from, endExclusive)` in [currency] (MINT.md 5.6A). Transfers between two liquid accounts are neither in nor out. */
    fun cashFlow(data: MintData, from: LocalDate, endExclusive: LocalDate, currency: String): CashFlow {
        val liquid = data.accounts.filter { it.type.liquid && it.currency == currency }.map { it.id }.toSet()
        var inflow = 0L
        var outflow = 0L
        for (t in data.transactions) {
            if (!counts(t) || t.day < from || t.day >= endExclusive || t.currency != currency) continue
            val fromLiquid = t.accountId in liquid
            when (t.type) {
                TxType.INCOME, TxType.REFUND -> if (fromLiquid) inflow += t.amountMinor
                TxType.EXPENSE, TxType.FEE -> if (fromLiquid) outflow += t.amountMinor
                TxType.ADJUSTMENT -> if (fromLiquid) { if (t.up) inflow += t.amountMinor else outflow += t.amountMinor }
                TxType.TRANSFER -> {
                    val toLiquid = t.toAccountId in liquid
                    if (fromLiquid && !toLiquid) outflow += t.amountMinor
                    if (!fromLiquid && toLiquid) inflow += t.amountMinor
                }
            }
        }
        return CashFlow(inflow, outflow)
    }
}
