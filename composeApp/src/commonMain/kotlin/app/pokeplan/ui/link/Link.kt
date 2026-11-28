package app.pokeplan.ui.link

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
import androidx.compose.ui.focus.focusRequester
import app.pokeplan.ui.screens.pullReveal
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Tutorials
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUp
import app.pokeplan.link.FollowUpState
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.HitKind
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkKinds
import app.pokeplan.link.LinkRules
import app.pokeplan.link.MemoryPhoto
import app.pokeplan.link.Person
import app.pokeplan.link.Sort
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.Accordion
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.screens.PullRevealSlot
import app.pokeplan.ui.screens.pullReveal
import app.pokeplan.ui.screens.rememberPullReveal
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
import app.pokeplan.ui.pokemon.LinkPartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.Actions
import app.pokeplan.ui.screens.Confirm
import app.pokeplan.ui.screens.DateField
import app.pokeplan.ui.screens.FirstRecordEgg
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuGrid
import app.pokeplan.ui.screens.SectionsKey
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.ModuleLeadStage
import app.pokeplan.ui.screens.ModuleTabs
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.Rule
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

enum class LinkSection(val label: String, val icon: String) {
    PEOPLE("People", "friend-ball"), MEMORIES("Memories", "tropic-mail"), DATES("Dates", "timer-ball"), FOLLOW_UPS("Follow-ups", "member-card"),
    ONEDRIVE("OneDrive", "oaks-letter"),
    SETTINGS("Settings", "storage-key"),
}

private val accent: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.LINK.accent

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val phoneKeys = KeyboardOptions(keyboardType = KeyboardType.Phone)
private val emailKeys = KeyboardOptions(keyboardType = KeyboardType.Email)
private val wordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = TabButton(label, selected, onClick, modifier, key = accent)

internal class LinkJump(val section: LinkSection, val id: String? = null, val tab: Int? = null)

private var pendingLinkJump: LinkJump? = null

internal fun go(model: AppModel, section: LinkSection, id: String? = null, tab: Int? = null) {
    if (section == LinkSection.PEOPLE) {
        if (id != null) openPerson(model, id, tab ?: 0) else { model.linkPersonId = null; model.linkSection = section }
        return
    }
    pendingLinkJump = LinkJump(section, id, tab)
    model.linkPersonId = null
    model.linkSection = section
}

/** Opens a memory's Memory Detail from anywhere in LINK. */
internal fun openMemory(model: AppModel, id: String) = go(model, LinkSection.MEMORIES, id)

/** Opens the Chapters view of Memories with one chapter open. */
internal fun openChapter(model: AppModel, eraId: String) = go(model, LinkSection.MEMORIES, eraId, MEMORIES_CHAPTERS)

/** Opens Memories with the add form already open (Home's and LINK's Quick actions). */
internal fun addMemoryNow(model: AppModel) {
    model.addMemoryRequested = true
    go(model, LinkSection.MEMORIES)
}

internal fun openPerson(model: AppModel, id: String, tab: Int = 0) {
    model.linkPersonTab = tab
    model.linkPersonId = id
    model.linkSection = LinkSection.PEOPLE
}

internal fun takeJump(section: LinkSection): LinkJump? = pendingLinkJump?.takeIf { it.section == section }?.also { pendingLinkJump = null }

internal fun linkSubtitle(model: AppModel): String {
    model.linkData
    val n = model.link.dueNow().size
    return if (n > 0) "$n due" else "people, dates, follow-ups"
}

@Composable
internal fun LinkScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    LaunchedEffect(model.data.tasks, model.linkData.followUps) { model.link.syncFromPlan() }
    val personId = model.linkPersonId?.takeIf { id -> model.linkData.people.any { it.id == id } }
    ScreenSwitch(LinkPlace(model.linkSection, personId.takeIf { model.linkSection == LinkSection.PEOPLE }), depth = { if (it.section == null) 0 else if (it.personId == null) 1 else 2 }) { place -> LinkPage(model, place, openPokemon) }
}

private data class LinkPlace(val section: LinkSection?, val personId: String? = null)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LinkPage(model: AppModel, place: LinkPlace, openPokemon: (String) -> Unit) {
    val section = place.section
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        LinkMenu(model, toModules, openPokemon)
        return
    }
    if (place.personId != null) {
        PersonScreen(model, place.personId)
        return
    }
    if (section == LinkSection.PEOPLE) {
        PeopleScreen(model)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.linkSection = null }, icon = section.icon, color = Module.LINK.look.header) {
        when (section) {
            LinkSection.PEOPLE -> Unit
            LinkSection.MEMORIES -> MemoriesScreen(model)
            LinkSection.DATES -> DatesView(model)
            LinkSection.FOLLOW_UPS -> FollowUpsView(model)
            LinkSection.ONEDRIVE -> OneDriveScreen(model)
            LinkSection.SETTINGS -> LinkSettingsView(model)
        }
    }
}

@Composable
private fun LinkMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.linkData
    val today = model.store.today
    val tint = ModuleAccent.LINK.accent
    val open = LinkRules.open(data, today)
    val sections = buildList {
        add(MenuEntry(LinkSection.PEOPLE.name, LinkSection.PEOPLE.icon, "People", if (data.people.isEmpty()) "add one" else "${data.people.size} people", tint = tint))
        add(MenuEntry(LinkSection.MEMORIES.name, LinkSection.MEMORIES.icon, "Memories", if (data.memories.isEmpty()) "add one" else "${data.memories.size} memories", tint = tint))
        add(MenuEntry(LinkSection.DATES.name, LinkSection.DATES.icon, "Dates", if (data.dates.isEmpty()) "add one" else "${LinkRules.upcoming(data, today).size} soon", tint = tint))
        add(MenuEntry(LinkSection.FOLLOW_UPS.name, LinkSection.FOLLOW_UPS.icon, "Follow-ups", if (data.followUps.isEmpty()) "add one" else "${open.size} open", tint = tint))
        // OneDrive is optional, and only where the platform has a real source (never desktop or web).
        model.oneDrive?.let { source -> add(MenuEntry(LinkSection.ONEDRIVE.name, LinkSection.ONEDRIVE.icon, "OneDrive", oneDriveDetail(source.state()), tint = tint)) }
        add(MenuEntry(LinkSection.SETTINGS.name, LinkSection.SETTINGS.icon, "Settings", "Postcard Trail, your data", tint = Pixel.InkSoft))
    }
    val scroll = rememberScrollState()
    var query by remember { mutableStateOf("") }
    val reveal = app.pokeplan.ui.screens.rememberPullReveal(scroll, keepOpen = { query.isNotBlank() }, onClose = { query = "" })
    val hasRecords = LinkRules.hasRecords(data)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.LINK.label, onBack = onBack, color = Module.LINK.look.header, trailing = {
            SectionsKey(Module.LINK.label, sections) { key -> model.linkSection = LinkSection.valueOf(key) }
        })
        Column(
            Modifier.weight(1f).pullReveal(reveal).verticalScroll(scroll).padding(horizontal = Spacing.Page, vertical = Spacing.L),
        ) {
            if (hasRecords) app.pokeplan.ui.screens.PullRevealSlot(reveal, model.data.settings.reduceMotion) { LinkSearch(model, query, { query = it }, reveal::close) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                ModuleLeadStage(model, Module.LINK, model.data.player.linkLead, model.store.moduleEvent(app.pokeplan.app.GameStore.LINK_LOG_PREFIX), openPokemon = openPokemon)
                FirstRecordEgg(model, Tutorials.LINK_POSTCARD, data.people.size + data.dates.size + data.followUps.size)
                LinkOverview(model)
                LinkPartyPanel(model, openPokemon)
            }
        }
    }
}

private fun dateLine(u: app.pokeplan.link.Upcoming): String {
    val years = u.years?.let { n -> if (u.date.kind == DateKind.BIRTHDAY) "turns $n" else "$n years" }
    return listOfNotNull(u.day.short(), LinkRules.inDays(u.daysAway), years).joinToString(" · ")
}

private fun planTask(model: AppModel, f: FollowUp): app.pokeplan.plan.Task? {
    model.data
    return model.store.linkedTask(app.pokeplan.plan.LinkKind.LINK_FOLLOW_UP, f.id)
}

private fun planWord(t: app.pokeplan.plan.Task): String = when (t.state) { TaskState.DONE -> "Done in Plan"; TaskState.OPEN -> "In Plan"; else -> "In Plan, closed" }

private fun dueText(f: FollowUp, today: LocalDate): String? = f.due?.let { if (it < today) "Due since ${it.short()}" else if (it == today) "Due today" else "Due ${it.short()}" }

private fun closedLine(f: FollowUp, today: LocalDate): String =
    "${if (f.status == FollowUpStatus.COMPLETED) "Done" else "Cancelled"} · ${(f.completedMillis?.let(::dayOf) ?: f.completedDay ?: today).short()}"

private fun followUpLine(data: app.pokeplan.link.LinkData, f: FollowUp, today: LocalDate): String {
    val who = f.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
    return listOfNotNull(who, dueText(f, today)).joinToString(" · ")
}

@Composable
private fun LinkOverview(model: AppModel) {
    val data = model.linkData
    val link = model.link
    val today = model.store.today
    val due = link.dueNow()
    val soon = LinkRules.upcoming(data, today, 14)
    val attention = due.isNotEmpty() || soon.isNotEmpty()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (attention) "Coming up" else if (data.people.isEmpty() && data.followUps.isEmpty()) "Add your first person" else "Nothing coming up")
            if (due.isNotEmpty()) {
                SectionLabel("Follow-ups")
                due.take(5).forEach { f ->
                    RecordRow(
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
                    RecordRow(u.date.title, listOfNotNull(who, dateLine(u)).joinToString(" · "), onClick = { go(model, LinkSection.DATES, u.date.id) }, onClickLabel = "Open dates")
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Quick actions")
            var adding by remember { mutableStateOf(false) }
            if (!adding) PixelButton("Add", { adding = true }, Modifier.fillMaxWidth(), compact = true)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Person", { go(model, LinkSection.PEOPLE) }, Modifier.weight(1f), compact = true)
                PixelButton("Date", { go(model, LinkSection.DATES) }, Modifier.weight(1f), compact = true)
                PixelButton("Follow-up", { go(model, LinkSection.FOLLOW_UPS) }, Modifier.weight(1f), compact = true)
                PixelButton("Memory", { addMemoryNow(model) }, Modifier.weight(1f), compact = true)
            }
        }
    }
}

/** Results shown per kind before the search stops listing; the player narrows the words instead of paging. */
private const val HITS_PER_KIND = 5

@Composable
private fun LinkSearch(model: AppModel, query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    val field = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { field.requestFocus() }
    val hits = model.link.search(query)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(query, { onQuery(it.take(40)) }, Modifier.weight(1f).focusRequester(field), placeholder = "Search Link", singleLine = true)
        PixelIconButton("close", "Close search", onDone)
    }
    if (query.isNotBlank() && hits.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    // Grouped by what each result is, with the context that tells two of them apart; each opens its exact record.
    hits.groupBy { it.kind }.forEach { (kind, group) ->
        SectionLabel(if (group.size > HITS_PER_KIND) "${kind.plural} · ${group.size}" else kind.plural)
        group.take(HITS_PER_KIND).forEach { h ->
            RecordRow(h.title, h.detail, onClick = { openHit(model, h.kind, h.id); onDone() }, onClickLabel = "Open")
        }
    }
}

private fun openHit(model: AppModel, kind: HitKind, id: String) {
    when (kind) {
        HitKind.PERSON -> go(model, LinkSection.PEOPLE, id)
        HitKind.NOTE -> go(model, LinkSection.PEOPLE, id, 2)
        HitKind.MEMORY, HitKind.PHOTO -> openMemory(model, id)
        HitKind.ERA -> openChapter(model, id)
        HitKind.DATE -> go(model, LinkSection.DATES, id)
        HitKind.FOLLOW_UP -> go(model, LinkSection.FOLLOW_UPS, id, if (model.linkData.followUps.firstOrNull { it.id == id }?.status == FollowUpStatus.OPEN) 0 else 1)
    }
}

private const val DIRECTORY_PAGE = 40
private const val NO_GROUP = "No group"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleScreen(model: AppModel) {
    val link = model.link
    val data = model.linkData
    var name by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.GROUP) }
    var message by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(DIRECTORY_PAGE) }
    var importing by remember { mutableStateOf(false) }
    val contacts = model.contacts
    val scroll = rememberScrollState()
    val reveal = rememberPullReveal(
        scroll,
        keepOpen = { query.isNotBlank() || category != null || group != null || favorites || sort != Sort.GROUP },
        onClose = { query = ""; category = null; group = null; favorites = false; sort = Sort.GROUP },
    )
    SectionScaffold(
        LinkSection.PEOPLE.label, null, onBack = { model.linkSection = null }, icon = LinkSection.PEOPLE.icon, color = Module.LINK.look.header,
        scrollState = scroll, bodyModifier = Modifier.pullReveal(reveal),
    ) {
        if (importing && contacts != null) {
            TextBox { ContactImportView(model, contacts) { importing = false } }
            return@SectionScaffold
        }
        val people = link.directory(query, category, null, favorites, sort, group)
        if (data.people.isNotEmpty()) PullRevealSlot(reveal, model.data.settings.reduceMotion) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(query, { query = it.take(40) }, Modifier.weight(1f), placeholder = "Search people", singleLine = true)
                PixelIconButton("close", "Close search", reveal::close)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                val pinnedKey = "\u0001pinned"
                val filterKeys = listOf<String?>(null) + (if (data.people.any { it.favorite }) listOf(pinnedKey) else emptyList()) +
                    LinkRules.categories(data).filter { c -> data.people.any { it.category.equals(c, ignoreCase = true) } }
                PixelDropdown(
                    filterKeys, if (favorites) pinnedKey else category, { k -> if (k == null) "All" else if (k == pinnedKey) "Pinned" else k },
                    { k -> if (k == pinnedKey) { favorites = true; category = null } else { category = k; favorites = false } },
                    Modifier.weight(1f), key = accent,
                )
                PixelDropdown(
                    Sort.entries.toList(), sort,
                    { s -> when (s) { Sort.RECENT -> "Recent"; Sort.MANUAL -> "My order"; Sort.GROUP -> "By group"; else -> "By name" } },
                    { s -> sort = s ?: Sort.NAME }, Modifier.weight(1f), key = accent,
                )
            }
            val groupChips = data.settings.groups.filter { g -> data.people.any { g.id in it.groupIds } }
            if (groupChips.isNotEmpty()) {
                FieldLabel("Groups")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    groupChips.forEach { g -> Chip(g.name, group == g.id, { group = if (group == g.id) null else g.id }) }
                }
            }
        }
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (!adding) PixelButton("Add person", { adding = true }, Modifier.fillMaxWidth(), accent, compact = true)
                else {
                    FormGroup {
                        PixelField(name, { name = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Person", placeholder = "Add a person", singleLine = true, keyboardOptions = wordKeys)
                        FormSave("Add", {
                            val out = link.addPerson(name)
                            if (out.record == null) message = out.refused else { name = ""; message = null; adding = false; model.haptic(); openPerson(model, out.record!!.id) }
                        }, accent, enabled = name.isNotBlank())
                    }
                    PixelButton("Cancel", { adding = false; name = ""; message = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    message?.let { Report(model, it, NoticeTone.WARNING) }
                    val similar = if (name.isBlank()) emptyList() else link.possibleDuplicates(name)
                    if (similar.isNotEmpty()) Text("Already in your list: ${similar.joinToString { LinkRules.displayName(it) }}. You can still add another.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: result
                }
                if (data.people.isNotEmpty()) {
                    if (people.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    val upTo = shown
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        val firstMovable = people.indexOfFirst { !it.isMe }
                        val ids = people.map { it.id }
                        var lastHeading: String? = null
                        people.take(upTo).forEachIndexed { i, p ->
                            if (sort == Sort.GROUP) {
                                val heading = if (p.isMe) null else LinkRules.groupOf(data, p) ?: NO_GROUP
                                if (heading != null && heading != lastHeading) {
                                    FieldLabel(heading)
                                    lastHeading = heading
                                }
                            }
                            val sub = listOfNotNull(p.category.takeIf { it.isNotBlank() }, p.organization.takeIf { it.isNotBlank() }).joinToString(" · ")
                            val moveKeys: (@Composable () -> Unit)? = if (sort == Sort.MANUAL && !p.isMe) {
                                {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                        PixelIconButton("arrow-up", "Move ${LinkRules.displayName(p)} up", { link.moveInOrder(p.id, ids, -1) }, accent, enabled = i > firstMovable)
                                        PixelIconButton("arrow-down", "Move ${LinkRules.displayName(p)} down", { link.moveInOrder(p.id, ids, 1) }, accent, enabled = i < people.lastIndex)
                                    }
                                }
                            } else null
                            if (moveKeys == null) PersonTile(p, sub) { openPerson(model, p.id) }
                            // The keys sit under the tile, not beside it: a name keeps its whole row at any text size.
                            else Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                                PersonTile(p, sub) { openPerson(model, p.id) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { moveKeys() }
                            }
                        }
                    }
                    if (people.size > upTo) PixelButton("Show ${minOf(DIRECTORY_PAGE, people.size - upTo)} more of ${people.size - upTo}", { shown = upTo + DIRECTORY_PAGE }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
                val noMe = data.people.none { it.isMe }
                Accordion("Groups") { GroupsManager(model) }
                if (noMe || contacts != null) Accordion("More") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (noMe) PixelButton("Set up my profile", {
                            link.createMe(model.data.trainerName).record?.let { me -> model.haptic(); openPerson(model, me.id, 1) }
                        }, Modifier.fillMaxWidth(), accent, compact = true)
                        if (contacts != null) PixelButton("Import contacts", { importing = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupsManager(model: AppModel) {
    val link = model.link
    val data = model.linkData
    var renaming by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        data.settings.groups.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (deleting == g.id) Confirm("Delete ${g.name}? Nobody is deleted.", "Delete", "Keep it", { link.deleteGroup(g.id); deleting = null }, { deleting = null })
                else if (renaming == g.id) {
                    PixelField(text, { text = it.take(LinkRules.TAG_MAX) }, Modifier.fillMaxWidth(), label = "Group", singleLine = true, keyboardOptions = wordKeys)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Save", {
                            val out = link.renameGroup(g.id, text)
                            if (out.record == null) message = out.refused else { renaming = null; message = null }
                        }, Modifier.weight(1f), accent, compact = true, enabled = text.isNotBlank())
                        PixelButton("Cancel", { renaming = null; message = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Pixel.Ink, maxLines = 1)
                    PixelButton("Rename", { renaming = g.id; text = g.name; deleting = null; message = null }, color = Pixel.InkSoft, compact = true)
                    PixelButton("Delete", { deleting = g.id; renaming = null }, color = Pixel.Danger, compact = true)
                }
            }
        }
        val unused = LinkRules.STARTING_GROUPS.filter { s -> data.settings.groups.none { it.name.equals(s, ignoreCase = true) } }
        if (unused.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            unused.forEach { s -> Chip(s, false, { newName = s }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
            PixelField(newName, { newName = it.take(LinkRules.TAG_MAX) }, Modifier.weight(1f), label = "New group", singleLine = true, keyboardOptions = wordKeys)
            PixelButton("Add group", {
                val out = link.addGroup(newName)
                if (out.record == null) message = out.refused else { newName = ""; message = null }
            }, color = accent, enabled = newName.isNotBlank())
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
private fun PersonScreen(model: AppModel, personId: String) {
    val p = model.linkData.people.firstOrNull { it.id == personId }
    if (p == null) {
        LaunchedEffect(personId) { model.linkPersonId = null }
        return
    }
    val close = { model.linkPersonId = null }
    var choosing by remember(p.id) { mutableStateOf(false) }
    SectionScaffold(if (p.isMe) "My profile" else "Profile", null, onBack = close, icon = LinkSection.PEOPLE.icon, color = Module.LINK.look.header) {
        if (choosing) PersonPicture(model, p) { choosing = false }
        else {
            PersonCard(p, LinkRules.groupNames(model.linkData, p)) { choosing = true }
            TextBox { PersonProfile(model, p, model.linkPersonTab, close) }
            if (model.link.safariOn) SafariPanel(model, p)
        }
    }
}

@Composable
private fun PersonProfile(model: AppModel, p: Person, firstTab: Int, onClose: () -> Unit) {
    var tab by remember(p.id) { mutableStateOf(firstTab) }
    var editing by remember(p.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (editing) {
            PersonForm(model, p, onDone = { editing = false })
            return@Column
        }
        ModuleTabs(listOf("Overview", "Profile"), if (tab < 2) tab else -1, accent) { tab = it }
        ModuleTabs(listOf("Notes", "Follow-ups"), tab - 2, accent) { tab = it + 2 }
        when (tab) {
            0 -> PersonOverview(model, p, onEdit = { editing = true }, onClose = onClose)
            1 -> PersonProfileTab(model, p)
            2 -> PersonNotes(model, p)
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
    var addingDate by remember(p.id) { mutableStateOf(false) }
    var addingMemory by remember(p.id) { mutableStateOf(false) }
    var viewingPhoto by remember(p.id) { mutableStateOf<MemoryPhoto?>(null) }
    var keepDates by remember(p.id) { mutableStateOf(true) }
    var keepFollowUps by remember(p.id) { mutableStateOf(true) }
    val dates = data.dates.filter { it.personId == p.id }
    val next = LinkRules.open(data, today, p.id).firstOrNull()
    if (asking) {
        val nd = dates.size
        val nf = data.followUps.count { it.personId == p.id }
        Text("Delete ${LinkRules.displayName(p)} and their notes?", style = MaterialTheme.typography.bodyLarge)
        if (data.memories.any { p.id in it.personIds }) Text("Their memories stay, without them.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: result
        if (nd > 0) ToggleRow(if (nd == 1) "Keep their date" else "Keep their $nd dates", keepDates, detail = "Kept dates stay, with no person on them.") { keepDates = it } // copy-ok: result
        if (nf > 0) ToggleRow(if (nf == 1) "Keep their follow-up" else "Keep their $nf follow-ups", keepFollowUps, detail = "Kept follow-ups stay, with no person on them.") { keepFollowUps = it } // copy-ok: result
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Delete", {
                val before = model.linkData
                link.deletePerson(p.id, keepDates, keepFollowUps); onClose()
                model.offerUndo("Deleted: ${LinkRules.displayName(p)}") { link.restore(before) }
            }, Modifier.weight(1f), Pixel.Danger, compact = true)
            PixelButton("Keep them", { asking = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        return
    }
    if (addingDate) {
        DateForm(model, null, onDone = { addingDate = false }, presetPerson = p.id)
        return
    }
    if (addingMemory) {
        FormBox { MemoryForm(model, existing = null, presetPersonId = p.id, onDone = { addingMemory = false }) }
        return
    }
    val photo = viewingPhoto
    if (photo != null) {
        UpTo { viewingPhoto = null }
        PhotoViewer(photo, onOpenMemory = { viewingPhoto = null; openMemory(model, photo.memoryId) }) { viewingPhoto = null }
        return
    }
    val lines = listOf("Phone" to p.phone, "Email" to p.email, "Address" to p.address).filter { it.second.isNotBlank() }
    lines.forEach { (k, v) -> RecordRow(v, k) }
    if (p.tags.isNotEmpty()) Text(p.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    profileSummary(model, p).takeIf { it.isNotEmpty() }?.let { RecordRow(it, "Sign, animal, type") }
    dates.forEach { d -> LinkRules.next(d, today)?.let { u -> RecordRow(d.title, dateLine(u)) } ?: RecordRow(d.title, "${d.day}/${d.month}${d.year?.let { "/$it" }.orEmpty()}") }
    next?.let { RecordRow(it.title, dueText(it, today) ?: "Next follow-up") }
    FieldLabel("Memories")
    link.memoriesOf(p.id).take(4).forEach { m -> RecordRow(link.memoryLabel(m), LinkRules.dateLabel(m.date), onClick = { openMemory(model, m.id) }, onClickLabel = "Open the memory") }
    PixelButton("Add a memory", { addingMemory = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    val photos = link.photosOfPerson(p.id)
    if (photos.isNotEmpty()) {
        FieldLabel("Photos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            photos.take(8).forEach { ph -> PhotoThumb(ph, 80.dp) { viewingPhoto = ph } }
        }
    }
    app.pokeplan.ui.integrations.PersonEvents(model, p.id)
    val contact = model.phone?.takeIf { it.canContact }
    if (contact != null && (p.phone.isNotBlank() || p.email.isNotBlank())) Actions {
        if (p.phone.isNotBlank()) {
            PixelButton("Call", { contact.call(p.phone) }, Modifier.weight(1f), Pixel.Tone, compact = true)
            PixelButton("Message", { contact.message(p.phone) }, Modifier.weight(1f), Pixel.Tone, compact = true)
        }
        if (p.email.isNotBlank()) PixelButton("Email", { contact.email(p.email) }, Modifier.weight(1f), Pixel.Tone, compact = true)
    }
    Actions {
        PixelButton("Edit", onEdit, Modifier.weight(1f), accent, compact = true)
        PixelButton(if (p.favorite) "Unpin" else "Pin", { link.setFavorite(p.id, !p.favorite) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
    Actions {
        PixelButton("Add a date", { addingDate = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}

@Composable
private fun PersonNotes(model: AppModel, p: Person) {
    val link = model.link
    var text by remember(p.id) { mutableStateOf("") }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    var editing by remember(p.id) { mutableStateOf<String?>(null) }
    var draft by remember(p.id) { mutableStateOf("") }
    FormGroup {
        PixelField(text, { text = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", placeholder = "Add a note", keyboardOptions = sentenceKeys)
        FormSave("Add", {
            val out = link.addNote(p.id, text)
            if (out.record == null) message = out.refused else { text = ""; message = null; model.haptic() }
        }, accent, enabled = text.isNotBlank())
    }
    message?.let { Report(model, it, NoticeTone.WARNING) }
    val notes = link.notesOf(p.id)
    if (notes.isEmpty()) Text("No notes yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    notes.forEachIndexed { i, n ->
        if (i > 0) Rule()
        RecordRow(
            n.text.lineSequence().first(), "${dayOf(n.atMillis).short()}${if (n.text.contains('\n')) " · …" else ""}",
            onClick = { editing = if (editing == n.id) null else n.id; draft = n.text }, onClickLabel = if (editing == n.id) "Close the note" else "Open the note",
        )
        if (editing == n.id) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
            PixelField(draft, { draft = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 4, keyboardOptions = sentenceKeys)
            Actions {
                PixelButton("Save", { if (link.updateNote(n.id, draft).record != null) editing = null }, Modifier.weight(1f), accent, enabled = draft.isNotBlank(), compact = true)
                PixelButton("Close", { editing = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { val before = model.linkData; link.deleteNote(n.id); editing = null; model.offerUndo("Deleted: note") { link.restore(before) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
    var due by remember(p.id) { mutableStateOf<LocalDate?>(null) }
    var openId by remember(p.id) { mutableStateOf<String?>(null) }
    FormGroup {
        PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Follow-up", placeholder = "Add a follow-up", singleLine = true, keyboardOptions = sentenceKeys)
        DateField("Day", due, { due = it }, today, accent, optional = true)
        FormSave("Add", { if (link.addFollowUp(title, p.id, due).record != null) { title = ""; due = null; model.haptic() } }, accent, enabled = title.isNotBlank())
    }
    val open = link.openFollowUps(p.id)
    val history = link.followUpHistory(p.id).take(10)
    if (open.isEmpty() && history.isEmpty()) Text("Nothing to follow up.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    open.forEach { f ->
        val task = planTask(model, f)
        RecordRow(
            f.title, listOfNotNull(task?.let(::planWord), dueText(f, today)).joinToString(" · "),
            lead = { if (task == null) PixelIconButton("check", "Mark ${f.title} done", { link.complete(f.id); model.haptic() }, accent) },
            onClick = { openId = if (openId == f.id) null else f.id }, onClickLabel = if (openId == f.id) "Hide options" else "Show options",
        )
        if (openId == f.id) FollowUpOptions(model, f) { openId = null }
    }
    history.forEach { f ->
        RecordRow(f.title, closedLine(f, today), trail = { if (planTask(model, f) == null) PixelButton("Reopen", { link.reopen(f.id) }, color = Pixel.InkSoft, compact = true) })
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
    var groupIds by remember(p.id) { mutableStateOf(p.groupIds) }
    var tags by remember(p.id) { mutableStateOf(p.tags.joinToString(", ")) }
    var newCategory by remember(p.id) { mutableStateOf("") }
    var newGroup by remember(p.id) { mutableStateOf("") }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    val similar = link.possibleDuplicates(name, phone, email, exceptId = p.id)
    var more by remember(p.id) { mutableStateOf(listOf(organization, role, address, tags, p.preferred).any { it.isNotBlank() }) }
    FormStack {
        FormGroup {
            PixelField(name, { name = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true, keyboardOptions = wordKeys)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(phone, { phone = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Phone", singleLine = true, keyboardOptions = phoneKeys)
                PixelField(email, { email = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Email", singleLine = true, keyboardOptions = emailKeys)
            }
            val categoryList = LinkRules.categories(data)
            PixelDropdown(
                listOf<String?>(null) + categoryList, if (category.isBlank()) null else (categoryList.firstOrNull { it.equals(category, ignoreCase = true) } ?: category),
                { it ?: "No category" }, { category = it ?: "" }, Modifier.fillMaxWidth(), key = accent, label = "Main category",
            )
            val groups = data.settings.groups
            if (groups.isNotEmpty()) {
                SectionLabel("Groups")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    groups.forEach { g -> Chip(g.name, g.id in groupIds, { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                PixelField(newGroup, { newGroup = it.take(LinkRules.TAG_MAX) }, Modifier.weight(1f), label = "New group", singleLine = true, keyboardOptions = wordKeys)
                PixelButton("Add", {
                    val out = link.addGroup(newGroup)
                    val made = out.record
                    if (made == null) message = out.refused else { groupIds = groupIds + made.id; newGroup = ""; message = null }
                }, color = accent, enabled = newGroup.isNotBlank())
            }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PixelField(preferred, { preferred = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Called", singleLine = true, keyboardOptions = wordKeys)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(organization, { organization = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Organization", singleLine = true, keyboardOptions = wordKeys)
                    PixelField(role, { role = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Role", singleLine = true, keyboardOptions = wordKeys)
                }
                PixelField(address, { address = it.take(LinkRules.FIELD_MAX) }, Modifier.fillMaxWidth(), label = "Address", singleLine = true, keyboardOptions = wordKeys)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                    PixelField(newCategory, { newCategory = it.take(LinkRules.TAG_MAX) }, Modifier.weight(1f), label = "New category", singleLine = true, keyboardOptions = wordKeys)
                    PixelButton("Add", { link.addCategory(newCategory); category = newCategory.trim(); newCategory = "" }, color = accent, enabled = newCategory.isNotBlank())
                }
                PixelField(tags, { tags = it.take(120) }, Modifier.fillMaxWidth(), label = "Tags, separated by commas", singleLine = true)
            }
        }
        if (similar.isNotEmpty()) Text("This may be ${similar.joinToString(" or ") { LinkRules.displayName(it) }}, already in your list. Nothing is merged.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: result
        FormSave("Save", {
            val out = link.updatePerson(p.copy(name = name, preferred = preferred, phone = phone, email = email, organization = organization, role = role, address = address, category = category, groupIds = groupIds, tags = LinkRules.tags(tags)))
            if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
        }, accent, enabled = name.isNotBlank())
        PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatesView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val today = model.store.today
    val jump = remember { takeJump(LinkSection.DATES) }
    var openId by remember { mutableStateOf(jump?.id) }
    var adding by remember { mutableStateOf(false) }
    val all = link.allDates()
    if (all.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            all.forEachIndexed { i, (d, u) ->
                if (i > 0) Rule()
                val who = d.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
                val line = if (u != null) dateLine(u) else "Passed · ${LinkRules.observed(d.month, d.day, d.year ?: today.year).short()}"
                RecordRow(d.title, listOfNotNull(who, line).joinToString(" · "), onClick = { openId = if (openId == d.id) null else d.id }, onClickLabel = if (openId == d.id) "Close the date" else "Open the date")
                if (openId == d.id) Column(Modifier.padding(bottom = Spacing.S)) { DateForm(model, d, onDone = { openId = null }) }
            }
        }
    }
    if (adding) FormBox { DateForm(model, null, onDone = { adding = false }) }
    else PixelButton("Add a date", { adding = true }, Modifier.fillMaxWidth(), compact = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateForm(model: AppModel, initial: ImportantDate?, onDone: () -> Unit, presetPerson: String? = null) {
    val link = model.link
    val data = model.linkData
    val key = initial?.id ?: "new"
    var kind by remember(key) { mutableStateOf(initial?.kind ?: DateKind.BIRTHDAY) }
    var title by remember(key) { mutableStateOf(initial?.title.orEmpty()) }
    var month by remember(key) { mutableStateOf(initial?.month?.toString().orEmpty()) }
    var day by remember(key) { mutableStateOf(initial?.day?.toString().orEmpty()) }
    var year by remember(key) { mutableStateOf(initial?.year?.toString().orEmpty()) }
    var annual by remember(key) { mutableStateOf(initial?.annual ?: true) }
    var personId by remember(key) { mutableStateOf(initial?.personId ?: presetPerson) }
    var note by remember(key) { mutableStateOf(initial?.note.orEmpty()) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    var asking by remember(key) { mutableStateOf(false) }
    var remind by remember(key) { mutableStateOf<Int?>(null) }
    var more by remember(key) { mutableStateOf(initial != null) }
    FormStack {
        if (asking && initial != null) {
            Confirm("Delete this date?", "Delete", onConfirm = { link.deleteDate(initial.id); onDone() }, onKeep = { asking = false })
            return@FormStack
        }
        if (initial == null) PixelTitle("Add a date")
        FormGroup {
            PixelChoice(DateKind.entries.toList(), kind, { it?.label ?: "" }, { k -> if (k != null) { kind = k; if (title.isBlank() || DateKind.entries.any { it.label == title }) title = k.label } }, label = "Kind", key = accent)
            PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelDropdown((1..12).toList(), month.toIntOrNull(), { m -> if (m == null) "" else monthNames[m - 1] }, { m -> if (m != null) month = m.toString() }, Modifier.weight(1.2f), key = accent, label = "Month")
                PixelField(day, { day = it.filter(Char::isDigit).take(2) }, Modifier.weight(0.8f), label = "Day", singleLine = true, keyboardOptions = numberKeys)
                PixelField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = "Year", placeholder = "unknown", singleLine = true, keyboardOptions = numberKeys)
            }
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                ToggleRow("Every year", annual) { annual = it }
                PersonPicker(data.people, personId) { personId = it }
                PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", singleLine = true, keyboardOptions = sentenceKeys)
                val saved = if (initial != null) data.dates.firstOrNull { it.id == initial.id }?.remindDays else remind
                PixelChoice(
                    listOf<Int?>(null) + LinkRules.REMIND_PRESETS, saved, { n -> if (n == null) "No" else remindLabel(n) },
                    { n -> if (initial != null) link.setDateRemind(initial.id, n) else remind = n }, label = "Remind me", key = accent,
                )
            }
        }
        FormSave(if (initial == null) "Add" else "Save", {
            val m = month.toIntOrNull(); val d = day.toIntOrNull()
            if (m == null || d == null) message = "Write the day and the month as numbers." // copy-ok: result
            else {
                val out = if (initial == null) link.addDate(kind, title, m, d, year.toIntOrNull(), annual, personId, note, remind)
                else link.updateDate(initial.copy(kind = kind, title = title, month = m, day = d, year = year.toIntOrNull(), annual = annual, personId = personId, note = note))
                if (out.record == null) message = out.refused ?: "That could not be saved."
                else {
                    message = null; model.haptic()
                    if (initial == null && presetPerson == null) { title = ""; month = ""; day = ""; year = ""; note = ""; personId = null; remind = null } else onDone()
                }
            }
        }, accent, enabled = title.isNotBlank() && month.isNotBlank() && day.isNotBlank())
        if (initial != null) Actions {
            PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        } else if (presetPerson != null) PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPicker(people: List<Person>, selected: String?, onPick: (String?) -> Unit) {
    if (people.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val chosen = people.firstOrNull { it.id == selected }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), label = "Person", placeholder = chosen?.let { LinkRules.displayName(it) } ?: "Search to choose", singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Chip("No one", selected == null, { onPick(null) })
            chosen?.let { Chip(LinkRules.displayName(it), true, { onPick(null) }) }
            val matches = people.filter { it.id != selected && (query.isNotBlank() || people.size <= 6) && (query.isBlank() || LinkRules.displayName(it).contains(query.trim(), ignoreCase = true)) }
            matches.sortedBy { LinkRules.displayName(it).lowercase() }.take(6).forEach { p -> Chip(LinkRules.displayName(p), false, { onPick(p.id); query = "" }) }
        }
    }
}

@Composable
private fun FollowUpsView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val today = model.store.today
    val jump = remember { takeJump(LinkSection.FOLLOW_UPS) }
    var tab by remember { mutableStateOf(jump?.tab ?: 0) }
    var title by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf<LocalDate?>(null) }
    var openId by remember { mutableStateOf(jump?.id) }
    FormBox {
        FormStack {
            FormGroup {
                PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Follow-up", placeholder = "Add a follow-up", singleLine = true, keyboardOptions = sentenceKeys)
                DateField("Day", newDue, { newDue = it }, today, accent, optional = true)
            }
            FormSave("Add", { if (link.addFollowUp(title, null, newDue).record != null) { title = ""; newDue = null; model.haptic() } }, accent, enabled = title.isNotBlank())
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ModuleTabs(listOf("Open", "History"), tab, accent) { tab = it; openId = null }
            val rows = if (tab == 0) link.openFollowUps() else link.followUpHistory().take(60)
            if (rows.isEmpty()) PixelTitle(if (data.followUps.isEmpty()) "No follow-ups yet" else if (tab == 0) "Nothing open" else "Nothing here yet")
            rows.forEachIndexed { i, f ->
                if (i > 0) Rule()
                val task = planTask(model, f)
                val detail = if (tab == 0) listOfNotNull(task?.let(::planWord), followUpLine(data, f, today).takeIf { it.isNotEmpty() }).joinToString(" · ")
                else listOfNotNull(f.personId?.let { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }, closedLine(f, today)).joinToString(" · ")
                RecordRow(
                    f.title, detail,
                    lead = { if (f.status == FollowUpStatus.OPEN && task == null) PixelIconButton("check", "Mark ${f.title} done", { link.complete(f.id); model.haptic() }, accent) },
                    trail = { if (f.status != FollowUpStatus.OPEN && task == null) PixelButton("Reopen", { link.reopen(f.id) }, color = Pixel.InkSoft, compact = true) },
                    onClick = { openId = if (openId == f.id) null else f.id }, onClickLabel = if (openId == f.id) "Hide options" else "Show options",
                )
                if (openId == f.id) FollowUpOptions(model, f) { openId = null }
            }
        }
    }
}

internal fun remindLabel(days: Int) = when (days) { 0 -> "On the day"; 1 -> "1 day before"; 7 -> "A week before"; else -> "$days days before" }

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
    var more by remember(f.id) { mutableStateOf(f.note.isNotBlank() || f.personId != null) }
    FormStack(Modifier.padding(bottom = Spacing.S)) {
        if (asking) {
            Confirm("Delete this follow-up?", "Delete", onConfirm = { link.deleteFollowUp(f.id); onDone() }, onKeep = { asking = false })
            return@FormStack
        }
        FormGroup {
            PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
            DateField("Day", due, { due = it }, model.store.today, accent, optional = true)
        }
        FormDisclosure("Additional details", more, { more = !more }) {
            FormGroup {
                PersonPicker(data.people, personId) { personId = it }
                PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 2, keyboardOptions = sentenceKeys)
            }
        }
        FormSave("Save", {
            val out = link.updateFollowUp(f.copy(title = title, due = due, note = note, personId = personId))
            if (out.record == null) message = out.refused ?: "That could not be saved." else onDone()
        }, accent, enabled = title.isNotBlank())
        Actions {
            if (f.status == FollowUpStatus.OPEN && planTask(model, f) == null) PixelButton("Cancel it", { link.cancel(f.id); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        val task = planTask(model, f)
        if (data.settings.reminders && f.status == FollowUpStatus.OPEN && f.due != null && !f.converted) {
            val now = data.followUps.firstOrNull { it.id == f.id }?.remind == true
            PixelButton(if (now) "Reminder on" else "Remind me on the day", { link.setFollowUpRemind(f.id, !now) }, Modifier.fillMaxWidth(), if (now) accent else Pixel.InkSoft, compact = true)
        }
        if (f.status == FollowUpStatus.OPEN) {
            if (task == null) PixelButton("Make it a Plan task", { model.link.convertToPlan(f.id); model.haptic() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            else PixelButton("Stop linking to Plan", { model.store.unlinkTask(task.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkSettingsView(model: AppModel) {
    val link = model.link
    val data = model.linkData
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<LinkKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Postcard Trail")
            ToggleRow("Find Pokémon from finished follow-ups", data.settings.postcardTrail) { link.setPostcardTrail(it) }
            if (data.settings.postcardTrail) ToggleRow("Friend Safari on each person", data.settings.safari) { link.setSafari(it) }
        }
    }
    if (data.settings.reminders) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Reminders")
            run {
                PixelChoice(listOf(8, 9, 12, 18, 20), data.settings.reminderHour, { "${it.toString().padStart(2, '0')}:00" }, { h -> if (h != null) link.setReminderHour(h) }, label = "At", key = accent)
                ToggleRow("Name it", data.settings.reminderNames) { link.setReminderNames(it) }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-link.json", link.export()) }, Modifier.fillMaxWidth(), accent, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    LinkKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-link-${k.name.lowercase()}.json", link.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            }
            note?.let { Report(model, it) }
            LinkKinds.entries.forEach { k ->
                if (asking == k) Confirm(
                    if (k == LinkKinds.PEOPLE) "Erase all people and their notes? Dates, follow-ups and memories stay, with no person on them. This cannot be undone." else "Erase all ${k.label.lowercase()}? This cannot be undone.",
                    "Erase", "Keep them", onConfirm = { link.erase(k); asking = null }, onKeep = { asking = null },
                ) else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) Confirm("Erase every Link record? This cannot be undone.", "Erase all", "Keep them", onConfirm = { link.eraseAll(); askingAll = false }, onKeep = { askingAll = false })
            else PixelButton("Erase all Link records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
}
