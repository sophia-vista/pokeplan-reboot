package app.pokeplan.app.mint

import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.Bill
import app.pokeplan.mint.BillMonth
import app.pokeplan.mint.BillPayment
import app.pokeplan.mint.Budget
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.Category
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.CardStyle
import app.pokeplan.mint.Check
import app.pokeplan.mint.CreditActivity
import app.pokeplan.mint.CreditStatement
import app.pokeplan.mint.EarnSchedule
import app.pokeplan.mint.FeeKind
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.InstallmentPlan
import app.pokeplan.mint.Installments
import app.pokeplan.mint.PlanEvery
import app.pokeplan.mint.PlanView
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.MintData
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.MintSettings
import app.pokeplan.mint.MonthlyBill
import app.pokeplan.mint.Money
import app.pokeplan.mint.MonthlyBills
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.Reconciliation
import app.pokeplan.mint.ReviewRecord
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.SavingsTarget
import app.pokeplan.mint.StatementImport
import app.pokeplan.mint.Transaction
import app.pokeplan.mint.TransactionSplit
import app.pokeplan.mint.TxSource
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import app.pokeplan.app.World
import app.pokeplan.app.heal.Logged
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId

/**
 * The Mint module on this device (docs/pokelife `MODULES/MINT.md`): its own save in its own [storage], apart from every other module's, kept by
 * `MintTables` in the live database. Everything in it is private: it reaches the shared world only through [world], with a [MintEvent] that carries
 * a stable id, the module, an opaque record reference, the broad kind and the time, and **never** an amount, a currency, an account, a merchant,
 * a category or a note.
 *
 * Only abstract organisation can earn, and only while Ledger Research is on: a reconciled account (paid once per account a day) and a finished
 * review (once a week). Spending, income, transfers, balances, budgets and bills never pay, whatever they say. A record is paid when it is created
 * and never again: the record keeps `paid`, and the game remembers the event id, so a restore or a replay can not pay twice.
 */
class MintStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val weekStart: () -> DayOfWeek = { DayOfWeek.MONDAY },
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, MintData.serializer(), json, tidy = { tidy(it) }) { tidy(MintData()) }

    val data: MintData get() = document.value

    fun observe(listener: (MintData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date

    /** Picks up a change another process made, without writing anything back; see [SavedDocument.reload]. */
    fun reload() = document.reload()

    /** Picks up what another store in this process (a widget, a notification) wrote; cheap when nothing was. */
    fun sync() = document.sync()

    private fun update(change: (MintData) -> MintData) = document.update(change)

    /** Puts back a [snapshot] taken before a delete, for the undo bar. */
    fun restore(snapshot: MintData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    /** Noon of [day] on this phone, for a record made for a day other than today (it keeps its day whatever the zone changes). */
    fun noonOf(day: LocalDate): Long = day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: MintKind, recordId: String, streak: Int = 0): List<app.pokeplan.core.reward.Reward>? =
        if (!data.settings.progression) null else world.mint(MintEvent("mint:${kind.name.lowercase()}:$recordId", kind, nowMillis, recordId, streak = streak))

    /** How many weeks in a row, this one included, had a paid review (the count that adds shiny rolls to a review's outbreak). Counts weeks only. */
    private fun reviewStreak(): Int {
        val weeks = data.reviews.filter { it.paid }.map { MintRules.weekStartOf(it.day, weekStart()) }.toSet()
        var week = MintRules.weekStartOf(today, weekStart()).minus(DatePeriod(days = 7))
        var streak = 1
        while (week in weeks) { streak++; week = week.minus(DatePeriod(days = 7)) }
        return streak
    }

    /**
     * Pays the flat "logged a record" reward for [tx] (already saved) when [MintRules.logPays] allows, and marks it paid. The event holds no amount (MINT.md 16.2, 16.3).
     * Returns the rewards, empty when nothing was paid.
     */
    private fun payForLog(tx: Transaction): List<app.pokeplan.core.reward.Reward> {
        if (!MintRules.logPays(data, tx, today)) return emptyList()
        val rewards = pay(MintKind.LOGGED, tx.id) ?: return emptyList()
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == tx.id) it.copy(paid = true) else it }) }
        return rewards
    }

    /** Every amount on a screen goes through this, so hiding values (MINT.md section 3.3) is one switch and covers all of Mint. */
    fun show(minor: Long, currency: String): String = app.pokeplan.mint.Money.format(minor, currency, data.settings.hideValues)

    // --- accounts ----------------------------------------------------------------------------------------------------

    fun addAccount(
        name: String, type: AccountType, currency: String, openingMinor: Long = 0, institution: String = "", suffix: String = "",
        group: String = "", card: String = "", limitMinor: Long = 0, statementDay: Int = 0, dueDay: Int = 0,
        annualAprBasisPoints: Int = 0, interestGraceDays: Int = 0,
        earnRateBasisPoints: Int = 0, earnSchedule: EarnSchedule = EarnSchedule.OFF, earnTaxBasisPoints: Int = 0, earnAutoPost: Boolean = false,
    ): Logged<FinancialAccount> {
        val code = currency.trim().uppercase()
        (MintRules.checkAccount(name, code, openingMinor, suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkLimit(type, limitMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkCardDay(type, statementDay) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkCardDay(type, dueDay) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (annualAprBasisPoints !in 0..MintRules.MAX_APR_BASIS_POINTS) return Logged(null, "Enter an annual rate from 0 to 1000 percent.")
        if (interestGraceDays !in 0..MintRules.MAX_GRACE_DAYS) return Logged(null, "Enter a grace period from 0 to ${MintRules.MAX_GRACE_DAYS} days.")
        if (!Installments.allowedFor(type) && (annualAprBasisPoints != 0 || interestGraceDays != 0)) return Logged(null, "Interest estimates are for credit card and pay-later accounts.")
        (MintRules.checkEarn(type, earnRateBasisPoints, earnSchedule, earnTaxBasisPoints) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val account = FinancialAccount(
            newId(), name.trim(), type, code, openingMinor, today, institution.trim().take(MintRules.NAME_MAX), suffix.trim(), group.trim().take(MintRules.NAME_MAX),
            createdMillis = nowMillis, card = MintRules.cardId(card), limitMinor = limitMinor, statementDay = statementDay, dueDay = dueDay,
            annualAprBasisPoints = annualAprBasisPoints, interestGraceDays = interestGraceDays,
            earnRateBasisPoints = earnRateBasisPoints, earnSchedule = earnSchedule, earnTaxBasisPoints = earnTaxBasisPoints, earnAutoPost = earnAutoPost,
            earnFrom = if (earnSchedule != EarnSchedule.OFF) today else null,
        )
        update { it.copy(accounts = it.accounts + account) }
        return Logged(account)
    }

    /**
     * Keeps [base64Png] as the account's receive-money QR image, or removes it when empty (MINT.md 4.2). The caller has already shrunk the picture; it is checked
     * for size and PNG form only, never decoded or read. Works on an archived account too; nothing else about the account changes and nothing leaves Mint.
     */
    fun setReceiveQr(accountId: String, base64Png: String): Logged<FinancialAccount> {
        val old = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "That account is gone.")
        (MintRules.checkReceiveQr(base64Png) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(receiveQr = base64Png)
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == accountId) next else it }) }
        return Logged(next)
    }

    /**
     * Puts the accounts named in [orderedIds] in that order, in the slots those accounts already hold in the list (the others stay where they are), so a group or an
     * account can be moved without touching the rest. The Accounts list shows the list's order (MINT.md 4.3). Refused (false) when an id is unknown or repeated.
     */
    fun arrangeAccounts(orderedIds: List<String>): Boolean {
        if (orderedIds.toSet().size != orderedIds.size) return false
        val slots = data.accounts.withIndex().filter { it.value.id in orderedIds }.map { it.index }
        if (slots.size != orderedIds.size) return false
        val byId = data.accounts.associateBy { it.id }
        update { d ->
            val next = d.accounts.toMutableList()
            slots.forEachIndexed { i, slot -> next[slot] = byId.getValue(orderedIds[i]) }
            d.copy(accounts = next)
        }
        return true
    }

    /** Whether [id] has records that depend on its currency: a transaction, a reconciliation or a bill paid from it. */
    fun hasHistory(id: String): Boolean =
        data.transactions.any { it.accountId == id || it.toAccountId == id } || data.plans.any { it.accountId == id } ||
            data.creditStatements.any { it.accountId == id } || data.reconciliations.any { it.accountId == id } || data.bills.any { it.accountId == id }

    /**
     * Edits an account: its name, type (only to another type on the same side, an asset stays an asset and a liability a liability), institution, group, last
     * digits and **opening balance** (a mistake made when the account was added; the balance is derived, so the shown balance moves by the difference and no
     * record is rewritten). The **currency** can change only while the account has no history ([hasHistory]); with history it stays, because every record and
     * bill linked to it is in that currency (MINT.md section 5.2). Nothing is paid, and nothing about the edit leaves Mint.
     */
    fun updateAccount(a: FinancialAccount): Logged<FinancialAccount> {
        val old = data.accounts.firstOrNull { it.id == a.id } ?: return Logged(null, "That account is gone.")
        if ((data.plans.any { it.accountId == old.id } || data.creditStatements.any { it.accountId == old.id }) && !Installments.allowedFor(a.type))
            return Logged(null, "This account has credit records. Keep it as a credit card or pay later account.")
        if (a.annualAprBasisPoints !in 0..MintRules.MAX_APR_BASIS_POINTS) return Logged(null, "Enter an annual rate from 0 to 1000 percent.")
        if (a.interestGraceDays !in 0..MintRules.MAX_GRACE_DAYS) return Logged(null, "Enter a grace period from 0 to ${MintRules.MAX_GRACE_DAYS} days.")
        if (data.savingsTargets.any { it.status != PlanStatus.ARCHIVED && old.id in it.accountIds } &&
            (a.currency.trim().uppercase() != old.currency || a.type != old.type))
            return Logged(null, "This account is linked to a savings target. Remove it from the target before changing its type or currency.")
        val currency = if (a.currency == old.currency || hasHistory(old.id)) old.currency else a.currency.trim().uppercase()
        (MintRules.checkAccount(a.name, currency, a.openingMinor, a.suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val newType = if (a.type.liability == old.type.liability) a.type else old.type
        if (!Installments.allowedFor(newType) && (a.annualAprBasisPoints != 0 || a.interestGraceDays != 0))
            return Logged(null, "Interest estimates are for credit card and pay-later accounts.")
        (MintRules.checkLimit(newType, a.limitMinor) as? Check.Refused)?.let { if (Installments.allowedFor(newType)) return Logged(null, it.reason) }
        if (Installments.allowedFor(newType)) listOf(a.statementDay, a.dueDay).forEach { d -> (MintRules.checkCardDay(newType, d) as? Check.Refused)?.let { return Logged(null, it.reason) } }
        val earnType = if (a.type.liability == old.type.liability) a.type else old.type
        (MintRules.checkEarn(earnType, a.earnRateBasisPoints, a.earnSchedule, a.earnTaxBasisPoints) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(
            earnRateBasisPoints = a.earnRateBasisPoints, earnSchedule = a.earnSchedule, earnTaxBasisPoints = a.earnTaxBasisPoints, earnAutoPost = a.earnAutoPost,
            earnFrom = if (a.earnSchedule == EarnSchedule.OFF) null else if (old.earnSchedule == EarnSchedule.OFF) today else old.earnFrom,
            name = a.name.trim(), institution = a.institution.trim().take(MintRules.NAME_MAX), suffix = a.suffix.trim(), group = a.group.trim().take(MintRules.NAME_MAX),
            type = if (a.type.liability == old.type.liability) a.type else old.type, openingMinor = a.openingMinor, currency = currency, card = MintRules.cardId(a.card), hideFromTotal = a.hideFromTotal, appPackage = MintRules.cleanPackage(a.appPackage), limitMinor = if (Installments.allowedFor(newType)) a.limitMinor else 0, statementDay = if (Installments.allowedFor(newType)) a.statementDay else 0, dueDay = if (Installments.allowedFor(newType)) a.dueDay else 0,
            annualAprBasisPoints = if (Installments.allowedFor(newType)) a.annualAprBasisPoints else 0,
            interestGraceDays = if (Installments.allowedFor(newType)) a.interestGraceDays else 0,
        )
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == a.id) next else it }) }
        return Logged(next)
    }

    /** Active, archived (hidden from default views, history kept) or closed (takes no new records). Never touches a transaction. */
    fun setAccountStatus(id: String, status: AccountStatus) =
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(status = status) else it }) }

    /** Deletes an account with no history; one with history is refused and should be archived instead (MINT.md section 4.6). Returns the reason, or null when deleted. */
    fun deleteAccount(id: String): String? {
        if (data.transactions.any { it.accountId == id || it.toAccountId == id } ||
            data.reconciliations.any { it.accountId == id } ||
            data.plans.any { it.accountId == id } || data.creditStatements.any { it.accountId == id } ||
            data.savingsTargets.any { it.status != PlanStatus.ARCHIVED && id in it.accountIds })
            return "This account has records, installment plans or an active savings target. Remove those links or archive it instead."
        update { d -> d.copy(
            accounts = d.accounts.filterNot { it.id == id },
            bills = d.bills.map { if (it.accountId == id) it.copy(accountId = null) else it },
            savingsTargets = d.savingsTargets.map { target -> target.copy(accountIds = target.accountIds.filterNot { it == id }) },
        ) }
        return null
    }

    // --- categories --------------------------------------------------------------------------------------------------

    fun addCategory(name: String, kind: CategoryKind): Category? {
        val n = name.trim().take(MintRules.NAME_MAX)
        if (n.isEmpty() || data.categories.any { it.kind == kind && it.name.equals(n, ignoreCase = true) && !it.archived }) return null
        val c = Category(newId(), n, kind)
        update { it.copy(categories = it.categories + c) }
        return c
    }

    fun renameCategory(id: String, name: String) {
        val n = name.trim().take(MintRules.NAME_MAX)
        if (n.isNotEmpty()) update { d -> d.copy(categories = d.categories.map { if (it.id == id) it.copy(name = n) else it }) }
    }

    fun setCategoryArchived(id: String, archived: Boolean) = update { d -> d.copy(categories = d.categories.map { if (it.id == id) it.copy(archived = archived) else it }) }

    /** Deletes a category nothing uses; one in use is refused and should be archived (MINT.md section 6: no history is lost). Returns the reason, or null. */
    fun deleteCategory(id: String): String? {
        if (data.transactions.any { it.categoryId == id || it.splits.any { split -> split.categoryId == id } } ||
            data.budgets.any { it.categoryId == id } || data.bills.any { it.categoryId == id })
            return "Something uses this category. Archive it instead."
        update { d -> d.copy(categories = d.categories.filterNot { it.id == id }) }
        return null
    }

    // --- transactions ------------------------------------------------------------------------------------------------

    /**
     * Saves one transaction. An amount is in minor units of the account's currency; a transfer is one record with two legs, so it can never be saved
     * one-sided. Nothing here pays: money moving is never a reward (MINT.md section 16.2).
     */
    fun record(
        type: TxType, amountMinor: Long, accountId: String, toAccountId: String? = null, categoryId: String? = null, payee: String = "", note: String = "",
        atMillis: Long = nowMillis, status: TxStatus = TxStatus.POSTED, up: Boolean = true, refundOf: String? = null, splits: List<TransactionSplit> = emptyList(), feeMinor: Long = 0,
        iouMinor: Long = 0, iouFor: String = "",
    ): Logged<Transaction> {
        val currency = data.accounts.firstOrNull { it.id == accountId }?.currency ?: return Logged(null, "Choose an account.")
        if (type == TxType.TRANSFER && data.accounts.firstOrNull { it.id == toAccountId }?.let { target ->
                Installments.allowedFor(target.type) && Installments.views(data, today).any { it.plan.accountId == target.id && it.remainingMinor > 0 }
            } == true)
            return Logged(null, "Repayments to this account must be allocated to its unpaid installment plans.")
        val tx = Transaction(newId(), type, amountMinor, currency, atMillis, dayOf(atMillis), accountId, toAccountId, categoryId, payee.trim().take(MintRules.NAME_MAX * 2), note.trim().take(MintRules.NOTE_MAX), status, TxSource.MANUAL, up, refundOf, createdMillis = nowMillis, splits = splits, feeMinor = feeMinor,
            iouMinor = iouMinor, iouFor = if (iouMinor > 0) cleanName(iouFor) else "")
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx, rewards = payForLog(tx))
    }

    /** Changes what was written down. The account's currency can not change under a record; a source of [TxSource.ADJUSTMENT] stays an adjustment. */
    fun updateTransaction(t: Transaction): Logged<Transaction> {
        val old = data.transactions.firstOrNull { it.id == t.id } ?: return Logged(null, "That record is gone.")
        val shaped = t.copy(
            planId = old.planId, planNo = old.planNo, feeKind = old.feeKind, planAllocations = old.planAllocations,
            creditStatementId = old.creditStatementId, interestThrough = old.interestThrough, paid = old.paid, source = old.source, createdMillis = old.createdMillis, repaymentOf = old.repaymentOf,
            importBatch = old.importBatch, importLabel = old.importLabel, fingerprint = old.fingerprint,
            day = dayOf(t.atMillis), payee = t.payee.trim().take(MintRules.NAME_MAX * 2), note = t.note.trim().take(MintRules.NOTE_MAX),
            iouFor = if (t.iouMinor > 0) cleanName(t.iouFor) else "",
        )
        // A repayment is the player's own money coming back: it keeps no IOU of its own, stays in the expense's currency, and never exceeds what is still owed.
        val parent = old.repaymentOf?.let { id -> data.transactions.firstOrNull { it.id == id } ?: return Logged(null, "The expense this repays is gone.") }
        val next = if (parent == null) shaped else shaped.copy(iouMinor = 0, iouFor = "")
        if (parent != null) {
            val room = MintRules.iouOwed(data, parent) + if (old.status == TxStatus.VOIDED) 0 else old.amountMinor
            if (next.amountMinor > room) return Logged(null, "Only " + Money.format(room, parent.currency) + " of that IOU is still owed.")
            if (data.accounts.firstOrNull { it.id == next.accountId }?.currency != parent.currency) return Logged(null, "Put the repayment in an account in ${parent.currency}.")
        }
        // An IOU changed together with the amount is checked together, in this one save; it cannot go under what was already paid back, and clearing it takes its repayments.
        val paid = if (old.repaymentOf == null) data.transactions.filter { it.repaymentOf == old.id && it.status != TxStatus.VOIDED }.sumOf { it.amountMinor } else 0L
        if (next.iouMinor != 0L && next.iouMinor < paid) return Logged(null, "More than that has already been paid back, so the IOU cannot go lower; clear it instead.")
        // A record an installment plan counts on keeps its type, amount and accounts; the plan is what changes (MINT.md 5.6A).
        if ((old.planId != null || old.planAllocations.isNotEmpty()) && (next.type != old.type || next.amountMinor != old.amountMinor || next.accountId != old.accountId || next.toAccountId != old.toAccountId))
            return Logged(null, "This record belongs to an installment plan. Change the plan instead.")
        // Editing a record on an account that was since closed is allowed to keep its history readable; only the amount rules apply there.
        (MintRules.checkAmount(next.amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val open = data.copy(accounts = data.accounts.map { it.copy(status = AccountStatus.ACTIVE) })
        (MintRules.checkTransaction(open, next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d ->
            d.copy(
                transactions = d.transactions.map { if (it.id == t.id) next else it }.filterNot { next.iouMinor == 0L && old.repaymentOf == null && it.repaymentOf == t.id },
                plans = d.plans.map { plan ->
                    if (plan.purchaseTxId == t.id && next.payee.isNotBlank()) plan.copy(name = next.payee.trim().take(MintRules.NAME_MAX)) else plan
                },
            )
        }
        return Logged(next)
    }

    // --- interest earned by a deposit account (MINT.md 4.3E) ---------------------------------------------------------------------

    /** The estimates waiting for the player: pending income records [accrueInterest] made, oldest first. */
    fun interestToReview(): List<Transaction> = data.transactions.filter { it.interestThrough != null && it.status == TxStatus.PENDING }.sortedBy { it.day }

    /**
     * Makes the estimates now due for every deposit account that earns interest (complete days only; a catch-up after days away is one record). Each is an income
     * record in the Interest category: pending, so it waits for [confirmInterest], or posted at once for an account set to post without review. Nothing is paid
     * (16.2). Safe to call as often as wanted: a period already made, in any status, is not made again. Returns how many were made.
     */
    fun accrueInterest(): Int {
        var working = data
        val made = mutableListOf<Transaction>()
        for (account in data.accounts) {
            for (period in MintRules.interestPeriods(working, account, today)) {
                val amount = MintRules.earnedInterest(working, account, period.from, period.through)
                if (amount <= 0) continue
                val tx = Transaction(
                    newId(), TxType.INCOME, amount, account.currency, noonOf(period.through), period.through, account.id, categoryId = MintRules.INTEREST_CATEGORY,
                    payee = "Interest", note = "Estimate for ${period.from} to ${period.through}", status = if (account.earnAutoPost) TxStatus.POSTED else TxStatus.PENDING,
                    createdMillis = nowMillis, interestThrough = period.through,
                )
                working = working.copy(transactions = working.transactions + tx)
                made += tx
            }
        }
        if (made.isEmpty()) return 0
        update { d ->
            val withCategory = if (d.categories.any { it.id == MintRules.INTEREST_CATEGORY }) d.categories else d.categories + Category(MintRules.INTEREST_CATEGORY, "Interest", CategoryKind.INCOME)
            d.copy(categories = withCategory, transactions = d.transactions + made)
        }
        return made.size
    }

    /** Posts an estimate, optionally at the amount the bank really credited. */
    fun confirmInterest(id: String, amountMinor: Long? = null): Logged<Transaction> {
        val old = data.transactions.firstOrNull { it.id == id && it.interestThrough != null && it.status == TxStatus.PENDING } ?: return Logged(null, "That estimate is gone.")
        if (amountMinor != null) (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(amountMinor = amountMinor ?: old.amountMinor, status = TxStatus.POSTED)
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    /** Posts every estimate waiting, at its estimated amount. */
    fun confirmAllInterest(): Int = interestToReview().count { confirmInterest(it.id).record != null }

    /** Leaves an estimate out (kept as a voided record so the same days are not estimated again). */
    fun skipInterest(id: String): Boolean {
        if (data.transactions.none { it.id == id && it.interestThrough != null && it.status == TxStatus.PENDING }) return false
        setStatus(id, TxStatus.VOIDED)
        return true
    }

    // --- statement import (a user-assisted import of a CSV file; no provider is connected) -----------------------------------

    /** What an import did, for the screen: the records added, and the rows left out because they were already here, looked like one of yours, or could not be read. */
    data class ImportResult(val batch: String?, val added: Int, val skipped: Int, val note: String)

    /** One import as it stands now: the records still in it, derived from the records themselves (nothing else is kept). */
    data class ImportBatchInfo(val id: String, val label: String, val importedMillis: Long, val accountId: String, val count: Int, val unreviewed: Int)

    /** Reads the file's text and proposes the columns, the date order and the preview for [accountId]; nothing is saved. [refused] is the reason, in words, when it can not be read at all. */
    fun previewStatement(text: String, accountId: String, columns: StatementImport.Columns? = null, order: StatementImport.DateOrder? = null): StatementPreview {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return StatementPreview(refused = "Choose an account first.")
        val table = when (val p = StatementImport.parse(text)) { is StatementImport.Parsed.Refused -> return StatementPreview(refused = p.reason); is StatementImport.Parsed.Table -> p.rows }
        val cols = columns ?: StatementImport.guess(table.first())
        val dates = table.drop(1).mapNotNull { cols.date?.let { i -> it.getOrNull(i) } }
        val detected = StatementImport.detectOrder(dates)
        val used = order ?: detected ?: StatementImport.DateOrder.DAY_FIRST
        val preview = if (cols.usable) StatementImport.preview(table, account, cols, used, data) else null
        return StatementPreview(table = table, columns = cols, order = used, orderCertain = order != null || detected != null || dates.any { it.trim().length >= 4 && it.trim().take(4).all(Char::isDigit) }, preview = preview)
    }

    /**
     * Saves the rows of [preview] listed in [keep] (their line numbers) as posted transactions of the preview's account, in **one write**, each tagged with a new
     * batch, [label] (the file's name) and its fingerprint, and marked unreviewed. A row whose fingerprint is already here is never added, so a retry or a second
     * import of the same file changes nothing (idempotent). **Nothing is paid** and nothing leaves Mint: an import is not a reward event (MINT.md section 16.2).
     */
    fun importStatement(preview: StatementImport.Preview, keep: Set<Int>, label: String): ImportResult {
        val account = data.accounts.firstOrNull { it.id == preview.account.id } ?: return ImportResult(null, 0, 0, "That account is gone.")
        if (account.status != AccountStatus.ACTIVE) return ImportResult(null, 0, 0, "That account is not open.")
        val have = data.transactions.mapNotNull { it.fingerprint }.toHashSet()
        val batch = "csv:$nowMillis"
        val name = label.trim().take(MintRules.NAME_MAX * 2)
        val chosen = preview.rows.filter { it.line in keep && it.state != StatementImport.RowState.PROBLEM && it.state != StatementImport.RowState.DUPLICATE && it.day != null && it.fingerprint !in have }
        val added = chosen.mapIndexedNotNull { i, r ->
            val day = r.day ?: return@mapIndexedNotNull null
            val noon = day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L + i * 1000L
            val tx = Transaction(
                newId(), r.type, r.amountMinor, account.currency, noon, day, account.id, null, null, r.description, "", TxStatus.POSTED, TxSource.IMPORTED,
                createdMillis = nowMillis, importBatch = batch, importLabel = name, fingerprint = r.fingerprint, unreviewed = true,
            )
            tx.takeIf { MintRules.checkTransaction(data, it) is Check.Ok }
        }
        if (added.isEmpty()) return ImportResult(null, 0, preview.rows.size, "Nothing new to add.")
        update { it.copy(transactions = it.transactions + added) }
        return ImportResult(batch, added.size, preview.rows.size - added.size, "Added ${added.size} ${if (added.size == 1) "record" else "records"}.")
    }

    /** The imports still holding records, newest first. */
    fun importBatches(): List<ImportBatchInfo> =
        data.transactions.filter { it.importBatch != null }.groupBy { it.importBatch!! }.map { (id, list) ->
            ImportBatchInfo(id, list.first().importLabel, list.first().createdMillis, list.first().accountId, list.size, list.count { it.unreviewed })
        }.sortedByDescending { it.importedMillis }

    /** Marks every record of a batch as looked at (MINT.md 9.1); changes nothing else about them. */
    fun markBatchReviewed(batch: String) = update { d -> d.copy(transactions = d.transactions.map { if (it.importBatch == batch) it.copy(unreviewed = false) else it }) }

    /**
     * Removes **only** the records that one import added (INTEGRATIONS_AND_PRIVACY.md 10.3): a record the player wrote, a reconciliation and a bill stay. A bill payment
     * or reconciliation that pointed to one of them loses only that link. Returns how many were removed.
     */
    fun removeImportBatch(batch: String): Int {
        val ids = data.transactions.filter { it.importBatch == batch }.map { it.id }.toSet()
        if (ids.isEmpty()) return 0
        update { d ->
            d.copy(
                transactions = d.transactions.filterNot { it.id in ids },
                reconciliations = d.reconciliations.map { if (it.adjustmentTxId in ids) it.copy(adjustmentTxId = null) else it },
                billPayments = d.billPayments.map { if (it.txId in ids) it.copy(txId = null) else it },
            )
        }
        return ids.size
    }

    /** Pending, posted, excluded (kept in history, left out of spending) or voided (kept, counted nowhere). */
    fun setTransactionStatus(id: String, status: TxStatus): Boolean {
        if (data.transactions.none { it.id == id } || (status != TxStatus.POSTED && planOfPurchase(id) != null)) return false
        setStatus(id, status)
        return true
    }

    private fun setStatus(id: String, status: TxStatus) = update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) it.copy(status = status) else it }) }

    /** The installment plan whose purchase record is [id], if any: that record cannot be deleted or voided while the plan exists (MINT.md 5.6A). */
    fun planOfPurchase(id: String): InstallmentPlan? = data.plans.firstOrNull { it.purchaseTxId != null && it.purchaseTxId == id }

    fun deleteTransaction(id: String): Boolean {
        if (planOfPurchase(id) != null) return false
        deleteTransactionRecord(id)
        return true
    }

    private fun deleteTransactionRecord(id: String) = update { d ->
        d.copy(
            transactions = d.transactions.filterNot { it.id == id || it.repaymentOf == id },
            reconciliations = d.reconciliations.map { if (it.adjustmentTxId == id) it.copy(adjustmentTxId = null) else it },
            billPayments = d.billPayments.map { if (it.txId == id) it.copy(txId = null) else it },
            creditStatements = d.creditStatements.map { if (it.estimatedInterestTxId == id) it.copy(estimatedInterestTxId = null) else it },
        )
    }

    // --- budgets -----------------------------------------------------------------------------------------------------

    fun addBudget(plannedMinor: Long, currency: String, period: BudgetPeriod, categoryId: String? = null): Logged<Budget> {
        (MintRules.checkAmount(plannedMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (categoryId != null && data.categories.none { it.id == categoryId }) return Logged(null, "That category is gone.")
        if (data.budgets.any { it.status != PlanStatus.ARCHIVED && it.currency == currency && it.period == period && it.categoryId == categoryId })
            return Logged(null, "There is already a budget for that.")
        val b = Budget(newId(), plannedMinor, currency, period, categoryId, createdMillis = nowMillis)
        update { it.copy(budgets = it.budgets + b) }
        return Logged(b)
    }

    /** Changes a budget's planned amount and its period. Spending is derived, so nothing else moves; a second budget for the same category and period is refused. */
    fun updateBudget(id: String, plannedMinor: Long, period: BudgetPeriod): Logged<Budget> {
        val old = data.budgets.firstOrNull { it.id == id } ?: return Logged(null, "That budget is gone.")
        (MintRules.checkAmount(plannedMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (data.budgets.any { it.id != id && it.status != PlanStatus.ARCHIVED && it.currency == old.currency && it.period == period && it.categoryId == old.categoryId })
            return Logged(null, "There is already a budget for that.")
        val next = old.copy(plannedMinor = plannedMinor, period = period)
        update { d -> d.copy(budgets = d.budgets.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    fun setBudgetStatus(id: String, status: PlanStatus) = update { d -> d.copy(budgets = d.budgets.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteBudget(id: String) = update { d -> d.copy(budgets = d.budgets.filterNot { it.id == id }) }

    fun addSavingsTarget(name: String, targetMinor: Long, currency: String, accountIds: List<String>, targetDay: LocalDate? = null): Logged<SavingsTarget> {
        val cleanName = name.trim().take(MintRules.NAME_MAX)
        if (cleanName.isBlank()) return Logged(null, "Give the savings target a name.")
        (MintRules.checkAmount(targetMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        checkSavingsAccounts(currency, accountIds)?.let { return Logged(null, it) }
        val target = SavingsTarget(newId(), cleanName, targetMinor, currency, accountIds.distinct(), targetDay, createdMillis = nowMillis)
        update { it.copy(savingsTargets = it.savingsTargets + target) }
        return Logged(target)
    }

    fun updateSavingsTarget(target: SavingsTarget): Logged<SavingsTarget> {
        val old = data.savingsTargets.firstOrNull { it.id == target.id } ?: return Logged(null, "That savings target is gone.")
        val cleanName = target.name.trim().take(MintRules.NAME_MAX)
        if (cleanName.isBlank()) return Logged(null, "Give the savings target a name.")
        (MintRules.checkAmount(target.targetMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        (MintRules.checkCurrency(target.currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        checkSavingsAccounts(target.currency, target.accountIds, target.id)?.let { return Logged(null, it) }
        val next = old.copy(name = cleanName, targetMinor = target.targetMinor, accountIds = target.accountIds.distinct(), targetDay = target.targetDay)
        update { d -> d.copy(savingsTargets = d.savingsTargets.map { if (it.id == target.id) next else it }) }
        return Logged(next)
    }

    private fun checkSavingsAccounts(currency: String, ids: List<String>, exceptId: String? = null): String? {
        val unique = ids.distinct()
        if (unique.isEmpty()) return "Choose at least one account for this target."
        if (unique.size != ids.size) return "An account is listed twice."
        for (id in unique) {
            val account = data.accounts.firstOrNull { it.id == id } ?: return "A linked account is gone."
            if (account.type.liability || account.currency != currency) return "Choose asset accounts in $currency."
            if (data.savingsTargets.any { it.id != exceptId && it.status != PlanStatus.ARCHIVED && id in it.accountIds })
                return "An account can count toward only one active savings target."
        }
        return null
    }

    fun setSavingsTargetStatus(id: String, status: PlanStatus): String? {
        val target = data.savingsTargets.firstOrNull { it.id == id } ?: return "That savings target is gone."
        if (status != PlanStatus.ARCHIVED) checkSavingsAccounts(target.currency, target.accountIds, target.id)?.let { return it }
        update { d -> d.copy(savingsTargets = d.savingsTargets.map { if (it.id == id) it.copy(status = status) else it }) }
        return null
    }

    fun deleteSavingsTarget(id: String) =
        update { d -> d.copy(savingsTargets = d.savingsTargets.filterNot { it.id == id }) }

    // --- bills -------------------------------------------------------------------------------------------------------

    /** The days of a twice-a-month bill: 1 to 31, distinct, sorted, at most four; empty (15 and 30) for any other frequency or none given (MINT.md 8.3B). */
    private fun cleanDays(frequency: Frequency, days: List<Int>): List<Int> =
        if (frequency != Frequency.SEMI_MONTHLY) emptyList() else days.filter { it in 1..31 }.distinct().sorted().take(4).let { if (it == listOf(15, 30)) emptyList() else it }

    private fun cleanName(raw: String): String = raw.trim().split(' ', '\n', '\t').filter { it.isNotEmpty() }.joinToString(" ").take(MintRules.NAME_MAX)

    // --- IOUs (MINT.md 5.8A): part of one expense someone owes back, and the records that repay it -------------------------------

    /**
     * Starts, changes or removes the IOU on an expense: [amountMinor] of it is owed back by [person]; zero removes it, and removing it takes the
     * repayments with it, because a repayment without an IOU would be money from nowhere. It moves no balance; spending counts the player's own share.
     * Returns a reason in words when it cannot be kept.
     */
    fun setIou(txId: String, amountMinor: Long, person: String): String? {
        val old = data.transactions.firstOrNull { it.id == txId } ?: return "That record is gone."
        val name = cleanName(person)
        val next = old.copy(iouMinor = amountMinor, iouFor = if (amountMinor > 0) name else "")
        (MintRules.checkTransaction(data, next) as? Check.Refused)?.let { return it.reason }
        val paid = data.transactions.filter { it.repaymentOf == txId }.sumOf { it.amountMinor }
        if (amountMinor != 0L && amountMinor < paid) return "More than that has already been paid back, so it cannot go lower; clear it instead."
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == txId) next else it }.filterNot { it.repaymentOf == txId && amountMinor == 0L }) }
        return null
    }

    /**
     * Records what someone paid back on an expense's IOU (MINT.md 5.8A): one income record in [accountId] linked to the expense. It is the player's
     * own money coming back, so it counts as no income and pays no reward; the balance and net position rise by it. Refused when it overshoots what is owed.
     */
    fun recordRepayment(txId: String, amountMinor: Long, accountId: String, atMillis: Long = nowMillis): Logged<Transaction> {
        val parent = data.transactions.firstOrNull { it.id == txId } ?: return Logged(null, "That record is gone.")
        if (parent.iouMinor <= 0) return Logged(null, "That expense has no IOU to repay.")
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val owed = MintRules.iouOwed(data, parent)
        if (amountMinor > owed) return Logged(null, "Only " + Money.format(owed, parent.currency) + " of that IOU is still owed.")
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "Choose an account.")
        if (account.currency != parent.currency) return Logged(null, "Put the repayment in an account in ${parent.currency}.")
        val tx = Transaction(
            newId(), TxType.INCOME, amountMinor, parent.currency, atMillis, dayOf(atMillis), accountId,
            payee = (parent.iouFor.ifBlank { "Someone" }).take(MintRules.NAME_MAX * 2),
            note = ("Paid back for " + parent.payee.ifBlank { "an expense" }).take(MintRules.NOTE_MAX),
            createdMillis = nowMillis, repaymentOf = txId,
        )
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx)
    }

    fun addBill(name: String, currency: String, frequency: Frequency, firstDue: LocalDate, expectedMinor: Long? = null, categoryId: String? = null, accountId: String? = null, income: Boolean = false, days: List<Int> = emptyList(), workday: Boolean = false, estimate: Boolean = false): Logged<Bill> {
        if (name.isBlank()) return Logged(null, "Give the bill a name.")
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (expectedMinor != null) (MintRules.checkAmount(expectedMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val bill = Bill(newId(), name.trim().take(MintRules.NAME_MAX), currency, frequency, firstDue, expectedMinor, categoryId, accountId, createdMillis = nowMillis, income = income, days = cleanDays(frequency, days), workday = workday, estimate = estimate)
        update { it.copy(bills = it.bills + bill) }
        return Logged(bill)
    }

    /**
     * Edits a bill's name, amount, account and category. The first due day and the frequency change only while no occurrence has been settled, so no
     * past occurrence is rewritten (MINT.md section 8.3); with payments recorded they are kept as they were.
     */
    fun updateBill(b: Bill): Logged<Bill> {
        val old = data.bills.firstOrNull { it.id == b.id } ?: return Logged(null, "That bill is gone.")
        if (b.name.isBlank()) return Logged(null, "Give the bill a name.")
        b.expectedMinor?.let { m -> (MintRules.checkAmount(m) as? Check.Refused)?.let { return Logged(null, it.reason) } }
        val days = cleanDays(b.frequency, b.days)
        val rescheduled = b.firstDue != old.firstDue || b.frequency != old.frequency || days != old.days
        if (rescheduled && data.billPayments.any { it.billId == old.id }) return Logged(null, "Payments are recorded for this bill, so its schedule cannot change. Pause it and add a new bill.")
        val next = old.copy(name = b.name.trim().take(MintRules.NAME_MAX), expectedMinor = b.expectedMinor, categoryId = b.categoryId, accountId = b.accountId, firstDue = b.firstDue, frequency = b.frequency, days = days, workday = b.workday, estimate = b.estimate)
        update { d -> d.copy(bills = d.bills.map { if (it.id == b.id) next else it }) }
        return Logged(next)
    }

    fun setBillStatus(id: String, status: PlanStatus) = update { d -> d.copy(bills = d.bills.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteBill(id: String) = update { d -> d.copy(bills = d.bills.filterNot { it.id == id }, billPayments = d.billPayments.filterNot { it.billId == id }) }

    /**
     * Settles one occurrence: paid, partly paid or skipped. For an estimated bill (`Bill.estimate`, MINT.md 8.3B) the amount is only a guess, so any amount settles it as paid. **It creates no transaction** (MINT.md section 8.2); [txId] only links one the user chose.
     * Settling again replaces the earlier settlement, so a mistake can be put right. A part payment that covers the whole expected amount is saved as paid.
     */
    fun settle(billId: String, dueDay: LocalDate, status: SettleStatus, paidMinor: Long = 0, txId: String? = null, paidDay: LocalDate = today): Boolean {
        val bill = data.bills.firstOrNull { it.id == billId } ?: return false
        if (paidDay > today) return false
        if (status != SettleStatus.SKIPPED && MintRules.checkAmount(paidMinor) is Check.Refused) return false
        val amount = if (status == SettleStatus.SKIPPED) 0L else paidMinor
        val full = bill.expectedMinor
        val settled = if (status == SettleStatus.PARTIAL && full != null && (amount >= full || bill.estimate)) SettleStatus.PAID else status
        val p = BillPayment(newId(), bill.id, dueDay, settled, amount, paidDay, txId, nowMillis)
        update { d -> d.copy(billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay } + p) }
        return true
    }

    /**
     * "Paid, record it" (MINT.md section 8.2): settles one occurrence as paid **and** writes the matching expense, linked both ways (the transaction's
     * `billId` and due day, the payment's `txId`), in one save. The account is [accountId] or else the bill's own; the amount is [paidMinor]. Nothing is
     * written when the bill or account is missing or closed, the currencies differ, or the amount is refused. A bill settled without this still works.
     */
    fun settleAndRecord(billId: String, dueDay: LocalDate, paidMinor: Long, accountId: String? = null, paidDay: LocalDate = today): Logged<Transaction> {
        val bill = data.bills.firstOrNull { it.id == billId } ?: return Logged(null, "That bill is gone.")
        if (paidDay > today) return Logged(null, "A payment cannot be dated in the future.")
        val account = data.accounts.firstOrNull { it.id == (accountId ?: bill.accountId) } ?: return Logged(null, "Choose an account.")
        val at = if (paidDay == today) nowMillis else paidDay.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
        val tx = Transaction(newId(), if (bill.income) TxType.INCOME else TxType.EXPENSE, paidMinor, account.currency, at, paidDay, account.id, categoryId = bill.categoryId, payee = bill.name.take(MintRules.NAME_MAX * 2), billId = bill.id, billDueDay = dueDay, createdMillis = nowMillis)
        if (account.currency != bill.currency) return Logged(null, "That account is in ${account.currency}, the bill in ${bill.currency}.")
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val full = bill.expectedMinor
        val payment = BillPayment(newId(), bill.id, dueDay, SettleStatus.PAID.takeIf { bill.estimate || full == null || paidMinor >= full } ?: SettleStatus.PARTIAL, paidMinor, paidDay, tx.id, nowMillis)
        update { d -> d.copy(transactions = d.transactions + tx, billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay } + payment) }
        return Logged(tx, rewards = payForLog(tx))
    }

    /** Takes a settlement back, so the occurrence is upcoming, due or overdue again. */
    fun unsettle(billId: String, dueDay: LocalDate) = update { d -> d.copy(billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay }) }

    // --- credit lines, pay later and installment plans (MINT.md 5.6A) -----------------------------------------------------------

    /** Every plan as it stands today, derived from the ledger. */
    fun planViews(): List<PlanView> = Installments.views(data, today)

    fun planView(planId: String): PlanView? = data.plans.firstOrNull { it.id == planId }?.let { Installments.view(data, it, today) }

    private fun atMillis(day: LocalDate): Long = if (day == today) nowMillis else day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun creditAccount(id: String, allowInactive: Boolean = false): FinancialAccount? =
        data.accounts.firstOrNull { it.id == id }?.takeIf { Installments.allowedFor(it.type) && (allowInactive || it.status == AccountStatus.ACTIVE) }

    private fun checkPlanTransfer(data: MintData, tx: Transaction, destinationId: String): Check {
        val paymentData = data.copy(accounts = data.accounts.map { if (it.id == destinationId) it.copy(status = AccountStatus.ACTIVE) else it })
        return MintRules.checkTransaction(paymentData, tx)
    }

    /**
     * A purchase paid later (MINT.md 5.6A): writes **one** expense at the full [totalMinor] on the credit account, on [day], and the plan with its generated schedule,
     * in one save. The outstanding rises by the price, the purchase counts in spending once, and nothing is repaid yet. The fee of each installment is recorded
     * later, when it is billed or reached by a payment. A limit is information only, so a purchase beyond it is still recorded. A null [firstDue] is worked out
     * from the account's statement and due days ([MonthlyBills.firstDue]) so the purchase lands on the account's next bill without the player typing a date.
     */
    fun addPlanPurchase(
        accountId: String, name: String, totalMinor: Long, categoryId: String?, count: Int, every: PlanEvery, firstDue: LocalDate?, fee: FeeRule = FeeRule.None,
        feeKind: FeeKind = FeeKind.FEE, day: LocalDate = today,
    ): Logged<InstallmentPlan> {
        val account = creditAccount(accountId) ?: return Logged(null, "Choose a credit card or pay later account that is open.")
        if (name.isBlank()) return Logged(null, "Give the purchase a name.")
        if (day > today) return Logged(null, "A purchase cannot be dated in the future.")
        if (feeKind == FeeKind.LATE) return Logged(null, "Choose fee or interest.")
        (Installments.checkRequest(account.type, totalMinor, count, fee) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val txId = newId()
        val tx = Transaction(txId, TxType.EXPENSE, totalMinor, account.currency, atMillis(day), day, account.id, categoryId = categoryId, payee = name.trim().take(MintRules.NAME_MAX * 2), createdMillis = nowMillis)
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val planId = newId()
        val first = firstDue ?: MonthlyBills.firstDue(account, day, every)
        val plan = InstallmentPlan(
            planId, account.id, name.trim().take(MintRules.NAME_MAX), account.currency, txId, totalMinor, day, every, feeKind,
            Installments.generate(totalMinor, count, every, first, fee), nowMillis, feeRule = fee,
        )
        update { it.copy(transactions = it.transactions + tx.copy(planId = planId), plans = it.plans + plan) }
        return Logged(plan, rewards = payForLog(tx))
    }

    /**
     * Installments already owed (MINT.md 5.6A): a plan with no purchase record for a schedule that was running before tracking began. [rows] are the upcoming
     * installments (due day, amount still owed, optional fee, in due-day order). Nothing is recorded as spending. With [addToStartingBalance] the account's
     * starting balance owed (opening balance) rises by what the schedule asks, so the outstanding equals it; leave it off when the opening balance already holds that.
     */
    fun addExistingPlan(accountId: String, name: String, rows: List<app.pokeplan.mint.Installment>, addToStartingBalance: Boolean = true, feeKind: FeeKind = FeeKind.FEE): Logged<InstallmentPlan> {
        val account = creditAccount(accountId) ?: return Logged(null, "Choose a credit card or pay later account that is open.")
        if (name.isBlank()) return Logged(null, "Give the installments a name.")
        if (feeKind == FeeKind.LATE) return Logged(null, "Choose fee or interest.")
        (Installments.checkExisting(account.type, rows) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val total = rows.sumOf { it.principalMinor }
        val opening = if (addToStartingBalance) account.openingMinor + total else account.openingMinor
        if (opening > app.pokeplan.mint.Money.MAX_MINOR) return Logged(null, "That total looks wrong.")
        val numbered = rows.mapIndexed { i, r -> r.copy(no = i + 1) }
        val plan = InstallmentPlan(
            newId(), account.id, name.trim().take(MintRules.NAME_MAX), account.currency, null, total, numbered.first().dueDay,
            PlanEvery.MONTH, feeKind, numbered, nowMillis, openingBalanceIncluded = addToStartingBalance,
        )
        update { d -> d.copy(plans = d.plans + plan, accounts = d.accounts.map { if (it.id == account.id) it.copy(openingMinor = opening) else it }) }
        return Logged(plan)
    }

    /**
     * A repayment toward a plan (MINT.md 5.6A): one **transfer** from [fromAccountId] (the payment source) to the plan's account, so cash and the outstanding fall
     * together and nothing is spent. Any amount up to what is still owed (a partial or an early payment); it is applied to the earliest installment first. Every
     * installment it reaches whose fee is not yet recorded gets that fee recorded, dated [day], so the outstanding carries it before it is paid off.
     */
    fun payPlan(planId: String, amountMinor: Long, fromAccountId: String, day: LocalDate = today): Logged<Transaction> {
        val view = planView(planId) ?: return Logged(null, "That plan is gone.")
        val plan = view.plan
        val to = creditAccount(plan.accountId, allowInactive = true) ?: return Logged(null, "That account is not a credit line.")
        val from = data.accounts.firstOrNull { it.id == fromAccountId } ?: return Logged(null, "Choose where the money comes from.")
        if (from.type.liability) return Logged(null, "Pay from a bank, wallet or cash account.")
        if (day > today) return Logged(null, "A payment cannot be dated in the future.")
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (amountMinor > view.remainingMinor) return Logged(null, "That is more than is left on this plan.")
        val tx = Transaction(newId(), TxType.TRANSFER, amountMinor, from.currency, atMillis(day), day, from.id, toAccountId = to.id, createdMillis = nowMillis, planId = planId)
        (checkPlanTransfer(data, tx, to.id) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val after = Installments.view(data.copy(transactions = data.transactions + tx), plan, today)
        val fees = after.installments.filter { it.paidMinor > 0 && it.feeMinor > 0 && !it.feeRecorded }.map { feeRecord(plan, it.no, it.feeMinor, day, plan.feeKind) }
        update { it.copy(transactions = it.transactions + tx + fees) }
        return Logged(tx, rewards = payForLog(tx))
    }

    /** The bills of a credit account that still have something owed, earliest month first (MINT.md 5.6C). */
    fun openBills(accountId: String): List<MonthlyBill> = MonthlyBills.open(data, accountId, today)

    /** Every bill of a credit account, paid months too. */
    fun bills(accountId: String): List<MonthlyBill> = MonthlyBills.of(data, accountId, today)

    /**
     * A repayment of one monthly bill (MINT.md 5.6C): a transfer to the account allocated to that month's installments first; anything beyond the bill spills
     * forward into the next bills, and anything beyond every scheduled balance is an ordinary repayment.
     */
    fun payBill(accountId: String, month: BillMonth, amountMinor: Long, fromAccountId: String, day: LocalDate = today, feeMinor: Long = 0): Logged<Transaction> {
        if (openBills(accountId).none { it.month == month }) return Logged(null, "That bill has nothing left to pay.")
        return payPlans(accountId, amountMinor, fromAccountId, day, month, feeMinor)
    }

    /**
     * A repayment is allocated installment by installment in due-date order across every unpaid plan on the credit account, so it settles the earliest bill first
     * (or [firstBill] first), never one purchase's whole schedule before another purchase's earlier month.
     */
    fun payPlans(accountId: String, amountMinor: Long, fromAccountId: String, day: LocalDate = today, firstBill: BillMonth? = null, feeMinor: Long = 0): Logged<Transaction> {
        val to = creditAccount(accountId, allowInactive = true) ?: return Logged(null, "That account is not a credit line.")
        val from = data.accounts.firstOrNull { it.id == fromAccountId } ?: return Logged(null, "Choose where the money comes from.")
        if (from.type.liability) return Logged(null, "Pay from a bank, wallet or cash account.")
        if (day > today) return Logged(null, "A payment cannot be dated in the future.")
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val allocations = Installments.allocatePayment(data, accountId, amountMinor, today, firstBill)
        if (allocations.isEmpty()) return Logged(null, "There are no unpaid plans on this account.")
        val tx = Transaction(
            id = newId(), type = TxType.TRANSFER, amountMinor = amountMinor, currency = from.currency, atMillis = atMillis(day), day = day,
            accountId = from.id, toAccountId = to.id, createdMillis = nowMillis, planAllocations = allocations, feeMinor = feeMinor,
        )
        (checkPlanTransfer(data, tx, to.id) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val withPayment = data.copy(transactions = data.transactions + tx)
        val fees = allocations.map { it.planId }.distinct().flatMap { planId ->
            val plan = data.plans.first { it.id == planId }
            Installments.view(withPayment, plan, today).installments
                .filter { it.paidMinor > 0 && it.feeMinor > 0 && !it.feeRecorded }
                .map { feeRecord(plan, it.no, it.feeMinor, day, plan.feeKind) }
        }
        update { it.copy(transactions = it.transactions + tx + fees) }
        return Logged(tx, rewards = payForLog(tx))
    }

    private fun feeRecord(plan: InstallmentPlan, no: Int, amount: Long, day: LocalDate, kind: FeeKind): Transaction =
        Transaction(newId(), TxType.FEE, amount, plan.currency, atMillis(day), day, plan.accountId, payee = plan.name.take(MintRules.NAME_MAX * 2), createdMillis = nowMillis, planId = plan.id, planNo = no, feeKind = kind)

    /** "Bill it": records installment [no]'s fee (or interest) as a `FEE` record on [day] without a payment. Refused when it has no fee or is already recorded. */
    fun billInstallmentFee(planId: String, no: Int, day: LocalDate = today): Logged<Transaction> {
        val view = planView(planId) ?: return Logged(null, "That plan is gone.")
        val inst = view.installments.firstOrNull { it.no == no } ?: return Logged(null, "That installment is gone.")
        if (creditAccount(view.plan.accountId) == null) return Logged(null, "That account is not open.")
        if (day > today) return Logged(null, "A fee cannot be dated in the future.")
        if (inst.feeMinor <= 0) return Logged(null, "That installment has no fee.")
        if (inst.feeRecorded) return Logged(null, "That fee is already recorded.")
        val tx = feeRecord(view.plan, no, inst.feeMinor, day, view.plan.feeKind)
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx)
    }

    /** A late fee for installment [no] (MINT.md 5.6A): a `FEE` record of kind late on the credit account; it raises the outstanding and is owed with that installment. */
    fun addLateFee(planId: String, no: Int, amountMinor: Long, day: LocalDate = today): Logged<Transaction> {
        val view = planView(planId) ?: return Logged(null, "That plan is gone.")
        if (view.installments.none { it.no == no }) return Logged(null, "That installment is gone.")
        if (creditAccount(view.plan.accountId) == null) return Logged(null, "That account is not open.")
        if (day > today) return Logged(null, "A fee cannot be dated in the future.")
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val tx = feeRecord(view.plan, no, amountMinor, day, FeeKind.LATE)
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx)
    }

    /**
     * A refund on a plan's purchase (MINT.md 5.6A): one `REFUND` on the credit account, linked to the purchase, which lowers the outstanding and spending; it
     * cancels the unpaid installments from the last one backwards. At most the price less what was refunded already.
     */
    fun refundPlan(planId: String, amountMinor: Long, day: LocalDate = today): Logged<Transaction> {
        val view = planView(planId) ?: return Logged(null, "That plan is gone.")
        val plan = view.plan
        if (creditAccount(plan.accountId) == null) return Logged(null, "That account is not open.")
        if (day > today) return Logged(null, "A refund cannot be dated in the future.")
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (amountMinor > plan.totalMinor - view.refundedMinor) return Logged(null, "That is more than the purchase less what was refunded.")
        val purchase = plan.purchaseTxId?.let { id -> data.transactions.firstOrNull { it.id == id } }
        val tx = Transaction(newId(), TxType.REFUND, amountMinor, plan.currency, atMillis(day), day, plan.accountId, categoryId = purchase?.categoryId, payee = plan.name.take(MintRules.NAME_MAX * 2), refundOf = plan.purchaseTxId, createdMillis = nowMillis, planId = planId)
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx)
    }

    /**
     * Changes the due day or fee of an installment that is unpaid and whose fee is not recorded yet (MINT.md 5.6A); nothing that happened is rewritten. The
     * installments stay in order of due day.
     */
    fun updateInstallment(planId: String, no: Int, dueDay: LocalDate, feeMinor: Long): Boolean {
        val installment = planView(planId)?.installments?.firstOrNull { it.no == no } ?: return false
        return updateInstallment(planId, no, dueDay, installment.principalMinor, feeMinor)
    }

    fun updateInstallment(planId: String, no: Int, dueDay: LocalDate, principalMinor: Long, feeMinor: Long): Boolean {
        val view = planView(planId) ?: return false
        val inst = view.installments.firstOrNull { it.no == no } ?: return false
        if (inst.paidMinor > 0 || inst.feeRecorded || inst.lateMinor > 0 || view.refundedMinor > 0 ||
            principalMinor < 0 || principalMinor > app.pokeplan.mint.Money.MAX_MINOR ||
            feeMinor < 0 || feeMinor > app.pokeplan.mint.Money.MAX_MINOR) return false
        val ordered = view.plan.installments.map { if (it.no == no) it.copy(dueDay = dueDay, principalMinor = principalMinor, feeMinor = feeMinor) else it }
        if (ordered.zipWithNext().any { (a, b) -> a.dueDay > b.dueDay }) return false
        val total = ordered.sumOf { it.principalMinor }
        if (total <= 0 || total > app.pokeplan.mint.Money.MAX_MINOR) return false
        val plan = view.plan.copy(totalMinor = total, installments = ordered, feeRule = null)
        val purchase = plan.purchaseTxId?.let { id -> data.transactions.firstOrNull { it.id == id } }
        val account = data.accounts.firstOrNull { it.id == plan.accountId } ?: return false
        val opening = if (plan.openingBalanceIncluded) account.openingMinor + total - view.plan.totalMinor else account.openingMinor
        if (opening !in 0..app.pokeplan.mint.Money.MAX_MINOR) return false
        update { d ->
            d.copy(
                plans = d.plans.map { if (it.id == planId) plan else it },
                transactions = d.transactions.map { tx -> if (tx.id == purchase?.id) tx.copy(amountMinor = total) else tx },
                accounts = d.accounts.map { a -> if (a.id == account.id) a.copy(openingMinor = opening) else a },
            )
        }
        return true
    }

    /** The plans of [accountId] with their unpaid monthly installments moved onto the account's due day, and how many moved (see [alignInstallmentsToDueDay]). */
    private fun alignment(accountId: String): Pair<Map<String, InstallmentPlan>, Int> {
        val account = creditAccount(accountId, allowInactive = true)?.takeIf { it.dueDay in 1..31 } ?: return emptyMap<String, InstallmentPlan>() to 0
        var moved = 0
        val changed = planViews().filter { it.plan.accountId == accountId && it.plan.every == PlanEvery.MONTH }.mapNotNull { v ->
            val next = v.plan.installments.map { inst ->
                val view = v.installments.first { it.no == inst.no }
                val free = view.paidMinor == 0L && !view.feeRecorded && view.lateMinor == 0L
                if (free) inst.copy(dueDay = MonthlyBills.onDueDay(account, inst.dueDay)) else inst
            }
            val count = next.indices.count { next[it].dueDay != v.plan.installments[it].dueDay }
            if (count == 0 || next.zipWithNext().any { (a, b) -> a.dueDay > b.dueDay }) null else { moved += count; v.plan.copy(installments = next) }
        }.associateBy { it.id }
        return changed to moved
    }

    /** How many unpaid monthly installments are not on the account's due day. */
    fun misalignedInstallments(accountId: String): Int = alignment(accountId).second

    /**
     * Moves the unpaid installments of the account's monthly plans onto the account's due day (MINT.md 5.6C), so one bill has one due day: the player sets that day
     * once on the account and does not edit each purchase. A plan is left alone when an installment already paid, billed or fined in that month would end up after
     * a moved one. Returns how many installments moved.
     */
    fun alignInstallmentsToDueDay(accountId: String): Int {
        val (changed, moved) = alignment(accountId)
        if (changed.isNotEmpty()) update { d -> d.copy(plans = d.plans.map { changed[it.id] ?: it }) }
        return moved
    }

    fun updatePlanName(planId: String, name: String): Logged<InstallmentPlan> {
        val plan = data.plans.firstOrNull { it.id == planId } ?: return Logged(null, "That plan is gone.")
        val clean = name.trim().take(MintRules.NAME_MAX)
        if (clean.isBlank()) return Logged(null, "Give the purchase or schedule a name.")
        val updated = plan.copy(name = clean)
        update { d ->
            d.copy(
                plans = d.plans.map { if (it.id == planId) updated else it },
                transactions = d.transactions.map { tx -> if (tx.id == plan.purchaseTxId) tx.copy(payee = clean) else tx },
            )
        }
        return Logged(updated)
    }

    fun canEditPlanTerms(planId: String): Boolean {
        val plan = data.plans.firstOrNull { it.id == planId } ?: return false
        return data.transactions.none { it.planId == planId && it.id != plan.purchaseTxId }
    }

    fun updatePlanTerms(
        planId: String, name: String, totalMinor: Long, count: Int, every: PlanEvery, firstDue: LocalDate, fee: FeeRule, feeKind: FeeKind,
    ): Logged<InstallmentPlan> {
        val old = data.plans.firstOrNull { it.id == planId } ?: return Logged(null, "That plan is gone.")
        if (!canEditPlanTerms(planId)) return Logged(null, "A payment, fee or refund has already changed this plan.")
        val account = creditAccount(old.accountId) ?: return Logged(null, "That account is not open.")
        val cleanName = name.trim().take(MintRules.NAME_MAX)
        if (cleanName.isBlank()) return Logged(null, "Give the purchase or schedule a name.")
        if (feeKind == FeeKind.LATE) return Logged(null, "Choose fee or interest.")
        (Installments.checkRequest(account.type, totalMinor, count, fee) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val installments = Installments.generate(totalMinor, count, every, firstDue, fee)
        val opening = if (old.openingBalanceIncluded) account.openingMinor + totalMinor - old.totalMinor else account.openingMinor
        if (opening !in 0..app.pokeplan.mint.Money.MAX_MINOR) return Logged(null, "That opening balance looks wrong.")
        val updated = old.copy(name = cleanName, totalMinor = totalMinor, startDay = firstDue, every = every, feeKind = feeKind, installments = installments, feeRule = fee)
        val purchase = old.purchaseTxId?.let { id -> data.transactions.firstOrNull { it.id == id } }
        if (old.purchaseTxId != null && purchase == null) return Logged(null, "The purchase record is missing.")
        val changedPurchase = purchase?.copy(amountMinor = totalMinor, payee = cleanName)
        if (changedPurchase != null) (MintRules.checkTransaction(data, changedPurchase) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d ->
            d.copy(
                plans = d.plans.map { if (it.id == planId) updated else it },
                transactions = if (changedPurchase == null) d.transactions else d.transactions.map { if (it.id == changedPurchase.id) changedPurchase else it },
                accounts = d.accounts.map { a -> if (a.id == account.id) a.copy(openingMinor = opening) else a },
            )
        }
        return Logged(updated)
    }

    /** Removes the plan and leaves every record it made (the purchase, fees, repayments, refunds) in place, unlinked; they are ordinary records again. */
    fun deletePlan(planId: String) = update { d ->
        d.copy(
            plans = d.plans.filterNot { it.id == planId },
            transactions = d.transactions.map { transaction ->
                val unlinked = if (transaction.planId == planId) transaction.copy(planId = null, planNo = null) else transaction
                unlinked.copy(planAllocations = unlinked.planAllocations.filterNot { it.planId == planId })
            },
        )
    }

    /** What an account did over a period: purchases, fees, interest, late fees, refunds and repayments (MINT.md 5.6A). */
    fun creditActivity(accountId: String, from: LocalDate, endExclusive: LocalDate): CreditActivity = Installments.activity(data, accountId, from, endExclusive)

    fun recordCreditStatement(
        accountId: String, statementDay: LocalDate, statementBalanceMinor: Long, minimumPaymentMinor: Long, dueDay: LocalDate,
    ): Logged<CreditStatement> {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "That account is gone.")
        if (account.status != AccountStatus.ACTIVE) return Logged(null, "That account is not open.")
        if (!Installments.allowedFor(account.type)) return Logged(null, "Choose a credit card or pay-later account.")
        if (statementDay > today) return Logged(null, "A statement date cannot be in the future.")
        if (dueDay <= statementDay) return Logged(null, "The payment due date must be after the statement date.")
        if (statementBalanceMinor !in 0..app.pokeplan.mint.Money.MAX_MINOR || minimumPaymentMinor !in 0..app.pokeplan.mint.Money.MAX_MINOR)
            return Logged(null, "Those statement amounts look wrong.")
        if (minimumPaymentMinor > statementBalanceMinor) return Logged(null, "The minimum payment cannot be more than the statement balance.")
        if (data.creditStatements.any { it.accountId == accountId && it.statementDay == statementDay })
            return Logged(null, "A statement is already recorded for that date.")

        val previous = data.creditStatements.filter { it.accountId == accountId && it.statementDay < statementDay }.maxByOrNull { it.statementDay }
        val interestStart = previous?.dueDay?.plus(DatePeriod(days = account.interestGraceDays + 1))
        val interestMinor = if (interestStart != null && interestStart <= statementDay)
            MintRules.simpleInterestEstimate(data, account, interestStart, statementDay)
        else 0L
        val statementId = newId()
        val interestId = newId().takeIf { interestMinor > 0 }
        val statement = CreditStatement(
            statementId, accountId, statementDay, statementBalanceMinor, minimumPaymentMinor, dueDay, interestId, nowMillis,
        )
        val interest = interestId?.let {
            Transaction(
                id = it, type = TxType.FEE, amountMinor = interestMinor, currency = account.currency,
                atMillis = noonOf(statementDay), day = statementDay, accountId = accountId, payee = "Estimated interest",
                note = "Simple daily APR/365 estimate; not a lender quote.", createdMillis = nowMillis,
                feeKind = FeeKind.INTEREST, creditStatementId = statementId,
            )
        }
        val withStatement = data.copy(creditStatements = data.creditStatements + statement)
        interest?.let { tx -> (MintRules.checkTransaction(withStatement, tx) as? Check.Refused)?.let { return Logged(null, it.reason) } }
        update { d ->
            d.copy(
                creditStatements = d.creditStatements + statement,
                transactions = if (interest == null) d.transactions else d.transactions + interest,
            )
        }
        return Logged(statement)
    }

    // --- reconciliation and review (the only things that can pay) -----------------------------------------------------------

    /** What Mint's records say the account holds now, for the reconcile screen to set beside the statement. */
    fun bookBalance(accountId: String): Long? = data.accounts.firstOrNull { it.id == accountId }?.let { MintRules.balance(data, it).postedMinor }

    /**
     * Reconciles an account with a [statementMinor] the user read off a statement or an app. A match is recorded as it is. A difference is recorded
     * only when the user accepts it ([acceptAdjustment]), as an explicit adjustment record (MINT.md section 15.2, item 8); otherwise nothing is saved.
     * It is paid by Ledger Research when that is on and the account has real history; the same account pays once a day.
     */
    fun reconcile(accountId: String, statementMinor: Long, acceptAdjustment: Boolean = false): Logged<Reconciliation> {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "That account is gone.")
        if (account.status != AccountStatus.ACTIVE) return Logged(null, "That account is not open.")
        if (statementMinor < 0 || statementMinor > app.pokeplan.mint.Money.MAX_MINOR) return Logged(null, "That amount looks wrong.")
        val book = MintRules.balance(data, account).postedMinor
        val diff = MintRules.reconcileDifference(statementMinor, book)
        if (diff != 0L && !acceptAdjustment) return Logged(null, "The statement and Mint differ. Accept the difference to record it as an adjustment.")
        var adjustmentId: String? = null
        if (diff != 0L) {
            val adj = Transaction(newId(), TxType.ADJUSTMENT, kotlin.math.abs(diff), account.currency, nowMillis, today, account.id, source = TxSource.ADJUSTMENT, up = diff > 0, createdMillis = nowMillis)
            adjustmentId = adj.id
            update { it.copy(transactions = it.transactions + adj) }
        }
        val record = Reconciliation(newId(), account.id, today, statementMinor, book, adjustmentId, nowMillis)
        update { it.copy(reconciliations = it.reconciliations + record) }
        val pays = MintRules.reconcilePays(data.copy(reconciliations = data.reconciliations.filterNot { it.id == record.id }), account.id, today)
        val rewards = if (pays) pay(MintKind.RECONCILED, record.id) else null
        if (rewards != null) update { d -> d.copy(reconciliations = d.reconciliations.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.reconciliations.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    /** Finishes the periodic review: one record with no figure in it. Paid once a week when Ledger Research is on and there is something to review. */
    fun finishReview(): Logged<ReviewRecord> {
        val record = ReviewRecord(newId(), today, nowMillis)
        val pays = MintRules.reviewPays(data, today, weekStart())
        update { it.copy(reviews = it.reviews + record) }
        val rewards = if (pays) pay(MintKind.REVIEW, record.id, reviewStreak()) else null
        if (rewards != null) update { d -> d.copy(reviews = d.reviews.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.reviews.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    // --- settings ------------------------------------------------------------------------------------------------------

    private fun settings(change: (MintSettings) -> MintSettings) = update { it.copy(settings = change(it.settings)) }

    /** Turns Ledger Research (the reward) on or off. Off never touches a record and never hides a feature. */
    fun setProgression(on: Boolean) = settings { it.copy(progression = on) }

    /** Hides every amount across Mint (MINT.md section 3.3). */
    fun setHideValues(on: Boolean) = settings { it.copy(hideValues = on) }

    /** How far ahead the Overview and the widgets look for what is due (MINT.md 3.2B). */
    fun setOverviewWindow(window: app.pokeplan.mint.OverviewWindow) = settings { it.copy(overviewWindow = window) }

    /** How much of a Pay later account the Overview's net position counts (MINT.md 3.2B). */
    fun setOverviewPayLater(count: app.pokeplan.mint.PayLaterCount) = settings { it.copy(overviewPayLater = count) }

    /** Turns bill reminders on or off (MINT.md section 12). Off by default; turning them off never touches a bill. */
    fun setBillReminders(on: Boolean) = settings { it.copy(billReminders = on) }

    /** How many days before the due day a reminder is for, 0 to [MAX_REMIND_DAYS]. */
    fun setBillReminderDays(days: Int) = settings { it.copy(billReminderDays = days.coerceIn(0, MAX_REMIND_DAYS)) }

    /** The hour a reminder goes off, 0 to 23. */
    fun setBillReminderHour(hour: Int) = settings { it.copy(billReminderHour = hour.coerceIn(0, 23)) }

    /** Whether a reminder names its bill (never an amount). */
    fun setBillReminderNames(on: Boolean) = settings { it.copy(billReminderNames = on) }

    /** Lets one bill ask for a reminder, or not. */
    fun setBillRemind(billId: String, on: Boolean) = update { d -> d.copy(bills = d.bills.map { if (it.id == billId) it.copy(remind = on) else it }) }

    /** Turns the optional access gate on or off. The caller has already proved the phone can ask for its screen lock; the gate holds no key and no password. */
    fun setGate(on: Boolean) = settings { it.copy(gate = on) }

    /** Whether the built-in Philippine holidays count as days off for paydays (MINT.md 8.3B). */
    fun setBuiltInHolidays(on: Boolean) = settings { it.copy(builtInHolidays = on) }

    /** Counts or stops counting one built-in holiday (for example one that was not observed that year). */
    fun setHolidayCounted(day: LocalDate, counted: Boolean) = settings { it.copy(holidaysIgnored = if (counted) it.holidaysIgnored - day else (it.holidaysIgnored + day).distinct().sorted()) }

    /** Adds or removes a day off of the player's own (Eid, a proclaimed day); at most 200. */
    fun setDayOff(day: LocalDate, off: Boolean) = settings { it.copy(daysOff = if (off) (it.daysOff + day).distinct().sorted().takeLast(200) else it.daysOff - day) }

    fun setCurrency(code: String) = settings { if (MintRules.checkCurrency(code.trim().uppercase()) is Check.Ok) it.copy(currency = code.trim().uppercase()) else it }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    // --- export and erase (one kind at a time, or all) ----------------------------------------------------------------------

    /** The chosen kinds of record (all by default) as readable JSON, exactly as saved, amounts in minor units with their currency. Nothing of the game is in it. */
    fun export(kinds: Set<MintKinds> = MintKinds.entries.toSet()): String = MintExport.json(data, kinds)

    /**
     * Erases every record of [kind]. Erasing accounts takes the records that point at them with it (transactions, reconciliations); nothing else is
     * touched: other kinds, the settings, Pokémon, Poké, the Bag and the other modules stay.
     */
    fun erase(kind: MintKinds) = update { MintExport.without(it, kind) }

    /** Erases all of MINT's records (not its settings, not what Ledger Research paid). Categories come back as the starting set. */
    fun eraseAll() = update { d -> tidy(MintKinds.entries.fold(d) { acc, k -> MintExport.without(acc, k) }) }

    /** The full-device reset: MINT as new, its records and its settings (the starting categories come back). */
    fun resetAll() = update { tidy(MintData()) }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        /** The furthest ahead of its due day a bill reminder may be set (a week). */
        const val MAX_REMIND_DAYS = 7

        /** A document with no categories gets the starting set, with stable ids, so a fresh install can record an expense at once. */
        internal fun tidy(d: MintData): MintData = if (d.categories.isEmpty()) d.copy(categories = MintRules.seedCategories()) else d
    }
}

/** Export and erase by kind, pure and free of the game: one place that knows which fields of the document each kind is. */
object MintExport {
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    private val fields: Map<MintKinds, List<String>> = mapOf(
        MintKinds.ACCOUNTS to listOf("accounts", "plans", "creditStatements"),
        MintKinds.CATEGORIES to listOf("categories"),
        MintKinds.TRANSACTIONS to listOf("transactions", "creditStatements"),
        MintKinds.BUDGETS to listOf("budgets", "savingsTargets"),
        MintKinds.BILLS to listOf("bills", "billPayments"),
        MintKinds.REVIEWS to listOf("reconciliations", "reviews"),
    )

    fun json(data: MintData, kinds: Set<MintKinds>): String {
        val all = MintStore.json.encodeToJsonElement(MintData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> fields.any { (kind, names) -> kind in kinds && key in names } }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to JsonPrimitive("pokelife-mint-export"), "unit" to JsonPrimitive("amounts are integer minor units of the currency beside them")) + chosen))
    }

    fun without(d: MintData, kind: MintKinds): MintData = when (kind) {
        MintKinds.ACCOUNTS -> d.copy(
            accounts = emptyList(), plans = emptyList(), creditStatements = emptyList(), transactions = emptyList(), reconciliations = emptyList(),
            bills = d.bills.map { it.copy(accountId = null) },
            savingsTargets = d.savingsTargets.map { it.copy(accountIds = emptyList()) },
        )
        MintKinds.CATEGORIES -> d.copy(
            categories = emptyList(),
            transactions = d.transactions.map { it.copy(categoryId = null, splits = it.splits.map { split -> split.copy(categoryId = null) }) },
            budgets = d.budgets.filter { it.categoryId == null },
            bills = d.bills.map { it.copy(categoryId = null) },
        )
        MintKinds.TRANSACTIONS -> d.copy(
            transactions = emptyList(), plans = emptyList(),
            creditStatements = d.creditStatements.map { it.copy(estimatedInterestTxId = null) },
            reconciliations = d.reconciliations.map { it.copy(adjustmentTxId = null) },
            billPayments = d.billPayments.map { it.copy(txId = null) },
        )
        MintKinds.BUDGETS -> d.copy(budgets = emptyList(), savingsTargets = emptyList())
        MintKinds.BILLS -> d.copy(bills = emptyList(), billPayments = emptyList(), transactions = d.transactions.map { it.copy(billId = null, billDueDay = null) })
        MintKinds.REVIEWS -> d.copy(reconciliations = emptyList(), reviews = emptyList())
    }
}
/** What reading a statement file gave: the table, the columns and date order in use (guessed, or chosen), whether the order is certain, and the preview; or [refused], in words. */
data class StatementPreview(
    val table: List<List<String>> = emptyList(),
    val columns: StatementImport.Columns = StatementImport.Columns(),
    val order: StatementImport.DateOrder = StatementImport.DateOrder.DAY_FIRST,
    val orderCertain: Boolean = true,
    val preview: StatementImport.Preview? = null,
    val refused: String? = null,
)
