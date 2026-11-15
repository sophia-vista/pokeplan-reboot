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
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.TabButton
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

private fun money(raw: String) = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = TabButton(label, selected, onClick, key = ModuleAccent.MINT.accent)

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
            TextBox { Column(Modifier.verticalScroll(rememberScrollState())) { NewPurchaseForm(model, a, startExisting = existing) { buying = false } } }
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
            val live = plans.filter { !it.finished }
            val done = plans.filter { it.finished }
            if (plans.isNotEmpty()) SectionLabel("Installment plans")
            live.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            if (done.isNotEmpty()) {
                MintLine(onClick = { finished = !finished }, onClickLabel = if (finished) "Hide finished plans" else "Show finished plans") { Text("Finished (${done.size})", style = MaterialTheme.typography.bodyLarge) }
                if (finished) done.forEach { PlanRow(model, it, open == it.plan.id, { open = if (open == it.plan.id) null else it.plan.id }, problem) }
            }
            PixelButton("New installment purchase", { existing = false; buying = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            if (plans.isEmpty()) PixelButton("Already owe installments? Add them", { existing = true; buying = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
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
    var amount by remember(planId, next?.no, next?.remainingMinor) { mutableStateOf(next?.let { Money.plain(it.remainingMinor, cur) } ?: "") }
    val sources = data.accounts.filter { it.status == AccountStatus.ACTIVE && !it.type.liability && it.currency == cur }
    var from by remember(planId) { mutableStateOf(sources.firstOrNull()?.id) }
    var payDay by remember(planId) { mutableStateOf(today) }
    var action by remember(planId) { mutableStateOf<Pair<Int, String>?>(null) }
    var late by remember(planId) { mutableStateOf("") }
    var editDue by remember(planId) { mutableStateOf(today) }
    var editFee by remember(planId) { mutableStateOf("") }
    var refund by remember(planId) { mutableStateOf("") }
    var asking by remember(planId) { mutableStateOf(false) }
    var refused by remember(planId) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
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
                    if (i.paidMinor == 0L && !i.feeRecorded) PixelButton("Edit", { action = if (action == i.no to "edit") null else i.no to "edit"; editDue = i.dueDay; editFee = Money.plain(i.feeMinor, cur) }, color = Pixel.InkSoft, compact = true)
                }
                if (action == i.no to "late") {
                    PixelField(late, { late = money(it) }, Modifier.fillMaxWidth(), label = "Late fee", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    PixelButton("Add late fee", {
                        val m = Money.parse(late, cur)
                        refused = if (m == null) "Check the amount." else mint.addLateFee(planId, i.no, m).note()
                        if (refused == null) action = null
                    }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true, enabled = late.isNotBlank())
                }
                if (action == i.no to "edit") {
                    PixelDateField(editDue, today, ModuleAccent.MINT.accent, { editDue = it ?: editDue }, presets = emptyList())
                    PixelField(editFee, { editFee = money(it) }, Modifier.fillMaxWidth(), label = "Fee", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    PixelButton("Save", {
                        val m = Money.parse(editFee.ifBlank { "0" }, cur)
                        refused = if (m == null) "Check the fee." else if (mint.updateInstallment(planId, i.no, editDue, m)) null else "That installment can no longer be changed."
                        if (refused == null) action = null
                    }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                }
            }
        }
        if (next != null) {
            HorizontalDivider(color = Pixel.Border)
            SectionLabel("Pay")
            PixelField(amount, { amount = money(it) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (sources.isNotEmpty()) {
                SectionLabel("Pay from")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    sources.forEach { s -> Chip(s.name, s.id == from) { from = s.id } }
                }
            }
            PixelDateField(payDay, today, ModuleAccent.MINT.accent, { payDay = it ?: today }, max = today)
            Text("Early or part payments go to the earliest installment first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelButton("Pay", {
                val m = Money.parse(amount, cur)
                val src = from
                refused = if (m == null) "Check the amount." else if (src == null) "Choose where to pay from." else mint.payPlan(planId, m, src, payDay).note()
                problem(refused)
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true, enabled = amount.isNotBlank() && from != null)
        }
        val history = data.transactions.filter { it.planId == planId && it.type == TxType.TRANSFER }.sortedByDescending { it.atMillis }
        if (history.isNotEmpty()) {
            HorizontalDivider(color = Pixel.Border)
            SectionLabel("Payments")
            history.forEach { t ->
                MintValueRow("${t.day.compact()} · ${data.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()}", mint.show(t.amountMinor, t.currency), soft = true)
            }
        }
        HorizontalDivider(color = Pixel.Border)
        if (asking) Confirm("Your records stay; only the schedule is removed.", "Delete plan", onConfirm = { mint.deletePlan(planId); asking = false }, onKeep = { asking = false })
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

private enum class FeeChoice(val label: String) { NONE("None"), PER("Per installment"), TOTAL("Total"), RATE("% per period") }

/** The new installment purchase form (MINT.md 5.6A): the purchase, how it is split, an optional fee or interest, and the live schedule it will get. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewPurchaseForm(
    model: AppModel, a: FinancialAccount, startName: String = "", startAmount: String = "", startCount: String = "3", startFee: String = "", startExisting: Boolean = false,
    existingAmounts: List<String> = listOf(""), existingFees: List<String> = listOf(""), onClose: () -> Unit,
) {
    var existing by remember { mutableStateOf(startExisting) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("New purchase", !existing) { existing = false }
            Chip("Already owed", existing) { existing = true }
        }
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
    var countText by remember { mutableStateOf(startCount) }
    var every by remember { mutableStateOf(PlanEvery.MONTH) }
    var firstOverride by remember { mutableStateOf<LocalDate?>(null) }
    var choice by remember { mutableStateOf(if (startFee.isEmpty()) FeeChoice.NONE else FeeChoice.RATE) }
    var feeText by remember { mutableStateOf(startFee) }
    var interest by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<String?>(null) }
    val total = Money.parse(amount, cur)?.takeIf { it > 0 }
    val count = countText.toIntOrNull()?.takeIf { it in 1..Installments.MAX_COUNT }
    val firstDue = firstOverride ?: Installments.dueOn(date, every, 1)
    val fee: FeeRule? = when (choice) {
        FeeChoice.NONE -> FeeRule.None
        FeeChoice.PER -> Money.parse(feeText, cur)?.let { FeeRule.PerInstallment(it) }
        FeeChoice.TOTAL -> Money.parse(feeText, cur)?.let { FeeRule.Total(it) }
        FeeChoice.RATE -> percentToBasisPoints(feeText)?.let { FeeRule.RatePerPeriod(it) }
    }
    val schedule = if (total != null && count != null && fee != null && Installments.checkRequest(a.type, total, count, fee) == app.pokeplan.mint.Check.Ok) Installments.generate(total, count, every, firstDue, fee) else null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelTitle("New installment purchase")
        PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true)
        PixelField(amount, { amount = money(it) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        SectionLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("None", categoryId == null) { categoryId = null }
            model.mintData.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.forEach { c -> Chip(c.name, c.id == categoryId) { categoryId = c.id } }
        }
        SectionLabel("Date")
        PixelDateField(date, today, ModuleAccent.MINT.accent, { date = it ?: today }, max = today)
        SectionLabel("Installments")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(1, 3, 6, 9, 12, 18, 24).forEach { n -> Chip("$n", count == n) { countText = "$n" } }
        }
        PixelField(countText, { countText = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = "Or any number, 1 to ${Installments.MAX_COUNT}", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        SectionLabel("Every")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PlanEvery.entries.forEach { e -> Chip(e.label, e == every) { every = e } }
        }
        SectionLabel("First due")
        PixelDateField(firstDue, today, ModuleAccent.MINT.accent, { firstOverride = it }, min = date)
        SectionLabel("Fee")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FeeChoice.entries.forEach { c -> Chip(c.label, c == choice) { choice = c } }
        }
        if (choice != FeeChoice.NONE) {
            PixelField(
                feeText, { feeText = if (choice == FeeChoice.RATE) it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) else money(it) }, Modifier.fillMaxWidth(),
                label = when (choice) { FeeChoice.PER -> "Fee on each installment"; FeeChoice.TOTAL -> "Total fee"; else -> "Percent each period" }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("Fee", !interest) { interest = false }
                Chip("Interest", interest) { interest = true }
            }
        }
        if (schedule != null) {
            SectionLabel("Schedule")
            ScheduleTable(model, schedule, cur)
        }
        Text("Counts once as spending today; repayments are not spending.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        refused?.let { Report(model, it, NoticeTone.WARNING) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Create", {
                if (total == null) refused = "Check the amount."
                else if (count == null) refused = "Choose 1 to ${Installments.MAX_COUNT} installments."
                else if (fee == null) refused = "Check the fee."
                else mint.addPlanPurchase(a.id, name, total, categoryId, count, every, firstDue, fee, if (interest) FeeKind.INTEREST else FeeKind.FEE, date).let { if (it.record != null) onClose() else refused = it.refused }
            }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true, enabled = name.isNotBlank())
            PixelButton("Cancel", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
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
        PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true)
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
        if (rows.any { it.fee.isNotBlank() }) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("Fee", !interest) { interest = false }
            Chip("Interest", interest) { interest = true }
        }
        ToggleRow("Add to starting balance owed", addTo, detail = "The account owes ${mint.show(total, cur)} more. Turn off if you already entered what you owe as the starting balance.") { addTo = it }
        Text("Total ${mint.show(total, cur)} over $months ${if (months == 1) "month" else "months"}", style = MaterialTheme.typography.titleSmall)
        Text("Not spending: these were bought before you started tracking.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        refused?.let { Report(model, it, NoticeTone.WARNING) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Add schedule", {
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
            }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true, enabled = name.isNotBlank() && months > 0)
            PixelButton("Cancel", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}
