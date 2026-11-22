package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import app.pokeplan.ui.BackHandler
import app.pokeplan.core.reward.Feature
import app.pokeplan.plan.Task
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
import app.pokeplan.ui.plan.PlanActionItems
import app.pokeplan.ui.plan.PlanOverview
import app.pokeplan.ui.plan.PlanView
import app.pokeplan.ui.plan.ProgressView
import app.pokeplan.ui.plan.planTasks
import app.pokeplan.ui.pokemon.PlanPartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.world.WorldScreen

enum class Module(val label: String, val icon: String, val look: RegionLook) {
    PLAN("Plan", "journal", RegionLook.PLAN), CARE("Care", "soothe-bell", RegionLook.CARE), HEAL("Heal", "potion", RegionLook.HEAL), MINT("Mint", "amulet-coin", RegionLook.MINT), BASE("Base", "chipped-pot", RegionLook.BASE), LINK("Link", "link-cord", RegionLook.LINK), WORLD("World", "town-map", RegionLook.WORLD),
}

enum class PlanSection(val label: String, val icon: String) {
    TASKS("Tasks", "journal"), CALENDAR("Calendar", "town-map"), FOCUS("Focus", "mystery-egg"), GOALS("Goals", "star-piece"), SETTINGS("Settings", "oaks-letter"),
}

private fun focusOn(model: AppModel, task: Task) { model.focusTaskId = task.id; model.planSection = PlanSection.FOCUS }

@Composable
fun ModulesScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    val open = model.module
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

/** The Modules search opens and closes in this long: snappy, and skipped when Reduce motion is on. */
private const val SEARCH_MS = 140

/** How far the current pull at the top of the list has gone, in pixels. */
private class SearchPull { var pulled = 0f }

@Composable
private fun ModulesMenu(model: AppModel) {
    model.careData
    model.healData
    model.mintData
    model.baseData
    model.linkData
    val today = model.store.todayTasks().size
    val reduce = model.data.settings.reduceMotion
    val scroll = rememberScrollState()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val closeSearch = { searchOpen = false; query = "" }
    BackHandler(enabled = searchOpen, onBack = closeSearch)
    val pull = remember { SearchPull() }
    val searchThreshold = with(LocalDensity.current) { Spacing.XXL.toPx() }
    // A drag left over at the top of the list is the pull that opens the search; a swipe up there puts
    // away a search with nothing typed in it yet. Both are consumed so the page does not bounce first.
    val searchReveal = remember(scroll, searchThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || scroll.value > 0 || available.y <= 0f) {
                    pull.pulled = 0f
                    return Offset.Zero
                }
                // A drag down at the top cannot scroll: before the search opens it is the pull that opens
                // it, and while it is open the page simply stays still.
                if (searchOpen) return Offset(0f, available.y)
                pull.pulled += available.y
                if (pull.pulled < searchThreshold) return Offset(0f, available.y)
                pull.pulled = 0f
                searchOpen = true
                return Offset(0f, available.y)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !searchOpen || scroll.value > 0 || query.isNotBlank() || available.y >= 0f) return Offset.Zero
                closeSearch()
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pull.pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Modules", icon = "explorer-kit", trailing = {
            app.pokeplan.ui.theme.PixelKey({ model.gearOpen = true }, key = Pixel.InkSoft, compact = true, sound = Sfx.DOOR_ENTER) { look -> app.pokeplan.ui.theme.KeyLabel("Gear", look) }
        })
        Column(
            Modifier.weight(1f).nestedScroll(searchReveal).verticalScroll(scroll).padding(horizontal = Spacing.Page, vertical = Spacing.L),
        ) {
            val routines = model.care.todayRoutines()
            val routinesLeft = routines.count { !it.doneOn(model.care.today) }
            AnimatedVisibility(
                visible = searchOpen,
                enter = if (reduce) EnterTransition.None else expandVertically(tween(SEARCH_MS), expandFrom = Alignment.Top) + fadeIn(tween(SEARCH_MS)),
                exit = if (reduce) ExitTransition.None else shrinkVertically(tween(SEARCH_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(SEARCH_MS)),
            ) {
                // The bottom padding is inside the animated block, so the list starts at the page inset
                // while the search is hidden and slides down as it opens, with the section gap.
                Column(Modifier.padding(bottom = Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    JumpSearch(model, query, { query = it }, onDone = closeSearch)
                }
            }
            MenuList(
                listOfNotNull(
                    MenuEntry(Module.PLAN.name, Module.PLAN.icon, Module.PLAN.label, if (today == 0) "tasks and focus" else "$today to do today", tint = ModuleAccent.PLAN.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.MINT.name, Module.MINT.icon, Module.MINT.label, mintSubtitle(model), tint = ModuleAccent.MINT.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.BASE.name, Module.BASE.icon, Module.BASE.label, baseSubtitle(model), tint = ModuleAccent.BASE.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(
                        Module.CARE.name, Module.CARE.icon, Module.CARE.label, if (routinesLeft == 0) "routines and calm" else if (routinesLeft == 1) "1 routine today" else "$routinesLeft routines today", tint = ModuleAccent.CARE.accent,
                    ),
                    MenuEntry(Module.HEAL.name, Module.HEAL.icon, Module.HEAL.label, healSubtitle(model), tint = ModuleAccent.HEAL.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.LINK.name, Module.LINK.icon, Module.LINK.label, linkSubtitle(model), tint = ModuleAccent.LINK.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.WORLD.name, Module.WORLD.icon, Module.WORLD.label, "dungeons and expeditions", badge = model.data.world.finds.size, tint = ModuleAccent.WORLD.accent, sound = Sfx.DOOR_ENTER),
                ),
                rowTint = true,
            ) { key -> model.module = Module.valueOf(key) }
        }
    }
}

@Composable
private fun PlanScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    ScreenSwitch(model.planSection, depth = { if (it == null) 0 else 1 }) { section -> PlanPage(model, section, focusTab, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlanPage(model: AppModel, section: PlanSection?, focusTab: Boolean, openPokemon: (String) -> Unit) {
    val back = { model.planSection = null }
    when (section) {
        null -> {
            val toModules = { model.module = null }
            BackHandler(enabled = true, onBack = toModules)
            PlanHome(model, focusTab, toModules, openPokemon)
        }
        PlanSection.FOCUS -> {
            BackHandler(enabled = true, onBack = back)
            FocusScreen(model, openPokemon, onBack = back)
        }
        PlanSection.GOALS -> SectionScaffold(section.label, null, onBack = back, icon = section.icon, color = Module.PLAN.look.header) { GoalsView(model) }
        else -> PlanView(model, section, onBack = back, open = { model.planSection = it }, onFocus = if (focusTab) ({ t -> focusOn(model, t) }) else null)
    }
}

@Composable
private fun PlanHome(model: AppModel, focusTab: Boolean, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.data
    val store = model.store
    val session = data.focus
    val open = planTasks(model).count { it.isOpen }
    val sections = listOfNotNull(
        MenuEntry(PlanSection.TASKS.name, PlanSection.TASKS.icon, PlanSection.TASKS.label, tint = ModuleAccent.PLAN.accent, count = if (open > 0) "$open" else ""),
        MenuEntry(PlanSection.CALENDAR.name, PlanSection.CALENDAR.icon, PlanSection.CALENDAR.label, tint = ModuleAccent.PLAN.accent),
        if (!focusTab) null else MenuEntry(PlanSection.FOCUS.name, PlanSection.FOCUS.icon, PlanSection.FOCUS.label, badge = if (session != null) 1 else 0, tint = ModuleAccent.PLAN.accent, count = if (session != null) "on" else ""),
        if (!store.sees(Feature.GOALS)) null else MenuEntry(PlanSection.GOALS.name, PlanSection.GOALS.icon, PlanSection.GOALS.label, tint = ModuleAccent.PLAN.accent, count = if (data.goals.isEmpty()) "" else "${data.goals.size}"),
        MenuEntry(PlanSection.SETTINGS.name, PlanSection.SETTINGS.icon, PlanSection.SETTINGS.label, tint = app.pokeplan.ui.theme.Pixel.InkSoft),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            Module.PLAN.label, onBack = onBack, color = Module.PLAN.look.header,
            trailing = { SectionsKey(Module.PLAN.label, sections) { key -> model.planSection = PlanSection.valueOf(key) } },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.PLAN, data.player.planLead, store.moduleEvent(null), resting = session != null, openPokemon = openPokemon)
            FirstRecordEgg(model, app.pokeplan.app.Tutorials.PLAN_FOCUS, model.data.tasks.count { it.completedAtMillis != null })
            PlanOverview(model)
            PlanActionItems(model, onFocus = if (!focusTab) null else ({ t -> focusOn(model, t) }))
            PlanPartyPanel(model, openPokemon)
        }
    }
}
