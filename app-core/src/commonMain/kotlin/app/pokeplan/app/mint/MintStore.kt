package app.pokeplan.app.mint

import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.Bill
import app.pokeplan.mint.BillPayment
import app.pokeplan.mint.Budget
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.Category
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.CardStyle
import app.pokeplan.mint.Check
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.MintData
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.MintSettings
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.Reconciliation
import app.pokeplan.mint.ReviewRecord
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.StatementImport
import app.pokeplan.mint.Transaction
import app.pokeplan.mint.TxSource
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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

    private fun pay(kind: MintKind, recordId: String): List<app.pokeplan.core.reward.Reward>? =
        if (!data.settings.progression) null else world.mint(MintEvent("mint:${kind.name.lowercase()}:$recordId", kind, nowMillis, recordId))

    /** Every amount on a screen goes through this, so hiding values (MINT.md section 3.3) is one switch and covers all of Mint. */
    fun show(minor: Long, currency: String): String = app.pokeplan.mint.Money.format(minor, currency, data.settings.hideValues)

    // --- accounts ----------------------------------------------------------------------------------------------------

    fun addAccount(name: String, type: AccountType, currency: String, openingMinor: Long = 0, institution: String = "", suffix: String = "", group: String = "", card: String = ""): Logged<FinancialAccount> {
        val code = currency.trim().uppercase()
        (MintRules.checkAccount(name, code, openingMinor, suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val account = FinancialAccount(newId(), name.trim(), type, code, openingMinor, today, institution.trim().take(MintRules.NAME_MAX), suffix.trim(), group.trim().take(MintRules.NAME_MAX), createdMillis = nowMillis, card = CardStyle.of(card).id)
        update { it.copy(accounts = it.accounts + account) }
        return Logged(account)
    }

    /** Whether [id] has records that depend on its currency: a transaction, a reconciliation or a bill paid from it. */
    fun hasHistory(id: String): Boolean =
        data.transactions.any { it.accountId == id || it.toAccountId == id } || data.reconciliations.any { it.accountId == id } || data.bills.any { it.accountId == id }

    /**
     * Edits an account: its name, type (only to another type on the same side, an asset stays an asset and a liability a liability), institution, group, last
     * digits and **opening balance** (a mistake made when the account was added; the balance is derived, so the shown balance moves by the difference and no
     * record is rewritten). The **currency** can change only while the account has no history ([hasHistory]); with history it stays, because every record and
     * bill linked to it is in that currency (MINT.md section 5.2). Nothing is paid, and nothing about the edit leaves Mint.
     */
    fun updateAccount(a: FinancialAccount): Logged<FinancialAccount> {
        val old = data.accounts.firstOrNull { it.id == a.id } ?: return Logged(null, "That account is gone.")
        val currency = if (a.currency == old.currency || hasHistory(old.id)) old.currency else a.currency.trim().uppercase()
        (MintRules.checkAccount(a.name, currency, a.openingMinor, a.suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(
            name = a.name.trim(), institution = a.institution.trim().take(MintRules.NAME_MAX), suffix = a.suffix.trim(), group = a.group.trim().take(MintRules.NAME_MAX),
            type = if (a.type.liability == old.type.liability) a.type else old.type, openingMinor = a.openingMinor, currency = currency, card = CardStyle.of(a.card).id,
        )
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == a.id) next else it }) }
        return Logged(next)
    }

    /** Active, archived (hidden from default views, history kept) or closed (takes no new records). Never touches a transaction. */
    fun setAccountStatus(id: String, status: AccountStatus) =
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(status = status) else it }) }

    /** Deletes an account with no history; one with history is refused and should be archived instead (MINT.md section 4.6). Returns the reason, or null when deleted. */
    fun deleteAccount(id: String): String? {
        if (data.transactions.any { it.accountId == id || it.toAccountId == id } || data.reconciliations.any { it.accountId == id })
            return "This account has history. Archive it instead, or erase its transactions first."
        update { d -> d.copy(accounts = d.accounts.filterNot { it.id == id }, bills = d.bills.map { if (it.accountId == id) it.copy(accountId = null) else it }) }
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
        if (data.transactions.any { it.categoryId == id } || data.budgets.any { it.categoryId == id } || data.bills.any { it.categoryId == id })
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
        atMillis: Long = nowMillis, status: TxStatus = TxStatus.POSTED, up: Boolean = true, refundOf: String? = null,
    ): Logged<Transaction> {
        val currency = data.accounts.firstOrNull { it.id == accountId }?.currency ?: return Logged(null, "Choose an account.")
        val tx = Transaction(newId(), type, amountMinor, currency, atMillis, dayOf(atMillis), accountId, toAccountId, categoryId, payee.trim().take(MintRules.NAME_MAX * 2), note.trim().take(MintRules.NOTE_MAX), status, TxSource.MANUAL, up, refundOf, createdMillis = nowMillis)
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx)
    }

    /** Changes what was written down. The account's currency can not change under a record; a source of [TxSource.ADJUSTMENT] stays an adjustment. */
    fun updateTransaction(t: Transaction): Logged<Transaction> {
        val old = data.transactions.firstOrNull { it.id == t.id } ?: return Logged(null, "That record is gone.")
        val next = t.copy(source = old.source, createdMillis = old.createdMillis, importBatch = old.importBatch, importLabel = old.importLabel, fingerprint = old.fingerprint, day = dayOf(t.atMillis), payee = t.payee.trim().take(MintRules.NAME_MAX * 2), note = t.note.trim().take(MintRules.NOTE_MAX))
        // Editing a record on an account that was since closed is allowed to keep its history readable; only the amount rules apply there.
        (MintRules.checkAmount(next.amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val open = data.copy(accounts = data.accounts.map { it.copy(status = AccountStatus.ACTIVE) })
        (MintRules.checkTransaction(open, next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == t.id) next else it }) }
        return Logged(next)
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
    fun setTransactionStatus(id: String, status: TxStatus) = update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteTransaction(id: String) = update { d ->
        d.copy(
            transactions = d.transactions.filterNot { it.id == id },
            reconciliations = d.reconciliations.map { if (it.adjustmentTxId == id) it.copy(adjustmentTxId = null) else it },
            billPayments = d.billPayments.map { if (it.txId == id) it.copy(txId = null) else it },
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

    // --- bills -------------------------------------------------------------------------------------------------------

    fun addBill(name: String, currency: String, frequency: Frequency, firstDue: LocalDate, expectedMinor: Long? = null, categoryId: String? = null, accountId: String? = null): Logged<Bill> {
        if (name.isBlank()) return Logged(null, "Give the bill a name.")
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (expectedMinor != null) (MintRules.checkAmount(expectedMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val bill = Bill(newId(), name.trim().take(MintRules.NAME_MAX), currency, frequency, firstDue, expectedMinor, categoryId, accountId, createdMillis = nowMillis)
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
        val rescheduled = b.firstDue != old.firstDue || b.frequency != old.frequency
        if (rescheduled && data.billPayments.any { it.billId == old.id }) return Logged(null, "Payments are recorded for this bill, so its schedule cannot change. Pause it and add a new bill.")
        val next = old.copy(name = b.name.trim().take(MintRules.NAME_MAX), expectedMinor = b.expectedMinor, categoryId = b.categoryId, accountId = b.accountId, firstDue = b.firstDue, frequency = b.frequency)
        update { d -> d.copy(bills = d.bills.map { if (it.id == b.id) next else it }) }
        return Logged(next)
    }

    fun setBillStatus(id: String, status: PlanStatus) = update { d -> d.copy(bills = d.bills.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteBill(id: String) = update { d -> d.copy(bills = d.bills.filterNot { it.id == id }, billPayments = d.billPayments.filterNot { it.billId == id }) }

    /**
     * Settles one occurrence: paid, partly paid or skipped. **It creates no transaction** (MINT.md section 8.2); [txId] only links one the user chose.
     * Settling again replaces the earlier settlement, so a mistake can be put right. A part payment that covers the whole expected amount is saved as paid.
     */
    fun settle(billId: String, dueDay: LocalDate, status: SettleStatus, paidMinor: Long = 0, txId: String? = null, paidDay: LocalDate = today): Boolean {
        val bill = data.bills.firstOrNull { it.id == billId } ?: return false
        if (paidDay > today) return false
        if (status != SettleStatus.SKIPPED && MintRules.checkAmount(paidMinor) is Check.Refused) return false
        val amount = if (status == SettleStatus.SKIPPED) 0L else paidMinor
        val full = bill.expectedMinor
        val settled = if (status == SettleStatus.PARTIAL && full != null && amount >= full) SettleStatus.PAID else status
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
        val tx = Transaction(newId(), TxType.EXPENSE, paidMinor, account.currency, at, paidDay, account.id, categoryId = bill.categoryId, payee = bill.name.take(MintRules.NAME_MAX * 2), billId = bill.id, billDueDay = dueDay, createdMillis = nowMillis)
        if (account.currency != bill.currency) return Logged(null, "That account is in ${account.currency}, the bill in ${bill.currency}.")
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val full = bill.expectedMinor
        val payment = BillPayment(newId(), bill.id, dueDay, SettleStatus.PAID.takeIf { full == null || paidMinor >= full } ?: SettleStatus.PARTIAL, paidMinor, paidDay, tx.id, nowMillis)
        update { d -> d.copy(transactions = d.transactions + tx, billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay } + payment) }
        return Logged(tx)
    }

    /** Takes a settlement back, so the occurrence is upcoming, due or overdue again. */
    fun unsettle(billId: String, dueDay: LocalDate) = update { d -> d.copy(billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay }) }

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
        val rewards = if (pays) pay(MintKind.REVIEW, record.id) else null
        if (rewards != null) update { d -> d.copy(reviews = d.reviews.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.reviews.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    // --- settings ------------------------------------------------------------------------------------------------------

    private fun settings(change: (MintSettings) -> MintSettings) = update { it.copy(settings = change(it.settings)) }

    /** Turns Ledger Research (the reward) on or off. Off never touches a record and never hides a feature. */
    fun setProgression(on: Boolean) = settings { it.copy(progression = on) }

    /** Hides every amount across Mint (MINT.md section 3.3). */
    fun setHideValues(on: Boolean) = settings { it.copy(hideValues = on) }

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
        MintKinds.ACCOUNTS to listOf("accounts"),
        MintKinds.CATEGORIES to listOf("categories"),
        MintKinds.TRANSACTIONS to listOf("transactions"),
        MintKinds.BUDGETS to listOf("budgets"),
        MintKinds.BILLS to listOf("bills", "billPayments"),
        MintKinds.REVIEWS to listOf("reconciliations", "reviews"),
    )

    fun json(data: MintData, kinds: Set<MintKinds>): String {
        val all = MintStore.json.encodeToJsonElement(MintData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> fields.any { (kind, names) -> kind in kinds && key in names } }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to JsonPrimitive("pokelife-mint-export"), "unit" to JsonPrimitive("amounts are integer minor units of the currency beside them")) + chosen))
    }

    fun without(d: MintData, kind: MintKinds): MintData = when (kind) {
        MintKinds.ACCOUNTS -> d.copy(accounts = emptyList(), transactions = emptyList(), reconciliations = emptyList(), bills = d.bills.map { it.copy(accountId = null) })
        MintKinds.CATEGORIES -> d.copy(categories = emptyList(), transactions = d.transactions.map { it.copy(categoryId = null) }, budgets = d.budgets.filter { it.categoryId == null }, bills = d.bills.map { it.copy(categoryId = null) })
        MintKinds.TRANSACTIONS -> d.copy(transactions = emptyList(), reconciliations = d.reconciliations.map { it.copy(adjustmentTxId = null) }, billPayments = d.billPayments.map { it.copy(txId = null) })
        MintKinds.BUDGETS -> d.copy(budgets = emptyList())
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
