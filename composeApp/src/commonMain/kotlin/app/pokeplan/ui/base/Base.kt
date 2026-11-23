package app.pokeplan.ui.base

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Tutorials
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskState
import app.pokeplan.base.Asset
import app.pokeplan.base.Chore
import app.pokeplan.base.ChoreState
import app.pokeplan.base.ChoreView
import app.pokeplan.base.DueState
import app.pokeplan.base.EntryStatus
import app.pokeplan.base.HitKind
import app.pokeplan.base.BaseArea
import app.pokeplan.base.BaseKinds
import app.pokeplan.base.BaseRules
import app.pokeplan.base.MaintenanceRecord
import app.pokeplan.base.MaintenanceSchedule
import app.pokeplan.base.Overview
import app.pokeplan.base.Quantity
import app.pokeplan.base.RecordStatus
import app.pokeplan.base.Recurrence
import app.pokeplan.base.Reference
import app.pokeplan.base.RepeatKind
import app.pokeplan.base.RestockLine
import app.pokeplan.base.SchedulePolicy
import app.pokeplan.base.Supply
import app.pokeplan.base.Warranty
import app.pokeplan.base.WarrantyState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.Accordion
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
import app.pokeplan.ui.pokemon.BasePartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.Actions
import app.pokeplan.ui.screens.Confirm
import app.pokeplan.ui.screens.DateField
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormLabel
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.FirstRecordEgg
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuGrid
import app.pokeplan.ui.screens.SectionsKey
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.ModuleLeadStage
import app.pokeplan.ui.screens.ModuleTabs
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.pullReveal
import app.pokeplan.ui.screens.Rule
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.plural
import app.pokeplan.ui.screens.short

enum class BaseSection(val label: String, val icon: String) {
    CHORES("Chores", "cracked-pot"), BELONGINGS("Belongings", "seal-bag"), MAINTENANCE("Maintenance", "iron"),
    NOTES("Notes", "oaks-letter"), ROOM("Room", "star-piece"), SETTINGS("Settings", "storage-key"),
}

private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.BASE.accent

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val wordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExampleChips(examples: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        examples.forEach { e -> Chip(e, false, { onPick(e) }) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

private fun tickChore(model: AppModel, v: ChoreView) {
    val entry = model.base.complete(v.chore.id).record ?: return
    model.haptic()
    model.offerUndo("Done: ${v.chore.title}") { model.base.undo(entry.id) }
}

private fun tickMaintenance(model: AppModel, s: MaintenanceSchedule, notes: String = "", cost: String = "") {
    val before = model.baseData
    if (model.base.completeMaintenance(s.id, notes, cost).record == null) return
    model.haptic()
    model.offerUndo("Done: ${s.title}") { model.base.restore(before) }
}

private class Jump(val section: BaseSection, val id: String? = null, val tab: Int? = null)

private var pendingJump: Jump? = null

private fun go(model: AppModel, section: BaseSection, id: String? = null, tab: Int? = null) {
    pendingJump = Jump(section, id, tab)
    model.baseSection = section
}

private fun takeJump(section: BaseSection): Jump? = pendingJump?.takeIf { it.section == section }?.also { pendingJump = null }

internal fun baseSubtitle(model: AppModel): String {
    val n = BaseRules.overview(model.baseData, model.store.today).chores.size
    return if (n > 0) "$n to do today" else "chores, belongings, upkeep"
}

@Composable
internal fun BaseScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenSwitch(model.baseSection, depth = { if (it == null) 0 else 1 }) { section -> BasePage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BasePage(model: AppModel, section: BaseSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        BaseMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.baseSection = null }, icon = section.icon, color = Module.BASE.look.header) {
        when (section) {
            BaseSection.CHORES -> ChoresView(model)
            BaseSection.BELONGINGS -> BelongingsView(model)
            BaseSection.MAINTENANCE -> MaintenanceView(model)
            BaseSection.NOTES -> NotesView(model)
            BaseSection.ROOM -> RoomView(model)
            BaseSection.SETTINGS -> BaseSettingsView(model)
        }
    }
}

@Composable
private fun BaseMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.baseData
    val today = model.store.today
    val tint = ModuleAccent.BASE.accent
    val overview = BaseRules.overview(data, today)
    val sections = listOf(
        MenuEntry(BaseSection.CHORES.name, BaseSection.CHORES.icon, "Chores", if (data.chores.isEmpty()) "add one" else "${overview.chores.size} to do today", tint = tint),
        MenuEntry(BaseSection.BELONGINGS.name, BaseSection.BELONGINGS.icon, "Belongings", belongingsDetail(data, overview), tint = tint),
        MenuEntry(BaseSection.MAINTENANCE.name, BaseSection.MAINTENANCE.icon, "Maintenance", if (data.schedules.isEmpty()) "add one" else "${overview.maintenance.size} due or soon", tint = tint),
        MenuEntry(BaseSection.NOTES.name, BaseSection.NOTES.icon, "Notes", if (data.references.isEmpty()) "add one" else "${data.references.size} notes", tint = tint),
        MenuEntry(BaseSection.ROOM.name, BaseSection.ROOM.icon, "Room", "${data.room.size} placed", tint = tint),
        MenuEntry(BaseSection.SETTINGS.name, BaseSection.SETTINGS.icon, "Settings", tint = Pixel.InkSoft),
    )
    val scroll = rememberScrollState()
    var query by remember { mutableStateOf("") }
    val reveal = app.pokeplan.ui.screens.rememberPullReveal(scroll, keepOpen = { query.isNotBlank() }, onClose = { query = "" })
    val hasRecords = data.chores.isNotEmpty() || data.supplies.isNotEmpty() || data.assets.isNotEmpty() || data.references.isNotEmpty() || data.schedules.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.BASE.label, onBack = onBack, color = Module.BASE.look.header, trailing = { SectionsKey(Module.BASE.label, sections) { key -> model.baseSection = BaseSection.valueOf(key) } })
        Column(
            Modifier.weight(1f).pullReveal(reveal).verticalScroll(scroll).padding(horizontal = Spacing.Page, vertical = Spacing.L),
        ) {
            if (hasRecords) app.pokeplan.ui.screens.PullRevealSlot(reveal, model.data.settings.reduceMotion) { BaseSearch(model, query, { query = it }, reveal::close) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                ModuleLeadStage(model, Module.BASE, model.data.player.baseLead, model.store.moduleEvent(app.pokeplan.app.GameStore.BASE_LOG_PREFIX), openPokemon = openPokemon)
                FirstRecordEgg(model, Tutorials.BASE_TIDY, data.chores.size + data.supplies.size + data.assets.size)
                BaseOverview(model, overview)
                BaseQuickActions(model)
                BasePartyPanel(model, openPokemon)
            }
        }
    }
}

/** What the Belongings card says under its name: the lowest count that matters first. */
private fun belongingsDetail(data: app.pokeplan.base.BaseData, o: Overview): String = when {
    data.supplies.isEmpty() && data.assets.isEmpty() -> "add one"
    o.low.isNotEmpty() -> "${o.low.size} low"
    data.supplies.isNotEmpty() && data.assets.isNotEmpty() -> "${data.supplies.size} supplies and ${data.assets.size} items"
    data.supplies.isNotEmpty() -> "${data.supplies.size} supplies"
    else -> "${data.assets.size} items"
}

@Composable
private fun BaseOverview(model: AppModel, o: Overview) {
    val data = model.baseData
    val home = model.base
    val today = model.store.today
    val attention = o.chores.isNotEmpty() || o.maintenance.isNotEmpty() || o.warranties.isNotEmpty() || o.low.isNotEmpty()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (attention) "Needs a look" else if (data.chores.isEmpty() && data.supplies.isEmpty() && data.assets.isEmpty()) "Add your first chore" else "Nothing needs attention")
            if (o.chores.isNotEmpty()) {
                SectionLabel("Chores")
                o.chores.take(5).forEach { v ->
                    RecordRow(
                        v.chore.title, choreLine(data, v, today), warn = v.state == ChoreState.OVERDUE,
                        lead = { PixelIconButton("check", "Mark ${v.chore.title} done", { tickChore(model, v) }, accent) },
                        onClick = { go(model, BaseSection.CHORES, v.chore.id, 0) }, onClickLabel = "Open chores",
                    )
                }
                if (o.chores.size > 5) PixelButton("All ${o.chores.size} chores", { go(model, BaseSection.CHORES, tab = 0) }, color = Pixel.InkSoft, compact = true)
            }
            if (o.maintenance.isNotEmpty()) {
                SectionLabel("Maintenance")
                o.maintenance.take(5).forEach { v ->
                    RecordRow(
                        v.schedule.title, scheduleLine(data, v), warn = v.state == DueState.OVERDUE,
                        lead = { PixelIconButton("check", "Mark ${v.schedule.title} done", { tickMaintenance(model, v.schedule) }, accent) },
                        onClick = { go(model, BaseSection.MAINTENANCE, v.schedule.id, 0) }, onClickLabel = "Open maintenance",
                    )
                }
            }
            if (o.low.isNotEmpty()) {
                SectionLabel("Running low")
                o.low.take(5).forEach { s ->
                    RecordRow(
                        s.name, "Low · ${quantityText(s)}",
                        trail = { PixelButton("Not now", { home.dismissLow(s.id) }, color = Pixel.InkSoft, compact = true) },
                        onClick = { go(model, BaseSection.BELONGINGS, s.id, 1) }, onClickLabel = "Open the shopping list",
                    )
                }
            }
            if (o.warranties.isNotEmpty()) {
                SectionLabel("Warranties")
                o.warranties.take(3).forEach { w ->
                    val asset = data.assets.firstOrNull { it.id == w.assetId }
                    RecordRow(
                        asset?.name ?: "Warranty", listOfNotNull("Ends ${w.expiryDay?.short()}", w.provider.takeIf { it.isNotBlank() }).joinToString(" · "),
                        onClick = { go(model, BaseSection.BELONGINGS, w.assetId, 2) }, onClickLabel = "Open belongings",
                    )
                }
            }
        }
    }
}

/** The kinds quick capture can make from the overview. */
private enum class CaptureKind(val label: String, val placeholder: String) {
    CHORE("Chore", "Add a chore"), STOCK("Stock", "Add a supply"), SHOPPING("Shopping", "Add to the shopping list"), NOTE("Note", "Add a note"),
}

/**
 * Quick actions that really act, not a second way to reach the sections: one title is enough to
 * make a chore, a supply, a shopping line or a note.
 */
@Composable
private fun BaseQuickActions(model: AppModel) {
    val home = model.base
    var kind by remember { mutableStateOf(CaptureKind.CHORE) }
    var text by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf<String?>(null) }
    fun add() {
        val title = text.trim()
        if (title.isEmpty()) return
        val problem = when (kind) {
            CaptureKind.CHORE -> home.addChore(title).refused
            CaptureKind.STOCK -> home.addSupply(title).refused
            CaptureKind.SHOPPING -> if (home.addRestock(title) == null) "That is already on the list." else null
            CaptureKind.NOTE -> home.addReference(title).refused
        }
        if (problem != null) { refused = problem; return }
        refused = null; text = ""; model.haptic()
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Quick actions")
            PixelChoice(CaptureKind.entries.toList(), kind, { it?.label ?: "" }, { picked -> if (picked != null) { kind = picked; refused = null } }, key = accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    text, { text = it.take(BaseRules.TITLE_MAX) }, Modifier.weight(1f), placeholder = kind.placeholder, singleLine = true,
                    keyboardOptions = wordKeys,
                )
                PixelIconButton("plus", "Add", ::add, accent, enabled = text.isNotBlank())
            }
            refused?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BaseSearch(model: AppModel, query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    val field = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { field.requestFocus() }
    val hits = model.base.search(query).take(8)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(query, { onQuery(it.take(40)) }, Modifier.weight(1f).focusRequester(field), placeholder = "Search the household", singleLine = true)
        PixelIconButton("close", "Close search", onDone)
    }
    if (query.isNotBlank() && hits.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    hits.forEach { h ->
        RecordRow(h.title, listOf(h.kind.label, h.detail.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), onClick = { openHit(model, h.kind, h.id); onDone() }, onClickLabel = "Open")
    }
}

private fun openHit(model: AppModel, kind: HitKind, id: String) {
    val data = model.baseData
    when (kind) {
        HitKind.CHORE -> go(model, BaseSection.CHORES, id, if (model.base.choreView(id)?.let { inDueTab(it, model.store.today) } == true) 0 else 1)
        HitKind.SUPPLY -> go(model, BaseSection.BELONGINGS, id, 0)
        HitKind.ASSET -> go(model, BaseSection.BELONGINGS, id, 2)
        HitKind.MAINTENANCE -> go(model, BaseSection.MAINTENANCE, id, 0)
        HitKind.WARRANTY -> go(model, BaseSection.BELONGINGS, data.warranties.firstOrNull { it.id == id }?.assetId, 2)
        HitKind.REFERENCE -> go(model, BaseSection.NOTES, id)
    }
}

private fun repeatText(r: Recurrence): String {
    if (r.kind == RepeatKind.ONCE) return ""
    val base = if (r.every <= 1) r.kind.label else "Every ${r.every} ${r.kind.unit}s"
    return if (r.kind == RepeatKind.WEEKLY && r.weekdays.isNotEmpty()) "$base on ${r.weekdays.sorted().joinToString(", ") { dayNames[it - 1] }}" else base
}

private fun areaName(data: app.pokeplan.base.BaseData, id: String?): String? = id?.let { a -> data.areas.firstOrNull { it.id == a }?.name }

private fun quantityText(s: Supply): String {
    val unit = if (s.quantityMilli == 1000L && s.unit.length > 3 && s.unit.endsWith("s")) s.unit.dropLast(1) else s.unit
    return Quantity.format(s.quantityMilli) + if (s.quantityMilli != null && unit.isNotEmpty()) " $unit" else ""
}

private fun choreLine(data: app.pokeplan.base.BaseData, v: ChoreView, today: LocalDate): String {
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

private fun scheduleLine(data: app.pokeplan.base.BaseData, v: app.pokeplan.base.ScheduleView): String {
    val s = v.schedule
    val when_ = if (v.state == DueState.PAUSED) "Paused · ${s.nextDue.short()}" else if (v.state == DueState.DUE) "Due today" else "${v.state.label} · ${s.nextDue.short()}"
    return listOfNotNull(when_, data.assets.firstOrNull { it.id == s.assetId }?.name, repeatText(s.recurrence).takeIf { it.isNotEmpty() }).joinToString(" · ")
}

@Composable
private fun FormActions(adding: Boolean, addLabel: String, enabled: Boolean, onSave: () -> Unit, onCancel: (() -> Unit)?) {
    FormSave(if (adding) addLabel else "Save", onSave, accent, enabled = enabled)
    if (!adding && onCancel != null) PixelButton("Cancel", onCancel, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatEditor(r: Recurrence, onChange: (Recurrence) -> Unit) {
    var every by remember { mutableStateOf(r.every.toString()) }
    LaunchedEffect(r.every) { if ((every.toIntOrNull() ?: 1) != r.every) every = r.every.toString() }
    PixelChoice(
        RepeatKind.entries.toList(), r.kind, { it?.label ?: "" }, { k -> if (k != null) onChange(r.copy(kind = k, weekdays = if (k == RepeatKind.WEEKLY) r.weekdays else emptyList())) },
        label = "Repeat", key = accent,
    )
    if (r.kind != RepeatKind.ONCE) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Text("Every", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelField(every, { t -> every = t.filter(Char::isDigit).take(2); onChange(r.copy(every = (every.toIntOrNull() ?: 1).coerceIn(1, BaseRules.EVERY_MAX))) }, Modifier.width(88.dp), singleLine = true, keyboardOptions = numberKeys)
            Text(r.kind.unit + if (r.every == 1) "" else "s", style = MaterialTheme.typography.bodyLarge)
        }
        if (r.kind == RepeatKind.WEEKLY) FormLabel("On")
        if (r.kind == RepeatKind.WEEKLY) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            (1..7).forEach { d -> Chip(dayNames[d - 1], d in r.weekdays, { onChange(r.copy(weekdays = if (d in r.weekdays) r.weekdays - d else (r.weekdays + d).sorted())) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaChips(areas: List<BaseArea>, selected: String?, onPick: (String?) -> Unit) {
    val shown = areas.filter { !it.archived || it.id == selected }
    if (shown.isEmpty()) return
    PixelDropdown(listOf<String?>(null) + shown.map { it.id }, selected, { id -> shown.firstOrNull { it.id == id }?.name ?: "None" }, onPick, Modifier.fillMaxWidth(), key = accent, label = "Area")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetChips(assets: List<Asset>, selected: String?, onPick: (String?) -> Unit) {
    val shown = assets.filter { !it.archived || it.id == selected }
    if (shown.isEmpty()) return
    PixelDropdown(listOf<String?>(null) + shown.map { it.id }, selected, { id -> shown.firstOrNull { it.id == id }?.name ?: "None" }, onPick, Modifier.fillMaxWidth(), key = accent, label = "Item")
}

private val openStates = setOf(ChoreState.OVERDUE, ChoreState.DUE, ChoreState.ANYTIME)

private fun inDueTab(v: ChoreView, today: LocalDate): Boolean = v.state in openStates || ((v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED) && v.entry?.day == today)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoresView(model: AppModel) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    val jump = remember { takeJump(BaseSection.CHORES) }
    val views = BaseRules.views(data, today)
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var area by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf(jump?.id) }
    var showArchived by remember { mutableStateOf(false) }
    val shown = views.filter { area == null || it.chore.areaId == area }
    val due = shown.filter { inDueTab(it, today) }
    val upcoming = shown.filter { v -> v !in due && !((v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED) && v.chore.recurrence.kind == RepeatKind.ONCE) }
    val archived = data.chores.filter { it.status == RecordStatus.ARCHIVED }
    FormBox { ChoreForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ModuleTabs(listOf("Due", "Later", "History"), tab, accent) { tab = it; openId = null }
            val usedAreas = data.areas.filter { a -> !a.archived && data.chores.any { it.areaId == a.id } }
            if (usedAreas.isNotEmpty()) PixelDropdown(listOf<String?>(null) + usedAreas.map { it.id }, area, { id -> usedAreas.firstOrNull { it.id == id }?.name ?: "All areas" }, { area = it }, Modifier.fillMaxWidth(), key = accent)
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
                    val history = BaseRules.history(data).take(60)
                    if (history.isEmpty()) PixelTitle("Nothing here yet")
                    history.forEachIndexed { i, e ->
                        if (i > 0) Rule()
                        val title = data.chores.firstOrNull { it.id == e.choreId }?.title ?: "Chore"
                        val word = when (e.status) {
                            EntryStatus.DONE -> "Done"
                            EntryStatus.SKIPPED -> "Skipped"
                            EntryStatus.DEFERRED -> "Put off" + (e.deferTo?.let { " to ${it.short()}" } ?: "")
                        }
                        RecordRow(title, listOfNotNull("$word · ${e.day.short()}", e.note.takeIf { it.isNotBlank() }).joinToString(" · "))
                    }
                    if (archived.isNotEmpty()) {
                        PixelButton(if (showArchived) "Hide archived" else "Archived (${archived.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        if (showArchived) archived.forEach { c ->
                            RecordRow(c.title, "Archived", trail = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    PixelButton("Restore", { home.setChoreStatus(c.id, RecordStatus.ACTIVE) }, color = accent, compact = true)
                                    PixelIconButton("trash", "Delete ${c.title}", { val before = model.baseData; home.deleteChore(c.id); model.offerUndo("Deleted: ${c.title}") { home.restore(before) } }, Pixel.InkSoft)
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
    val home = model.base
    val finished = v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED
    RecordRow(
        v.chore.title, choreLine(model.baseData, v, today), warn = v.state == ChoreState.OVERDUE,
        lead = { if (!finished && v.state != ChoreState.PAUSED) PixelIconButton("check", "Mark ${v.chore.title} done", { tickChore(model, v) }, accent) },
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
    val home = model.base
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
        if (v.chore.instructions.isNotBlank()) Text(v.chore.instructions, style = MaterialTheme.typography.bodyLarge)
        if (waiting) {
            FormLabel("Put off")
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
        val linked = model.data.tasks.any { it.link?.kind == LinkKind.HOME_CHORE && it.link?.ref == id && it.state != TaskState.DELETED }
        if (linked || (waiting && v.chore.status == RecordStatus.ACTIVE)) Actions {
            if (linked) PixelButton("Remove from Plan", { model.store.data.tasks.firstOrNull { it.link?.ref == id && it.state != TaskState.DELETED }?.let { t -> model.store.unlinkTask(t.id) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            else PixelButton("Show in Plan", { model.store.linkChore(id) }, Modifier.weight(1f), accent, compact = true)
        }
        if (v.chore.status == RecordStatus.ACTIVE) Actions {
            PixelButton(if (v.chore.remind) "Reminder on" else "Remind me", { home.setChoreRemind(id, !v.chore.remind) }, Modifier.weight(1f), if (v.chore.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            PixelButton("Archive", { home.setChoreStatus(id, RecordStatus.ARCHIVED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun ChoreForm(model: AppModel, initial: Chore?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.base
    val data = model.baseData
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
    FormStack {
        FormGroup {
            PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Chore", placeholder = if (adding) "Add a chore" else null, singleLine = true, keyboardOptions = wordKeys)
            if (adding && data.chores.isEmpty() && title.isEmpty()) ExampleChips(listOf("Wipe the kitchen counter", "Take out the trash", "Water the plants")) { title = it }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                RepeatEditor(repeat) { repeat = it }
                DateField(if (repeat.kind == RepeatKind.ONCE) "Date, optional" else "First date", first, { first = it }, today, accent, optional = repeat.kind == RepeatKind.ONCE)
                AreaChips(data.areas, areaId) { areaId = it }
                PixelField(category, { category = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
                PixelField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = "Minutes, optional", singleLine = true, keyboardOptions = numberKeys)
                PixelField(instructions, { instructions = it.take(BaseRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Instructions, optional", minLines = 2, keyboardOptions = sentenceKeys)
            }
        }
        FormActions(adding, "Add", title.isNotBlank(), ::save, onCancel)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongingsView(model: AppModel) {
    val data = model.baseData
    val jump = remember { takeJump(BaseSection.BELONGINGS) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var openId by remember { mutableStateOf(jump?.id) }
    if (tab == 0) FormBox { SupplyForm(model, null, onDone = {}) }
    if (tab == 2) FormBox { AssetForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ModuleTabs(listOf("Stock", "Shopping", "Items"), tab, accent) { tab = it; openId = null }
            when (tab) {
                0 -> StockList(model, openId, { openId = it }) { tab = 1; openId = null }
                1 -> RestockTab(model, jump?.id)
                else -> ItemsTab(model, openId) { openId = it }
            }
        }
    }
}

/** Consumables: quantity, the low mark, and a way on to the shopping list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StockList(model: AppModel, openId: String?, onOpen: (String?) -> Unit, onLowStock: () -> Unit) {
    val home = model.base
    val data = model.baseData
    var showArchived by remember { mutableStateOf(false) }
    val active = data.supplies.filter { !it.archived }.sortedBy { it.name.lowercase() }
    val low = BaseRules.lowSupplies(data, includeQuieted = true)
    val archived = data.supplies.filter { it.archived }
    if (low.isNotEmpty()) PixelButton("Running low (${low.size})", onLowStock, Modifier.fillMaxWidth(), accent, compact = true)
    if (active.isEmpty()) PixelTitle("No supplies yet")
    active.forEachIndexed { i, s ->
        if (i > 0) Rule()
        RecordRow(
            s.name, listOfNotNull("Low".takeIf { BaseRules.isLow(s) }, s.category.takeIf { it.isNotBlank() }, areaName(data, s.areaId)).joinToString(" · "),
            warn = BaseRules.isLow(s),
            trail = { Text(quantityText(s), style = MaterialTheme.typography.titleSmall) },
            onClick = { onOpen(if (openId == s.id) null else s.id) }, onClickLabel = if (openId == s.id) "Hide options" else "Show options",
        )
        if (openId == s.id) SupplyOptions(model, s) { onOpen(null) }
    }
    if (archived.isNotEmpty()) {
        PixelButton(if (showArchived) "Hide archived" else "Archived (${archived.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (showArchived) archived.forEach { s ->
            RecordRow(s.name, "Archived", trail = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Restore", { home.setSupplyArchived(s.id, false) }, color = accent, compact = true)
                    PixelIconButton("trash", "Delete ${s.name}", { val before = model.baseData; home.deleteSupply(s.id); model.offerUndo("Deleted: ${s.name}") { home.restore(before) } }, Pixel.InkSoft)
                }
            })
        }
    }
}

/** Which way an adjust form changes the quantity. */
private enum class StockChange(val label: String) { SET("Set"), ADD("Add"), REMOVE("Remove") }

@Composable
private fun SupplyOptions(model: AppModel, s: Supply, onDone: () -> Unit) {
    val home = model.base
    val data = model.baseData
    var editing by remember(s.id) { mutableStateOf(false) }
    var asking by remember(s.id) { mutableStateOf(false) }
    var adjusting by remember(s.id) { mutableStateOf(false) }
    if (editing) { SupplyForm(model, s, onDone = { editing = false; onDone() }, onCancel = { editing = false }); return }
    if (adjusting) { AdjustStockForm(model, s) { adjusting = false }; return }
    val history = home.recentAdjustments(s.id)
    val detail = listOfNotNull(s.category.takeIf { it.isNotBlank() }, areaName(data, s.areaId), s.lowMilli?.let { "Low at ${Quantity.format(it)}" }).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this supply, its stock history and its restocking entries?", "Delete", onConfirm = { home.deleteSupply(s.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(quantityText(s), style = MaterialTheme.typography.titleMedium)
                if (BaseRules.isLow(s)) Text("Low", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
                else if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            PixelButton("Adjust stock", { adjusting = true }, color = accent, compact = true)
        }
        if (history.isNotEmpty()) Accordion("History", summary = "${history.size} changes") {
            history.forEachIndexed { i, a ->
                if (i > 0) Rule()
                RecordRow(a.reason.ifBlank { "Changed" }, "${Quantity.format(a.beforeMilli)} to ${Quantity.format(a.afterMilli)} · ${home.dayOf(a.atMillis).short()}")
            }
        }
        if (model.baseData.settings.reminders && s.lowMilli != null) Actions {
            PixelButton(if (s.remind) "Low-stock reminder on" else "Tell me when low", { home.setSupplyRemind(s.id, !s.remind) }, Modifier.weight(1f), if (s.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Archive", { home.setSupplyArchived(s.id, true); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

/** Set, add or remove stock with an optional reason; the change is one `StockAdjustment`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustStockForm(model: AppModel, s: Supply, onDone: () -> Unit) {
    val home = model.base
    var change by remember(s.id) { mutableStateOf(StockChange.SET) }
    var amount by remember(s.id) { mutableStateOf("") }
    var reason by remember(s.id) { mutableStateOf("") }
    var message by remember(s.id) { mutableStateOf<String?>(null) }
    fun save() {
        if (amount.isBlank()) { message = "Enter a quantity."; return }
        val milli = Quantity.parse(amount)
        if (milli == null || milli == 0L) { message = "Check the quantity."; return }
        val before = model.baseData
        val refusal = when (change) {
            StockChange.SET -> home.setQuantity(s.id, milli, reason)
            StockChange.ADD -> home.adjust(s.id, milli, reason)
            StockChange.REMOVE -> home.adjust(s.id, -milli, reason)
        }
        if (refusal != null) { message = refusal; return }
        model.haptic()
        model.offerUndo("Stock: ${s.name}") { home.restore(before) }
        onDone()
    }
    val unit = if (s.unit.isNotEmpty()) " (${s.unit})" else ""
    FormStack(Modifier.padding(bottom = Spacing.S)) {
        FormGroup {
            FormLabel("Now ${quantityText(s)}")
            PixelChoice(StockChange.entries.toList(), change, { it?.label ?: "" }, { if (it != null) change = it }, key = accent)
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(),
                label = when (change) { StockChange.SET -> "New quantity$unit"; StockChange.ADD -> "How much to add$unit"; StockChange.REMOVE -> "How much to remove$unit" },
                singleLine = true, keyboardOptions = decimalKeys,
            )
            FormLabel("Reason, optional")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                BaseRules.ADJUSTMENT_REASONS.forEach { r -> Chip(r, reason == r, onClick = { reason = if (reason == r) "" else r }) }
            }
        }
        Actions {
            PixelButton(if (change == StockChange.SET) "Set stock" else "Save", ::save, Modifier.weight(1f), accent, compact = true)
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        if (change == StockChange.SET) PixelButton("Count as unknown", {
            val before = model.baseData
            if (home.setQuantity(s.id, null, reason) == null) {
                model.haptic(); model.offerUndo("Stock: ${s.name}") { home.restore(before) }; onDone()
            }
        }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

/** The shopping list: low supplies and hand-written lines in one list, under their area. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RestockTab(model: AppModel, focus: String?) {
    val home = model.base
    val data = model.baseData
    val groups = BaseRules.restockGroups(data)
    val stock = data.supplies.filter { !it.archived }.sortedBy { it.name.lowercase() }
    var name by remember { mutableStateOf("") }
    var supplyId by remember { mutableStateOf<String?>(null) }
    val focusKey = focus?.let { id -> groups.flatMap { it.lines }.firstOrNull { it.supply?.id == id }?.let { it.entry?.id ?: "low:${it.supply?.id}" } }
    var askingKey by remember { mutableStateOf(focusKey) }
    var quantity by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf<String?>(null) }
    fun tick(line: RestockLine) {
        val before = model.baseData
        val problem = home.bought(entryId = line.entry?.id, supplyId = line.supply?.id)
        if (problem != null) refused = problem
        else { model.haptic(); model.offerUndo("Bought: ${line.name}") { home.restore(before) } }
    }
    if (groups.isEmpty()) PixelTitle("Nothing to restock")
    groups.forEach { group ->
        if (groups.size > 1) SectionLabel(group.title)
        group.lines.forEachIndexed { i, line ->
            if (i > 0) Rule()
            val key = line.entry?.id ?: "low:${line.supply?.id}"
            RecordRow(
                line.name,
                listOfNotNull("Low".takeIf { line.low }, line.supply?.let { quantityText(it) }, line.supply?.let { areaName(data, it.areaId) }).joinToString(" · "),
                warn = line.low,
                lead = {
                    PixelIconButton(
                        "check", "Bought: ${line.name}",
                        { if (line.supply == null) tick(line) else { askingKey = key; quantity = "" } },
                        accent,
                    )
                },
                trail = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        val lowSupply = line.supply
                        val entry = line.entry
                        if (line.low && lowSupply != null && !lowSupply.lowDismissed) PixelButton("Not now", { home.dismissLow(lowSupply.id) }, color = Pixel.InkSoft, compact = true)
                        if (entry != null) PixelIconButton("close", "Clear ${line.name} from the list", { home.removeRestock(entry.id) }, Pixel.InkSoft)
                    }
                },
            )
            if (askingKey == key) {
                val supply = line.supply
                val needsNew = supply != null && (line.low || supply.quantityMilli == null)
                val unit = if (supply != null && supply.unit.isNotEmpty()) " (${supply.unit})" else ""
                PixelField(
                    quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(),
                    label = if (needsNew) "How much is there now$unit" else "How much did you buy$unit, blank to just tick it off",
                    singleLine = true, keyboardOptions = decimalKeys,
                )
                Actions {
                    PixelButton("Bought", {
                        val milli = if (quantity.isBlank()) null else Quantity.parse(quantity)
                        if (quantity.isNotBlank() && milli == null) refused = "Check the quantity."
                        else {
                            val before = model.baseData
                            val problem = home.bought(
                                entryId = line.entry?.id, supplyId = supply?.id,
                                addMilli = if (needsNew) null else milli, newQuantityMilli = if (needsNew) milli else null,
                            )
                            if (problem != null) refused = problem
                            else { askingKey = null; quantity = ""; model.haptic(); model.offerUndo("Bought: ${line.name}") { home.restore(before) } }
                        }
                    }, Modifier.weight(1f), accent, compact = true)
                    PixelButton("Cancel", { askingKey = null; quantity = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
    FormGroup {
        PixelField(name, { name = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Add to the shopping list", placeholder = "Add an item", singleLine = true, keyboardOptions = wordKeys)
        if (stock.isNotEmpty()) PixelDropdown(
            listOf<String?>(null) + stock.map { it.id }, supplyId, { id -> stock.firstOrNull { it.id == id }?.name ?: "Not stock" }, { supplyId = it },
            Modifier.fillMaxWidth(), key = accent, label = "Count in stock",
        )
        PixelButton("Add", {
            if (home.addRestock(name, supplyId) == null) refused = "That supply is already on the list."
            else { name = ""; supplyId = null; refused = null }
        }, color = accent, enabled = name.isNotBlank() || supplyId != null)
    }
    refused?.let { Report(model, it, NoticeTone.WARNING) }
}

/** Durable things: the assets, their warranties, maintenance and history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ItemsTab(model: AppModel, openId: String?, onOpen: (String?) -> Unit) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    var showArchived by remember { mutableStateOf(false) }
    val assets = data.assets.filter { !it.archived }
    val archivedAssets = data.assets.filter { it.archived }
    if (assets.isEmpty()) PixelTitle("No items yet")
    assets.forEachIndexed { i, a ->
        if (i > 0) Rule()
        val warranties = data.warranties.filter { it.assetId == a.id }
        val states = warranties.map { BaseRules.warrantyState(it, today) }
        val word = if (WarrantyState.ENDING in states) "Warranty ends soon" else if (WarrantyState.ACTIVE in states || WarrantyState.UNDATED in states) "Covered" else if (states.isNotEmpty()) "Warranty expired" else null
        RecordRow(
            a.name, listOfNotNull(listOf(a.brand, a.model).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }, areaName(data, a.areaId), word).joinToString(" · "),
            warn = WarrantyState.ENDING in states,
            onClick = { onOpen(if (openId == a.id) null else a.id) }, onClickLabel = if (openId == a.id) "Hide details" else "Show details",
        )
        if (openId == a.id) AssetDetail(model, a) { onOpen(null) }
    }
    if (archivedAssets.isNotEmpty()) {
        PixelButton(if (showArchived) "Hide archived" else "Archived (${archivedAssets.size})", { showArchived = !showArchived }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (showArchived) archivedAssets.forEach { a ->
            RecordRow(a.name, "Archived", trail = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Restore", { home.setAssetArchived(a.id, false) }, color = accent, compact = true)
                    PixelIconButton("trash", "Delete ${a.name}", { val before = model.baseData; home.deleteAsset(a.id); model.offerUndo("Deleted: ${a.name}") { home.restore(before) } }, Pixel.InkSoft)
                }
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplyForm(model: AppModel, initial: Supply?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.base
    val data = model.baseData
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
    FormStack {
        FormGroup {
            PixelField(name, { name = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Supply", placeholder = if (adding) "Add a supply" else null, singleLine = true, keyboardOptions = wordKeys)
            if (adding && data.supplies.isEmpty() && name.isEmpty()) ExampleChips(listOf("Dish soap", "Light bulbs", "Trash bags")) { name = it }
            if (adding) PixelField(quantity, { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(), label = "Quantity, blank if unknown", singleLine = true, keyboardOptions = decimalKeys)
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelDropdown(listOf<String?>(null) + (data.settings.units + unit).filter { it.isNotBlank() }.distinct(), unit.ifEmpty { null }, { it ?: "None" }, { unit = it ?: "" }, Modifier.fillMaxWidth(), key = accent, label = "Unit")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                    PixelField(newUnit, { newUnit = it.take(16) }, Modifier.weight(1f), label = "Another unit", singleLine = true)
                    PixelButton("Add unit", { home.addUnit(newUnit); if (newUnit.isNotBlank()) { unit = newUnit.trim(); newUnit = "" } }, color = Pixel.InkSoft, enabled = newUnit.isNotBlank(), compact = true)
                }
                PixelField(low, { low = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, Modifier.fillMaxWidth(), label = "Low at or under, blank for none", singleLine = true, keyboardOptions = decimalKeys)
                AreaChips(data.areas, areaId) { areaId = it }
                PixelField(category, { category = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
                PixelField(notes, { notes = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", minLines = 2, keyboardOptions = sentenceKeys)
            }
        }
        FormActions(adding, "Add", name.isNotBlank(), ::save, onCancel)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun MaintenanceView(model: AppModel) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    val jump = remember { takeJump(BaseSection.MAINTENANCE) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var openId by remember { mutableStateOf(jump?.id) }
    var logging by remember { mutableStateOf(false) }
    val views = BaseRules.schedules(data, today)
    val history = BaseRules.maintenanceHistory(data).take(60)
    if (tab == 0) FormBox { ScheduleForm(model, null, onDone = {}) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ModuleTabs(listOf("Later", "History"), tab, accent) { tab = it; openId = null }
            when (tab) {
                0 -> {
                    if (views.isEmpty()) PixelTitle("No maintenance yet")
                    views.forEachIndexed { i, v ->
                        if (i > 0) Rule()
                        RecordRow(
                            v.schedule.title, scheduleLine(data, v), warn = v.state == DueState.OVERDUE,
                            lead = { if (v.state != DueState.PAUSED) PixelIconButton("check", "Mark ${v.schedule.title} done", { tickMaintenance(model, v.schedule) }, accent) },
                            trail = { if (v.state == DueState.PAUSED) PixelButton("Resume", { home.setScheduleStatus(v.schedule.id, RecordStatus.ACTIVE) }, color = Pixel.InkSoft, compact = true) },
                            onClick = { openId = if (openId == v.schedule.id) null else v.schedule.id }, onClickLabel = if (openId == v.schedule.id) "Hide options" else "Show options",
                        )
                        if (openId == v.schedule.id) ScheduleOptions(model, v.schedule) { openId = null }
                    }
                    PixelButton(if (logging) "Hide the form" else "Log work done", { logging = !logging }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    if (logging) LogWorkForm(model) { logging = false }
                }
                else -> {
                    if (history.isEmpty()) PixelTitle("Nothing here yet")
                    var editingRec by remember { mutableStateOf<String?>(null) }
                    history.forEachIndexed { i, r ->
                        if (i > 0) Rule()
                        var asking by remember(r.id) { mutableStateOf(false) }
                        RecordRow(
                            r.title, listOfNotNull(r.day.short(), data.assets.firstOrNull { it.id == r.assetId }?.name, r.notes.takeIf { it.isNotBlank() }, r.cost.takeIf { it.isNotBlank() }).joinToString(" · "),
                            trail = {
                                PixelIconButton("pencil", "Edit ${r.title}", { editingRec = if (editingRec == r.id) null else r.id }, Pixel.InkSoft)
                                PixelIconButton("trash", "Remove ${r.title} from the history", { asking = true }, Pixel.InkSoft)
                            },
                        )
                        if (editingRec == r.id) MaintenanceRecordForm(model, r) { editingRec = null }
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
    val home = model.base
    var editing by remember(s.id) { mutableStateOf(false) }
    var asking by remember(s.id) { mutableStateOf(false) }
    var notes by remember(s.id) { mutableStateOf("") }
    var cost by remember(s.id) { mutableStateOf("") }
    if (editing) { ScheduleForm(model, s, onDone = { editing = false; onDone() }, onCancel = { editing = false }); return }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this schedule? The work it recorded stays in the history.", "Delete", onConfirm = { home.deleteSchedule(s.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        if (s.instructions.isNotBlank()) Text(s.instructions, style = MaterialTheme.typography.bodyLarge)
        PixelField(notes, { notes = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", keyboardOptions = sentenceKeys)
        PixelField(cost, { cost = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Cost, optional", singleLine = true)
        Actions {
            if (s.status != RecordStatus.PAUSED) PixelButton("Done", { tickMaintenance(model, s, notes, cost); onDone() }, Modifier.weight(1f), accent, compact = true)
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        if (model.baseData.settings.reminders && s.status == RecordStatus.ACTIVE) Actions {
            PixelButton(if (s.remind) "Reminder on" else "Remind me", { home.setScheduleRemind(s.id, !s.remind) }, Modifier.weight(1f), if (s.remind) accent else Pixel.InkSoft, compact = true)
        }
        Actions {
            if (s.status == RecordStatus.PAUSED) PixelButton("Resume", { home.setScheduleStatus(s.id, RecordStatus.ACTIVE); onDone() }, Modifier.weight(1f), accent, compact = true)
            else PixelButton("Pause", { home.setScheduleStatus(s.id, RecordStatus.PAUSED); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleForm(model: AppModel, initial: MaintenanceSchedule?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    val adding = initial == null
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var due by remember { mutableStateOf<LocalDate?>(initial?.nextDue) }
    var repeat by remember { mutableStateOf(initial?.recurrence ?: Recurrence()) }
    var policy by remember { mutableStateOf(initial?.policy ?: SchedulePolicy.FIXED) }
    var assetId by remember { mutableStateOf(initial?.assetId) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var more by remember { mutableStateOf(!adding) }
    var message by remember { mutableStateOf<String?>(null) }
    fun save() {
        val out = if (initial == null) home.addSchedule(title, due, repeat, policy, assetId, instructions)
        else home.updateSchedule(initial.copy(title = title, nextDue = due ?: initial.nextDue, recurrence = repeat, policy = policy, assetId = assetId, instructions = instructions))
        if (out.record == null) { message = out.refused ?: "That could not be saved."; return }
        message = null
        if (adding) { title = ""; due = null; repeat = Recurrence(); policy = SchedulePolicy.FIXED; assetId = null; instructions = ""; more = false; model.haptic() }
        onDone()
    }
    FormStack {
        FormGroup {
            PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Maintenance", placeholder = if (adding) "Add maintenance" else null, singleLine = true, keyboardOptions = wordKeys)
            if (adding && data.schedules.isEmpty() && title.isEmpty()) ExampleChips(listOf("Change the aircon filter", "Test smoke alarm", "Descale the kettle")) { title = it }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                DateField(if (adding) "First due, optional" else "Next due", due, { due = it }, today, accent, optional = adding)
                RepeatEditor(repeat) { repeat = it }
                if (repeat.kind != RepeatKind.ONCE) PixelChoice(SchedulePolicy.entries.toList(), policy, { it?.label ?: "" }, { p -> if (p != null) policy = p }, label = "Next date counts from", key = accent)
                AssetChips(data.assets, assetId) { assetId = it }
                PixelField(instructions, { instructions = it.take(BaseRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Instructions, optional", minLines = 2, keyboardOptions = sentenceKeys)
            }
        }
        FormActions(adding, "Add", title.isNotBlank() && (adding || due != null), ::save, onCancel)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun MaintenanceRecordForm(model: AppModel, r: MaintenanceRecord, onDone: () -> Unit) {
    val today = model.store.today
    var title by remember(r.id) { mutableStateOf(r.title) }
    var day by remember(r.id) { mutableStateOf<LocalDate?>(r.day) }
    var assetId by remember(r.id) { mutableStateOf(r.assetId) }
    var notes by remember(r.id) { mutableStateOf(r.notes) }
    var cost by remember(r.id) { mutableStateOf(r.cost) }
    var message by remember(r.id) { mutableStateOf<String?>(null) }
    var more by remember(r.id) { mutableStateOf(r.notes.isNotBlank() || r.cost.isNotBlank()) }
    FormStack(Modifier.padding(bottom = Spacing.S)) {
        FormGroup {
            PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "What was done", singleLine = true, keyboardOptions = wordKeys)
            DateField("Day", day, { day = it }, today, accent, past = true)
            AssetChips(model.baseData.assets, assetId) { assetId = it }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(notes, { notes = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", keyboardOptions = sentenceKeys)
                PixelField(cost, { cost = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Cost, optional", singleLine = true)
            }
        }
        FormActions(false, "Save", title.isNotBlank() && day != null, {
            val d = day
            if (d == null) message = "Choose the day."
            else {
                val out = model.base.updateMaintenanceRecord(r.copy(title = title, day = d, assetId = assetId, notes = notes, cost = cost))
                if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
            }
        }, onDone)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun LogWorkForm(model: AppModel, onDone: () -> Unit) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<LocalDate?>(today) }
    var assetId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    FormStack {
        FormGroup {
            PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "What was done", singleLine = true, keyboardOptions = wordKeys)
            DateField("Day", day, { day = it }, today, accent, past = true)
            AssetChips(data.assets, assetId) { assetId = it }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(notes, { notes = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", keyboardOptions = sentenceKeys)
                PixelField(cost, { cost = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Cost, optional", singleLine = true)
            }
        }
        FormSave("Save", {
            val d = day
            if (d == null) message = "Choose the day."
            else {
                val out = home.logMaintenance(title, assetId, notes, cost, d)
                if (out.record == null) message = out.refused ?: "That could not be saved."
                else { model.haptic(); onDone() }
            }
        }, accent, enabled = title.isNotBlank() && day != null)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun AssetDetail(model: AppModel, a: Asset, onDone: () -> Unit) {
    val home = model.base
    val data = model.baseData
    val today = model.store.today
    var editing by remember(a.id) { mutableStateOf(false) }
    var asking by remember(a.id) { mutableStateOf(false) }
    var warranty by remember(a.id) { mutableStateOf<String?>(null) }
    var editingRecord by remember(a.id) { mutableStateOf<String?>(null) }
    if (editing) { AssetForm(model, a, onDone = { editing = false }, onCancel = { editing = false }); return }
    val warranties = data.warranties.filter { it.assetId == a.id }
    val schedules = BaseRules.schedules(data, today).filter { it.schedule.assetId == a.id }
    val history = BaseRules.maintenanceHistory(data, a.id).take(10)
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
            val state = BaseRules.warrantyState(w, today)
            RecordRow(
                listOf(w.provider, w.coverage).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Warranty" },
                listOfNotNull(state.label, w.expiryDay?.let { "until ${it.short()}" }, w.reference.takeIf { it.isNotBlank() }).joinToString(" · "), warn = state == WarrantyState.ENDING,
                trail = { PixelIconButton("trash", "Delete this warranty", { val before = model.baseData; home.deleteWarranty(w.id); model.offerUndo("Deleted: warranty") { home.restore(before) } }, Pixel.InkSoft) },
                onClick = { warranty = if (warranty == w.id) null else w.id }, onClickLabel = "Edit this warranty",
            )
            if (warranty == w.id) WarrantyForm(model, a.id, w) { warranty = null }
            if (model.baseData.settings.reminders && w.expiryDay != null) PixelButton(if (w.remind) "Reminder on" else "Remind me before it ends", { home.setWarrantyRemind(w.id, !w.remind) }, Modifier.fillMaxWidth(), if (w.remind) accent else Pixel.InkSoft, compact = true)
        }
        if (warranty == "") WarrantyForm(model, a.id, null) { warranty = null }
        else PixelButton("Add warranty", { warranty = "" }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (schedules.isNotEmpty()) SectionLabel("Maintenance")
        schedules.forEach { v -> RecordRow(v.schedule.title, scheduleLine(data, v).substringBefore(" · ${data.assets.firstOrNull { it.id == a.id }?.name}"), warn = v.state == DueState.OVERDUE) }
        if (history.isNotEmpty()) SectionLabel("History")
        history.forEach { r ->
            RecordRow(
                r.title, listOfNotNull(r.day.short(), r.notes.takeIf { it.isNotBlank() }, r.cost.takeIf { it.isNotBlank() }).joinToString(" · "),
                trail = { PixelIconButton("pencil", "Edit ${r.title}", { editingRecord = if (editingRecord == r.id) null else r.id }, Pixel.InkSoft) },
            )
            if (editingRecord == r.id) MaintenanceRecordForm(model, r) { editingRecord = null }
        }
        Actions {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Archive", { home.setAssetArchived(a.id, true); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun WarrantyForm(model: AppModel, assetId: String, initial: Warranty?, onDone: () -> Unit) {
    val today = model.store.today
    var provider by remember { mutableStateOf(initial?.provider.orEmpty()) }
    var coverage by remember { mutableStateOf(initial?.coverage.orEmpty()) }
    var start by remember { mutableStateOf(initial?.startDay) }
    var end by remember { mutableStateOf(initial?.expiryDay) }
    var reference by remember { mutableStateOf(initial?.reference.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(initial != null && (initial.coverage.isNotBlank() || initial.startDay != null || initial.reference.isNotBlank())) }
    FormStack {
        FormGroup {
            PixelField(provider, { provider = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Provider", singleLine = true, keyboardOptions = wordKeys)
            DateField("Ends, optional", end, { end = it }, today, accent, optional = true)
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(coverage, { coverage = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Coverage, optional", keyboardOptions = sentenceKeys)
                DateField("Starts, optional", start, { start = it }, today, accent, optional = true, past = true)
                PixelField(reference, { reference = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Reference, optional", singleLine = true)
            }
        }
        FormActions(false, "Save", true, {
            val out = if (initial == null) model.base.addWarranty(assetId, provider, coverage, start, end, reference)
            else model.base.updateWarranty(initial.copy(provider = provider, coverage = coverage, startDay = start, expiryDay = end, reference = reference))
            if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
        }, onDone)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun AssetForm(model: AppModel, initial: Asset?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val home = model.base
    val data = model.baseData
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
    FormStack {
        FormGroup {
            PixelField(name, { name = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Item", placeholder = if (adding) "Add an item" else null, singleLine = true, keyboardOptions = wordKeys)
            if (adding && data.assets.isEmpty() && name.isEmpty()) ExampleChips(listOf("Rice cooker", "Washing machine", "Bike")) { name = it }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(category, { category = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(brand, { brand = it.take(BaseRules.TITLE_MAX) }, Modifier.weight(1f), label = "Brand, optional", singleLine = true, keyboardOptions = wordKeys)
                    PixelField(modelName, { modelName = it.take(BaseRules.TITLE_MAX) }, Modifier.weight(1f), label = "Model, optional", singleLine = true)
                }
                PixelField(serial, { serial = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Serial number, optional", singleLine = true)
                DateField("Bought, optional", bought, { bought = it }, today, accent, optional = true, past = true)
                AreaChips(data.areas, areaId) { areaId = it }
                PixelField(notes, { notes = it.take(BaseRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes, optional", minLines = 2, keyboardOptions = sentenceKeys)
            }
        }
        FormActions(adding, "Add", name.isNotBlank(), ::save, onCancel)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun NotesView(model: AppModel) {
    val home = model.base
    val data = model.baseData
    val jump = remember { takeJump(BaseSection.NOTES) }
    var title by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf(jump?.id) }
    var message by remember { mutableStateOf<String?>(null) }
    val matching = if (query.isBlank()) null else home.search(query).filter { it.kind == HitKind.REFERENCE }.map { it.id }.toSet()
    val notes = data.references.filter { matching == null || it.id in matching }.sortedByDescending { it.updatedMillis }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (data.references.isNotEmpty()) PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Search notes", singleLine = true)
            FormGroup {
                PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Note", placeholder = "Add a note", singleLine = true, keyboardOptions = sentenceKeys)
                FormSave("Add", {
                    val out = home.addReference(title)
                    if (out.record == null) message = out.refused else { title = ""; message = null; openId = out.record!!.id; model.haptic() }
                }, accent, enabled = title.isNotBlank())
            }
            message?.let { Report(model, it, NoticeTone.WARNING) }
            if (data.references.isEmpty() && title.isEmpty()) ExampleChips(listOf("Landlord contact", "Wi-Fi password", "Condo admin hours")) { title = it }
            if (notes.isEmpty() && data.references.isNotEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            notes.forEachIndexed { i, n ->
                if (i > 0) Rule()
                RecordRow(
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
    val home = model.base
    val data = model.baseData
    var title by remember(n.id) { mutableStateOf(n.title) }
    var text by remember(n.id) { mutableStateOf(n.text) }
    var category by remember(n.id) { mutableStateOf(n.category) }
    var areaId by remember(n.id) { mutableStateOf(n.areaId) }
    var assetId by remember(n.id) { mutableStateOf(n.assetId) }
    var asking by remember(n.id) { mutableStateOf(false) }
    var message by remember(n.id) { mutableStateOf<String?>(null) }
    var more by remember(n.id) { mutableStateOf(n.category.isNotBlank() || n.areaId != null || n.assetId != null) }
    FormStack(Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this note?", "Delete", onConfirm = { home.deleteReference(n.id); onDone() }, onKeep = { asking = false })
            return@FormStack
        }
        FormGroup {
            PixelField(title, { title = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(text, { text = it.take(BaseRules.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 5, keyboardOptions = sentenceKeys)
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(category, { category = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Category, optional", singleLine = true, keyboardOptions = sentenceKeys)
                AreaChips(data.areas, areaId) { areaId = it }
                AssetChips(data.assets, assetId) { assetId = it }
            }
        }
        FormSave("Save", {
            val out = home.updateReference(n.copy(title = title, text = text, category = category, areaId = areaId, assetId = assetId))
            if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
        }, accent, enabled = title.isNotBlank())
        Actions {
            PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseSettingsView(model: AppModel) {
    val home = model.base
    val data = model.baseData
    val settings = data.settings
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<BaseKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Habitat Discovery")
            ToggleRow("Find Pokémon from finished work", settings.progression) { home.setProgression(it) }
        }
    }
    if (settings.reminders) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Reminders")
            run {
                PixelChoice(listOf(8, 9, 12, 18, 20), settings.reminderHour, { "${it.toString().padStart(2, '0')}:00" }, { h -> if (h != null) home.setReminderHour(h) }, label = "At", key = accent)
                val maintenanceLeads = listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before")
                PixelChoice(maintenanceLeads.map { it.first }, settings.maintenanceLeadDays, { d -> maintenanceLeads.firstOrNull { it.first == d }?.second ?: "" }, { d -> if (d != null) home.setMaintenanceLeadDays(d) }, label = "Maintenance", key = accent)
                val warrantyLeads = listOf(7 to "A week before", 14 to "2 weeks", 30 to "A month", 60 to "2 months")
                PixelChoice(warrantyLeads.map { it.first }, settings.warrantyLeadDays, { d -> warrantyLeads.firstOrNull { it.first == d }?.second ?: "" }, { d -> if (d != null) home.setWarrantyLeadDays(d) }, label = "Warranties", key = accent)
                ToggleRow("Name it", settings.reminderNames) { home.setReminderNames(it) }
            }
        }
    }
    AreasBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-home.json", home.export()) }, Modifier.fillMaxWidth(), accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    BaseKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-home-${k.name.lowercase()}.json", home.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            }
            note?.let { Report(model, it) }
            BaseKinds.entries.forEach { k ->
                if (asking == k) Confirm(
                    if (k == BaseKinds.ASSETS) "Erase all belongings with their maintenance and warranties? This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                    "Erase", "Keep them", onConfirm = { home.erase(k); asking = null }, onKeep = { asking = null },
                ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) Confirm("Erase every Base record? This cannot be undone.", "Erase all", "Keep them", onConfirm = { home.eraseAll(); askingAll = false }, onKeep = { askingAll = false })
            else PixelButton("Erase all Base records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
}

@Composable
internal fun AreasBox(model: AppModel, startOpen: String? = null, startAsking: Boolean = false) {
    val home = model.base
    val data = model.baseData
    var name by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf(startOpen) }
    var renaming by remember { mutableStateOf(data.areas.firstOrNull { it.id == startOpen }?.name.orEmpty()) }
    var asking by remember { mutableStateOf(if (startAsking) startOpen else null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Areas")
            data.areas.forEachIndexed { i, a ->
                if (i > 0) Rule()
                RecordRow(
                    a.name, if (a.archived) "Archived" else "",
                    onClick = { openId = if (openId == a.id) null else a.id; renaming = a.name; asking = null }, onClickLabel = if (openId == a.id) "Hide options" else "Show options",
                    trail = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelIconButton("pencil", "Rename ${a.name}", { if (openId == a.id && asking == null) openId = null else { openId = a.id; renaming = a.name; asking = null } }, Pixel.Tone)
                            PixelIconButton("trash", "Delete ${a.name}", { openId = a.id; asking = a.id }, Pixel.InkSoft)
                        }
                    },
                )
                if (openId == a.id) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                    if (asking == a.id) Confirm("Delete this area? What names it stays, with no area.", "Delete", onConfirm = { home.deleteArea(a.id); openId = null; asking = null }, onKeep = { asking = null })
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                            PixelField(renaming, { renaming = it.take(BaseRules.TITLE_MAX) }, Modifier.weight(1f), label = "Name", singleLine = true, keyboardOptions = wordKeys)
                            PixelButton("Rename", { home.renameArea(a.id, renaming) }, color = accent, enabled = renaming.isNotBlank() && renaming.trim() != a.name, compact = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelIconButton("arrow-up", "Move ${a.name} up", { home.moveArea(a.id, true) }, enabled = i > 0)
                            PixelIconButton("arrow-down", "Move ${a.name} down", { home.moveArea(a.id, false) }, enabled = i < data.areas.lastIndex)
                        }
                        Actions {
                            PixelButton(if (a.archived) "Restore" else "Archive", { home.setAreaArchived(a.id, !a.archived); openId = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
            FormGroup {
                PixelField(name, { name = it.take(BaseRules.TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Area", placeholder = "Add an area", singleLine = true, keyboardOptions = wordKeys)
                FormSave("Add", { if (home.addArea(name) != null) name = "" }, accent, enabled = name.isNotBlank())
            }
        }
    }
}
