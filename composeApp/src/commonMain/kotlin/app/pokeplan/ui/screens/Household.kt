package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Tutorials
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskState
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.home.Asset
import app.pokeplan.home.Chore
import app.pokeplan.home.ChoreState
import app.pokeplan.home.ChoreView
import app.pokeplan.home.DueState
import app.pokeplan.home.EntryStatus
import app.pokeplan.home.HitKind
import app.pokeplan.home.HomeArea
import app.pokeplan.home.HomeKinds
import app.pokeplan.home.HomeRules
import app.pokeplan.home.MaintenanceSchedule
import app.pokeplan.home.Quantity
import app.pokeplan.home.RecordStatus
import app.pokeplan.home.Recurrence
import app.pokeplan.home.Reference
import app.pokeplan.home.RepeatKind
import app.pokeplan.home.RestockLine
import app.pokeplan.home.SchedulePolicy
import app.pokeplan.home.Supply
import app.pokeplan.home.Warranty
import app.pokeplan.home.WarrantyState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.focusRing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The household module's screens (docs/pokelife `MODULES/HOME.md`): chores, supplies, maintenance with the belongings it is done on, reference
 * notes, and settings. Utility presentation (HOME.md section 11): a few panels, plain rows with a 48 dp target, no box per record. Nothing here
 * judges: overdue is a word, never a failure, and no screen shows money.
 */
enum class HouseholdSection(val label: String, val icon: String) {
    CHORES("Chores", "cracked-pot"), SUPPLIES("Supplies", "seal-bag"), MAINTENANCE("Maintenance", "iron"), NOTES("Notes", "oaks-letter"),
    SETTINGS("Settings", "storage-key"),
}

private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.HOME.accent

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val wordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

@Composable
private fun Confirm(text: String, confirm: String, keep: String = "Keep it", onConfirm: () -> Unit, onKeep: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(confirm, onConfirm, Modifier.weight(1f), Pixel.Danger, compact = true)
        PixelButton(keep, onKeep, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}

/** A plain list row: a lead, a title with one quiet line under it, a trail, all on one centre line, with a 48 dp target. No box of its own. */
@Composable
private fun HRow(
    title: String, detail: String = "", warn: Boolean = false, lead: @Composable () -> Unit = {}, trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null, onClickLabel: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target)
            .let { if (onClick != null) it.clip(PixelShape).focusRing(PixelShape).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (warn) Pixel.Warning else Pixel.InkSoft)
        }
        trail()
    }
}

@Composable
private fun Rule() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(Pixel.Border.copy(alpha = 0.6f)))
}

@Composable
private fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, l -> Chip(l, i == selected, { onSelect(i) }, Modifier.weight(1f)) }
    }
}

// ----- Moving between sections ------------------------------------------------------------------------------------------------

private class Jump(val section: HouseholdSection, val id: String? = null, val tab: Int? = null)

/** Where the next section should open (a search hit, an overview line); read once when the section appears. */
private var pendingJump: Jump? = null

private fun go(model: AppModel, section: HouseholdSection, id: String? = null, tab: Int? = null) {
    pendingJump = Jump(section, id, tab)
    model.homeSection = section
}

private fun takeJump(section: HouseholdSection): Jump? = pendingJump?.takeIf { it.section == section }?.also { pendingJump = null }

/** The line under the household's card in Modules: how much waits today, or what the module is for. Never a private word of the household. */
internal fun householdSubtitle(model: AppModel): String {
    val n = HomeRules.overview(model.homeData, model.store.today).chores.size
    return if (n > 0) "$n to do today" else "chores, supplies, upkeep"
}

@Composable
internal fun HouseholdScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenSwitch(model.homeSection, depth = { if (it == null) 0 else 1 }) { section -> HouseholdPage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HouseholdPage(model: AppModel, section: HouseholdSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        HouseholdMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.homeSection = null }, icon = section.icon, color = Module.HOME.look.header) {
        when (section) {
            HouseholdSection.CHORES -> ChoresView(model)
            HouseholdSection.SUPPLIES -> SuppliesView(model)
            HouseholdSection.MAINTENANCE -> MaintenanceView(model)
            HouseholdSection.NOTES -> NotesView(model)
            HouseholdSection.SETTINGS -> HouseholdSettingsView(model)
        }
    }
}

// ----- Menu -------------------------------------------------------------------------------------------------------------------

@Composable
private fun HouseholdMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.homeData
    val today = model.store.today
    val tint = ModuleAccent.HOME.accent
    val overview = HomeRules.overview(data, today)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.HOME.label, onBack = onBack, color = Module.HOME.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.HOME, model.data.player.homeLead, model.store.moduleEvent(app.pokeplan.app.GameStore.HOME_LOG_PREFIX), openPokemon = openPokemon)
            RewardBox(model)
            TutorialCard(model, Tutorials.HOME_TIDY, homeRecords = data.chores.size + data.supplies.size + data.assets.size)
            HouseholdOverview(model)
            HouseholdSearch(model)
            MenuGrid(
                listOf(
                    MenuEntry(HouseholdSection.CHORES.name, HouseholdSection.CHORES.icon, "Chores", if (data.chores.isEmpty()) "add one" else "${overview.chores.size} to do today", tint = tint),
                    MenuEntry(HouseholdSection.SUPPLIES.name, HouseholdSection.SUPPLIES.icon, "Supplies", if (data.supplies.isEmpty()) "add one" else "${overview.low.size} low", tint = tint),
                    MenuEntry(HouseholdSection.MAINTENANCE.name, HouseholdSection.MAINTENANCE.icon, "Maintenance", if (data.schedules.isEmpty() && data.assets.isEmpty()) "add one" else "${overview.maintenance.size} due or soon", tint = tint),
                    MenuEntry(HouseholdSection.NOTES.name, HouseholdSection.NOTES.icon, "Notes", if (data.references.isEmpty()) "add one" else "${data.references.size} notes", tint = tint),
                    MenuEntry(HouseholdSection.SETTINGS.name, HouseholdSection.SETTINGS.icon, "Settings", "Habitat Discovery, areas, your data", tint = Pixel.InkSoft),
                ),
            ) { key -> model.homeSection = HouseholdSection.valueOf(key) }
            Text(
                if (data.settings.progression) "Habitat Discovery is on · Habitat Egg ${model.store.homeEggMeter()} of ${RewardEngine.HOME_EGG_METER_SIZE}" else "Habitat Discovery is off",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            HomePartyPanel(model, openPokemon)
        }
    }
}

// ----- Overview ---------------------------------------------------------------------------------------------------------------

/** Only what needs attention (HOME.md 3.1): chores due or overdue, maintenance due or soon, low supplies and warranties ending soon; never a count of everything. */
@Composable
private fun HouseholdOverview(model: AppModel) {
    val data = model.homeData
    val home = model.home
    val today = model.store.today
    val hidden = data.settings.hidden
    val o = HomeRules.overview(data, today)
    val chores = if ("chores" in hidden) emptyList() else o.chores
    val upkeep = if ("maintenance" in hidden) emptyList() else o.maintenance
    val warranties = if ("maintenance" in hidden) emptyList() else o.warranties
    val low = if ("supplies" in hidden) emptyList() else o.low
    val attention = chores.isNotEmpty() || upkeep.isNotEmpty() || warranties.isNotEmpty() || low.isNotEmpty()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (attention) "Needs a look" else if (data.chores.isEmpty() && data.supplies.isEmpty() && data.assets.isEmpty()) "Add your first chore" else "Nothing needs attention")
            if (chores.isNotEmpty()) {
                SectionLabel("Chores")
                chores.take(5).forEach { v ->
                    HRow(
                        v.chore.title, choreLine(data, v, today), warn = v.state == ChoreState.OVERDUE,
                        lead = { PixelIconButton("check", "Mark ${v.chore.title} done", { home.complete(v.chore.id); model.haptic() }, accent) },
                        onClick = { go(model, HouseholdSection.CHORES, v.chore.id, 0) }, onClickLabel = "Open chores",
                    )
                }
                if (chores.size > 5) PixelButton("All ${chores.size} chores", { go(model, HouseholdSection.CHORES, tab = 0) }, color = Pixel.InkSoft, compact = true)
            }
            if (upkeep.isNotEmpty()) {
                SectionLabel("Maintenance")
                upkeep.take(5).forEach { v ->
                    HRow(
                        v.schedule.title, scheduleLine(data, v), warn = v.state == DueState.OVERDUE,
                        lead = { PixelIconButton("check", "Mark ${v.schedule.title} done", { home.completeMaintenance(v.schedule.id); model.haptic() }, accent) },
                        onClick = { go(model, HouseholdSection.MAINTENANCE, v.schedule.id, 0) }, onClickLabel = "Open maintenance",
                    )
                }
            }
            if (low.isNotEmpty()) {
                SectionLabel("Running low")
                low.take(5).forEach { s ->
                    HRow(
                        s.name, "Low · ${quantityText(s)}",
                        trail = { PixelButton("Not now", { home.dismissLow(s.id) }, color = Pixel.InkSoft, compact = true) },
                        onClick = { go(model, HouseholdSection.SUPPLIES, s.id, 2) }, onClickLabel = "Open the restock list",
                    )
                }
            }
            if (warranties.isNotEmpty()) {
                SectionLabel("Warranties")
                warranties.take(3).forEach { w ->
                    val asset = data.assets.firstOrNull { it.id == w.assetId }
                    HRow(
                        asset?.name ?: "Warranty", listOfNotNull("Ends ${w.expiryDay?.short()}", w.provider.takeIf { it.isNotBlank() }).joinToString(" · "),
                        onClick = { go(model, HouseholdSection.MAINTENANCE, w.assetId, 1) }, onClickLabel = "Open belongings",
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Chore", { go(model, HouseholdSection.CHORES) }, Modifier.weight(1f), accent, compact = true)
                PixelButton("Stock", { go(model, HouseholdSection.SUPPLIES) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Item", { go(model, HouseholdSection.MAINTENANCE, tab = 1) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Note", { go(model, HouseholdSection.NOTES) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
private fun HouseholdSearch(model: AppModel) {
    val data = model.homeData
    if (data.chores.isEmpty() && data.supplies.isEmpty() && data.assets.isEmpty() && data.references.isEmpty() && data.schedules.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val hits = model.home.search(query).take(8)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Search the household", singleLine = true)
            if (query.isNotBlank() && hits.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            hits.forEach { h ->
                HRow(h.title, listOf(h.kind.label, h.detail.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), onClick = { openHit(model, h.kind, h.id); query = "" }, onClickLabel = "Open")
            }
        }
    }
}

private fun openHit(model: AppModel, kind: HitKind, id: String) {
    val data = model.homeData
    when (kind) {
        HitKind.CHORE -> go(model, HouseholdSection.CHORES, id, if (model.home.choreView(id)?.state.let { it == ChoreState.OVERDUE || it == ChoreState.DUE || it == ChoreState.ANYTIME }) 0 else 1)
        HitKind.SUPPLY -> go(model, HouseholdSection.SUPPLIES, id, 0)
        HitKind.ASSET -> go(model, HouseholdSection.MAINTENANCE, id, 1)
        HitKind.MAINTENANCE -> go(model, HouseholdSection.MAINTENANCE, id, 0)
        HitKind.WARRANTY -> go(model, HouseholdSection.MAINTENANCE, data.warranties.firstOrNull { it.id == id }?.assetId, 1)
        HitKind.REFERENCE -> go(model, HouseholdSection.NOTES, id)
    }
}

// ----- Shared words and fields ------------------------------------------------------------------------------------------------

private fun parseDay(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

private fun repeatText(r: Recurrence): String {
    if (r.kind == RepeatKind.ONCE) return ""
    val base = if (r.every <= 1) r.kind.label else "Every ${r.every} ${r.kind.unit}s"
    return if (r.kind == RepeatKind.WEEKLY && r.weekdays.isNotEmpty()) "$base on ${r.weekdays.sorted().joinToString(", ") { dayNames[it - 1] }}" else base
}

private fun areaName(data: app.pokeplan.home.HomeData, id: String?): String? = id?.let { a -> data.areas.firstOrNull { it.id == a }?.name }

private fun quantityText(s: Supply): String {
    // "1 bottles" reads wrong: one of a plural unit drops its s (a unit like "kg" or "L" has none to drop).
    val unit = if (s.quantityMilli == 1000L && s.unit.length > 3 && s.unit.endsWith("s")) s.unit.dropLast(1) else s.unit
    return Quantity.format(s.quantityMilli) + if (s.quantityMilli != null && unit.isNotEmpty()) " $unit" else ""
}

private fun choreLine(data: app.pokeplan.home.HomeData, v: ChoreView, today: LocalDate): String {
    val first = when (v.state) {
        ChoreState.OVERDUE -> "Overdue · ${v.dueDay?.short()}"
        ChoreState.DUE -> "Due today"
        ChoreState.UPCOMING -> if (v.entry?.status == EntryStatus.DEFERRED) "Put off · ${v.dueDay?.short()}" else v.dueDay?.short().orEmpty()
        ChoreState.ANYTIME -> "Whenever"
        ChoreState.DONE -> if (v.entry?.day == today) "Done today" else "Done · ${v.entry?.day?.short()}"
        ChoreState.SKIPPED -> if (v.entry?.day == today) "Skipped today" else "Skipped · ${v.entry?.day?.short()}"
        ChoreState.PAUSED -> "Paused"
        ChoreState.ARCHIVED -> "Archived"
    }
    val following = v.dueDay
    val next = if ((v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED) && v.chore.recurrence.kind != RepeatKind.ONCE && following != null && following != v.entry?.dueDay) "next ${following.short()}" else null
    return listOfNotNull(first.takeIf { it.isNotEmpty() }, next, areaName(data, v.chore.areaId), repeatText(v.chore.recurrence).takeIf { it.isNotEmpty() }, v.chore.minutes?.let { "$it min" }).joinToString(" · ")
}

private fun scheduleLine(data: app.pokeplan.home.HomeData, v: app.pokeplan.home.ScheduleView): String {
    val s = v.schedule
    val when_ = if (v.state == DueState.PAUSED) "Paused · ${s.nextDue.short()}" else if (v.state == DueState.DUE) "Due today" else "${v.state.label} · ${s.nextDue.short()}"
    return listOfNotNull(when_, data.assets.firstOrNull { it.id == s.assetId }?.name, repeatText(s.recurrence).takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/** A date for a chore, an asset or a warranty: quick keys and a month to tap, never typed (design review, 11 November 2026); [past] limits it to today and before. */
@Composable
private fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, today: LocalDate, optional: Boolean = false, past: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelDateField(
            value, today, accent, onChange,
            presets = if (past) listOf("Today" to 0, "Yesterday" to -1, "A week ago" to -7) else listOf("Today" to 0, "Tomorrow" to 1, "In a week" to 7),
            noneLabel = if (optional) "None" else null, max = if (past) today else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatEditor(r: Recurrence, onChange: (Recurrence) -> Unit) {
    var every by remember { mutableStateOf(r.every.toString()) }
    LaunchedEffect(r.every) { if ((every.toIntOrNull() ?: 1) != r.every) every = r.every.toString() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        RepeatKind.entries.forEach { k -> Chip(k.label, k == r.kind, { onChange(r.copy(kind = k, weekdays = if (k == RepeatKind.WEEKLY) r.weekdays else emptyList())) }) }
    }
    if (r.kind != RepeatKind.ONCE) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Text("Every", style = MaterialTheme.typography.bodyLarge)
            PixelField(every, { t -> every = t.filter(Char::isDigit).take(2); onChange(r.copy(every = (every.toIntOrNull() ?: 1).coerceIn(1, HomeRules.EVERY_MAX))) }, Modifier.width(88.dp), singleLine = true, keyboardOptions = numberKeys)
            Text(r.kind.unit + if (r.every == 1) "" else "s", style = MaterialTheme.typography.bodyLarge)
        }
        if (r.kind == RepeatKind.WEEKLY) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            (1..7).forEach { d -> Chip(dayNames[d - 1], d in r.weekdays, { onChange(r.copy(weekdays = if (d in r.weekdays) r.weekdays - d else (r.weekdays + d).sorted())) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaChips(areas: List<HomeArea>, selected: String?, onPick: (String?) -> Unit) {
    val shown = areas.filter { !it.archived || it.id == selected }
    if (shown.isEmpty()) return
    SectionLabel("Area")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Chip("None", selected == null, { onPick(null) })
        shown.forEach { a -> Chip(a.name, a.id == selected, { onPick(a.id) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetChips(assets: List<Asset>, selected: String?, onPick: (String?) -> Unit) {
    val shown = assets.filter { !it.archived || it.id == selected }
    if (shown.isEmpty()) return
    SectionLabel("Item")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Chip("None", selected == null, { onPick(null) })
        shown.forEach { a -> Chip(a.name, a.id == selected, { onPick(a.id) }) }
    }
}

/** A compact row of the three or four buttons that act on one record. */
@Composable
private fun Actions(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically, content = content)
}

// ----- Chores -----------------------------------------------------------------------------------------------------------------

private val openStates = setOf(ChoreState.OVERDUE, ChoreState.DUE, ChoreState.ANYTIME)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoresView(model: AppModel) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    val jump = remember { takeJump(HouseholdSection.CHORES) }
    val views = HomeRules.views(data, today)
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var area by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf(jump?.id) }
    var showArchived by remember { mutableStateOf(false) }
    val shown = views.filter { area == null || it.chore.areaId == area }
    val due = shown.filter { it.state in openStates || ((it.state == ChoreState.DONE || it.state == ChoreState.SKIPPED) && it.entry?.day == today) }
    val upcoming = shown.filter { v -> v !in due && !((v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED) && v.chore.recurrence.kind == RepeatKind.ONCE) }
    val archived = data.chores.filter { it.status == RecordStatus.ARCHIVED }
    RewardBox(model)
    TextBox { ChoreForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Tabs(listOf("Due", "Upcoming", "History"), tab) { tab = it; openId = null }
            val usedAreas = data.areas.filter { a -> !a.archived && data.chores.any { it.areaId == a.id } }
            if (usedAreas.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("All areas", area == null, { area = null })
                usedAreas.forEach { a -> Chip(a.name, area == a.id, { area = if (area == a.id) null else a.id }) }
            }
            when (tab) {
                0, 1 -> {
                    val rows = if (tab == 0) due else upcoming
                    if (rows.isEmpty()) PixelTitle(if (data.chores.isEmpty()) "No chores yet" else if (tab == 0) "Nothing due" else "Nothing coming up")
                    rows.forEachIndexed { i, v ->
                        if (i > 0) Rule()
                        ChoreRow(model, v, today, openId == v.chore.id) { openId = if (openId == v.chore.id) null else v.chore.id }
                        if (openId == v.chore.id) ChoreOptions(model, v, today) { openId = null }
                    }
                }
                else -> {
                    val history = HomeRules.history(data).take(60)
                    if (history.isEmpty()) PixelTitle("Nothing here yet")
                    history.forEachIndexed { i, e ->
                        if (i > 0) Rule()
                        val title = data.chores.firstOrNull { it.id == e.choreId }?.title ?: "Chore"
                        val word = when (e.status) {
                            EntryStatus.DONE -> "Done"
                            EntryStatus.SKIPPED -> "Skipped"
                            EntryStatus.DEFERRED -> "Put off" + (e.deferTo?.let { " to ${it.short()}" } ?: "")
                        }
                        HRow(title, listOfNotNull("$word · ${e.day.short()}", e.note.takeIf { it.isNotBlank() }).joinToString(" · "))
                    }
                    if (archived.isNotEmpty()) {
                        PixelButton(if (showArchived) "Hide archived" else "Archived (${archived.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        if (showArchived) archived.forEach { c ->
                            HRow(c.title, "Archived", trail = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    PixelButton("Restore", { home.setChoreStatus(c.id, RecordStatus.ACTIVE) }, color = accent, compact = true)
                                    PixelIconButton("trash", "Delete ${c.title}", { val before = model.homeData; home.deleteChore(c.id); model.offerUndo("Deleted: ${c.title}") { home.restore(before) } }, Pixel.InkSoft)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoreRow(model: AppModel, v: ChoreView, today: LocalDate, open: Boolean, onClick: () -> Unit) {
    val home = model.home
    val finished = v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED
    HRow(
        v.chore.title, choreLine(model.homeData, v, today), warn = v.state == ChoreState.OVERDUE,
        lead = { if (!finished && v.state != ChoreState.PAUSED) PixelIconButton("check", "Mark ${v.chore.title} done", { home.complete(v.chore.id); model.haptic() }, accent) },
        trail = {
            val entry = v.entry
            if (finished && entry != null) PixelButton("Undo", { home.undo(entry.id) }, color = Pixel.InkSoft, compact = true)
            else if (v.state == ChoreState.PAUSED) PixelButton("Resume", { home.setChoreStatus(v.chore.id, RecordStatus.ACTIVE) }, color = Pixel.InkSoft, compact = true)
        },
        onClick = onClick, onClickLabel = if (open) "Hide options" else "Show options",
    )
}

@Composable
private fun ChoreOptions(model: AppModel, v: ChoreView, today: LocalDate, onDone: () -> Unit) {
    val home = model.home
    var editing by remember(v.chore.id) { mutableStateOf(false) }
    var asking by remember(v.chore.id) { mutableStateOf(false) }
    val id = v.chore.id
    val waiting = v.state != ChoreState.DONE && v.state != ChoreState.SKIPPED && v.state != ChoreState.PAUSED
    if (editing) { ChoreForm(model, v.chore, onDone = { editing = false; onDone() }, onCancel = { editing = false }); return }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this chore and what was done about it? Archiving keeps the history.", "Delete", onConfirm = { home.deleteChore(id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        if (waiting) {
            SectionLabel("Put off")
            Actions {
                listOf(1 to "Tomorrow", 3 to "In 3 days", 7 to "Next week").forEach { (d, l) ->
                    PixelButton(l, { home.defer(id, today.plus(d, DateTimeUnit.DAY)); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
        Actions {
            if (waiting) PixelButton("Skip", { home.skip(id); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            if (v.state == ChoreState.PAUSED) PixelButton("Resume", { home.setChoreStatus(id, RecordStatus.ACTIVE); onDone() }, Modifier.weight(1f), accent, compact = true)
            else PixelButton("Pause", { home.setChoreStatus(id, RecordStatus.PAUSED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        // Making the responsibility visible in Plan is the user's choice and never moves the chore: Home still completes and pays it (HOME.md 2.1).
        val linked = model.data.tasks.any { it.link?.kind == LinkKind.HOME_CHORE && it.link?.ref == id && it.state != TaskState.DELETED }
        if (linked || (waiting && v.chore.status == RecordStatus.ACTIVE)) Actions {
            if (linked) PixelButton("Remove from Plan", { model.store.data.tasks.firstOrNull { it.link?.ref == id && it.state != TaskState.DELETED }?.let { t -> model.store.unlinkTask(t.id) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            else PixelButton("Show in Plan", { model.store.linkChore(id) }, Modifier.weight(1f), accent, compact = true)
        }
        if (model.homeData.settings.reminders && v.chore.status == RecordStatus.ACTIVE) Actions {
            PixelButton(if (v.chore.remind) "Reminder on" else "Remind me", { home.setChoreRemind(id, !v.chore.remind) }, Modifier.weight(1f), if (v.chore.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            PixelButton("Archive", { home.setChoreStatus(id, RecordStatus.ARCHIVED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

/** Adds a chore (title and an Add key; the rest behind a toggle) or, with [initial], edits one. */
@Composable
private fun ChoreForm(model: AppModel, initial: Chore?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    val adding = initial == null
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var repeat by remember { mutableStateOf(initial?.recurrence ?: Recurrence()) }
    var first by remember { mutableStateOf(initial?.firstDue) }
    var areaId by remember { mutableStateOf(initial?.areaId) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var minutes by remember { mutableStateOf(initial?.minutes?.toString().orEmpty()) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var more by remember { mutableStateOf(!adding) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save() {
        val mins = if (minutes.isBlank()) null else minutes.toIntOrNull()
        if (minutes.isNotBlank() && mins == null) { message = "Minutes is a whole number."; return }
        val out = if (initial == null) home.addChore(title, repeat, first, instructions, areaId, category, mins)
        else home.updateChore(initial.copy(title = title, recurrence = repeat, firstDue = first, areaId = areaId, category = category, minutes = mins, instructions = instructions))
        if (out.record == null) { message = out.refused ?: "That could not be saved."; return }
        message = null
        if (adding) { title = ""; repeat = Recurrence(); first = null; areaId = null; category = ""; minutes = ""; instructions = ""; more = false; model.haptic() }
        onDone()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (adding) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add a chore", singleLine = true, keyboardOptions = wordKeys)
            PixelButton("Add", ::save, color = accent, enabled = title.isNotBlank())
        } else PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Chore", singleLine = true, keyboardOptions = wordKeys)
        if (adding) PixelButton(if (more) "Fewer options" else "Repeat and details", { more = !more }, color = Pixel.InkSoft, compact = true)
        if (more) {
            SectionLabel("Repeat")
            RepeatEditor(repeat) { repeat = it }
            DateField(if (repeat.kind == RepeatKind.ONCE) "Date, optional" else "First date", first, { first = it }, today, optional = repeat.kind == RepeatKind.ONCE)
            AreaChips(data.areas, areaId) { areaId = it }
            PixelField(category, { category = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = "Minutes, optional", singleLine = true, keyboardOptions = numberKeys)
            PixelField(instructions, { instructions = it.take(HomeRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Instructions, optional", minLines = 2, keyboardOptions = sentenceKeys)
        }
        if (!adding) Actions {
            PixelButton("Save", ::save, Modifier.weight(1f), accent, enabled = title.isNotBlank(), compact = true)
            PixelButton("Cancel", { onCancel?.invoke() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Supplies ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuppliesView(model: AppModel) {
    val home = model.home
    val data = model.homeData
    val jump = remember { takeJump(HouseholdSection.SUPPLIES) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var openId by remember { mutableStateOf(jump?.id.takeIf { jump?.tab != 2 }) }
    var showArchived by remember { mutableStateOf(false) }
    val active = data.supplies.filter { !it.archived }.sortedBy { it.name.lowercase() }
    val low = HomeRules.lowSupplies(data, includeQuieted = true)
    val archived = data.supplies.filter { it.archived }
    TextBox { SupplyForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Tabs(listOf("All", "Low", "Restock"), tab) { tab = it; openId = null }
            when (tab) {
                0, 1 -> {
                    val rows = if (tab == 0) active else low
                    if (rows.isEmpty()) PixelTitle(if (tab == 0) "No supplies yet" else "Nothing is low")
                    rows.forEachIndexed { i, s ->
                        if (i > 0) Rule()
                        HRow(
                            s.name, listOfNotNull("Low".takeIf { HomeRules.isLow(s) }, s.category.takeIf { it.isNotBlank() }, areaName(data, s.areaId)).joinToString(" · "), warn = HomeRules.isLow(s),
                            trail = { Text(quantityText(s), style = MaterialTheme.typography.titleSmall) },
                            onClick = { openId = if (openId == s.id) null else s.id }, onClickLabel = if (openId == s.id) "Hide options" else "Show options",
                        )
                        if (openId == s.id) SupplyOptions(model, s) { openId = null }
                    }
                    if (tab == 0 && archived.isNotEmpty()) {
                        PixelButton(if (showArchived) "Hide archived" else "Archived (${archived.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        if (showArchived) archived.forEach { s ->
                            HRow(s.name, "Archived", trail = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    PixelButton("Restore", { home.setSupplyArchived(s.id, false) }, color = accent, compact = true)
                                    PixelIconButton("trash", "Delete ${s.name}", { val before = model.homeData; home.deleteSupply(s.id); model.offerUndo("Deleted: ${s.name}") { home.restore(before) } }, Pixel.InkSoft)
                                }
                            })
                        }
                    }
                }
                else -> RestockTab(model, jump?.id)
            }
        }
    }
}

@Composable
private fun SupplyOptions(model: AppModel, s: Supply, onDone: () -> Unit) {
    val home = model.home
    var editing by remember(s.id) { mutableStateOf(false) }
    var asking by remember(s.id) { mutableStateOf(false) }
    var setText by remember(s.id) { mutableStateOf("") }
    var refused by remember(s.id) { mutableStateOf<String?>(null) }
    if (editing) { SupplyForm(model, s, onDone = { editing = false; onDone() }, onCancel = { editing = false }); return }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this supply and its restocking entries?", "Delete", onConfirm = { home.deleteSupply(s.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelIconButton("minus", "One less ${s.name}", { refused = home.adjust(s.id, -1000) })
            PixelIconButton("plus", "One more ${s.name}", { refused = home.adjust(s.id, 1000) }, accent)
            PixelField(setText, { setText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.weight(1f), placeholder = "Quantity, blank for unknown", singleLine = true, keyboardOptions = decimalKeys)
            PixelButton("Set", {
                val milli = if (setText.isBlank()) null else Quantity.parse(setText)
                if (setText.isNotBlank() && milli == null) refused = "Check the quantity."
                else { refused = home.setQuantity(s.id, milli, "Set by hand"); if (refused == null) setText = "" }
            }, color = Pixel.InkSoft, compact = true)
        }
        if (model.homeData.settings.reminders && s.lowMilli != null) Actions {
            PixelButton(if (s.remind) "Low-stock reminder on" else "Tell me when low", { home.setSupplyRemind(s.id, !s.remind) }, Modifier.weight(1f), if (s.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Archive", { home.setSupplyArchived(s.id, true); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun RestockTab(model: AppModel, focus: String?) {
    val home = model.home
    val data = model.homeData
    val lines = HomeRules.restockList(data)
    var name by remember { mutableStateOf("") }
    var askingKey by remember { mutableStateOf<String?>(focus?.let { "low:$it" }) }
    var quantity by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf<String?>(null) }
    if (lines.isEmpty()) PixelTitle("Nothing to restock")
    lines.forEachIndexed { i, line ->
        if (i > 0) Rule()
        val supply = when (line) { is RestockLine.Low -> line.supply; is RestockLine.Manual -> line.supply }
        val key = when (line) { is RestockLine.Low -> "low:${line.supply.id}"; is RestockLine.Manual -> "entry:${line.entry.id}" }
        val title = when (line) { is RestockLine.Low -> line.supply.name; is RestockLine.Manual -> line.entry.name }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            HRow(
                title, if (line is RestockLine.Low) "Low · ${quantityText(line.supply)}" + if (line.supply.lowDismissed) " · not now" else "" else supply?.let { quantityText(it) }.orEmpty(),
                warn = line is RestockLine.Low,
                trail = {
                    if (askingKey != key) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        when (line) {
                            is RestockLine.Low -> {
                                PixelButton("Bought", { askingKey = key; quantity = "" }, color = accent, compact = true)
                                if (!line.supply.lowDismissed) PixelButton("Not now", { home.dismissLow(line.supply.id) }, color = Pixel.InkSoft, compact = true)
                            }
                            is RestockLine.Manual -> {
                                if (supply != null) PixelButton("Bought", { askingKey = key; quantity = "" }, color = accent, compact = true)
                                else PixelButton("Done", { home.removeRestock(line.entry.id) }, color = accent, compact = true)
                                PixelIconButton("close", "Remove $title from the list", { home.removeRestock(line.entry.id) }, Pixel.InkSoft)
                            }
                        }
                    }
                },
            )
            if (askingKey == key && supply != null) {
                PixelField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(), label = "New quantity${if (supply.unit.isNotEmpty()) " (${supply.unit})" else ""}, blank to keep it", singleLine = true, keyboardOptions = decimalKeys)
                Actions {
                    PixelButton("Save", {
                        val milli = if (quantity.isBlank()) null else Quantity.parse(quantity)
                        if (quantity.isNotBlank() && milli == null) refused = "Check the quantity."
                        else { refused = home.restocked(supply.id, milli, (line as? RestockLine.Manual)?.entry?.id); if (refused == null) { askingKey = null; quantity = "" } }
                    }, Modifier.weight(1f), accent, compact = true)
                    PixelButton("Cancel", { askingKey = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add to the list", singleLine = true, keyboardOptions = wordKeys)
        PixelButton("Add", { if (home.addRestock(name) != null) name = "" }, color = accent, enabled = name.isNotBlank())
    }
    refused?.let { Report(model, it, NoticeTone.WARNING) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplyForm(model: AppModel, initial: Supply?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.home
    val data = model.homeData
    val adding = initial == null
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit.orEmpty()) }
    var quantity by remember { mutableStateOf("") }
    var low by remember { mutableStateOf(initial?.lowMilli?.let { Quantity.format(it) }.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var areaId by remember { mutableStateOf(initial?.areaId) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var newUnit by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(!adding) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save() {
        val qty = if (quantity.isBlank()) null else Quantity.parse(quantity)
        val lowMilli = if (low.isBlank()) null else Quantity.parse(low)
        if (quantity.isNotBlank() && qty == null) { message = "Check the quantity."; return }
        if (low.isNotBlank() && lowMilli == null) { message = "Check the low mark."; return }
        val out = if (initial == null) home.addSupply(name, unit, qty, lowMilli, category, areaId, notes)
        else home.updateSupply(initial.copy(name = name, unit = unit, lowMilli = lowMilli, category = category, areaId = areaId, notes = notes))
        if (out.record == null) { message = out.refused ?: "That could not be saved."; return }
        message = null
        if (adding) { name = ""; unit = ""; quantity = ""; low = ""; category = ""; areaId = null; notes = ""; more = false; model.haptic() }
        onDone()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (adding) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add a supply", singleLine = true, keyboardOptions = wordKeys)
            PixelButton("Add", ::save, color = accent, enabled = name.isNotBlank())
        } else PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Supply", singleLine = true, keyboardOptions = wordKeys)
        if (adding) PixelButton(if (more) "Fewer options" else "Quantity and details", { more = !more }, color = Pixel.InkSoft, compact = true)
        if (more) {
            if (adding) PixelField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(), label = "Quantity, blank if unknown", singleLine = true, keyboardOptions = decimalKeys)
            SectionLabel("Unit")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("None", unit.isEmpty(), { unit = "" })
                (data.settings.units + unit).filter { it.isNotBlank() }.distinct().forEach { u -> Chip(u, u == unit, { unit = u }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(newUnit, { newUnit = it.take(16) }, Modifier.weight(1f), placeholder = "Another unit", singleLine = true)
                PixelButton("Add unit", { home.addUnit(newUnit); if (newUnit.isNotBlank()) { unit = newUnit.trim(); newUnit = "" } }, color = Pixel.InkSoft, enabled = newUnit.isNotBlank(), compact = true)
            }
            PixelField(low, { low = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(), label = "Low at or under, blank for none", singleLine = true, keyboardOptions = decimalKeys)
            AreaChips(data.areas, areaId) { areaId = it }
            PixelField(category, { category = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(notes, { notes = it.take(HomeRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", minLines = 2, keyboardOptions = sentenceKeys)
        }
        if (!adding) Actions {
            PixelButton("Save", ::save, Modifier.weight(1f), accent, enabled = name.isNotBlank(), compact = true)
            PixelButton("Cancel", { onCancel?.invoke() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Maintenance ------------------------------------------------------------------------------------------------------------

@Composable
private fun MaintenanceView(model: AppModel) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    val jump = remember { takeJump(HouseholdSection.MAINTENANCE) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var openId by remember { mutableStateOf(jump?.id) }
    var logging by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val views = HomeRules.schedules(data, today)
    val history = HomeRules.maintenanceHistory(data).take(60)
    val assets = data.assets.filter { !it.archived }
    val archivedAssets = data.assets.filter { it.archived }
    RewardBox(model)
    if (tab == 0) TextBox { ScheduleForm(model, null, onDone = {}) }
    if (tab == 1) TextBox { AssetForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Tabs(listOf("Upcoming", "Items", "History"), tab) { tab = it; openId = null }
            when (tab) {
                0 -> {
                    if (views.isEmpty()) PixelTitle("No maintenance yet")
                    views.forEachIndexed { i, v ->
                        if (i > 0) Rule()
                        HRow(
                            v.schedule.title, scheduleLine(data, v), warn = v.state == DueState.OVERDUE,
                            lead = { if (v.state != DueState.PAUSED) PixelIconButton("check", "Mark ${v.schedule.title} done", { home.completeMaintenance(v.schedule.id); model.haptic() }, accent) },
                            trail = { if (v.state == DueState.PAUSED) PixelButton("Resume", { home.setScheduleStatus(v.schedule.id, RecordStatus.ACTIVE) }, color = Pixel.InkSoft, compact = true) },
                            onClick = { openId = if (openId == v.schedule.id) null else v.schedule.id }, onClickLabel = if (openId == v.schedule.id) "Hide options" else "Show options",
                        )
                        if (openId == v.schedule.id) ScheduleOptions(model, v.schedule) { openId = null }
                    }
                    PixelButton(if (logging) "Hide the form" else "Log work done", { logging = !logging }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    if (logging) LogWorkForm(model) { logging = false }
                }
                1 -> {
                    if (assets.isEmpty()) PixelTitle("No items yet")
                    assets.forEachIndexed { i, a ->
                        if (i > 0) Rule()
                        val warranties = data.warranties.filter { it.assetId == a.id }
                        val states = warranties.map { HomeRules.warrantyState(it, today) }
                        val word = if (WarrantyState.ENDING in states) "Warranty ends soon" else if (WarrantyState.ACTIVE in states || WarrantyState.UNDATED in states) "Covered" else if (states.isNotEmpty()) "Warranty expired" else null
                        HRow(
                            a.name, listOfNotNull(listOf(a.brand, a.model).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }, areaName(data, a.areaId), word).joinToString(" · "),
                            warn = WarrantyState.ENDING in states,
                            onClick = { openId = if (openId == a.id) null else a.id }, onClickLabel = if (openId == a.id) "Hide details" else "Show details",
                        )
                        if (openId == a.id) AssetDetail(model, a) { openId = null }
                    }
                    if (archivedAssets.isNotEmpty()) {
                        PixelButton(if (showArchived) "Hide archived" else "Archived (${archivedAssets.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        if (showArchived) archivedAssets.forEach { a ->
                            HRow(a.name, "Archived", trail = { PixelButton("Restore", { home.setAssetArchived(a.id, false) }, color = accent, compact = true) })
                        }
                    }
                }
                else -> {
                    if (history.isEmpty()) PixelTitle("Nothing here yet")
                    history.forEachIndexed { i, r ->
                        if (i > 0) Rule()
                        var asking by remember(r.id) { mutableStateOf(false) }
                        HRow(
                            r.title, listOfNotNull(r.day.short(), data.assets.firstOrNull { it.id == r.assetId }?.name, r.notes.takeIf { it.isNotBlank() }, r.cost.takeIf { it.isNotBlank() }).joinToString(" · "),
                            trail = { PixelIconButton("trash", "Remove ${r.title} from the history", { asking = true }, Pixel.InkSoft) },
                        )
                        if (asking) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Confirm("Remove this record?", "Remove", onConfirm = { home.deleteMaintenanceRecord(r.id); asking = false }, onKeep = { asking = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleOptions(model: AppModel, s: MaintenanceSchedule, onDone: () -> Unit) {
    val home = model.home
    var editing by remember(s.id) { mutableStateOf(false) }
    var asking by remember(s.id) { mutableStateOf(false) }
    var notes by remember(s.id) { mutableStateOf("") }
    var cost by remember(s.id) { mutableStateOf("") }
    var refused by remember(s.id) { mutableStateOf<String?>(null) }
    if (editing) { ScheduleForm(model, s, onDone = { editing = false; onDone() }, onCancel = { editing = false }); return }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this schedule? The work it recorded stays in the history.", "Delete", onConfirm = { home.deleteSchedule(s.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        if (s.instructions.isNotBlank()) Text(s.instructions, style = MaterialTheme.typography.bodyLarge)
        PixelField(notes, { notes = it.take(HomeRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", keyboardOptions = sentenceKeys)
        PixelField(cost, { cost = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Cost, optional", singleLine = true)
        Actions {
            if (s.status != RecordStatus.PAUSED) PixelButton("Done", { home.completeMaintenance(s.id, notes, cost); model.haptic(); onDone() }, Modifier.weight(1f), accent, compact = true)
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        if (model.homeData.settings.reminders && s.status == RecordStatus.ACTIVE) Actions {
            PixelButton(if (s.remind) "Reminder on" else "Remind me", { home.setScheduleRemind(s.id, !s.remind) }, Modifier.weight(1f), if (s.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            if (s.status == RecordStatus.PAUSED) PixelButton("Resume", { home.setScheduleStatus(s.id, RecordStatus.ACTIVE); onDone() }, Modifier.weight(1f), accent, compact = true)
            else PixelButton("Pause", { home.setScheduleStatus(s.id, RecordStatus.PAUSED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        refused?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

/** Adds a maintenance schedule or, with [initial], edits one (its next date is where it stands). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleForm(model: AppModel, initial: MaintenanceSchedule?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    val adding = initial == null
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var due by remember { mutableStateOf<LocalDate?>(initial?.nextDue ?: today) }
    var repeat by remember { mutableStateOf(initial?.recurrence ?: Recurrence()) }
    var policy by remember { mutableStateOf(initial?.policy ?: SchedulePolicy.FIXED) }
    var assetId by remember { mutableStateOf(initial?.assetId) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var more by remember { mutableStateOf(!adding) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save() {
        val day = due
        if (day == null) { message = "Choose the first date."; return }
        val out = if (initial == null) home.addSchedule(title, day, repeat, policy, assetId, instructions)
        else home.updateSchedule(initial.copy(title = title, firstDue = day, nextDue = day, recurrence = repeat, policy = policy, assetId = assetId, instructions = instructions))
        if (out.record == null) { message = out.refused ?: "That could not be saved."; return }
        message = null
        if (adding) { title = ""; due = today; repeat = Recurrence(); policy = SchedulePolicy.FIXED; assetId = null; instructions = ""; more = false; model.haptic() }
        onDone()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (adding) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add maintenance", singleLine = true, keyboardOptions = wordKeys)
            PixelButton("Add", ::save, color = accent, enabled = title.isNotBlank() && due != null)
        } else PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Maintenance", singleLine = true, keyboardOptions = wordKeys)
        if (adding) PixelButton(if (more) "Fewer options" else "Date, repeat and item", { more = !more }, color = Pixel.InkSoft, compact = true)
        if (more) {
            DateField(if (adding) "First due" else "Next due", due, { due = it }, today)
            SectionLabel("Repeat")
            RepeatEditor(repeat) { repeat = it }
            if (repeat.kind != RepeatKind.ONCE) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SchedulePolicy.entries.forEach { p -> Chip(p.label, p == policy, { policy = p }) }
            }
            AssetChips(data.assets, assetId) { assetId = it }
            PixelField(instructions, { instructions = it.take(HomeRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Instructions, optional", minLines = 2, keyboardOptions = sentenceKeys)
        }
        if (!adding) Actions {
            PixelButton("Save", ::save, Modifier.weight(1f), accent, enabled = title.isNotBlank() && due != null, compact = true)
            PixelButton("Cancel", { onCancel?.invoke() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun LogWorkForm(model: AppModel, onDone: () -> Unit) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<LocalDate?>(today) }
    var assetId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "What was done", singleLine = true, keyboardOptions = wordKeys)
        DateField("Day", day, { day = it }, today, past = true)
        AssetChips(data.assets, assetId) { assetId = it }
        PixelField(notes, { notes = it.take(HomeRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", keyboardOptions = sentenceKeys)
        PixelField(cost, { cost = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Cost, optional", singleLine = true)
        PixelButton("Save", {
            val d = day
            if (d == null) message = "Choose the day."
            else {
                val out = home.logMaintenance(title, assetId, notes, cost, d)
                if (out.record == null) message = out.refused ?: "That could not be saved."
                else { model.haptic(); onDone() }
            }
        }, Modifier.fillMaxWidth(), accent, enabled = title.isNotBlank() && day != null)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun AssetDetail(model: AppModel, a: Asset, onDone: () -> Unit) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    var editing by remember(a.id) { mutableStateOf(false) }
    var asking by remember(a.id) { mutableStateOf(false) }
    var warranty by remember(a.id) { mutableStateOf(false) }
    if (editing) { AssetForm(model, a, onDone = { editing = false }, onCancel = { editing = false }); return }
    val warranties = data.warranties.filter { it.assetId == a.id }
    val schedules = HomeRules.schedules(data, today).filter { it.schedule.assetId == a.id }
    val history = HomeRules.maintenanceHistory(data, a.id).take(10)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this item? Its warranties go. Its maintenance schedules and records stay as general ones; archiving keeps everything together.", "Delete", onConfirm = { home.deleteAsset(a.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        listOf(
            "Category" to a.category, "Brand" to a.brand, "Model" to a.model, "Serial" to a.serial, "Bought" to (a.purchaseDay?.short() ?: ""), "Area" to (areaName(data, a.areaId) ?: ""), "Notes" to a.notes,
        ).filter { it.second.isNotBlank() }.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyLarge) }
        if (warranties.isNotEmpty()) SectionLabel("Warranty")
        warranties.forEach { w ->
            val state = HomeRules.warrantyState(w, today)
            HRow(
                listOf(w.provider, w.coverage).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Warranty" },
                listOfNotNull(state.label, w.expiryDay?.let { "until ${it.short()}" }, w.reference.takeIf { it.isNotBlank() }).joinToString(" · "), warn = state == WarrantyState.ENDING,
                trail = { PixelIconButton("trash", "Delete this warranty", { val before = model.homeData; home.deleteWarranty(w.id); model.offerUndo("Deleted: warranty") { home.restore(before) } }, Pixel.InkSoft) },
            )
            if (model.homeData.settings.reminders && w.expiryDay != null) PixelButton(if (w.remind) "Reminder on" else "Remind me before it ends", { home.setWarrantyRemind(w.id, !w.remind) }, Modifier.fillMaxWidth(), if (w.remind) accent else Pixel.InkSoft, compact = true)
        }
        if (warranty) WarrantyForm(model, a.id) { warranty = false }
        else PixelButton("Add warranty", { warranty = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (schedules.isNotEmpty()) SectionLabel("Maintenance")
        schedules.forEach { v -> HRow(v.schedule.title, scheduleLine(data, v).substringBefore(" · ${data.assets.firstOrNull { it.id == a.id }?.name}"), warn = v.state == DueState.OVERDUE) }
        if (history.isNotEmpty()) SectionLabel("History")
        history.forEach { r -> HRow(r.title, listOfNotNull(r.day.short(), r.notes.takeIf { it.isNotBlank() }, r.cost.takeIf { it.isNotBlank() }).joinToString(" · ")) }
        Actions {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Archive", { home.setAssetArchived(a.id, true); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun WarrantyForm(model: AppModel, assetId: String, onDone: () -> Unit) {
    val today = model.store.today
    var provider by remember { mutableStateOf("") }
    var coverage by remember { mutableStateOf("") }
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var reference by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(provider, { provider = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Provider", singleLine = true, keyboardOptions = wordKeys)
        PixelField(coverage, { coverage = it.take(HomeRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Coverage, optional", keyboardOptions = sentenceKeys)
        DateField("Starts, optional", start, { start = it }, today, optional = true, past = true)
        DateField("Ends, optional", end, { end = it }, today, optional = true)
        PixelField(reference, { reference = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Reference, optional", singleLine = true)
        Actions {
            PixelButton("Save", {
                val out = model.home.addWarranty(assetId, provider, coverage, start, end, reference)
                if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
            }, Modifier.weight(1f), accent, compact = true)
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun AssetForm(model: AppModel, initial: Asset?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.home
    val data = model.homeData
    val today = model.store.today
    val adding = initial == null
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var modelName by remember { mutableStateOf(initial?.model.orEmpty()) }
    var serial by remember { mutableStateOf(initial?.serial.orEmpty()) }
    var bought by remember { mutableStateOf(initial?.purchaseDay) }
    var areaId by remember { mutableStateOf(initial?.areaId) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var more by remember { mutableStateOf(!adding) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save() {
        val out = if (initial == null) home.addAsset(name, category, brand, modelName, serial, bought, areaId, notes)
        else home.updateAsset(initial.copy(name = name, category = category, brand = brand, model = modelName, serial = serial, purchaseDay = bought, areaId = areaId, notes = notes))
        if (out.record == null) { message = out.refused ?: "That could not be saved."; return }
        message = null
        if (adding) { name = ""; category = ""; brand = ""; modelName = ""; serial = ""; bought = null; areaId = null; notes = ""; more = false; model.haptic() }
        onDone()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (adding) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add an item", singleLine = true, keyboardOptions = wordKeys)
            PixelButton("Add", ::save, color = accent, enabled = name.isNotBlank())
        } else PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Item", singleLine = true, keyboardOptions = wordKeys)
        if (adding) PixelButton(if (more) "Fewer options" else "Brand, model and more", { more = !more }, color = Pixel.InkSoft, compact = true)
        if (more) {
            PixelField(category, { category = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(brand, { brand = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Brand, optional", singleLine = true, keyboardOptions = wordKeys)
            PixelField(modelName, { modelName = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Model, optional", singleLine = true)
            PixelField(serial, { serial = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Serial number, optional", singleLine = true)
            DateField("Bought, optional", bought, { bought = it }, today, optional = true, past = true)
            AreaChips(data.areas, areaId) { areaId = it }
            PixelField(notes, { notes = it.take(HomeRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", minLines = 2, keyboardOptions = sentenceKeys)
        }
        if (!adding) Actions {
            PixelButton("Save", ::save, Modifier.weight(1f), accent, enabled = name.isNotBlank(), compact = true)
            PixelButton("Cancel", { onCancel?.invoke() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Notes ------------------------------------------------------------------------------------------------------------------

@Composable
private fun NotesView(model: AppModel) {
    val home = model.home
    val data = model.homeData
    val jump = remember { takeJump(HouseholdSection.NOTES) }
    var title by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf(jump?.id) }
    var message by remember { mutableStateOf<String?>(null) }
    val matching = if (query.isBlank()) null else home.search(query).filter { it.kind == HitKind.REFERENCE }.map { it.id }.toSet()
    val notes = data.references.filter { matching == null || it.id in matching }.sortedByDescending { it.updatedMillis }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add a note", singleLine = true, keyboardOptions = sentenceKeys)
                PixelButton("Add", {
                    val out = home.addReference(title)
                    if (out.record == null) message = out.refused else { title = ""; message = null; openId = out.record!!.id; model.haptic() }
                }, color = accent, enabled = title.isNotBlank())
            }
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
    if (data.references.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Search notes", singleLine = true)
            if (notes.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            notes.forEachIndexed { i, n ->
                if (i > 0) Rule()
                HRow(
                    n.title, listOfNotNull(n.category.takeIf { it.isNotBlank() }, n.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)).joinToString(" · "),
                    onClick = { openId = if (openId == n.id) null else n.id }, onClickLabel = if (openId == n.id) "Close the note" else "Open the note",
                )
                if (openId == n.id) NoteEditor(model, n) { openId = null }
            }
        }
    }
}

@Composable
private fun NoteEditor(model: AppModel, n: Reference, onDone: () -> Unit) {
    val home = model.home
    val data = model.homeData
    var title by remember(n.id) { mutableStateOf(n.title) }
    var text by remember(n.id) { mutableStateOf(n.text) }
    var category by remember(n.id) { mutableStateOf(n.category) }
    var areaId by remember(n.id) { mutableStateOf(n.areaId) }
    var assetId by remember(n.id) { mutableStateOf(n.assetId) }
    var asking by remember(n.id) { mutableStateOf(false) }
    var message by remember(n.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this note?", "Delete", onConfirm = { home.deleteReference(n.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        PixelField(title, { title = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
        PixelField(text, { text = it.take(HomeRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 5, keyboardOptions = sentenceKeys)
        PixelField(category, { category = it.take(HomeRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
        AreaChips(data.areas, areaId) { areaId = it }
        AssetChips(data.assets, assetId) { assetId = it }
        Actions {
            PixelButton("Save", {
                val out = home.updateReference(n.copy(title = title, text = text, category = category, areaId = areaId, assetId = assetId))
                if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
            }, Modifier.weight(1f), accent, enabled = title.isNotBlank(), compact = true)
            PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Settings ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseholdSettingsView(model: AppModel) {
    val home = model.home
    val data = model.homeData
    val settings = data.settings
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<HomeKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Habitat Discovery")
            ToggleRow(
                "Find Pokémon from finished work", settings.progression,
                detail = "A chore or a piece of maintenance you finish can find a Habitat Egg; adding, editing, quantities and notes never do.",
            ) { home.setProgression(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Reminders")
            ToggleRow(
                "Remind me about the home", settings.reminders,
                detail = "Off by default, and each chore, job, supply and warranty is switched on by itself. One reminder each, never a daily nag for something late. It never shows a quantity or a serial number, and it never records anything for you.",
            ) { home.setReminders(it) }
            if (settings.reminders) {
                SectionLabel("At")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(8, 9, 12, 18, 20).forEach { h -> Chip("${h.toString().padStart(2, '0')}:00", h == settings.reminderHour, { home.setReminderHour(h) }) }
                }
                SectionLabel("Maintenance")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before").forEach { (d, l) -> Chip(l, d == settings.maintenanceLeadDays, { home.setMaintenanceLeadDays(d) }) }
                }
                SectionLabel("Warranties")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(7 to "A week before", 14 to "2 weeks", 30 to "A month", 60 to "2 months").forEach { (d, l) -> Chip(l, d == settings.warrantyLeadDays, { home.setWarrantyLeadDays(d) }) }
                }
                Text("Your quiet hours and focus sessions still hold a reminder back.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                ToggleRow("Name it", settings.reminderNames, detail = "After you unlock your phone it says which chore, job or supply. The lock screen only says what kind.") { home.setReminderNames(it) }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Overview")
            listOf("chores" to "Chores", "supplies" to "Low supplies", "maintenance" to "Maintenance and warranties").forEach { (key, label) ->
                ToggleRow(label, key !in settings.hidden) { home.setSectionHidden(key, !it) }
            }
        }
    }
    AreasBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            Text("Your records stay on this phone. Erasing Home never touches your Pokémon, your Poké or the other modules.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-home.json", home.export()) }, Modifier.fillMaxWidth(), accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    HomeKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-home-${k.name.lowercase()}.json", home.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            } else Text("Exporting is not available on this device.", style = MaterialTheme.typography.bodyMedium)
            note?.let { Report(model, it) }
            HomeKinds.entries.forEach { k ->
                if (asking == k) Confirm(
                    if (k == HomeKinds.ASSETS) "Erase all belongings with their maintenance and warranties? This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                    "Erase", "Keep them", onConfirm = { home.erase(k); asking = null }, onKeep = { asking = null },
                ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) Confirm("Erase every Home record? This cannot be undone.", "Erase all", "Keep them", onConfirm = { home.eraseAll(); askingAll = false }, onKeep = { askingAll = false })
            else PixelButton("Erase all Home records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
}

@Composable
private fun AreasBox(model: AppModel) {
    val home = model.home
    val data = model.homeData
    var name by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Areas")
            data.areas.forEachIndexed { i, a ->
                if (i > 0) Rule()
                HRow(
                    a.name, if (a.archived) "Archived" else "",
                    onClick = { openId = if (openId == a.id) null else a.id; renaming = a.name; asking = null }, onClickLabel = if (openId == a.id) "Hide options" else "Show options",
                    trail = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelIconButton("arrow-up", "Move ${a.name} up", { home.moveArea(a.id, true) }, enabled = i > 0)
                            PixelIconButton("arrow-down", "Move ${a.name} down", { home.moveArea(a.id, false) }, enabled = i < data.areas.lastIndex)
                        }
                    },
                )
                if (openId == a.id) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    if (asking == a.id) Confirm("Delete this area? What names it stays, with no area.", "Delete", onConfirm = { home.deleteArea(a.id); openId = null; asking = null }, onKeep = { asking = null })
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelField(renaming, { renaming = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Name", singleLine = true, keyboardOptions = wordKeys)
                            PixelButton("Rename", { home.renameArea(a.id, renaming) }, color = accent, enabled = renaming.isNotBlank() && renaming.trim() != a.name, compact = true)
                        }
                        Actions {
                            PixelButton(if (a.archived) "Restore" else "Archive", { home.setAreaArchived(a.id, !a.archived); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { asking = a.id }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(name, { name = it.take(HomeRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = "Add an area", singleLine = true, keyboardOptions = wordKeys)
                PixelButton("Add", { if (home.addArea(name) != null) name = "" }, color = accent, enabled = name.isNotBlank())
            }
        }
    }
}
