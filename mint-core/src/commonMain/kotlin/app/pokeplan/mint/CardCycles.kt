package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The card month as the player sees it (MINT.md 5.6B, 5.6D): the cycle a credit card or pay-later account's configured
 * statement and due days make, what it billed at the close, what has been paid since, and whether it is due, overdue,
 * partly paid or paid. It is derived from the records — nothing here writes — so the recorded statement stays the
 * lender's fact and the cycle is only how Mint reads it.
 */
object CardCycles {
    enum class State(val label: String) { UPCOMING("Upcoming"), DUE("Due today"), OVERDUE("Overdue"), PARTLY_PAID("Partly paid"), PAID("Paid") }

    data class Cycle(
        /** The day the statement closed. */
        val statementDay: LocalDate,
        /** The day its payment is due (the configured due day after the close). */
        val dueDay: LocalDate,
        /** What was owed at the close, posted only. */
        val billedMinor: Long,
        /** Repayments into the account since the close, posted only. */
        val paidMinor: Long,
        /** The recorded statement's minimum payment, when the player has entered one. */
        val minimumMinor: Long,
        /** The recorded statement this cycle matches, when one exists. */
        val statementId: String?,
    ) {
        val remainingMinor: Long get() = (billedMinor - paidMinor).coerceAtLeast(0)

        fun state(today: LocalDate): State = when {
            remainingMinor == 0L -> State.PAID
            today > dueDay -> State.OVERDUE
            paidMinor > 0 -> State.PARTLY_PAID
            today == dueDay -> State.DUE
            else -> State.UPCOMING
        }
    }

    /** Whether this account has a cycle at all: a credit line with both days configured. */
    fun applies(account: FinancialAccount): Boolean =
        Installments.allowedFor(account.type) && account.statementDay in 1..31 && account.dueDay in 1..31

    /** The date of [day] in [month] clamped to the month's length. */
    private fun inMonth(year: Int, month: Int, day: Int): LocalDate {
        val last = LocalDate(year, month, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
        return LocalDate(year, month, minOf(day, last))
    }

    /** The last [day] of the month on or before [date]. */
    private fun onOrBefore(day: Int, date: LocalDate): LocalDate {
        val here = inMonth(date.year, date.monthNumber, day)
        return if (here <= date) here else {
            val previous = LocalDate(date.year, date.monthNumber, 1).minus(DatePeriod(months = 1))
            inMonth(previous.year, previous.monthNumber, day)
        }
    }

    /** The first [day] of the month strictly after [date]. */
    private fun after(day: Int, date: LocalDate): LocalDate {
        val next = date.plus(DatePeriod(days = 1))
        val here = inMonth(next.year, next.monthNumber, day)
        return if (here >= next) here else {
            val following = LocalDate(next.year, next.monthNumber, 1).plus(DatePeriod(months = 1))
            inMonth(following.year, following.monthNumber, day)
        }
    }

    /** The cycle a statement closed on [close]: what was owed then, what has been paid since, and its due day. */
    private fun cycle(data: MintData, account: FinancialAccount, close: LocalDate, today: LocalDate): Cycle {
        val due = after(account.dueDay, close)
        val billed = MintRules.balance(data.copy(transactions = data.transactions.filter { it.day <= close }), account).postedMinor.coerceAtLeast(0)
        val paid = data.transactions.filter {
            it.status == TxStatus.POSTED && it.type == TxType.TRANSFER && it.toAccountId == account.id && it.day > close && it.day <= today
        }.sumOf { it.amountMinor }
        val statement = data.creditStatements.filter { it.accountId == account.id && it.dueDay == due }.maxByOrNull { it.statementDay }
        return Cycle(close, due, billed, paid, statement?.minimumPaymentMinor ?: 0, statement?.id)
    }

    /**
     * The open cycle: the one whose statement has closed at or before [today].
     *
     * Nothing is derived from before the account was added: its opening balance only says what was owed on that first
     * day, so a close before it never billed anything (MINT.md 4.3D). Without the guard a card added on the 20th with an
     * opening balance and a statement day earlier in the month looked like an old statement whose due date had passed.
     */
    fun current(data: MintData, account: FinancialAccount, today: LocalDate): Cycle? {
        if (!applies(account)) return null
        val close = onOrBefore(account.statementDay, today)
        if (close < account.openingDay) return null
        return cycle(data, account, close, today)
    }

    /**
     * The cycles still worth showing, newest first: the open one and the [back] before it that billed something or
     * recorded a statement.
     */
    fun recent(data: MintData, account: FinancialAccount, today: LocalDate, back: Int = 6): List<Cycle> {
        if (!applies(account)) return emptyList()
        val out = ArrayList<Cycle>()
        var close = onOrBefore(account.statementDay, today)
        repeat(back) {
            if (close < account.openingDay) return out
            val c = cycle(data, account, close, today)
            if (c.billedMinor > 0 || c.statementId != null) out += c
            close = onOrBefore(account.statementDay, close.minus(DatePeriod(days = 1)))
        }
        return out
    }

    /**
     * The next close and the day it is due, for the card's own "closes on" line: the day the next statement closes and
     * the configured due day after it. Null when the account keeps neither day.
     */
    fun next(account: FinancialAccount, today: LocalDate): Pair<LocalDate, LocalDate>? {
        if (!applies(account)) return null
        val close = MonthlyBills.dayOnOrAfter(account.statementDay, today)
        return close to after(account.dueDay, close)
    }

    /**
     * Every card cycle due within [days] (or already overdue), for the Overview and the Bills list.
     *
     * A cycle is left out when the player already has a bill on that card for the same due day that is still open: the
     * payment is one obligation, so it is never shown twice (MINT.md 5.6C).
     */
    fun dueWithin(data: MintData, today: LocalDate, days: Int): List<Pair<FinancialAccount, Cycle>> {
        val until = today.plus(DatePeriod(days = days))
        return data.accounts.filter { it.status == AccountStatus.ACTIVE }
            .mapNotNull { account -> current(data, account, today)?.let { account to it } }
            .filter { (account, c) ->
                c.remainingMinor > 0 && c.state(today) != State.PAID && c.dueDay <= until && !alreadyABill(data, account, c.dueDay, today)
            }
            .sortedBy { (_, c) -> c.dueDay }
    }

    /** Whether an open bill on this account already carries the obligation for [due]. */
    private fun alreadyABill(data: MintData, account: FinancialAccount, due: LocalDate, today: LocalDate): Boolean =
        data.bills.any { bill ->
            bill.accountId == account.id && bill.status == PlanStatus.ACTIVE &&
                MintRules.occurrence(data, bill, due, today).state !in setOf(OccurrenceState.PAID, OccurrenceState.SKIPPED)
        }
}
