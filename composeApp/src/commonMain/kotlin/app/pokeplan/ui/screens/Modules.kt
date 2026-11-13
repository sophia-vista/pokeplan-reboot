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
import androidx.compose.ui.backhandler.BackHandler
import app.pokeplan.core.reward.Feature
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.LocalFrameTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.frameToneOf
import app.pokeplan.ui.theme.RegionLook
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing

/**
 * The life modules (docs/ecosystem.md, section 4), each a section of the Modules tab. Each one is a different part of
 * life; everything done in any of them looks after the same Pokémon, which live in the shared tabs (Home, Pokémon, Me).
 */
enum class Module(val label: String, val icon: String, val look: RegionLook) {
    PLAN("Plan", "journal", RegionLook.PLAN), CARE("Care", "soothe-bell", RegionLook.CARE), HEAL("Heal", "potion", RegionLook.HEAL), MINT("Mint", "amulet-coin", RegionLook.MINT), HOME("Base", "chipped-pot", RegionLook.BASE), LINK("Link", "link-cord", RegionLook.LINK), WORLD("World", "town-map", RegionLook.WORLD),
}

/** Plan's screens: tasks and focus, and the trends, goals and weekly review that read them. */
enum class PlanSection(val label: String, val icon: String) {
    PLANNER("Planner", "journal"), FOCUS("Focus", "mystery-egg"), GROWTH("Progress", "exp-share"), GOALS("Goals", "star-piece"), REVIEW("Review", "oaks-letter"), SETTINGS("Settings", "storage-key"),
}

/**
 * The Modules tab: the list of modules, or the one open in [AppModel.module], which stays open while other tabs are
 * used. [focusTab] is false where there is no Focus (desktop and the web).
 */
@Composable
fun ModulesScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    // Care switched off in Settings hides it; what it saved is kept.
    val open = model.module?.takeIf { it != Module.CARE || model.data.settings.careModule }
    ScreenSwitch(open, depth = { if (it == null) 0 else 1 }) { page ->
        when (page) {
            Module.PLAN -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.PLAN.look.accent)) { PlanScreen(model, focusTab, openPokemon) }
            Module.CARE -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.CARE.look.accent)) { ModuleGate(model, Module.CARE) { CareScreen(model, openPokemon) } }
            Module.HEAL -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.HEAL.look.accent)) { ModuleGate(model, Module.HEAL) { HealScreen(model, openPokemon) } }
            Module.MINT -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.MINT.look.accent)) { MintScreen(model, openPokemon) }
            Module.HOME -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.HOME.look.accent)) { ModuleGate(model, Module.HOME) { BaseScreen(model, openPokemon) } }
            Module.LINK -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.LINK.look.accent)) { ModuleGate(model, Module.LINK) { LinkScreen(model, openPokemon) } }
            Module.WORLD -> androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(Module.WORLD.look.accent)) { WorldScreen(model, openPokemon) }
            null -> ModulesMenu(model)
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
        ScreenHeader("Modules", icon = "explorer-kit")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val routines = model.care.todayRoutines()
            val routinesLeft = routines.count { !it.doneOn(model.care.today) }
            // Pokégear first and World last, the six between in the order of the accent rainbow (ModuleAccent). A module that is not built
            // yet is a quiet placeholder in its own colour, so the list shows where it will sit.
            MenuGrid(
                listOfNotNull(
                    MenuEntry("GEAR", "explorer-kit", "Gear", tint = ModuleAccent.GEAR.accent, wide = true, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.PLAN.name, Module.PLAN.icon, Module.PLAN.label, if (today == 0) "tasks and focus" else "$today to do today", tint = ModuleAccent.PLAN.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.MINT.name, Module.MINT.icon, Module.MINT.label, mintSubtitle(model), tint = ModuleAccent.MINT.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.HOME.name, Module.HOME.icon, Module.HOME.label, baseSubtitle(model), tint = ModuleAccent.BASE.accent, sound = Sfx.DOOR_ENTER),
                    if (!model.data.settings.careModule) null else MenuEntry(
                        Module.CARE.name, Module.CARE.icon, Module.CARE.label, if (routinesLeft == 0) "routines and calm" else if (routinesLeft == 1) "1 routine today" else "$routinesLeft routines today", tint = ModuleAccent.CARE.accent,
                    ),
                    MenuEntry(Module.HEAL.name, Module.HEAL.icon, Module.HEAL.label, healSubtitle(model), tint = ModuleAccent.HEAL.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.LINK.name, Module.LINK.icon, Module.LINK.label, linkSubtitle(model), tint = ModuleAccent.LINK.accent, sound = Sfx.DOOR_ENTER),
                    MenuEntry(Module.WORLD.name, Module.WORLD.icon, Module.WORLD.label, "dungeons and expeditions", badge = model.data.world.finds.size, tint = ModuleAccent.WORLD.accent, wide = true, sound = Sfx.DOOR_ENTER),
                ),
            ) { key -> if (key == "GEAR") model.gearOpen = true else model.module = Module.valueOf(key) }
        }
    }
}

@Composable
private fun PlanScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    val toMenu = { model.planSection = null }
    ScreenSwitch(model.planSection, depth = { if (it == null) 0 else 1 }) { section -> PlanPage(model, section, focusTab, toMenu, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlanPage(model: AppModel, section: PlanSection?, focusTab: Boolean, toMenu: () -> Unit, openPokemon: (String) -> Unit) {
    when (section) {
        null -> {
            val toModules = { model.module = null }
            BackHandler(enabled = true, onBack = toModules)
            PlanMenu(model, focusTab, toModules, openPokemon)
        }
        // Planner and Focus have their own headers; the screens they open inside handle Back before this does.
        PlanSection.PLANNER -> {
            BackHandler(enabled = true, onBack = toMenu)
            PlannerScreen(model, startView = if (model.addTaskRequested) 0 else -1, onBack = toMenu)
        }
        PlanSection.FOCUS -> {
            BackHandler(enabled = true, onBack = toMenu)
            FocusScreen(model, openPokemon, onBack = toMenu)
        }
        else -> SectionScaffold(section.label, null, onBack = toMenu, icon = section.icon, color = Module.PLAN.look.header) {
            when (section) {
                PlanSection.GROWTH -> GrowthView(model)
                PlanSection.GOALS -> GoalsView(model)
                PlanSection.SETTINGS -> PlanSettings(model)
                else -> ReviewView(model)
            }
        }
    }
}

/** How many of today's tasks Plan's page lists; the Planner has the rest. */
private const val TODAY_SHOWN = 3

/**
 * Plan's own page, laid out like Care's so the two modules read alike: its buddy in Plan's town, new rewards, the whole party
 * with its perks, today's tasks, then the sections.
 */
@Composable
private fun PlanMenu(model: AppModel, focusTab: Boolean, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.data
    val store = model.store
    val today = store.todayTasks().size
    val session = data.focus
    val open = store.openTodayTasks()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.PLAN.label, onBack = onBack, color = Module.PLAN.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.PLAN, data.player.planLead, store.moduleEvent(null), resting = session != null, openPokemon = openPokemon)
            TutorialCard(model, app.pokeplan.app.Tutorials.PLAN_FOCUS)
            PlanPartyPanel(model, openPokemon)
            SectionLabel("Today", icon = PlanSection.PLANNER.icon)
            if (open.isEmpty()) {
                val doneToday = data.tasks.any { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }
                EmptyState(
                    if (doneToday) "All done for today" else "Nothing planned for today",
                    if (doneToday) "Rest, or add one more if you feel like it." else "One small thing is plenty. Each one you finish pays candies.",
                    action = "Add a task" to { model.addTaskRequested = true; model.planSection = PlanSection.PLANNER },
                )
            } else {
                // The one to do next first, with Focus on this and Pick for me (+10% candies); then a few more.
                val next = open.firstOrNull { it.id == data.pickedTaskId } ?: open.first()
                NextUp(
                    task = next,
                    onDone = { model.completeTask(next.id) },
                    onFocus = if (!focusTab) null else ({ model.focusTaskId = next.id; model.planSection = PlanSection.FOCUS }),
                    onChoose = if (open.size > 1 || data.pickedTaskId != next.id) ({ store.pickForMe() }) else null,
                    picked = next.id == data.pickedTaskId,
                )
                open.filter { it.id != next.id }.take(TODAY_SHOWN - 1).forEach { t -> TaskRow(t, onDone = { model.completeTask(t.id) }) }
                if (open.size > TODAY_SHOWN) Text("${open.size - TODAY_SHOWN} more in the Planner", style = MaterialTheme.typography.bodyMedium)
            }
            MenuGrid(
                listOfNotNull(
                    MenuEntry(PlanSection.PLANNER.name, PlanSection.PLANNER.icon, "Planner", if (today == 0) "nothing left today" else "$today to do today", tint = ModuleAccent.PLAN.accent, sound = Sfx.DOOR_ENTER),
                    if (!focusTab) null else MenuEntry(
                        PlanSection.FOCUS.name, PlanSection.FOCUS.icon, "Focus",
                        if (session == null) "hatch an egg" else "egg incubating", badge = if (session != null) 1 else 0, tint = ModuleAccent.PLAN.accent,
                    ),
                    if (store.sees(Feature.GROWTH_SCREEN)) MenuEntry(PlanSection.GROWTH.name, PlanSection.GROWTH.icon, "Progress", "effort and trends", tint = ModuleAccent.PLAN.accent) else null,
                    if (!store.sees(Feature.GOALS)) null else MenuEntry(PlanSection.GOALS.name, PlanSection.GOALS.icon, "Goals", if (data.goals.size == 1) "1 goal" else "${data.goals.size} goals", tint = ModuleAccent.PLAN.accent),
                    MenuEntry(PlanSection.REVIEW.name, PlanSection.REVIEW.icon, "Review", if (store.canReview()) "ready this week" else "done this week", badge = if (store.canReview()) 1 else 0, tint = ModuleAccent.PLAN.accent),
                    MenuEntry(PlanSection.SETTINGS.name, PlanSection.SETTINGS.icon, "Settings", "focus, week, calendars", tint = Pixel.InkSoft),
                ),
            ) { key -> model.planSection = PlanSection.valueOf(key) }
        }
    }
}
