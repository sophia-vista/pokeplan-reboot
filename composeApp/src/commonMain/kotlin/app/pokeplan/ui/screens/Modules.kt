package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import app.pokeplan.core.reward.Feature
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.LocalFrameTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.frameToneOf
import app.pokeplan.ui.theme.RegionLook
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.base.BaseScreen
import app.pokeplan.ui.base.baseSubtitle
import app.pokeplan.ui.care.CareScreen
import app.pokeplan.ui.heal.HealScreen
import app.pokeplan.ui.heal.healSubtitle
import app.pokeplan.ui.link.LinkScreen
import app.pokeplan.ui.link.linkSubtitle
import app.pokeplan.ui.mint.MintScreen
import app.pokeplan.ui.mint.mintSubtitle
import app.pokeplan.ui.plan.AddTo
import app.pokeplan.ui.plan.FocusScreen
import app.pokeplan.ui.plan.GoalsView
import app.pokeplan.ui.plan.PlanView
import app.pokeplan.ui.plan.ProgressView
import app.pokeplan.ui.plan.QuickAddBox
import app.pokeplan.ui.plan.TodayList
import app.pokeplan.ui.plan.planTasks
import app.pokeplan.ui.pokemon.PlanPartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.world.WorldScreen

/**
 * The life modules (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md), each a section of the Modules tab. Each one is a different part of
 * life; everything done in any of them looks after the same Pokémon, which live in the shared tabs (Home, Pokémon, Me).
 */
enum class Module(val label: String, val icon: String, val look: RegionLook) {
    PLAN("Plan", "journal", RegionLook.PLAN), CARE("Care", "soothe-bell", RegionLook.CARE), HEAL("Heal", "potion", RegionLook.HEAL), MINT("Mint", "amulet-coin", RegionLook.MINT), BASE("Base", "chipped-pot", RegionLook.BASE), LINK("Link", "link-cord", RegionLook.LINK), WORLD("World", "town-map", RegionLook.WORLD),
}

/**
 * Plan's sections behind its Today page (PLAN.md section 3): the menu's Inbox, Upcoming, Lists, Calendar, Focus and More, and
 * the places More holds (Progress with the weekly review, Goals, Targets, All tasks). Today itself is the module's page (null).
 */
enum class PlanSection(val label: String, val icon: String) {
    INBOX("Inbox", "seal-bag"), UPCOMING("Upcoming", "timer-ball"), LISTS("Lists", "journal"), CALENDAR("Calendar", "town-map"), FOCUS("Focus", "mystery-egg"), MORE("More", "storage-key"),
    PROGRESS("Progress", "exp-share"), GOALS("Goals", "star-piece"), TARGETS("Targets", "heart-scale"), ALL("All tasks", "scope-lens"),
    OUTSTANDING("Outstanding", "journal");

    /** The section Back returns to: More for the places it holds, Today (null) for the rest. */
    val parent: PlanSection? get() = if (this in MORE_HOLDS) MORE else null

    companion object {
        val MORE_HOLDS = setOf(PROGRESS, GOALS, TARGETS, ALL, OUTSTANDING)
        /** Today's icon, for its empty state. */
        const val TODAY_ICON = "oaks-parcel"
    }
}

/**
 * The Modules tab: the list of modules, or the one open in [AppModel.module], which stays open while other tabs are
 * used. [focusTab] is false where there is no Focus (desktop and the web).
 */
@Composable
fun ModulesScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    // Care switched off in Settings hides it; what it saved is kept.
    val open = model.module?.takeIf { it != Module.CARE || model.data.settings.careModule }
    androidx.compose.runtime.CompositionLocalProvider(LocalFocusTab provides focusTab) {
    ScreenSwitch(open, depth = { if (it == null) 0 else 1 }) { page ->
        when (page) {
            Module.PLAN -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.PLAN.look.accent)) { PlanScreen(model, focusTab, openPokemon) }
            Module.CARE -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.CARE.look.accent)) { ModuleGate(model, Module.CARE) { CareScreen(model, openPokemon) } }
            Module.HEAL -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.HEAL.look.accent)) { ModuleGate(model, Module.HEAL) { HealScreen(model, openPokemon) } }
            Module.MINT -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.MINT.look.accent)) { MintScreen(model, openPokemon) }
            Module.BASE -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.BASE.look.accent)) { ModuleGate(model, Module.BASE) { BaseScreen(model, openPokemon) } }
            Module.LINK -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.LINK.look.accent)) { ModuleGate(model, Module.LINK) { LinkScreen(model, openPokemon) } }
            Module.WORLD -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.WORLD.look.accent)) { WorldScreen(model, openPokemon) }
            null -> ModulesMenu(model)
        }
    }
    }
}

@Composable
private fun ModulesMenu(model: AppModel) {
    model.careData // redraw when Care changes
    model.healData // and when Heal does
    model.mintData // and Mint
    model.baseData // and Home
    model.linkData // and Link
    val today = model.store.todayTasks().size
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Modules", icon = "explorer-kit", trailing = {
            app.pokeplan.ui.theme.PixelKey({ model.gearOpen = true }, key = Pixel.InkSoft, compact = true, sound = Sfx.DOOR_ENTER) { look -> app.pokeplan.ui.theme.KeyLabel("Gear", look) }
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val routines = model.care.todayRoutines()
            val routinesLeft = routines.count { !it.doneOn(model.care.today) }
            // Gear is the key in the header (a tool drawer, not a life module). The modules follow in the order of the accent rainbow
            // (ModuleAccent), World last; "Jump to" opens any section by name.
            JumpSearch(model)
            MenuGrid(
                listOfNotNull(
                    MenuEntry(Module.PLAN.name, Module.PLAN.icon, Module.PLAN.label, if (today == 0) "tasks and focus" else "$today to do today", tint = ModuleAccent.PLAN.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.MINT.name, Module.MINT.icon, Module.MINT.label, mintSubtitle(model), tint = ModuleAccent.MINT.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.BASE.name, Module.BASE.icon, Module.BASE.label, baseSubtitle(model), tint = ModuleAccent.BASE.accent, sound = Sfx.DOOR_ENTER),
                    if (!model.data.settings.careModule) null else MenuEntry(
                        Module.CARE.name, Module.CARE.icon, Module.CARE.label, if (routinesLeft == 0) "routines and calm" else if (routinesLeft == 1) "1 routine today" else "$routinesLeft routines today", tint = ModuleAccent.CARE.accent,
                    ),
                    MenuEntry(Module.HEAL.name, Module.HEAL.icon, Module.HEAL.label, healSubtitle(model), tint = ModuleAccent.HEAL.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.LINK.name, Module.LINK.icon, Module.LINK.label, linkSubtitle(model), tint = ModuleAccent.LINK.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.WORLD.name, Module.WORLD.icon, Module.WORLD.label, "dungeons and expeditions", badge = model.data.world.finds.size, tint = ModuleAccent.WORLD.accent, wide = true, sound = Sfx.DOOR_ENTER),
                ),
            ) { key -> model.module = Module.valueOf(key) }
        }
    }
}

@Composable
private fun PlanScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    ScreenSwitch(model.planSection, depth = { if (it == null) 0 else if (it.parent == null) 1 else 2 }) { section -> PlanPage(model, section, focusTab, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlanPage(model: AppModel, section: PlanSection?, focusTab: Boolean, openPokemon: (String) -> Unit) {
    val back = { model.planSection = section?.parent }
    val toToday = { model.planSection = null }
    when (section) {
        null -> {
            val toModules = { model.module = null }
            BackHandler(enabled = true, onBack = toModules)
            PlanToday(model, focusTab, toModules, openPokemon)
        }
        // Focus has its own header; the screens it opens inside handle Back before this does.
        PlanSection.FOCUS -> {
            BackHandler(enabled = true, onBack = back)
            FocusScreen(model, openPokemon, onBack = back)
        }
        PlanSection.MORE -> SectionScaffold(section.label, null, onBack = back, icon = section.icon, color = Module.PLAN.look.header) { PlanMoreMenu(model) }
        PlanSection.PROGRESS, PlanSection.GOALS -> SectionScaffold(section.label, null, onBack = back, icon = section.icon, color = Module.PLAN.look.header) {
            if (section == PlanSection.GOALS) GoalsView(model) else ProgressView(model)
        }
        else -> PlanView(model, section, onBack = back, open = { model.planSection = it }, toToday = toToday)
    }
}

/**
 * Plan's own page is its Today (PLAN.md section 3.1): its lead in Plan's town, quick add, the day's tasks and targets, then one
 * menu of rows with counts behind the header's Sections key (Inbox, Upcoming, Lists, Focus, More) and the party as one row.
 */
@Composable
private fun PlanToday(model: AppModel, focusTab: Boolean, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.data
    val store = model.store
    val session = data.focus
    val live = planTasks(model)
    fun count(n: Int) = if (n > 0) "$n" else ""
    val sections = listOfNotNull(
            MenuEntry(PlanSection.INBOX.name, PlanSection.INBOX.icon, PlanSection.INBOX.label, tint = ModuleAccent.PLAN.accent, count = count(TaskViews.inbox(live).size)),
            MenuEntry(PlanSection.UPCOMING.name, PlanSection.UPCOMING.icon, PlanSection.UPCOMING.label, tint = ModuleAccent.PLAN.accent, count = count(TaskViews.upcoming(live, store.now).size)),
            MenuEntry(PlanSection.LISTS.name, PlanSection.LISTS.icon, PlanSection.LISTS.label, tint = ModuleAccent.PLAN.accent, count = count(store.activeLists.size)),
            if (!focusTab) null else MenuEntry(PlanSection.FOCUS.name, PlanSection.FOCUS.icon, PlanSection.FOCUS.label, badge = if (session != null) 1 else 0, tint = ModuleAccent.PLAN.accent, count = if (session != null) "on" else ""),
            MenuEntry(PlanSection.MORE.name, PlanSection.MORE.icon, PlanSection.MORE.label, badge = if (store.canReview()) 1 else 0, tint = ModuleAccent.PLAN.accent),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.PLAN.label, onBack = onBack, color = Module.PLAN.look.header, footer = { ModuleNavKeys(model, Module.PLAN, sections, { key -> model.planSection = PlanSection.valueOf(key) }) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.PLAN, data.player.planLead, store.moduleEvent(null), resting = session != null, openPokemon = openPokemon)
            FirstRecordEgg(model, app.pokeplan.app.Tutorials.PLAN_FOCUS, model.data.tasks.count { it.completedAtMillis != null })
            QuickAddBox(model, AddTo.TODAY)
            TodayList(model, onFocus = if (!focusTab) null else ({ t -> model.focusTaskId = t.id; model.planSection = PlanSection.FOCUS }))
            PlanPartyPanel(model, openPokemon)
        }
    }
}

/** More: the Plan places visited less often, as rows with counts; the weekly review waits in Progress. */
@Composable
private fun PlanMoreMenu(model: AppModel) {
    val store = model.store
    val data = model.data
    val targets = store.frequencyTasks()
    MenuList(
        listOfNotNull(
            MenuEntry(PlanSection.OUTSTANDING.name, PlanSection.OUTSTANDING.icon, PlanSection.OUTSTANDING.label, tint = ModuleAccent.PLAN.accent, count = planTasks(model).count { it.isOpen }.let { if (it > 0) "$it" else "" }),
            MenuEntry(PlanSection.PROGRESS.name, PlanSection.PROGRESS.icon, PlanSection.PROGRESS.label, badge = if (store.canReview()) 1 else 0, tint = ModuleAccent.PLAN.accent, count = if (store.canReview()) "review" else ""),
            if (!store.sees(Feature.GOALS)) null else MenuEntry(PlanSection.GOALS.name, PlanSection.GOALS.icon, PlanSection.GOALS.label, tint = ModuleAccent.PLAN.accent, count = if (data.goals.isEmpty()) "" else "${data.goals.size}"),
            MenuEntry(PlanSection.TARGETS.name, PlanSection.TARGETS.icon, PlanSection.TARGETS.label, tint = ModuleAccent.PLAN.accent, count = if (targets.isEmpty()) "" else "${targets.count { store.today in it.completions }}/${targets.size}"),
            MenuEntry(PlanSection.ALL.name, PlanSection.ALL.icon, PlanSection.ALL.label, tint = ModuleAccent.PLAN.accent, count = planTasks(model).count { it.isOpen }.let { if (it > 0) "$it" else "" }),
        ),
    ) { key -> model.planSection = PlanSection.valueOf(key) }
}
