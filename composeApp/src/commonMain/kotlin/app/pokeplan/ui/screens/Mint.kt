package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pokeplan.app.Tutorials
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.Budget
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.CategoryKind
import app.pokeplan.mint.FinancialAccount
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.Money
import app.pokeplan.mint.Occurrence
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.Transaction
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.DeviceGate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.pokeplan.ui.theme.ListRow
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Mint's screens (docs/pokelife `MODULES/MINT.md`), the manual slice: accounts, activity (expense, income, transfer, refund, fee, adjustment), budgets,
 * bills, a review that holds the reports, the reconciliation and the weekly review, and settings. Every amount goes through `MintStore.show`, so
 * hiding values is one switch for the whole module (section 3.3). Nothing here judges: going over a budget is a number, not a failure.
 */
enum class MintSection(val label: String, val icon: String) {
    ACCOUNTS("Accounts", "member-card"), ACTIVITY("Activity", "journal"), BUDGETS("Budgets", "heart-scale"), BILLS("Bills", "oaks-letter"),
    REVIEW("Review", "scope-lens"), IMPORT("Import", "town-map"), SETTINGS("Settings", "storage-key"),
}

private val zone: TimeZone get() = TimeZone.currentSystemDefault()
private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.MINT.accent

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

/** The line under Mint's card in Modules: how many accounts, or what it is for. Never an amount, so the Modules list shows no figure. */
internal fun mintSubtitle(model: AppModel): String {
    val n = model.mintData.accounts.count { it.status == AccountStatus.ACTIVE }
    return if (n == 0) "money in and out" else if (n == 1) "1 account" else "$n accounts"
}

@Composable
internal fun MintScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    MintGate(model) { ScreenSwitch(model.mintSection, depth = { if (it == null) 0 else 1 }) { section -> MintPage(model, section, openPokemon) } }
}

/** Whether Mint must ask for the phone's screen lock now: it was never opened with it, or the app or the Mint screens were left for longer than the grace. */
private fun mintLocked(model: AppModel, gate: DeviceGate, now: Long): Boolean {
    val away = maxOf(gate.leftAt(), model.mintLeftAt)
    return model.mintUnlockedAt == 0L || (away > model.mintUnlockedAt && now - away > DeviceGate.GRACE_MILLIS)
}

/**
 * The optional access gate (MINT.md section 13.2): with the setting on and a screen lock on the phone, the Mint screens show a locked card until the person proves
 * themselves with that lock, and lock again after the app or the module has been left for a while. A door only: nothing is encrypted with it and no password is
 * made, so it can never make a record unreadable. Without a gate or a lock on the phone it shows the screens at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MintGate(model: AppModel, content: @Composable () -> Unit) {
    val gate = model.gate
    if (gate == null || !model.mintData.settings.gate || !gate.available()) { content(); return }
    var locked by remember { mutableStateOf(mintLocked(model, gate, model.store.nowMillis)) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { model.mintLeftAt = model.store.nowMillis } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(locked) {
        while (!locked) {
            val now = model.store.nowMillis
            // Coming back inside the grace keeps it open. Only while the app is in front: a tick after the app was left must not count as being back, or the door would never close again.
            if (mintLocked(model, gate, now)) locked = true
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.mintUnlockedAt = now
            kotlinx.coroutines.delay(1_000)
        }
    }
    if (!locked) { content(); return }
    fun ask() { gate.authenticate { ok -> if (ok) { model.mintUnlockedAt = model.store.nowMillis; failed = false; locked = false } else failed = true } }
    LaunchedEffect(Unit) { ask() }
    val toModules = { model.module = null }
    BackHandler(enabled = true, onBack = toModules)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Mint is locked")
            Text("Use your phone's screen lock to open it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (failed) Report(model, "That did not open it.", tone = NoticeTone.WARNING)
            PixelButton("Unlock", ::ask, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
            PixelButton("Back", toModules, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
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
    SectionScaffold(section.label, null, onBack = { model.mintSection = null }, icon = section.icon, color = Module.MINT.look.header) {
        when (section) {
            MintSection.ACCOUNTS -> AccountsView(model)
            MintSection.ACTIVITY -> ActivityView(model)
            MintSection.BUDGETS -> BudgetsView(model)
            MintSection.BILLS -> BillsView(model)
            MintSection.REVIEW -> MintReviewView(model)
            MintSection.IMPORT -> MintImportView(model)
            MintSection.SETTINGS -> MintSettingsView(model)
        }
    }
}

@Composable
private fun MintMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.mintData
    val imports = data.transactions.mapNotNull { it.importBatch }.toSet().size
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.MINT.label, onBack = onBack, color = Module.MINT.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.MINT, model.data.player.mintLead, model.store.moduleEvent(app.pokeplan.app.GameStore.MINT_LOG_PREFIX), openPokemon = openPokemon)
            TutorialCard(model, Tutorials.MINT_TALLY, mintRecords = data.accounts.size + data.transactions.size)
            OverviewCard(model)
            MenuGrid(
                listOf(
                    MenuEntry(MintSection.ACCOUNTS.name, MintSection.ACCOUNTS.icon, "Accounts", if (data.accounts.isEmpty()) "add one" else "${data.accounts.count { it.status == AccountStatus.ACTIVE }} open", tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.ACTIVITY.name, MintSection.ACTIVITY.icon, "Activity", if (data.transactions.isEmpty()) "add a purchase" else "${data.transactions.size} records", tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.BUDGETS.name, MintSection.BUDGETS.icon, "Budgets", if (data.budgets.none { it.status == PlanStatus.ACTIVE }) "optional" else "${data.budgets.count { it.status == PlanStatus.ACTIVE }} active", tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.BILLS.name, MintSection.BILLS.icon, "Bills", billsDetail(model), tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.REVIEW.name, MintSection.REVIEW.icon, "Review", "reports and reconcile", tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.IMPORT.name, MintSection.IMPORT.icon, "Import", if (imports == 0) "statement file" else "$imports imports", tint = ModuleAccent.MINT.accent),
                    MenuEntry(MintSection.SETTINGS.name, MintSection.SETTINGS.icon, "Settings", "Ledger Research, privacy, your data", tint = Pixel.InkSoft),
                ),
            ) { key -> model.mintStartType = null; model.mintSection = MintSection.valueOf(key) }
            LedgerLine(model)
            MintPartyPanel(model, openPokemon)
        }
    }
}

private fun billsDetail(model: AppModel): String {
    val today = model.store.today
    val open = MintRules.upcoming(model.mintData, today, 7)
    return if (model.mintData.bills.isEmpty()) "optional" else if (open.isEmpty()) "none due soon" else "${open.size} to settle"
}

@Composable
private fun LedgerLine(model: AppModel) {
    val on = model.mintData.settings.progression
    Text(
        if (on) "Ledger Research is on · Ledger Egg ${model.store.mintEggMeter()} of ${RewardEngine.MINT_EGG_METER_SIZE}" else "Ledger Research is off",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

// ----- Overview ---------------------------------------------------------------------------------------------------------

/**
 * The first screen (MINT.md section 3.2): net position when there is something to count, available money with its basis shown, the next bills, and the
 * three quick actions. With no account yet it is one title and one action.
 */
@Composable
private fun OverviewCard(model: AppModel) {
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
    val position = MintRules.netPosition(data)
    val currency = data.settings.currency
    val available = MintRules.available(data, currency, today)
    val upcoming = MintRules.upcoming(data, today, 14).take(3)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Overview")
            position.forEach { (cur, minor) -> Text("Net position: ${mint.show(minor, cur)}", style = MaterialTheme.typography.titleSmall) }
            if (available.liquidMinor != 0L || available.billsCount > 0) {
                Text("Available: ${mint.show(available.availableMinor, currency)}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Cash, banks and wallets ${mint.show(available.liquidMinor, currency)}" + if (available.billsCount > 0) ", less ${available.billsCount} bill${if (available.billsCount == 1) "" else "s"} in 14 days ${mint.show(available.billsMinor, currency)}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
            }
            upcoming.forEach { o -> Text("${o.bill.name}: ${o.state.label.lowercase()}, ${o.dueDay.short()}", style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Expense", { model.mintStartType = TxType.EXPENSE; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                PixelButton("Income", { model.mintStartType = TxType.INCOME; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Transfer", { model.mintStartType = TxType.TRANSFER; model.mintSection = MintSection.ACTIVITY }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

// ----- Accounts ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var currency by remember { mutableStateOf(data.settings.currency) }
    var opening by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var suffix by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    fun save() {
        val minor = if (opening.isBlank()) 0L else Money.parse(opening, currency)
        if (minor == null) { message = "Check the opening balance." to NoticeTone.WARNING; return }
        val out = mint.addAccount(name, type, currency, minor, suffix = suffix, group = group)
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else { message = null; name = ""; opening = ""; suffix = ""; group = ""; more = false; model.haptic() }
    }
    val balances = MintRules.balances(data)
    val shown = balances.filter { it.account.status == AccountStatus.ACTIVE } + balances.filter { it.account.status != AccountStatus.ACTIVE }
    if (shown.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            shown.forEach { b ->
                val a = b.account
                ListRow(
                    trail = { Text(mint.show(b.postedMinor, a.currency), style = MaterialTheme.typography.titleSmall) },
                    onClick = { open = if (open == a.id) null else a.id }, onClickLabel = "Show options",
                ) {
                    Column {
                        Text(a.name + if (a.suffix.isNotEmpty()) " ···${a.suffix}" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOf(a.type.label, a.group.takeIf { it.isNotEmpty() }, a.status.name.lowercase().takeIf { a.status != AccountStatus.ACTIVE }).filterNotNull().joinToString(" · ") +
                                if (b.pendingMinor != 0L) " · pending ${mint.show(b.pendingMinor, a.currency)}" else "",
                            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                        )
                    }
                }
                if (open == a.id) AccountOptions(model, a) { open = null }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("New account")
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                AccountType.entries.forEach { t -> Chip(t.label, t == type, { type = t }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Money.common.take(6).forEach { c -> Chip(c, c == currency, { currency = c }) }
            }
            PixelField(
                opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                label = if (type.liability) "Amount owed now, optional" else "Balance now, optional", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PixelButton(if (more) "Fewer options" else "Group and last digits", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group, optional", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits, optional", singleLine = true, helper = "Never a full number, a PIN or a code.")
            }
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = name.isNotBlank())
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountOptions(model: AppModel, a: FinancialAccount, startEditing: Boolean = false, onDone: () -> Unit) {
    val mint = model.mint
    var asking by remember(a.id) { mutableStateOf(false) }
    var editing by remember(a.id) { mutableStateOf(startEditing) }
    var name by remember(a.id) { mutableStateOf(a.name) }
    var type by remember(a.id) { mutableStateOf(a.type) }
    var currency by remember(a.id) { mutableStateOf(a.currency) }
    var opening by remember(a.id) { mutableStateOf(Money.plain(a.openingMinor, a.currency)) }
    var institution by remember(a.id) { mutableStateOf(a.institution) }
    var group by remember(a.id) { mutableStateOf(a.group) }
    var suffix by remember(a.id) { mutableStateOf(a.suffix) }
    var refused by remember(a.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (editing) {
            val locked = mint.hasHistory(a.id)
            val minor = Money.parse(opening.ifBlank { "0" }, currency)
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                AccountType.entries.filter { it.liability == a.type.liability }.forEach { t -> Chip(t.label, t == type, { type = t }) }
            }
            if (locked) Text("${a.currency} stays, because this account has records. Everything else can change.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Money.common.take(6).forEach { c -> Chip(c, c == currency, { if (c != currency) { opening = Money.parse(opening.ifBlank { "0" }, currency)?.let { m -> Money.plain(m, c) } ?: opening; currency = c } }) }
            }
            PixelField(
                opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                label = if (type.liability) "Amount owed when you started tracking" else "Balance when you started tracking", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), helper = "Records stay as they are; the balance is worked out from this and them.",
            )
            PixelField(institution, { institution = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Bank or provider, optional", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group, optional", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits, optional", singleLine = true, helper = "Never a full number, a PIN or a code.")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", {
                    if (minor == null) refused = "Check the balance."
                    else mint.updateAccount(a.copy(name = name, type = type, currency = currency, openingMinor = minor, institution = institution, group = group, suffix = suffix))
                        .let { if (it.record != null) { editing = false; refused = null; onDone() } else refused = it.refused }
                }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = name.isNotBlank(), compact = true)
                PixelButton("Cancel", { editing = false; refused = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        } else if (asking) {
            Confirm("Delete this account? Only an account with no records can be deleted.", "Delete", onConfirm = { refused = mint.deleteAccount(a.id); asking = false; if (refused == null) onDone() }, onKeep = { asking = false })
        } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            when (a.status) {
                AccountStatus.ACTIVE -> {
                    PixelButton("Archive", { mint.setAccountStatus(a.id, AccountStatus.ARCHIVED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Close", { mint.setAccountStatus(a.id, AccountStatus.CLOSED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
                else -> PixelButton("Reopen", { mint.setAccountStatus(a.id, AccountStatus.ACTIVE); onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
            }
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Activity ---------------------------------------------------------------------------------------------------------

/** How many records the Activity list shows at a time; "Show more" adds another page. */
private const val PAGE = 50

private val quickTypes = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER, TxType.REFUND, TxType.FEE, TxType.ADJUSTMENT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val open = data.accounts.filter { it.status == AccountStatus.ACTIVE }
    var type by remember { mutableStateOf(model.mintStartType ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var up by remember { mutableStateOf(true) }
    // null: it happened just now (read when Save is pressed).
    var payAt by remember { mutableStateOf<Long?>(null) }
    var more by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var payee by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<TxType?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    val from = open.firstOrNull { it.id == accountId } ?: open.firstOrNull()
    val currency = from?.currency ?: data.settings.currency
    // Where a transfer can go: another open account in the same currency. The one shown as chosen is the one saved.
    val targets = open.filter { it.id != from?.id && it.currency == from?.currency }
    val to = targets.firstOrNull { it.id == toId } ?: targets.firstOrNull()
    var limit by remember { mutableStateOf(PAGE) }
    if (open.isEmpty()) {
        EmptyState("Add an account first", null, icon = "member-card", action = "Add account" to { model.mintSection = MintSection.ACCOUNTS })
        return
    }
    fun save() {
        val minor = Money.parse(amount, currency)
        if (minor == null) { message = "Check the amount." to NoticeTone.WARNING; return }
        val at = payAt ?: model.store.nowMillis
        val out = mint.record(
            type, minor, from!!.id, toAccountId = if (type == TxType.TRANSFER) to?.id else null,
            categoryId = categoryId.takeIf { type != TxType.TRANSFER && type != TxType.ADJUSTMENT }, payee = payee, note = note, atMillis = at, up = up,
        )
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else { message = null; model.say("Saved."); payAt = null; amount = ""; payee = ""; note = ""; more = false; model.haptic() }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                quickTypes.forEach { t -> Chip(t.label, t == type, { type = t; categoryId = null }) }
            }
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount ($currency)", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            SectionLabel(if (type == TxType.TRANSFER) "From" else "Account")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                open.forEach { a -> Chip(a.name, a.id == from?.id, { accountId = a.id; if (toId == a.id) toId = null }) }
            }
            if (type == TxType.TRANSFER) {
                SectionLabel("To")
                if (targets.isEmpty()) Text("Add a second account in the same currency.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    targets.forEach { a -> Chip(a.name, a.id == to?.id, { toId = a.id }) }
                }
            } else if (type == TxType.ADJUSTMENT) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Balance goes up", up, { up = true }, Modifier.weight(1f)); Chip("Balance goes down", !up, { up = false }, Modifier.weight(1f))
                }
            } else {
                val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Chip("None", categoryId == null, { categoryId = null })
                    data.categories.filter { it.kind == kind && !it.archived }.forEach { c -> Chip(c.name, c.id == categoryId, { categoryId = c.id }) }
                    Chip(if (addingCategory) "Cancel" else "New", addingCategory, { addingCategory = !addingCategory })
                }
                if (addingCategory) NewCategoryRow(mint, kind) { categoryId = it.id; addingCategory = false }
            }
            PixelMomentField(payAt ?: model.store.nowMillis, { payAt = it }, model.store.nowMillis, ModuleAccent.MINT.accent, label = "When")
            PixelButton(if (more) "Fewer options" else "Payee and a note", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = "Payee or source, optional", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                PixelField(note, { note = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note, optional", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = amount.isNotBlank())
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
    val found = remember(data, query, filter) { MintRules.search(data, query, filter) }
    if (data.transactions.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(40); limit = PAGE }, Modifier.fillMaxWidth(), placeholder = "Search payee, note, category or amount", singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("All", filter == null, { filter = null })
                quickTypes.forEach { t -> Chip(t.label, filter == t, { filter = if (filter == t) null else t }) }
            }
            if (found.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            found.take(limit).forEach { t ->
                TransactionRow(model, t, openId == t.id) { openId = if (openId == t.id) null else t.id }
                if (openId == t.id) TransactionOptions(model, t) { openId = null }
            }
            if (found.size > limit) PixelButton("Show ${minOf(PAGE, found.size - limit)} more", { limit += PAGE }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
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
    val category = data.categories.firstOrNull { it.id == t.categoryId }?.name
    val account = data.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()
    val to = data.accounts.firstOrNull { it.id == t.toAccountId }?.name
    val title = t.payee.ifBlank { category ?: t.type.label }
    val line = listOfNotNull(t.day.short(), if (to != null) "$account to $to" else account, category.takeIf { t.payee.isNotBlank() }, t.status.name.lowercase().takeIf { t.status != TxStatus.POSTED }, (if (t.unreviewed) "imported, unreviewed" else "imported").takeIf { t.importBatch != null }).joinToString(" · ")
    ListRow(
        trail = { Text(t.signed() + model.mint.show(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall) },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 3)
        }
    }
}

@Composable
private fun TransactionOptions(model: AppModel, t: Transaction, onDone: () -> Unit) {
    val mint = model.mint
    var editing by remember(t.id) { mutableStateOf(false) }
    var asking by remember(t.id) { mutableStateOf(false) }
    var amount by remember(t.id) { mutableStateOf(Money.plain(t.amountMinor, t.currency)) }
    var payee by remember(t.id) { mutableStateOf(t.payee) }
    var note by remember(t.id) { mutableStateOf(t.note) }
    var refused by remember(t.id) { mutableStateOf<String?>(null) }
    var at by remember(t.id) { mutableStateOf(t.atMillis) }
    var categoryId by remember(t.id) { mutableStateOf(t.categoryId) }
    var accountId by remember(t.id) { mutableStateOf(t.accountId) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (editing) {
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount (${t.currency})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = "Payee or source", singleLine = true)
            PixelField(note, { note = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note")
            PixelMomentField(at, { at = it }, model.store.nowMillis, ModuleAccent.MINT.accent, label = "When", nowKey = false)
            val accounts = model.mintData.accounts.filter { (it.status == AccountStatus.ACTIVE && it.currency == t.currency) || it.id == t.accountId }
            if (accounts.size > 1 && t.type != TxType.TRANSFER) {
                SectionLabel("Account")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    accounts.forEach { a -> Chip(a.name, a.id == accountId, { accountId = a.id }) }
                }
            }
            if (t.type != TxType.TRANSFER && t.type != TxType.ADJUSTMENT) {
                SectionLabel("Category")
                val kind = if (t.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Chip("None", categoryId == null, { categoryId = null })
                    model.mintData.categories.filter { it.kind == kind && (!it.archived || it.id == t.categoryId) }.forEach { c -> Chip(c.name, c.id == categoryId, { categoryId = c.id }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", {
                    val minor = Money.parse(amount, t.currency)
                    if (minor == null) refused = "Check the amount."
                    else mint.updateTransaction(t.copy(amountMinor = minor, payee = payee, note = note, atMillis = at, accountId = accountId, categoryId = categoryId)).let { if (it.record != null) { editing = false; onDone() } else refused = it.refused }
                }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                PixelButton("Cancel", { editing = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        } else if (asking) {
            Confirm("Delete this record? Your balance changes with it.", "Delete", onConfirm = { val before = model.mintData; mint.deleteTransaction(t.id); onDone(); model.offerUndo("Deleted: record") { mint.restore(before) } }, onKeep = { asking = false })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                val excluded = t.status == TxStatus.EXCLUDED
                PixelButton(if (excluded) "Include" else "Exclude", { mint.setTransactionStatus(t.id, if (excluded) TxStatus.POSTED else TxStatus.EXCLUDED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                when (t.status) {
                    TxStatus.PENDING -> PixelButton("Mark posted", { mint.setTransactionStatus(t.id, TxStatus.POSTED); onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    TxStatus.POSTED -> PixelButton("Mark pending", { mint.setTransactionStatus(t.id, TxStatus.PENDING); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    else -> {}
                }
                if (t.status == TxStatus.VOIDED) PixelButton("Restore", { mint.setTransactionStatus(t.id, TxStatus.POSTED); onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                else PixelButton("Void", { mint.setTransactionStatus(t.id, TxStatus.VOIDED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Budgets ----------------------------------------------------------------------------------------------------------

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
    val currency = data.settings.currency
    val budgets = data.budgets.filter { it.status != PlanStatus.ARCHIVED }
    if (budgets.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            budgets.forEach { b ->
                val p = MintRules.progress(data, b, today, model.data.settings.weekStart)
                val label = data.categories.firstOrNull { it.id == b.categoryId }?.name ?: "Everything"
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    ListRow(
                        trail = { Text(if (b.status == PlanStatus.PAUSED) "paused" else "${p.percent ?: 0}%", style = MaterialTheme.typography.titleSmall) },
                        onClick = { openId = if (openId == b.id) null else b.id; newAmount = Money.plain(b.plannedMinor, b.currency) }, onClickLabel = "Show options",
                    ) {
                        Column {
                            Text("$label · ${b.period.label.lowercase()}", style = MaterialTheme.typography.bodyLarge)
                            Text("${mint.show(p.spentMinor, b.currency)} of ${mint.show(b.plannedMinor, b.currency)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                    PixelBar((p.percent ?: 0) / 100f, color = ModuleAccent.MINT.accent, label = "$label: ${p.percent ?: 0} percent used")
                    Text(
                        if (p.remainingMinor >= 0) "${mint.show(p.remainingMinor, b.currency)} left until ${p.endExclusive.short()}" else "${mint.show(-p.remainingMinor, b.currency)} over the plan",
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                    if (openId == b.id) {
                        PixelField(newAmount, { newAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount (${b.currency})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Save", { Money.parse(newAmount, b.currency)?.let { if (mint.updateBudgetAmount(b.id, it)) openId = null } }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                            PixelButton(if (b.status == PlanStatus.PAUSED) "Resume" else "Pause", { mint.setBudgetStatus(b.id, if (b.status == PlanStatus.PAUSED) PlanStatus.ACTIVE else PlanStatus.PAUSED); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { val before = model.mintData; mint.deleteBudget(b.id); openId = null; model.offerUndo("Deleted: budget") { mint.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("New budget")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("Everything", categoryId == null, { categoryId = null })
                data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.forEach { c -> Chip(c.name, c.id == categoryId, { categoryId = c.id }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                BudgetPeriod.entries.forEach { p -> Chip(p.label, p == period, { period = p }, Modifier.weight(1f)) }
            }
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount ($currency)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            PixelButton("Save", {
                val minor = Money.parse(amount, currency)
                val out = minor?.let { mint.addBudget(it, currency, period, categoryId) }
                message = if (minor == null) "Check the amount." else out?.refused
                if (out?.record != null) { amount = ""; model.haptic() }
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = amount.isNotBlank())
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

// ----- Bills ------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillsView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var firstDue by remember { mutableStateOf<LocalDate?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editFirst by remember { mutableStateOf<LocalDate?>(null) }
    var editFrequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var message by remember { mutableStateOf<String?>(null) }
    var openKey by remember { mutableStateOf<String?>(null) }
    var partial by remember { mutableStateOf("") }
    var paidOn by remember { mutableStateOf<LocalDate?>(null) }
    var manage by remember { mutableStateOf(false) }
    // Settling takes the occurrence off the list, so every settlement offers an undo.
    fun settle(o: Occurrence, status: SettleStatus, paid: Long, label: String) {
        val before = model.mintData
        if (mint.settle(o.bill.id, o.dueDay, status, paid, paidDay = paidOn ?: today)) { paidOn = null; openKey = null; model.offerUndo("$label: ${o.bill.name}") { mint.restore(before) } }
    }
    val currency = data.settings.currency
    val upcoming = MintRules.upcoming(data, today, 45)
    if (upcoming.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("To settle")
            upcoming.forEach { o ->
                val key = o.bill.id + o.dueDay
                OccurrenceRow(model, o, openKey == key) { openKey = if (openKey == key) null else key; partial = ""; paidOn = null }
                if (openKey == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    val full = o.remainingMinor ?: o.bill.expectedMinor
                    Text("Marking a bill paid does not add a transaction; add one in Activity if you want the money to leave an account.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (full == null) PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount paid (${o.bill.currency})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    else PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Paid so far, if only part (${o.bill.currency})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    SectionLabel("Settled on")
                    PixelDateField(paidOn ?: today, today, ModuleAccent.MINT.accent, { paidOn = it }, presets = listOf("Today" to 0, "Yesterday" to -1), max = today)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("Paid", {
                            val paid = if (full == null) Money.parse(partial, o.bill.currency) else o.bill.expectedMinor
                            if (paid != null) { settle(o, SettleStatus.PAID, paid, "Paid"); model.effect(Sfx.CANDY) }
                        }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = full != null || Money.parse(partial, o.bill.currency) != null, compact = true)
                        if (full != null) PixelButton("Part", { Money.parse(partial, o.bill.currency)?.let { settle(o, SettleStatus.PARTIAL, it, "Part paid") } }, Modifier.weight(1f), Pixel.InkSoft, enabled = Money.parse(partial, o.bill.currency) != null, compact = true)
                        PixelButton("Skip", { settle(o, SettleStatus.SKIPPED, 0, "Skipped") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        if (o.payment != null) PixelButton("Undo", { mint.unsettle(o.bill.id, o.dueDay); openKey = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("New bill")
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount ($currency), blank if it changes", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Frequency.entries.forEach { f -> Chip(f.label, f == frequency, { frequency = f }) }
            }
            SectionLabel("First due")
            PixelDateField(firstDue ?: today, today, ModuleAccent.MINT.accent, { firstDue = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
            PixelButton("Save", {
                val minor = if (amount.isBlank()) null else Money.parse(amount, currency)
                if (amount.isNotBlank() && minor == null) message = "Check the amount."
                else {
                    val out = mint.addBill(name, currency, frequency, firstDue ?: today, minor)
                    message = out.refused
                    if (out.record != null) { name = ""; amount = ""; firstDue = null; model.haptic() }
                }
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = name.isNotBlank())
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
    if (data.bills.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton(if (manage) "Hide your bills" else "Your bills (${data.bills.size})", { manage = !manage }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (manage) data.bills.forEach { b ->
                ListRow(
                    trail = { Text(b.expectedMinor?.let { mint.show(it, b.currency) } ?: "varies", style = MaterialTheme.typography.titleSmall) },
                    onClick = { editId = if (editId == b.id) null else b.id; editName = b.name; editAmount = b.expectedMinor?.let { Money.plain(it, b.currency) }.orEmpty(); editFirst = b.firstDue; editFrequency = b.frequency }, onClickLabel = "Edit",
                ) {
                    Column {
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${b.frequency.label.lowercase()}${if (b.status != PlanStatus.ACTIVE) " · ${b.status.name.lowercase()}" else ""}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
                if (editId == b.id) {
                    // The name and the amount can change (rent goes up). The first due day and how often can change until a payment is
                    // recorded against the bill, so no settled occurrence is ever rewritten.
                    val scheduleOpen = data.billPayments.none { it.billId == b.id }
                    val minor = if (editAmount.isBlank()) null else Money.parse(editAmount, b.currency)
                    PixelField(editName, { editName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    PixelField(editAmount, { editAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount (${b.currency}), blank if it changes", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (scheduleOpen) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Frequency.entries.forEach { f -> Chip(f.label, f == editFrequency, { editFrequency = f }) }
                        }
                        SectionLabel("First due")
                        PixelDateField(editFirst ?: b.firstDue, today, ModuleAccent.MINT.accent, { editFirst = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
                    } else Text("Its schedule is fixed because payments are recorded. To change it, pause this bill and add a new one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelButton("Save", { if (mint.updateBill(b.copy(name = editName, expectedMinor = minor, firstDue = editFirst ?: b.firstDue, frequency = editFrequency)).let { r -> message = r.refused; r.record != null }) editId = null }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = editName.isNotBlank() && (editAmount.isBlank() || minor != null), compact = true)
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

@Composable
private fun OccurrenceRow(model: AppModel, o: Occurrence, open: Boolean, onClick: () -> Unit) {
    val amount = o.remainingMinor ?: o.bill.expectedMinor
    ListRow(
        trail = { Text(amount?.let { model.mint.show(it, o.bill.currency) } ?: "varies", style = MaterialTheme.typography.titleSmall) },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Column {
            Text(o.bill.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text("${o.state.label} · ${o.dueDay.short()}", style = MaterialTheme.typography.bodyMedium, color = if (o.state == OccurrenceState.OVERDUE) Pixel.Warning else Pixel.InkSoft)
        }
    }
}

// ----- Review: reports, reconcile, the weekly review -------------------------------------------------------------------------

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
            if (data.transactions.none { it.day >= from && it.day < end && it.currency == currency }) Text("Nothing recorded in $currency yet.", style = MaterialTheme.typography.bodyLarge)
            else {
                Text("Money in: ${mint.show(earned, currency)}", style = MaterialTheme.typography.bodyLarge)
                Text("Spent: ${mint.show(spent.coerceAtLeast(0), currency)}", style = MaterialTheme.typography.bodyLarge)
                Text("Difference: ${mint.show(earned - spent, currency)}", style = MaterialTheme.typography.bodyLarge)
                MintRules.spendingByCategory(data, from, end, currency).take(6).forEach { (id, minor) ->
                    Text("${data.categories.firstOrNull { it.id == id }?.name ?: "No category"}: ${mint.show(minor, currency)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                Text("Calculated from the records on this phone. Transfers between your accounts are left out; pending and excluded records are too.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    ReconcileCard(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Weekly review")
            val first = model.data.settings.weekStart
            val weekDone = data.reviews.any { MintRules.weekStartOf(it.day, first) == MintRules.weekStartOf(today, first) }
            val soon = MintRules.upcoming(data, today, 7)
            Text(
                "${data.accounts.count { it.status == AccountStatus.ACTIVE }} open accounts · ${soon.size} bill${if (soon.size == 1) "" else "s"} to settle this week · ${data.budgets.count { it.status == PlanStatus.ACTIVE }} budgets",
                style = MaterialTheme.typography.bodyLarge,
            )
            PixelButton(if (weekDone) "Reviewed this week" else "Finish this week's review", { mint.finishReview(); model.effect(Sfx.ITEM_GET); model.say("Saved.") }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = !weekDone && data.accounts.isNotEmpty())
        }
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
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var difference by remember { mutableStateOf<Long?>(null) }
    val account = open.firstOrNull { it.id == accountId } ?: open.first()
    val book = MintRules.balance(data, account).postedMinor
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Reconcile an account")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                open.forEach { a -> Chip(a.name, a.id == account.id, { accountId = a.id; difference = null; message = null }) }
            }
            Text("Mint has ${mint.show(book, account.currency)}", style = MaterialTheme.typography.bodyLarge)
            PixelField(
                statement, { statement = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); difference = null }, Modifier.fillMaxWidth(),
                label = "Your statement says (${account.currency})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val parsed = Money.parse(statement, account.currency)
            PixelButton("Compare", {
                if (parsed == null) { message = "Check the amount." to NoticeTone.WARNING; return@PixelButton }
                val diff = MintRules.reconcileDifference(parsed, book)
                if (diff == 0L) { mint.reconcile(account.id, parsed); model.effect(Sfx.ITEM_GET); message = null; model.say("They match. Saved."); statement = ""; difference = null }
                else { difference = diff; message = null }
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = parsed != null)
            difference?.let { diff ->
                Text("The statement is ${mint.show(kotlin.math.abs(diff), account.currency)} ${if (diff > 0) "higher" else "lower"} than Mint. Accepting records the difference as a balance adjustment; nothing else changes.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Accept the difference", {
                        parsed?.let { mint.reconcile(account.id, it, acceptAdjustment = true); model.effect(Sfx.ITEM_GET) }
                        message = null; model.say("Adjustment recorded. Saved."); statement = ""; difference = null
                    }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    PixelButton("Not now", { difference = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
}

// ----- Settings ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MintSettingsView(model: AppModel) {
    val mint = model.mint
    val settings = model.mintData.settings
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<MintKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Ledger Research")
            ToggleRow(
                "Find Pokémon from reviews", settings.progression,
                detail = "On by default. Reconciling an account and finishing the weekly review can find a Ledger Egg. Spending, income, balances and every amount never earn anything, and nothing about your money leaves Mint.",
            ) { mint.setProgression(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Bill reminders")
            ToggleRow(
                "Remind me about bills", settings.billReminders,
                detail = "Off by default. One reminder for each bill that is due, never a daily nag for one that is late. It never shows an amount, and it never marks a bill paid; you do that in Mint.",
            ) { mint.setBillReminders(it) }
            if (settings.billReminders) {
                SectionLabel("How early")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(0 to "On the day", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 7 to "A week before").forEach { (d, label) -> Chip(label, d == settings.billReminderDays, { mint.setBillReminderDays(d) }) }
                }
                SectionLabel("At")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(8, 9, 12, 18, 20).forEach { h -> Chip("${h.toString().padStart(2, '0')}:00", h == settings.billReminderHour, { mint.setBillReminderHour(h) }) }
                }
                Text("Your quiet hours and focus sessions still hold a reminder back.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                ToggleRow("Name the bill", settings.billReminderNames, detail = "After you unlock your phone it says which bill. The lock screen always says only that a bill is coming up.") { mint.setBillReminderNames(it) }
            }
        }
    }
    CategoriesBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Privacy")
            ToggleRow("Hide amounts", settings.hideValues, detail = "Every amount in Mint shows as dots.") { mint.setHideValues(it) }
            model.gate?.let { gate ->
                if (gate.available() || settings.gate) ToggleRow(
                    "Ask for my screen lock", settings.gate,
                    detail = "Mint stays closed until your phone's own lock (PIN, pattern, password or fingerprint) opens it, and closes again after you leave. It does not change how your records are kept, and it cannot lock you out of them.",
                ) { on ->
                    if (!on) mint.setGate(false)
                    else gate.authenticate { ok -> if (ok) { mint.setGate(true); model.mintUnlockedAt = model.store.nowMillis } }
                } else Text("Set a screen lock on this phone first, and Mint can ask for it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            PixelTitle("Currency")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Money.common.forEach { c -> Chip(c, c == settings.currency, { mint.setCurrency(c) }) }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            Text(
                "Your records stay on this phone. Export what you choose as a file you can read (amounts are whole minor units, like centavos, with their currency), or erase one kind at a time. Erasing Mint never touches your Pokémon, your Poké, your Bag or the other modules.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-mint.json", mint.export()) }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MintKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-mint-${k.name.lowercase()}.json", mint.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            } else Text("Exporting is not available on this device.", style = MaterialTheme.typography.bodyMedium)
            note?.let { Report(model, it) }
            MintKinds.entries.forEach { k ->
                if (asking == k) Confirm(
                    if (k == MintKinds.ACCOUNTS) "Erase all accounts and the records that belong to them? This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                    "Erase", "Keep them", onConfirm = { mint.erase(k); asking = null }, onKeep = { asking = null },
                ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) Confirm("Erase every Mint record? This cannot be undone.", "Erase all", "Keep them", onConfirm = { mint.eraseAll(); askingAll = false }, onKeep = { askingAll = false })
            else PixelButton("Erase all Mint records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
    Text("Statement files are under Import. No bank or e-wallet is connected, and Mint is complete without one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

/** A name field and an Add key for a new category of [kind]; [onAdded] gets it (the entry form selects it). */
@Composable
private fun NewCategoryRow(mint: app.pokeplan.app.MintStore, kind: CategoryKind, onAdded: (app.pokeplan.mint.Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelField(name, { name = it.take(MintRules.NAME_MAX); refused = false }, Modifier.weight(1f), placeholder = "Category name", singleLine = true)
        PixelButton("Add", { mint.addCategory(name, kind)?.let(onAdded) ?: run { refused = true } }, color = ModuleAccent.MINT.accent, enabled = name.isNotBlank())
    }
    if (refused) Text("There is already one with that name.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

/** Mint, Settings: the categories of spending and income, to add, rename, put away or delete (MINT.md section 6). */
@Composable
private fun CategoriesBox(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Categories")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("Spending", kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, Modifier.weight(1f))
                Chip("Income", kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, Modifier.weight(1f))
            }
            data.categories.filter { it.kind == kind }.sortedBy { it.archived }.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (editing == c.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelField(draft, { draft = it.take(MintRules.NAME_MAX) }, Modifier.weight(1f), singleLine = true)
                            PixelButton("Save", { mint.renameCategory(c.id, draft); editing = null }, color = ModuleAccent.MINT.accent, enabled = draft.isNotBlank())
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (c.archived) "${c.name} (put away)" else c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (c.archived) Pixel.InkSoft else Pixel.Ink)
                        PixelButton("Rename", { editing = c.id; draft = c.name; deleting = null }, color = Pixel.InkSoft, compact = true)
                        PixelButton(if (c.archived) "Bring back" else "Put away", { mint.setCategoryArchived(c.id, !c.archived) }, color = Pixel.InkSoft, compact = true)
                        PixelButton("Delete", { deleting = c.id; note = null }, color = Pixel.Danger, compact = true)
                    }
                    if (deleting == c.id) {
                        Text("Delete ${c.name}?", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("Delete", { note = mint.deleteCategory(c.id); deleting = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                            PixelButton("Keep it", { deleting = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            NewCategoryRow(mint, kind) { }
        }
    }
}
