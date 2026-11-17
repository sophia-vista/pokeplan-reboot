package app.pokeplan.ui.mint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.app.heal.Logged
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.FeeKind
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.InstallmentPlan
import app.pokeplan.mint.InstallmentState
import app.pokeplan.mint.Installments
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.Money
import app.pokeplan.mint.PlanEvery
import app.pokeplan.mint.PlanView
import app.pokeplan.mint.TxType
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.Confirm
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDivider
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate

/** "1.5" or "1,5" as basis points (150), with plain digit parsing, no floats; null when it is not a percentage with at most two decimals. */
internal fun percentToBasisPoints(raw: String): Int? {
    val t = raw.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val parts = t.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }
    val frac = parts.getOrElse(1) { "" }
    if (whole.length > 3 || frac.length > 2 || !whole.all { it.isDigit() } || !frac.all { it.isDigit() }) return null
    return whole.toInt() * 100 + frac.padEnd(2, '0').toInt()
}

private fun LocalDate.compact(): String = "$dayOfMonth ${month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"

private fun <T> Logged<T>.note(): String? = if (record == null) (refused ?: "That could not be saved.") else null

internal fun money(raw: String) = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16)

/**
 * The "Credit line" box of a credit card or pay later account (MINT.md 4.3D, 5.6A): limit, what is owed, what is available, this month's activity, the statement
 * and due days, then the installment plans with a button to add one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreditLine(model: AppModel, a: FinancialAccount, problem: (String?) -> Unit) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    val owed = MintRules.balances(data).firstOrNull { it.account.id == a.id }?.postedMinor ?: 0L
    val (from, end) = MintRules.periodOf(BudgetPeriod.MONTHLY, today)
    val act = mint.creditActivity(a.id, from, end)
    val plans = mint.planViews().filter { it.plan.accountId == a.id }
    var open by remember(a.id) { mutableStateOf<String?>(null) }
    var finished by remember(a.id) { mutableStateOf(false) }
    var buying by remember(a.id) { mutableStateOf(false) }
    var existing by remember(a.id) { mutableStateOf(false) }
    if (buying) Dialog({ buying = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            FormBox { Column(Modifier.verticalScroll(rememberScrollState())) { NewPurchaseForm(model, a, startExisting = existing) { buying = false } } }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Credit line")
            if (a.limitMinor > 0) MintValueRow("Limit", mint.show(a.limitMinor, a.currency))
            MintValueRow("Owed", mint.show(owed, a.currency))
            MintRules.leftToSpend(a, owed)?.let { left ->
                MintValueRow("Available credit", mint.show(left, a.currency))
                if (owed > a.limitMinor) Text("Over the limit by ${mint.show(owed - a.limitMinor, a.currency)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
            }
            listOf("Purchases" to act.purchasesMinor, "Fees" to act.feesMinor, "Interest" to act.interestMinor, "Late fees" to act.lateFeesMinor, "Refunds" to act.refundsMinor, "Repaid" to act.repaymentsMinor)
                .filter { it.second != 0L }.forEach { (label, minor) -> MintValueRow(label, mint.show(minor, a.currency), soft = true) }
            dayLine("Statement", a.statementDay, today)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            dayLine("Due", a.dueDay, today)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            if (a.statementDay > 0 || a.dueDay > 0) Text("Account days are not reminders.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (plans.isNotEmpty()) Text("Plan reminders need Bill reminders on.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val live = plans.filter { !it.finished }
            val done = plans.filter { it.finished }
            if (plans.isNotEmpty()) SectionLabel("Installment plans")
            live.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            if (done.isNotEmpty()) {
                MintLine(onClick = { finished = !finished }, onClickLabel = if (finished) "Hide finished plans" else "Show finished plans") { Text("Finished (${done.size})", style = MaterialTheme.typography.bodyLarge) }
                if (finished) done.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            }
            PixelButton("Add plan", { existing = false; buying = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
        }
    }
}

@Composable
private fun PlanRow(model: AppModel, v: PlanView, open: Boolean, toggle: () -> Unit, problem: (String?) -> Unit) {
    val mint = model.mint
    val cur = v.plan.currency
    HorizontalDivider(color = Pixel.Border)
    MintLine(onClick = toggle, onClickLabel = if (open) "Hide plan" else "Show plan") {
        Text(v.plan.name, style = MaterialTheme.typography.bodyLarge)
        if (v.plan.purchaseTxId == null) Text("Existing schedule", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Text(
            if (v.finished) "Paid" else "${v.installments.count { it.state == InstallmentState.PAID }} of ${v.installments.size} paid · ${mint.show(v.remainingMinor, cur)}",
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
        v.next?.let { n ->
            Text("Next: ${n.dueDay.compact()} ${mint.show(n.remainingMinor, cur)}", style = MaterialTheme.typography.bodyMedium, color = if (n.state == InstallmentState.OVERDUE) Pixel.Warning else Pixel.InkSoft)
        }
    }
    if (open) PlanOptions(model, v.plan.id, problem)
}

/** Pay toward a plan: the amount (the next installment's remaining by default), where from, the day, and the Pay button. Early or part payments go to the earliest installment first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PayArea(model: AppModel, v: PlanView, defaultMinor: Long, resetKey: Any, onResult: (String?) -> Unit) {
    val mint = model.mint
    val today = model.store.today
    val planId = v.plan.id
    val cur = v.plan.currency
    val sources = model.mintData.accounts.filter { it.status == AccountStatus.ACTIVE && !it.type.liability && it.currency == cur }
    var amount by remember(planId, resetKey, defaultMinor) { mutableStateOf(Money.plain(defaultMinor, cur)) }
    var from by remember(planId) { mutableStateOf(sources.firstOrNull()?.id) }
    var payDay by remember(planId) { mutableStateOf(today) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(
            amount, { amount = money(it) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            large = true, prefix = cur, placeholder = "0.00",
        )
        if (sources.isNotEmpty()) {
            PixelDropdown(sources.map { it.id }, from, { id -> sources.firstOrNull { it.id == id }?.name ?: "" }, { id -> if (id != null) from = id }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Pay from")
        }
        FieldLabel("Paid on")
        PixelDateField(payDay, today, ModuleAccent.MINT.accent, { payDay = it ?: today }, max = today)
        Text("Early or part payments go to the earliest installment first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        FormSave("Pay", {
            val m = Money.parse(amount, cur)
            val src = from
            onResult(if (m == null) "Check the amount." else if (src == null) "Choose where to pay from." else mint.payPlan(planId, m, src, payDay).note())
        }, ModuleAccent.MINT.accent, enabled = amount.isNotBlank() && from != null)
    }
}

/** One plan's schedule with each installment's state, the payments so far, and the actions: pay, bill a fee, add a late fee, edit, refund, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanOptions(model: AppModel, planId: String, problem: (String?) -> Unit) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    val v = mint.planView(planId) ?: return
    val cur = v.plan.currency
    val next = v.next
    val planAccount = data.accounts.firstOrNull { it.id == v.plan.accountId } ?: return
    var action by remember(planId) { mutableStateOf<Pair<Int, String>?>(null) }
    var late by remember(planId) { mutableStateOf("") }
    var editDue by remember(planId) { mutableStateOf(today) }
    var editPrincipal by remember(planId) { mutableStateOf("") }
    var editFee by remember(planId) { mutableStateOf("") }
    var refund by remember(planId) { mutableStateOf("") }
    var editName by remember(planId, v.plan.name) { mutableStateOf(v.plan.name) }
    var renaming by remember(planId) { mutableStateOf(false) }
    var editingPlan by remember(planId) { mutableStateOf(false) }
    var editAmount by remember(planId, v.plan.totalMinor) { mutableStateOf(Money.plain(v.plan.totalMinor, cur)) }
    val editTerms = remember(planId, v.plan.totalMinor, v.plan.installments, v.plan.every, v.plan.feeRule) { termsFor(v.plan) }
    var asking by remember(planId) { mutableStateOf(false) }
    var refused by remember(planId) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        when {
            editingPlan -> {
                PixelField(editAmount, { editAmount = money(it) }, Modifier.fillMaxWidth(), label = "Purchase amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = cur)
                InstallmentTerms(model, planAccount, editTerms, Money.parse(editAmount, cur), v.plan.startDay)
                FormSave("Save plan", {
                    val total = Money.parse(editAmount, cur)
                    val count = editTerms.count
                    val fee = editTerms.fee(cur)
                    refused = if (total == null) "Check the amount."
                    else if (count == null) "Choose 1 to ${Installments.MAX_COUNT} installments."
                    else if (fee == null) "Check the fee."
                    else mint.updatePlanTerms(planId, editName, total, count, editTerms.every, editTerms.firstDue(v.plan.startDay), fee, editTerms.kind).note()
                    if (refused == null) editingPlan = false
                }, ModuleAccent.MINT.accent, enabled = editName.isNotBlank())
                PixelButton("Cancel", { editingPlan = false }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            }
            renaming -> {
                PixelField(editName, { editName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Purchase or order name", singleLine = true)
                FormSave("Save name", {
                    val result = mint.updatePlanName(planId, editName)
                    refused = result.refused
                    if (result.record != null) renaming = false
                }, ModuleAccent.MINT.accent, enabled = editName.isNotBlank())
                PixelButton("Cancel", { renaming = false }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            }
            else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text(v.plan.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                PixelButton("Rename", { editName = v.plan.name; renaming = true }, color = Pixel.InkSoft, compact = true)
                if (mint.canEditPlanTerms(planId)) PixelButton("Edit plan", { editingPlan = true }, color = Pixel.InkSoft, compact = true)
            }
            }
        }
        v.installments.forEach { i ->
            HorizontalDivider(color = Pixel.Border)
            MintLine(trail = { Text(mint.show(i.remainingMinor, cur), style = MaterialTheme.typography.titleSmall) }) {
                Text("${i.no} · ${i.dueDay.compact()}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(i.state.label, "fee ${mint.show(i.feeMinor, cur)}".takeIf { i.feeMinor > 0 }, "late ${mint.show(i.lateMinor, cur)}".takeIf { i.lateMinor > 0 }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = if (i.state == InstallmentState.OVERDUE) Pixel.Warning else Pixel.InkSoft,
                )
            }
            if (i.state != InstallmentState.PAID && i.state != InstallmentState.CANCELLED) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (i.feeMinor > 0 && !i.feeRecorded) PixelButton("Bill it", { refused = mint.billInstallmentFee(planId, i.no).note(); problem(refused) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("Late fee", { action = if (action == i.no to "late") null else i.no to "late"; late = "" }, color = Pixel.InkSoft, compact = true)
                    if (i.paidMinor == 0L && !i.feeRecorded && i.lateMinor == 0L) PixelButton("Edit", {
                        action = if (action == i.no to "edit") null else i.no to "edit"
                        editDue = i.dueDay
                        editPrincipal = Money.plain(i.principalMinor, cur)
                        editFee = Money.plain(i.feeMinor, cur)
                    }, color = Pixel.InkSoft, compact = true)
                }
                if (action == i.no to "late") {
                    PixelField(late, { late = money(it) }, Modifier.fillMaxWidth(), label = "Late fee", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    FormSave("Add late fee", {
                        val m = Money.parse(late, cur)
                        refused = if (m == null) "Check the amount." else mint.addLateFee(planId, i.no, m).note()
                        if (refused == null) action = null
                    }, ModuleAccent.MINT.accent, enabled = late.isNotBlank())
                }
                if (action == i.no to "edit") {
                    PixelField(editPrincipal, { editPrincipal = money(it) }, Modifier.fillMaxWidth(), label = "Principal", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = cur)
                    FieldLabel("Due")
                    PixelDateField(editDue, today, ModuleAccent.MINT.accent, { editDue = it ?: editDue }, presets = emptyList())
                    PixelField(editFee, { editFee = money(it) }, Modifier.fillMaxWidth(), label = "Fee", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    FormSave("Save", {
                        val principal = Money.parse(editPrincipal, cur)
                        val m = Money.parse(editFee.ifBlank { "0" }, cur)
                        refused = if (principal == null) "Check the principal."
                        else if (m == null) "Check the fee."
                        else if (mint.updateInstallment(planId, i.no, editDue, principal, m)) null
                        else "That installment can no longer be changed."
                        if (refused == null) action = null
                    }, ModuleAccent.MINT.accent)
                }
            }
        }
        if (next != null) {
            HorizontalDivider(color = Pixel.Border)
            PayArea(model, v, next.remainingMinor, next.no) { refused = it; problem(it) }
            Text("Not a lender's early-settlement quote.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        val history = data.transactions.filter {
            it.type == TxType.TRANSFER && (it.planId == planId || it.planAllocations.any { allocation -> allocation.planId == planId })
        }.sortedByDescending { it.atMillis }
        if (history.isNotEmpty()) {
            HorizontalDivider(color = Pixel.Border)
            SectionLabel("Payments")
            history.forEach { t ->
                MintValueRow("${t.day.compact()} · ${data.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()}", mint.show(t.amountMinor, t.currency), soft = true)
            }
        }
        HorizontalDivider(color = Pixel.Border)
        if (asking) Confirm("The purchase and its records stay unlinked. An existing schedule's opening balance stays.", "Delete plan", onConfirm = { mint.deletePlan(planId); asking = false }, onKeep = { asking = false })
        else {
            PixelField(refund, { refund = money(it) }, Modifier.fillMaxWidth(), label = "Refund", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Refund", {
                    val m = Money.parse(refund, cur)
                    refused = if (m == null) "Check the amount." else mint.refundPlan(planId, m).note()
                    if (refused == null) refund = ""
                }, Modifier.weight(1f), Pixel.InkSoft, compact = true, enabled = refund.isNotBlank())
                PixelButton("Delete plan", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

internal enum class FeeChoice(val label: String) { NONE("None"), PER("Per installment"), TOTAL("Total"), RATE("Rate per period") }

/** How an installment purchase is split: how many, how often, when the first falls due, and the fee or interest. Shared by the purchase form and the Activity form. */
internal class TermsState(count: String = "3", fee: String = "") {
    var countText by mutableStateOf(count)
    var every by mutableStateOf(PlanEvery.MONTH)
    var firstOverride by mutableStateOf<LocalDate?>(null)
    var choice by mutableStateOf(if (fee.isEmpty()) FeeChoice.NONE else FeeChoice.RATE)
    var feeText by mutableStateOf(fee)
    var interest by mutableStateOf(false)
    val count: Int? get() = countText.toIntOrNull()?.takeIf { it in 1..Installments.MAX_COUNT }
    val kind: FeeKind get() = if (interest) FeeKind.INTEREST else FeeKind.FEE
    fun firstDue(date: LocalDate): LocalDate = firstOverride ?: Installments.dueOn(date, every, 1)
    fun fee(cur: String): FeeRule? = when (choice) {
        FeeChoice.NONE -> FeeRule.None
        FeeChoice.PER -> Money.parse(feeText, cur)?.let { FeeRule.PerInstallment(it) }
        FeeChoice.TOTAL -> Money.parse(feeText, cur)?.let { FeeRule.Total(it) }
        FeeChoice.RATE -> percentToBasisPoints(feeText)?.let { FeeRule.RatePerPeriod(it) }
    }
}

private fun termsFor(plan: InstallmentPlan): TermsState = TermsState(plan.installments.size.toString()).apply {
    every = plan.every
    firstOverride = plan.startDay
    val rule = plan.feeRule ?: run {
        val fees = plan.installments.map { it.feeMinor }
        when {
            fees.all { it == 0L } -> FeeRule.None
            fees.distinct().size == 1 -> FeeRule.PerInstallment(fees.first())
            else -> FeeRule.Total(fees.sum())
        }
    }
    when (rule) {
        FeeRule.None -> choice = FeeChoice.NONE
        is FeeRule.PerInstallment -> {
            choice = FeeChoice.PER
            feeText = Money.plain(rule.minor, plan.currency)
        }
        is FeeRule.Total -> {
            choice = FeeChoice.TOTAL
            feeText = Money.plain(rule.minor, plan.currency)
        }
        is FeeRule.RatePerPeriod -> {
            choice = FeeChoice.RATE
            val whole = rule.basisPoints / 100
            val fraction = (rule.basisPoints % 100).toString().padStart(2, '0')
            feeText = "$whole.$fraction"
            interest = true
        }
    }
}

/** The new installment purchase form (MINT.md 5.6A): the purchase, how it is split, an optional fee or interest, and the live schedule it will get. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewPurchaseForm(
    model: AppModel, a: FinancialAccount, startName: String = "", startAmount: String = "", startCount: String = "3", startFee: String = "", startExisting: Boolean = false,
    existingAmounts: List<String> = listOf(""), existingFees: List<String> = listOf(""), onClose: () -> Unit,
) {
    var existing by remember { mutableStateOf(startExisting) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelChoice(listOf(false, true), existing, { if (it == true) "Already owed" else "New purchase" }, { if (it != null) existing = it }, label = "Add", key = ModuleAccent.MINT.accent)
        if (existing) ExistingScheduleForm(model, a, existingAmounts, existingFees, onClose) else PurchaseFields(model, a, startName, startAmount, startCount, startFee, onClose)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseFields(model: AppModel, a: FinancialAccount, startName: String, startAmount: String, startCount: String, startFee: String, onClose: () -> Unit) {
    val mint = model.mint
    val today = model.store.today
    val cur = a.currency
    var name by remember { mutableStateOf(startName) }
    var amount by remember { mutableStateOf(startAmount) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(today) }
    val terms = remember { TermsState(startCount, startFee) }
    var refused by remember { mutableStateOf<String?>(null) }
    val total = Money.parse(amount, cur)?.takeIf { it > 0 }
    FormStack {
        PixelTitle("New purchase plan")
        PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Purchase or order name", singleLine = true)
        PixelField(
            amount, { amount = money(it) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            large = true, prefix = cur, placeholder = "0.00",
        )
        FormDivider()
        FormGroup {
            val expenseCategories = model.mintData.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
            PixelDropdown(listOf<String?>(null) + expenseCategories.map { it.id }, categoryId, { id -> expenseCategories.firstOrNull { it.id == id }?.name ?: "None" }, { categoryId = it }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Category")
            FieldLabel("Date")
            PixelDateField(date, today, ModuleAccent.MINT.accent, { date = it ?: today }, max = today)
        }
        InstallmentTerms(model, a, terms, total, date)
        refused?.let { Report(model, it, NoticeTone.WARNING) }
        FormSave("Create", {
            refused = createPlanPurchase(mint, a, name, total, categoryId, date, terms)
            if (refused == null) onClose()
        }, ModuleAccent.MINT.accent, enabled = name.isNotBlank())
        PixelButton("Cancel", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft)
    }
}

/** Saves an installment purchase from the form values; returns the reason it was refused, or null once it is saved. */
internal fun createPlanPurchase(mint: app.pokeplan.app.mint.MintStore, a: FinancialAccount, name: String, total: Long?, categoryId: String?, date: LocalDate, t: TermsState): String? {
    val count = t.count
    val fee = t.fee(a.currency)
    return if (total == null) "Check the amount."
    else if (count == null) "Choose 1 to ${Installments.MAX_COUNT} installments."
    else if (fee == null) "Check the fee."
    else mint.addPlanPurchase(a.id, name, total, categoryId, count, t.every, t.firstDue(date), fee, t.kind, date).let { if (it.record != null) null else (it.refused ?: "That could not be saved.") }
}

/** The terms of an installment purchase: how many, how often, the first due date, a fee or interest, the live schedule, and the one caption. [total] and [date] come from the form around it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstallmentTerms(model: AppModel, a: FinancialAccount, t: TermsState, total: Long?, date: LocalDate) {
    val today = model.store.today
    val cur = a.currency
    val count = t.count
    val fee = t.fee(cur)
    val firstDue = t.firstDue(date)
    val schedule = if (total != null && total > 0 && count != null && fee != null && Installments.checkRequest(a.type, total, count, fee) == app.pokeplan.mint.Check.Ok) Installments.generate(total, count, t.every, firstDue, fee) else null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        // One control for the count (any number, 1 to the maximum), paired with how often it falls due.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(t.countText, { t.countText = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = "Installments (1 to ${Installments.MAX_COUNT})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            PixelDropdown(PlanEvery.entries.toList(), t.every, { it?.label ?: "" }, { e -> if (e != null) t.every = e }, Modifier.weight(1f), key = ModuleAccent.MINT.accent, label = "Every")
        }
        FieldLabel("First due")
        PixelDateField(firstDue, today, ModuleAccent.MINT.accent, { t.firstOverride = it }, min = date)
        PixelDropdown(FeeChoice.entries.toList(), t.choice, { it?.label ?: "" }, { c -> if (c != null) t.choice = c }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Fee")
        if (t.choice != FeeChoice.NONE) {
            PixelField(
                t.feeText, { t.feeText = if (t.choice == FeeChoice.RATE) it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) else money(it) }, Modifier.fillMaxWidth(),
                label = when (t.choice) { FeeChoice.PER -> "Fee on each installment"; FeeChoice.TOTAL -> "Total fee"; else -> "Percent each period" }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PixelChoice(listOf(false, true), t.interest, { if (it == true) "Interest" else "Fee" }, { if (it != null) t.interest = it }, label = "Charged as", key = ModuleAccent.MINT.accent)
        }
        if (schedule != null) {
            FieldLabel("Schedule")
            ScheduleTable(model, schedule, cur)
        }
        Text("Counts once as spending today; repayments are not spending.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** No, due, principal, fee and total of each installment, with a totals row. */
@Composable
private fun ScheduleTable(model: AppModel, rows: List<app.pokeplan.mint.Installment>, cur: String) {
    val mint = model.mint
    val style = MaterialTheme.typography.bodySmall
    val widths = listOf(0.45f, 1.0f, 1.45f, 1.2f, 1.5f)
    @Composable
    fun line(cells: List<String>, bold: Boolean = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Top) {
            cells.forEachIndexed { i, c ->
                Text(c, Modifier.weight(widths[i]), style = if (bold) MaterialTheme.typography.labelSmall else style, color = Pixel.Ink, textAlign = if (i < 2) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
    line(listOf("No", "Due", "Principal", "Fee", "Total"), bold = true)
    HorizontalDivider(color = Pixel.Border)
    rows.forEach { r -> line(listOf("${r.no}", r.dueDay.compact(), mint.show(r.principalMinor, cur), mint.show(r.feeMinor, cur), mint.show(r.principalMinor + r.feeMinor, cur))) }
    HorizontalDivider(color = Pixel.Border)
    val p = rows.sumOf { it.principalMinor }
    val f = rows.sumOf { it.feeMinor }
    line(listOf("", "Total", mint.show(p, cur), mint.show(f, cur), mint.show(p + f, cur)), bold = true)
}

private data class OweRow(val due: LocalDate, val amount: String = "", val fee: String = "", val feeOpen: Boolean = false)

/** Installments already owed (MINT.md 5.6A): a schedule without a purchase, for what was bought before tracking began. */
@Composable
private fun ExistingScheduleForm(model: AppModel, a: FinancialAccount, amounts: List<String>, fees: List<String>, onClose: () -> Unit) {
    val mint = model.mint
    val today = model.store.today
    val cur = a.currency
    val first = MintRules.nextOnDay(a.dueDay, today) ?: Installments.dueOn(today, PlanEvery.MONTH, 1)
    var name by remember { mutableStateOf("Existing installments") }
    val rows = remember {
        mutableStateListOf<OweRow>().also { l ->
            amounts.forEachIndexed { i, amt -> l.add(OweRow(Installments.dueOn(first, PlanEvery.MONTH, i), amt, fees.getOrElse(i) { "" }, fees.getOrElse(i) { "" }.isNotEmpty())) }
        }
    }
    var interest by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(true) }
    var refused by remember { mutableStateOf<String?>(null) }
    val principal = rows.map { Money.parse(it.amount, cur)?.takeIf { m -> m > 0 } }
    val total = principal.filterNotNull().sum()
    val months = principal.count { it != null }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Schedule name", singleLine = true)
        rows.forEachIndexed { i, r ->
            HorizontalDivider(color = Pixel.Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PixelDateField(r.due, today, ModuleAccent.MINT.accent, { d -> if (d != null) rows[i] = rows[i].copy(due = d) }, presets = emptyList()) }
                if (rows.size > 1) PixelIconButton("close", "Remove month ${i + 1}", { rows.removeAt(i) }, Pixel.InkSoft)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                PixelField(r.amount, { rows[i] = rows[i].copy(amount = money(it)) }, Modifier.weight(1f), label = "Amount owed", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (r.feeOpen) PixelField(r.fee, { rows[i] = rows[i].copy(fee = money(it)) }, Modifier.weight(1f), label = "Fee on top", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                else PixelButton("Fee", { rows[i] = rows[i].copy(feeOpen = true) }, color = Pixel.InkSoft, compact = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            if (rows.size < Installments.MAX_COUNT) PixelButton("Add month", { rows.add(OweRow(Installments.dueOn(rows.last().due, PlanEvery.MONTH, 1))) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Next 3 months", {
                val head = rows.first()
                rows.clear()
                repeat(3) { n -> rows.add(OweRow(Installments.dueOn(head.due, PlanEvery.MONTH, n), head.amount, if (n == 0) head.fee else "", head.feeOpen && n == 0)) }
            }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        if (rows.any { it.fee.isNotBlank() }) PixelChoice(listOf(false, true), interest, { if (it == true) "Interest" else "Fee" }, { if (it != null) interest = it }, label = "Charged as", key = ModuleAccent.MINT.accent)
        ToggleRow("Add to starting balance owed", addTo, detail = "The account owes ${mint.show(total, cur)} more. Turn off if you already entered what you owe as the starting balance.") { addTo = it }
        Text("Total ${mint.show(total, cur)} over $months ${if (months == 1) "month" else "months"}", style = MaterialTheme.typography.titleSmall)
        Text("Not spending: these were bought before you started tracking.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        refused?.let { Report(model, it, NoticeTone.WARNING) }
        run {
            FormSave("Add schedule", {
                var bad = false
                val list = rows.mapNotNull { r ->
                    val p = Money.parse(r.amount, cur)?.takeIf { it > 0 } ?: return@mapNotNull null
                    val f = if (r.fee.isBlank()) 0L else Money.parse(r.fee, cur)
                    if (f == null) { bad = true; return@mapNotNull null }
                    app.pokeplan.mint.Installment(0, r.due, p, f)
                }
                if (bad) refused = "Check the fee."
                else if (list.isEmpty()) refused = "Enter what is owed for at least one month."
                else mint.addExistingPlan(a.id, name, list, addTo, if (interest) FeeKind.INTEREST else FeeKind.FEE).let { if (it.record != null) onClose() else refused = it.refused }
            }, ModuleAccent.MINT.accent, enabled = name.isNotBlank() && months > 0)
            PixelButton("Cancel", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft)
        }
    }
}

/** One installment still owed and due within a window (or overdue), with its plan. */
internal class DueInstallment(val view: PlanView, val installment: app.pokeplan.mint.InstallmentView)

/** The installments of every plan with something still owed, due within [withinDays] or overdue, soonest first (MINT.md 5.6A). */
internal fun dueInstallments(model: AppModel, withinDays: Int): List<DueInstallment> {
    val limit = kotlinx.datetime.LocalDate.fromEpochDays(model.store.today.toEpochDays() + withinDays)
    return model.mint.planViews().flatMap { v ->
        v.installments.filter { it.remainingMinor > 0 && it.state != InstallmentState.PAID && it.state != InstallmentState.CANCELLED && it.dueDay <= limit }.map { DueInstallment(v, it) }
    }.sortedBy { it.installment.dueDay }
}

/** The "Installments due" box of the Bills screen: each row opens to pay it or bill its fee. */
@Composable
internal fun InstallmentsDueBox(model: AppModel, startOpenFirst: Boolean = false) {
    val mint = model.mint
    val due = dueInstallments(model, 30)
    if (due.isEmpty()) return
    var open by remember { mutableStateOf<String?>(if (startOpenFirst) due.first().view.plan.id + due.first().installment.no else null) }
    var refused by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Installments due")
            due.forEachIndexed { i, d ->
                val inst = d.installment
                val cur = d.view.plan.currency
                val key = d.view.plan.id + inst.no
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                MintLine(
                    onClick = { open = if (open == key) null else key },
                    onClickLabel = if (open == key) "Hide options" else "Show options",
                    trail = { Text(mint.show(inst.remainingMinor, cur), style = MaterialTheme.typography.titleSmall) },
                ) {
                    Text(d.view.plan.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${inst.dueDay.compact()} · installment ${inst.no} of ${d.view.installments.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (inst.state == InstallmentState.OVERDUE || inst.state == InstallmentState.PARTIAL) Text(inst.state.label, style = MaterialTheme.typography.bodyMedium, color = if (inst.state == InstallmentState.OVERDUE) Pixel.Warning else Pixel.InkSoft)
                }
                if (open == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    if (inst.feeMinor > 0 && !inst.feeRecorded) PixelButton("Bill it", { refused = mint.billInstallmentFee(d.view.plan.id, inst.no).note() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    PayArea(model, d.view, inst.remainingMinor, inst.no) { refused = it }
                }
            }
            refused?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}
