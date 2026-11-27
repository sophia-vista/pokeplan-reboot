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
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.BillMonth
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.FeeKind
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.InstallmentPlan
import app.pokeplan.mint.InstallmentState
import app.pokeplan.mint.Installments
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.MonthlyBill
import app.pokeplan.mint.MonthlyBills
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
    var openBill by remember(a.id) { mutableStateOf<BillMonth?>(null) }
    var allBills by remember(a.id) { mutableStateOf(false) }
    var howItWorks by remember(a.id) { mutableStateOf(false) }
    var statementPay by remember(a.id) { mutableStateOf<Long?>(null) }
    var recordingStatement by remember(a.id) { mutableStateOf(false) }
    var statementMinimum by remember(a.id) { mutableStateOf("") }
    val statementCycle = mint.currentCardCycle(a.id)
    if (howItWorks) CardFlowInfo { howItWorks = false }
    if (buying) Dialog({ buying = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            FormBox { Column(Modifier.verticalScroll(rememberScrollState())) { NewPurchaseForm(model, a, startExisting = existing) { buying = false } } }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PixelTitle("Credit line") }
                PixelButton("?", { howItWorks = true }, color = Pixel.InkSoft, compact = true)
            }
            if (a.limitMinor > 0) MintValueRow("Limit", mint.show(a.limitMinor, a.currency))
            MintValueRow("Owed now", mint.show(owed, a.currency))
            MintRules.leftToSpend(a, owed)?.let { left ->
                MintValueRow("Available credit", mint.show(left, a.currency))
                if (owed > a.limitMinor) Text("Over the limit by ${mint.show(owed - a.limitMinor, a.currency)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
            }
            listOf("Purchases" to act.purchasesMinor, "Fees" to act.feesMinor, "Interest" to act.interestMinor, "Late fees" to act.lateFeesMinor, "Refunds" to act.refundsMinor, "Repaid" to act.repaymentsMinor)
                .filter { it.second != 0L }.forEach { (label, minor) -> MintValueRow(label, mint.show(minor, a.currency), soft = true) }
            data.creditStatements.filter { it.accountId == a.id }.maxByOrNull { it.statementDay }?.let { st ->
                MintValueRow("Last statement", mint.show(st.statementBalanceMinor, a.currency), soft = true)
                if (st.minimumPaymentMinor > 0) MintValueRow("Minimum due", mint.show(st.minimumPaymentMinor, a.currency), soft = true)
                Text("Pay by ${st.dueDay.compact()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            statementCycle?.takeIf { it.billedMinor > 0 }?.let { c ->
                val state = c.state(today)
                SectionLabel("This statement")
                MintValueRow("Billed on " + c.statementDay.compact(), mint.show(c.billedMinor, a.currency))
                if (c.minimumMinor > 0) MintValueRow("Minimum due", mint.show(c.minimumMinor, a.currency), soft = true)
                if (c.paidMinor > 0) MintValueRow("Paid since", mint.show(c.paidMinor, a.currency), soft = true)
                MintValueRow("Due " + c.dueDay.compact(), state.label + " \u00b7 " + mint.show(c.remainingMinor, a.currency), soft = state != app.pokeplan.mint.CardCycles.State.OVERDUE)
                if (state != app.pokeplan.mint.CardCycles.State.PAID) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        if (c.minimumMinor > 0) PixelButton("Pay minimum", { statementPay = c.minimumMinor }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        PixelButton("Pay in full", { statementPay = c.remainingMinor }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    }
                    statementPay?.let { amount ->
                        PayForm(model, "statement-" + a.id, a.currency, amount, amount, "Paying a statement is a transfer to the card, never spending.", problem) { minor, source, day ->
                            mint.payStatement(a.id, minor, source, day, statementId = c.statementId).note()
                        }
                        PixelButton("Not now", { statementPay = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                    if (c.statementId == null && !recordingStatement) {
                        PixelButton("Record the card's statement", { recordingStatement = true; statementMinimum = "" }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                    if (recordingStatement) {
                        Text("The card's own figures: " + mint.show(c.billedMinor, a.currency) + " billed on " + c.statementDay.compact() + ", due " + c.dueDay.compact() + ". The lender's minimum is yours to enter.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        PixelField(
                            statementMinimum, { statementMinimum = money(it) }, Modifier.fillMaxWidth(), label = "Minimum payment", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = a.currency,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Save statement", {
                                val min = Money.parse(statementMinimum, a.currency)
                                if (min == null) problem("Check the minimum payment.")
                                else {
                                    val out = mint.recordStatementFromCycle(a.id, min)
                                    if (out.record == null) problem(out.refused) else { problem(null); model.say("Statement saved."); recordingStatement = false; statementMinimum = "" }
                                }
                            }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                            PixelButton("Cancel", { recordingStatement = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
            dayLine("Statement", a.statementDay, today)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            dayLine("Due", a.dueDay, today)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            // A card added since its last statement day has nothing billed yet: say when the first one closes (5.6B).
            if (statementCycle == null) app.pokeplan.mint.CardCycles.next(a, today)?.let { (close, due) ->
                Text("Nothing has closed since this account was added. The next statement closes on " + close.compact() + ", due " + due.compact() + ".", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            val bills = mint.openBills(a.id)
            if (plans.isNotEmpty() && !model.mintData.settings.billReminders) Text("Bill reminders are off.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (bills.isNotEmpty()) {
                SectionLabel("Monthly bills")
                val shown = if (allBills) bills else bills.take(BILLS_SHOWN)
                shown.forEach { BillRow(model, it, openBill == it.month, { openBill = if (openBill == it.month) null else it.month }, problem) }
                if (bills.size > BILLS_SHOWN) MintLine(onClick = { allBills = !allBills }, onClickLabel = if (allBills) "Show fewer months" else "Show later months") {
                    Text(if (allBills) "Fewer months" else "Later months (${bills.size - BILLS_SHOWN})", style = MaterialTheme.typography.bodyLarge)
                }
                if (mint.misalignedInstallments(a.id) > 0) PixelButton("Use the ${ordinal(a.dueDay)} for every bill", { problem(null); mint.alignInstallmentsToDueDay(a.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            val live = plans.filter { !it.finished }
            val done = plans.filter { it.finished }
            if (plans.isNotEmpty()) SectionLabel("Purchases")
            live.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            if (done.isNotEmpty()) {
                MintLine(onClick = { finished = !finished }, onClickLabel = if (finished) "Hide finished plans" else "Show finished plans") { Text("Finished (${done.size})", style = MaterialTheme.typography.bodyLarge) }
                if (finished) done.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            }
            if (owed > 0 && a.status == AccountStatus.ACTIVE) PixelButton(
                if (a.type == AccountType.CREDIT_CARD) "Pay card" else "Pay balance",
                { model.mintStartType = TxType.TRANSFER; model.mintStartTo = a.id; model.mintSection = MintSection.ACTIVITY },
                Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true,
            )
            PixelButton("Add purchase", { existing = false; buying = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
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

private const val BILLS_SHOWN = 3

private fun BillMonth.label(): String = "${first.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} $year"

@Composable
internal fun BillRow(model: AppModel, bill: MonthlyBill, open: Boolean, toggle: () -> Unit, problem: (String?) -> Unit) {
    val mint = model.mint
    val cur = bill.currency
    var inspect by remember(bill.accountId, bill.month) { mutableStateOf<String?>(null) }
    HorizontalDivider(color = Pixel.Border)
    MintLine(onClick = toggle, onClickLabel = if (open) "Hide bill" else "Show bill", trail = { Text(mint.show(bill.remainingMinor, cur), style = MaterialTheme.typography.titleSmall) }) {
        Text(bill.month.label(), style = MaterialTheme.typography.bodyLarge)
        Text(
            listOf("Due ${bill.dueDay.compact()}", if (bill.lines.size == 1) "1 purchase" else "${bill.lines.size} purchases").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = if (bill.state == InstallmentState.OVERDUE) Pixel.Warning else Pixel.InkSoft,
        )
        if (bill.paidMinor > 0) Text("Paid ${mint.show(bill.paidMinor, cur)} of ${mint.show(bill.totalMinor, cur)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        bill.lines.forEach { l ->
            MintLine(
                onClick = { inspect = if (inspect == l.planId) null else l.planId }, onClickLabel = if (inspect == l.planId) "Hide purchase" else "Show purchase",
                trail = { Text(mint.show(l.remainingMinor, cur), style = MaterialTheme.typography.bodyLarge) },
            ) {
                Text(l.planName, style = MaterialTheme.typography.bodyLarge)
                Text("${l.no} of ${l.count}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            if (inspect == l.planId) PlanOptions(model, l.planId, problem)
        }
        HorizontalDivider(color = Pixel.Border)
        PayForm(model, "${bill.accountId}${bill.month}", cur, bill.remainingMinor, bill.remainingMinor, caption = null, onResult = problem) { minor, source, day ->
            mint.payBill(bill.accountId, bill.month, minor, source, day).note()
        }
    }
}

@Composable
internal fun PayArea(model: AppModel, v: PlanView, defaultMinor: Long, resetKey: Any, onResult: (String?) -> Unit) {
    PayForm(model, v.plan.id, v.plan.currency, defaultMinor, resetKey, "Early or part payments go to the earliest installment first.", onResult) { minor, source, day ->
        model.mint.payPlan(v.plan.id, minor, source, day).note()
    }
}

@Composable
private fun PayForm(
    model: AppModel, key: String, cur: String, defaultMinor: Long, resetKey: Any, caption: String?, onResult: (String?) -> Unit, pay: (Long, String, LocalDate) -> String?,
) {
    val today = model.store.today
    val sources = model.mintData.accounts.filter { it.status == AccountStatus.ACTIVE && !it.type.liability && it.currency == cur }
    var amount by remember(key, resetKey, defaultMinor) { mutableStateOf(Money.plain(defaultMinor, cur)) }
    var from by remember(key) { mutableStateOf(sources.firstOrNull()?.id) }
    var payDay by remember(key) { mutableStateOf(today) }
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
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        FormSave("Pay", {
            val m = Money.parse(amount, cur)
            val src = from
            onResult(if (m == null) "Check the amount." else if (src == null) "Choose where to pay from." else pay(m, src, payDay))
        }, ModuleAccent.MINT.accent, enabled = amount.isNotBlank() && from != null)
    }
}

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
    var deleting by remember(planId) { mutableStateOf(false) }
    var refused by remember(planId) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        when {
            editingPlan -> {
                PixelField(editAmount, { editAmount = money(it) }, Modifier.fillMaxWidth(), label = "Purchase amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = cur)
                InstallmentTerms(model, planAccount, editTerms, Money.parse(editAmount, cur), data.transactions.firstOrNull { it.id == v.plan.purchaseTxId }?.day ?: v.plan.startDay)
                Text("Only the schedule changes: the purchase stays one expense, and nothing already recorded is moved.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FormSave("Save plan", {
                    val total = Money.parse(editAmount, cur)
                    val count = editTerms.count
                    val fee = editTerms.fee(cur)
                    refused = if (total == null) "Check the amount."
                    else if (count == null) "Choose 1 to ${Installments.MAX_COUNT} installments."
                    else if (fee == null) "Check the fee."
                    else mint.updatePlanTerms(planId, editName, total, count, editTerms.every, editTerms.firstDue(planAccount, data.transactions.firstOrNull { it.id == v.plan.purchaseTxId }?.day ?: v.plan.startDay), fee, editTerms.kind).note()
                    if (refused == null) editingPlan = false
                }, ModuleAccent.MINT.accent)
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
                if (mint.canEditPlanTerms(planId)) PixelButton("Edit schedule", { editingPlan = true }, color = Pixel.InkSoft, compact = true)
            }
            // A locked schedule says so, instead of an Edit key quietly missing (MINT.md 5.6C).
            mint.planLockReason(planId)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
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
                    if (mint.installmentLockReason(planId, i.no) == null) PixelButton("Edit", {
                        action = if (action == i.no to "edit") null else i.no to "edit"
                        editDue = i.dueDay
                        editPrincipal = Money.plain(i.principalMinor, cur)
                        editFee = Money.plain(i.feeMinor, cur)
                    }, color = Pixel.InkSoft, compact = true)
                }
                mint.installmentLockReason(planId, i.no)?.let { Text("Locked \u00b7 $it", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
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
                        else mint.installmentLockReason(planId, i.no) ?: "The due days must stay in order, and the principal above zero." 
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
        if (deleting) {
            val preview = mint.planDeletion(planId)
            if (preview != null) {
                PixelTitle(if (preview.canDelete) "Delete the purchase and its schedule" else "It cannot be deleted")
                preview.purchaseId?.let { id ->
                    data.transactions.firstOrNull { it.id == id }?.let { t ->
                        MintValueRow("Purchase record", t.payee + " \u00b7 " + mint.show(t.amountMinor, t.currency), soft = true)
                    }
                }
                MintValueRow("Installments", preview.installments.toString(), soft = true)
                if (preview.payments > 0) MintValueRow("Payments recorded", preview.payments.toString() + " \u00b7 " + mint.show(preview.paidMinor, cur), soft = true)
                if (preview.fees > 0) MintValueRow("Fees recorded", preview.fees.toString() + " \u00b7 " + mint.show(preview.feeMinor, cur), soft = true)
                if (preview.refunds > 0) MintValueRow("Refunds recorded", preview.refunds.toString() + " \u00b7 " + mint.show(preview.refundMinor, cur), soft = true)
                Text(
                    if (preview.canDelete) "The purchase record and the schedule go together. No other record changes." else preview.blockedReason.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    if (preview.canDelete) PixelButton("Delete both", {
                        val out = mint.deletePlanPurchase(planId)
                        refused = if (out.record == true) null else (out.refused ?: "That could not be deleted.")
                        if (refused == null) { deleting = false; problem(null) }
                    }, Modifier.weight(1f), Pixel.Danger, compact = true)
                    PixelButton("Keep them", { deleting = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        } else if (asking) Confirm(
            if (v.plan.purchaseTxId != null) "Remove the installment schedule? The purchase record stays in Activity as one expense, and anything already recorded on the schedule stays too." else "Remove the installment schedule? The opening balance stays as it is.",
            "Remove schedule", "Keep it",
            onConfirm = { mint.deletePlan(planId); asking = false; problem(null) }, onKeep = { asking = false },
        ) else {
            PixelField(refund, { refund = money(it) }, Modifier.fillMaxWidth(), label = "Refund", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Refund", {
                    val m = Money.parse(refund, cur)
                    refused = if (m == null) "Check the amount." else mint.refundPlan(planId, m).note()
                    if (refused == null) refund = ""
                }, Modifier.weight(1f), Pixel.InkSoft, compact = true, enabled = refund.isNotBlank())
                PixelButton("Remove schedule", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (v.plan.purchaseTxId != null) PixelButton("Delete purchase and schedule\u2026", { deleting = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

internal enum class FeeChoice(val label: String) { NONE("None"), PER("Per installment"), TOTAL("Total"), RATE("Rate per period") }

internal class TermsState(count: String = "3", fee: String = "") {
    var countText by mutableStateOf(count)
    var every by mutableStateOf(PlanEvery.MONTH)
    var firstOverride by mutableStateOf<LocalDate?>(null)
    var choice by mutableStateOf(if (fee.isEmpty()) FeeChoice.NONE else FeeChoice.RATE)
    var feeText by mutableStateOf(fee)
    var interest by mutableStateOf(false)
    val count: Int? get() = countText.toIntOrNull()?.takeIf { it in 1..Installments.MAX_COUNT }
    val kind: FeeKind get() = if (interest) FeeKind.INTEREST else FeeKind.FEE
    fun firstDue(a: FinancialAccount, date: LocalDate): LocalDate = firstOverride ?: MonthlyBills.firstDue(a, date, every)
    fun fee(cur: String): FeeRule? = when (choice) {
        FeeChoice.NONE -> FeeRule.None
        FeeChoice.PER -> Money.parse(feeText, cur)?.let { FeeRule.PerInstallment(it) }
        FeeChoice.TOTAL -> Money.parse(feeText, cur)?.let { FeeRule.Total(it) }
        FeeChoice.RATE -> percentToBasisPoints(feeText)?.let { FeeRule.RatePerPeriod(it) }
    }
}

private fun termsFor(plan: InstallmentPlan): TermsState = TermsState(plan.installments.size.toString()).apply {
    every = plan.every
    firstOverride = plan.installments.firstOrNull()?.dueDay ?: plan.startDay
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

internal fun createPlanPurchase(mint: app.pokeplan.app.mint.MintStore, a: FinancialAccount, name: String, total: Long?, categoryId: String?, date: LocalDate, t: TermsState): String? {
    val count = t.count
    val fee = t.fee(a.currency)
    return if (total == null) "Check the amount."
    else if (count == null) "Choose 1 to ${Installments.MAX_COUNT} installments."
    else if (fee == null) "Check the fee."
    else mint.addPlanPurchase(a.id, name, total, categoryId, count, t.every, t.firstDue(a, date), fee, t.kind, date).let { if (it.record != null) null else (it.refused ?: "That could not be saved.") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstallmentTerms(model: AppModel, a: FinancialAccount, t: TermsState, total: Long?, date: LocalDate) {
    val today = model.store.today
    val cur = a.currency
    val count = t.count
    val fee = t.fee(cur)
    val firstDue = t.firstDue(a, date)
    val schedule = if (total != null && total > 0 && count != null && fee != null && Installments.checkRequest(a.type, total, count, fee) == app.pokeplan.mint.Check.Ok) Installments.generate(total, count, t.every, firstDue, fee) else null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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

internal class DueBill(val account: FinancialAccount, val bill: MonthlyBill)

internal fun dueBills(model: AppModel, withinDays: Int): List<DueBill> {
    val limit = kotlinx.datetime.LocalDate.fromEpochDays(model.store.today.toEpochDays() + withinDays)
    return model.mintData.accounts.filter { Installments.allowedFor(it.type) }
        .flatMap { account -> model.mint.openBills(account.id).filter { it.dueDay <= limit }.map { DueBill(account, it) } }
        .sortedBy { it.bill.dueDay }
}

/**
 * The card statements due soon, as one place to see what a credit line billed and pay it (MINT.md 5.6D). Each payment is
 * a transfer, never spending, and the cycle is only what Mint derives from the card's configured statement and due days.
 */
@Composable
internal fun CardStatementsDueBox(model: AppModel) {
    val mint = model.mint
    val today = model.store.today
    val due = remember(model.mintData) { mint.cardObligationsWithin(30) }
    if (due.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Card statements due")
            due.forEach { (account, cycle) ->
                val state = cycle.state(today)
                HorizontalDivider(color = Pixel.Border)
                MintLine(trail = { Text(mint.show(cycle.remainingMinor, account.currency), style = MaterialTheme.typography.titleSmall) }) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Billed " + cycle.statementDay.compact() + " \u00b7 due " + cycle.dueDay.compact() + " \u00b7 " + state.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == app.pokeplan.mint.CardCycles.State.OVERDUE) Pixel.Warning else Pixel.InkSoft,
                    )
                }
                PixelButton("Pay statement", {
                    model.mintStartType = TxType.TRANSFER
                    model.mintStartTo = account.id
                    model.mintStartAmount = Money.plain(cycle.remainingMinor, account.currency)
                    model.mintSection = MintSection.ACTIVITY
                }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
internal fun InstallmentsDueBox(model: AppModel, startOpenFirst: Boolean = false) {
    val mint = model.mint
    val due = dueBills(model, 30)
    if (due.isEmpty()) return
    fun keyOf(d: DueBill) = d.account.id + d.bill.month
    var open by remember { mutableStateOf<String?>(if (startOpenFirst) keyOf(due.first()) else null) }
    var refused by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Credit bills due")
            due.forEachIndexed { i, d ->
                val key = keyOf(d)
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                MintLine(
                    onClick = { open = if (open == key) null else key },
                    onClickLabel = if (open == key) "Hide options" else "Show options",
                    trail = { Text(mint.show(d.bill.remainingMinor, d.bill.currency), style = MaterialTheme.typography.titleSmall) },
                ) {
                    Text(d.account.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${d.bill.dueDay.compact()} · ${if (d.bill.lines.size == 1) "1 purchase" else "${d.bill.lines.size} purchases"}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (d.bill.state == InstallmentState.OVERDUE || d.bill.state == InstallmentState.PARTIAL) {
                        Text(d.bill.state.label, style = MaterialTheme.typography.bodyMedium, color = if (d.bill.state == InstallmentState.OVERDUE) Pixel.Warning else Pixel.InkSoft)
                    }
                }
                if (open == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    d.bill.lines.forEach { l ->
                        MintValueRow("${l.planName} · ${l.no} of ${l.count}", mint.show(l.remainingMinor, d.bill.currency), soft = true)
                    }
                    PayForm(model, key, d.bill.currency, d.bill.remainingMinor, d.bill.remainingMinor, caption = null, onResult = { refused = it }) { minor, source, day ->
                        mint.payBill(d.account.id, d.bill.month, minor, source, day).note()
                    }
                }
            }
            refused?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

@Composable
private fun CardFlowInfo(onClose: () -> Unit) {
    val steps = listOf(
        "Spend" to "Record an Expense on the card. It raises Owed now and counts as spending once, on the day you spent.",
        "Statement" to "When the lender's statement arrives, add it under Statements. It is a note of what was billed; it changes nothing you owe.",
        "Pay" to "Pay card moves money from a bank or wallet to the card. Owed now and that account fall together. It is not spending.",
        "Fees and interest" to "Add a late fee or interest as a Fee or interest on the card. It raises Owed now and counts as spending.",
        "Limit" to "The limit and Available credit are information. They are never counted as your money.",
    )
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            TextBox {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PixelTitle("How a card works")
                    steps.forEach { (title, note) ->
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                    PixelButton("Close", onClose, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
                }
            }
        }
    }
}
