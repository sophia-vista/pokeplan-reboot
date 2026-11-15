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
import androidx.compose.ui.text.input.KeyboardType
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
import app.pokeplan.ui.theme.FieldLabel
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
import app.pokeplan.ui.screens.PixelMomentField
import app.pokeplan.ui.screens.ScreenLockGate
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

/**
 * Mint's screens (docs/pokelife `MODULES/MINT.md`), the manual slice: accounts, activity (expense, income, transfer, refund, fee, adjustment), budgets,
 * bills, a review that holds the reports, the reconciliation and the weekly review, and settings. Every amount goes through `MintStore.show`, so
 * hiding values is one switch for the whole module (section 3.3). Nothing here judges: going over a budget is a number, not a failure.
 */
enum class MintSection(val label: String, val icon: String) {
    ACCOUNTS("Accounts", "member-card"), ACTIVITY("Activity", "journal"), BUDGETS("Budgets", "heart-scale"), BILLS("Bills and income", "oaks-letter"),
    REVIEW("Review", "scope-lens"), IMPORT("Import", "town-map"), SETTINGS("Settings", "storage-key"),
}

private val zone: TimeZone get() = TimeZone.currentSystemDefault()
private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.MINT.accent

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

/** A flat row for a list inside one box (rows are divided by [HorizontalDivider], never boxed): content at the left, [trail] at the right, one centre line. */
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

/** A label at the left and its value at the right. */
@Composable
internal fun MintValueRow(label: String, value: String, soft: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f), style = if (soft) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = if (soft) Pixel.InkSoft else Pixel.Ink)
        Text(value, style = if (soft) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall)
    }
}

/** The line under Mint's card in Modules: how many accounts, or what it is for. Never an amount, so the Modules list shows no figure. */
internal fun mintSubtitle(model: AppModel): String {
    val n = model.mintData.accounts.count { it.status == AccountStatus.ACTIVE }
    return if (n == 0) "money in and out" else if (n == 1) "1 account" else "$n accounts"
}

@Composable
internal fun MintScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenLockGate(model, Module.MINT, model.mintData.settings.gate) { ScreenSwitch(model.mintSection, depth = { if (it == null) 0 else 1 }) { section -> MintPage(model, section, openPokemon) } }
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
    val sections = listOf(
        MenuEntry(MintSection.ACCOUNTS.name, MintSection.ACCOUNTS.icon, "Accounts", if (data.accounts.isEmpty()) "add one" else "${data.accounts.count { it.status == AccountStatus.ACTIVE }} open", tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.ACTIVITY.name, MintSection.ACTIVITY.icon, "Activity", if (data.transactions.isEmpty()) "add a purchase" else "${data.transactions.size} records", tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.BUDGETS.name, MintSection.BUDGETS.icon, "Budgets", if (data.budgets.none { it.status == PlanStatus.ACTIVE }) "optional" else "${data.budgets.count { it.status == PlanStatus.ACTIVE }} active", tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.BILLS.name, MintSection.BILLS.icon, "Bills and income", billsDetail(model), tint = ModuleAccent.MINT.accent),
        MenuEntry(MintSection.REVIEW.name, MintSection.REVIEW.icon, "Review", "reports and reconcile", tint = ModuleAccent.MINT.accent),
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
            MintPartyPanel(model, openPokemon)
        }
    }
}

private fun billsDetail(model: AppModel): String {
    val today = model.store.today
    val open = MintRules.upcoming(model.mintData, today, 7)
    return if (model.mintData.bills.isEmpty()) "optional" else if (open.isEmpty()) "none due soon" else "${open.size} to settle"
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
    // One bill's next date is enough here.
    val upcoming = MintRules.upcoming(data, today, 14).distinctBy { it.bill.id }.take(3)
    val leadsWithAvailable = available.liquidMinor != 0L || available.billsCount > 0
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Overview")
            if (leadsWithAvailable) {
                Text(mint.show(available.availableMinor, currency), style = MaterialTheme.typography.titleLarge)
                position.forEach { (cur, minor) -> MintValueRow("Net position", mint.show(minor, cur), soft = true) }
            } else position.forEach { (cur, minor) -> Text(mint.show(minor, cur), style = MaterialTheme.typography.titleLarge) }
            upcoming.forEach { o ->
                MintValueRow(o.bill.name, if (o.dueDay == today) "Today" else if (o.state == OccurrenceState.OVERDUE) "Overdue" else o.dueDay.short(), soft = true)
            }
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
    var card by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(data.accounts.isEmpty()) }
    var typesInfo by remember { mutableStateOf(false) }
    if (typesInfo) AccountTypesInfo { typesInfo = false }
    fun save() {
        val minor = if (opening.isBlank()) 0L else Money.parse(opening, currency)
        if (minor == null) { message = "Check the opening balance." to NoticeTone.WARNING; return }
        val first = data.accounts.none { it.status == AccountStatus.ACTIVE }
        val out = mint.addAccount(name, type, currency, minor, suffix = suffix, group = group, card = card)
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else {
            message = null; name = ""; opening = ""; suffix = ""; group = ""; card = ""; more = false; adding = false; model.haptic()
            // The first account ends on the overview, where Expense is the one next action (REP-056).
            if (first) model.mintSection = null
        }
    }
    val balances = MintRules.balances(data)
    val shown = balances.filter { it.account.status == AccountStatus.ACTIVE } + balances.filter { it.account.status != AccountStatus.ACTIVE }
    if (shown.isNotEmpty()) TextBox {
        Column {
            shown.forEachIndexed { i, b ->
                val a = b.account
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                MintLine(
                    trail = { Text(mint.show(b.postedMinor, a.currency), style = MaterialTheme.typography.titleSmall) },
                    onClick = { open = if (open == a.id) null else a.id }, onClickLabel = "Show options",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Box(Modifier.size(10.dp).background(cardColor(CardStyle.of(a.card))))
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    val meta = listOfNotNull(
                        if (a.suffix.isNotEmpty()) "···${a.suffix}" else a.type.label,
                        a.status.name.lowercase().takeIf { a.status != AccountStatus.ACTIVE },
                        "pending ${mint.show(b.pendingMinor, a.currency)}".takeIf { b.pendingMinor != 0L },
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (open == a.id) AccountOptions(model, a) { open = null }
            }
        }
    }
    if (!adding) {
        PixelButton("Add account", { adding = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
        return
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PixelTitle("Where is this money?") }
                PixelButton("?", { typesInfo = true }, color = Pixel.InkSoft, compact = true)
            }
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", placeholder = "GCash", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                AccountType.entries.forEach { t -> Chip(t.label, t == type, { type = t }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Money.common.take(6).forEach { c -> Chip(c, c == currency, { currency = c }) }
            }
            PixelField(
                opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                label = if (type.liability) "Amount owed now" else "Balance now", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CardStylePicker(card) { card = it }
            PixelButton(if (more) "Fewer options" else "Group and last digits", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                // Help: the last digits are never a full number, a PIN or a code.
                PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits", singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", ::save, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = name.isNotBlank())
                if (data.accounts.isNotEmpty()) PixelButton("Cancel", { adding = false; message = null }, Modifier.weight(1f), Pixel.InkSoft)
            }
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
}

/** The colour of a card style in the current palette. Cosmetic: nothing reads it but the picker and the account list. */
@Composable
private fun cardColor(style: CardStyle): Color = when (style) {
    CardStyle.PLAIN -> Pixel.InkSoft
    CardStyle.BLUE -> Pixel.Blue
    CardStyle.GREEN -> Pixel.Green
    CardStyle.GOLD -> Pixel.Warning
    CardStyle.VIOLET -> if (Pixel.dark) Color(0xFFA98CF0) else Color(0xFF6E45C2)
    CardStyle.SLATE -> Pixel.Ink
}

/** The card-style picker: one key per style in its own colour. It changes how the account looks and nothing else. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStylePicker(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
    FieldLabel("Card style")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        CardStyle.entries.forEach { c -> TabButton(c.label, c.id == CardStyle.of(selected).id, { onPick(c.id) }, key = cardColor(c)) }
    }
    }
}

/** What each account type means, behind the "?" on the new-account form and written once (MINT.md 4.1, REP-056). */
private val accountTypeNotes = listOf(
    AccountType.BANK to "Money in a bank. The balance is what you have.",
    AccountType.EWALLET to "An app wallet such as GCash or Maya.",
    AccountType.DIGITAL_BANK to "A bank that lives in an app.",
    AccountType.CASH to "Notes and coins you carry or keep at home.",
    AccountType.CREDIT_CARD to "Money you owe. The balance is what you owe.",
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
    var asking by remember(a.id) { mutableStateOf(false) }
    var editing by remember(a.id) { mutableStateOf(startEditing) }
    var name by remember(a.id) { mutableStateOf(a.name) }
    var type by remember(a.id) { mutableStateOf(a.type) }
    var currency by remember(a.id) { mutableStateOf(a.currency) }
    var opening by remember(a.id) { mutableStateOf(Money.plain(a.openingMinor, a.currency)) }
    var institution by remember(a.id) { mutableStateOf(a.institution) }
    var group by remember(a.id) { mutableStateOf(a.group) }
    var suffix by remember(a.id) { mutableStateOf(a.suffix) }
    var card by remember(a.id) { mutableStateOf(a.card) }
    var refused by remember(a.id) { mutableStateOf<String?>(null) }
    var removingQr by remember(a.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (!editing && !asking && (model.images != null || a.receiveQr.isNotEmpty())) ReceiveMoney(model, a, removingQr, { removingQr = it }, { refused = it })
        if (editing) {
            val locked = mint.hasHistory(a.id)
            val minor = Money.parse(opening.ifBlank { "0" }, currency)
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                AccountType.entries.filter { it.liability == a.type.liability }.forEach { t -> Chip(t.label, t == type, { type = t }) }
            }
            // Help: the currency stays once the account has records.
            if (!locked) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Money.common.take(6).forEach { c -> Chip(c, c == currency, { if (c != currency) { opening = Money.parse(opening.ifBlank { "0" }, currency)?.let { m -> Money.plain(m, c) } ?: opening; currency = c } }) }
            }
            PixelField(
                opening, { opening = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(),
                label = if (type.liability) "Amount owed when you started tracking" else "Balance when you started tracking", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CardStylePicker(card) { card = it }
            PixelField(institution, { institution = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Bank or provider", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PixelField(group, { group = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Group", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            PixelField(suffix, { suffix = it.filter(Char::isLetterOrDigit).take(MintRules.SUFFIX_MAX) }, Modifier.fillMaxWidth(), label = "Last ${MintRules.SUFFIX_MAX} digits", singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", {
                    if (minor == null) refused = "Check the balance."
                    else mint.updateAccount(a.copy(name = name, type = type, currency = currency, openingMinor = minor, institution = institution, group = group, suffix = suffix, card = card))
                        .let { if (it.record != null) { editing = false; refused = null; onDone() } else refused = it.refused }
                }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = name.isNotBlank(), compact = true)
                PixelButton("Cancel", { editing = false; refused = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        } else if (asking) {
            Confirm("Delete this account? Only an account with no records can be deleted.", "Delete", onConfirm = { refused = mint.deleteAccount(a.id); asking = false; if (refused == null) onDone() }, onKeep = { asking = false })
        } else Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            // Two rows of two, so the longest label ("Archive") is never clipped at a large font.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                if (a.status == AccountStatus.ACTIVE) PixelButton("Archive", { mint.setAccountStatus(a.id, AccountStatus.ARCHIVED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                else PixelButton("Reopen", { mint.setAccountStatus(a.id, AccountStatus.ACTIVE); onDone() }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                if (a.status == AccountStatus.ACTIVE) PixelButton("Close", { mint.setAccountStatus(a.id, AccountStatus.CLOSED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

/** "Receive money": the account's own QR code (a picture the user chose), shown large so another phone can scan it. Stays on this phone like the rest of Mint. */
@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun ReceiveMoney(model: AppModel, a: FinancialAccount, removing: Boolean, setRemoving: (Boolean) -> Unit, problem: (String?) -> Unit) {
    val mint = model.mint
    val chooser = model.images
    val hide = model.mintData.settings.hideValues
    val bitmap = remember(a.receiveQr) { if (a.receiveQr.isEmpty()) null else runCatching { Base64.decode(a.receiveQr).decodeToImageBitmap() }.getOrNull() }
    var full by remember { mutableStateOf(false) }
    if (full && !hide && bitmap != null) {
        val viewer = model.imageViewer
        DisposableEffect(Unit) {
            viewer?.bright(true)
            onDispose { viewer?.bright(false) }
        }
        Dialog({ full = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            BackHandler(true) { full = false }
            QrFullScreen(a.name, bitmap, { full = false }, if (viewer == null) null else ({
                problem(runCatching { viewer.share(Base64.decode(a.receiveQr), "receive-qr.png") }.getOrDefault("That picture could not be shared."))
            }))
        }
    }
    fun choose() {
        chooser?.choose { png, why ->
            if (png == null) { problem(why); return@choose }
            val out = mint.setReceiveQr(a.id, Base64.encode(png))
            problem(if (out.record == null) (out.refused ?: "That picture could not be saved.") else null)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelTitle("Receive money", Modifier.fillMaxWidth())
            if (a.receiveQr.isEmpty()) {
                PixelButton("Add QR code", { choose() }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            } else {
                val desc = if (hide) "Receive money QR code for ${a.name}, hidden" else "Receive money QR code for ${a.name}"
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (hide) Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(PixelShape).background(Pixel.Border).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
                        Text("Hidden", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
                    }
                    else if (bitmap != null) Image(
                        bitmap, desc,
                        Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .clickable(role = Role.Button, onClickLabel = "Open receive money QR code full screen") { full = true },
                        contentScale = ContentScale.Fit, filterQuality = qrFilter(bitmap),
                    )
                    else Text("Could not show it", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (removing) Confirm("Remove this QR code?", "Remove", onConfirm = { mint.setReceiveQr(a.id, ""); setRemoving(false) }, onKeep = { setRemoving(false) })
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    if (!hide && bitmap != null) PixelButton("Open", { full = true }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                    if (chooser != null) PixelButton("Replace", { choose() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Remove", { setRemoving(true) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

/** A bare, roughly square QR is upscaled, so keep its modules crisp; a tall wallet screenshot is shrunk, so smooth it. */
private fun qrFilter(b: androidx.compose.ui.graphics.ImageBitmap) =
    if (b.width.toFloat() / b.height in 0.9f..1.1f) FilterQuality.None else FilterQuality.Medium

/** The receive-money image (a bare QR or a whole wallet screenshot) on a plain white page, fitted at its own aspect ratio and as large as fits, so another phone can scan it. Close and (when the platform can) Share sit at the bottom. */
@Composable
internal fun QrFullScreen(name: String, bitmap: androidx.compose.ui.graphics.ImageBitmap, onClose: () -> Unit, onShare: (() -> Unit)?) {
    // The whole image, edge to edge at its own ratio: fitted when it fits the screen, otherwise full width and scrollable (never shrunk).
    // The Close/Share bar sits below the image area, so nothing covers the image.
    Column(Modifier.fillMaxSize().background(Color.White)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val ratio = bitmap.width.toFloat() / bitmap.height
            val fits = maxWidth / ratio <= maxHeight
            val desc = "Receive money QR code for $name"
            if (fits) Image(bitmap, desc, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = qrFilter(bitmap))
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Image(bitmap, desc, Modifier.fillMaxWidth().aspectRatio(ratio), contentScale = ContentScale.FillWidth, filterQuality = qrFilter(bitmap))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Pixel.Paper).padding(horizontal = Spacing.M, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelButton("Close", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            if (onShare != null) PixelButton("Share", onShare, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
        }
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
    var allCategories by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
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
        EmptyState("Add an account first", icon = "member-card", action = "Add account" to { model.mintSection = MintSection.ACCOUNTS })
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
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                quickTypes.forEach { t -> Chip(t.label, t == type, { type = t; categoryId = null; allCategories = false }) }
            }
            SectionLabel(if (type == TxType.TRANSFER) "From" else "Account")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                open.forEach { a -> Chip(a.name, a.id == from?.id, { accountId = a.id; if (toId == a.id) toId = null }) }
            }
            if (type == TxType.TRANSFER) {
                SectionLabel("To")
                if (targets.isEmpty()) Text("Add a second account in the same currency.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: result
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    targets.forEach { a -> Chip(a.name, a.id == to?.id, { toId = a.id }) }
                }
            } else if (type == TxType.ADJUSTMENT) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Balance goes up", up, { up = true }, Modifier.weight(1f)); Chip("Balance goes down", !up, { up = false }, Modifier.weight(1f))
                }
            } else {
                val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val usable = data.categories.filter { it.kind == kind && !it.archived }
                // The few used last come first; "More" opens the whole list.
                val recent = data.transactions.sortedByDescending { it.atMillis }.mapNotNull { t -> usable.firstOrNull { it.id == t.categoryId } }.distinct()
                val top = (recent + usable).distinct().take(4).let { t -> usable.firstOrNull { it.id == categoryId }?.let { c -> if (c in t) t else t + c } ?: t }
                val shownCategories = if (allCategories) usable else top
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    shownCategories.forEach { c -> Chip(c.name, c.id == categoryId, { categoryId = if (categoryId == c.id) null else c.id }) }
                    if (usable.size > top.size || allCategories) Chip(if (allCategories) "Fewer" else "More", false, { allCategories = !allCategories })
                    if (allCategories) Chip(if (addingCategory) "Cancel" else "New", addingCategory, { addingCategory = !addingCategory })
                }
                if (addingCategory) NewCategoryRow(mint, kind) { categoryId = it.id; addingCategory = false }
            }
            PixelMomentField(payAt ?: model.store.nowMillis, { payAt = it }, model.store.nowMillis, ModuleAccent.MINT.accent, label = "When")
            PixelButton(if (more) "Fewer options" else "Payee and a note", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                PixelField(payee, { payee = it.take(MintRules.NAME_MAX * 2) }, Modifier.fillMaxWidth(), label = "Payee or source", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                PixelField(note, { note = it.take(MintRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = amount.isNotBlank())
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
    val found = remember(data, query, filter) { MintRules.search(data, query, filter) }
    if (data.transactions.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(query, { query = it.take(40); limit = PAGE }, Modifier.weight(1f), placeholder = "Search", singleLine = true)
                PixelButton(filter?.label ?: "Filter", { showFilter = !showFilter }, color = Pixel.InkSoft, compact = true)
            }
            if (showFilter) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("All", filter == null, { filter = null })
                quickTypes.forEach { t -> Chip(t.label, filter == t, { filter = if (filter == t) null else t }) }
            }
            if (found.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Column {
                found.take(limit).forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    TransactionRow(model, t, openId == t.id) { openId = if (openId == t.id) null else t.id }
                    if (openId == t.id) TransactionOptions(model, t) { openId = null }
                }
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
    MintLine(
        trail = { Text(t.signed() + model.mint.show(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall) },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 3)
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
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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
    var newPeriod by remember { mutableStateOf(BudgetPeriod.MONTHLY) }
    var editMessage by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
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
                    if (openId == b.id) {
                        PixelField(newAmount, { newAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            BudgetPeriod.entries.forEach { per -> Chip(per.label, per == newPeriod, { newPeriod = per }, Modifier.weight(1f)) }
                        }
                        editMessage?.let { Report(model, it, NoticeTone.WARNING) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Save", {
                                val minor = Money.parse(newAmount, b.currency)
                                val out = minor?.let { mint.updateBudget(b.id, it, newPeriod) }
                                editMessage = if (minor == null) "Check the amount." else out?.refused
                                if (out?.record != null) openId = null
                            }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = Money.parse(newAmount, b.currency) != null, compact = true)
                            PixelButton(if (b.status == PlanStatus.PAUSED) "Resume" else "Pause", { mint.setBudgetStatus(b.id, if (b.status == PlanStatus.PAUSED) PlanStatus.ACTIVE else PlanStatus.PAUSED); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { val before = model.mintData; mint.deleteBudget(b.id); openId = null; model.offerUndo("Deleted: budget") { mint.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
    if (!adding) {
        PixelButton("New budget", { adding = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
        return
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
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Planned amount", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            PixelButton("Save", {
                val minor = Money.parse(amount, currency)
                val out = minor?.let { mint.addBudget(it, currency, period, categoryId) }
                message = if (minor == null) "Check the amount." else out?.refused
                if (out?.record != null) { amount = ""; adding = false; model.haptic() }
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
    var showSettled by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var incomeNew by remember { mutableStateOf(false) }
    var incomeCategory by remember { mutableStateOf<String?>(null) }
    // "Paid, record it": the occurrence whose small account picker is open, and the account chosen (the bill's own until another is tapped).
    var recordKey by remember { mutableStateOf<String?>(null) }
    var recordAccount by remember { mutableStateOf<String?>(null) }
    // Settling takes the occurrence off the list, so every settlement offers an undo.
    fun settle(o: Occurrence, status: SettleStatus, paid: Long, label: String) {
        val before = model.mintData
        if (mint.settle(o.bill.id, o.dueDay, status, paid, paidDay = paidOn ?: today)) { paidOn = null; openKey = null; model.offerUndo("$label: ${o.bill.name}") { mint.restore(before) } }
    }
    val currency = data.settings.currency
    // Only the next unsettled date of each bill.
    val upcoming = MintRules.upcoming(data, today, 45).distinctBy { it.bill.id }
    if (upcoming.isNotEmpty()) TextBox {
        Column {
            upcoming.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val key = o.bill.id + o.dueDay
                OccurrenceRow(model, o, openKey == key) { openKey = if (openKey == key) null else key; partial = o.payment?.paidMinor?.let { Money.plain(it, o.bill.currency) }.orEmpty(); paidOn = null }
                if (openKey == key) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    val full = o.remainingMinor ?: o.bill.expectedMinor
                    val inc = o.bill.income
                    if (full == null) PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Amount received" else "Amount paid", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    else PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Received so far" else "Paid so far", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    SectionLabel(if (inc) "Received on" else "Settled on")
                    PixelDateField(paidOn ?: today, today, ModuleAccent.MINT.accent, { paidOn = it }, presets = listOf("Today" to 0, "Yesterday" to -1), max = today)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton(if (inc) "Received" else "Paid", {
                            val paid = if (full == null) Money.parse(partial, o.bill.currency) else o.bill.expectedMinor
                            if (paid != null) { settle(o, SettleStatus.PAID, paid, if (inc) "Received" else "Paid"); model.effect(Sfx.CANDY) }
                        }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = full != null || Money.parse(partial, o.bill.currency) != null, compact = true)
                        if (full != null) PixelButton(if (inc) "Part received" else "Part", { Money.parse(partial, o.bill.currency)?.let { settle(o, SettleStatus.PARTIAL, it, if (inc) "Part received" else "Part paid") } }, Modifier.weight(1f), Pixel.InkSoft, enabled = Money.parse(partial, o.bill.currency) != null, compact = true)
                        PixelButton("Skip", { settle(o, SettleStatus.SKIPPED, 0, "Skipped") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        if (o.payment != null) PixelButton("Undo", { mint.unsettle(o.bill.id, o.dueDay); openKey = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                    // Marking paid makes no transaction by itself; this path also writes the expense, linked to the bill, on the account chosen here.
                    val accounts = data.accounts.filter { it.status == AccountStatus.ACTIVE && it.currency == o.bill.currency }
                    val amountToRecord = if (full == null) Money.parse(partial, o.bill.currency) else o.bill.expectedMinor
                    if (accounts.isNotEmpty()) {
                        if (recordKey != key) PixelButton(if (inc) "Received, record it" else "Paid, record it", { recordKey = key; recordAccount = o.bill.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.first().id }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = amountToRecord != null, compact = true)
                        else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                accounts.forEach { a -> Chip(a.name, a.id == recordAccount, { recordAccount = a.id }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                PixelButton("Record", {
                                    val before = model.mintData
                                    val out = amountToRecord?.let { mint.settleAndRecord(o.bill.id, o.dueDay, it, recordAccount, paidOn ?: today) }
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
    // Paid and skipped occurrences leave the list above; they stay here for a while, so a wrong amount or a slip can be settled again or taken back.
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
                    PixelField(partial, { partial = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (inc) "Amount received" else "Amount paid", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    SectionLabel(if (inc) "Received on" else "Settled on")
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
    } else TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle(if (incomeNew) "New income" else "New bill")
            // Direction: something to pay, or money expected (a salary, an allowance); the schedule works the same.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Chip("Bill to pay", !incomeNew, { incomeNew = false; incomeCategory = null }, Modifier.weight(1f))
                Chip("Income expected", incomeNew, { incomeNew = true }, Modifier.weight(1f))
            }
            PixelField(name, { name = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            PixelField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = if (incomeNew) "Expected amount" else "Amount", placeholder = "Varies", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (incomeNew) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                data.categories.filter { it.kind == CategoryKind.INCOME && !it.archived }.forEach { c -> Chip(c.name, c.id == incomeCategory, { incomeCategory = if (incomeCategory == c.id) null else c.id }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Frequency.entries.forEach { f -> Chip(f.label, f == frequency, { frequency = f }) }
            }
            SectionLabel("First due")
            PixelDateField(firstDue ?: today, today, ModuleAccent.MINT.accent, { firstDue = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
            PixelButton("Save", {
                val minor = if (amount.isBlank()) null else Money.parse(amount, currency)
                if (amount.isNotBlank() && minor == null) message = "Check the amount."
                else {
                    val out = mint.addBill(name, currency, frequency, firstDue ?: today, minor, categoryId = incomeCategory.takeIf { incomeNew }, income = incomeNew)
                    message = out.refused
                    if (out.record != null) { name = ""; amount = ""; firstDue = null; incomeCategory = null; adding = false; model.haptic() }
                }
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = name.isNotBlank())
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
    if (data.bills.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton(if (manage) "Hide bills" else "All bills", { manage = !manage }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (manage) data.bills.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                MintLine(
                    trail = { Text(b.expectedMinor?.let { mint.show(it, b.currency) } ?: "varies", style = MaterialTheme.typography.titleSmall) },
                    onClick = { editId = if (editId == b.id) null else b.id; editName = b.name; editAmount = b.expectedMinor?.let { Money.plain(it, b.currency) }.orEmpty(); editFirst = b.firstDue; editFrequency = b.frequency }, onClickLabel = "Edit",
                ) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${if (b.income) "income · " else ""}${b.frequency.label.lowercase()}${if (b.status != PlanStatus.ACTIVE) " · ${b.status.name.lowercase()}" else ""}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (editId == b.id) {
                    // The name and the amount can change (rent goes up). The first due day and how often can change until a payment is
                    // recorded against the bill, so no settled occurrence is ever rewritten.
                    val scheduleOpen = data.billPayments.none { it.billId == b.id }
                    val minor = if (editAmount.isBlank()) null else Money.parse(editAmount, b.currency)
                    PixelField(editName, { editName = it.take(MintRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    PixelField(editAmount, { editAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = "Amount", placeholder = "Varies", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (scheduleOpen) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Frequency.entries.forEach { f -> Chip(f.label, f == editFrequency, { editFrequency = f }) }
                        }
                        SectionLabel("First due")
                        PixelDateField(editFirst ?: b.firstDue, today, ModuleAccent.MINT.accent, { editFirst = it }, presets = listOf("Today" to 0, "In a week" to 7, "In a month" to 30))
                    }
                    // Help: the schedule is fixed once payments are recorded; to change it, pause this bill and add a new one.
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
private fun OccurrenceRow(model: AppModel, o: Occurrence, open: Boolean, paid: Boolean = false, onClick: () -> Unit) {
    // A settled occurrence shows what was paid; one still to settle shows what is left.
    val amount = if (paid) o.payment?.paidMinor?.takeIf { o.state != OccurrenceState.SKIPPED } else o.remainingMinor ?: o.bill.expectedMinor
    val today = model.store.today
    MintLine(
        trail = {
            // A bill still to settle shows when it is due at the right; a settled one shows what was paid.
            Text(
                if (paid) amount?.let { (if (o.bill.income) "+" else "") + model.mint.show(it, o.bill.currency) }.orEmpty() else if (o.dueDay == today) "Today" else if (o.state == OccurrenceState.OVERDUE) "Overdue" else o.dueDay.short(),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    ) {
        Text(o.bill.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        val stateLabel = if (o.bill.income && o.state == OccurrenceState.PAID) "Received" else o.state.label
        val line = if (paid) "$stateLabel · ${o.dueDay.short()}" else amount?.let { (if (o.bill.income) "+" else "") + model.mint.show(it, o.bill.currency) } ?: "varies"
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
            if (data.transactions.none { it.day >= from && it.day < end && it.currency == currency }) Text("Nothing recorded in ${Money.label(currency)} yet.", style = MaterialTheme.typography.bodyLarge)
            else {
                MintValueRow("Money in", mint.show(earned, currency))
                MintValueRow("Spent", mint.show(spent.coerceAtLeast(0), currency))
                MintValueRow("Difference", mint.show(earned - spent, currency))
                // Help: transfers between your accounts are left out; pending and excluded records are too.
                MintRules.spendingByCategory(data, from, end, currency).take(6).forEach { (id, minor) ->
                    MintValueRow(data.categories.firstOrNull { it.id == id }?.name ?: "No category", mint.show(minor, currency), soft = true)
                }
            }
        }
    }
    ReconcileCard(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Weekly review")
            val first = model.data.settings.weekStart
            val weekDone = data.reviews.any { MintRules.weekStartOf(it.day, first) == MintRules.weekStartOf(today, first) }
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
            MintValueRow("In Mint", mint.show(book, account.currency))
            PixelField(
                statement, { statement = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16); difference = null }, Modifier.fillMaxWidth(),
                label = "Your statement says", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val parsed = Money.parse(statement, account.currency)
            PixelButton("Compare", {
                if (parsed == null) { message = "Check the amount." to NoticeTone.WARNING; return@PixelButton }
                val diff = MintRules.reconcileDifference(parsed, book)
                if (diff == 0L) { mint.reconcile(account.id, parsed); model.effect(Sfx.ITEM_GET); message = null; model.say("They match. Saved."); statement = ""; difference = null }
                else { difference = diff; message = null }
            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = parsed != null)
            difference?.let { diff ->
                Text("The statement is ${mint.show(kotlin.math.abs(diff), account.currency)} ${if (diff > 0) "higher" else "lower"} than Mint.", style = MaterialTheme.typography.bodyLarge)
                // Help: accepting records the difference as a balance adjustment; nothing else changes.
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
    // Help: Ledger Research can find a Ledger Egg from reconciling an account or finishing the weekly review; spending, income and balances never earn anything.
    // Help: bill reminders are off by default, one per due bill, never show an amount and never mark a bill paid; quiet hours and focus sessions hold them back.
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            ToggleRow("Ledger Research", settings.progression) { mint.setProgression(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            ToggleRow("Bill reminders", settings.billReminders) { mint.setBillReminders(it) }
            if (settings.billReminders) {
                SectionLabel("How early")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(0 to "On the day", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 7 to "A week before").forEach { (d, label) -> Chip(label, d == settings.billReminderDays, { mint.setBillReminderDays(d) }) }
                }
                SectionLabel("At")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(8, 9, 12, 18, 20).forEach { h -> Chip("${h.toString().padStart(2, '0')}:00", h == settings.billReminderHour, { mint.setBillReminderHour(h) }) }
                }
                // Help: with "Name the bill" on, the notification names the bill after the phone is unlocked; the lock screen only says a bill is coming up.
                ToggleRow("Name the bill", settings.billReminderNames) { mint.setBillReminderNames(it) }
            }
        }
    }
    CategoriesBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Privacy")
            ToggleRow("Hide amounts", settings.hideValues) { mint.setHideValues(it) }
            model.gate?.let { gate ->
                if (gate.available() || settings.gate) ToggleRow("Ask for my screen lock", settings.gate) { on ->
                    if (!on) mint.setGate(false)
                    else gate.authenticate { ok -> if (ok) { mint.setGate(true); model.moduleUnlockedAt[Module.MINT.name] = model.store.nowMillis } }
                }
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
            // Help: exports are files you can read (amounts are whole minor units, like centavos, with their currency); erasing Mint never touches Pokémon, Poké, the Bag or other modules.
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-mint.json", mint.export()) }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MintKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-mint-${k.name.lowercase()}.json", mint.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            }
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
}

/** A name field and an Add key for a new category of [kind]; [onAdded] gets it (the entry form selects it). */
@Composable
private fun NewCategoryRow(mint: app.pokeplan.app.mint.MintStore, kind: CategoryKind, onAdded: (app.pokeplan.mint.Category) -> Unit) {
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
    var openId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Categories")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("Spending", kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, Modifier.weight(1f))
                Chip("Income", kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, Modifier.weight(1f))
            }
            Column {
                data.categories.filter { it.kind == kind }.sortedBy { it.archived }.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    // A category is a row; a tap opens its actions below it.
                    MintLine(onClick = { openId = if (openId == c.id) null else c.id; editing = null; deleting = null }, onClickLabel = "Show options") {
                        Text(if (c.archived) "${c.name} (put away)" else c.name, style = MaterialTheme.typography.bodyLarge, color = if (c.archived) Pixel.InkSoft else Pixel.Ink)
                    }
                    if (openId == c.id) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (editing == c.id) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                PixelField(draft, { draft = it.take(MintRules.NAME_MAX) }, Modifier.weight(1f), singleLine = true)
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
