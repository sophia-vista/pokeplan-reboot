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
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUp
import app.pokeplan.link.FollowUpState
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.HitKind
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkKinds
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Person
import app.pokeplan.link.Sort
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
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
 * The connection module's screens (docs/pokelife `MODULES/LINK.md`): people with their notes, important dates, follow-ups, and settings. Utility
 * presentation (LINK.md section 10): a few panels, plain rows with a 48 dp target, no box per person. Nothing here judges or counts: no meter, no
 * streak, no "last contacted", no urgency colour; a follow-up that is late is just Due, and no person is ever contacted by the app.
 */
enum class LinkSection(val label: String, val icon: String) {
    PEOPLE("People", "friend-ball"), DATES("Dates", "timer-ball"), FOLLOW_UPS("Follow-ups", "member-card"), SETTINGS("Settings", "storage-key"),
}

private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.LINK.accent

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val phoneKeys = KeyboardOptions(keyboardType = KeyboardType.Phone)
private val emailKeys = KeyboardOptions(keyboardType = KeyboardType.Email)
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
private fun LRow(
    title: String, detail: String = "", lead: @Composable () -> Unit = {}, trail: @Composable () -> Unit = {},
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
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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

@Composable
private fun Actions(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically, content = content)
}

private fun parseDay(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, today: LocalDate) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    LaunchedEffect(value) { if (value != parseDay(text)) text = value?.toString().orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(
            text, { t -> text = t.filter { it.isDigit() || it == '-' }.take(10); onChange(parseDay(text)) }, Modifier.fillMaxWidth(),
            label = label, placeholder = today.toString(), singleLine = true, keyboardOptions = numberKeys,
            error = if (text.length == 10 && parseDay(text) == null) "Write it like $today." else null,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(0 to "Today", 1 to "Tomorrow", 7 to "In a week", 30 to "In a month").forEach { (d, l) ->
                val day = today.plus(d, DateTimeUnit.DAY)
                Chip(l, value == day, { text = day.toString(); onChange(day) })
            }
            Chip("None", value == null, { text = ""; onChange(null) })
        }
    }
}

// ----- Moving between sections ------------------------------------------------------------------------------------------------

private class LinkJump(val section: LinkSection, val id: String? = null, val tab: Int? = null)

/** Where the next section should open (a search hit, an overview line); read once when the section appears. */
private var pendingLinkJump: LinkJump? = null

private fun go(model: AppModel, section: LinkSection, id: String? = null, tab: Int? = null) {
    pendingLinkJump = LinkJump(section, id, tab)
    model.linkSection = section
}

private fun takeJump(section: LinkSection): LinkJump? = pendingLinkJump?.takeIf { it.section == section }?.also { pendingLinkJump = null }

/** The line under Link's card in Modules: how many follow-ups are due today, or what the module is for. Never a name or a word of a record. */
internal fun linkSubtitle(model: AppModel): String {
    val today = model.store.today
    val n = model.linkData.followUps.count { LinkRules.state(it, today) == FollowUpState.DUE }
    return if (n > 0) "$n due" else "people, dates, follow-ups"
}

@Composable
internal fun LinkScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenSwitch(model.linkSection, depth = { if (it == null) 0 else 1 }) { section -> LinkPage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LinkPage(model: AppModel, section: LinkSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        LinkMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.linkSection = null }, icon = section.icon, color = Module.LINK.look.header) {
        when (section) {
            LinkSection.PEOPLE -> PeopleView(model)
            LinkSection.DATES -> DatesView(model)
            LinkSection.FOLLOW_UPS -> FollowUpsView(model)
            LinkSection.SETTINGS -> LinkSettingsView(model)
        }
    }
}

// ----- Menu -------------------------------------------------------------------------------------------------------------------

@Composable
private fun LinkMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.linkData
    val today = model.store.today
    val tint = ModuleAccent.LINK.accent
    val open = LinkRules.open(data, today)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.LINK.label, onBack = onBack, color = Module.LINK.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            RewardBox(model)
            TutorialCard(model, Tutorials.LINK_POSTCARD, linkRecords = data.people.size + data.dates.size + data.followUps.size)
            LinkOverview(model)
            LinkSearch(model)
            MenuGrid(
                listOf(
                    MenuEntry(LinkSection.PEOPLE.name, LinkSection.PEOPLE.icon, "People", if (data.people.isEmpty()) "add one" else "${data.people.size} people", tint = tint),
                    MenuEntry(LinkSection.DATES.name, LinkSection.DATES.icon, "Dates", if (data.dates.isEmpty()) "add one" else "${LinkRules.upcoming(data, today).size} soon", tint = tint),
                    MenuEntry(LinkSection.FOLLOW_UPS.name, LinkSection.FOLLOW_UPS.icon, "Follow-ups", if (data.followUps.isEmpty()) "add one" else "${open.size} open", tint = tint),
                    MenuEntry(LinkSection.SETTINGS.name, LinkSection.SETTINGS.icon, "Settings", "Postcard Trail, your data", tint = Pixel.InkSoft),
                ),
            ) { key -> model.linkSection = LinkSection.valueOf(key) }
            Text(
                if (data.settings.postcardTrail) "Postcard Trail is on · Postcard ${model.store.linkEggMeter()} of ${RewardEngine.LINK_EGG_METER_SIZE}" else "Postcard Trail is off",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            LinkPartyPanel(model, openPokemon)
        }
    }
}

// ----- Overview ---------------------------------------------------------------------------------------------------------------

private fun dateLine(u: app.pokeplan.link.Upcoming): String {
    val years = u.years?.let { n -> if (u.date.kind == DateKind.BIRTHDAY) "turns $n" else "$n years" }
    return listOfNotNull(u.day.short(), LinkRules.inDays(u.daysAway), years).joinToString(" · ")
}

private fun dueText(f: FollowUp, today: LocalDate): String? = f.due?.let { if (it < today) "Due since ${it.short()}" else if (it == today) "Due today" else "Due ${it.short()}" }

private fun followUpLine(data: app.pokeplan.link.LinkData, f: FollowUp, today: LocalDate): String {
    val who = f.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
    return listOfNotNull(who, dueText(f, today)).joinToString(" · ")
}

/** Only what needs attention (LINK.md 2.4, no pressure): follow-ups that are due and dates coming soon; never a count of everything and never a nudge to contact. */
@Composable
private fun LinkOverview(model: AppModel) {
    val data = model.linkData
    val link = model.link
    val today = model.store.today
    val due = LinkRules.open(data, today).filter { LinkRules.state(it, today) == FollowUpState.DUE }
    val soon = LinkRules.upcoming(data, today, 14)
    val attention = due.isNotEmpty() || soon.isNotEmpty()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (attention) "Coming up" else if (data.people.isEmpty() && data.followUps.isEmpty()) "Add your first person" else "Nothing coming up")
            if (due.isNotEmpty()) {
                SectionLabel("Follow-ups")
                due.take(5).forEach { f ->
                    LRow(
                        f.title, followUpLine(data, f, today),
                        lead = { PixelIconButton("check", "Mark ${f.title} done", { link.complete(f.id); model.haptic() }, accent) },
                        onClick = { go(model, LinkSection.FOLLOW_UPS, f.id, 0) }, onClickLabel = "Open follow-ups",
                    )
                }
                if (due.size > 5) PixelButton("All ${due.size} due", { go(model, LinkSection.FOLLOW_UPS, tab = 0) }, color = Pixel.InkSoft, compact = true)
            }
            if (soon.isNotEmpty()) {
                SectionLabel("Dates")
                soon.take(4).forEach { u ->
                    val who = u.date.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
                    LRow(u.date.title, listOfNotNull(who, dateLine(u)).joinToString(" · "), onClick = { go(model, LinkSection.DATES, u.date.id) }, onClickLabel = "Open dates")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Person", { go(model, LinkSection.PEOPLE) }, Modifier.weight(1f), accent, compact = true)
                PixelButton("Date", { go(model, LinkSection.DATES) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Follow-up", { go(model, LinkSection.FOLLOW_UPS) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
private fun LinkSearch(model: AppModel) {
    val data = model.linkData
    if (data.people.isEmpty() && data.dates.isEmpty() && data.followUps.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val hits = model.link.search(query).take(8)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Search Link", singleLine = true)
            if (query.isNotBlank() && hits.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            hits.forEach { h ->
                LRow(h.title, listOf(h.kind.label, h.detail.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), onClick = { openHit(model, h.kind, h.id); query = "" }, onClickLabel = "Open")
            }
        }
    }
}

private fun openHit(model: AppModel, kind: HitKind, id: String) {
    when (kind) {
        HitKind.PERSON -> go(model, LinkSection.PEOPLE, id)
        HitKind.NOTE -> go(model, LinkSection.PEOPLE, id, 1)
        HitKind.DATE -> go(model, LinkSection.DATES, id)
        HitKind.FOLLOW_UP -> go(model, LinkSection.FOLLOW_UPS, id, if (model.linkData.followUps.firstOrNull { it.id == id }?.status == FollowUpStatus.OPEN) 0 else 1)
    }
}

// ----- People -----------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val jump = remember { takeJump(LinkSection.PEOPLE) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.NAME) }
    var openId by remember { mutableStateOf(jump?.id) }
    var message by remember { mutableStateOf<String?>(null) }
    val people = link.directory(query, category, null, favorites, sort)
    RewardBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(name, { name = it.take(LinkRules.NAME_MAX) }, Modifier.weight(1f), placeholder = "Add a person", singleLine = true, keyboardOptions = wordKeys)
                PixelButton("Add", {
                    val out = link.addPerson(name)
                    if (out.record == null) message = out.refused else { name = ""; message = null; openId = out.record!!.id; model.haptic() }
                }, color = accent, enabled = name.isNotBlank())
            }
            message?.let { Notice(it, tone = NoticeTone.WARNING) }
            val similar = if (name.isBlank()) emptyList() else link.possibleDuplicates(name)
            if (similar.isNotEmpty()) Text("Already in your list: ${similar.joinToString { LinkRules.displayName(it) }}. You can still add another.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    if (data.people.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Search people", singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Chip("All", category == null && !favorites, { category = null; favorites = false })
                if (data.people.any { it.favorite }) Chip("Pinned", favorites, { favorites = !favorites; category = null })
                LinkRules.categories(data).filter { c -> data.people.any { it.category.equals(c, ignoreCase = true) } }.forEach { c ->
                    Chip(c, category == c, { category = if (category == c) null else c; favorites = false })
                }
                Chip(if (sort == Sort.NAME) "By name" else "Recent", false, { sort = if (sort == Sort.NAME) Sort.RECENT else Sort.NAME })
            }
            if (people.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            people.forEachIndexed { i, p ->
                if (i > 0) Rule()
                LRow(
                    LinkRules.displayName(p), listOfNotNull(p.category.takeIf { it.isNotBlank() }, p.organization.takeIf { it.isNotBlank() }).joinToString(" · "),
                    onClick = { openId = if (openId == p.id) null else p.id }, onClickLabel = if (openId == p.id) "Close the profile" else "Open the profile",
                )
                if (openId == p.id) PersonProfile(model, p, jump?.tab) { openId = null }
            }
        }
    }
}

@Composable
private fun PersonProfile(model: AppModel, p: Person, firstTab: Int?, onClose: () -> Unit) {
    var tab by remember(p.id) { mutableStateOf(firstTab ?: 0) }
    var editing by remember(p.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (editing) {
            PersonForm(model, p, onDone = { editing = false })
            return@Column
        }
        Tabs(listOf("Overview", "Notes", "Follow-ups"), tab) { tab = it }
        when (tab) {
            0 -> PersonOverview(model, p, onEdit = { editing = true }, onClose = onClose)
            1 -> PersonNotes(model, p)
            else -> PersonFollowUps(model, p)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonOverview(model: AppModel, p: Person, onEdit: () -> Unit, onClose: () -> Unit) {
    val link = model.link
    val data = model.linkData
    val today = model.store.today
    var asking by remember(p.id) { mutableStateOf(false) }
    var keepDates by remember(p.id) { mutableStateOf(true) }
    var keepFollowUps by remember(p.id) { mutableStateOf(true) }
    val dates = data.dates.filter { it.personId == p.id }
    val next = LinkRules.open(data, today, p.id).firstOrNull()
    if (asking) {
        val nd = dates.size
        val nf = data.followUps.count { it.personId == p.id }
        Text("Delete ${LinkRules.displayName(p)} and their notes? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
        if (nd > 0) ToggleRow(if (nd == 1) "Keep their date" else "Keep their $nd dates", keepDates, detail = "Kept dates stay, with no person on them.") { keepDates = it }
        if (nf > 0) ToggleRow(if (nf == 1) "Keep their follow-up" else "Keep their $nf follow-ups", keepFollowUps, detail = "Kept follow-ups stay, with no person on them.") { keepFollowUps = it }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Delete", { link.deletePerson(p.id, keepDates, keepFollowUps); onClose() }, Modifier.weight(1f), Pixel.Danger, compact = true)
            PixelButton("Keep them", { asking = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        return
    }
    val lines = listOf(
        "Called" to p.preferred, "Phone" to p.phone, "Email" to p.email, "Organization" to p.organization, "Role" to p.role, "Address" to p.address,
    ).filter { it.second.isNotBlank() }
    if (lines.isEmpty() && p.tags.isEmpty() && dates.isEmpty() && next == null) Text("Only a name so far. Add what is useful, nothing more.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    lines.forEach { (k, v) -> LRow(v, k) }
    if (p.tags.isNotEmpty()) Text(p.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    dates.forEach { d -> LinkRules.next(d, today)?.let { u -> LRow(d.title, dateLine(u)) } ?: LRow(d.title, "${d.day}/${d.month}${d.year?.let { "/$it" }.orEmpty()}") }
    next?.let { LRow(it.title, dueText(it, today) ?: "Next follow-up") }
    Actions {
        PixelButton("Edit", onEdit, Modifier.weight(1f), accent, compact = true)
        PixelButton(if (p.favorite) "Unpin" else "Pin", { link.setFavorite(p.id, !p.favorite) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
    PixelButton("Close", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

@Composable
private fun PersonNotes(model: AppModel, p: Person) {
    val link = model.link
    var text by remember(p.id) { mutableStateOf("") }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    var editing by remember(p.id) { mutableStateOf<String?>(null) }
    var draft by remember(p.id) { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelField(text, { text = it.take(LinkRules.NOTE_MAX) }, Modifier.weight(1f), placeholder = "Add a note", keyboardOptions = sentenceKeys)
        PixelButton("Add", {
            val out = link.addNote(p.id, text)
            if (out.record == null) message = out.refused else { text = ""; message = null; model.haptic() }
        }, color = accent, enabled = text.isNotBlank())
    }
    message?.let { Notice(it, tone = NoticeTone.WARNING) }
    val notes = link.notesOf(p.id)
    if (notes.isEmpty()) Text("No notes yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    notes.forEachIndexed { i, n ->
        if (i > 0) Rule()
        LRow(
            n.text.lineSequence().first(), "${dayOf(n.atMillis).short()}${if (n.text.contains('\n')) " · …" else ""}",
            onClick = { editing = if (editing == n.id) null else n.id; draft = n.text }, onClickLabel = if (editing == n.id) "Close the note" else "Open the note",
        )
        if (editing == n.id) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
            PixelField(draft, { draft = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 4, keyboardOptions = sentenceKeys)
            Actions {
                PixelButton("Save", { if (link.updateNote(n.id, draft).record != null) editing = null }, Modifier.weight(1f), accent, enabled = draft.isNotBlank(), compact = true)
                PixelButton("Close", { editing = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { link.deleteNote(n.id); editing = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date

@Composable
private fun PersonFollowUps(model: AppModel, p: Person) {
    val link = model.link
    val today = model.store.today
    var title by remember(p.id) { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.weight(1f), placeholder = "Add a follow-up", singleLine = true, keyboardOptions = sentenceKeys)
        PixelButton("Add", { if (link.addFollowUp(title, p.id).record != null) { title = ""; model.haptic() } }, color = accent, enabled = title.isNotBlank())
    }
    val open = link.openFollowUps(p.id)
    val history = link.followUpHistory(p.id).take(10)
    if (open.isEmpty() && history.isEmpty()) Text("Nothing to follow up.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    open.forEach { f ->
        LRow(f.title, dueText(f, today).orEmpty(), lead = { PixelIconButton("check", "Mark ${f.title} done", { link.complete(f.id); model.haptic() }, accent) })
    }
    history.forEach { f ->
        LRow(f.title, "${if (f.status == FollowUpStatus.COMPLETED) "Done" else "Cancelled"} · ${(f.completedDay ?: today).short()}", trail = { PixelButton("Reopen", { link.reopen(f.id) }, color = Pixel.InkSoft, compact = true) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonForm(model: AppModel, p: Person, onDone: () -> Unit) {
    val link = model.link
    val data = model.linkData
    var name by remember(p.id) { mutableStateOf(p.name) }
    var preferred by remember(p.id) { mutableStateOf(p.preferred) }
    var phone by remember(p.id) { mutableStateOf(p.phone) }
    var email by remember(p.id) { mutableStateOf(p.email) }
    var organization by remember(p.id) { mutableStateOf(p.organization) }
    var role by remember(p.id) { mutableStateOf(p.role) }
    var address by remember(p.id) { mutableStateOf(p.address) }
    var category by remember(p.id) { mutableStateOf(p.category) }
    var tags by remember(p.id) { mutableStateOf(p.tags.joinToString(", ")) }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    val similar = link.possibleDuplicates(name, phone, email, exceptId = p.id)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(name, { name = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = wordKeys)
        PixelField(preferred, { preferred = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Called, optional", singleLine = true, keyboardOptions = wordKeys)
        PixelField(phone, { phone = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Phone, optional", singleLine = true, keyboardOptions = phoneKeys)
        PixelField(email, { email = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Email, optional", singleLine = true, keyboardOptions = emailKeys)
        PixelField(organization, { organization = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Organization, optional", singleLine = true, keyboardOptions = wordKeys)
        PixelField(role, { role = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Role, optional", singleLine = true, keyboardOptions = wordKeys)
        PixelField(address, { address = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Address, optional", singleLine = true, keyboardOptions = wordKeys)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("No category", category.isBlank(), { category = "" })
            LinkRules.categories(data).forEach { c -> Chip(c, category.equals(c, ignoreCase = true), { category = c }) }
        }
        PixelField(tags, { tags = it.take(120) }, Modifier.fillMaxWidth(), label = "Tags, separated by commas", singleLine = true)
        if (similar.isNotEmpty()) Text("This may be ${similar.joinToString(" or ") { LinkRules.displayName(it) }}, already in your list. Nothing is merged.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Actions {
            PixelButton("Save", {
                val out = link.updatePerson(p.copy(name = name, preferred = preferred, phone = phone, email = email, organization = organization, role = role, address = address, category = category, tags = LinkRules.tags(tags)))
                if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
            }, Modifier.weight(1f), accent, enabled = name.isNotBlank(), compact = true)
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Notice(it, tone = NoticeTone.WARNING) }
    }
}

// ----- Dates ------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatesView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val today = model.store.today
    val jump = remember { takeJump(LinkSection.DATES) }
    var openId by remember { mutableStateOf(jump?.id) }
    RewardBox(model)
    TextBox { DateForm(model, null, onDone = {}) }
    val all = link.allDates()
    if (all.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            all.forEachIndexed { i, (d, u) ->
                if (i > 0) Rule()
                val who = d.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
                val line = if (u != null) dateLine(u) else "Passed · ${LinkRules.observed(d.month, d.day, d.year ?: today.year).short()}"
                LRow(d.title, listOfNotNull(who, line).joinToString(" · "), onClick = { openId = if (openId == d.id) null else d.id }, onClickLabel = if (openId == d.id) "Close the date" else "Open the date")
                if (openId == d.id) Column(Modifier.padding(bottom = Spacing.S)) { DateForm(model, d, onDone = { openId = null }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateForm(model: AppModel, initial: ImportantDate?, onDone: () -> Unit) {
    val link = model.link
    val data = model.linkData
    val key = initial?.id ?: "new"
    var kind by remember(key) { mutableStateOf(initial?.kind ?: DateKind.BIRTHDAY) }
    var title by remember(key) { mutableStateOf(initial?.title.orEmpty()) }
    var month by remember(key) { mutableStateOf(initial?.month?.toString().orEmpty()) }
    var day by remember(key) { mutableStateOf(initial?.day?.toString().orEmpty()) }
    var year by remember(key) { mutableStateOf(initial?.year?.toString().orEmpty()) }
    var annual by remember(key) { mutableStateOf(initial?.annual ?: true) }
    var personId by remember(key) { mutableStateOf(initial?.personId) }
    var note by remember(key) { mutableStateOf(initial?.note.orEmpty()) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    var asking by remember(key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (asking && initial != null) {
            Confirm("Delete this date?", "Delete", onConfirm = { link.deleteDate(initial.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        if (initial == null) PixelTitle("Add a date")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            DateKind.entries.forEach { k -> Chip(k.label, kind == k, { kind = k; if (title.isBlank() || DateKind.entries.any { it.label == title }) title = k.label }) }
        }
        PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(day, { day = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = "Day", singleLine = true, keyboardOptions = numberKeys)
            PixelField(month, { month = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = "Month", singleLine = true, keyboardOptions = numberKeys)
            PixelField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = "Year", placeholder = "unknown", singleLine = true, keyboardOptions = numberKeys)
        }
        ToggleRow("Every year", annual, detail = "A 29 February is kept on 28 February in years without one.") { annual = it }
        PersonPicker(data.people, personId) { personId = it }
        PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note, optional", singleLine = true, keyboardOptions = sentenceKeys)
        Actions {
            PixelButton(if (initial == null) "Add" else "Save", {
                val m = month.toIntOrNull(); val d = day.toIntOrNull()
                if (m == null || d == null) message = "Write the day and the month as numbers."
                else {
                    val out = if (initial == null) link.addDate(kind, title, m, d, year.toIntOrNull(), annual, personId, note)
                    else link.updateDate(initial.copy(kind = kind, title = title, month = m, day = d, year = year.toIntOrNull(), annual = annual, personId = personId, note = note))
                    if (out.record == null) message = out.refused ?: "That could not be saved."
                    else { message = null; model.haptic(); if (initial == null) { title = ""; month = ""; day = ""; year = ""; note = ""; personId = null } else onDone() }
                }
            }, Modifier.weight(1f), accent, enabled = title.isNotBlank() && month.isNotBlank() && day.isNotBlank(), compact = true)
            if (initial != null) {
                PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
        message?.let { Notice(it, tone = NoticeTone.WARNING) }
    }
}

/** Chooses one person, or none: a field to narrow the list and the first few matches as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPicker(people: List<Person>, selected: String?, onPick: (String?) -> Unit) {
    if (people.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val chosen = people.firstOrNull { it.id == selected }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), label = "Person, optional", placeholder = chosen?.let { LinkRules.displayName(it) } ?: "Search to choose", singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("No one", selected == null, { onPick(null) })
            chosen?.let { Chip(LinkRules.displayName(it), true, { onPick(null) }) }
            val matches = people.filter { it.id != selected && (query.isNotBlank() || people.size <= 6) && (query.isBlank() || LinkRules.displayName(it).contains(query.trim(), ignoreCase = true)) }
            matches.sortedBy { LinkRules.displayName(it).lowercase() }.take(6).forEach { p -> Chip(LinkRules.displayName(p), false, { onPick(p.id); query = "" }) }
        }
    }
}

// ----- Follow-ups -------------------------------------------------------------------------------------------------------------

@Composable
private fun FollowUpsView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val today = model.store.today
    val jump = remember { takeJump(LinkSection.FOLLOW_UPS) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var title by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf(jump?.id) }
    RewardBox(model)
    TextBox {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.weight(1f), placeholder = "Add a follow-up", singleLine = true, keyboardOptions = sentenceKeys)
            PixelButton("Add", { if (link.addFollowUp(title).record != null) { title = ""; model.haptic() } }, color = accent, enabled = title.isNotBlank())
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Tabs(listOf("Open", "History"), tab) { tab = it; openId = null }
            val rows = if (tab == 0) link.openFollowUps() else link.followUpHistory().take(60)
            if (rows.isEmpty()) PixelTitle(if (data.followUps.isEmpty()) "No follow-ups yet" else if (tab == 0) "Nothing open" else "Nothing here yet")
            rows.forEachIndexed { i, f ->
                if (i > 0) Rule()
                val detail = if (tab == 0) followUpLine(data, f, today)
                else listOfNotNull(f.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }, "${if (f.status == FollowUpStatus.COMPLETED) "Done" else "Cancelled"} · ${(f.completedDay ?: today).short()}").joinToString(" · ")
                LRow(
                    f.title, detail,
                    lead = { if (f.status == FollowUpStatus.OPEN) PixelIconButton("check", "Mark ${f.title} done", { link.complete(f.id); model.haptic() }, accent) },
                    trail = { if (f.status != FollowUpStatus.OPEN) PixelButton("Reopen", { link.reopen(f.id) }, color = Pixel.InkSoft, compact = true) },
                    onClick = { openId = if (openId == f.id) null else f.id }, onClickLabel = if (openId == f.id) "Hide options" else "Show options",
                )
                if (openId == f.id) FollowUpOptions(model, f) { openId = null }
            }
        }
    }
}

@Composable
private fun FollowUpOptions(model: AppModel, f: FollowUp, onDone: () -> Unit) {
    val link = model.link
    val data = model.linkData
    var title by remember(f.id) { mutableStateOf(f.title) }
    var due by remember(f.id) { mutableStateOf(f.due) }
    var note by remember(f.id) { mutableStateOf(f.note) }
    var personId by remember(f.id) { mutableStateOf(f.personId) }
    var asking by remember(f.id) { mutableStateOf(false) }
    var message by remember(f.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this follow-up?", "Delete", onConfirm = { link.deleteFollowUp(f.id); onDone() }, onKeep = { asking = false })
            return@Column
        }
        PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
        DateField("Day, optional", due, { due = it }, model.store.today)
        PersonPicker(data.people, personId) { personId = it }
        PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note, optional", minLines = 2, keyboardOptions = sentenceKeys)
        Actions {
            PixelButton("Save", {
                val out = link.updateFollowUp(f.copy(title = title, due = due, note = note, personId = personId))
                if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
            }, Modifier.weight(1f), accent, enabled = title.isNotBlank(), compact = true)
            if (f.status == FollowUpStatus.OPEN) PixelButton("Cancel it", { link.cancel(f.id); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Notice(it, tone = NoticeTone.WARNING) }
    }
}

// ----- Settings ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkSettingsView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<LinkKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Postcard Trail")
            ToggleRow(
                "Find Pokémon from finished follow-ups", data.settings.postcardTrail,
                detail = "A follow-up you finish counts once; people, notes, dates and edits never do. Off keeps everything in Link.",
            ) { link.setPostcardTrail(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Categories")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                LinkRules.categories(data).forEach { c -> Chip(c, false, {}) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(category, { category = it.take(LinkRules.TAG_MAX) }, Modifier.weight(1f), placeholder = "Add a category", singleLine = true, keyboardOptions = wordKeys)
                PixelButton("Add", { link.addCategory(category); category = "" }, color = accent, enabled = category.isNotBlank())
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            Text("Your records stay on this phone, and nobody is ever contacted by the app. Erasing Link never touches your Pokémon, your Poké or the other modules.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-link.json", link.export()) }, Modifier.fillMaxWidth(), accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    LinkKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-link-${k.name.lowercase()}.json", link.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            } else Text("Exporting is not available on this device.", style = MaterialTheme.typography.bodyMedium)
            note?.let { Notice(it) }
            LinkKinds.entries.forEach { k ->
                if (asking == k) Confirm(
                    if (k == LinkKinds.PEOPLE) "Erase all people and their notes? Dates and follow-ups stay, with no person on them. This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                    "Erase", "Keep them", onConfirm = { link.erase(k); asking = null }, onKeep = { asking = null },
                ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) Confirm("Erase every Link record? This cannot be undone.", "Erase all", "Keep them", onConfirm = { link.eraseAll(); askingAll = false }, onKeep = { askingAll = false })
            else PixelButton("Erase all Link records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
}
