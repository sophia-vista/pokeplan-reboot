package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import app.pokeplan.ui.StatusKind
import app.pokeplan.ui.StatusIcon
import app.pokeplan.core.reward.RewardEngine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.care.CareRules
import app.pokeplan.care.Emotion
import app.pokeplan.care.Skills
import app.pokeplan.care.Visitors
import app.pokeplan.core.dex.Dex
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.Sfx
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import app.pokeplan.ui.Sprite
import app.pokeplan.care.Prompts
import app.pokeplan.care.Routine
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.SceneStage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/**
 * Care's screens (docs/ecosystem.md, section 6), after journalling and DBT: routines, calm breaks, a daily diary card, a
 * skills library and a journal, and trends. Doing earns (a routine, a calm break, a practised skill, a week with a rhythm);
 * a diary card, a note or a journal entry never does, and the buddy never comments on them.
 */
enum class CareSection(val label: String, val icon: String) {
    ROUTINES("Routines", "oaks-parcel"), CALM("Calm break", "lunar-wing"), DIARY("Diary card", "heart-scale"),
    SKILLS("Skills", "mental-herb"), JOURNAL("Journal", "journal"), TRENDS("Trends", "exp-share"),
    MEADOW("Meadow", "town-map"), PLAY("Play", "poke-ball"),
}

@Composable
internal fun CareScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenSwitch(model.careSection, depth = { if (it == null) 0 else 1 }) { section -> CarePage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarePage(model: AppModel, section: CareSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        CareMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, Module.CARE.label, onBack = { model.careSection = null }, icon = section.icon, color = Module.CARE.look.header) {
        when (section) {
            CareSection.ROUTINES -> RoutinesView(model)
            CareSection.CALM -> CalmBreakView(model)
            CareSection.DIARY -> DiaryView(model)
            CareSection.SKILLS -> SkillsView(model)
            CareSection.JOURNAL -> JournalView(model)
            CareSection.TRENDS -> TrendsView(model)
            CareSection.MEADOW -> MeadowView(model) { key -> model.playTarget = key; model.careSection = CareSection.PLAY }
            CareSection.PLAY -> PlayView(model, model.playTarget.also { model.playTarget = null })
        }
    }
}

@Composable
private fun CareMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val care = model.careData
    val today = model.care.todayRoutines()
    val done = today.count { it.doneOn(model.care.today) }
    val diarySaved = model.care.diaryToday() != null
    val practised = model.care.practisedToday().size
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.CARE.label, subtitle = "Modules", onBack = onBack, color = Module.CARE.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // What to do comes before the party and the visitors: a new player sees "Add a routine" without scrolling past six empty slots.
            CareBuddy(model, openPokemon)
            RewardBox(model)
            TutorialCard(model, app.pokeplan.app.Tutorials.CARE_BREATH, careBreaks = care.breaks.size)
            SectionLabel("Today", icon = CareSection.ROUTINES.icon)
            if (today.isEmpty()) {
                EmptyState(
                    if (care.routines.none { !it.archived }) "No routines yet" else "Nothing planned for today",
                    "Small things you do for yourself, like a glass of water or a stretch.",
                    action = "Add a routine" to { model.careSection = CareSection.ROUTINES }, sleeper = true,
                )
            } else today.forEach { RoutineRow(model, it) }
            CareEggMeter(model)
            MenuGrid(
                listOf(
                    MenuEntry(CareSection.ROUTINES.name, CareSection.ROUTINES.icon, "Routines", if (today.isEmpty()) "none today" else "$done of ${today.size} today", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.CALM.name, CareSection.CALM.icon, "Calm break", "a minute to breathe", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.DIARY.name, CareSection.DIARY.icon, "Diary card", if (diarySaved) "saved today" else "how today felt", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.SKILLS.name, CareSection.SKILLS.icon, "Skills", if (practised == 0) "DBT skills to try" else "$practised practised today", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.JOURNAL.name, CareSection.JOURNAL.icon, "Journal", if (care.journal.isEmpty()) "write anything" else if (care.journal.size == 1) "1 entry" else "${care.journal.size} entries", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.TRENDS.name, CareSection.TRENDS.icon, "Trends", "the last few weeks", tint = Pixel.InkSoft),
                    MenuEntry(CareSection.MEADOW.name, CareSection.MEADOW.icon, "Meadow", if (care.visitors.isEmpty()) "your buddy's garden" else "${care.visitors.size.coerceAtMost(4)} here", tint = ModuleAccent.CARE.accent),
                    MenuEntry(CareSection.PLAY.name, CareSection.PLAY.icon, "Play", "time with a Pokémon", tint = ModuleAccent.CARE.accent),
                ),
            ) { key -> model.careSection = CareSection.valueOf(key) }
            VisitorsCard(model)
            CarePartyPanel(model, openPokemon)
        }
    }
}

/**
 * Care's buddy (Care's lead, else the Plan buddy) in the meadow, as on Home: pet it, give it a berry, and it celebrates Care's
 * own moments (a routine, a calm break, a skill, a friend) with its cry, its portrait and a hop. Two of today's visitors stand
 * beside it, and Care's whole party sits below ([CarePartyPanel]). It never comments on how the player feels.
 */
@Composable
private fun CareBuddy(model: AppModel, openPokemon: (String) -> Unit) {
    val store = model.store
    val buddy = store.careBuddy ?: return
    val data = model.data
    val berry = store.buddyBerry()
    BuddyStage(
        buddy = buddy, scene = Module.CARE.look.scene, darkness = BuddyScene.darkness(store.now.hour, store.now.minute),
        progress = store.levelProgress(buddy), event = store.buddyEvent(care = true),
        calm = data.settings.reduceMotion, resting = data.focus != null, berry = berry,
        onPet = { avoid -> store.pet(buddy.id); model.cry(buddy.speciesId); BuddyReaction.pet(buddy, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> berry?.let { store.useItem(it, buddy.id); model.effect(Sfx.EATING) }; BuddyReaction.feed(buddy, avoid = avoid) },
        onOpen = { openPokemon(buddy.id) }, onHaptic = { model.haptic() },
        onEvent = { model.cry(buddy.speciesId) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
        // The warmest visitors wait in the meadow beside the buddy; a tap is a pat, just for fun.
        guests = Visitors.meadow(model.careData.visitors).sortedByDescending { it.progress }.take(2).map { v ->
            StageGuest(Dex.form(v.formIdentifier)?.id ?: Dex.defaultForm(v.speciesId).id, Dex.species(v.speciesId).name) { model.haptic(); model.cry(v.speciesId) }
        },
    )
}

// ----- Visitors -------------------------------------------------------------------------------------------------------

/**
 * Who is in Care's meadow (docs/pokelife `MODULES/CARE.md`, section 6). Visitors arrive on their own, four are in the meadow and the
 * rest wait in a quiet queue that never expires. Any of them can be visited at any time, with nothing done first: each kind of care
 * warms it at once and the warmth is never lost, however long you are away. At sixteen it becomes a friend: usually an egg of its
 * family's first stage that hatches at once, rarely the visitor itself. A visitor can also be played with, which counts for
 * nothing, and sent away on purpose (it keeps what it earned and can be asked back).
 */
@Composable
private fun VisitorsCard(model: AppModel) {
    val care = model.care
    // The day's arrivals are made the first time they are asked for.
    LaunchedEffect(care.today) { care.visitors() }
    val all = model.careData.visitors
    val meadow = Visitors.meadow(all)
    val waiting = Visitors.queue(all)
    var note by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Int?>(null) }
    var sendAway by remember { mutableStateOf<Int?>(null) }
    var showAway by remember { mutableStateOf(false) }
    if (all.isEmpty() && model.careData.dismissed.isEmpty()) return
    val visit = model.careData.visit
    val open = meadow.firstOrNull { it.speciesId == (visit?.speciesId ?: playing) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Visitors")
            if (open == null) Text(
                "Visit any time. Every kind of care warms a visitor and it likes one best; its warmth is never lost. Most leave you an egg of their family when they become friends; a few stay themselves.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            note?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (open != null) VisitorScene(model, open, visit?.takeIf { it.speciesId == open.speciesId }, { note = it }) { playing = null }
            meadow.forEach { v ->
                val species = Dex.species(v.speciesId)
                val resting = care.resting(v)
                ListRow(
                    lead = {
                        AnimatedSprite(
                            Dex.form(v.formIdentifier)?.id ?: Dex.defaultForm(v.speciesId).id, false, species.name,
                            Modifier.size(56.dp).clickable(onClickLabel = "Hear ${species.name}", role = Role.Button) { model.cry(v.speciesId) },
                            size = 56.dp, animate = !model.data.settings.reduceMotion,
                        )
                    },
                    trail = {
                        when {
                            visit?.speciesId == v.speciesId -> Text("Visiting", style = MaterialTheme.typography.labelMedium, color = Pixel.Green)
                            care.canVisit(v) -> PixelButton("Visit", {
                                if (care.startVisit(v.speciesId) == null) note = "Not now." else { playing = null; note = null; model.haptic(); model.cry(v.speciesId) }
                            }, color = Pixel.Green, compact = true)
                            else -> PixelButton(if (playing == v.speciesId) "Playing" else "Play", {
                                playing = v.speciesId; note = null; model.haptic()
                            }, color = Pixel.InkSoft, enabled = visit == null && playing != v.speciesId, compact = true)
                        }
                    },
                    highlight = false,
                ) {
                    Column {
                        Text(species.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                if (Visitors.friend(v)) "ready to be a friend" else if (v.progress == 0) "Just arrived" else "${v.progress} of ${Visitors.FRIEND_PROGRESS} warm",
                                if (resting) "resting, ready for a visit later" else null,
                            ).joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                        )
                    }
                }
            }
            if (waiting.isNotEmpty()) {
                SectionLabel("Waiting for a place (${waiting.size})")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    waiting.forEach { v -> Sprite(Dex.form(v.formIdentifier)?.id ?: Dex.defaultForm(v.speciesId).id, false, "${Dex.species(v.speciesId).name}, waiting for a place", size = 40.dp) }
                }
            }
            // Sending a visitor away is a deliberate choice, not a chore: it asks once, keeps what the visitor earned and can be undone.
            if (open != null && visit == null) {
                if (sendAway == open.speciesId) {
                    Text("Send ${Dex.species(open.speciesId).name} away? It keeps what it has earned, and you can ask it back.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Send away", { care.dismiss(open.speciesId); sendAway = null; playing = null; note = null }, color = Pixel.InkSoft, compact = true)
                        PixelButton("Keep", { sendAway = null }, color = Pixel.Green, compact = true)
                    }
                } else PixelButton("Send away", { sendAway = open.speciesId }, color = Pixel.InkSoft, compact = true)
            }
            val away = model.careData.dismissed
            if (away.isNotEmpty()) {
                PixelButton(if (showAway) "Hide sent away" else "Sent away (${away.size})", { showAway = !showAway }, color = Pixel.InkSoft, compact = true)
                if (showAway) away.asReversed().forEach { v ->
                    val species = Dex.species(v.speciesId)
                    ListRow(
                        lead = { Sprite(Dex.form(v.formIdentifier)?.id ?: Dex.defaultForm(v.speciesId).id, false, species.name, size = 40.dp) },
                        trail = { PixelButton("Ask back", { note = if (care.restore(v.id)) "${species.name} is back, waiting for a place." else "No room just now." }, color = Pixel.Green, compact = true) },
                        highlight = false,
                    ) { Text(species.name, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            MetAlbum(model)
        }
    }
}

/** The visitors cared for so far, as small sprites: a quiet album with no counts to chase. */
@Composable
private fun MetAlbum(model: AppModel) {
    val met = model.careData.met.sorted()
    if (met.isEmpty()) return
    SectionLabel("Met")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        met.forEach { id -> Sprite(Dex.defaultForm(id).id, false, Dex.species(id).name, size = 40.dp) }
    }
}

// ----- Routines -------------------------------------------------------------------------------------------------------

/** A routine with Done (or Undo once done today) and one quiet line: its time and days. */
@Composable
private fun RoutineRow(model: AppModel, routine: Routine, onOpen: (() -> Unit)? = null) {
    val care = model.care
    val doneToday = routine.doneOn(care.today)
    val due = routine.isDueOn(care.today)
    ListRow(
        onClick = onOpen, onClickLabel = if (onOpen != null) "Show options" else null,
        trail = {
            when {
                routine.archived || !due -> Unit
                doneToday -> PixelIconButton("undo", "Undo", { care.undoRoutine(routine.id) }, Pixel.InkSoft)
                else -> PixelIconButton("check", "Done", { model.effect(Sfx.SELECT); model.haptic(); care.completeRoutine(routine.id) }, Pixel.Green)
            }
        },
    ) {
        Text(routine.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(if (doneToday) "Done today" else null, routine.time?.let { clock(it) }, routineDays(routine.days)).joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Every day", "Weekdays", "Mon Wed Fri". */
private fun routineDays(days: Set<DayOfWeek>): String = when {
    days.isEmpty() || days.size == 7 -> "Every day"
    days == WEEKDAYS -> "Weekdays"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { dayNames[it.ordinal] }
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutinesView(model: AppModel) {
    val care = model.care
    val data = model.careData
    var title by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var days by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var open by remember { mutableStateOf<String?>(null) }
    fun add() {
        if (title.isBlank()) return
        care.addRoutine(title, time, days)
        title = ""; more = false; time = null; days = emptySet()
    }

    RewardBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    title, { title = it.take(60) }, Modifier.weight(1f), singleLine = true,
                    placeholder = "New routine",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, Pixel.Green, enabled = title.isNotBlank())
            }
            PixelButton(if (more) "Fewer options" else "A time, and which days", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) RoutineFields(time, { time = it }, days, { days = it })
        }
    }

    val (archived, active) = data.routines.partition { it.archived }
    if (active.isEmpty()) EmptyState("No routines yet", "Add one above: drink water, stretch, take a walk, wind down.", icon = CareSection.ROUTINES.icon)
    active.sortedWith(compareBy(nullsLast()) { it.time }).forEach { r ->
        RoutineRow(model, r) { open = if (open == r.id) null else r.id }
        if (open == r.id) RoutineOptions(model, r) { open = null }
    }
    if (archived.isNotEmpty()) {
        SectionLabel("Put away")
        archived.forEach { r ->
            RoutineRow(model, r) { open = if (open == r.id) null else r.id }
            if (open == r.id) RoutineOptions(model, r) { open = null }
        }
    }
    Text(
        "The first ${CareRules.ROUTINES_PAID_PER_DAY} routines you finish each day earn friendship and berries. There are no streaks: a quiet day costs nothing.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

/** The time and the days of a routine, to set when it is added and when it is edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineFields(time: LocalTime?, onTime: (LocalTime?) -> Unit, days: Set<DayOfWeek>, onDays: (Set<DayOfWeek>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelTitle("Time")
        if (time == null) PixelButton("Give it a time", { onTime(LocalTime(9, 0)) }, color = Pixel.InkSoft, compact = true)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("-15", { onTime(shifted(time, -15)) }, color = Pixel.InkSoft, compact = true)
            Text(clock(time), style = MaterialTheme.typography.bodyLarge)
            PixelButton("+15", { onTime(shifted(time, 15)) }, color = Pixel.InkSoft, compact = true)
            PixelButton("No time", { onTime(null) }, color = Pixel.InkSoft, compact = true)
        }
        PixelTitle("Days")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            DayOfWeek.entries.forEach { d -> TabButton(dayNames[d.ordinal], d in days, { onDays(if (d in days) days - d else days + d) }) }
        }
        Text(if (days.isEmpty()) "No day chosen means every day." else routineDays(days), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** Edit a routine's name, time and days (what it earned and when it was done stay), put it away (kept for trends) or back, or delete it, which asks first. */
@Composable
internal fun RoutineOptions(model: AppModel, routine: Routine, startEditing: Boolean = false, onDone: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    var editing by remember(routine.id) { mutableStateOf(startEditing) }
    var title by remember(routine.id) { mutableStateOf(routine.title) }
    var time by remember(routine.id) { mutableStateOf(routine.time) }
    var days by remember(routine.id) { mutableStateOf(routine.days) }
    if (editing) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Edit routine")
                PixelField(
                    title, { title = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                RoutineFields(time, { time = it }, days, { days = it })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelIconButton("check", "Save", { model.care.updateRoutine(routine.copy(title = title.trim(), time = time, days = days)); editing = false; onDone() }, Pixel.Green, enabled = title.isNotBlank())
                    PixelIconButton("close", "Cancel", { editing = false; title = routine.title; time = routine.time; days = routine.days }, Pixel.InkSoft)
                }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        if (asking) {
            Text("Delete it and its history?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
            PixelIconButton("trash", "Delete", { model.care.deleteRoutine(routine.id); onDone() }, Pixel.Danger)
            PixelButton("Keep", { asking = false }, color = Pixel.InkSoft, compact = true)
        } else {
            PixelIconButton("pencil", "Edit", { editing = true }, Pixel.Blue)
            PixelButton(if (routine.archived) "Bring back" else "Put away", { model.care.archiveRoutine(routine.id, !routine.archived); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelIconButton("trash", "Delete", { asking = true }, Pixel.InkSoft)
        }
    }
}

// ----- Calm break -----------------------------------------------------------------------------------------------------

/** One breath: in for 4 seconds, out for 6, about six breaths a minute. */
private const val BREATHE_IN_MILLIS = 4_000
private const val BREATH_MILLIS = 10_000

@Composable
private fun CalmBreakView(model: AppModel) {
    val care = model.care
    val reduce = model.data.settings.reduceMotion
    var seconds by remember { mutableStateOf(CareRules.BREAK_MIN_SECONDS) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val buddy = model.store.careBuddy
    var wake by remember { mutableStateOf(0) }
    var waking by remember { mutableStateOf(false) }

    fun finish(length: Int) {
        val short = length < CareRules.BREAK_MIN_SECONDS
        val paid = care.finishCalmBreak(length).isNotEmpty()
        startedAt = null
        // It wakes up beside you: a stretch (its hop), its cry, and the healing chime when the break earned.
        buddy?.let { model.cry(it.speciesId) }
        if (paid) model.effect(Sfx.CALM_BREAK_END)
        if (!reduce) { waking = true; wake++ }
        result = when {
            paid -> "A calm break, done. Well rested."
            short -> "That one was under a minute, so it doesn't earn anything. It still counts in Trends."
            else -> "Today's calm breaks have all earned already. This one still counts in Trends."
        }
    }

    val start = startedAt
    val elapsed = if (start == null) 0L else (model.now - start).coerceAtLeast(0)
    if (start != null && elapsed >= seconds * 1000L) finish(seconds)

    if (buddy != null) SceneStage(Module.CARE.look.scene, 200.dp) {
        AnimatedSprite(
            buddy.form.id, buddy.isShiny, if (start != null) "${buddy.nickname}, resting" else buddy.nickname,
            Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XXL).clickable(enabled = start == null, onClickLabel = "Hear ${buddy.nickname}", role = Role.Button) {
                model.haptic(); model.cry(buddy.speciesId)
                if (!reduce) { waking = true; wake++ }
            },
            size = 120.dp, animate = !reduce,
            action = when {
                start != null -> SpriteAction.SLEEP
                waking -> SpriteAction.HOP
                else -> SpriteAction.IDLE
            },
            actionKey = wake, onActionDone = { waking = false },
        )
        // The Zz while it rests.
        if (start != null) StatusIcon(StatusKind.SLEEP, (3 * LocalDensity.current.density).roundToInt(), Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XXL + 96.dp).offset(x = 36.dp), loop = true, animate = !reduce)
    }
    if (start == null) {
        RewardBox(model)
        result?.let { line -> TextBox { Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text("A minute or two to breathe, with your buddy resting beside you. Breathe in as the circle grows and out as it shrinks.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(60, 120).forEach { s -> TabButton(if (s == 60) "1 minute" else "2 minutes", seconds == s, { seconds = s }, Modifier.weight(1f)) }
                }
                PixelButton("Start", { result = null; waking = false; model.haptic(); model.effect(Sfx.CALM_BREAK_START); startedAt = model.now }, Modifier.fillMaxWidth(), Pixel.Blue)
                Text(
                    "Up to ${CareRules.BREAKS_PAID_PER_DAY} calm breaks a day earn friendship and a berry.",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
            }
        }
    } else {
        val inhale = elapsed % BREATH_MILLIS < BREATHE_IN_MILLIS
        val left = (seconds - (elapsed / 1000).toInt()).coerceAtLeast(0)
        TextBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                BreathCircle(inhale, still = reduce)
                Text(if (inhale) "Breathe in" else "Breathe out", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text("${left / 60}:${(left % 60).toString().padStart(2, '0')} left", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("End now", { finish((elapsed / 1000).toInt()) }, Modifier.fillMaxWidth(), Pixel.InkSoft)
            }
        }
    }
}

/** A soft circle that grows while breathing in and shrinks while breathing out; with Reduce motion it stays still. */
@Composable
private fun BreathCircle(inhale: Boolean, still: Boolean) {
    val target = if (still || inhale) 1f else 0.55f
    val scale by animateFloatAsState(target, tween(if (still) 0 else if (inhale) BREATHE_IN_MILLIS else BREATH_MILLIS - BREATHE_IN_MILLIS))
    val fill = Pixel.Blue.copy(alpha = 0.18f)
    val edge = Pixel.Blue
    Canvas(Modifier.size(160.dp).semantics { contentDescription = if (inhale) "Breathe in" else "Breathe out" }) {
        val radius = size.minDimension / 2 * (if (still) 0.8f else scale)
        drawCircle(fill, radius)
        drawCircle(edge, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
    }
}


// ----- Diary card -----------------------------------------------------------------------------------------------------

/** A 0 to 5 rating, as six small buttons; tapping the chosen one again clears it. */
@Composable
private fun Rating(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            (0..5).forEach { n -> TabButton("$n", value == n, { onChange(if (value == n) null else n) }, Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryView(model: AppModel) {
    val care = model.care
    val data = model.careData
    val saved = care.diaryToday()
    // The skills practised today start ticked, so the card is quick to fill in.
    var feelings by remember(saved?.id) { mutableStateOf(saved?.feelings ?: emptyMap()) }
    var urges by remember(saved?.id) { mutableStateOf(saved?.urges ?: emptyMap()) }
    var skills by remember(saved?.id) { mutableStateOf(saved?.skills ?: care.practisedToday()) }
    var note by remember(saved?.id) { mutableStateOf(saved?.note ?: "") }
    var newUrge by remember { mutableStateOf("") }
    var allSkills by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("How strongly did you feel each of these today?")
            Text("0 is not at all, 5 is very strongly. Leave out any you like.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Emotion.entries.forEach { e ->
                Rating("${e.emoji} ${e.label}", feelings[e]) { v -> feelings = if (v == null) feelings - e else feelings + (e to v); done = false }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Urges")
            val tracked = data.settings.urges
            if (tracked.isEmpty()) Text("If there's an urge you want to keep an eye on, add it here. Only you see it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            tracked.forEach { u ->
                Rating(u, urges[u]) { v -> urges = if (v == null) urges - u else urges + (u to v); done = false }
                PixelButton("Stop tracking", { care.changeSettings { s -> s.copy(urges = s.urges - u) }; urges = urges - u }, color = Pixel.InkSoft, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                fun add() {
                    val u = newUrge.trim()
                    if (u.isNotEmpty() && u !in tracked) care.changeSettings { s -> s.copy(urges = s.urges + u) }
                    newUrge = ""
                }
                PixelField(
                    newUrge, { newUrge = it.take(40) }, Modifier.weight(1f), singleLine = true,
                    placeholder = "Track an urge",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, Pixel.InkSoft, enabled = newUrge.isNotBlank())
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Skills you used")
            val shown = if (allSkills) Skills.all else Skills.all.filter { it.id in skills || it.id in care.practisedToday() }
            if (shown.isEmpty()) Text("None yet today, and that's fine.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                shown.forEach { k -> TabButton(k.name, k.id in skills, { skills = if (k.id in skills) skills - k.id else skills + k.id; done = false }) }
            }
            PixelButton(if (allSkills) "Fewer skills" else "All skills", { allSkills = !allSkills }, color = Pixel.InkSoft, compact = true)
            PixelField(
                note, { note = it.take(500); done = false }, Modifier.fillMaxWidth(),
                placeholder = "A note, if you like",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            PixelButton(if (saved == null) "Save today's card" else "Update today's card", {
                care.saveDiary(feelings, urges, skills, note)
                model.effect(Sfx.SELECT)
                done = true
            }, Modifier.fillMaxWidth(), ModuleAccent.CARE.accent, enabled = feelings.isNotEmpty() || urges.isNotEmpty() || skills.isNotEmpty() || note.isNotBlank())
            Text(
                if (done) "Saved on this device." else "Your diary card stays on this device and never earns anything, so there is nothing to get right.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    val past = data.diary.filter { it.day != care.today }.sortedByDescending { it.day }.take(14)
    if (past.isNotEmpty()) SectionLabel("Earlier cards")
    past.forEach { c ->
        ListRow(trail = { PixelIconButton("trash", "Delete", { care.deleteDiary(c.id) }, Pixel.InkSoft) }) {
            Text(c.day.short(), style = MaterialTheme.typography.bodyLarge)
            val strongest = c.feelings.entries.sortedByDescending { it.value }.filter { it.value > 0 }.take(3)
            if (strongest.isNotEmpty()) Text(strongest.joinToString(", ") { "${it.key.label} ${it.value}" }, style = MaterialTheme.typography.bodyMedium)
            if (c.skills.isNotEmpty()) Text("Skills: " + c.skills.mapNotNull { Skills.byId(it)?.name }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            c.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// ----- Skills ---------------------------------------------------------------------------------------------------------

@Composable
private fun SkillsView(model: AppModel) {
    val care = model.care
    model.careData // redraw when Care changes
    var open by remember { mutableStateOf<String?>(null) }
    val practised = care.practisedToday()
    val skill = open?.let { Skills.byId(it) }

    RewardBox(model)
    if (skill != null) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium)
                Text(skill.group.label, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Text(skill.use, style = MaterialTheme.typography.bodyLarge)
                skill.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyLarge) }
                when {
                    skill.calmBreak -> PixelButton("Start a calm break", { model.careSection = CareSection.CALM }, Modifier.fillMaxWidth(), Pixel.Blue)
                    else -> PixelButton(if (skill.id in practised) "Practised again" else "I practised this", { model.effect(Sfx.SELECT); model.haptic(); care.practiseSkill(skill.id) }, Modifier.fillMaxWidth(), Pixel.Green)
                }
                if (skill.id in practised) Text("Practised today.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("All skills", { open = null }, color = Pixel.InkSoft, compact = true)
            }
        }
        return
    }
    Text(
        "Skills from DBT (dialectical behaviour therapy), as short reminders. Try one, then tick that you practised it: up to " +
            "${CareRules.SKILLS_PAID_PER_DAY} skills a day earn friendship and a berry.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
    app.pokeplan.care.SkillGroup.entries.forEach { group ->
        SectionLabel(group.label)
        Text(group.line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Skills.inGroup(group).forEach { k ->
            ListRow(onClick = { open = k.id }, onClickLabel = "Open ${k.name}") {
                Text(k.name, style = MaterialTheme.typography.bodyLarge)
                Text(if (k.id in practised) "Practised today" else k.use, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text(
        "This is a reminder, not therapy. A therapist or a DBT skills group teaches these properly. If you are in danger, call your local emergency number.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

// ----- Journal --------------------------------------------------------------------------------------------------------

private val JOURNAL_TAGS = listOf("Gratitude", "People", "Work", "Rest", "Health", "Outside")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalView(model: AppModel) {
    val care = model.care
    val data = model.careData
    val prompt = care.prompt()
    // The half-written entry lives in the model, so leaving the screen (or a tab) and coming back finds it still there.
    var text by model::journalDraft
    // The draft is also kept in Care's save (a moment after you stop typing), so it is there after the app is closed.
    LaunchedEffect(Unit) { if (model.journalDraft.isEmpty() && data.journalDraft.isNotEmpty()) model.journalDraft = data.journalDraft }
    LaunchedEffect(model.journalDraft) { kotlinx.coroutines.delay(1_200); care.setJournalDraft(model.journalDraft) }
    var answering by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    fun save() {
        if (care.write(text, answering, tags) != null) { text = ""; answering = null; tags = emptySet() }
    }

    if (prompt != null && answering == null) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Today's question", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(prompt.text, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("Write about this", { answering = prompt.id }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Another question", { care.skipPrompt() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            val asked = answering?.let { Prompts.byId(it) }
            if (asked != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(asked.text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                PixelButton("Not this", { answering = null }, color = Pixel.InkSoft, compact = true)
            }
            PixelField(
                text, { text = it.take(5_000) }, Modifier.fillMaxWidth(), minLines = 4,
                placeholder = "Write anything",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                JOURNAL_TAGS.forEach { t -> TabButton(t, t in tags, { tags = if (t in tags) tags - t else tags + t }) }
            }
            PixelButton("Save entry", ::save, Modifier.fillMaxWidth(), Pixel.Warning, enabled = text.isNotBlank())
            Text("Only for you: the journal stays on this device and never earns anything.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    if (data.journal.size > 3) PixelField(
        query, { query = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = "Search the journal",
    )
    val entries = care.searchJournal(query).take(30)
    if (data.journal.isNotEmpty() && entries.isEmpty()) EmptyState("Nothing found", "No entry has \"${query.trim()}\" in it.", icon = CareSection.JOURNAL.icon)
    entries.forEach { e ->
        ListRow(trail = { PixelIconButton("trash", "Delete", { care.deleteEntry(e.id) }, Pixel.InkSoft) }) {
            Text(e.day.short(), style = MaterialTheme.typography.bodyMedium)
            e.promptId?.let { Prompts.byId(it) }?.let { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
            Text(e.text, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (e.tags.isNotEmpty()) Text(e.tags.sorted().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ----- Trends ---------------------------------------------------------------------------------------------------------

@Composable
private fun TrendsView(model: AppModel) {
    val care = model.care
    model.careData // redraw when Care changes
    var emotion by remember { mutableStateOf(Emotion.JOY) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("The last 7 days")
            Text("Routines done: ${care.routinesLast7Days()}", style = MaterialTheme.typography.bodyLarge)
            Text("Skills used: ${care.skillsLast7Days()}", style = MaterialTheme.typography.bodyLarge)
            Text("Calm breaks: ${care.calmMinutesLast7Days()} min", style = MaterialTheme.typography.bodyLarge)
            Text("Your best week: ${care.bestWeek()} routines", style = MaterialTheme.typography.bodyLarge)
            Text("No streaks here: a quiet week costs nothing and says nothing.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Feelings, week by week")
            val weeks = care.feelingWeeks(8)
            if (weeks.all { it.cards == 0 }) {
                Text("Fill in a diary card now and then and your weeks show here.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Emotion.entries.forEach { e -> TabButton(e.label, e == emotion, { emotion = e }) }
            }
            weeks.asReversed().forEach { w ->
                val avg = w.averages[emotion]
                val label = if (avg == null) "not rated" else "${emotion.label} ${(avg * 10).roundToInt() / 10.0} of 5, ${w.cards} card${if (w.cards == 1) "" else "s"}"
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("Week of ${w.weekStart.dayMonth()}: $label", style = MaterialTheme.typography.bodyMedium)
                    if (avg != null) PixelBar((avg / 5).toFloat(), Modifier.fillMaxWidth(), Pixel.Blue, label = label)
                }
            }
            Text("Only you see this. It never earns and your buddy never mentions it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

/** How far the next Care Egg is: every routine, calm break and skill fills it a little. */
@Composable
private fun CareEggMeter(model: AppModel) {
    model.data // observe the save: the store itself is not Compose state
    val points = model.store.careEggMeter()
    val size = RewardEngine.CARE_EGG_METER_SIZE
    val label = "Care Egg: $points of $size"
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            PixelBar(points.toFloat() / size, color = Pixel.Green, label = label)
            Text("A routine adds 1, a calm break or a skill 2.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}
