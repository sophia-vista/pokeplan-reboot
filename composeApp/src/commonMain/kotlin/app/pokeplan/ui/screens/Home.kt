package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.pokeplan.care.Visitors
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.Sprite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import app.pokeplan.ui.WhatsNew
import app.pokeplan.ui.AppVersion
import androidx.compose.ui.semantics.semantics
import app.pokeplan.ui.theme.spoken
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.theme.RegionLook
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.MiniQuest
import app.pokeplan.plan.Task
import app.pokeplan.plan.EventCompanions
import app.pokeplan.world.WorldSim
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.describe
import app.pokeplan.ui.isWorldSource
import app.pokeplan.ui.Acquisition
import app.pokeplan.ui.acquisitionOfSource
import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.ExpBar
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.frameToneOf
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.StatChip
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.care.CareSection
import app.pokeplan.ui.heal.HealSection
import app.pokeplan.app.world.worldReport
import app.pokeplan.ui.care.MoodKeys
import app.pokeplan.ui.plan.taskMeta
import app.pokeplan.base.BaseRules
import app.pokeplan.base.ChoreState
import app.pokeplan.base.DueState
import app.pokeplan.heal.HealRules
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.plan.Rank
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.base.BaseSection
import app.pokeplan.ui.link.LinkSection
import app.pokeplan.ui.mint.MintSection
import app.pokeplan.ui.mint.mintSubtitle
import app.pokeplan.ui.world.WorldSection
import app.pokeplan.world.Dungeons
import androidx.compose.runtime.key

@Composable
fun HomeScreen(model: AppModel, openPokemon: (String) -> Unit = {}, go: (Place) -> Unit) {
    val store = model.store
    val data = model.data
    val points = data.player.trainerPoints

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = data.trainerName,
            subtitle = TrainerRank.nameFor(points) + store.streak.current.let { if (it > 0) ", $it day${if (it == 1) "" else "s"} streak" else "" },
            icon = "town-map",
            footer = { RankProgress(points) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    if (store.sees(Feature.COINS)) StatChip("${data.player.coins}", { ItemIcon("amulet-coin", size = 28.dp, description = "Poké") })
                    StatChip("${data.player.inventory[Items.RARE_CANDY]}", { ItemIcon("rare-candy", size = 28.dp, description = "Exp. Candies") })
                }
            },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            WhatsNewCard(model)
            RankUpCard(model)
            WelcomeBackCard(model)
            HomeBuddyStage(model) { data.player.buddyId?.let(openPokemon) }
            RewardBox(model)
            ModuleCards(model, go)
            QuickActionsCard(model, go)

            if (store.sees(Feature.MINI_QUESTS)) MiniQuestsCard(model, go)
            RecentCard(model, go)
        }
    }
}

@Composable
private fun RegionCard(
    name: String, icon: String, look: RegionLook, darkness: Float, lead: OwnedPokemon?, status: String?,
    still: Boolean, onOpen: () -> Unit, step: @Composable () -> Unit,
) {
    val shape = DialogueShape
    Column(
        Modifier.fillMaxWidth().dialogueBox(tone = frameToneOf(look.accent)).clip(shape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open $name", role = Role.Button, onClick = onOpen).padding(horizontal = Spacing.BoxX, vertical = Spacing.L),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val window = PixelShape
            Box(Modifier.size(80.dp).clip(window).border(Sizes.Hairline, Pixel.Border, window), contentAlignment = Alignment.BottomCenter) {
                SceneImage(look.scene, Modifier.matchParentSize(), darkness)
                if (lead != null) AnimatedSprite(lead.form.id, lead.isShiny, lead.nickname, size = Sizes.SpriteM, animate = !still, ground = 4.dp)
                else ItemIcon(icon, size = Sizes.SpriteS, description = null, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PixelTitle(name)
                if (lead != null) Text(leadLine(lead), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2)
                if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = Pixel.InkSoft)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.S).height(1.dp).background(Pixel.Border))
        // A Column, not a Box: a card with a main step and live lines must stack its rows, never draw them on top of each other.
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.BoxX, vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) { step() }
    }
}

@Composable
private fun StepRow(text: String, action: String?, color: Color = Pixel.Green, lead: @Composable () -> Unit = {}, onClick: () -> Unit = {}) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                lead()
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            if (action != null) PixelButton(action, onClick, Modifier.fillMaxWidth(), color = color, compact = true)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        lead()
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (action != null) PixelButton(action, onClick, color = color, compact = true)
    }
}

private fun leadLine(p: OwnedPokemon) = "${p.nickname}, Lv. ${p.level}"

/** One module on Home: how urgent it is, the status it shows, and the one thing worth doing from here. */
private data class ModuleConcern(
    val module: Module,
    val severity: Int,
    val status: String?,
    val step: String,
    val action: String? = null,
    val actionTone: Color = Pixel.Green,
    val rank: Rank? = null,
    /** The module's live, time-sensitive lines ([HomeOpportunities]), one row each under the main step. */
    val live: List<HomeLiveLine> = emptyList(),
    // Last, so a trailing lambda at a call site is the action.
    val onAction: () -> Unit = {},
)

private const val SOON_DAYS = 3
private const val LINK_DATES_DAYS = 7

private fun countOf(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

private fun placeOf(module: Module): Place = when (module) {
    Module.PLAN -> Place.PLAN
    Module.MINT -> Place.MINT
    Module.BASE -> Place.BASE
    Module.CARE -> Place.CARE
    Module.HEAL -> Place.HEAL
    Module.LINK -> Place.LINK
    Module.WORLD -> Place.WORLD
}

@Composable
private fun ModuleCards(model: AppModel, go: (Place) -> Unit) {
    val darkness = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
    // Built in the canonical order, then a stable sort puts the modules with something due first.
    val live = liveLines(model)
    val concerns = listOf(
        planConcern(model, go),
        mintConcern(model, go),
        baseConcern(model, go),
        careConcern(model, go),
        healConcern(model, go),
        linkConcern(model, go),
        worldConcern(model, go),
    ).map { concern ->
        val lines = live[concern.module].orEmpty()
        if (lines.isEmpty()) concern else concern.copy(severity = maxOf(concern.severity, HomeOpportunities.severityOf(lines.first())), live = lines)
    }.sortedByDescending { it.severity }
    concerns.forEach { concern -> key(concern.module.name) { ModuleCard(model, concern, darkness, go) } }
}

@Composable
private fun ModuleCard(model: AppModel, concern: ModuleConcern, darkness: Float, go: (Place) -> Unit) {
    RegionCard(
        concern.module.label, concern.module.icon, concern.module.look, darkness, leadOf(model, concern.module),
        concern.status, still = model.data.settings.reduceMotion, onOpen = { go(placeOf(concern.module)) },
    ) {
        StepRow(concern.step, concern.action, concern.actionTone, lead = { concern.rank?.let { RankBadge(it) } }, onClick = concern.onAction)
        concern.live.forEach { line -> key(line.id) { StepRow(line.text, line.action, ModuleAccent.valueOf(concern.module.name).accent, onClick = { openLive(model, go, line.kind) }) } }
    }
}

private fun leadOf(model: AppModel, module: Module): OwnedPokemon? {
    val player = model.data.player
    return when (module) {
        Module.PLAN -> player.planLead
        Module.MINT -> player.mintLead
        Module.BASE -> player.baseLead
        Module.CARE -> player.careLead
        Module.HEAL -> player.healLead
        Module.LINK -> player.linkLead
        Module.WORLD -> {
            val world = model.data.world
            val team = world.teams.firstOrNull { it.expedition != null } ?: world.teams.firstOrNull { it.members.isNotEmpty() }
            team?.members?.firstNotNullOfOrNull { id -> player.pokemon.firstOrNull { it.id == id } }
        }
    }
}

private fun planConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    val store = model.store
    val data = model.data
    val open = store.openTodayTasks()
    val next = open.firstOrNull { it.id == data.pickedTaskId } ?: open.firstOrNull()
    val doneToday = store.doneToday()
    val total = doneToday + open.size
    val overdue = TaskViews.overdue(data.tasks, store.now).size
    val status = if (total == 0) null else "$doneToday of $total tasks done today"
    return when {
        data.focus != null -> ModuleConcern(Module.PLAN, if (overdue > 0) 3 else 2, status, "An egg is incubating", "Open", ModuleAccent.PLAN.accent) { go(Place.FOCUS) }
        next != null -> ModuleConcern(Module.PLAN, if (overdue > 0) 3 else 2, status, next.title, "Done", Pixel.Green, next.rank) { model.completeTask(next.id) }
        doneToday > 0 -> ModuleConcern(Module.PLAN, 0, status, "All done for today", "Open", ModuleAccent.PLAN.accent) { go(Place.PLAN) }
        else -> ModuleConcern(Module.PLAN, 0, null, "Nothing planned for today", "Add a task", ModuleAccent.PLAN.accent) { model.addTaskRequested = true; go(Place.PLANNER) }
    }
}

private fun mintConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    val data = model.mintData
    val today = model.store.today
    if (data.settings.gate) return ModuleConcern(Module.MINT, 0, null, "Locked", "Open", ModuleAccent.MINT.accent) { go(Place.MINT) }
    val due = MintRules.upcoming(data, today, 0).count { !it.bill.income && (it.state == OccurrenceState.DUE || it.state == OccurrenceState.OVERDUE) }
    val soon = MintRules.upcoming(data, today, SOON_DAYS).filter { !it.bill.income && it.state == OccurrenceState.UPCOMING }
    val bills = { go(Place.MINT); model.mintSection = MintSection.BILLS }
    val summary = mintSubtitle(model)
    val soonStep = if (soon.size == 1) {
        val days = today.daysUntil(soon.first().payDay)
        "1 bill due ${if (days <= 1) "tomorrow" else "in $days days"}"
    } else {
        countOf(soon.size, "bill", "bills") + " due within $SOON_DAYS days"
    }
    return when {
        due > 0 -> ModuleConcern(Module.MINT, 3, summary, "${countOf(due, "bill", "bills")} to settle", "Settle", ModuleAccent.MINT.accent) { bills() }
        soon.isNotEmpty() -> ModuleConcern(Module.MINT, 2, summary, soonStep, "Settle", ModuleAccent.MINT.accent) { bills() }
        else -> ModuleConcern(Module.MINT, 0, summary, "No bills due", "Open", ModuleAccent.MINT.accent) { go(Place.MINT) }
    }
}

private fun baseConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    val data = model.baseData
    if (Module.BASE.name in model.data.settings.lockedModules) return ModuleConcern(Module.BASE, 0, null, "Locked", "Open", ModuleAccent.BASE.accent) { go(Place.BASE) }
    val overview = BaseRules.overview(data, model.store.today)
    val chores = overview.chores
    val upkeep = overview.maintenance
    val overdue = chores.count { it.state == ChoreState.OVERDUE } + upkeep.count { it.state == DueState.OVERDUE }
    val dueToday = chores.count { it.state == ChoreState.DUE } + upkeep.count { it.state == DueState.DUE }
    val total = chores.size + upkeep.size
    val parts = buildList {
        if (chores.isNotEmpty()) add(countOf(chores.size, "chore", "chores"))
        if (upkeep.isNotEmpty()) add(countOf(upkeep.size, "upkeep", "upkeep"))
    }.joinToString(" and ")
    val openSection = {
        go(Place.BASE)
        model.baseSection = if (chores.isNotEmpty()) BaseSection.CHORES else BaseSection.MAINTENANCE
    }
    return when {
        overdue > 0 -> ModuleConcern(Module.BASE, 3, null, parts + if (overdue == total) " overdue" else " due", "Open", ModuleAccent.BASE.accent) { openSection() }
        dueToday > 0 -> ModuleConcern(Module.BASE, 2, null, "$parts due", "Open", ModuleAccent.BASE.accent) { openSection() }
        total > 0 -> ModuleConcern(Module.BASE, 2, null, "$parts due soon", "Open", ModuleAccent.BASE.accent) { openSection() }
        else -> ModuleConcern(Module.BASE, 0, null, "Nothing due today", "Open", ModuleAccent.BASE.accent) { go(Place.BASE) }
    }
}

private fun careConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    if (Module.CARE.name in model.data.settings.lockedModules) return ModuleConcern(Module.CARE, 0, null, "Locked", "Open", ModuleAccent.CARE.accent) { go(Place.CARE) }
    val careData = model.careData
    val care = model.care
    val today = care.todayRoutines()
    val done = today.count { it.doneOn(care.today) }
    val next = today.firstOrNull { !it.doneOn(care.today) }
    val status = if (today.isEmpty()) null else "$done of ${today.size} routines today"
    val openSection = { section: CareSection -> go(Place.CARE); model.careSection = section }
    return when {
        next != null -> ModuleConcern(Module.CARE, 2, status, next.title, "Done", Pixel.Green) { model.effect(Sfx.SELECT); model.haptic(); care.completeRoutine(next.id) }
        today.isNotEmpty() -> ModuleConcern(Module.CARE, 0, status, "Routines done for today", "Calm break", ModuleAccent.CARE.accent) { openSection(CareSection.CALM) }
        careData.routines.none { !it.archived } -> ModuleConcern(Module.CARE, 0, null, "No routines yet", "Add a routine", ModuleAccent.CARE.accent) { openSection(CareSection.ROUTINES) }
        else -> ModuleConcern(Module.CARE, 0, null, "No routines today", "Calm break", ModuleAccent.CARE.accent) { openSection(CareSection.CALM) }
    }
}

private fun healConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    if (Module.HEAL.name in model.data.settings.lockedModules) return ModuleConcern(Module.HEAL, 0, null, "Locked", "Open", ModuleAccent.HEAL.accent) { go(Place.HEAL) }
    val data = model.healData
    val ml = HealRules.hydrationOn(data, model.store.today)
    val target = data.settings.hydrationTargetMl
    val step = when {
        ml == null -> "No water logged today"
        target != null -> "${waterText(ml)} of ${waterText(target)}"
        else -> "${waterText(ml)} so far"
    }
    val behind = target != null && (ml ?: 0) < target
    return ModuleConcern(Module.HEAL, if (behind) 1 else 0, null, step, "+250 ml", ModuleAccent.HEAL.accent) {
        model.heal.logDrink(250); model.haptic(); model.effect(Sfx.HEAL)
    }
}

private fun waterText(ml: Int): String = if (ml >= 1000) HealRules.format(ml / 1000.0) + " L" else "$ml ml"

private fun linkConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    if (Module.LINK.name in model.data.settings.lockedModules) return ModuleConcern(Module.LINK, 0, null, "Locked", "Open", ModuleAccent.LINK.accent) { go(Place.LINK) }
    model.linkData
    val due = model.link.dueNow()
    val dates = model.link.upcoming(LINK_DATES_DAYS)
    val step = buildList {
        if (due.isNotEmpty()) add("${countOf(due.size, "follow-up", "follow-ups")} due")
        if (dates.isNotEmpty()) add("${countOf(dates.size, "date", "dates")} this week")
    }.joinToString(", ").ifEmpty { "No follow-ups due" }
    val openSection = {
        go(Place.LINK)
        model.linkSection = when {
            due.isNotEmpty() -> LinkSection.FOLLOW_UPS
            dates.isNotEmpty() -> LinkSection.DATES
            else -> null
        }
    }
    return when {
        due.isNotEmpty() -> ModuleConcern(Module.LINK, 3, null, step, "Open", ModuleAccent.LINK.accent) { openSection() }
        dates.isNotEmpty() -> ModuleConcern(Module.LINK, 1, null, step, "Open", ModuleAccent.LINK.accent) { openSection() }
        else -> ModuleConcern(Module.LINK, 0, null, "No follow-ups due", "Open", ModuleAccent.LINK.accent) { go(Place.LINK) }
    }
}

private fun worldConcern(model: AppModel, go: (Place) -> Unit): ModuleConcern {
    val world = model.data.world
    val out = world.teams.count { it.expedition != null }
    val openSection = { section: WorldSection? ->
        go(Place.WORLD)
        model.worldSection = section
    }
    return when {
        out > 0 -> {
            val where = world.teams.firstNotNullOfOrNull { team -> team.expedition?.let { Dungeons.byId(it.dungeonId)?.name } }
            ModuleConcern(
                Module.WORLD, 1, "${countOf(out, "team", "teams")} out exploring", if (where != null) "Exploring $where" else "Out in a dungeon",
                "Open", ModuleAccent.WORLD.accent,
            ) { openSection(WorldSection.TEAMS) }
        }
        else -> ModuleConcern(Module.WORLD, 0, null, "Send a team into a dungeon", "Open", ModuleAccent.WORLD.accent) { go(Place.WORLD) }
    }
}

/**
 * The save-derived facts behind the module cards' live lines, gathered once here so the selection in
 * [HomeOpportunities] is pure and testable without a store, a clock or a device. Each module's lines
 * go on that module's card; there is no separate strip.
 */
@Composable
private fun liveLines(model: AppModel): Map<Module, List<HomeLiveLine>> {
    val store = model.store
    val data = model.data
    val world = data.world
    val locked = data.settings.lockedModules
    val now = store.nowMillis
    val report = store.worldReport()
    val nextFloor = remember(world, data.player.pokemon, now) { WorldSim.nextFloorAt(world, data.player.pokemon, now) }
    val safariVisits = if (Module.LINK.name in locked || !model.link.safariOn) 0 else model.linkData.people.count { p ->
        model.link.safariPool(p.id).isNotEmpty() && model.link.safariToday(p.id).let { !it.visited && it.left > 0 }
    }
    val companion = remember(data.events, now) {
        EventCompanions.due(data.events, store.now) { it.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
    }
    val live = HomeLive(
        reportFloors = report?.floors ?: 0,
        floorDue = report == null && nextFloor != null && nextFloor <= now,
        floorDueWhere = world.teams.firstNotNullOfOrNull { team -> team.expedition?.let { Dungeons.byId(it.dungeonId)?.name } } ?: "",
        jobsReady = world.missions.count { it.done },
        finds = world.finds.size,
        outbreaks = if (Module.MINT.name in locked || model.mintData.settings.gate) 0 else data.outbreaks.size,
        dreamers = if (Module.CARE.name in locked || model.care.befriendsLeft() <= 0) 0 else Visitors.meadow(model.careData.visitors).count { model.care.canVisit(it) },
        safariVisits = safariVisits,
        companionCandies = companion.sumOf { it.candies },
        companionTitle = companion.firstOrNull()?.title ?: "",
    )
    return HomeOpportunities.byModule(live)
}

private fun openLive(model: AppModel, go: (Place) -> Unit, kind: HomeLiveAction) {
    when (kind) {
        HomeLiveAction.WORLD_REPORT -> go(Place.WORLD)
        HomeLiveAction.WORLD_BOARD -> { go(Place.WORLD); model.worldSection = WorldSection.MISSIONS }
        HomeLiveAction.WORLD_FINDS -> { go(Place.WORLD); model.worldSection = WorldSection.BAG }
        HomeLiveAction.MINT_OUTBREAK -> go(Place.MINT)
        HomeLiveAction.CARE_DREAM -> { go(Place.CARE); model.careSection = CareSection.DREAM }
        HomeLiveAction.LINK_SAFARI -> { go(Place.LINK); model.linkSection = LinkSection.PEOPLE }
        HomeLiveAction.PLAN_CALENDAR -> { go(Place.PLAN); model.planSection = PlanSection.CALENDAR }
    }
}

/** The module actions worth one tap from Home. Closed by default (12B): one title opens the compact action row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickActionsCard(model: AppModel, go: (Place) -> Unit) {
    model.careData; model.healData
    val locked = model.data.settings.lockedModules
    val mood = if (Module.CARE.name !in locked) model.care.diaryToday()?.mood else -1
    val routine = if (Module.CARE.name !in locked) model.care.todayRoutines().firstOrNull { !it.doneOn(model.care.today) } else null
    TextBox {
        app.pokeplan.ui.theme.Accordion("Quick actions", summary = "task, expense, routine") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("Task", { model.addTaskRequested = true; go(Place.PLANNER) }, color = ModuleAccent.PLAN.accent, compact = true)
                    if (Module.MINT.name !in locked && !model.mintData.settings.gate) {
                        PixelButton("Expense", { go(Place.MINT); model.mintSection = MintSection.ACTIVITY }, color = ModuleAccent.MINT.accent, compact = true)
                    }
                    if (Module.CARE.name !in locked) {
                        PixelButton("Routine", {
                            val next = model.care.todayRoutines().firstOrNull { !it.doneOn(model.care.today) }
                            if (next != null) { model.effect(Sfx.SELECT); model.haptic(); model.care.completeRoutine(next.id) } else { go(Place.CARE); model.careSection = CareSection.ROUTINES }
                        }, color = ModuleAccent.CARE.accent, compact = true)
                    }
                    if (Module.HEAL.name !in locked) {
                        PixelButton("+250 ml", { model.heal.logDrink(250); model.haptic(); model.effect(Sfx.HEAL) }, color = ModuleAccent.HEAL.accent, compact = true)
                        PixelButton("Health record", { go(Place.HEAL); model.healSection = HealSection.READINGS }, color = ModuleAccent.HEAL.accent, compact = true)
                    }
                    if (Module.BASE.name !in locked) PixelButton("Base chore", { go(Place.BASE); model.baseSection = BaseSection.CHORES }, color = ModuleAccent.BASE.accent, compact = true)
                    if (Module.LINK.name !in locked) PixelButton("Add a person", { go(Place.LINK); model.linkSection = LinkSection.PEOPLE }, color = ModuleAccent.LINK.accent, compact = true)
                }
                if (mood != -1) MoodKeys(mood) { model.care.setMood(it); model.haptic() }
                RestDayRow(model)
            }
        }
    }
}

private const val RECENT_SHOWN = 4

@Composable
private fun RecentCard(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    val entries = model.data.rewardLog.filter { it.seen }.takeLast(RECENT_SHOWN).reversed()
    if (entries.isEmpty()) return
    SectionLabel("Recently", icon = "oaks-letter")
    entries.forEach { entry ->
        val hatched = entry.rewards.filterIsInstance<Reward.Hatched>().firstOrNull()?.pokemon
        val owned = hatched?.let { h -> model.data.player.pokemon.firstOrNull { it.id == h.id } }
        ListRow(
            lead = {
                Box(Modifier.size(Spacing.Target), contentAlignment = Alignment.Center) {
                    if (hatched != null) Sprite(hatched.form.id, hatched.isShiny, hatched.nickname, size = Spacing.Target)
                    else entry.rewards.firstNotNullOfOrNull { it.iconItem() }?.let { ItemIcon(it, size = Sizes.Icon, description = null) }
                }
            },
            onClick = owned?.let { p -> { model.openPokemonId = p.id; go(Place.COLLECTION) } },
            onClickLabel = owned?.let { "Open ${it.nickname}" },
        ) {
            Text(if (hatched != null) "${hatched.nickname} ${if (isWorldSource(entry.source)) "joined you" else when (acquisitionOfSource(entry.source)) { Acquisition.ARRIVAL -> "arrived"; Acquisition.FIELD -> "was found"; Acquisition.LEDGER -> "was catalogued"; Acquisition.HABITAT -> "settled in"; Acquisition.POSTCARD -> "was delivered"; Acquisition.SAFARI -> "was met"; Acquisition.EGG -> "hatched" }}" else entry.source, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            val what = if (hatched != null) entry.source else entry.rewards.take(2).joinToString(", ") { it.describe() }
            Text(listOf(what, daysAgo(store.today, store.dayOf(entry.atMillis))).filter { it.isNotEmpty() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

private fun daysAgo(today: LocalDate, day: LocalDate): String = when (val n = day.daysUntil(today)) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$n days ago"
}

@Composable
fun HomeBuddyStage(model: AppModel, onOpen: () -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy ?: return
    val berry = store.buddyBerry()
    BuddyStage(
        buddy = buddy, scene = BuddyScene.of(buddy.form.types), darkness = BuddyScene.darkness(store.now.hour, store.now.minute),
        progress = store.levelProgress(buddy), event = store.buddyEvent(),
        calm = data.settings.reduceMotion, resting = data.focus != null, berry = berry,
        onPet = { avoid -> store.pet(); model.cry(buddy); BuddyReaction.pet(buddy, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> berry?.let { store.useItem(it, buddy.id); model.effect(Sfx.EATING) }; BuddyReaction.feed(buddy, avoid = avoid) },
        onOpen = onOpen, onHaptic = { model.haptic() }, onEvent = { model.cry(buddy) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
    )
}

@Composable
internal fun NextUp(task: Task, onDone: () -> Unit, onFocus: (() -> Unit)?, onChoose: (() -> Unit)?, picked: Boolean = false, day: kotlinx.datetime.LocalDate? = null) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (picked) Text("Picked for you: +10% candies", style = MaterialTheme.typography.labelMedium, color = Pixel.Green)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelIconButton("check", "Done", onDone, Pixel.Green)
                Column(Modifier.weight(1f)) {
                    TaskTitleLine(task, MaterialTheme.typography.titleSmall)
                    val meta = taskMeta(task, day)
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (onFocus != null || onChoose != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (onFocus != null) PixelButton("Focus on this", onFocus, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                if (onChoose != null) PixelButton(if (picked) "Pick another" else "Pick for me", onChoose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
internal fun TaskTitleLine(task: Task, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(task.title, Modifier.weight(1f, fill = false), style = style, maxLines = 3)
        Text(task.rank.letter.toString(), Modifier.semantics { contentDescription = task.rank.spoken }, style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
    }
}

@Composable
fun TaskRow(task: Task, onDone: () -> Unit) {
    ListRow(lead = { PixelIconButton("check", "Done", onDone, Pixel.Green) }) {
        TaskTitleLine(task, MaterialTheme.typography.bodyLarge)
        val meta = taskMeta(task)
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RankProgress(points: Int) {
    val rank = TrainerRank.rankFor(points)
    val low = TrainerRank.thresholds[rank]
    val high = TrainerRank.thresholds.getOrNull(rank + 1)
    val next = TrainerRank.pointsToNext(points)
    val label = if (next == null) "Top rank" else "$next points to ${TrainerRank.names[rank + 1]}"
    ExpBar(if (high == null) 1f else (points - low).toFloat() / (high - low), label, tag = "", color = Pixel.Yellow)
}

@Composable
private fun RestDayRow(model: AppModel) {
    val store = model.store
    val day = store.streakDay
    if (day in model.data.activeDays) return
    val resting = day in model.data.restDays
    var refused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            if (resting) "Rest day" else if (refused) "No rest days left this month" else "Rest day",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
        )
        PixelButton(if (resting) "Undo" else "Rest today", { refused = !store.toggleRestDay(day) }, color = Pixel.InkSoft, compact = true)
    }
}

@Composable
private fun MiniQuestsCard(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    model.data
    val quests = store.miniQuests()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Mini-quests")
            quests.forEach { q ->
                val done = store.miniQuestDone(q.id)
                val avoided = q.kind == MiniQuest.Kind.AVOIDED_TASK
                val oldest = if (avoided) store.oldestOpenTask() else null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(q.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (done) Pixel.InkSoft else Color.Unspecified)
                    when {
                        done -> Text("Done", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                        avoided && oldest != null && model.data.focus == null -> PixelButton("Start 5 min", {
                            store.completeMiniQuest(q.id)
                            model.effect(Sfx.FOCUS_START); store.startFocus(FocusRules.justFiveMinutes().minutesRoundedUp, deepFocus = false, taskId = oldest.id)
                            go(Place.FOCUS)
                        }, color = Pixel.Tone, compact = true)
                        else -> PixelIconButton("check", "Done", { if (store.completeMiniQuest(q.id)) model.effect(Sfx.HEAL) }, Pixel.Green)
                    }
                }
            }
            val got = store.miniQuestRewards()
            got.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    r.iconItem()?.let { ItemIcon(it, size = Sizes.IconS) }
                    Text("You got ${r.describe()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                }
            }
            if (store.canRerollMiniQuests()) PixelButton("Different ones", { store.rerollMiniQuests() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun WelcomeBackCard(model: AppModel) {
    model.data
    if (!model.store.welcomeBackDue()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Welcome back!")
            Text("It's good to see you. Here's a little something to start again with.", style = MaterialTheme.typography.bodyLarge) // copy-ok: game
            PixelButton("Open the gift", { model.store.claimWelcomeBack(); model.effect(Sfx.HEAL) }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

@Composable
private fun RankUpCard(model: AppModel) {
    val opened = model.store.newlyOpened()
    LaunchedEffect(opened.isEmpty()) { if (opened.isNotEmpty()) model.effect(Sfx.RANK_UP) }
    if (opened.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Rank up: ${TrainerRank.nameFor(model.data.player.trainerPoints)}!", style = MaterialTheme.typography.titleSmall)
            Text("New for you:", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            opened.forEach { Text("•  ${it.label}", style = MaterialTheme.typography.bodyLarge) }
            PixelButton("Got it", { model.store.markRankSeen() }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

@Composable
private fun WhatsNewCard(model: AppModel) {
    val version = AppVersion.NAME
    val lines = WhatsNew.forVersion(version) ?: return
    if (model.data.lastSeenVersion == version) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("What's new in $version", style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
            PixelButton("Got it", { model.store.markVersionSeen(version) }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

