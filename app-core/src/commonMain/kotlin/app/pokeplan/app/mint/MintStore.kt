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
import app.pokeplan.mint.CaptureCandidate
import app.pokeplan.mint.CaptureSource
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

    fun reload() = document.reload()

    fun sync() = document.sync()

    private fun update(change: (MintData) -> MintData) = document.update(change)

    fun restore(snapshot: MintData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    fun noonOf(day: LocalDate): Long = day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: MintKind, recordId: String): List<app.pokeplan.core.reward.Reward>? =
        if (!data.settings.progression) null else world.mint(MintEvent("mint:${kind.name.lowercase()}:$recordId", kind, nowMillis, recordId))

    private fun payForLog(tx: Transaction): List<app.pokeplan.core.reward.Reward> {
        if (!MintRules.logPays(data, tx, today)) return emptyList()
        val rewards = pay(MintKind.LOGGED, tx.id) ?: return emptyList()
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == tx.id) it.copy(paid = true) else it }) }
        return rewards
    }

    fun show(minor: Long, currency: String): String = app.pokeplan.mint.Money.format(minor, currency, data.settings.hideValues)

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

    fun setReceiveQr(accountId: String, base64Png: String): Logged<FinancialAccount> {
        val old = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "That account is gone.")
        (MintRules.checkReceiveQr(base64Png) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(receiveQr = base64Png)
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == accountId) next else it }) }
        return Logged(next)
    }

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

    fun hasHistory(id: String): Boolean =
        data.transactions.any { it.accountId == id || it.toAccountId == id } || data.plans.any { it.accountId == id } ||
            data.creditStatements.any { it.accountId == id } || data.reconciliations.any { it.accountId == id } || data.bills.any { it.accountId == id }

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

    fun setAccountStatus(id: String, status: AccountStatus) =
        update { d -> d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(status = status) else it }) }

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

    fun deleteCategory(id: String): String? {
        if (data.transactions.any { it.categoryId == id || it.splits.any { split -> split.categoryId == id } } ||
            data.budgets.any { it.categoryId == id } || data.bills.any { it.categoryId == id })
            return "Something uses this category. Archive it instead."
        update { d -> d.copy(categories = d.categories.filterNot { it.id == id }) }
        return null
    }

    fun record(
        type: TxType, amountMinor: Long, accountId: String, toAccountId: String? = null, categoryId: String? = null, payee: String = "", note: String = "",
        atMillis: Long = nowMillis, status: TxStatus = TxStatus.POSTED, up: Boolean = true, refundOf: String? = null, splits: List<TransactionSplit> = emptyList(), feeMinor: Long = 0,
        iou: Boolean = false,
    ): Logged<Transaction> {
        val currency = data.accounts.firstOrNull { it.id == accountId }?.currency ?: return Logged(null, "Choose an account.")
        if (type == TxType.TRANSFER && data.accounts.firstOrNull { it.id == toAccountId }?.let { target ->
                Installments.allowedFor(target.type) && Installments.views(data, today).any { it.plan.accountId == target.id && it.remainingMinor > 0 }
            } == true)
            return Logged(null, "Repayments to this account must be allocated to its unpaid installment plans.")
        val tx = Transaction(newId(), type, amountMinor, currency, atMillis, dayOf(atMillis), accountId, toAccountId, categoryId, payee.trim().take(MintRules.NAME_MAX * 2), note.trim().take(MintRules.NOTE_MAX), status, TxSource.MANUAL, up, refundOf, createdMillis = nowMillis, splits = splits, feeMinor = feeMinor,
            iou = iou)
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(transactions = it.transactions + tx) }
        return Logged(tx, rewards = payForLog(tx))
    }

    fun updateTransaction(t: Transaction): Logged<Transaction> {
        val old = data.transactions.firstOrNull { it.id == t.id } ?: return Logged(null, "That record is gone.")
        // A plan's purchase keeps a name even when the player clears the field: the plan is named too (5.6A).
        val planName = data.plans.firstOrNull { it.purchaseTxId == old.id }?.name
        val keptPayee = if (t.payee.isBlank()) (planName ?: old.payee) else t.payee
        val next = t.copy(
            planId = old.planId, planNo = old.planNo, feeKind = old.feeKind, planAllocations = old.planAllocations,
            creditStatementId = old.creditStatementId, interestThrough = old.interestThrough, paid = old.paid, source = old.source, createdMillis = old.createdMillis, iou = old.iou,
            capturedFrom = old.capturedFrom, captureRef = old.captureRef,
            importBatch = old.importBatch, importLabel = old.importLabel, fingerprint = old.fingerprint,
            day = dayOf(t.atMillis), payee = keptPayee.trim().take(MintRules.NAME_MAX * 2), note = t.note.trim().take(MintRules.NOTE_MAX),
        )
        // A record an installment plan counts on keeps its type, amount and accounts; the plan is what changes (MINT.md 5.6A).
        if ((old.planId != null || old.planAllocations.isNotEmpty()) && (next.type != old.type || next.amountMinor != old.amountMinor || next.accountId != old.accountId || next.toAccountId != old.toAccountId))
            return Logged(null, "This record belongs to an installment plan. Change the plan instead.")
        (MintRules.checkAmount(next.amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val open = data.copy(accounts = data.accounts.map { it.copy(status = AccountStatus.ACTIVE) })
        (MintRules.checkTransaction(open, next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d ->
            d.copy(
                transactions = d.transactions.map { if (it.id == t.id) next else it },
                plans = d.plans.map { plan ->
                    if (plan.purchaseTxId == t.id && next.payee.isNotBlank()) plan.copy(name = next.payee.trim().take(MintRules.NAME_MAX)) else plan
                },
            )
        }
        return Logged(next)
    }

    fun interestToReview(): List<Transaction> = data.transactions.filter { it.interestThrough != null && it.status == TxStatus.PENDING }.sortedBy { it.day }

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

    fun confirmInterest(id: String, amountMinor: Long? = null): Logged<Transaction> {
        val old = data.transactions.firstOrNull { it.id == id && it.interestThrough != null && it.status == TxStatus.PENDING } ?: return Logged(null, "That estimate is gone.")
        if (amountMinor != null) (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(amountMinor = amountMinor ?: old.amountMinor, status = TxStatus.POSTED)
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    fun confirmAllInterest(): Int = interestToReview().count { confirmInterest(it.id).record != null }

    fun skipInterest(id: String): Boolean {
        if (data.transactions.none { it.id == id && it.interestThrough != null && it.status == TxStatus.PENDING }) return false
        setStatus(id, TxStatus.VOIDED)
        return true
    }

    // --- the one review box: interest estimates and alerts the phone read (MINT.md 4.3E, 10.4) ----------------------

    /** What became of one captured alert: the PENDING record it proposed, or the reason it was not kept. */
    data class CaptureResult(val record: Transaction? = null, val reason: String? = null) {
        val ok: Boolean get() = record != null
    }

    /** Every PENDING record waiting for the player, an interest estimate or a captured alert, oldest day first. */
    fun toReview(): List<Transaction> = data.transactions
        .filter { it.status == TxStatus.PENDING && (it.interestThrough != null || it.captureRef != null) }
        .sortedWith(compareBy({ it.day }, { it.createdMillis }))

    /** Posts a waiting record, with the amount the player adjusted it to when one is given. */
    fun confirmPending(id: String, amountMinor: Long? = null): Logged<Transaction> {
        val old = toReview().firstOrNull { it.id == id } ?: return Logged(null, "That record is gone.")
        if (amountMinor != null) (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(amountMinor = amountMinor ?: old.amountMinor, status = TxStatus.POSTED)
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    fun confirmAllPending(): Int = toReview().count { confirmPending(it.id).record != null }

    /** Skips a waiting record: kept, voided, so the same days are not estimated again and a captured one is not proposed again. */
    fun skipPending(id: String): Boolean {
        if (toReview().none { it.id == id }) return false
        setStatus(id, TxStatus.VOIDED)
        return true
    }

    /** Removes a captured record that is still waiting (MINT.md 10.4: deletion of the derived pending records). */
    fun deleteCaptured(id: String): Boolean {
        if (data.transactions.none { it.id == id && it.status == TxStatus.PENDING && it.captureRef != null }) return false
        deleteTransactionRecord(id)
        return true
    }

    /** Removes every captured record still waiting; how many it removed. Confirmed ones are ordinary history and stay. */
    fun eraseCaptured(): Int {
        val gone = data.transactions.count { it.status == TxStatus.PENDING && it.captureRef != null }
        if (gone == 0) return 0
        update { d -> d.copy(transactions = d.transactions.filterNot { it.status == TxStatus.PENDING && it.captureRef != null }) }
        return gone
    }

    /**
     * Proposes one alert the phone read (MINT.md 10.4) as a PENDING record the player confirms, adjusts or skips. It
     * never posts by itself and never pays a reward.
     */
    fun capture(candidate: CaptureCandidate): CaptureResult {
        if (candidate.type != TxType.EXPENSE && candidate.type != TxType.INCOME) return CaptureResult(reason = "That alert is not a purchase or income.")
        if (candidate.amountMinor <= 0) return CaptureResult(reason = "That alert has no amount.")
        (MintRules.checkAmount(candidate.amountMinor) as? Check.Refused)?.let { return CaptureResult(reason = it.reason) }
        if (data.transactions.any { it.captureRef != null && it.captureRef == candidate.ref }) return CaptureResult(reason = "Already recorded.")
        val label = CaptureSource.ofName(candidate.source)?.label ?: candidate.source
        val account = captureAccount(candidate, label) ?: return CaptureResult(reason = "No account to put an alert from $label in.")
        if (account.currency != candidate.currency) return CaptureResult(reason = "That alert is in another currency.")
        val tx = Transaction(
            newId(), candidate.type, candidate.amountMinor, account.currency, candidate.atMillis, candidate.day, account.id,
            categoryId = null, payee = candidate.payee.trim().take(MintRules.NAME_MAX * 2),
            note = candidate.note.trim().take(MintRules.NOTE_MAX), status = TxStatus.PENDING, source = TxSource.CAPTURED,
            createdMillis = nowMillis, capturedFrom = label, captureRef = candidate.ref,
        )
        val open = data.copy(accounts = data.accounts.map { it.copy(status = AccountStatus.ACTIVE) })
        (MintRules.checkTransaction(open, tx) as? Check.Refused)?.let { return CaptureResult(reason = it.reason) }
        update { d -> d.copy(transactions = d.transactions + tx) }
        return CaptureResult(tx)
    }

    /** Which account an alert belongs to: the card or account it names, then one named after the source, then the only one. */
    private fun captureAccount(candidate: CaptureCandidate, label: String): FinancialAccount? {
        val open = data.accounts.filter { it.status == AccountStatus.ACTIVE }
        candidate.lastDigits?.takeIf { it.isNotBlank() }?.let { digits ->
            open.firstOrNull { it.suffix.trim().equals(digits.trim(), ignoreCase = true) }?.let { return it }
        }
        open.firstOrNull { it.institution.contains(label, ignoreCase = true) || it.name.contains(label, ignoreCase = true) }?.let { return it }
        return open.singleOrNull()
    }

    data class ImportResult(val batch: String?, val added: Int, val skipped: Int, val note: String)

    data class ImportBatchInfo(val id: String, val label: String, val importedMillis: Long, val accountId: String, val count: Int, val unreviewed: Int)

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

    fun importBatches(): List<ImportBatchInfo> =
        data.transactions.filter { it.importBatch != null }.groupBy { it.importBatch!! }.map { (id, list) ->
            ImportBatchInfo(id, list.first().importLabel, list.first().createdMillis, list.first().accountId, list.size, list.count { it.unreviewed })
        }.sortedByDescending { it.importedMillis }

    fun markBatchReviewed(batch: String) = update { d -> d.copy(transactions = d.transactions.map { if (it.importBatch == batch) it.copy(unreviewed = false) else it }) }

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

    fun setTransactionStatus(id: String, status: TxStatus): Boolean {
        if (data.transactions.none { it.id == id } || (status != TxStatus.POSTED && planOfPurchase(id) != null)) return false
        setStatus(id, status)
        return true
    }

    private fun setStatus(id: String, status: TxStatus) = update { d -> d.copy(transactions = d.transactions.map { if (it.id == id) it.copy(status = status) else it }) }

    fun planOfPurchase(id: String): InstallmentPlan? = data.plans.firstOrNull { it.purchaseTxId != null && it.purchaseTxId == id }

    fun deleteTransaction(id: String): Boolean {
        if (planOfPurchase(id) != null) return false
        deleteTransactionRecord(id)
        return true
    }

    private fun deleteTransactionRecord(id: String) = update { d ->
        d.copy(
            transactions = d.transactions.filterNot { it.id == id },
            reconciliations = d.reconciliations.map { if (it.adjustmentTxId == id) it.copy(adjustmentTxId = null) else it },
            billPayments = d.billPayments.map { if (it.txId == id) it.copy(txId = null) else it },
            creditStatements = d.creditStatements.map { if (it.estimatedInterestTxId == id) it.copy(estimatedInterestTxId = null) else it },
        )
    }

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

    private fun cleanDays(frequency: Frequency, days: List<Int>): List<Int> =
        if (frequency != Frequency.SEMI_MONTHLY) emptyList() else days.filter { it in 1..31 }.distinct().sorted().take(4).let { if (it == listOf(15, 30)) emptyList() else it }

    fun addBill(name: String, currency: String, frequency: Frequency, firstDue: LocalDate, expectedMinor: Long? = null, categoryId: String? = null, accountId: String? = null, income: Boolean = false, days: List<Int> = emptyList(), workday: Boolean = false, estimate: Boolean = false): Logged<Bill> {
        if (name.isBlank()) return Logged(null, "Give the bill a name.")
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (expectedMinor != null) (MintRules.checkAmount(expectedMinor) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val bill = Bill(newId(), name.trim().take(MintRules.NAME_MAX), currency, frequency, firstDue, expectedMinor, categoryId, accountId, createdMillis = nowMillis, income = income, days = cleanDays(frequency, days), workday = workday, estimate = estimate)
        update { it.copy(bills = it.bills + bill) }
        return Logged(bill)
    }

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

    fun unsettle(billId: String, dueDay: LocalDate) = update { d -> d.copy(billPayments = d.billPayments.filterNot { it.billId == billId && it.dueDay == dueDay }) }

    fun planViews(): List<PlanView> = Installments.views(data, today)

    fun planView(planId: String): PlanView? = data.plans.firstOrNull { it.id == planId }?.let { Installments.view(data, it, today) }

    private fun atMillis(day: LocalDate): Long = if (day == today) nowMillis else day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun creditAccount(id: String, allowInactive: Boolean = false): FinancialAccount? =
        data.accounts.firstOrNull { it.id == id }?.takeIf { Installments.allowedFor(it.type) && (allowInactive || it.status == AccountStatus.ACTIVE) }

    private fun checkPlanTransfer(data: MintData, tx: Transaction, destinationId: String): Check {
        val paymentData = data.copy(accounts = data.accounts.map { if (it.id == destinationId) it.copy(status = AccountStatus.ACTIVE) else it })
        return MintRules.checkTransaction(paymentData, tx)
    }

    fun addPlanPurchase(
        accountId: String, name: String, totalMinor: Long, categoryId: String?, count: Int, every: PlanEvery, firstDue: LocalDate?, fee: FeeRule = FeeRule.None,
        feeKind: FeeKind = FeeKind.FEE, day: LocalDate = today,
    ): Logged<InstallmentPlan> {
        val account = creditAccount(accountId) ?: return Logged(null, "Choose a credit card or pay later account that is open.")
        if (day > today) return Logged(null, "A purchase cannot be dated in the future.")
        if (feeKind == FeeKind.LATE) return Logged(null, "Choose fee or interest.")
        (Installments.checkRequest(account.type, totalMinor, count, fee) as? Check.Refused)?.let { return Logged(null, it.reason) }
        // The name is optional: a purchase the player leaves unnamed is still a purchase, and gets the fixed name so its
        // record and its schedule can be found again (MINT.md 5.6A).
        val label = name.trim().take(MintRules.NAME_MAX).ifBlank { Installments.DEFAULT_PURCHASE_NAME }
        val txId = newId()
        val tx = Transaction(txId, TxType.EXPENSE, totalMinor, account.currency, atMillis(day), day, account.id, categoryId = categoryId, payee = label.take(MintRules.NAME_MAX * 2), createdMillis = nowMillis)
        (MintRules.checkTransaction(data, tx) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val planId = newId()
        val first = firstDue ?: MonthlyBills.firstDue(account, day, every)
        val plan = InstallmentPlan(
            planId, account.id, label, account.currency, txId, totalMinor, day, every, feeKind,
            Installments.generate(totalMinor, count, every, first, fee), nowMillis, feeRule = fee,
        )
        update { it.copy(transactions = it.transactions + tx.copy(planId = planId), plans = it.plans + plan) }
        return Logged(plan, rewards = payForLog(tx))
    }

    fun addExistingPlan(accountId: String, name: String, rows: List<app.pokeplan.mint.Installment>, addToStartingBalance: Boolean = true, feeKind: FeeKind = FeeKind.FEE): Logged<InstallmentPlan> {
        val account = creditAccount(accountId) ?: return Logged(null, "Choose a credit card or pay later account that is open.")
        if (feeKind == FeeKind.LATE) return Logged(null, "Choose fee or interest.")
        (Installments.checkExisting(account.type, rows) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val label = name.trim().take(MintRules.NAME_MAX).ifBlank { Installments.DEFAULT_SCHEDULE_NAME }
        val total = rows.sumOf { it.principalMinor }
        val opening = if (addToStartingBalance) account.openingMinor + total else account.openingMinor
        if (opening > app.pokeplan.mint.Money.MAX_MINOR) return Logged(null, "That total looks wrong.")
        val numbered = rows.mapIndexed { i, r -> r.copy(no = i + 1) }
        val plan = InstallmentPlan(
            newId(), account.id, label, account.currency, null, total, numbered.first().dueDay,
            PlanEvery.MONTH, feeKind, numbered, nowMillis, openingBalanceIncluded = addToStartingBalance,
        )
        update { d -> d.copy(plans = d.plans + plan, accounts = d.accounts.map { if (it.id == account.id) it.copy(openingMinor = opening) else it }) }
        return Logged(plan)
    }

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

    fun openBills(accountId: String): List<MonthlyBill> = MonthlyBills.open(data, accountId, today)

    fun bills(accountId: String): List<MonthlyBill> = MonthlyBills.of(data, accountId, today)

    fun payBill(accountId: String, month: BillMonth, amountMinor: Long, fromAccountId: String, day: LocalDate = today, feeMinor: Long = 0): Logged<Transaction> {
        if (openBills(accountId).none { it.month == month }) return Logged(null, "That bill has nothing left to pay.")
        return payPlans(accountId, amountMinor, fromAccountId, day, month, feeMinor)
    }

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

    fun misalignedInstallments(accountId: String): Int = alignment(accountId).second

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

    /** Why a schedule's terms cannot be changed, in words, or null when they can (MINT.md 5.6C). */
    fun planLockReason(planId: String): String? = data.plans.firstOrNull { it.id == planId }?.let { Installments.scheduleLock(data, it).reason }

    /** Why one installment cannot be edited, in words, or null when it can. */
    fun installmentLockReason(planId: String, no: Int): String? {
        val plan = data.plans.firstOrNull { it.id == planId } ?: return "That plan is gone."
        val view = planView(planId) ?: return "That plan is gone."
        val installment = view.installments.firstOrNull { it.no == no } ?: return "That installment is gone."
        return Installments.installmentLock(data, plan, installment)
    }

    /** What deleting a plan's purchase and schedule would take away and what would stay, or null when it is gone. */
    fun planDeletion(planId: String): Installments.DeletionPreview? =
        data.plans.firstOrNull { it.id == planId }?.let { Installments.deletionPreview(data, it, today) }

    /**
     * Deletes a plan's purchase record and its schedule together, only when nothing has been recorded on it yet (MINT.md
     * 5.6A): a payment, a fee or a refund is real history and stops it, so the player removes the schedule instead.
     */
    fun deletePlanPurchase(planId: String): Logged<Boolean> {
        val preview = planDeletion(planId) ?: return Logged(null, "That plan is gone.")
        val purchaseId = preview.purchaseId ?: return Logged(null, "This schedule has no purchase record to delete.")
        preview.blockedReason?.let { return Logged(null, it) }
        update { d ->
            d.copy(
                plans = d.plans.filterNot { it.id == planId },
                transactions = d.transactions.filterNot { it.id == purchaseId },
            )
        }
        return Logged(true)
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

    fun deletePlan(planId: String) = update { d ->
        d.copy(
            plans = d.plans.filterNot { it.id == planId },
            transactions = d.transactions.map { transaction ->
                val unlinked = if (transaction.planId == planId) transaction.copy(planId = null, planNo = null) else transaction
                unlinked.copy(planAllocations = unlinked.planAllocations.filterNot { it.planId == planId })
            },
        )
    }

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

    fun bookBalance(accountId: String): Long? = data.accounts.firstOrNull { it.id == accountId }?.let { MintRules.balance(data, it).postedMinor }

    /**
     * One balance check before anything is written (MINT.md 5.8): what Mint holds, what the player's statement says, the
     * difference, and the things in Mint that may explain it. A quick check records nothing; only a reconciliation does.
     */
    data class BalanceCheck(
        val account: FinancialAccount,
        val bookMinor: Long,
        val reportedMinor: Long,
        val differenceMinor: Long,
        val causes: List<String>,
    ) {
        val matches: Boolean get() = differenceMinor == 0L
    }

    fun balanceCheck(accountId: String, reportedMinor: Long): BalanceCheck? {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return null
        (MintRules.checkAmount(reportedMinor) as? Check.Refused)?.let { return null }
        val book = MintRules.balance(data, account).postedMinor
        val diff = MintRules.reconcileDifference(reportedMinor, book)
        val causes = buildList {
            val pending = data.transactions.count { it.accountId == account.id && it.status == TxStatus.PENDING }
            if (pending > 0) add(if (pending == 1) "One record is still pending, so it is not in Mint's balance yet." else "$pending records are still pending, so they are not in Mint's balance yet.")
            val unreviewed = data.transactions.count { it.accountId == account.id && it.importBatch != null && it.unreviewed }
            if (unreviewed > 0) add(if (unreviewed == 1) "One imported record has not been reviewed." else "$unreviewed imported records have not been reviewed.")
            val recent = data.transactions.count { it.accountId == account.id && it.status == TxStatus.POSTED && it.day > today.minus(DatePeriod(days = 7)) }
            if (recent > 0) add(if (recent == 1) "One record is from the last week and may not be on the statement yet." else "$recent records are from the last week and may not be on the statement yet.")
            if (isEmpty() && diff != 0L) add("Nothing in Mint explains it: look for a purchase or a fee that was never recorded.")
        }
        return BalanceCheck(account, book, reportedMinor, diff, causes)
    }

    /** Every balance check and reconciliation kept for an account, newest first: the audit trail of what was compared. */
    fun reconciliationHistory(accountId: String? = null): List<Reconciliation> = data.reconciliations
        .filter { accountId == null || it.accountId == accountId }
        .sortedByDescending { it.createdMillis }

    fun reconcile(accountId: String, statementMinor: Long, acceptAdjustment: Boolean = false, reason: String = ""): Logged<Reconciliation> {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: return Logged(null, "That account is gone.")
        if (account.status != AccountStatus.ACTIVE) return Logged(null, "That account is not open.")
        if (statementMinor < 0 || statementMinor > app.pokeplan.mint.Money.MAX_MINOR) return Logged(null, "That amount looks wrong.")
        val book = MintRules.balance(data, account).postedMinor
        val diff = MintRules.reconcileDifference(statementMinor, book)
        if (diff != 0L && !acceptAdjustment) return Logged(null, "The statement and Mint differ. Accept the difference to record it as an adjustment.")
        val why = reason.trim().take(MintRules.NOTE_MAX)
        // A manual adjustment must say what it is for, so the audit trail means something later (MINT.md 5.8).
        if (diff != 0L && why.isBlank()) return Logged(null, "Say what the difference is for, in a few words.")
        var adjustmentId: String? = null
        if (diff != 0L) {
            // The reason is the adjustment's own note, so the audit trail is readable from the record itself.
            val adj = Transaction(newId(), TxType.ADJUSTMENT, kotlin.math.abs(diff), account.currency, nowMillis, today, account.id, note = why, source = TxSource.ADJUSTMENT, up = diff > 0, createdMillis = nowMillis)
            adjustmentId = adj.id
            update { it.copy(transactions = it.transactions + adj) }
        }
        val record = Reconciliation(newId(), account.id, today, statementMinor, book, adjustmentId, why, nowMillis)
        update { it.copy(reconciliations = it.reconciliations + record) }
        val pays = MintRules.reconcilePays(data.copy(reconciliations = data.reconciliations.filterNot { it.id == record.id }), account.id, today)
        val rewards = if (pays) pay(MintKind.RECONCILED, record.id) else null
        if (rewards != null) update { d -> d.copy(reconciliations = d.reconciliations.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.reconciliations.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    fun finishReview(): Logged<ReviewRecord> {
        val record = ReviewRecord(newId(), today, nowMillis)
        val pays = MintRules.reviewPays(data, today, weekStart())
        update { it.copy(reviews = it.reviews + record) }
        val rewards = if (pays) pay(MintKind.REVIEW, record.id) else null
        if (rewards != null) update { d -> d.copy(reviews = d.reviews.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.reviews.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    private fun settings(change: (MintSettings) -> MintSettings) = update { it.copy(settings = change(it.settings)) }

    fun setProgression(on: Boolean) = settings { it.copy(progression = on) }

    fun setHideValues(on: Boolean) = settings { it.copy(hideValues = on) }

    fun setOverviewWindow(window: app.pokeplan.mint.OverviewWindow) = settings { it.copy(overviewWindow = window) }

    fun setOverviewPayLater(count: app.pokeplan.mint.PayLaterCount) = settings { it.copy(overviewPayLater = count) }

    fun setCaptureNotifications(on: Boolean) = settings { it.copy(captureNotifications = on) }

    fun setCaptureMessages(on: Boolean) = settings { it.copy(captureMessages = on) }

    fun setBillReminders(on: Boolean) = settings { it.copy(billReminders = on) }

    fun setBillReminderDays(days: Int) = settings { it.copy(billReminderDays = days.coerceIn(0, MAX_REMIND_DAYS)) }

    fun setBillReminderHour(hour: Int) = settings { it.copy(billReminderHour = hour.coerceIn(0, 23)) }

    fun setBillReminderNames(on: Boolean) = settings { it.copy(billReminderNames = on) }

    fun setBillRemind(billId: String, on: Boolean) = update { d -> d.copy(bills = d.bills.map { if (it.id == billId) it.copy(remind = on) else it }) }

    fun setGate(on: Boolean) = settings { it.copy(gate = on) }

    fun setBuiltInHolidays(on: Boolean) = settings { it.copy(builtInHolidays = on) }

    fun setHolidayCounted(day: LocalDate, counted: Boolean) = settings { it.copy(holidaysIgnored = if (counted) it.holidaysIgnored - day else (it.holidaysIgnored + day).distinct().sorted()) }

    fun setDayOff(day: LocalDate, off: Boolean) = settings { it.copy(daysOff = if (off) (it.daysOff + day).distinct().sorted().takeLast(200) else it.daysOff - day) }

    fun setCurrency(code: String) = settings { if (MintRules.checkCurrency(code.trim().uppercase()) is Check.Ok) it.copy(currency = code.trim().uppercase()) else it }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    fun export(kinds: Set<MintKinds> = MintKinds.entries.toSet()): String = MintExport.json(data, kinds)

    fun erase(kind: MintKinds) = update { MintExport.without(it, kind) }

    fun eraseAll() = update { d -> tidy(MintKinds.entries.fold(d) { acc, k -> MintExport.without(acc, k) }) }

    fun resetAll() = update { tidy(MintData()) }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        const val MAX_REMIND_DAYS = 7

        internal fun tidy(d: MintData): MintData = if (d.categories.isEmpty()) d.copy(categories = MintRules.seedCategories()) else d
    }
}

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
data class StatementPreview(
    val table: List<List<String>> = emptyList(),
    val columns: StatementImport.Columns = StatementImport.Columns(),
    val order: StatementImport.DateOrder = StatementImport.DateOrder.DAY_FIRST,
    val orderCertain: Boolean = true,
    val preview: StatementImport.Preview? = null,
    val refused: String? = null,
)
