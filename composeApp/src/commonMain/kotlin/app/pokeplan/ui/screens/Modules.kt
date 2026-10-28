package app.pokeplan.ui.screens

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
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing

/**
 * The life modules (docs/ecosystem.md, section 4), each a section of the Modules tab. Each one is a different part of
 * life; everything done in any of them looks after the same Pokémon, which live in the shared tabs (Home, Pokémon, Me).
 */
enum class Module(val label: String, val icon: String) {
    PLAN("Plan", "journal"), CARE("Care", "soothe-bell"),
}

/** Plan's screens: tasks and focus, and the trends, goals and weekly review that read them. */
enum class PlanSection(val label: String, val icon: String) {
    PLANNER("Planner", "journal"), FOCUS("Focus", "mystery-egg"), GROWTH("Growth", "exp-share"), GOALS("Goals", "star-piece"), REVIEW("Review", "oaks-letter"),
}

/**
 * The Modules tab: the list of modules, or the one open in [AppModel.module], which stays open while other tabs are
 * used. [focusTab] is false where there is no Focus (desktop and the web).
 */
@Composable
fun ModulesScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    when (model.module) {
        Module.PLAN -> PlanScreen(model, focusTab, openPokemon)
        // Care switched off in Settings hides it; what it saved is kept.
        Module.CARE -> if (model.data.settings.careModule) CareScreen(model, openPokemon) else ModulesMenu(model)
        null -> ModulesMenu(model)
    }
}

@Composable
private fun ModulesMenu(model: AppModel) {
    model.careData // redraw when Care changes
    val today = model.store.todayTasks().size
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Modules", subtitle = "Each looks after the same Pokémon", icon = "explorer-kit")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val routines = model.care.todayRoutines()
            val routinesLeft = routines.count { !it.doneOn(model.care.today) }
            MenuGrid(
                listOfNotNull(
                    MenuEntry(Module.PLAN.name, Module.PLAN.icon, Module.PLAN.label, if (today == 0) "tasks and focus" else "$today to do today", tint = Pixel.Red),
                    if (!model.data.settings.careModule) null else MenuEntry(
                        Module.CARE.name, Module.CARE.icon, Module.CARE.label, if (routinesLeft == 0) "routines and calm" else "$routinesLeft routines today", tint = Pixel.Green,
                    ),
                ),
            ) { key -> model.module = Module.valueOf(key) }
            Text(
                "Each module is one part of your day. Your party, Pokédex and Bag are shared by all of them, under Pokémon." +
                    if (model.data.settings.careModule) "" else " Care is switched off in Me, Settings.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlanScreen(model: AppModel, focusTab: Boolean, openPokemon: (String) -> Unit) {
    val section = model.planSection
    val toMenu = { model.planSection = null }
    when (section) {
        null -> {
            val toModules = { model.module = null }
            BackHandler(enabled = true, onBack = toModules)
            PlanMenu(model, focusTab, toModules)
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
        else -> SectionScaffold(section.label, Module.PLAN.label, onBack = toMenu, icon = section.icon) {
            when (section) {
                PlanSection.GROWTH -> GrowthView(model)
                PlanSection.GOALS -> GoalsView(model)
                else -> ReviewView(model)
            }
        }
    }
}

@Composable
private fun PlanMenu(model: AppModel, focusTab: Boolean, onBack: () -> Unit) {
    val data = model.data
    val store = model.store
    val today = store.todayTasks().size
    val session = data.focus
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.PLAN.label, subtitle = "Modules", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            MenuGrid(
                listOfNotNull(
                    MenuEntry(PlanSection.PLANNER.name, PlanSection.PLANNER.icon, "Planner", if (today == 0) "nothing left today" else "$today to do today", tint = Pixel.Red),
                    if (!focusTab) null else MenuEntry(
                        PlanSection.FOCUS.name, PlanSection.FOCUS.icon, "Focus",
                        if (session == null) "hatch an egg" else "egg incubating", badge = if (session != null) 1 else 0, tint = Pixel.Warning,
                    ),
                    if (store.sees(Feature.GROWTH_SCREEN)) MenuEntry(PlanSection.GROWTH.name, PlanSection.GROWTH.icon, "Growth", "effort and trends", tint = Pixel.Green) else null,
                    if (!store.sees(Feature.GOALS)) null else MenuEntry(PlanSection.GOALS.name, PlanSection.GOALS.icon, "Goals", if (data.goals.size == 1) "1 goal" else "${data.goals.size} goals", tint = Pixel.Warning),
                    MenuEntry(PlanSection.REVIEW.name, PlanSection.REVIEW.icon, "Review", if (store.canReview()) "ready this week" else "done this week", badge = if (store.canReview()) 1 else 0, tint = Pixel.Blue),
                ),
            ) { key -> model.planSection = PlanSection.valueOf(key) }
        }
    }
}
