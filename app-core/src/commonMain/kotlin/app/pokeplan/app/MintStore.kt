package app.pokeplan.app

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
import app.pokeplan.mint.Transaction
import app.pokeplan.mint.TxSource
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

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

    private fun update(change: (MintData) -> MintData) = document.write(change(data))

    private fun newId(): String = newId(nowMillis, rng)

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: MintKind, recordId: String): List<app.pokeplan.core.reward.Reward>? =
        if (!data.settings.progression) null else world.mint(MintEvent("mint:${kind.name.lowercase()}:$recordId", kind, nowMillis, recordId))

    /** Every amount on a screen goes through this, so hiding values (MINT.md section 3.3) is one switch and covers all of Mint. */
    fun show(minor: Long, currency: String): String = app.pokeplan.mint.Money.format(minor, currency, data.settings.hideValues)

    // --- accounts ----------------------------------------------------------------------------------------------------

    fun addAccount(name: String, type: AccountType, currency: String, openingMinor: Long = 0, institution: String = "", suffix: String = "", group: String = ""): Logged<FinancialAccount> {
        val code = currency.trim().uppercase()
        (MintRules.checkAccount(name, code, openingMinor, suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val account = FinancialAccount(newId(), name.trim(), type, code, openingMinor, today, institution.trim().take(MintRules.NAME_MAX), suffix.trim(), group.trim().take(MintRules.NAME_MAX), createdMillis = nowMillis)
        update { it.copy(accounts = it.accounts + account) }
        return Logged(account)
    }

    /** Renames or regroups an account. Its currency and opening balance stay: changing them would rewrite history (use an adjustment instead). */
    fun updateAccount(a: FinancialAccount): Logged<FinancialAccount> {
        val old = data.accounts.firstOrNull { it.id == a.id } ?: return Logged(null, "That account is gone.")
        (MintRules.checkAccount(a.name, old.currency, old.openingMinor, a.suffix.trim()) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(name = a.name.trim(), institution = a.institution.trim().take(MintRules.NAME_MAX), suffix = a.suffix.trim(), group = a.group.trim().take(MintRules.NAME_MAX), type = if (a.type.liability == old.type.liability) a.type else old.type)
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
        val next = t.copy(source = old.source, createdMillis = old.createdMillis, day = dayOf(t.atMillis), payee = t.payee.trim().take(MintRules.NAME_MAX * 2), note = t.note.trim().take(MintRules.NOTE_MAX))
        // Editing a record on an account that was since closed is allowed to keep its history readable; only the amount rules apply there.
        (MintRules.checkAmount(next.amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val open = data.copy(accounts = data.accounts.map { it.copy(status = AccountStatus.ACTIVE) })
        (MintRules.checkTransaction(open, next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == t.id) next else it }) }
        return Logged(next)
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

    fun updateBudgetAmount(id: String, plannedMinor: Long): Boolean {
        if (MintRules.checkAmount(plannedMinor) is Check.Refused) return false
        update { d -> d.copy(budgets = d.budgets.map { if (it.id == id) it.copy(plannedMinor = plannedMinor) else it }) }
        return true
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

    /** Edits a bill's name, amount, account and category. The schedule's first date and frequency stay, so no past occurrence is rewritten (MINT.md section 8.3). */
    fun updateBill(b: Bill): Logged<Bill> {
        val old = data.bills.firstOrNull { it.id == b.id } ?: return Logged(null, "That bill is gone.")
        if (b.name.isBlank()) return Logged(null, "Give the bill a name.")
        b.expectedMinor?.let { m -> (MintRules.checkAmount(m) as? Check.Refused)?.let { return Logged(null, it.reason) } }
        val next = old.copy(name = b.name.trim().take(MintRules.NAME_MAX), expectedMinor = b.expectedMinor, categoryId = b.categoryId, accountId = b.accountId)
        update { d -> d.copy(bills = d.bills.map { if (it.id == b.id) next else it }) }
        return Logged(next)
    }

    fun setBillStatus(id: String, status: PlanStatus) = update { d -> d.copy(bills = d.bills.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteBill(id: String) = update { d -> d.copy(bills = d.bills.filterNot { it.id == id }, billPayments = d.billPayments.filterNot { it.billId == id }) }

    /**
     * Settles one occurrence: paid, partly paid or skipped. **It creates no transaction** (MINT.md section 8.2); [txId] only links one the user chose.
     * Settling again replaces the earlier settlement, so a mistake can be put right.
     */
    fun settle(billId: String, dueDay: LocalDate, status: SettleStatus, paidMinor: Long = 0, txId: String? = null): Boolean {
        val bill = data.bills.firstOrNull { it.id == billId } ?: return false
        if (status != SettleStatus.SKIPPED && MintRules.checkAmount(paidMinor) is Check.Refused) return false
        val amount = if (status == SettleStatus.SKIPPED) 0L else paidMinor
        val p = BillPayment(newId(), bill.id, dueDay, status, amount, today, txId, nowMillis)
        update { d -> d.copy(billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay } + p) }
        return true
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
