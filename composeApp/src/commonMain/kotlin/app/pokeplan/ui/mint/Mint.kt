package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pokeplan.ui.theme.PixelShape
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.mint.CardStyle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.ui.media.MediaItem
import app.pokeplan.ui.media.MediaSources
import app.pokeplan.ui.media.PhotoViewerConfig
import app.pokeplan.ui.media.ViewerBackdrop
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pokeplan.app.Tutorials
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.Budget
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.Installments
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.EarnSchedule
import app.pokeplan.mint.Money
import app.pokeplan.mint.Occurrence
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.Transaction
import app.pokeplan.mint.TransactionSplit
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.DeviceGate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import app.pokeplan.ui.theme.Accordion
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.screens.PullRevealSlot
import app.pokeplan.ui.screens.PullRevealState
import app.pokeplan.ui.screens.pullReveal
import app.pokeplan.ui.screens.rememberPullReveal
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.pokemon.MintPartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.Confirm
import app.pokeplan.ui.screens.FirstRecordEgg
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuGrid
import app.pokeplan.ui.screens.SectionsKey
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.ModuleLeadStage
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormDivider
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelMomentField
import app.pokeplan.ui.screens.ScreenLockGate
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

enum class MintSection(val label: String, val icon: String) {
    ACCOUNTS("Accounts", "member-card"), ACTIVITY("Activity", "journal"), PLANS("Plans", "heart-scale"),
    REVIEW("Review", "scope-lens"), IMPORT("Import", "town-map"), SETTINGS("Settings", "storage-key"),
}

private val zone: TimeZone get() = TimeZone.currentSystemDefault()
private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.MINT.accent
internal fun percentText(basisPoints: Int): String {
    if (basisPoints == 0) return ""
    val fraction = (basisPoints % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) (basisPoints / 100).toString() else "${basisPoints / 100}.$fraction"
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

@Composable
internal fun MintLine(onClick: (() -> Unit)? = null, onClickLabel: String? = null, trail: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target)
            .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
            .padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}

@Composable
internal fun MintValueRow(label: String, value: String, soft: Boolean = false) {
    // Both sides get a share of the row: an unweighted value used to take its full width first and leave the label a
    // single character wide, which stacked "Cash flow" one letter per line on a long value (27 November 2026).
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f).alignByBaseline(), style = if (soft) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = if (soft) Pixel.InkSoft else Pixel.Ink)
        Text(
            value, Modifier.weight(1.2f).alignByBaseline(), textAlign = TextAlign.End,
            style = if (soft) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
        )
    }
}

internal fun mintSubtitle(model: AppModel): String {
    val n = model.mintData.accounts.count { it.status == AccountStatus.ACTIVE }
    return if (n == 0) "money in and out" else if (n == 1) "1 account" else "$n accounts"
}

@Composable
internal fun MintScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenLockGate(model, Module.MINT, model.mintData.settings.gate) { ScreenSwitch(model.mintSection, depth = { if (it == null) 0 else 1 }) { section -> MintPage(model, section, openPokemon) } }
}

/** The Activity search and filter, held by the page so the pull-down block and the list share them. */
internal class ActivityFind {
    var query by mutableStateOf("")
    var filter by mutableStateOf<TxType?>(null)
    var showFilter by mutableStateOf(false)
    val active: Boolean get() = query.isNotBlank() || filter != null
    fun clear() { query = ""; filter = null; showFilter = false }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MintPage(model: AppModel, section: MintSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        MintMenu(model, toModules, openPokemon)
        return
    }
    val scroll = rememberScrollState()
    val find = remember { ActivityFind() }
    val reveal = rememberPullReveal(scroll, keepOpen = { find.active }, onClose = { find.clear() })
    SectionScaffold(
        section.label, null, onBack = { model.mintSection = null }, icon = section.icon, color = Module.MINT.look.header,
        scrollState = scroll, bodyModifier = if (section == MintSection.ACTIVITY) Modifier.pullReveal(reveal) else Modifier,
    ) {
        when (section) {
            MintSection.ACCOUNTS -> AccountsView(model)
            MintSection.ACTIVITY -> ActivityView(
                model, find, reveal, startAmount = model.mintStartAmount.orEmpty(), startPayee = model.mintStartPayee.orEmpty(),
                startAccount = model.mintStartAccount, startCategory = model.mintStartCategory,
            )
            MintSection.PLANS -> PlansPlace(model)
            MintSection.REVIEW -> MintReviewView(model)
            MintSection.IMPORT -> MintImportView(model)
            MintSection.SETTINGS -> MintSettingsView(model)
        }
    }
}

/** Plans is one place with two halves (MINT.md 3): the budgets, and the bills and income. */
@Composable
private fun PlansPlace(model: AppModel) {
    val bills = model.mintPlansTab == 1
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        TabButton("Budgets", !bills, { model.mintPlansTab = 0 }, Modifier.weight(1f))
        TabButton("Bills and income", bills, { model.mintPlansTab = 1 }, Modifier.weight(1f))
    }
    if (bills) BillsView(model) else BudgetsView(model)
}

@Composable
private fun MintMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.mintData
    val imports = data.transactions.mapNotNull { it.importBatch }.toSet().size
    val activeBudgets = data.budgets.count { it.status == PlanStatus.ACTIVE }
    val plansDetail = if (activeBudgets == 0) billsDetail(model) else "${if (activeBudgets == 1) "1 budget" else "$activeBudgets budgets"}, ${billsDetail(model)}"
    val sections = listOf(
        MenuEntry(MintSection.ACCOUNTS.name, MintSection.ACCOUNTS.icon, "Accounts", if (data.accounts.isEmpty()) "add one" else "${data.accounts.count { it.status == AccountStatus.ACTIVE }} open", tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.ACTIVITY.name, MintSection.ACTIVITY.icon, "Activity", if (data.transactions.isEmpty()) "add a purchase" else "${data.transactions.size} records", tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.PLANS.name, MintSection.PLANS.icon, "Plans", plansDetail, tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.REVIEW.name, MintSection.REVIEW.icon, "Review", reviewDetail(model), tint = ModuleAccent.MINT.accent),
    ) + listOfNotNull(
        MenuEntry(MintSection.IMPORT.name, MintSection.IMPORT.icon, "Import", if (imports == 0) "statement file" else "$imports imports", tint = ModuleAccent.MINT.accent).takeIf { model.textFiles != null },
        MenuEntry(MintSection.SETTINGS.name, MintSection.SETTINGS.icon, "Settings", "Ledger Research, privacy, your data", tint = Pixel.InkSoft),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.MINT.label, onBack = onBack, color = Module.MINT.look.header, trailing = { SectionsKey(Module.MINT.label, sections) { key -> model.mintStartType = null; model.mintSection = MintSection.valueOf(key) } })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.MINT, model.data.player.mintLead, model.store.moduleEvent(app.pokeplan.app.GameStore.MINT_LOG_PREFIX), openPokemon = openPokemon)
            FirstRecordEgg(model, Tutorials.MINT_TALLY, data.accounts.size + data.transactions.size)
            OverviewCard(model)
            PendingReview(model)
            OutbreakPanel(model)
            MintPartyPanel(model, openPokemon)
        }
    }
}

private fun reviewDetail(model: AppModel): String {
    val n = model.mint.reviewInbox().size
    return if (n == 0) "nothing to look at" else if (n == 1) "1 to look at" else "$n to look at"
}

private fun billsDetail(model: AppModel): String {
    val today = model.store.today
    val open = MintRules.upcoming(model.mintData, today, 7).size + dueBills(model, 7).size
    return if (model.mintData.bills.isEmpty() && model.mintData.plans.isEmpty()) "optional" else if (open == 0) "none due soon" else "$open to settle"
}

@Composable
internal fun OverviewCard(model: AppModel) {
    val data = model.mintData
    val mint = model.mint
    val today = model.store.today
    if (data.accounts.none { it.status == AccountStatus.ACTIVE }) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Add your first account")
                PixelButton("Add account", { model.mintSection = MintSection.ACCOUNTS }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
            }
        }
        return
    }
    val position = MintRules.netPosition(data, today)
    val currency = data.settings.currency
    val days = app.pokeplan.mint.OverviewCounts.days(data, today)
    val available = MintRules.available(data, currency, today, days)
    val upcoming = MintRules.upcoming(data, today, days).filter { !it.bill.income }.distinctBy { it.bill.id }.take(3)
    val leadsWithAvailable = available.liquidMinor != 0L || available.billsCount > 0
    var breakdown by remember { mutableStateOf(false) }
    if (breakdown) Dialog({ breakdown = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(true) { breakdown = false }
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) { OverviewBreakdownBox(model, currency) { breakdown = false } }
    }
    val hidden = data.settings.hideValues
    TextBox {
        Column(Modifier.clickable(onClickLabel = "Tap for the breakdown", role = Role.Button) { breakdown = true }, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PixelTitle("Overview") }
                app.pokeplan.ui.theme.PixelIconButton(if (hidden) "eye-off" else "eye", if (hidden) "Show values" else "Hide values", { mint.setHideValues(!hidden) }, Pixel.InkSoft)
            }
            if (leadsWithAvailable) {
                Text(mint.show(available.availableMinor, currency), style = MaterialTheme.typography.titleLarge)
                position.forEach { (cur, minor) -> MintValueRow("Net position", mint.show(minor, cur), soft = true) }
                if (available.installmentsCount > 0) MintValueRow("Credit bills due", "${mint.show(available.installmentsMinor, currency)} (${available.installmentsCount})", soft = true)
            } else position.forEach { (cur, minor) -> Text(mint.show(minor, cur), style = MaterialTheme.typography.titleLarge) }
            if (data.settings.overviewUpcoming) {
                upcoming.forEach { o ->
                    MintValueRow(o.bill.name, if (o.dueDay == today) "Today" else if (o.state == OccurrenceState.OVERDUE) "Overdue" else o.dueDay.short(), soft = true)
                }
                // A card's own statement cycle is an obligation too, so it shows beside the bills (MINT.md 5.6D).
                mint.cardObligationsWithin(days).take(3).forEach { (account, cycle) ->
                    MintValueRow(
                        account.name + " statement",
                        if (cycle.dueDay == today) "Today" else if (cycle.state(today) == app.pokeplan.mint.CardCycles.State.OVERDUE) "Overdue" else cycle.dueDay.short(),
                        soft = true,
                    )
                }
            }
            // Accounts and budgets, a few of each; the full lists are in their own sections.
            val accounts = remember(data.accounts, data.transactions) {
                MintRules.balances(data).filter { it.account.status == AccountStatus.ACTIVE && !it.account.hideFromTotal }.take(4)
            }
            if (data.settings.overviewAccounts && accounts.isNotEmpty()) {
                SectionLabel("Accounts")
                accounts.forEach { b -> MintValueRow(b.account.name, mint.show(b.postedMinor, b.account.currency), soft = true) }
            }
            val budgets = data.budgets.filter { it.status == PlanStatus.ACTIVE }.take(3)
            if (data.settings.overviewBudgets && budgets.isNotEmpty()) {
                SectionLabel("Budgets")
                budgets.forEach { b ->
                    val p = MintRules.progress(data, b, today, model.data.settings.weekStart)
                    val label = data.categories.firstOrNull { it.id == b.categoryId }?.name ?: "Everything"
                    MintValueRow(label, if (p.remainingMinor >= 0) "${mint.show(p.remainingMinor, b.currency)} left" else "${mint.show(-p.remainingMinor, b.currency)} over", soft = true)
                    PixelBar((p.percent ?: 0) / 100f, color = ModuleAccent.MINT.accent, label = "$label: ${p.percent ?: 0} percent used")
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Quick actions")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Expense", { model.mintStartType = TxType.EXPENSE; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                PixelButton("Income", { model.mintStartType = TxType.INCOME; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Transfer", { model.mintStartType = TxType.TRANSFER; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            val repeatable = remember(data.transactions) { data.transactions.filter { it.status == TxStatus.POSTED && it.type in setOf(TxType.EXPENSE, TxType.INCOME) }.maxByOrNull { it.atMillis } }
            repeatable?.let { last ->
                PixelButton("Again: " + last.payee.ifBlank { last.type.label } + " " + mint.show(last.amountMinor, last.currency), {
                    model.mintStartType = last.type
                    model.mintStartAmount = Money.plain(last.amountMinor, last.currency)
                    model.mintStartPayee = last.payee
                    model.mintStartCategory = last.categoryId
                    model.mintStartAccount = last.accountId
                    model.mintSection = MintSection.ACTIVITY
                }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var currency by remember { mutableStateOf(data.settings.currency) }
    var opening by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var annualApr by remember { mutableStateOf("") }
    var interestGraceDays by remember { mutableStateOf("0") }
    val earn = remember { EarnDraft() }
    var more by remember { mutableStateOf(false) }
    var suffix by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(data.accounts.isEmpty()) }
    var typesInfo by remember { mutableStateOf(false) }
    if (typesInfo) AccountTypesInfo { typesInfo = false }
    fun save() {
        val minor = if (opening.isBlank()) 0L else Money.parse(opening, currency)
        if (minor == null) { message = "Check the opening balance." to NoticeTone.WARNING; return }
        val limitMinor = if (Installments.allowedFor(type) && limit.isNotBlank()) Money.parse(limit, currency) else 0L
        if (limitMinor == null) { message = "Check the credit limit." to NoticeTone.WARNING; return }
        val aprBps = if (Installments.allowedFor(type) && annualApr.isNotBlank()) percentToBasisPoints(annualApr) else 0
        if (aprBps == null) { message = "Check the annual rate." to NoticeTone.WARNING; return }
        val grace = if (Installments.allowedFor(type)) interestGraceDays.toIntOrNull() ?: 0 else 0
        if (grace !in 0..MintRules.MAX_GRACE_DAYS) { message = "Enter a grace period from 0 to ${MintRules.MAX_GRACE_DAYS} days." to NoticeTone.WARNING; return }
        val earnBps = earn.basisPoints(type)
        if (earnBps == null) { message = "Check the interest rate and tax." to NoticeTone.WARNING; return }
        val first = data.accounts.none { it.status == AccountStatus.ACTIVE }
        val out = mint.addAccount(
            name, type, currency, minor, suffix = suffix, group = group, card = card, limitMinor = limitMinor,
            statementDay = if (Installments.allowedFor(type)) statementDay.toIntOrNull() ?: 0 else 0,
            dueDay = if (Installments.allowedFor(type)) dueDay.toIntOrNull() ?: 0 else 0,
            annualAprBasisPoints = aprBps, interestGraceDays = grace,
            earnRateBasisPoints = earnBps.first, earnSchedule = if (canEarn(type)) earn.schedule else EarnSchedule.OFF, earnTaxBasisPoints = earnBps.second, earnAutoPost = earn.autoPost,
        )
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else {
            message = null; name = ""; opening = ""; limit = ""; statementDay = ""; dueDay = ""; annualApr = ""; interestGraceDays = "0"; earn.rate = ""; earn.tax = ""; earn.schedule = EarnSchedule.OFF; earn.autoPost = false; suffix = ""; group = ""; card = ""; more = false; adding = false; model.haptic()
            if (first) model.mintSection = null
        }
    }
    AccountList(model, open) { open = it }
    if (!adding) {
        PixelButton("Add account", { adding = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
        return
    }
    FormBox {
        FormStack {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PixelTitle("Where is this money?") }
                PixelButton("?", { typesInfo = true }, color = Pixel.InkSoft, compact = true)
            }
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", placeholder = "GCash", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            FormDivider()
            FormGroup {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelDropdown(AccountType.entries.toList(), type, { it?.label ?: "" }, { t -> if (t != null) type = t }, Modifier.weight(2f), key = accent, label = "Type")
                    PixelDropdown(Money.common.take(6), currency, { it ?: "" }, { c -> if (c != null) currency = c }, Modifier.weight(1f), key = accent, label = "Currency")
                }
                PixelField(
                    opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                    label = if (Installments.allowedFor(type)) "Starting balance owed" else if (type.liability) "Amount owed now" else "Balance now", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (Installments.allowedFor(type)) PixelField(
                    limit, { limit = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                    label = "Credit limit", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (Installments.allowedFor(type)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(statementDay, { statementDay = dayInput(it) }, Modifier.weight(1f), label = "Statement day", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    PixelField(dueDay, { dueDay = dayInput(it) }, Modifier.weight(1f), label = "Due day", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (Installments.allowedFor(type)) {
                    PixelField(
                        annualApr, { annualApr = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, Modifier.fillMaxWidth(),
                        label = "Annual rate for estimated interest (%)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    PixelField(
                        interestGraceDays, { interestGraceDays = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(),
                        label = "Grace period (days)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("Simple APR/365 estimate; not a lender quote.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                EarnFields(type, earn)
            }
            FormDisclosure("Additional details", more, { more = !more }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    CardColorPicker(card) { card = it }
                    PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits", singleLine = true)
                }
            }
            FormSave("Save", ::save, ModuleAccent.MINT.accent, enabled = name.isNotBlank())
            if (data.accounts.isNotEmpty()) PixelButton("Cancel", { adding = false; message = null }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
}

@Composable
internal fun cardColor(style: CardStyle): Color = when (style) {
    CardStyle.PLAIN -> Pixel.InkSoft
    CardStyle.BLUE -> Pixel.Blue
    CardStyle.GREEN -> Pixel.Green
    CardStyle.GOLD -> Pixel.Warning
    CardStyle.VIOLET -> if (Pixel.dark) Color(0xFFA98CF0) else Color(0xFF6E45C2)
    CardStyle.SLATE -> Pixel.Ink
}


private val accountTypeNotes = listOf(
    AccountType.BANK to "Money in a bank. The balance is what you have.",
    AccountType.EWALLET to "An app wallet such as GCash or Maya.",
    AccountType.DIGITAL_BANK to "A bank that lives in an app.",
    AccountType.CASH to "Notes and coins you carry or keep at home.",
    AccountType.CREDIT_CARD to "Money you owe. The balance is what you owe.",
    AccountType.PAY_LATER to "Money you owe a buy-now-pay-later service such as SPayLater (BNPL). It is a credit line, not a wallet; you repay it in installments.",
    AccountType.LOAN to "Money you owe and pay back over time.",
    AccountType.OTHER_ASSET to "Anything else you own that has a value.",
    AccountType.OTHER_LIABILITY to "Anything else you owe.",
)

@Composable
private fun AccountTypesInfo(onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            TextBox {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PixelTitle("Account types")
                    accountTypeNotes.forEach { (t, note) ->
                        Column {
                            Text(t.label, style = MaterialTheme.typography.titleSmall)
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                    PixelButton("Close", onClose, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountOptions(model: AppModel, a: FinancialAccount, startEditing: Boolean = false, onDone: () -> Unit) {
    val mint = model.mint
    val hasPlans = model.mintData.plans.any { it.accountId == a.id }
    val accountTypes = AccountType.entries.filter { it.liability == a.type.liability && (!hasPlans || Installments.allowedFor(it)) }
    var asking by remember(a.id) { mutableStateOf(false) }
    var editing by remember(a.id) { mutableStateOf(startEditing) }
    var name by remember(a.id) { mutableStateOf(a.name) }
    var type by remember(a.id) { mutableStateOf(a.type) }
    var currency by remember(a.id) { mutableStateOf(a.currency) }
    var opening by remember(a.id) { mutableStateOf(Money.plain(a.openingMinor, a.currency)) }
    var statementDay by remember(a.id) { mutableStateOf(if (a.statementDay > 0) a.statementDay.toString() else "") }
    var dueDay by remember(a.id) { mutableStateOf(if (a.dueDay > 0) a.dueDay.toString() else "") }
    var annualApr by remember(a.id) { mutableStateOf(percentText(a.annualAprBasisPoints)) }
    var interestGraceDays by remember(a.id) { mutableStateOf(a.interestGraceDays.toString()) }
    val earn = remember(a.id) { EarnDraft(percentText(a.earnRateBasisPoints), a.earnSchedule, percentText(a.earnTaxBasisPoints), a.earnAutoPost) }
    var limit by remember(a.id) { mutableStateOf(if (a.limitMinor > 0) Money.plain(a.limitMinor, a.currency) else "") }
    var institution by remember(a.id) { mutableStateOf(a.institution) }
    var group by remember(a.id) { mutableStateOf(a.group) }
    var suffix by remember(a.id) { mutableStateOf(a.suffix) }
    var card by remember(a.id) { mutableStateOf(a.card) }
    var hideTotal by remember(a.id) { mutableStateOf(a.hideFromTotal) }
    var appPackage by remember(a.id) { mutableStateOf(a.appPackage) }
    var refused by remember(a.id) { mutableStateOf<String?>(null) }
    var removingQr by remember(a.id) { mutableStateOf(false) }
    var moreEdit by remember(a.id) { mutableStateOf(a.institution.isNotEmpty() || a.group.isNotEmpty() || a.suffix.isNotEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (!editing && !asking && a.receiveQr.isNotEmpty()) ReceiveMoney(model, a) { refused = it }
        if (editing) {
            val locked = mint.hasHistory(a.id)
            val minor = Money.parse(opening.ifBlank { "0" }, currency)
            FormStack {
                PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                FormGroup {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelDropdown(accountTypes, type, { it?.label ?: "" }, { t -> if (t != null) type = t }, Modifier.weight(2f), key = accent, label = "Type")
                        if (!locked) PixelDropdown(
                            Money.common.take(6), currency, { it ?: "" },
                            { c -> if (c != null && c != currency) { opening = Money.parse(opening.ifBlank { "0" }, currency)?.let { m -> Money.plain(m, c) } ?: opening; currency = c } },
                            Modifier.weight(1f), key = accent, label = "Currency",
                        )
                    }
                    PixelField(
                        opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                        label = if (Installments.allowedFor(type)) "Starting balance owed" else if (type.liability) "Amount owed when you started tracking" else "Balance when you started tracking", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (Installments.allowedFor(type)) PixelField(
                        limit, { limit = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                        label = "Credit limit", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (Installments.allowedFor(type)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelField(statementDay, { statementDay = dayInput(it) }, Modifier.weight(1f), label = "Statement day", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        PixelField(dueDay, { dueDay = dayInput(it) }, Modifier.weight(1f), label = "Due day", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (Installments.allowedFor(type)) {
                        PixelField(
                            annualApr, { annualApr = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, Modifier.fillMaxWidth(),
                            label = "Annual rate for estimated interest (%)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        PixelField(
                            interestGraceDays, { interestGraceDays = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(),
                            label = "Grace period (days)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("Simple APR/365 estimate; not a lender quote.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    EarnFields(type, earn)
                }
                ToggleRow("Hide from total", hideTotal) { hideTotal = it }
                FormDisclosure("Additional details", moreEdit, { moreEdit = !moreEdit }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        CardColorPicker(card) { card = it }
                        if (model.images != null) ReceiveMoneyEdit(model, a, removingQr, { removingQr = it }) { refused = it }
                        model.appLauncher?.let { AppPick(it, appPackage) { appPackage = it } }
                        PixelField(institution, { institution = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Bank or provider", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                        PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits", singleLine = true)
                    }
                }
                FormSave("Save", {
                    val limitMinor = if (Installments.allowedFor(type) && limit.isNotBlank()) Money.parse(limit, currency) else 0L
                    val aprBps = if (Installments.allowedFor(type) && annualApr.isNotBlank()) percentToBasisPoints(annualApr) else 0
                    val grace = if (Installments.allowedFor(type)) interestGraceDays.toIntOrNull() ?: 0 else 0
                    val earnBps = earn.basisPoints(type)
                    if (earnBps == null) refused = "Check the interest rate and tax."
                    else if (minor == null) refused = "Check the balance."
                    else if (limitMinor == null) refused = "Check the credit limit."
                    else if (aprBps == null) refused = "Check the annual rate."
                    else if (grace !in 0..MintRules.MAX_GRACE_DAYS) refused = "Enter a grace period from 0 to ${MintRules.MAX_GRACE_DAYS} days."
                    else mint.updateAccount(a.copy(
                        limitMinor = limitMinor, statementDay = if (Installments.allowedFor(type)) statementDay.toIntOrNull() ?: 0 else 0,
                        dueDay = if (Installments.allowedFor(type)) dueDay.toIntOrNull() ?: 0 else 0,
                        annualAprBasisPoints = aprBps, interestGraceDays = grace,
                        earnRateBasisPoints = earnBps?.first ?: 0, earnSchedule = if (canEarn(type)) earn.schedule else EarnSchedule.OFF, earnTaxBasisPoints = earnBps?.second ?: 0, earnAutoPost = earn.autoPost,
                        name = name, type = type, currency = currency, openingMinor = minor,
                        institution = institution, group = group, suffix = suffix, card = card,
                        hideFromTotal = hideTotal, appPackage = appPackage,
                    ))
                        .let { if (it.record != null) { editing = false; refused = null; onDone() } else refused = it.refused }
                }, ModuleAccent.MINT.accent, enabled = name.isNotBlank())
                PixelButton("Cancel", { editing = false; refused = null }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            }
        } else if (asking) {
            Confirm("Delete only works without records or installment plans.", "Delete", onConfirm = { refused = mint.deleteAccount(a.id); asking = false; if (refused == null) onDone() }, onKeep = { asking = false })
        } else Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (Installments.allowedFor(a.type)) {
                CreditLine(model, a) { refused = it }
                CreditStatements(model, a)
            }
            val launcher = model.appLauncher
            val appLabel = if (a.appPackage.isNotEmpty()) launcher?.label(a.appPackage) else null
            if (launcher != null && appLabel != null) PixelButton(
                "Open ${if (appLabel.length > 14) appLabel.take(13) + "…" else appLabel}",
                { if (!launcher.open(a.appPackage)) refused = "That app could not be opened." }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true,
            )
            QuickBalance(model, a)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                when (a.status) {
                    AccountStatus.ACTIVE -> PixelButton("Archive", { mint.setAccountStatus(a.id, AccountStatus.ARCHIVED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    AccountStatus.ARCHIVED -> PixelButton("Restore", { mint.setAccountStatus(a.id, AccountStatus.ACTIVE); onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    AccountStatus.CLOSED -> PixelButton("Reopen", { refused = mint.reopenAccount(a.id); if (refused == null) onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                if (a.status == AccountStatus.ACTIVE) PixelButton("Close", { refused = mint.closeAccount(a.id, model.store.today); if (refused == null) onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (a.status == AccountStatus.CLOSED) Text("Closed on " + (a.closedDay?.toString() ?: "a day not kept"), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            mint.accountDeletionReason(a.id)?.let { reason -> Text(reason, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

/**
 * Updating a balance from the account itself (MINT.md 4.4, 5.8): type what the bank app shows, compare it with Mint,
 * and record the reconciliation — with the reason an adjustment needs — without leaving the account.
 */
@Composable
private fun QuickBalance(model: AppModel, a: FinancialAccount) {
    val mint = model.mint
    var open by remember(a.id) { mutableStateOf(false) }
    var text by remember(a.id) { mutableStateOf("") }
    var reason by remember(a.id) { mutableStateOf("") }
    var check by remember(a.id) { mutableStateOf<app.pokeplan.app.mint.MintStore.BalanceCheck?>(null) }
    var note by remember(a.id) { mutableStateOf<String?>(null) }
    val fresh = mint.accountFreshness(a.id)
    val line = buildList {
        fresh?.lastActivity?.let { add("moved " + agoText(it, model.store.today)) }
        add("checked " + (fresh?.lastChecked?.let { agoText(it, model.store.today) } ?: "never"))
    }.joinToString(" · ")
    if (!open) {
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelButton("Update the balance", { open = true; check = null; note = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    } else {
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelField(
            text, { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); check = null; note = null }, Modifier.fillMaxWidth(),
            label = "Balance in the bank app", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = a.currency,
        )
        val shown = check
        if (shown != null) {
            if (shown.matches) {
                Text("It matches: " + mint.show(shown.bookMinor, a.currency) + ".", style = MaterialTheme.typography.bodyLarge)
                PixelButton("Record the check", {
                    mint.reconcile(a.id, shown.reportedMinor)
                    note = null; open = false; model.say("Saved."); model.haptic()
                }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            } else {
                Text(
                    "Mint has " + mint.show(shown.bookMinor, a.currency) + "; the bank says " + mint.show(shown.reportedMinor, a.currency) + " — " +
                        mint.show(kotlin.math.abs(shown.differenceMinor), a.currency) + " " + (if (shown.differenceMinor > 0) "higher" else "lower") + ".",
                    style = MaterialTheme.typography.bodyLarge,
                )
                shown.causes.forEach { cause -> Text("\u2022 " + cause, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                PixelField(
                    reason, { reason = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(),
                    label = "Why the difference (needed to adjust)", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FormSave("Record the adjustment", {
                    if (reason.isBlank()) { note = "Say what the difference is for, in a few words."; return@FormSave }
                    val out = mint.reconcile(a.id, shown.reportedMinor, acceptAdjustment = true, reason = reason)
                    if (out.record == null) note = out.refused ?: "That could not be saved."
                    else { note = null; open = false; reason = ""; text = ""; model.say("Adjustment recorded."); model.haptic() }
                }, ModuleAccent.MINT.accent, enabled = reason.isNotBlank())
                if (reason.isBlank()) Text("Say what the difference is for.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Compare", {
                val reported = Money.parse(text, a.currency)
                if (reported == null) note = "Check the amount."
                else { check = mint.balanceCheck(a.id, reported); note = null }
            }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
            PixelButton("Cancel", { open = false; check = null; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        note?.let { message -> Report(model, message, NoticeTone.WARNING) }
    }
}

@Composable
private fun CreditStatements(model: AppModel, account: FinancialAccount) {
    val mint = model.mint
    val today = model.store.today
    val records = model.mintData.creditStatements.filter { it.accountId == account.id }.sortedByDescending { it.statementDay }
    var adding by remember(account.id) { mutableStateOf(false) }
    var statementDay by remember(account.id) { mutableStateOf(today) }
    var statementBalance by remember(account.id) { mutableStateOf("") }
    var minimumPayment by remember(account.id) { mutableStateOf("") }
    var dueDay by remember(account.id) { mutableStateOf(today.plus(DatePeriod(days = 14))) }
    var message by remember(account.id) { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Statements")
            Text("Reference only; balance unchanged.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text("Interest estimated when you record a statement.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            records.forEach { statement ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(
                        "${statement.statementDay.short()} · ${mint.show(statement.statementBalanceMinor, account.currency)} statement · ${mint.show(statement.minimumPaymentMinor, account.currency)} minimum",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Due ${statement.dueDay.short()}" +
                            (if (statement.estimatedInterestTxId == null) "" else " · estimated interest recorded"),
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                }
            }
            if (account.status == AccountStatus.ACTIVE) {
                PixelButton(if (adding) "Cancel statement" else "Record statement", { adding = !adding; message = null }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                if (adding) FormStack {
                    val cardCycle = mint.currentCardCycle(account.id)
                    if (cardCycle != null && cardCycle.billedMinor > 0 && cardCycle.statementId == null) PixelButton("Use the card's cycle", {
                        statementDay = cardCycle.statementDay
                        statementBalance = Money.plain(cardCycle.billedMinor, account.currency)
                        dueDay = cardCycle.dueDay
                    }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    FieldLabel("Statement date")
                    PixelDateField(statementDay, today, ModuleAccent.MINT.accent, { statementDay = it ?: statementDay }, min = account.openingDay)
                    PixelField(
                        statementBalance, { statementBalance = money(it) }, Modifier.fillMaxWidth(), label = "Statement balance", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = account.currency,
                    )
                    PixelField(
                        minimumPayment, { minimumPayment = money(it) }, Modifier.fillMaxWidth(), label = "Minimum payment", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = account.currency,
                    )
                    FieldLabel("Payment due date")
                    PixelDateField(dueDay, today, ModuleAccent.MINT.accent, { dueDay = it ?: dueDay }, min = statementDay)
                    message?.let { Report(model, it, NoticeTone.WARNING) }
                    FormSave("Save statement", {
                        val balance = Money.parse(statementBalance, account.currency)
                        val minimum = Money.parse(minimumPayment, account.currency)
                        if (balance == null || minimum == null) message = "Check both statement amounts."
                        else {
                            val result = mint.recordCreditStatement(account.id, statementDay, balance, minimum, dueDay)
                            val saved = result.record
                            message = result.refused
                            if (saved != null) {
                                adding = false
                                statementBalance = ""
                                minimumPayment = ""
                                model.say(if (saved.estimatedInterestTxId == null) "Statement saved." else "Statement saved with an estimated interest record.")
                            }
                        }
                    }, ModuleAccent.MINT.accent, enabled = Money.parse(statementBalance, account.currency) != null && Money.parse(minimumPayment, account.currency) != null)
                }
            }
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun ReceiveMoney(model: AppModel, a: FinancialAccount, problem: (String?) -> Unit) {
    val hide = model.mintData.settings.hideValues
    val bitmap = remember(a.receiveQr) { if (a.receiveQr.isEmpty()) null else runCatching { Base64.decode(a.receiveQr).decodeToImageBitmap() }.getOrNull() }
    // The QR opens in the shared viewer: light field, hard edges, full brightness while it is up, and Share.
    val openFull = {
        model.photoViewer.open(
            listOf(MediaItem("receive-${a.id}", MediaSources.base64(a.receiveQr), title = a.name, description = "Receive money QR code")),
            config = PhotoViewerConfig(
                allowShare = true, showImageCounter = false, backdrop = ViewerBackdrop.LIGHT, crisp = true, brightScreen = true,
                module = ModuleAccent.MINT, shareLabel = "QR code",
            ),
        )
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelTitle("Receive money", Modifier.fillMaxWidth())
            val desc = if (hide) "Receive money QR code for ${a.name}, hidden" else "Receive money QR code for ${a.name}"
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (hide) Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(PixelShape).background(Pixel.Border).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
                    Text("Hidden", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
                }
                else if (bitmap != null) Image(
                    bitmap, desc,
                    Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                        .clickable(role = Role.Button, onClickLabel = "Open receive money QR code full screen") { openFull() },
                    contentScale = ContentScale.Fit, filterQuality = qrFilter(bitmap),
                )
                else Text("Could not show it", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            if (!hide && bitmap != null) PixelButton("Open", openFull, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun ReceiveMoneyEdit(model: AppModel, a: FinancialAccount, removing: Boolean, setRemoving: (Boolean) -> Unit, problem: (String?) -> Unit) {
    val mint = model.mint
    val bitmap = remember(a.receiveQr) { if (a.receiveQr.isEmpty()) null else runCatching { Base64.decode(a.receiveQr).decodeToImageBitmap() }.getOrNull() }
    fun choose() {
        model.images?.choose { png, why ->
            if (png == null) { problem(why); return@choose }
            val out = mint.setReceiveQr(a.id, Base64.encode(png))
            problem(if (out.record == null) (out.refused ?: "That picture could not be saved.") else null)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("Receive money")
        if (a.receiveQr.isEmpty()) PixelButton("Add QR code", { choose() }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
        else {
            if (bitmap != null) Image(bitmap, "Receive money QR code for ${a.name}", Modifier.fillMaxWidth(0.4f).aspectRatio(bitmap.width.toFloat() / bitmap.height), contentScale = ContentScale.Fit, filterQuality = qrFilter(bitmap))
            if (removing) Confirm("Remove this QR code?", "Remove", onConfirm = { mint.setReceiveQr(a.id, ""); setRemoving(false) }, onKeep = { setRemoving(false) })
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Replace", { choose() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Remove", { setRemoving(true) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

private fun qrFilter(b: androidx.compose.ui.graphics.ImageBitmap) =
    if (b.width.toFloat() / b.height in 0.9f..1.1f) FilterQuality.None else FilterQuality.Medium

private const val PAGE = 50

private val mainTypes = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
private val quickTypes = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER, TxType.REFUND, TxType.FEE, TxType.ADJUSTMENT)
private data class SplitDraft(val categoryId: String? = null, val amount: String = "")

@Composable
private fun SplitFields(parts: List<SplitDraft>, currency: String, categories: List<Pair<String, String>>, onChange: (List<SplitDraft>) -> Unit) {
    parts.forEachIndexed { index, part ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(
                part.amount, { value -> onChange(parts.toMutableList().also { it[index] = part.copy(amount = money(value)) }) },
                Modifier.weight(1f), label = "Part ${index + 1}", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = currency,
            )
            PixelDropdown(
                listOf<String?>(null) + categories.map { it.first }, part.categoryId,
                { id -> categories.firstOrNull { it.first == id }?.second ?: "None" },
                { id -> onChange(parts.toMutableList().also { it[index] = part.copy(categoryId = id) }) },
                Modifier.weight(1f), key = accent, label = "Category",
            )
        }
        if (parts.size > 2) PixelButton(
            "Remove part ${index + 1}",
            { onChange(parts.filterIndexed { partIndex, _ -> partIndex != index }) },
            color = Pixel.InkSoft, compact = true,
        )
    }
    if (parts.size < 8) PixelButton(
        "Add split", { onChange(parts + SplitDraft()) },
        Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityView(model: AppModel, find: ActivityFind = remember { ActivityFind() }, reveal: PullRevealState = rememberPullReveal(androidx.compose.foundation.rememberScrollState()), startInstallments: Boolean = false, startAmount: String = "", startPayee: String = "", startAccount: String? = null, startCategory: String? = null) {
    val mint = model.mint
    val data = model.mintData
    val open = data.accounts.filter { it.status == AccountStatus.ACTIVE }
    var type by remember { mutableStateOf(model.mintStartType ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(startAmount) }
    // Quick capture: an ordinary expense starts on the account and category last used, not on the first one (5.3).
    val newest = remember(data) { data.transactions.sortedByDescending { it.atMillis } }
    val lastExpense = remember(newest) { newest.firstOrNull { it.type == TxType.EXPENSE && it.categoryId != null } }
    var accountId by remember { mutableStateOf< String? >(startAccount ?: newest.firstOrNull { t -> open.any { it.id == t.accountId } }?.accountId) }
    var toId by remember { mutableStateOf(model.mintStartTo) }
    LaunchedEffect(Unit) { model.mintStartTo = null; model.mintStartAmount = null; model.mintStartAccount = null; model.mintStartPayee = null; model.mintStartCategory = null }
    var categoryId by remember { mutableStateOf< String? >(startCategory ?: lastExpense?.categoryId) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitParts by remember { mutableStateOf(listOf(SplitDraft(), SplitDraft())) }
    var up by remember { mutableStateOf(true) }
    var payAt by remember { mutableStateOf<Long?>(null) }
    var more by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(data.transactions.isEmpty() || startInstallments || startAmount.isNotBlank() || startPayee.isNotBlank() || model.mintStartType != null || model.mintStartTo != null) }
    var payee by remember { mutableStateOf(startPayee) }
    var note by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var inInstallments by remember { mutableStateOf(startInstallments) }
    var iou by remember { mutableStateOf(false) }
    var refundOfId by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    val terms = remember { TermsState() }
    var openId by remember { mutableStateOf<String?>(null) }
    val from = open.firstOrNull { it.id == accountId } ?: open.firstOrNull()
    val currency = from?.currency ?: data.settings.currency
    val targets = open.filter { it.id != from?.id && it.currency == from?.currency }
    val to = targets.firstOrNull { it.id == toId } ?: targets.firstOrNull()
    var limit by remember { mutableStateOf(PAGE) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulkCategory by remember { mutableStateOf<String?>(null) }
    if (open.isEmpty()) {
        EmptyState("Add an account first", icon = "member-card", action = "Add account" to { model.mintSection = MintSection.ACCOUNTS })
        return
    }
    val plansOwed = if (type == TxType.TRANSFER && to != null && Installments.allowedFor(to.type)) mint.planViews().filter { it.plan.accountId == to.id && it.next != null } else emptyList()
    val installmentMode = type == TxType.EXPENSE && inInstallments && from != null && Installments.allowedFor(from.type)
    fun save() {
        val minor = Money.parse(amount, currency)
        if (minor == null || minor <= 0) { amountError = "Type an amount above zero."; message = null; return }
        amountError = null
        val canSplitType = type in setOf(TxType.EXPENSE, TxType.INCOME, TxType.REFUND, TxType.FEE)
        val splits = if (splitEnabled && !installmentMode && canSplitType) {
            val parsed = splitParts.map { part -> part to Money.parse(part.amount, currency) }
            if (parsed.any { it.second == null }) { message = "Check each split amount." to NoticeTone.WARNING; return }
            if (parsed.sumOf { it.second!! } != minor) { message = "Split amounts must add up to the transaction." to NoticeTone.WARNING; return }
            parsed.map { (part, amount) -> TransactionSplit(part.categoryId, amount!!) }
        } else emptyList()
        val feeMinor = if (type == TxType.TRANSFER && fee.isNotBlank()) Money.parse(fee, currency) else 0L
        if (feeMinor == null) { message = "Check the fee." to NoticeTone.WARNING; return }
        val at = payAt ?: model.store.nowMillis
        val day = at.toLocalDateTime(zone).date
        if (installmentMode) {
            val refused = createPlanPurchase(mint, from!!, payee, minor, categoryId, day, terms)
            if (refused != null) message = refused to NoticeTone.WARNING
            else { message = null; model.say("Saved."); payAt = null; amount = ""; payee = ""; note = ""; more = false; inInstallments = false; adding = false; model.haptic() }
            return
        }
        if (plansOwed.isNotEmpty() && from != null && to != null) {
            val out = mint.payPlans(to.id, minor, from.id, day, feeMinor = feeMinor)
            if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
            else { message = null; model.say("Saved."); if (out.rewards.isNotEmpty()) model.effect(Sfx.ITEM_GET); payAt = null; amount = ""; fee = ""; adding = false; model.haptic() }
            return
        }
        val isIou = type == TxType.INCOME && iou
        if (isIou && payee.isBlank()) { message = "Say who paid it back." to NoticeTone.WARNING; return }
        val out = mint.record(
            type, minor, from!!.id, toAccountId = if (type == TxType.TRANSFER) to?.id else null,
            categoryId = categoryId.takeIf { type != TxType.TRANSFER && type != TxType.ADJUSTMENT && splits.isEmpty() },
            payee = payee, note = note, atMillis = at, up = up, refundOf = refundOfId.takeIf { type == TxType.REFUND && splits.isEmpty() },
            splits = splits, feeMinor = feeMinor,
            status = TxStatus.POSTED, iou = isIou,
        )
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else { message = null; model.say("Saved."); if (out.rewards.isNotEmpty()) model.effect(Sfx.ITEM_GET); payAt = null; amount = ""; fee = ""; payee = ""; note = ""; more = false; splitEnabled = false; iou = false; refundOfId = null; amountError = null; splitParts = listOf(SplitDraft(), SplitDraft()); adding = false; model.haptic() }
    }
    val installmentReady = !installmentMode || (terms.count != null && terms.fee(currency) != null)
    val canSave = amount.isNotBlank() && installmentReady && !(type == TxType.INCOME && iou && payee.isBlank())
    val saveHint = when {
        amount.isBlank() -> "Type an amount to save."
        !installmentReady -> if (terms.count == null) "Choose 1 to ${Installments.MAX_COUNT} installments." else "Check the fee."
        type == TxType.INCOME && iou && payee.isBlank() -> "Say who paid it back."
        else -> null
    }
    val otherTypes = quickTypes.filter { it !in mainTypes }
    PullRevealSlot(reveal, model.data.settings.reduceMotion) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelField(find.query, { find.query = it.take(40); limit = PAGE }, Modifier.weight(1f), placeholder = "Search", singleLine = true)
                    PixelButton(find.filter?.label ?: "Filter", { find.showFilter = !find.showFilter }, color = Pixel.InkSoft, compact = true)
                }
                if (find.showFilter) PixelDropdown(listOf<TxType?>(null) + quickTypes, find.filter, { it?.label ?: "All" }, { find.filter = it }, Modifier.fillMaxWidth(), key = accent)
            }
        }
    }
    val duplicates = remember(data) { mint.duplicates() }
    if (duplicates.isNotEmpty()) TextBox {
        Accordion("Possible duplicates (${duplicates.size})") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    "Same account, day, kind and amount. Open one of them to check before deleting anything.",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
                duplicates.take(6).forEach { g ->
                    val accountName = data.accounts.firstOrNull { it.id == g.accountId }?.name.orEmpty()
                    Text(
                        g.day.short() + " \u00b7 " + accountName + " \u00b7 " + g.type.label + " " + mint.show(g.amountMinor, g.currency) + " \u00b7 " + g.ids.size + " records",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    if (!adding) PixelButton("Add", { adding = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
    else FormBox {
        FormStack {
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); amountError = null }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = currency, placeholder = "0.00",
            )
            amountError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning) }
            FormDivider()
            FormGroup {
                PixelChoice(mainTypes, type.takeIf { it in mainTypes }, { it?.label ?: "" }, { t ->
                    if (t != null) {
                        if (t != type) { splitEnabled = false; iou = false }
                        type = t
                        categoryId = null
                    }
                }, label = "Transaction type", key = accent)
                PixelDropdown(
                    open.map { it.id }, from?.id, { id -> open.firstOrNull { it.id == id }?.name ?: "" },
                    { id -> if (id != null) { accountId = id; if (toId == id) toId = null } }, Modifier.fillMaxWidth(), key = accent, label = if (type == TxType.TRANSFER) "From" else "Account",
                )
                if (type == TxType.TRANSFER) {
                    if (targets.isEmpty()) Text("Add a second account in the same currency.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: result
                    if (targets.isNotEmpty()) PixelDropdown(
                        targets.map { it.id }, to?.id, { id -> targets.firstOrNull { it.id == id }?.name ?: "" },
                        { id -> if (id != null) toId = id }, Modifier.fillMaxWidth(), key = accent, label = "To",
                    )
                    if (targets.isNotEmpty()) PixelField(
                        fee, { fee = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Fee (optional)", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = currency, placeholder = "0.00",
                    )
                    if (plansOwed.isEmpty() && to?.type?.liability == true) Text("Paying a card is not spending. Owed now and the account you pay from both fall.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (plansOwed.isNotEmpty()) Text("Repayment fills earliest due plans first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                } else if (type == TxType.ADJUSTMENT) {
                    PixelChoice(listOf(true, false), up, { if (it == true) "Balance goes up" else "Balance goes down" }, { if (it != null) up = it }, label = "Change", key = accent)
                } else {
                    if (type == TxType.EXPENSE && from?.type?.liability == true) {
                        Text("Spending on this card: it counts once today and raises what is owed.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    if (type == TxType.REFUND) {
                        val refundable = data.transactions
                            .filter { it.type == TxType.EXPENSE && it.accountId == from?.id && it.status == TxStatus.POSTED && it.amountMinor > 0 }
                            .sortedByDescending { it.atMillis }.take(12)
                        if (refundable.isNotEmpty()) PixelDropdown(
                            listOf<String?>(null) + refundable.map { it.id }, refundOfId,
                            { id -> refundable.firstOrNull { it.id == id }?.let { r -> r.day.short() + " \u00b7 " + r.payee.ifBlank { "Expense" } } ?: "No record" },
                            { refundOfId = it }, Modifier.fillMaxWidth(), key = accent, label = "Refund of (optional)",
                        )
                    }
                    val kind = if (type == TxType.INCOME && !iou) CategoryKind.INCOME else CategoryKind.EXPENSE
                    val usable = data.categories.filter { it.kind == kind && !it.archived }
                    val recent = data.transactions.sortedByDescending { it.atMillis }.mapNotNull { t -> usable.firstOrNull { it.id == t.categoryId } }.distinct()
                    val ordered = (recent + usable).distinct()
                    if (type == TxType.INCOME) ToggleRow("Paid back by someone (IOU)", iou) {
                        iou = it; categoryId = null; if (it) splitEnabled = false
                    }
                    if (iou) {
                        PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = "Who paid it back", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    }
                    val canSplit = !installmentMode && !iou && type in setOf(TxType.EXPENSE, TxType.INCOME, TxType.REFUND, TxType.FEE)
                    if (canSplit) ToggleRow("Split across categories", splitEnabled) {
                        splitEnabled = it
                        if (it) categoryId = null
                    }
                    if (!splitEnabled || !canSplit) PixelDropdown(
                        listOf<String?>(null) + ordered.map { it.id }, categoryId,
                        { id -> ordered.firstOrNull { it.id == id }?.name ?: "None" }, { id -> categoryId = id },
                        Modifier.fillMaxWidth(), key = accent, label = "Category",
                    ) else SplitFields(splitParts, currency, ordered.map { it.id to it.name }) { splitParts = it }
                }
            }
            if (type == TxType.EXPENSE && from != null && Installments.allowedFor(from.type)) ToggleRow("Pay in installments", inInstallments) {
                inInstallments = it
                if (it) { splitEnabled = false; categoryId = null }
            }
            if (installmentMode && from != null) FormGroup(title = "Installment schedule") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(
                        payee, { payee = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name (optional)", singleLine = true,
                        placeholder = Installments.DEFAULT_PURCHASE_NAME, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    InstallmentTerms(model, from, terms, Money.parse(amount, currency), (payAt ?: model.store.nowMillis).toLocalDateTime(zone).date)
                }
            }
            PixelMomentField(payAt ?: model.store.nowMillis, { payAt = it }, model.store.nowMillis, ModuleAccent.MINT.accent, label = "When")
            FormDisclosure("Additional details", more || installmentMode, { more = !more }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelDropdown(
                        listOf<TxType?>(null) + otherTypes, type.takeIf { it in otherTypes }, { it?.label ?: "Expense, income or transfer" },
                        { t ->
                            if (t != null) {
                                if (t != type) { splitEnabled = false; iou = false }
                                type = t
                                categoryId = null
                            } else if (type in otherTypes) {
                                type = TxType.EXPENSE
                                categoryId = null
                            }
                        },
                        Modifier.fillMaxWidth(), key = accent, label = "Other kind of entry",
                    )
                    if (!installmentMode && !iou) PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = "Payee or source", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    if (!installmentMode && !iou) {
                        val recentPayees = remember(newest) { newest.mapNotNull { t -> t.payee.takeIf { p -> p.isNotBlank() } }.distinct().take(5) }
                        if (recentPayees.isNotEmpty()) {
                            Text("Used before", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                recentPayees.forEach { p -> Chip(p.take(MintRules.NAME_MAX), payee == p, { payee = p.take(MintRules.NAME_MAX * 2) }) }
                            }
                        }
                    }
                    PixelField(note, { note = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
            FormSave("Save", ::save, ModuleAccent.MINT.accent, enabled = canSave)
            if (!canSave) saveHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            message?.let { (text, tone) -> Report(model, text, tone) }
            if (data.transactions.isNotEmpty()) PixelButton("Cancel", { adding = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
    val found = remember(data, find.query, find.filter) { MintRules.search(data, find.query, find.filter) }
    // While an entry is being added, the list below is the chosen account's records, so the account the form is on is
    // the only one that can be found there (MINT.md 5.3). With the form closed every account's records are listed.
    val addingOn = if (adding) from else null
    val shown = if (addingOn != null) found.filter { it.accountId == addingOn.id || it.toAccountId == addingOn.id } else found
    if (data.transactions.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton(if (selecting) "Done selecting" else "Categorise many", {
                    selecting = !selecting
                    selected = emptySet()
                    bulkCategory = null
                }, Modifier.weight(1f), if (selecting) ModuleAccent.MINT.accent else Pixel.InkSoft, compact = true)
            }
            if (selecting) {
                val categorisable = data.categories.filter { !it.archived }
                PixelDropdown(
                    listOf<String?>(null) + categorisable.map { it.id }, bulkCategory,
                    { id -> categorisable.firstOrNull { it.id == id }?.name ?: "Choose a category" }, { bulkCategory = it },
                    Modifier.fillMaxWidth(), key = accent, label = "Category for the selected",
                )
                PixelButton("Categorise ${selected.size}", {
                    val changed = mint.categorise(selected, bulkCategory)
                    model.say(if (changed == 0) "Nothing to change." else "Categorised $changed records.")
                    selected = emptySet()
                }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = selected.isNotEmpty())
                Text("Transfers and adjustments are never categorised.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            if (addingOn != null && adding) Text("Showing only " + addingOn.name + ".", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (shown.isEmpty()) Text(if (addingOn != null) "Nothing in " + addingOn.name + " yet." else "Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Column {
                shown.take(limit).forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    if (selecting) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton(if (t.id in selected) "Selected" else "Select", {
                            selected = if (t.id in selected) selected - t.id else selected + t.id
                        }, color = if (t.id in selected) ModuleAccent.MINT.accent else Pixel.InkSoft, compact = true)
                        Box(Modifier.weight(1f)) { TransactionRow(model, t, false) {} }
                    } else {
                        TransactionRow(model, t, openId == t.id) { openId = if (openId == t.id) null else t.id }
                        if (openId == t.id) TransactionOptions(model, t) { openId = null }
                    }
                }
            }
            if (shown.size > limit) PixelButton("Show ${minOf(PAGE, shown.size - limit)} more", { limit += PAGE }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}

private fun Transaction.signed(): String = when (type) {
    TxType.EXPENSE, TxType.FEE -> "-"
    TxType.INCOME, TxType.REFUND -> "+"
    TxType.ADJUSTMENT -> if (up) "+" else "-"
    TxType.TRANSFER -> ""
}

@Composable
private fun TransactionRow(model: AppModel, t: Transaction, open: Boolean, onClick: () -> Unit) {
    val data = model.mintData
    val category = if (t.splits.isNotEmpty()) "Split (${t.splits.size})" else data.categories.firstOrNull { it.id == t.categoryId }?.name
    val account = data.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()
    val to = data.accounts.firstOrNull { it.id == t.toAccountId }?.name
    val planNames = buildList {
        t.planId?.let { id -> data.plans.firstOrNull { it.id == id }?.name?.let(::add) }
        t.planAllocations.forEach { allocation -> data.plans.firstOrNull { it.id == allocation.planId }?.name?.let(::add) }
    }.distinct()
    val plan = planNames.joinToString(", ").takeIf { it.isNotEmpty() }
    val repayment = t.type == TxType.TRANSFER && data.accounts.firstOrNull { it.id == t.toAccountId }?.type?.liability == true
    val title = t.payee.ifBlank { category ?: (if (t.type == TxType.FEE) t.feeKind?.label else null) ?: (if (repayment) "Repayment" else t.type.label) }
    val line = listOfNotNull(t.day.short(), if (to != null) "$account to $to" else account, category.takeIf { t.payee.isNotBlank() }, t.feeMinor.takeIf { it > 0 }?.let { "fee " + model.mint.show(it, t.currency) }, (if (t.iou && t.status == TxStatus.PENDING) "IOU, still owed" else if (t.iou && t.status == TxStatus.POSTED) "IOU" else t.status.name.lowercase().takeIf { t.status != TxStatus.POSTED }), plan?.let { "Plan: $it" }, (if (t.unreviewed) "imported, unreviewed" else "imported").takeIf { t.importBatch != null }).joinToString(" · ")
    MintLine(
        trail = { Text(t.signed() + model.mint.show(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall) },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 3)
    }
}

@Composable
private fun TransactionOptions(model: AppModel, t: Transaction, onDone: () -> Unit) {
    val mint = model.mint
    val planPurchase = mint.planOfPurchase(t.id) != null
    var editing by remember(t.id) { mutableStateOf(false) }
    var asking by remember(t.id) { mutableStateOf(false) }
    var amount by remember(t.id) { mutableStateOf(Money.plain(t.amountMinor, t.currency)) }
    var payee by remember(t.id) { mutableStateOf(t.payee) }
    var note by remember(t.id) { mutableStateOf(t.note) }
    var refused by remember(t.id) { mutableStateOf<String?>(null) }
    var at by remember(t.id) { mutableStateOf(t.atMillis) }
    var categoryId by remember(t.id) { mutableStateOf(t.categoryId) }
    var splitEnabled by remember(t.id) { mutableStateOf(t.splits.isNotEmpty()) }
    var splitParts by remember(t.id) {
        mutableStateOf(t.splits.map { SplitDraft(it.categoryId, Money.plain(it.amountMinor, t.currency)) }.ifEmpty { listOf(SplitDraft(), SplitDraft()) })
    }
    var accountId by remember(t.id) { mutableStateOf(t.accountId) }
    var fee by remember(t.id) { mutableStateOf(if (t.feeMinor > 0) Money.plain(t.feeMinor, t.currency) else "") }
    var moreTx by remember(t.id) { mutableStateOf(t.payee.isNotEmpty() || t.note.isNotEmpty()) }
    var amountError by remember(t.id) { mutableStateOf<String?>(null) }
    var feeError by remember(t.id) { mutableStateOf<String?>(null) }
    var refunding by remember(t.id) { mutableStateOf(false) }
    var refundAmount by remember(t.id) { mutableStateOf("") }
    val history = mint.historyOf(t.id)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (editing) {
            val accounts = model.mintData.accounts.filter { (it.status == AccountStatus.ACTIVE && it.currency == t.currency) || it.id == t.accountId }
            FormStack {
                if (planPurchase) Text("Change the purchase from its plan.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                else {
                    PixelField(
                        amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); amountError = null }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = t.currency, placeholder = "0.00",
                    )
                    amountError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning) }
                }
                FormDivider()
                FormGroup {
                    if (t.type == TxType.TRANSFER) {
                        PixelField(
                            fee, { fee = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); feeError = null }, Modifier.fillMaxWidth(), label = "Fee (optional)", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = t.currency, placeholder = "0.00",
                        )
                        feeError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning) }
                    }
                    if (!planPurchase && accounts.size > 1 && t.type != TxType.TRANSFER) {
                        PixelDropdown(accounts.map { it.id }, accountId, { id -> accounts.firstOrNull { it.id == id }?.name ?: "" }, { id -> if (id != null) accountId = id }, Modifier.fillMaxWidth(), key = accent, label = "Account")
                    }
                    if (t.type != TxType.TRANSFER && t.type != TxType.ADJUSTMENT) {
                        val kind = if (t.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                        val pickable = model.mintData.categories.filter { it.kind == kind && (!it.archived || it.id == t.categoryId || t.splits.any { part -> part.categoryId == it.id }) }
                        val canSplit = t.type in setOf(TxType.EXPENSE, TxType.INCOME, TxType.REFUND, TxType.FEE)
                        if (canSplit) ToggleRow("Split across categories", splitEnabled) {
                            splitEnabled = it
                            if (it) categoryId = null
                        }
                        if (!splitEnabled || !canSplit) {
                            PixelDropdown(listOf<String?>(null) + pickable.map { it.id }, categoryId, { id -> pickable.firstOrNull { it.id == id }?.name ?: "None" }, { categoryId = it }, Modifier.fillMaxWidth(), key = accent, label = "Category")
                        } else SplitFields(splitParts, t.currency, pickable.map { it.id to it.name }) { splitParts = it }
                    }
                }
                PixelMomentField(at, { at = it }, model.store.nowMillis, ModuleAccent.MINT.accent, label = "When")
                FormDisclosure("Additional details", moreTx, { moreTx = !moreTx }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = if (planPurchase) "Purchase name" else "Payee or source", singleLine = true)
                        PixelField(note, { note = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note")
                    }
                }
                FormSave("Save", {
                    val minor = if (planPurchase) t.amountMinor else Money.parse(amount, t.currency)
                    val feeMinor = if (t.type == TxType.TRANSFER && fee.isNotBlank()) Money.parse(fee, t.currency) else 0L
                    amountError = if (minor == null) "Type an amount above zero." else null
                    feeError = if (feeMinor == null) "Check the fee." else null
                    refused = when {
                        amountError != null -> amountError
                        feeError != null -> feeError
                        else -> null
                    }
                    if (amountError == null && feeError == null) {
                        val checked = minor ?: return@FormSave
                        val checkedFee = feeMinor ?: return@FormSave
                        val splits = if (splitEnabled) {
                            val parsed = splitParts.map { part -> part to Money.parse(part.amount, t.currency) }
                            if (parsed.any { it.second == null }) { refused = "Check each split amount."; return@FormSave }
                            if (parsed.sumOf { it.second!! } != checked) { refused = "Split amounts must add up to the transaction."; return@FormSave }
                            parsed.map { (part, amount) -> TransactionSplit(part.categoryId, amount!!) }
                        } else emptyList()
                        mint.updateTransaction(t.copy(
                            amountMinor = checked, feeMinor = checkedFee, payee = payee, note = note, atMillis = at, accountId = accountId,
                            categoryId = categoryId.takeIf { splits.isEmpty() }, splits = splits,
                        )).let { if (it.record != null) { editing = false; onDone() } else refused = it.refused }
                    }
                }, ModuleAccent.MINT.accent, enabled = planPurchase || amount.isNotBlank())
                if (!planPurchase && amount.isBlank()) Text("Type an amount to save.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Cancel", { editing = false }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            }
        } else if (asking) {
            Confirm("Delete this record? Your balance changes with it.", "Delete", onConfirm = {
                val before = model.mintData
                if (mint.deleteTransaction(t.id)) {
                    onDone()
                    model.offerUndo("Deleted: record") { mint.restore(before) }
                } else refused = "A purchase with a plan cannot be deleted."
                asking = false
            }, onKeep = { asking = false })
        } else {
            ReceiptAttachment(model, t) { image ->
                val result = mint.updateTransaction(t.copy(receiptImage = image))
                if (result.record != null) onDone() else refused = result.refused ?: "That receipt could not be saved."
            }
            if (planPurchase) {
                Text("Manage this purchase in its installment plan.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Edit", { editing = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    val excluded = t.status == TxStatus.EXCLUDED
                    PixelButton(if (excluded) "Include" else "Exclude", {
                        if (mint.setTransactionStatus(t.id, if (excluded) TxStatus.POSTED else TxStatus.EXCLUDED)) onDone()
                        else refused = "That record can no longer change status."
                    }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    when (t.status) {
                        TxStatus.PENDING -> PixelButton(if (t.iou) "Mark received" else "Mark posted", {
                            if (mint.setTransactionStatus(t.id, TxStatus.POSTED)) onDone() else refused = "That record can no longer change status."
                        }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                        TxStatus.POSTED -> PixelButton("Mark pending", {
                            if (mint.setTransactionStatus(t.id, TxStatus.PENDING)) onDone() else refused = "That record can no longer change status."
                        }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        else -> {}
                    }
                    if (t.status == TxStatus.VOIDED) PixelButton("Restore", {
                        if (mint.setTransactionStatus(t.id, TxStatus.POSTED)) onDone() else refused = "That record can no longer change status."
                    }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    else PixelButton("Void", {
                        if (mint.setTransactionStatus(t.id, TxStatus.VOIDED)) onDone() else refused = "That record can no longer change status."
                    }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                if (t.type == TxType.EXPENSE && t.status == TxStatus.POSTED) {
                    if (refunding) {
                        PixelField(
                            refundAmount, { refundAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                            label = "Refund amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = t.currency,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Save refund", {
                                val minor = Money.parse(refundAmount, t.currency)
                                if (minor == null || minor <= 0) refused = "Check the amount."
                                else {
                                    val out = mint.record(
                                        TxType.REFUND, minor, t.accountId, categoryId = t.categoryId, payee = t.payee,
                                        refundOf = t.id,
                                    )
                                    if (out.record == null) refused = out.refused ?: "That refund could not be saved."
                                    else { refunding = false; onDone() }
                                }
                            }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                            PixelButton("Cancel", { refunding = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    } else {
                        val refunded = model.mintData.transactions.filter { it.type == TxType.REFUND && it.refundOf == t.id }.sumOf { it.amountMinor }
                        if (refunded < t.amountMinor) PixelButton(
                            "Refund" + if (refunded > 0) " the rest" else " this",
                            { refunding = true; refundAmount = Money.plain(t.amountMinor - refunded, t.currency) },
                            Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true,
                        )
                    }
                }
            }
            if (history.isNotEmpty()) {
                HorizontalDivider(color = Pixel.Border)
                Accordion("History (${history.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        history.take(10).forEach { change ->
                            Text(
                                change.kind.replaceFirstChar { it.uppercase() } + " \u00b7 " + change.detail,
                                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                            )
                        }
                    }
                }
            }
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun ReceiptAttachment(model: AppModel, transaction: Transaction, onChange: (String) -> Unit) {
    val hide = model.mintData.settings.hideValues
    val image = transaction.receiptImage
    val bitmap = remember(image) { if (image.isEmpty()) null else runCatching { Base64.decode(image).decodeToImageBitmap() }.getOrNull() }
    val viewer = model.imageViewer
    val openFull = {
        model.photoViewer.open(
            listOf(MediaItem("receipt-${transaction.id}", MediaSources.base64(image), title = "Receipt")),
            config = PhotoViewerConfig(allowShare = true, showImageCounter = false, module = ModuleAccent.MINT, shareLabel = "receipt"),
        )
    }
    var removing by remember(transaction.id) { mutableStateOf(false) }
    if (removing) Confirm(
        "Remove the receipt image from this record?", "Remove",
        onConfirm = { onChange(""); removing = false },
        onKeep = { removing = false },
    )
    if (image.isNotEmpty() || (model.images != null && !hide)) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Receipt")
            when {
                hide && image.isNotEmpty() -> Text("Hidden", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                bitmap != null -> Image(
                    bitmap, "Receipt image",
                    Modifier.fillMaxWidth(0.45f).aspectRatio(bitmap.width.toFloat() / bitmap.height)
                        .clickable(role = Role.Button, onClickLabel = "Open receipt image") { openFull() },
                    contentScale = ContentScale.Fit, filterQuality = qrFilter(bitmap),
                )
                image.isNotEmpty() -> Text("Could not show this receipt image.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            if (!hide && model.images != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton(if (image.isEmpty()) "Attach image" else "Replace", {
                        model.images?.choose { png, problem ->
                            if (png == null) model.say(problem ?: "That image could not be opened.")
                            else onChange(Base64.encode(png))
                        }
                    }, Modifier.weight(1f), if (image.isEmpty()) ModuleAccent.MINT.accent else Pixel.InkSoft, compact = true)
                    if (image.isNotEmpty()) PixelButton("Remove", { removing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    if (bitmap != null) PixelButton("Open", openFull, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

@Composable
private fun SavingsAccountChoices(model: AppModel, currency: String, selected: List<String>, onSelected: (List<String>) -> Unit) {
    val mint = model.mint
    val accounts = model.mintData.accounts.filter {
        it.id in selected || (it.currency == currency && !it.type.liability && it.status == AccountStatus.ACTIVE)
    }
    if (accounts.isEmpty()) Text("Add an open $currency asset account.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    accounts.forEach { account ->
        val balance = MintRules.balance(model.mintData, account).postedMinor.coerceAtLeast(0)
        ToggleRow("${account.name} · ${mint.show(balance, account.currency)}", account.id in selected) { checked ->
            onSelected(if (checked) (selected + account.id).distinct() else selected.filterNot { it == account.id })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetsView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    var categoryId by remember { mutableStateOf<String?>(null) }
    var period by remember { mutableStateOf(BudgetPeriod.MONTHLY) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var newAmount by remember { mutableStateOf("") }
    var newPeriod by remember { mutableStateOf(BudgetPeriod.MONTHLY) }
    var editMessage by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingTarget by remember { mutableStateOf(false) }
    var targetName by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var targetAccounts by remember { mutableStateOf(emptyList<String>()) }
    var targetHasDay by remember { mutableStateOf(false) }
    var targetDay by remember { mutableStateOf(today) }
    var targetMessage by remember { mutableStateOf<String?>(null) }
    var openTargetId by remember { mutableStateOf<String?>(null) }
    var editTargetName by remember { mutableStateOf("") }
    var editTargetAmount by remember { mutableStateOf("") }
    var editTargetAccounts by remember { mutableStateOf(emptyList<String>()) }
    var editTargetHasDay by remember { mutableStateOf(false) }
    var editTargetDay by remember { mutableStateOf(today) }
    var editTargetMessage by remember { mutableStateOf<String?>(null) }
    val currency = data.settings.currency
    val budgets = data.budgets.filter { it.status != PlanStatus.ARCHIVED }
    if (budgets.isNotEmpty()) TextBox {
        Column {
            budgets.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val p = MintRules.progress(data, b, today, model.data.settings.weekStart)
                val label = (data.categories.firstOrNull { it.id == b.categoryId }?.name ?: "Everything") + if (b.period != BudgetPeriod.MONTHLY) " · ${b.period.label.lowercase()}" else ""
                Column(Modifier.padding(vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MintLine(
                        trail = {
                            Text(
                                if (b.status == PlanStatus.PAUSED) "paused" else if (p.remainingMinor >= 0) "${mint.show(p.remainingMinor, b.currency)} left" else "${mint.show(-p.remainingMinor, b.currency)} over",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                        onClick = { openId = if (openId == b.id) null else b.id; newAmount = Money.plain(b.plannedMinor, b.currency); newPeriod = b.period; editMessage = null }, onClickLabel = "Show options",
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                    PixelBar((p.percent ?: 0) / 100f, color = ModuleAccent.MINT.accent, label = "$label: ${p.percent ?: 0} percent used")
                    if (p.carriedMinor != 0L) Text(
                        if (p.carriedMinor > 0) "Carried from the period before: ${mint.show(p.carriedMinor, b.currency)}" else "Overspent the period before by ${mint.show(-p.carriedMinor, b.currency)}",
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                    MintRules.pace(p, today)?.let { pace ->
                        Text(
                            "At this pace the period reaches ${mint.show(pace, b.currency)}" + if (pace > b.plannedMinor) ", over the plan." else ".",
                            style = MaterialTheme.typography.bodyMedium, color = if (pace > b.plannedMinor) Pixel.Warning else Pixel.InkSoft,
                        )
                    }
                    if (openId == b.id) {
                        FormStack {
                        PixelField(
                            newAmount, { newAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = b.currency, placeholder = "0.00",
                        )
                        FormDivider()
                        PixelChoice(BudgetPeriod.entries.toList(), newPeriod, { it?.label ?: "" }, { if (it != null) newPeriod = it }, label = "Period", key = accent)
                        val current = data.budgets.firstOrNull { it.id == b.id } ?: b
                        ToggleRow("Carry what is left into the next period", current.rollover) { mint.setBudgetRollover(b.id, it) }
                        if (current.rollover) Text("A period's leftover, or overspend, moves into the next one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        editMessage?.let { Report(model, it, NoticeTone.WARNING) }
                        FormSave("Save", {
                            val minor = Money.parse(newAmount, b.currency)
                            val out = minor?.let { mint.updateBudget(b.id, it, newPeriod) }
                            editMessage = if (minor == null) "Check the amount." else out?.refused
                            if (out?.record != null) openId = null
                        }, ModuleAccent.MINT.accent, enabled = Money.parse(newAmount, b.currency) != null)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton(if (b.status == PlanStatus.PAUSED) "Resume" else "Pause", { mint.setBudgetStatus(b.id, if (b.status == PlanStatus.PAUSED) PlanStatus.ACTIVE else PlanStatus.PAUSED); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { val before = model.mintData; mint.deleteBudget(b.id); openId = null; model.offerUndo("Deleted: budget") { mint.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                        }
                        val targets = data.savingsTargets.filter { it.status != PlanStatus.ARCHIVED }
                        if (targets.isNotEmpty()) {
                        TextBox {
                            Column {
                                PixelTitle("Savings targets")
                                targets.forEachIndexed { index, target ->
                                    if (index > 0) HorizontalDivider(color = Pixel.Border)
                                    val progress = MintRules.savingsProgress(data, target)
                                    Column(Modifier.padding(vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                        MintLine(
                                            trail = { Text(if (target.status == PlanStatus.PAUSED) "paused" else "${progress.percent}%") },
                                            onClick = {
                                                openTargetId = if (openTargetId == target.id) null else target.id
                                                editTargetName = target.name
                                                editTargetAmount = Money.plain(target.targetMinor, target.currency)
                                                editTargetAccounts = target.accountIds
                                                editTargetHasDay = target.targetDay != null
                                                editTargetDay = target.targetDay ?: today
                                                editTargetMessage = null
                                            },
                                            onClickLabel = "Show savings target options",
                                        ) { Text(target.name, style = MaterialTheme.typography.bodyLarge) }
                                        val contributed = mint.savingsContributed(target.id)
                                        Text(
                                            "${mint.show(progress.savedMinor, target.currency)} of ${mint.show(target.targetMinor, target.currency)}" +
                                                (target.targetDay?.let { " · by ${it.short()}" } ?: "") +
                                                (if (contributed > 0) " · moved in " + mint.show(contributed, target.currency) + " in 30 days" else ""),
                                            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                                        )
                                        PixelBar(progress.percent / 100f, color = ModuleAccent.MINT.accent, label = "${target.name}: ${progress.percent} percent saved")
                                        if (openTargetId == target.id) {
                                            FormStack {
                                                PixelField(editTargetName, { editTargetName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Target name", singleLine = true)
                                                PixelField(
                                                    editTargetAmount, { editTargetAmount = money(it) }, Modifier.fillMaxWidth(), label = "Target amount", singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = target.currency,
                                                )
                                                Text("Progress uses these balances.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                                                Text("One active target per account.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                                                SavingsAccountChoices(model, target.currency, editTargetAccounts) { editTargetAccounts = it }
                                                ToggleRow("Set a target date", editTargetHasDay) { editTargetHasDay = it }
                                                if (editTargetHasDay) PixelDateField(editTargetDay, today, ModuleAccent.MINT.accent, { editTargetDay = it ?: editTargetDay }, min = today)
                                                FormSave("Save target", {
                                                    val amountMinor = Money.parse(editTargetAmount, target.currency)
                                                    val result = if (amountMinor == null) null else mint.updateSavingsTarget(
                                                        target.copy(name = editTargetName, targetMinor = amountMinor, accountIds = editTargetAccounts, targetDay = editTargetDay.takeIf { editTargetHasDay }),
                                                    )
                                                    editTargetMessage = if (amountMinor == null) "Check the amount." else result?.refused
                                                    if (result?.record != null) openTargetId = null
                                                }, ModuleAccent.MINT.accent, enabled = editTargetName.isNotBlank() && Money.parse(editTargetAmount, target.currency) != null)
                                                editTargetMessage?.let { Report(model, it, NoticeTone.WARNING) }
                                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                                    PixelButton(
                                                        if (target.status == PlanStatus.PAUSED) "Resume" else "Pause",
                                                        {
                                                            editTargetMessage = mint.setSavingsTargetStatus(
                                                                target.id, if (target.status == PlanStatus.PAUSED) PlanStatus.ACTIVE else PlanStatus.PAUSED,
                                                            )
                                                            if (editTargetMessage == null) openTargetId = null
                                                        },
                                                        Modifier.weight(1f), Pixel.InkSoft, compact = true,
                                                    )
                                                    PixelButton("Archive", {
                                                        editTargetMessage = mint.setSavingsTargetStatus(target.id, PlanStatus.ARCHIVED)
                                                        if (editTargetMessage == null) openTargetId = null
                                                    }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                                                }
                                                PixelButton("Delete target", {
                                                    val before = model.mintData
                                                    mint.deleteSavingsTarget(target.id)
                                                    openTargetId = null
                                                    model.offerUndo("Deleted: savings target") { mint.restore(before) }
                                                }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
    if (!adding) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("New budget", { adding = true; addingTarget = false }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
            PixelButton("New savings target", { addingTarget = true; adding = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
    if (adding) FormBox {
        FormStack {
            PixelTitle("New budget")
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = currency, placeholder = "0.00",
            )
            FormDivider()
            FormGroup {
                val budgetCategories = data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
                PixelDropdown(listOf<String?>(null) + budgetCategories.map { it.id }, categoryId, { id -> budgetCategories.firstOrNull { it.id == id }?.name ?: "Everything" }, { categoryId = it; amount = "" }, Modifier.fillMaxWidth(), key = accent, label = "Category")
                PixelChoice(BudgetPeriod.entries.toList(), period, { it?.label ?: "" }, { if (it != null) period = it }, label = "Period", key = accent)
                val usual = remember(categoryId, data.transactions) { mint.suggestedBudget(categoryId, currency) }
                if (usual > 0) PixelButton("Use " + mint.show(usual, currency) + ", the usual month", { amount = Money.plain(usual, currency) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            FormSave("Save", {
                val minor = Money.parse(amount, currency)
                val out = minor?.let { mint.addBudget(it, currency, period, categoryId) }
                message = if (minor == null) "Check the amount." else out?.refused
                if (out?.record != null) { amount = ""; adding = false; model.haptic() }
            }, ModuleAccent.MINT.accent, enabled = amount.isNotBlank())
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
    if (addingTarget) FormBox {
        FormStack {
            PixelTitle("New savings target")
            PixelField(targetName, { targetName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Target name", singleLine = true)
            PixelField(
                targetAmount, { targetAmount = money(it) }, Modifier.fillMaxWidth(), label = "Target amount", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = currency, placeholder = "0.00",
            )
            FormDivider()
            Text("Progress uses chosen balances.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text("One active target per account.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            SavingsAccountChoices(model, currency, targetAccounts) { targetAccounts = it }
            ToggleRow("Set a target date", targetHasDay) { targetHasDay = it }
            if (targetHasDay) PixelDateField(targetDay, today, ModuleAccent.MINT.accent, { targetDay = it ?: targetDay }, min = today)
            FormSave("Save target", {
                val minor = Money.parse(targetAmount, currency)
                val result = if (minor == null) null else mint.addSavingsTarget(targetName, minor, currency, targetAccounts, targetDay.takeIf { targetHasDay })
                targetMessage = if (minor == null) "Check the amount." else result?.refused
                if (result?.record != null) {
                    targetName = ""
                    targetAmount = ""
                    targetAccounts = emptyList()
                    targetHasDay = false
                    addingTarget = false
                    model.haptic()
                }
            }, ModuleAccent.MINT.accent, enabled = targetName.isNotBlank() && Money.parse(targetAmount, currency) != null)
            targetMessage?.let { Report(model, it, NoticeTone.WARNING) }
            PixelButton("Cancel", { addingTarget = false }, Modifier.fillMaxWidth(), Pixel.InkSoft)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BillsView(model: AppModel, openInstallments: Boolean = false, startAdding: Boolean = false, startIncome: Boolean = false, startFrequency: Frequency = Frequency.MONTHLY, startManage: Boolean = false) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(startFrequency) }
    var day1 by remember { mutableStateOf("15") }
    var day2 by remember { mutableStateOf("30") }
    var workday by remember { mutableStateOf(false) }
    var estimate by remember { mutableStateOf(false) }
    var firstDue by remember { mutableStateOf<LocalDate?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editFirst by remember { mutableStateOf<LocalDate?>(null) }
    var editFrequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var editDay1 by remember { mutableStateOf("15") }
    var editDay2 by remember { mutableStateOf("30") }
    var editWorkday by remember { mutableStateOf(false) }
    var editEstimate by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var openKey by remember { mutableStateOf<String?>(null) }
    var partial by remember { mutableStateOf("") }
    var paidOn by remember { mutableStateOf<LocalDate?>(null) }
    var manage by remember { mutableStateOf(startManage) }
    var showSettled by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(startAdding) }
    var incomeNew by remember { mutableStateOf(startIncome) }
    var moreBill by remember { mutableStateOf(false) }
    var moreEditBill by remember { mutableStateOf(false) }
    var incomeCategory by remember { mutableStateOf<String?>(null) }
    var recordKey by remember { mutableStateOf<String?>(null) }
    var recordAccount by remember { mutableStateOf<String?>(null) }
    fun settle(o: Occurrence, status: SettleStatus, paid: Long, label: String) {
        val before = model.mintData
        if (mint.settle(o.bill.id, o.dueDay, status, paid, paidDay = paidOn ?: defaultReceivedDay(o, today))) { paidOn = null; openKey = null; model.offerUndo("$label: ${o.bill.name}") { mint.restore(before) } }
    }
    val currency = data.settings.currency
    val upcoming = MintRules.upcoming(data, today, 45).distinctBy { it.bill.id }
    if (upcoming.isNotEmpty()) TextBox {
        Column {
            upcoming.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val key = o.bill.id + o.dueDay
                OccurrenceRow(model, o, openKey == key) { openKey = if (openKey == key) null else key; partial = (o.payment?.paidMinor ?: o.bill.expectedMinor.takeIf { o.bill.estimate })?.let { Money.plain(it, o.bill.currency) }.orEmpty(); paidOn = null }
                if (openKey == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    val full = o.remainingMinor ?: o.bill.expectedMinor
                    val inc = o.bill.income
                    val est = o.bill.estimate
                    val anyAmount = full == null || est
                    if (est) PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = (if (inc) "Amount received" else "Amount paid") + (o.bill.expectedMinor?.let { " (estimate ${mint.show(it, o.bill.currency)})" } ?: ""), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = o.bill.currency, placeholder = "0.00")
                    else if (full == null) PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Amount received" else "Amount paid", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = o.bill.currency, placeholder = "0.00")
                    else PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Received so far" else "Paid so far", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = o.bill.currency, placeholder = "0.00")
                    FieldLabel(if (inc) "Received on" else "Settled on")
                    PixelDateField(paidOn ?: defaultReceivedDay(o, today), today, ModuleAccent.MINT.accent, { paidOn = it }, presets = listOf("Today" to 0, "Yesterday" to -1), max = today)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton(if (inc) "Received" else "Paid", {
                            val paid = if (anyAmount) Money.parse(partial, o.bill.currency) else o.bill.expectedMinor
                            if (paid != null) { settle(o, SettleStatus.PAID, paid, if (inc) "Received" else "Paid"); model.effect(Sfx.CANDY) }
                        }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = !anyAmount || Money.parse(partial, o.bill.currency) != null, compact = true)
                        if (full != null && !est) PixelButton(if (inc) "Part received" else "Part", { Money.parse(partial, o.bill.currency)?.let { settle(o, SettleStatus.PARTIAL, it, if (inc) "Part received" else "Part paid") } }, Modifier.weight(1f), Pixel.InkSoft, enabled = Money.parse(partial, o.bill.currency) != null, compact = true)
                        PixelButton("Skip", { settle(o, SettleStatus.SKIPPED, 0, "Skipped") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        if (o.payment != null) PixelButton("Undo", { mint.unsettle(o.bill.id, o.dueDay); openKey = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                    Text("Paid/Received settles reminder only.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text("Record it to update balance.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    val accounts = data.accounts.filter { it.status == AccountStatus.ACTIVE && it.currency == o.bill.currency }
                    val amountToRecord = if (anyAmount) Money.parse(partial, o.bill.currency) else o.bill.expectedMinor
                    if (accounts.isNotEmpty()) {
                        if (recordKey != key) PixelButton(if (inc) "Received, record it" else "Paid, record it", { recordKey = key; recordAccount = o.bill.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.first().id }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = amountToRecord != null, compact = true)
                        else {
                            PixelDropdown(accounts.map { it.id }, recordAccount, { id -> accounts.firstOrNull { it.id == id }?.name ?: "" }, { id -> if (id != null) recordAccount = id }, Modifier.fillMaxWidth(), key = accent, label = "Account")
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                PixelButton("Record", {
                                    val before = model.mintData
                                    val out = amountToRecord?.let { mint.settleAndRecord(o.bill.id, o.dueDay, it, recordAccount, paidOn ?: defaultReceivedDay(o, today)) }
                                    if (out?.record != null) { paidOn = null; openKey = null; recordKey = null; model.effect(Sfx.CANDY); model.offerUndo((if (inc) "Received and recorded: " else "Paid and recorded: ") + o.bill.name) { mint.restore(before) } }
                                    else model.say(out?.refused ?: "Check the amount.")
                                }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = amountToRecord != null && recordAccount != null, compact = true)
                                PixelButton("Cancel", { recordKey = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
    CardStatementsDueBox(model)
    InstallmentsDueBox(model, openInstallments)
    val settled = MintRules.settled(data, today)
    if (settled.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton(if (showSettled) "Hide settled" else "Settled lately (${settled.size})", { showSettled = !showSettled; openKey = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (showSettled) settled.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val key = "settled" + o.bill.id + o.dueDay
                val p = o.payment
                OccurrenceRow(model, o, openKey == key, paid = true) {
                    openKey = if (openKey == key) null else key
                    partial = p?.takeIf { it.status != SettleStatus.SKIPPED }?.let { Money.plain(it.paidMinor, o.bill.currency) } ?: o.bill.expectedMinor?.let { Money.plain(it, o.bill.currency) }.orEmpty()
                    paidOn = p?.settledDay
                }
                if (openKey == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    val minor = Money.parse(partial, o.bill.currency)
                    val inc = o.bill.income
                    PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Amount received" else "Amount paid", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = o.bill.currency, placeholder = "0.00")
                    FieldLabel(if (inc) "Received on" else "Settled on")
                    PixelDateField(paidOn ?: today, today, ModuleAccent.MINT.accent, { paidOn = it }, presets = listOf("Today" to 0, "Yesterday" to -1), max = today)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton(if (inc) "Received" else "Paid", { minor?.let { settle(o, SettleStatus.PAID, it, if (inc) "Received" else "Paid") } }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = minor != null, compact = true)
                        if (o.bill.expectedMinor != null) PixelButton(if (inc) "Part received" else "Part", { minor?.let { settle(o, SettleStatus.PARTIAL, it, if (inc) "Part received" else "Part paid") } }, Modifier.weight(1f), Pixel.InkSoft, enabled = minor != null, compact = true)
                        if (o.state != OccurrenceState.SKIPPED) PixelButton("Skip", { settle(o, SettleStatus.SKIPPED, 0, "Skipped") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        PixelButton("Undo", { val before = model.mintData; mint.unsettle(o.bill.id, o.dueDay); openKey = null; model.offerUndo("Unsettled: ${o.bill.name}") { mint.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
    if (!adding) {
        PixelButton("New bill or income", { adding = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
    } else FormBox {
        FormStack {
            PixelTitle(if (incomeNew) "New income" else "New bill")
            PixelChoice(listOf(false, true), incomeNew, { if (it == true) "Income expected" else "Bill to pay" }, { if (it != null) { incomeNew = it; if (!it) incomeCategory = null } }, label = "Kind", key = accent)
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (incomeNew) "Expected amount" else "Amount", placeholder = "Varies", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = currency,
            )
            FormDivider()
            FormGroup {
                PixelDropdown(Frequency.entries.toList(), frequency, { it?.label ?: "" }, { f -> if (f != null) frequency = f }, Modifier.fillMaxWidth(), key = accent, label = "Repeats")
                if (frequency == Frequency.SEMI_MONTHLY) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(day1, { day1 = dayInput(it) }, Modifier.weight(1f), label = "Day 1", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    PixelField(day2, { day2 = dayInput(it) }, Modifier.weight(1f), label = "Day 2", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                FieldLabel(if (frequency == Frequency.SEMI_MONTHLY) "First payday" else "First due")
                PixelDateField(firstDue ?: today, today, ModuleAccent.MINT.accent, { firstDue = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
            }
            FormDisclosure("Additional details", moreBill, { moreBill = !moreBill }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (incomeNew) {
                        val incomeCategories = data.categories.filter { it.kind == CategoryKind.INCOME && !it.archived }
                        PixelDropdown(listOf<String?>(null) + incomeCategories.map { it.id }, incomeCategory, { id -> incomeCategories.firstOrNull { it.id == id }?.name ?: "None" }, { incomeCategory = it }, Modifier.fillMaxWidth(), key = accent, label = "Category")
                    }
                    ToggleRow("Move to a working day", workday, detail = "Paid on the nearest weekday on or before, skipping holidays") { workday = it }
                    if (incomeNew) ToggleRow("Estimated amount", estimate, detail = "You enter the real amount when it arrives") { estimate = it }
                }
            }
            FormSave("Save", {
                val minor = if (amount.isBlank()) null else Money.parse(amount, currency)
                if (amount.isNotBlank() && minor == null) message = "Check the amount."
                else {
                    val out = mint.addBill(name, currency, frequency, firstDue ?: today, minor, categoryId = incomeCategory.takeIf { incomeNew }, income = incomeNew, days = if (frequency == Frequency.SEMI_MONTHLY) listOf(day1.toIntOrNull() ?: 15, day2.toIntOrNull() ?: 30) else emptyList(), workday = workday, estimate = estimate && incomeNew)
                    message = out.refused
                    if (out.record != null) { name = ""; amount = ""; firstDue = null; incomeCategory = null; workday = false; estimate = false; adding = false; model.haptic() }
                }
            }, ModuleAccent.MINT.accent, enabled = name.isNotBlank())
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
    if (data.bills.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton(if (manage) "Hide bills" else "All bills", { manage = !manage }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (manage) data.bills.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                MintLine(
                    trail = { Text(b.expectedMinor?.let { (if (b.estimate) "≈ " else "") + mint.show(it, b.currency) } ?: "varies", style = MaterialTheme.typography.titleSmall) },
                    onClick = { editId = if (editId == b.id) null else b.id; editName = b.name; editAmount = b.expectedMinor?.let { Money.plain(it, b.currency) }.orEmpty(); editFirst = b.firstDue; editFrequency = b.frequency; MintRules.semiDays(b).let { d -> editDay1 = d.getOrElse(0) { 15 }.toString(); editDay2 = d.getOrElse(1) { 30 }.toString() }; editWorkday = b.workday; editEstimate = b.estimate }, onClickLabel = "Edit",
                ) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${if (b.income) "income · " else ""}${b.frequency.label.lowercase()}${if (b.status != PlanStatus.ACTIVE) " · ${b.status.name.lowercase()}" else ""}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (b.frequency == Frequency.SEMI_MONTHLY || b.workday) {
                        val next = MintRules.upcoming(data, today, 60).filter { it.bill.id == b.id }.take(4)
                        if (next.isNotEmpty()) Text("Next: " + next.joinToString(" · ") { it.payDay.short() }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
                if (editId == b.id) {
                    val scheduleOpen = data.billPayments.none { it.billId == b.id }
                    val minor = if (editAmount.isBlank()) null else Money.parse(editAmount, b.currency)
                    FormStack(Modifier.padding(bottom = Spacing.S)) {
                        PixelField(editName, { editName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                        PixelField(
                            editAmount, { editAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount", placeholder = "Varies", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = b.currency,
                        )
                        FormDivider()
                        if (scheduleOpen) FormGroup {
                            PixelDropdown(Frequency.entries.toList(), editFrequency, { it?.label ?: "" }, { f -> if (f != null) editFrequency = f }, Modifier.fillMaxWidth(), key = accent, label = "Repeats")
                            if (editFrequency == Frequency.SEMI_MONTHLY) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PixelField(editDay1, { editDay1 = dayInput(it) }, Modifier.weight(1f), label = "Day 1", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                PixelField(editDay2, { editDay2 = dayInput(it) }, Modifier.weight(1f), label = "Day 2", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            FieldLabel("First due")
                            PixelDateField(editFirst ?: b.firstDue, today, ModuleAccent.MINT.accent, { editFirst = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
                        }
                        FormDisclosure("Additional details", moreEditBill, { moreEditBill = !moreEditBill }) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                ToggleRow("Move to a working day", editWorkday, detail = "Paid on the nearest weekday on or before, skipping holidays") { editWorkday = it }
                                if (b.income) ToggleRow("Estimated amount", editEstimate, detail = "You enter the real amount when it arrives") { editEstimate = it }
                            }
                        }
                        Text(
                            "Changes apply from the next occurrence on. An occurrence already settled keeps what was paid, and its payment record stays.",
                            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                        )
                        FormSave("Save", { if (mint.updateBill(b.copy(name = editName, expectedMinor = minor, firstDue = editFirst ?: b.firstDue, frequency = editFrequency, days = if (editFrequency == Frequency.SEMI_MONTHLY) listOf(editDay1.toIntOrNull() ?: 15, editDay2.toIntOrNull() ?: 30) else b.days, workday = editWorkday, estimate = editEstimate && b.income)).let { r -> message = r.refused; r.record != null }) editId = null }, ModuleAccent.MINT.accent, enabled = editName.isNotBlank() && (editAmount.isBlank() || minor != null))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton(if (b.status == PlanStatus.PAUSED) "Resume" else "Pause", { mint.setBillStatus(b.id, if (b.status == PlanStatus.PAUSED) PlanStatus.ACTIVE else PlanStatus.PAUSED) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    if (data.settings.billReminders) PixelButton(if (b.remind) "Reminders on" else "Reminders off", { mint.setBillRemind(b.id, !b.remind) }, Modifier.weight(1f), if (b.remind) ModuleAccent.MINT.accent else Pixel.InkSoft, compact = true)
                    PixelButton("Delete", { val before = model.mintData; mint.deleteBill(b.id); model.offerUndo("Deleted: ${b.name}") { mint.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

private fun defaultReceivedDay(o: Occurrence, today: LocalDate): LocalDate = if (o.bill.income && o.payDay <= today) o.payDay else today

@Composable
private fun OccurrenceRow(model: AppModel, o: Occurrence, open: Boolean, paid: Boolean = false, onClick: () -> Unit) {
    val amount = if (paid) o.payment?.paidMinor?.takeIf { o.state != OccurrenceState.SKIPPED } else o.remainingMinor ?: o.bill.expectedMinor
    val today = model.store.today
    fun shown(m: Long) = (if (o.bill.income) "+" else "") + (if (o.bill.estimate && !paid) "≈ " else "") + model.mint.show(m, o.bill.currency)
    MintLine(
        trail = {
            Text(
                if (paid) amount?.let { shown(it) }.orEmpty() else if (o.payDay == today) "Today" else if (o.state == OccurrenceState.OVERDUE) "Overdue" else o.payDay.short(),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Text(o.bill.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        val stateLabel = if (o.bill.income && o.state == OccurrenceState.PAID) "Received" else o.state.label
        val line = if (paid) "$stateLabel · ${o.payDay.short()}" else amount?.let { shown(it) } ?: "varies"
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        if (o.payDay != o.dueDay) Text("${o.payDay.short()} · for the ${ordinal(o.dueDay.dayOfMonth)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MintReviewView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    val currency = data.settings.currency
    val (from, end) = MintRules.periodOf(BudgetPeriod.MONTHLY, today)
    val spent = MintRules.spent(data, from, end, currency)
    val earned = MintRules.earned(data, from, end, currency)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("This month")
            if (data.transactions.none { it.day >= from && it.day < end && it.currency == currency }) Text("Nothing recorded in ${Money.label(currency)} yet.", style = MaterialTheme.typography.bodyLarge)
            else {
                MintValueRow("Money in", mint.show(earned, currency))
                MintValueRow("Spent", mint.show(spent.coerceAtLeast(0), currency))
                MintValueRow("Difference", mint.show(earned - spent, currency))
                val flow = Installments.cashFlow(data, from, end, currency)
                // Three figures, one row each: a long sentence in the value column read badly on a phone (27 November 2026).
                MintValueRow("Cash in", mint.show(flow.inflowMinor, currency))
                MintValueRow("Cash out", mint.show(flow.outflowMinor, currency))
                MintValueRow("Net movement", mint.show(flow.netMinor, currency))
                Text("Money that really moved through your wallets and banks; repaying a card or pay later counts as out.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                MintRules.spendingByCategory(data, from, end, currency).take(6).forEach { (id, minor) ->
                    MintValueRow(data.categories.firstOrNull { it.id == id }?.name ?: "No category", mint.show(minor, currency), soft = true)
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Insights")
            val recurring = remember(data, today) { app.pokeplan.mint.Insights.recurring(data, today) }
            val changes = remember(data, today) { app.pokeplan.mint.Insights.changes(data, BudgetPeriod.MONTHLY, today, model.data.settings.weekStart).take(4) }
            val merchants = remember(data, today) { app.pokeplan.mint.Insights.merchants(data, today) }
            val forecast = remember(data, today) { app.pokeplan.mint.Insights.forecast(data, today, 30) }
            if (data.transactions.isEmpty()) {
                Text("Record a few expenses and this fills itself.", style = MaterialTheme.typography.bodyLarge)
            } else {
                if (recurring.isNotEmpty()) {
                    SectionLabel("Looks like a subscription")
                    recurring.take(4).forEach { r ->
                        MintValueRow(r.payee, mint.show(r.averageMinor, currency) + " · about every " + r.everyDays + " days", soft = true)
                    }
                }
                if (changes.isNotEmpty()) {
                    SectionLabel("Against last month")
                    changes.forEach { c ->
                        val name = data.categories.firstOrNull { it.id == c.categoryId }?.name ?: "No category"
                        val sign = if (c.differenceMinor >= 0) "+" else "-"
                        MintValueRow(name, sign + mint.show(kotlin.math.abs(c.differenceMinor), currency) + (c.percent?.let { " (" + it + "%)" } ?: ""), soft = true)
                    }
                }
                if (merchants.isNotEmpty()) {
                    SectionLabel("Where it goes")
                    merchants.forEach { m -> MintValueRow(m.payee, mint.show(m.totalMinor, currency) + " in " + m.count + (if (m.count == 1) " record" else " records"), soft = true) }
                }
                SectionLabel("Next 30 days")
                MintValueRow("Known payments", mint.show(forecast.comingOutMinor, currency), soft = true)
                MintValueRow("Usual spending", mint.show(forecast.usualSpendingMinor, currency), soft = true)
                if (forecast.comingInMinor > 0) MintValueRow("Expected in", mint.show(forecast.comingInMinor, currency), soft = true)
                MintValueRow("Likely left", mint.show(forecast.netMinor, currency), soft = true)
                Text("A forecast from what is recorded, not a promise.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Accordion("How available money is worked out") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    app.pokeplan.mint.Insights.availableLines(data, currency, today, app.pokeplan.mint.OverviewCounts.days(data, today))
                        .forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                }
            }
        }
    }
    if (data.accounts.any { it.status == AccountStatus.ACTIVE }) FormBox { Accordion("Balance check and reconciliation") { ReconcileCard(model) } }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Review")
            val items = mint.reviewInbox()
            if (items.isEmpty()) {
                Text("Nothing needs you right now.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    if (items.size == 1) "One thing to look at. Open it, put it off for a week, or dismiss it for a month." else items.size.toString() + " things to look at. Each one is optional: open it, put it off for a week, or dismiss it for a month.",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
                items.forEach { item ->
                    HorizontalDivider(color = Pixel.Border)
                    MintLine(trail = { PixelButton("Open", { openReviewItem(model, item) }, color = ModuleAccent.MINT.accent, compact = true) }) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("Later", { mint.snoozeReview(item.id, 7) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        PixelButton("Dismiss", { mint.snoozeReview(item.id, 30) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
            val snoozed = mint.reviewSnoozed()
            if (snoozed.isNotEmpty()) Accordion("Put off (${snoozed.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    snoozed.forEach { snooze ->
                        MintLine(trail = { PixelButton("Bring back", { mint.unsnoozeReview(snooze.id) }, color = Pixel.InkSoft, compact = true) }) {
                            Text(snooze.id.removePrefix("balance:").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                            Text("Back on " + snooze.until.short(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                }
            }
            HorizontalDivider(color = Pixel.Border)
            val first = model.data.settings.weekStart
            val weekDone = data.reviews.any { MintRules.weekStartOf(it.day, first) == MintRules.weekStartOf(today, first) }
            PixelButton(if (weekDone) "Reviewed this week" else "Mark this week reviewed", { mint.finishReview(); model.effect(Sfx.ITEM_GET); model.say("Saved.") }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true, enabled = !weekDone && data.accounts.isNotEmpty())
            Text("Optional: the inbox above is the review.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

/** Opens the place where one review item is dealt with (MINT.md 9.1). */
private fun openReviewItem(model: AppModel, item: app.pokeplan.mint.ReviewInbox.Item) {
    when (item.kind) {
        app.pokeplan.mint.ReviewInbox.Kind.CONFIRM -> model.mintSection = null
        app.pokeplan.mint.ReviewInbox.Kind.IMPORTED -> model.mintSection = MintSection.IMPORT
        app.pokeplan.mint.ReviewInbox.Kind.MISSING,
        app.pokeplan.mint.ReviewInbox.Kind.FEES -> model.mintSection = MintSection.ACTIVITY
        app.pokeplan.mint.ReviewInbox.Kind.UPCOMING -> model.mintSection = MintSection.PLANS
        app.pokeplan.mint.ReviewInbox.Kind.BUDGET -> model.mintSection = MintSection.PLANS
        app.pokeplan.mint.ReviewInbox.Kind.BALANCE -> model.mintSection = MintSection.REVIEW
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReconcileCard(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val open = data.accounts.filter { it.status == AccountStatus.ACTIVE }
    if (open.isEmpty()) return
    var accountId by remember { mutableStateOf<String?>(null) }
    var statement by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<app.pokeplan.app.mint.MintStore.BalanceCheck?>(null) }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    val account = open.firstOrNull { it.id == accountId } ?: open.first()
    val book = MintRules.balance(data, account).postedMinor
    val parsed = Money.parse(statement, account.currency)
    val history = mint.reconciliationHistory(account.id).take(4)
    val cur = account.currency
    FormStack {
        PixelDropdown(open.map { it.id }, account.id, { id -> open.firstOrNull { it.id == id }?.name ?: "" }, { id ->
            if (id != null) { accountId = id; check = null; reason = ""; message = null }
        }, Modifier.fillMaxWidth(), key = accent, label = "Account")
        MintValueRow("In Mint", mint.show(book, cur))
        PixelField(
            statement, { statement = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); check = null; message = null }, Modifier.fillMaxWidth(),
            label = "Your statement says", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = cur, placeholder = "0.00",
        )
        Text(
            "A quick check only compares the two and writes nothing. Recording the reconciliation keeps the comparison; when they differ, accepting the difference records one adjustment, and it needs your reason.",
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
        FormSave("Check", {
            val reported = Money.parse(statement, cur)
            if (reported == null) { message = "Check the amount." to NoticeTone.WARNING; return@FormSave }
            check = mint.balanceCheck(account.id, reported)
            message = null
        }, ModuleAccent.MINT.accent, enabled = parsed != null)
        val shown = check?.takeIf { it.account.id == account.id && it.reportedMinor == parsed }
        if (shown != null) {
            HorizontalDivider(color = Pixel.Border)
            if (shown.matches) {
                Text("They match: Mint has " + mint.show(shown.bookMinor, cur) + ".", style = MaterialTheme.typography.bodyLarge)
                PixelButton("Record the reconciliation", {
                    mint.reconcile(account.id, shown.reportedMinor)
                    model.effect(Sfx.ITEM_GET); model.say("Saved."); check = null; statement = ""; message = null
                }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            } else {
                Text(
                    "Mint has " + mint.show(shown.bookMinor, cur) + "; the statement says " + mint.show(shown.reportedMinor, cur) + ". The statement is " +
                        mint.show(kotlin.math.abs(shown.differenceMinor), cur) + " " + (if (shown.differenceMinor > 0) "higher" else "lower") + ".",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (shown.causes.isNotEmpty()) {
                    SectionLabel("What may explain it")
                    shown.causes.forEach { cause -> Text("\u2022 " + cause, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                }
                PixelField(
                    reason, { reason = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(),
                    label = "Why the difference (needed to adjust)", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FormSave("Record with the adjustment", {
                    if (reason.isBlank()) { message = "Say what the difference is for, in a few words." to NoticeTone.WARNING; return@FormSave }
                    val out = mint.reconcile(account.id, shown.reportedMinor, acceptAdjustment = true, reason = reason)
                    if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
                    else { model.effect(Sfx.ITEM_GET); model.say("Adjustment recorded. Saved."); check = null; statement = ""; reason = ""; message = null }
                }, ModuleAccent.MINT.accent, enabled = reason.isNotBlank())
                if (reason.isBlank()) Text("Say what the difference is for to record it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Not now", { check = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
        if (history.isNotEmpty()) {
            HorizontalDivider(color = Pixel.Border)
            SectionLabel("Earlier checks")
            history.forEach { r ->
                val adjusted = r.adjustmentTxId != null
                MintValueRow(
                    r.day.short() + " \u00b7 " + (if (adjusted) "adjusted" else "matched"),
                    "statement " + mint.show(r.statementMinor, cur) + " \u00b7 Mint " + mint.show(r.bookMinor, cur),
                    soft = true,
                )
                if (r.reason.isNotBlank()) Text(r.reason, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
        message?.let { (text, tone) -> Report(model, text, tone) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MintSettingsView(model: AppModel) {
    val mint = model.mint
    val settings = model.mintData.settings
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<MintKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            ToggleRow("Ledger Research", settings.progression) { mint.setProgression(it) }
        }
    }
    if (settings.billReminders) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Bill reminders")
            run {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    val early = listOf(0 to "On the day", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 7 to "A week before")
                    PixelDropdown(early.map { it.first }, settings.billReminderDays, { d -> early.firstOrNull { it.first == d }?.second ?: "" }, { d -> if (d != null) mint.setBillReminderDays(d) }, Modifier.weight(3f), key = accent, label = "How early")
                    PixelDropdown(listOf(8, 9, 12, 18, 20), settings.billReminderHour, { h -> if (h == null) "" else "${h.toString().padStart(2, '0')}:00" }, { h -> if (h != null) mint.setBillReminderHour(h) }, Modifier.weight(2f), key = accent, label = "At")
                }
                ToggleRow("Name the bill", settings.billReminderNames) { mint.setBillReminderNames(it) }
            }
        }
    }
    Accordion("Overview") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            OverviewCountsBox(model)
            ToggleRow("Accounts on the Overview", settings.overviewAccounts) { mint.setOverviewAccounts(it) }
            ToggleRow("Budgets on the Overview", settings.overviewBudgets) { mint.setOverviewBudgets(it) }
            ToggleRow("Coming up on the Overview", settings.overviewUpcoming) { mint.setOverviewUpcoming(it) }
            val staleChoices = listOf(7, 14, 30, 60, 90)
            PixelDropdown(staleChoices, settings.reviewStaleDays, { d -> if (d == null) "" else "Ask after $d days" }, { d -> if (d != null) mint.setReviewStaleDays(d) }, Modifier.fillMaxWidth(), key = accent, label = "Check a balance")
        }
    }
    Accordion("Days off") { PaydaysAndHolidays(model) }
    TextBox { Accordion("Categories", summary = model.mintData.categories.size.toString()) { CategoriesContent(model) } }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Privacy")
            ToggleRow("Hide amounts", settings.hideValues) { mint.setHideValues(it) }
            PixelDropdown(Money.common, settings.currency, { it ?: "" }, { c -> if (c != null) mint.setCurrency(c) }, Modifier.fillMaxWidth(), key = accent, label = "Currency")
        }
    }
    CaptureSettings(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            val files = model.files
            Accordion("Export") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (files != null) {
                        PixelButton("Export everything", { note = files.save("pokelife-mint.json", mint.export()) }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            MintKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-mint-${k.name.lowercase()}.json", mint.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                        }
                    }
                    note?.let { Report(model, it) }
                }
            }
            Accordion("Erase data", startOpen = asking != null || askingAll) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MintKinds.entries.forEach { k ->
                        if (asking == k) Confirm(
                            if (k == MintKinds.ACCOUNTS) "Erase all accounts and the records that belong to them? This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                            "Erase", "Keep them", onConfirm = {
                                mint.erase(k)
                                // A waiting bank alert is MINT data too: erasing records must not leave one to come back.
                                if (k == MintKinds.ACCOUNTS || k == MintKinds.TRANSACTIONS) model.capture?.let { c -> scope.launch { c.erase() } }
                                asking = null
                            }, onKeep = { asking = null },
                        ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                    if (askingAll) Confirm("Erase every Mint record? This cannot be undone.", "Erase all", "Keep them", onConfirm = {
                        mint.eraseAll()
                        model.capture?.let { c -> scope.launch { c.erase() } }
                        askingAll = false
                    }, onKeep = { askingAll = false })
                    else PixelButton("Erase all Mint records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
                }
            }
        }
    }
}

/**
 * Turning MINT's reading of bank alerts on and off (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3). Each source is its
 * own switch, off until the player turns it on, and Android's own permission is asked for first: nothing is read while
 * it is off, and what is read never leaves the phone.
 */
@Composable
private fun CaptureSettings(model: AppModel) {
    // Desktop and web have no bank alert to read: the whole box is Android's.
    val capture = model.capture ?: return
    val mint = model.mint
    val settings = model.mintData.settings
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var erasing by remember { mutableStateOf(false) }
    // Android binds a notification listener and wakes a receiver only while their part is switched on.
    LaunchedEffect(settings.captureNotifications, settings.captureMessages) {
        capture?.watch(settings.captureNotifications, settings.captureMessages)
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Record from messages")
            Text(
                "An alert from your bank or wallet can become a record here without typing it. Reading happens on this phone only and nothing is uploaded. " +
                    "MINT reads only the banks on its list, and only the amount, the merchant, the account's last digits and the day: the rest of the message is dropped at once and never kept. " +
                    "What it reads is never posted by itself; it waits in the \"To confirm\" box, where you confirm, adjust or skip it. " +
                    "While one waits, a notification says only that a transaction is waiting: never an amount, a bank or a merchant.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            ToggleRow("Bank notifications", settings.captureNotifications) { on ->
                mint.setCaptureNotifications(on)
                if (on && !capture.notificationsGranted()) capture.openNotificationSettings()
            }
            if (settings.captureNotifications && !capture.notificationsGranted()) {
                Text(
                    "Android gives notification access to an app for every app, so PokéLife drops everything that is not from a bank on the list before it reads a field. Allow it in Android's own settings.",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
                PixelButton("Allow notification access", { capture.openNotificationSettings() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            ToggleRow("Bank text messages", settings.captureMessages) { on ->
                when {
                    !on -> mint.setCaptureMessages(false)
                    capture.messagesGranted() -> mint.setCaptureMessages(true)
                    else -> capture.requestMessages { granted -> mint.setCaptureMessages(granted); if (!granted) note = "Text messages were not allowed." }
                }
            }
            if (settings.captureMessages && !capture.messagesGranted())
                Text("Allow reading text messages in Android's settings, then switch this on again.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if ((settings.captureNotifications || settings.captureMessages) && !capture.alertNotificationsAllowed()) {
                Text("Notifications are off, so MINT cannot tell you when one arrives.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Allow notifications", {
                    capture.requestAlertNotifications { granted -> if (!granted) note = "Notifications were not allowed." }
                }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            val waiting = model.mintData.transactions.count { it.status == TxStatus.PENDING && it.captureRef != null }
            if (erasing) Confirm(
                "Erase the $waiting alerts still waiting? The rest of MINT is untouched.",
                "Erase", "Keep them",
                onConfirm = { scope.launch { capture.erase() }; mint.eraseCaptured(); erasing = false; note = null },
                onKeep = { erasing = false },
            ) else PixelButton(
                if (waiting == 0) "Erase captured alerts" else "Erase $waiting captured alerts",
                { erasing = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true,
            )
            note?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

@Composable
private fun NewCategoryRow(mint: app.pokeplan.app.mint.MintStore, kind: CategoryKind, onAdded: (app.pokeplan.mint.Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelField(name, { name = it.take(MintRules.NAME_MAX); refused = false }, Modifier.weight(1f), label = "New category", placeholder = "Category name", singleLine = true)
        PixelButton("Add", { mint.addCategory(name, kind)?.let(onAdded) ?: run { refused = true } }, color = ModuleAccent.MINT.accent, enabled = name.isNotBlank())
    }
    if (refused) Text("There is already one with that name.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

@Composable
private fun CategoriesContent(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    run {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("Spending", kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, Modifier.weight(1f))
                Chip("Income", kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, Modifier.weight(1f))
            }
            Column {
                data.categories.filter { it.kind == kind }.sortedBy { it.archived }.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    MintLine(onClick = { openId = if (openId == c.id) null else c.id; editing = null; deleting = null }, onClickLabel = "Show options") {
                        Text(if (c.archived) "${c.name} (put away)" else c.name, style = MaterialTheme.typography.bodyLarge, color = if (c.archived) Pixel.InkSoft else Pixel.Ink)
                    }
                    if (openId == c.id) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (editing == c.id) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                PixelField(draft, { draft = it.take(MintRules.NAME_MAX) }, Modifier.weight(1f), label = "Name", singleLine = true)
                                PixelButton("Save", { mint.renameCategory(c.id, draft); editing = null; openId = null }, color = ModuleAccent.MINT.accent, enabled = draft.isNotBlank())
                            }
                        } else if (deleting == c.id) {
                            Text("Delete ${c.name}?", style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PixelButton("Delete", { note = mint.deleteCategory(c.id); deleting = null; openId = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                                PixelButton("Keep it", { deleting = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Rename", { editing = c.id; draft = c.name; deleting = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton(if (c.archived) "Bring back" else "Put away", { mint.setCategoryArchived(c.id, !c.archived); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { deleting = c.id; note = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        }
                    }
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            NewCategoryRow(mint, kind) { }
        }
    }
}
