package app.pokeplan.ui.care

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.drawPixelDisc
import app.pokeplan.ui.theme.drawPixelRing

import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
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
import app.pokeplan.care.Skill
import app.pokeplan.care.Skills
import app.pokeplan.care.Visitors
import app.pokeplan.core.dex.Dex
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.Sfx
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.Role
import app.pokeplan.ui.Sprite
import app.pokeplan.care.JournalEntry
import app.pokeplan.care.Prompts
import app.pokeplan.app.ExactInput
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.TimeZone
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.pokemon.CarePartyPanel
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.BuddyStage
import app.pokeplan.ui.screens.FirstRecordEgg
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormDivider
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormLabel
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuGrid
import app.pokeplan.ui.screens.SectionsKey
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.PixelMomentField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.StageGuest
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.VisitorScene
import app.pokeplan.ui.screens.dayMonth
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

/**
 * Care's screens (docs/pokelife/MODULES/CARE.md section 3). Five places, always one tap apart in a row under the header (owner, 20 November 2026):
 * **Today** (the landing: the buddy, today's routines and the mood key), **Routines** (add, edit, skip and archive them), **Calm** (the breathing break and the guided DBT skills),
 * **Reflect** (the diary card and the journal, two modes of one place) and **Dream** (the dream world: the buddy and the dreamers asleep in one
 * scene, visiting, play and the party). Doing earns (a routine, a calm break, a practised skill, a week with a
 * rhythm); a diary card, a note or a journal entry never does, and the buddy never comments on them.
 *
 * [CareSection] holds the places other than Today (`model.careSection == null` is Today).
 */
enum class CareSection(val label: String, val icon: String) {
    ROUTINES("Routines", "oaks-parcel"), CALM("Calm", "lunar-wing"), REFLECT("Reflect", "journal"), DREAM("Dream", "town-map"),
}

/** The header and the row of five places stay put; only the page under them changes. Back from a place goes to Today, from Today to the module list. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CareScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    val section = model.careSection
    val back = if (section == null) ({ model.module = null }) else ({ model.careSection = null })
    BackHandler(enabled = true, onBack = back)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.CARE.label, onBack = back, color = Module.CARE.look.header)
        // Each key is as wide as its word (five equal keys cut "Routines" short); at a large text size the row scrolls rather than clip.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = Spacing.Page, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            val places: List<Pair<CareSection?, String>> = listOf(null to "Today") + CareSection.entries.map { it to it.label }
            places.forEach { (s, label) -> TabButton(label, section == s, { model.careSection = s }) }
        }
        ScreenSwitch(section) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                when (page) {
                    null -> TodayView(model, openPokemon)
                    CareSection.ROUTINES -> RoutinesView(model)
                    CareSection.CALM -> CalmView(model)
                    CareSection.REFLECT -> ReflectView(model)
                    CareSection.DREAM -> DreamView(model, openPokemon)
                }
            }
        }
    }
}

/** What to do now: the buddy, today's routines and the mood key. Visitors, the egg and the party live in Dream. */
@Composable
private fun TodayView(model: AppModel, openPokemon: (String) -> Unit) {
    val care = model.careData
    val today = model.care.todayRoutines()
    val done = today.count { it.doneOn(model.care.today) }
    CareBuddy(model, openPokemon)
    FirstRecordEgg(model, app.pokeplan.app.Tutorials.CARE_BREATH, care.routines.size + care.breaks.size + care.practices.size + care.diary.size + care.journal.size)
    if (today.isEmpty()) {
        EmptyState(
            if (care.routines.none { !it.archived }) "No routines yet" else "Nothing planned for today",
            action = "Add a routine" to { model.careSection = CareSection.ROUTINES }, sleeper = true,
        )
    } else TextBox {
        Column {
            SectionLabel("$done of ${today.size} today", icon = CareSection.ROUTINES.icon)
            today.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                RoutineLine(model, r)
            }
        }
    }
    MoodRow(model)
}

/** Reflect: the diary card and the journal stay separate records and separate modes of one place (CARE.md 3.3); a half-written journal entry opens on the journal. */
@Composable
private fun ReflectView(model: AppModel) {
    var journal by remember { mutableStateOf(model.journalDraft.isNotEmpty()) }
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        TabButton("Diary card", !journal, { journal = false }, Modifier.weight(1f))
        TabButton("Journal", journal, { journal = true }, Modifier.weight(1f))
    }
    if (journal) JournalView(model) else DiaryView(model)
}

/** Calm: the breathing break, then the skills library; a skill opens as a guided walk-through that replaces both. */
@Composable
private fun CalmView(model: AppModel) {
    var skillId by remember { mutableStateOf<String?>(null) }
    val skill = skillId?.let { Skills.byId(it) }
    if (skill != null) { SkillGuide(model, skill) { skillId = null }; return }
    CalmBreakView(model)
    // While a break runs nothing else is offered: the screen is for the breath.
    if (model.calmBreakStartedAt == null) SkillsList(model) { skillId = it }
}

/**
 * Care's buddy (Care's lead, else the Plan buddy) in the meadow, as on Home: pet it, give it a berry, and it celebrates Care's
 * own moments (a routine, a calm break, a skill, a friend) with its cry, its portrait and a hop. The visitors and Care's party
 * are in Dream, not here. It never comments on how the player feels.
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
        onPet = { avoid -> store.pet(buddy.id); model.cry(buddy); BuddyReaction.pet(buddy, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> berry?.let { store.useItem(it, buddy.id); model.effect(Sfx.EATING) }; BuddyReaction.feed(buddy, avoid = avoid) },
        onOpen = { openPokemon(buddy.id) }, onHaptic = { model.haptic() },
        onEvent = { model.cry(buddy) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
        compact = true,
    )
}

// ----- Routines -------------------------------------------------------------------------------------------------------

/**
 * One routine line: its check key first, then its title and one quiet line (time and days), and a Skip key for a routine still to do today.
 * [day] is the day it is ticked for (today, or yesterday: that only records it). Rows of a list sit in one box.
 */
@Composable
private fun RoutineLine(model: AppModel, routine: Routine, day: LocalDate = model.care.today, onOpen: (() -> Unit)? = null) {
    val care = model.care
    val isToday = day == care.today
    val done = routine.doneOn(day)
    val skipped = routine.skippedOn(day)
    val due = routine.isDueOn(day)
    Row(
        Modifier.fillMaxWidth()
            .let { if (onOpen != null) it.clickable(onClickLabel = "Show options", role = Role.Button, onClick = onOpen) else it }
            .padding(vertical = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            routine.archived || !due -> Unit
            done -> PixelIconButton("undo", "Undo", { care.undoRoutine(routine.id, day) }, Pixel.InkSoft)
            skipped -> PixelIconButton("undo", "Bring back", { care.unskipRoutine(routine.id, day) }, Pixel.InkSoft)
            else -> PixelIconButton("check", "Done", { model.effect(Sfx.SELECT); model.haptic(); care.completeRoutine(routine.id, day) })
        }
        Column(Modifier.weight(1f)) {
            Text(routine.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    if (done) (if (isToday) "Done today" else "Done") else if (skipped) "Skipped" else null,
                    routine.time?.let { clock(it) }, routineDays(routine.days),
                ).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isToday && due && !done && !skipped && !routine.archived) PixelButton("Skip", { care.skipRoutine(routine.id) }, color = Pixel.InkSoft, compact = true)
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
    var yesterday by remember { mutableStateOf(false) }
    val shownDay = if (yesterday) care.today.minus(DatePeriod(days = 1)) else care.today
    fun add() {
        if (title.isBlank()) return
        care.addRoutine(title, time, days)
        title = ""; more = false; time = null; days = emptySet()
    }

    FormBox {
        FormStack {
            PixelField(
                title, { title = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = "New routine",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            FormDisclosure("Additional details", more, { more = !more }) { RoutineFields(time, { time = it }, days, { days = it }) }
            FormSave("Add routine", ::add, ModuleAccent.CARE.accent, enabled = title.isNotBlank())
        }
    }

    val (archived, active) = data.routines.partition { it.archived }
    if (active.isEmpty()) EmptyState("No routines yet", icon = CareSection.ROUTINES.icon, addAbove = "Add a routine")
    // Help: the first CareRules.ROUTINES_PAID_PER_DAY routines finished each day earn Exp. Candy and a little friendship; no streaks.
    val sorted = active.sortedWith(compareBy(nullsLast()) { it.time })
    if (sorted.isNotEmpty()) TextBox {
        Column {
            // Yesterday can be ticked too, for a day that slipped by; it only records the routine, it never pays.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("Today", !yesterday, { yesterday = false }, Modifier.weight(1f))
                TabButton("Yesterday", yesterday, { yesterday = true }, Modifier.weight(1f))
            }
            val (done, planned) = care.routineCount(shownDay)
            if (planned > 0) SectionLabel(if (yesterday) "$done of $planned yesterday" else "$done of $planned today")
            sorted.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                RoutineLine(model, r, shownDay) { open = if (open == r.id) null else r.id }
                if (open == r.id) RoutineOptions(model, r, framed = false) { open = null }
            }
        }
    }
    if (archived.isNotEmpty()) {
        SectionLabel("Put away")
        TextBox {
            Column {
                archived.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    RoutineLine(model, r) { open = if (open == r.id) null else r.id }
                    if (open == r.id) RoutineOptions(model, r, framed = false) { open = null }
                }
            }
        }
    }
}

/** The time and the days of a routine, to set when it is added and when it is edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineFields(time: LocalTime?, onTime: (LocalTime?) -> Unit, days: Set<DayOfWeek>, onDays: (Set<DayOfWeek>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelTimeField(time, onTime, ModuleAccent.CARE.accent, label = "Time", noneLabel = "No time")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FormLabel("Days")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                DayOfWeek.entries.forEach { d -> TabButton(dayNames[d.ordinal], d in days, { onDays(if (d in days) days - d else days + d) }) }
            }
        }
    }
}

/** Edit a routine's name, time and days (what it earned and when it was done stay), put it away (kept for trends) or back, or delete it, which asks first. */
@Composable
internal fun RoutineOptions(model: AppModel, routine: Routine, startEditing: Boolean = false, framed: Boolean = true, onDone: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    var editing by remember(routine.id) { mutableStateOf(startEditing) }
    var title by remember(routine.id) { mutableStateOf(routine.title) }
    var time by remember(routine.id) { mutableStateOf(routine.time) }
    var days by remember(routine.id) { mutableStateOf(routine.days) }
    if (editing) {
        val body: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(
                    title, { title = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = "Edit routine",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                RoutineFields(time, { time = it }, days, { days = it })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("check", "Save", { model.care.updateRoutine(routine.copy(title = title.trim(), time = time, days = days)); editing = false; onDone() }, enabled = title.isNotBlank())
                    PixelIconButton("close", "Cancel", { editing = false; title = routine.title; time = routine.time; days = routine.days }, Pixel.InkSoft)
                }
            }
        }
        if (framed) TextBox { body() } else Box(Modifier.padding(vertical = Spacing.S)) { body() }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        if (asking) {
            Text("Delete it and its history?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
            PixelIconButton("trash", "Delete", { model.care.deleteRoutine(routine.id); onDone() }, Pixel.Danger)
            PixelButton("Keep", { asking = false }, color = Pixel.InkSoft, compact = true)
        } else {
            PixelIconButton("pencil", "Edit", { editing = true }, Pixel.Tone)
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
    var seconds by model::calmBreakSeconds
    var startedAt by model::calmBreakStartedAt
    var result by remember { mutableStateOf<String?>(null) }
    val buddy = model.store.careBuddy
    var wake by remember { mutableStateOf(0) }
    var waking by remember { mutableStateOf(false) }
    // Each breath it takes a deep one (its DeepBreath animation) in the middle of resting; a form without one just sleeps on.
    var breathing by remember { mutableStateOf(false) }

    fun finish(length: Int) {
        val short = length < CareRules.BREAK_MIN_SECONDS
        val paid = care.finishCalmBreak(length).isNotEmpty()
        startedAt = null
        // It wakes up beside you: a stretch (its hop), its cry, and the healing chime when the break earned.
        buddy?.let { model.cry(it) }
        if (paid) model.effect(Sfx.CALM_BREAK_END)
        if (!reduce) { waking = true; wake++ }
        result = when {
            paid -> "A calm break, done. Well rested."
            short -> "That one was under a minute, so it doesn't earn anything. It still counts in Trends." // copy-ok: result
            else -> "Today's calm breaks have all earned already. This one still counts in Trends." // copy-ok: result
        }
    }

    val start = startedAt
    val elapsed = if (start == null) 0L else (model.now - start).coerceAtLeast(0)
    val timeUp = start != null && elapsed >= seconds * 1000L
    // A break left for much longer than its length (the screen was closed and opened later) is dropped, not paid.
    LaunchedEffect(timeUp) { if (timeUp) { if (elapsed > (seconds + 600) * 1000L) startedAt = null else finish(seconds) } }

    val breath = if (start == null) -1L else elapsed / BREATH_MILLIS
    LaunchedEffect(breath) { if (breath >= 0 && !reduce) { breathing = true; wake++ } }
    if (buddy != null) SceneStage(Module.CARE.look.scene, 200.dp) {
        AnimatedSprite(
            buddy.form.id, buddy.isShiny, if (start != null) "${buddy.nickname}, resting" else buddy.nickname,
            Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XXL).clickable(enabled = start == null, onClickLabel = "Hear ${buddy.nickname}", role = Role.Button) {
                model.haptic(); model.cry(buddy)
                if (!reduce) { waking = true; wake++ }
            },
            size = 120.dp, animate = !reduce,
            action = when {
                start != null && breathing -> SpriteAction.DEEP_BREATH
                start != null -> SpriteAction.SLEEP
                waking -> SpriteAction.WAKE
                else -> SpriteAction.IDLE
            },
            actionKey = wake, onActionDone = { waking = false; breathing = false },
        )
        // The Zz while it rests.
        if (start != null) StatusIcon(StatusKind.SLEEP, (3 * LocalDensity.current.density).roundToInt(), Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.XXL + 96.dp).offset(x = 36.dp), loop = true, animate = !reduce)
    }
    if (start == null) {
        result?.let { line -> TextBox { Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        FormBox {
            FormStack {
                PixelChoice(listOf(60, 120), seconds, { if (it == 60) "1 minute" else "2 minutes" }, { s -> if (s != null) seconds = s }, label = "How long", key = ModuleAccent.CARE.accent)
                FormSave("Start", { result = null; waking = false; model.haptic(); model.effect(Sfx.CALM_BREAK_START); startedAt = model.now }, ModuleAccent.CARE.accent)
                // Help: up to CareRules.BREAKS_PAID_PER_DAY calm breaks a day earn friendship and a berry.
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
    val fill = Pixel.Tone.copy(alpha = 0.18f)
    val edge = Pixel.Tone
    Canvas(Modifier.size(160.dp).semantics { contentDescription = if (inhale) "Breathe in" else "Breathe out" }) {
        val radius = size.minDimension / 2 * (if (still) 0.8f else scale)
        val center = Offset(size.width / 2, size.height / 2)
        drawPixelDisc(fill, center, radius)
        drawPixelRing(edge, center, radius, 4.dp.toPx())
    }
}


// ----- Diary card -----------------------------------------------------------------------------------------------------

/** A 0 to 5 rating, as six small buttons; tapping the chosen one again clears it. */
@Composable
private fun Rating(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FormLabel(label)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            (0..5).forEach { n -> TabButton("$n", value == n, { onChange(if (value == n) null else n) }, Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryView(model: AppModel) {
    val care = model.care
    val data = model.careData
    // The card of any day up to today: a forgotten day can be filled in and an earlier card corrected.
    var cardDay by remember { mutableStateOf(care.today) }
    val saved = care.diaryOn(cardDay)
    // The skills practised today start ticked, so the card is quick to fill in.
    var feelings by remember(saved?.id, cardDay) { mutableStateOf(saved?.feelings ?: emptyMap()) }
    var urges by remember(saved?.id, cardDay) { mutableStateOf(saved?.urges ?: emptyMap()) }
    var skills by remember(saved?.id, cardDay) { mutableStateOf(saved?.skills ?: if (cardDay == care.today) care.practisedToday() else emptySet()) }
    var note by remember(saved?.id, cardDay) { mutableStateOf(saved?.note ?: "") }
    var newUrge by remember { mutableStateOf("") }
    var editingUrges by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf<String?>(null) }
    var allSkills by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    var moreCard by remember { mutableStateOf(false) }
    val tracked = data.settings.urges
    val dayWord = if (cardDay == care.today) "today's" else "the ${cardDay.short()}"
    FormBox {
        FormStack {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                FormLabel("Which day?")
                PixelDateField(cardDay, care.today, ModuleAccent.CARE.accent, { it?.let { d -> cardDay = d; done = false } }, presets = listOf("Today" to 0, "Yesterday" to -1), max = care.today)
            }
            FormGroup(title = "Feelings") {
                Emotion.entries.forEach { e ->
                    Rating(e.label, feelings[e]) { v -> feelings = if (v == null) feelings - e else feelings + (e to v); done = false }
                }
            }
            if (tracked.isNotEmpty()) FormGroup(title = "Urges") {
                tracked.forEach { u ->
                    Rating(u, urges[u]) { v -> urges = if (v == null) urges - u else urges + (u to v); done = false }
                }
            }
            FormGroup {
                FormLabel("Skills you used")
                val shown = if (allSkills) Skills.all else Skills.all.filter { it.id in skills || it.id in care.practisedToday() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    shown.forEach { k -> TabButton(k.name, k.id in skills, { skills = if (k.id in skills) skills - k.id else skills + k.id; done = false }) }
                    TabButton(if (allSkills) "Fewer skills" else "All skills", allSkills, { allSkills = !allSkills })
                }
            }
            FormDisclosure("Additional details", moreCard || note.isNotBlank(), { moreCard = !moreCard }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(
                        note, { note = it.take(500); done = false }, Modifier.fillMaxWidth(), label = "Note",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    fun add() {
                        val u = newUrge.trim()
                        if (u.isNotEmpty() && u !in tracked) care.changeSettings { s -> s.copy(urges = s.urges + u) }
                        newUrge = ""
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                        PixelField(
                            newUrge, { newUrge = it.take(40) }, Modifier.weight(1f), singleLine = true, label = "Track an urge",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                        )
                        PixelIconButton("plus", "Add", ::add, Pixel.InkSoft, enabled = newUrge.isNotBlank())
                    }
                    if (tracked.isNotEmpty()) PixelButton(if (editingUrges) "Done" else "Edit urges", { editingUrges = !editingUrges; stopping = null }, color = Pixel.InkSoft, compact = true)
                    // Taking an urge off the card is rare and asks first; the cards already saved keep it.
                    if (editingUrges) tracked.forEach { u ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            if (stopping == u) {
                                Text("Stop tracking $u?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                                PixelButton("Stop tracking", { care.changeSettings { s -> s.copy(urges = s.urges - u) }; urges = urges - u; stopping = null }, color = Pixel.Danger, compact = true)
                                PixelButton("Keep", { stopping = null }, color = Pixel.InkSoft, compact = true)
                            } else {
                                Text(u, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                PixelIconButton("trash", "Stop tracking $u", { stopping = u }, Pixel.InkSoft)
                            }
                        }
                    }
                }
            }
            FormSave(
                if (saved == null) "Save $dayWord card" else "Update $dayWord card",
                { care.saveDiary(feelings, urges, skills, note, cardDay); model.effect(Sfx.SELECT); done = true }, ModuleAccent.CARE.accent,
                enabled = feelings.isNotEmpty() || urges.isNotEmpty() || skills.isNotEmpty() || note.isNotBlank(),
            )
            if (done) Text("Saved", style = MaterialTheme.typography.bodyMedium, color = Pixel.Tone, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    var pastShown by remember { mutableStateOf(14) }
    val pastAll = data.diary.filter { it.day != care.today }.sortedByDescending { it.day }
    val past = pastAll.take(pastShown)
    if (past.isNotEmpty()) SectionLabel("Earlier cards")
    past.forEach { c ->
        ListRow(onClick = { cardDay = c.day; done = false }, onClickLabel = "Open this card to edit", trail = { PixelIconButton("trash", "Delete", { val before = model.careData; care.deleteDiary(c.id); model.offerUndo("Deleted: diary card") { care.restore(before) } }, Pixel.InkSoft) }) {
            Text(c.day.short(), style = MaterialTheme.typography.bodyLarge)
            val strongest = c.feelings.entries.sortedByDescending { it.value }.filter { it.value > 0 }.take(3)
            if (strongest.isNotEmpty()) Text(strongest.joinToString(", ") { "${it.key.label} ${it.value}" }, style = MaterialTheme.typography.bodyMedium)
            if (c.urges.isNotEmpty()) Text("Urges: " + c.urges.entries.joinToString(", ") { "${it.key} ${it.value}" }, style = MaterialTheme.typography.bodyMedium)
            if (c.skills.isNotEmpty()) Text("Skills: " + c.skills.mapNotNull { Skills.byId(it)?.name }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            c.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (pastAll.size > pastShown) PixelButton("Show older", { pastShown += 14 }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

// ----- Skills ---------------------------------------------------------------------------------------------------------

/** The library, by DBT group: a tap opens the skill as a guided walk-through ([SkillGuide]). */
@Composable
private fun SkillsList(model: AppModel, onOpen: (String) -> Unit) {
    model.careData // redraw when Care changes
    val practised = model.care.practisedToday()
    // Help: skills come from DBT; up to CareRules.SKILLS_PAID_PER_DAY a day earn friendship and a berry. A reminder, not therapy; in danger, call the local emergency number.
    app.pokeplan.care.SkillGroup.entries.forEach { group ->
        SectionLabel(group.label)
        TextBox {
            Column {
                Skills.inGroup(group).forEachIndexed { i, k ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    Column(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${k.name}", role = Role.Button) { onOpen(k.id) }.padding(vertical = Spacing.S),
                    ) {
                        Text(k.name, style = MaterialTheme.typography.bodyLarge)
                        Text(if (k.id in practised) "Practised today" else k.use, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * A skill walked through the way the breathing break is: one page at a time with a bar, never the whole list of steps at once. Page 0 says what the
 * skill is for, pages 1 to n are its steps (Back and Next, no timer, no wrong answer), and the last page is the close: "I practised this" pays
 * through [care] as before (the breathing skill instead leads back to the break, which pays as a calm break). Leaving at any page costs nothing.
 */
@Composable
private fun SkillGuide(model: AppModel, skill: Skill, onClose: () -> Unit) {
    val care = model.care
    model.careData // redraw when Care changes
    val practised = skill.id in care.practisedToday()
    var page by remember(skill.id) { mutableStateOf(0) }
    val steps = skill.steps.size
    val end = steps + 1
    val where = when (page) { 0 -> skill.name; end -> "${skill.name}: the last page"; else -> "${skill.name}: step $page of $steps" }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelBar(page.toFloat() / end, color = Pixel.Tone, label = where)
            when (page) {
                0 -> {
                    Text(skill.name, style = MaterialTheme.typography.titleLarge)
                    Text(skill.group.label, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text(skill.use, style = MaterialTheme.typography.bodyLarge)
                }
                end -> {
                    Text(if (skill.calmBreak) "Ready to breathe?" else "That was the last step.", style = MaterialTheme.typography.titleLarge) // copy-ok: result
                    if (practised) Text("Practised today.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                else -> {
                    Text("Step $page", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                    Text(skill.steps[page - 1], style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (page == end) {
                if (skill.calmBreak) PixelButton("Start the breathing", onClose, Modifier.fillMaxWidth(), ModuleAccent.CARE.accent)
                else PixelButton(if (practised) "Practised again" else "I practised this", { model.effect(Sfx.LEARN); model.haptic(); care.practiseSkill(skill.id); onClose() }, Modifier.fillMaxWidth(), ModuleAccent.CARE.accent)
            } else PixelButton(if (page == 0) "Begin" else "Next", { model.haptic(); page++ }, Modifier.fillMaxWidth(), ModuleAccent.CARE.accent)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) PixelButton("Back", { page-- }, color = Pixel.InkSoft, compact = true)
                PixelButton("All skills", onClose, color = Pixel.InkSoft, compact = true)
            }
        }
    }
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
    // null: written just now.
    var writtenAt by remember { mutableStateOf<Long?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    fun save() {
        if (care.write(text, answering, tags, writtenAt) != null) { text = ""; answering = null; tags = emptySet(); writtenAt = null }
    }

    if (prompt != null && answering == null) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(prompt.text, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Write about this", { answering = prompt.id }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Another question", { care.skipPrompt() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
    var moreEntry by remember { mutableStateOf(false) }
    FormBox {
        FormStack {
            val asked = answering?.let { Prompts.byId(it) }
            if (asked != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(asked.text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                PixelButton("Not this", { answering = null }, color = Pixel.InkSoft, compact = true)
            }
            PixelField(
                text, { text = it.take(5_000) }, Modifier.fillMaxWidth(), minLines = 4, label = "Entry",
                placeholder = "Write anything",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FormDisclosure("Additional details", moreEntry || tags.isNotEmpty() || writtenAt != null, { moreEntry = !moreEntry }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        FormLabel("Tags")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            JOURNAL_TAGS.forEach { t -> TabButton(t, t in tags, { tags = if (t in tags) tags - t else tags + t }) }
                        }
                    }
                    PixelMomentField(writtenAt ?: model.store.nowMillis, { writtenAt = it }, model.store.nowMillis, ModuleAccent.CARE.accent, label = "Written")
                }
            }
            FormSave("Save entry", ::save, ModuleAccent.CARE.accent, enabled = text.isNotBlank())
        }
    }
    if (data.journal.size > 3) PixelField(
        query, { query = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search the journal",
    )
    var shown by remember { mutableStateOf(30) }
    val found = care.searchJournal(query)
    val entries = found.take(shown)
    if (data.journal.isNotEmpty() && entries.isEmpty()) EmptyState("Nothing found", icon = CareSection.REFLECT.icon, action = "Clear the search" to { query = "" })
    entries.forEach { e ->
        ListRow(
            trail = {
                PixelIconButton("pencil", "Edit", { editingId = if (editingId == e.id) null else e.id }, Pixel.InkSoft)
                PixelIconButton("trash", "Delete", { val before = model.careData; care.deleteEntry(e.id); model.offerUndo("Deleted: journal entry") { care.restore(before) } }, Pixel.InkSoft)
            },
        ) {
            Text(e.day.short() + " " + ExactInput.formatMinuteOfDay(e.atMillis.toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * 60 + it.minute }), style = MaterialTheme.typography.bodyMedium)
            e.promptId?.let { Prompts.byId(it) }?.let { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
            Text(e.text, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (e.tags.isNotEmpty()) Text(e.tags.sorted().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
        if (editingId == e.id) JournalEditor(model, e) { editingId = null }
    }
    if (found.size > shown) PixelButton("Show older", { shown += 30 }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

/** An entry's words, tags and the exact moment it is dated to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEditor(model: AppModel, e: JournalEntry, onDone: () -> Unit) {
    var text by remember(e.id) { mutableStateOf(e.text) }
    var at by remember(e.id) { mutableStateOf(e.atMillis) }
    var tags by remember(e.id) { mutableStateOf(e.tags) }
    FormBox {
        FormStack {
            PixelField(text, { text = it.take(5_000) }, Modifier.fillMaxWidth(), minLines = 4, label = "Entry", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            FormGroup {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    FormLabel("Tags")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        JOURNAL_TAGS.forEach { t -> TabButton(t, t in tags, { tags = if (t in tags) tags - t else tags + t }) }
                    }
                }
                PixelMomentField(at, { at = it }, model.store.nowMillis, ModuleAccent.CARE.accent, label = "Written")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft)
                FormSave("Save", { model.care.editEntry(e.id, text, at, tags); model.say("Saved."); onDone() }, ModuleAccent.CARE.accent, Modifier.weight(1f), enabled = text.isNotBlank())
            }
        }
    }
}

// ----- Trends ---------------------------------------------------------------------------------------------------------

/** Care's trends, shown in Me (owner, 20 November 2026). */
@Composable
internal fun CareTrendsView(model: AppModel) = app.pokeplan.ui.theme.ModuleTone(ModuleAccent.CARE) { TrendsView(model) }

@Composable
private fun TrendsView(model: AppModel) {
    val care = model.care
    model.careData // redraw when Care changes
    var emotion by remember { mutableStateOf(Emotion.JOY) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("The last 7 days")
            listOf(
                "Routines" to "${care.routinesLast7Days()}",
                "Skills" to "${care.skillsLast7Days()}",
                "Calm breaks" to "${care.calmMinutesLast7Days()} min",
                "Best week" to "${care.bestWeek()}",
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    val moods = care.moodDays(14)
    if (moods.any { it.second != null }) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("How days felt")
            moods.filter { it.second != null }.asReversed().forEach { (day, level) ->
                val name = moodNames[level!! - 1]
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("${day.dayMonth()}: $name", style = MaterialTheme.typography.bodyMedium)
                    PixelBar(level / 5f, Modifier.fillMaxWidth(), ModuleAccent.CARE.accent, label = name)
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Feelings, week by week")
            val weeks = care.feelingWeeks(8)
            if (weeks.all { it.cards == 0 }) {
                Text("No ratings yet", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            PixelDropdown(Emotion.entries.toList(), emotion, { it?.label ?: "" }, { e -> if (e != null) emotion = e }, Modifier.fillMaxWidth(), label = "Feeling")
            weeks.asReversed().filter { it.averages[emotion] != null }.forEach { w ->
                val avg = w.averages[emotion]
                val label = if (avg == null) "not rated" else "${emotion.label} ${(avg * 10).roundToInt() / 10.0} of 5, ${w.cards} card${if (w.cards == 1) "" else "s"}"
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("Week of ${w.weekStart.dayMonth()}: $label", style = MaterialTheme.typography.bodyMedium)
                    if (avg != null) PixelBar((avg / 5).toFloat(), Modifier.fillMaxWidth(), Pixel.Tone, label = label)
                }
            }
        }
    }
}

private val moodNames = listOf("Rough", "Low", "Okay", "Good", "Great")

/** Five mood keys that wrap onto a second line rather than cut a word; tapping the chosen one again takes it back ([onPick] gets null). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodKeys(chosen: Int?, onPick: (Int?) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        moodNames.forEachIndexed { i, name ->
            val level = i + 1
            TabButton(name, chosen == level, { onPick(if (chosen == level) null else level) }, key = ModuleAccent.CARE.accent)
        }
    }
}

/** "How is today?" in one tap: five keys, no card to fill in, nothing paid. */
@Composable
private fun MoodRow(model: AppModel) {
    model.careData // redraw when Care changes
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FormLabel("How is today?")
            MoodKeys(model.care.diaryToday()?.mood) { model.care.setMood(it); model.haptic() }
        }
    }
}
