package app.pokeplan.mint

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod

/**
 * The things worth a look today, made from the records themselves (MINT.md 9.1): a review the player works through
 * item by item, never a checklist that must be finished. Nothing here writes; the store keeps what was put off.
 */
object ReviewInbox {
    /** An account is worth checking when it has not been compared with its statement for this many days. */
    const val STALE_DAYS = 30

    /** How far back a missing category, a fee or a check counts as worth reviewing. */
    const val RECENT_DAYS = 30

    enum class Kind(val label: String) {
        CONFIRM("To confirm"),
        IMPORTED("Imported"),
        UPCOMING("Coming up"),
        FEES("Fees and interest"),
        BUDGET("Budgets"),
        MISSING("Details missing"),
        BALANCE("Balances"),
    }

    /** One thing to look at. [id] is stable, so putting it off is remembered across saves. */
    data class Item(val id: String, val kind: Kind, val title: String, val detail: String, val priority: Int, val count: Int = 1)

    fun items(data: MintData, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY, staleDays: Int = data.settings.reviewStaleDays): List<Item> {
        val out = ArrayList<Item>()
        val recentFrom = today.minus(DatePeriod(days = RECENT_DAYS))

        // What is waiting for the player, and what the phone read into the review.
        val waiting = data.transactions.count { it.status == TxStatus.PENDING && (it.interestThrough != null || it.captureRef != null) }
        if (waiting > 0) out += Item(
            "confirm", Kind.CONFIRM,
            if (waiting == 1) "A record is waiting to be confirmed" else "$waiting records are waiting to be confirmed",
            "They are not in a balance until you confirm, adjust or skip them.", priority = 0, count = waiting,
        )
        val imported = data.transactions.count { it.importBatch != null && it.unreviewed }
        if (imported > 0) out += Item(
            "imported", Kind.IMPORTED,
            if (imported == 1) "One imported record has not been reviewed" else "$imported imported records have not been reviewed",
            "Check them and mark the import reviewed when they look right.", priority = 0, count = imported,
        )

        // What is about to fall due: bills, installments and card statements within a week.
        val bills = MintRules.upcoming(data, today, 7).size
        val installments = Installments.dueWithin(data, data.settings.currency, today, 7).second
        val cardDues = CardCycles.dueWithin(data, today, 7).size
        val upcoming = bills + installments + cardDues
        if (upcoming > 0) out += Item(
            "upcoming", Kind.UPCOMING,
            if (upcoming == 1) "One payment is due within a week" else "$upcoming payments are due within a week",
            "Bills, installments and card statements from the next seven days.", priority = 1, count = upcoming,
        )

        // Fees and interest are worth a second look; they are easy to miss and count in spending.
        val fees = data.transactions.filter { it.status == TxStatus.POSTED && it.type == TxType.FEE && it.day >= recentFrom }
        if (fees.isNotEmpty()) out += Item(
            "fees", Kind.FEES,
            if (fees.size == 1) "One fee or interest charge was recorded" else "${fees.size} fees or interest charges were recorded",
            "Fees, interest and late fees count in spending; check they are right.", priority = 2, count = fees.size,
        )

        // Budgets at or over nine tenths of their limit.
        val budgets = data.budgets.filter { it.status == PlanStatus.ACTIVE }.mapNotNull { b -> MintRules.progress(data, b, today, weekStart).percent?.takeIf { it >= 90 }?.let { b to it } }
        if (budgets.isNotEmpty()) out += Item(
            "budget", Kind.BUDGET,
            if (budgets.size == 1) "One budget is close to its limit" else "${budgets.size} budgets are close to their limits",
            "Nine tenths or more of the amount planned for the period is used.", priority = 2, count = budgets.size,
        )

        // Records that would be hard to understand later: no category, no name.
        val missing = data.transactions.count {
            it.status == TxStatus.POSTED && it.type in setOf(TxType.EXPENSE, TxType.INCOME) && it.day >= recentFrom &&
                it.categoryId == null && it.splits.isEmpty()
        }
        if (missing > 0) out += Item(
            "missing", Kind.MISSING,
            if (missing == 1) "One record has no category" else "$missing records have no category",
            "A category keeps the reports and the budgets honest.", priority = 3, count = missing,
        )

        // Accounts not compared with a statement for a while, one item each so it can be done alone.
        data.accounts.filter { it.status == AccountStatus.ACTIVE }.forEach { account ->
            val hasRecords = data.transactions.any { it.status == TxStatus.POSTED && (it.accountId == account.id || it.toAccountId == account.id) }
            if (!hasRecords) return@forEach
            val last = data.reconciliations.filter { it.accountId == account.id }.maxOfOrNull { it.day }
            if (last == null || last < today.minus(DatePeriod(days = staleDays.coerceIn(7, 365)))) {
                out += Item(
                    "balance:" + account.id, Kind.BALANCE,
                    "Check " + account.name + "'s balance",
                    if (last == null) "Never compared with a statement." else "Last checked " + last.toString().take(10) + ".",
                    priority = 3,
                )
            }
        }

        return out.sortedWith(compareBy({ it.priority }, { -it.count }))
    }
}
