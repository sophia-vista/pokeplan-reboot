package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
enum class PlanEvery(val label: String) { WEEK("Every week"), TWO_WEEKS("Every 2 weeks"), MONTH("Every month") }

@Serializable
enum class FeeKind(val label: String) { FEE("Fee"), INTEREST("Interest"), LATE("Late fee") }

@Serializable
data class Installment(val no: Int, val dueDay: LocalDate, val principalMinor: Long, val feeMinor: Long = 0)

@Serializable
data class InstallmentPlan(
    val id: String,
    val accountId: String,
    val name: String,
    val currency: String,
    val purchaseTxId: String? = null,
    val totalMinor: Long,
    val startDay: LocalDate,
    val every: PlanEvery,
    val feeKind: FeeKind = FeeKind.FEE,
    val installments: List<Installment>,
    val createdMillis: Long = 0,
    val feeRule: FeeRule? = null,
    val openingBalanceIncluded: Boolean = false,
)

@Serializable
sealed interface FeeRule {
    @Serializable
    data object None : FeeRule

    @Serializable
    data class PerInstallment(val minor: Long) : FeeRule

    @Serializable
    data class Total(val minor: Long) : FeeRule

    @Serializable
    data class RatePerPeriod(val basisPoints: Int) : FeeRule
}

enum class InstallmentState(val label: String) { UPCOMING("Upcoming"), DUE("Due today"), OVERDUE("Overdue"), PARTIAL("Partly paid"), PAID("Paid"), CANCELLED("Cancelled") }

data class InstallmentView(
    val no: Int,
    val dueDay: LocalDate,
    val principalMinor: Long,
    val feeMinor: Long,
    val lateMinor: Long,
    val paidMinor: Long,
    val state: InstallmentState,
    val feeRecorded: Boolean,
) {
    val dueMinor: Long get() = principalMinor + feeMinor + lateMinor
    val remainingMinor: Long get() = (dueMinor - paidMinor).coerceAtLeast(0)
}

data class PlanView(val plan: InstallmentPlan, val installments: List<InstallmentView>, val refundedMinor: Long, val overpaidMinor: Long) {
    val remainingMinor: Long get() = installments.sumOf { it.remainingMinor }
    val paidMinor: Long get() = installments.sumOf { it.paidMinor }
    val finished: Boolean get() = installments.all { it.state == InstallmentState.PAID || it.state == InstallmentState.CANCELLED }
    val next: InstallmentView? get() = installments.firstOrNull { it.remainingMinor > 0 }
}

data class CreditActivity(
    val purchasesMinor: Long,
    val feesMinor: Long,
    val interestMinor: Long,
    val lateFeesMinor: Long,
    val refundsMinor: Long,
    val repaymentsMinor: Long,
)

data class CashFlow(val inflowMinor: Long, val outflowMinor: Long) {
    val netMinor: Long get() = inflowMinor - outflowMinor
}

object Installments {
    const val MAX_COUNT = 36

    fun allowedFor(type: AccountType): Boolean = type == AccountType.CREDIT_CARD || type == AccountType.PAY_LATER

    fun dueOn(first: LocalDate, every: PlanEvery, index: Int): LocalDate = when (every) {
        PlanEvery.WEEK -> first.plus(DatePeriod(days = 7 * index))
        PlanEvery.TWO_WEEKS -> first.plus(DatePeriod(days = 14 * index))
        PlanEvery.MONTH -> first.plus(DatePeriod(months = index))
    }

    fun split(total: Long, count: Int): List<Long> {
        val base = total / count
        val extra = (total % count).toInt()
        return List(count) { base + if (it < extra) 1 else 0 }
    }

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

    fun checkExisting(type: AccountType, rows: List<Installment>): Check {
        if (!allowedFor(type)) return Check.Refused("Installments belong to a credit card or a pay later account.")
        if (rows.isEmpty() || rows.size > MAX_COUNT) return Check.Refused("Add 1 to $MAX_COUNT installments.")
        if (rows.any { it.principalMinor <= 0 || it.principalMinor > Money.MAX_MINOR }) return Check.Refused("Each installment needs an amount above zero.")
        if (rows.any { it.feeMinor < 0 || it.feeMinor > Money.MAX_MINOR }) return Check.Refused("That fee looks wrong.")
        if (rows.zipWithNext().any { (a, b) -> a.dueDay > b.dueDay }) return Check.Refused("Put the installments in order of their due day.")
        return if (rows.sumOf { it.principalMinor } > Money.MAX_MINOR) Check.Refused("That total looks wrong.") else Check.Ok
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

    fun feeRecorded(data: MintData, plan: InstallmentPlan, no: Int): Boolean =
        data.transactions.any { it.planId == plan.id && it.planNo == no && it.type == TxType.FEE && it.feeKind != FeeKind.LATE && counts(it) }

    fun view(data: MintData, plan: InstallmentPlan, today: LocalDate): PlanView {
        val linked = data.transactions.filter { it.planId == plan.id || it.planAllocations.any { allocation -> allocation.planId == plan.id } }.filter(::counts)
        val refunded = linked.filter { it.type == TxType.REFUND }.sumOf { it.amountMinor }
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
        val specific = mutableMapOf<Int, Long>()
        var pool = 0L
        for (tx in linked.filter { it.type == TxType.TRANSFER && it.toAccountId == plan.accountId }) {
            val rows = tx.planAllocations.filter { it.planId == plan.id }
            when {
                rows.isNotEmpty() -> rows.forEach { row -> if (row.no != null) specific[row.no] = (specific[row.no] ?: 0L) + row.amountMinor else pool += row.amountMinor }
                tx.planId == plan.id -> pool += tx.amountMinor
            }
        }
        val dues = plan.installments.indices.map { principal[it] + fee[it] + late[it] }
        val direct = plan.installments.mapIndexed { i, inst -> minOf(specific[inst.no] ?: 0L, dues[i]) }
        pool += specific.values.sum() - direct.sum()
        val views = plan.installments.mapIndexed { i, inst ->
            val due = dues[i]
            val extra = minOf(pool, due - direct[i])
            pool -= extra
            val paid = direct[i] + extra
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

    fun allocatePayment(data: MintData, accountId: String, amountMinor: Long, today: LocalDate, firstBill: BillMonth? = null): List<PlanPaymentAllocation> =
        MonthlyBills.allocate(data, accountId, amountMinor, today, firstBill)

    fun views(data: MintData, today: LocalDate): List<PlanView> = data.plans.map { view(data, it, today) }

    // --- editing a schedule, and what blocks it (MINT.md 5.6A, 5.6C) ----------------------------------------------

    /** The fixed names a purchase or a schedule gets when the player leaves the name blank. */
    const val DEFAULT_PURCHASE_NAME = "Installment purchase"
    const val DEFAULT_SCHEDULE_NAME = "Existing installments"

    /** The records already made against one plan that fix its schedule: payments, fees and refunds. */
    data class ScheduleLock(val payments: Int, val fees: Int, val refunds: Int) {
        val blocks: Boolean get() = payments > 0 || fees > 0 || refunds > 0

        /** In words, or null when nothing blocks a change to the schedule. */
        val reason: String?
            get() {
                if (!blocks) return null
                val parts = buildList {
                    if (payments > 0) add(if (payments == 1) "a payment" else "$payments payments")
                    if (fees > 0) add(if (fees == 1) "a fee" else "$fees fees")
                    if (refunds > 0) add(if (refunds == 1) "a refund" else "$refunds refunds")
                }
                val what = when (parts.size) {
                    1 -> parts[0]
                    2 -> parts[0] + " and " + parts[1]
                    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
                }
                return "A change to the schedule is blocked: $what " + (if (parts.size == 1) "has" else "have") + " already been recorded."
            }
    }

    /** Counts the records that block a change to [plan]'s schedule; its own purchase record does not count. */
    fun scheduleLock(data: MintData, plan: InstallmentPlan): ScheduleLock {
        val purchaseId = plan.purchaseTxId
        val records = data.transactions.filter { t ->
            (t.planId == plan.id || t.planAllocations.any { it.planId == plan.id } || (purchaseId != null && t.refundOf == purchaseId)) && t.id != purchaseId
        }
        return ScheduleLock(
            payments = records.count { it.type == TxType.TRANSFER },
            fees = records.count { it.type == TxType.FEE },
            refunds = records.count { it.type == TxType.REFUND },
        )
    }

    /** Why one installment can no longer be changed, or null when it can. */
    fun installmentLock(data: MintData, plan: InstallmentPlan, view: InstallmentView): String? {
        if (data.transactions.any { t -> t.type == TxType.REFUND && (t.planId == plan.id || (plan.purchaseTxId != null && t.refundOf == plan.purchaseTxId)) }) {
            return "A refund has already changed this schedule."
        }
        if (view.lateMinor > 0) return "A late fee is recorded on this installment."
        if (view.feeRecorded) return "Its fee is already billed."
        if (view.paidMinor > 0) return "A payment is already recorded on it."
        return null
    }

    /**
     * What deleting a purchase and its schedule would take away, and what would stay (MINT.md 5.6A): the purchase and the
     * schedule go; a payment, a fee or a refund already recorded on it is real history, so it blocks the deletion.
     */
    data class DeletionPreview(
        val purchaseId: String?,
        val installments: Int,
        val payments: Int,
        val paidMinor: Long,
        val fees: Int,
        val feeMinor: Long,
        val refunds: Int,
        val refundMinor: Long,
        val remainingMinor: Long,
    ) {
        val canDelete: Boolean get() = purchaseId != null && payments == 0 && fees == 0 && refunds == 0

        /** What the records that stay are, or null when nothing would stay. */
        val blockedReason: String?
            get() {
                if (payments == 0 && fees == 0 && refunds == 0) return null
                val parts = buildList {
                    if (payments > 0) add(if (payments == 1) "a payment" else "$payments payments")
                    if (fees > 0) add(if (fees == 1) "a fee" else "$fees fees")
                    if (refunds > 0) add(if (refunds == 1) "a refund" else "$refunds refunds")
                }
                val what = when (parts.size) {
                    1 -> parts[0]
                    2 -> parts[0] + " and " + parts[1]
                    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
                }
                return "It cannot be deleted: $what " + (if (parts.size == 1) "has" else "have") + " already been recorded on it and would be left behind."
            }
    }

    /** The preview of deleting one plan's purchase and schedule; null when the plan is gone. */
    fun deletionPreview(data: MintData, plan: InstallmentPlan, today: LocalDate): DeletionPreview {
        val purchase = plan.purchaseTxId?.let { id -> data.transactions.firstOrNull { it.id == id } }
        val records = data.transactions.filter { t ->
            (t.planId == plan.id || t.planAllocations.any { it.planId == plan.id } || (purchase != null && t.refundOf == purchase.id)) && t.id != purchase?.id
        }
        return DeletionPreview(
            purchaseId = purchase?.id,
            installments = plan.installments.size,
            payments = records.count { it.type == TxType.TRANSFER },
            paidMinor = records.filter { it.type == TxType.TRANSFER }.sumOf { it.amountMinor },
            fees = records.count { it.type == TxType.FEE },
            feeMinor = records.filter { it.type == TxType.FEE }.sumOf { it.amountMinor },
            refunds = records.count { it.type == TxType.REFUND },
            refundMinor = records.filter { it.type == TxType.REFUND }.sumOf { it.amountMinor },
            remainingMinor = view(data, plan, today).remainingMinor,
        )
    }

    fun dueWithin(data: MintData, currency: String, today: LocalDate, withinDays: Int): Pair<Long, Int> {
        val until = today.plus(DatePeriod(days = withinDays))
        var sum = 0L
        val bills = mutableSetOf<Pair<String, BillMonth>>()
        for (v in views(data, today)) {
            if (v.plan.currency != currency) continue
            for (i in v.installments) if (i.remainingMinor > 0 && i.dueDay <= until) { sum += i.remainingMinor; bills += v.plan.accountId to BillMonth.of(i.dueDay) }
        }
        return sum to bills.size
    }

    fun activity(data: MintData, accountId: String, from: LocalDate, endExclusive: LocalDate): CreditActivity {
        val txs = data.transactions.filter { counts(it) && it.day >= from && it.day < endExclusive }
        fun sum(f: (Transaction) -> Boolean) = txs.filter(f).sumOf { it.amountMinor }
        return CreditActivity(
            purchasesMinor = sum { it.type == TxType.EXPENSE && it.accountId == accountId },
            feesMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind != FeeKind.INTEREST && it.feeKind != FeeKind.LATE } +
                txs.filter { it.type == TxType.TRANSFER && it.accountId == accountId }.sumOf { it.feeMinor },
            interestMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind == FeeKind.INTEREST },
            lateFeesMinor = sum { it.type == TxType.FEE && it.accountId == accountId && it.feeKind == FeeKind.LATE },
            refundsMinor = sum { it.type == TxType.REFUND && it.accountId == accountId },
            repaymentsMinor = sum { it.type == TxType.TRANSFER && it.toAccountId == accountId },
        )
    }

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
                    if (fromLiquid) outflow += t.feeMinor
                    if (fromLiquid && !toLiquid) outflow += t.amountMinor
                    if (!fromLiquid && toLiquid) inflow += t.amountMinor
                }
            }
        }
        return CashFlow(inflow, outflow)
    }
}
