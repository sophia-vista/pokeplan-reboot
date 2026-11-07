package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.app.Tutorials
import app.pokeplan.heal.ActivityRecord
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.GoalKind
import app.pokeplan.heal.GoalStatus
import app.pokeplan.heal.HealKinds
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.HealSettings
import app.pokeplan.heal.HealUnits
import app.pokeplan.heal.MeasureType
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToInt

/** Heal's screens (docs/pokelife `MODULES/HEAL.md`), the manual slice: activity, hydration, sleep, body measurements and optional goals. */
enum class HealSection(val label: String, val icon: String) {
    ACTIVITY("Activity", "x-speed"), HYDRATION("Hydration", "fresh-water"), SLEEP("Sleep", "moon-stone"), BODY("Body", "scope-lens"),
    GOALS("Goals", "star-piece"), SETTINGS("Settings", "storage-key"),
}

private val zone: TimeZone get() = TimeZone.currentSystemDefault()
private fun clockText(t: LocalTime) = t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
private fun shift(t: LocalTime, minutes: Int): LocalTime { val m = ((t.hour * 60 + t.minute + minutes) % 1440 + 1440) % 1440; return LocalTime(m / 60, m % 60) }
private fun Long.localTime(): LocalTime = toLocalDateTime(zone).time
private fun Long.localDay(): LocalDate = toLocalDateTime(zone).date
private fun ml(v: Int) = if (v >= 1000) HealRules.format(v / 1000.0) + " L" else "$v ml"

/** The line under Heal's card in Modules: the plain count of what is logged today, or what it is for. Never a score. */
internal fun healSubtitle(model: AppModel): String {
    val minutes = HealRules.activeMinutes(model.healData, model.store.today)
    return if (minutes == null) "activity, water, sleep" else HealRules.duration(minutes) + " active today"
}

@Composable
internal fun HealScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    ScreenSwitch(model.healSection, depth = { if (it == null) 0 else 1 }) { section -> HealPage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HealPage(model: AppModel, section: HealSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        HealMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.healSection = null }, icon = section.icon, color = Module.HEAL.look.header) {
        when (section) {
            HealSection.ACTIVITY -> ActivityView(model)
            HealSection.HYDRATION -> HydrationView(model)
            HealSection.SLEEP -> SleepView(model)
            HealSection.BODY -> BodyView(model)
            HealSection.GOALS -> GoalsHealView(model)
            HealSection.SETTINGS -> HealSettingsView(model)
        }
    }
}

@Composable
private fun HealMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.healData
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.HEAL.label, onBack = onBack, color = Module.HEAL.look.header)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            RewardBox(model)
            TutorialCard(model, Tutorials.HEAL_WALK, healActivities = data.activities.size)
            TodayCard(model)
            MenuGrid(
                listOf(
                    MenuEntry(HealSection.ACTIVITY.name, HealSection.ACTIVITY.icon, "Activity", activityDetail(model), tint = ModuleAccent.HEAL.accent),
                    MenuEntry(HealSection.HYDRATION.name, HealSection.HYDRATION.icon, "Hydration", hydrationDetail(model), tint = ModuleAccent.HEAL.accent),
                    MenuEntry(HealSection.SLEEP.name, HealSection.SLEEP.icon, "Sleep", if (data.sleep.isEmpty()) "log a night" else "${data.sleep.size} nights", tint = ModuleAccent.HEAL.accent),
                    MenuEntry(HealSection.BODY.name, HealSection.BODY.icon, "Body", if (data.measurements.isEmpty()) "optional" else "${data.measurements.size} entries", tint = ModuleAccent.HEAL.accent),
                    MenuEntry(HealSection.GOALS.name, HealSection.GOALS.icon, "Goals", if (data.goals.none { it.status == GoalStatus.ACTIVE }) "optional" else "${data.goals.count { it.status == GoalStatus.ACTIVE }} active", tint = Pixel.Warning),
                    MenuEntry(HealSection.SETTINGS.name, HealSection.SETTINGS.icon, "Settings", "Field Research, reminders, your data", tint = Pixel.InkSoft),
                ),
            ) { key -> model.healSection = HealSection.valueOf(key) }
            FieldResearchLine(model)
            HealPartyPanel(model, openPokemon)
        }
    }
}

private fun activityDetail(model: AppModel): String =
    HealRules.activeMinutes(model.healData, model.store.today)?.let { HealRules.duration(it) + " today" } ?: "not logged today"

private fun hydrationDetail(model: AppModel): String =
    HealRules.hydrationOn(model.healData, model.store.today)?.let { ml(it) + " today" } ?: "not logged today"

/**
 * What is recorded today, and what can be recorded now (HEAL.md section 2.2): plain numbers where something was logged, "Not logged" where not (never 0), the latest
 * finished night's sleep, and no score and no verdict. Body measurements show only if the player asked for them here.
 */
@Composable
private fun TodayCard(model: AppModel) {
    val data = model.healData
    val today = model.store.today
    val activity = HealRules.activeMinutes(data, today)
    val water = HealRules.hydrationOn(data, today)
    val sleep = HealRules.latestSleep(data)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Today")
            Text(if (activity == null) "Activity: not logged" else "Activity: ${HealRules.duration(activity)}", style = MaterialTheme.typography.bodyLarge)
            Text(if (water == null) "Water: not logged" else "Water: ${ml(water)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (sleep == null) "Last sleep: not logged" else "Last sleep: ${HealRules.duration(sleep.minutes)}, woke ${sleep.day.short()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (data.settings.showMeasurementsOnToday) {
                val w = HealRules.latestMeasurement(data, MeasureType.WEIGHT)
                if (w != null) Text("Weight: ${HealUnits.show(w, data.settings.weightUnit)} (${w.day.short()})", style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Log activity", { model.healSection = HealSection.ACTIVITY }, Modifier.weight(1f), Pixel.Red, compact = true)
                PixelButton("Add a drink", { model.healSection = HealSection.HYDRATION }, Modifier.weight(1f), Pixel.Blue, compact = true)
            }
        }
    }
}

@Composable
private fun FieldResearchLine(model: AppModel) {
    val on = model.healData.settings.fieldResearch
    val points = model.store.healEggMeter()
    Text(
        if (on) "Field Research is on · Field Egg $points of ${app.pokeplan.core.reward.RewardEngine.HEAL_EGG_METER_SIZE}"
        else "Field Research is off",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

// ----- Activity ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityView(model: AppModel) {
    val heal = model.heal
    var type by remember { mutableStateOf(ActivityType.WALK) }
    var minutes by remember { mutableStateOf("30") }
    var ago by remember { mutableStateOf(0) }
    var distance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    fun save() {
        val km = distance.replace(',', '.').toDoubleOrNull()
        val out = heal.logActivity(type, minutes.toIntOrNull() ?: 0, endedAtMillis = model.store.nowMillis - ago * 60_000L, distanceMeters = km?.let { (it * 1000).roundToInt() }, note = note)
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else { message = "Saved." to NoticeTone.INFO; note = ""; distance = ""; more = false; model.haptic(); model.effect(Sfx.HEAL) }
    }
    RewardBox(model)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("What did you do?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                ActivityType.entries.forEach { t -> TabButton(t.label, t == type, { type = t }) }
            }
            PixelField(
                minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = "Minutes", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(15, 30, 45, 60).forEach { m -> PixelButton("$m", { minutes = m.toString() }, color = Pixel.InkSoft, compact = true) }
            }
            PixelTitle("It ended")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(0 to "Just now", 60 to "1 h ago", 180 to "3 h ago", 1440 to "Yesterday").forEach { (a, label) -> TabButton(label, a == ago, { ago = a }) }
            }
            PixelButton(if (more) "Fewer options" else "Distance and a note", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                PixelField(
                    distance, { distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }, Modifier.fillMaxWidth(), label = "Distance (km), optional", singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note, optional", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), Pixel.Green, enabled = (minutes.toIntOrNull() ?: 0) > 0)
            message?.let { (text, tone) -> Notice(text, tone = tone) }
        }
    }
    if (!model.healData.settings.fieldResearch) Text(
        "Field Research is off, so this only keeps your history. Turn it on in Settings to find Pokémon from sessions of ${HealRules.RESEARCH_MIN_MINUTES} minutes or more.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
    val all = model.healData.activities.sortedByDescending { it.startMillis }
    if (all.isEmpty()) EmptyState("Nothing logged yet", "A type and how long it lasted are enough. Walks, stretching and everything else count.", icon = HealSection.ACTIVITY.icon)
    else {
        SectionLabel("History")
        all.take(30).forEach { a ->
            ActivityRow(a) { open = if (open == a.id) null else a.id }
            if (open == a.id) ActivityOptions(model, a) { open = null }
        }
        if (all.size > 30) Text("${all.size - 30} older ones are kept. Export them in Settings.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

@Composable
private fun ActivityRow(a: ActivityRecord, onClick: () -> Unit) {
    ListRow(onClick = onClick, onClickLabel = "Show options") {
        Text("${a.type.label} · ${HealRules.duration(a.minutes)}", style = MaterialTheme.typography.bodyLarge)
        val extra = listOfNotNull(a.distanceMeters?.let { HealRules.format(it / 1000.0) + " km" }, a.note.takeIf { it.isNotBlank() })
        Text((listOf(a.day.short() + " " + clockText(a.startMillis.localTime())) + extra).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityOptions(model: AppModel, a: ActivityRecord, onDone: () -> Unit) {
    var asking by remember(a.id) { mutableStateOf(false) }
    var editing by remember(a.id) { mutableStateOf(false) }
    var type by remember(a.id) { mutableStateOf(a.type) }
    var minutes by remember(a.id) { mutableStateOf(a.minutes.toString()) }
    var note by remember(a.id) { mutableStateOf(a.note) }
    var error by remember(a.id) { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (editing) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ActivityType.entries.forEach { t -> TabButton(t.label, t == type, { type = t }) }
                }
                PixelField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = "Minutes", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = error)
                PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Save", {
                        val out = model.heal.updateActivity(a.copy(type = type, minutes = minutes.toIntOrNull() ?: 0, note = note))
                        if (out.record == null) error = out.refused else onDone()
                    }, Modifier.weight(1f), Pixel.Green, compact = true)
                    PixelButton("Cancel", { editing = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            } else if (asking) {
                Text("Delete this activity? What it earned stays earned.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Delete", { model.heal.deleteActivity(a.id); onDone() }, Modifier.weight(1f), Pixel.Danger, compact = true)
                    PixelButton("Keep it", { asking = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.Blue, compact = true)
                PixelButton("Delete", { asking = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

// ----- Hydration ----------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HydrationView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    val today = model.store.today
    var custom by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    fun add(v: Int) {
        val out = heal.logDrink(v)
        message = if (out.record == null) out.refused else null
        if (out.record != null) { model.haptic(); model.effect(Sfx.HEAL); custom = "" }
    }
    RewardBox(model)
    val total = HealRules.hydrationOn(data, today)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Today")
            Text(if (total == null) "Nothing logged yet" else ml(total), style = MaterialTheme.typography.titleSmall)
            data.settings.hydrationTargetMl?.let { target ->
                PixelBar(((total ?: 0).toFloat() / target).coerceIn(0f, 1f), color = Pixel.Blue, label = "Your own target: ${ml(target)}")
                Text(if ((total ?: 0) >= target) "You reached the target you set." else "Your target is ${ml(target)}. No rush.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(150, 250, 330, 500).forEach { v -> PixelButton("+$v ml", { add(v) }, color = Pixel.Blue, compact = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    custom, { custom = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true, placeholder = "Another amount (ml)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                PixelButton("Add", { custom.toIntOrNull()?.let(::add) }, color = Pixel.Green, enabled = custom.toIntOrNull() != null, compact = true)
            }
            message?.let { Notice(it, tone = NoticeTone.WARNING) }
        }
    }
    val drinks = heal.drinksToday()
    if (drinks.isEmpty()) EmptyState("No drinks today", "Tap an amount above. Any drink counts the same here: this only keeps your own record.", icon = HealSection.HYDRATION.icon)
    else drinks.forEach { d ->
        ListRow(trail = { PixelButton("Delete", { heal.deleteDrink(d.id) }, color = Pixel.InkSoft, compact = true) }) {
            Text(ml(d.ml) + d.drink.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(clockText(d.atMillis.localTime()), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    val earlier = (1..7).mapNotNull { back ->
        val day = LocalDate.fromEpochDays(today.toEpochDays() - back)
        HealRules.hydrationOn(data, day)?.let { day to it }
    }
    if (earlier.isNotEmpty()) {
        SectionLabel("Earlier this week")
        earlier.forEach { (day, v) -> Text("${day.short()}: ${ml(v)}", style = MaterialTheme.typography.bodyLarge) }
    }
}

// ----- Sleep --------------------------------------------------------------------------------------------------------------

@Composable
private fun SleepView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    var yesterday by remember { mutableStateOf(false) }
    var wake by remember { mutableStateOf(LocalTime(7, 0)) }
    var length by remember { mutableStateOf(8 * 60) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    fun save() {
        val day = LocalDate.fromEpochDays(model.store.today.toEpochDays() - if (yesterday) 1 else 0)
        val end = LocalDateTime(day, wake).toInstant(zone).toEpochMilliseconds()
        val out = heal.logSleep(end - length * 60_000L, end)
        message = if (out.record == null) (out.refused ?: "That could not be saved.") to NoticeTone.WARNING else { model.effect(Sfx.HEAL); "Saved." to NoticeTone.INFO }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("A night")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TabButton("Woke today", !yesterday, { yesterday = false }, Modifier.weight(1f))
                TabButton("Woke yesterday", yesterday, { yesterday = true }, Modifier.weight(1f))
            }
            Stepper("Woke at", clockText(wake), { wake = shift(wake, -15) }, { wake = shift(wake, 15) })
            Stepper("Slept for", HealRules.duration(length), { length = (length - 15).coerceAtLeast(15) }, { length = (length + 15).coerceAtMost(HealRules.SLEEP_MAX_MINUTES) })
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), Pixel.Green)
            message?.let { (text, tone) -> Notice(text, tone = tone) }
        }
    }
    val nights = data.sleep.sortedByDescending { it.endMillis }
    if (nights.isEmpty()) EmptyState("No nights logged", "Add how long you slept whenever you like. Heal never tells you a night was too short or too long.", icon = HealSection.SLEEP.icon)
    else {
        SectionLabel("History")
        nights.take(21).forEach { s ->
            ListRow(trail = { PixelButton("Delete", { heal.deleteSleep(s.id) }, color = Pixel.InkSoft, compact = true) }) {
                Text(HealRules.duration(s.minutes), style = MaterialTheme.typography.bodyLarge)
                Text("${clockText(s.startMillis.localTime())} to ${clockText(s.endMillis.localTime())}, woke ${s.day.short()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        PixelButton("-15", minus, color = Pixel.InkSoft, compact = true)
        Text(value, style = MaterialTheme.typography.titleSmall)
        PixelButton("+15", plus, color = Pixel.InkSoft, compact = true)
    }
}

// ----- Body ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    var type by remember { mutableStateOf(MeasureType.WEIGHT) }
    var unit by remember { mutableStateOf(data.settings.weightUnit) }
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    fun pick(t: MeasureType) { type = t; unit = if (t == MeasureType.WEIGHT) data.settings.weightUnit else t.units.first() }
    fun save() {
        val v = value.replace(',', '.').toDoubleOrNull()
        val out = if (v == null) null else heal.logMeasurement(type, v, unit, label)
        message = when {
            out == null -> "Enter a number." to NoticeTone.WARNING
            out.record == null -> (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
            else -> "Saved." to NoticeTone.INFO
        }
        if (out?.record != null) { value = ""; label = "" }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("A measurement")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                MeasureType.entries.forEach { t -> TabButton(t.label, t == type, { pick(t) }) }
            }
            if (type == MeasureType.OTHER) PixelField(label, { label = it.take(40) }, Modifier.fillMaxWidth(), label = "What is it?", singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(value, { value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, Modifier.weight(1f), placeholder = "Value", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (type.units.size > 1) type.units.forEach { u -> TabButton(u, u == unit, { unit = u }) }
                else if (type.units.first().isNotEmpty()) Text(type.units.first(), style = MaterialTheme.typography.bodyLarge)
            }
            PixelButton("Save", ::save, Modifier.fillMaxWidth(), Pixel.Green, enabled = value.isNotBlank())
            message?.let { (text, tone) -> Notice(text, tone = tone) }
        }
    }
    ToggleRow("Show my weight on Today", data.settings.showMeasurementsOnToday, detail = "Off by default.") { heal.setShowMeasurementsOnToday(it) }
    if (data.measurements.isEmpty()) EmptyState("Nothing here", "You never need to enter a body measurement to use Heal.", icon = HealSection.BODY.icon)
    else {
        MeasureType.entries.forEach { t ->
            val own = data.measurements.filter { it.type == t }
            if (own.isEmpty()) return@forEach
            SectionLabel(t.label)
            own.map { it.unit }.distinct().forEach { u ->
                HealRules.trendText(data, t, u)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            }
            own.sortedByDescending { it.atMillis }.take(10).forEach { m ->
                ListRow(trail = { PixelButton("Delete", { heal.deleteMeasurement(m.id) }, color = Pixel.InkSoft, compact = true) }) {
                    Text((m.label.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()) + HealUnits.show(m, m.unit), style = MaterialTheme.typography.bodyLarge)
                    Text(m.day.short(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
        }
    }
}

// ----- Goals --------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsHealView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    var kind by remember { mutableStateOf(GoalKind.WEEKLY_ACTIVE_MINUTES) }
    var target by remember { mutableStateOf("") }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("A goal of your own")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                GoalKind.entries.forEach { k -> TabButton(k.label, k == kind, { kind = k }) }
            }
            PixelField(target, { target = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = "Target (${kind.unit})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            PixelButton("Add goal", { target.toIntOrNull()?.let { heal.addGoal(kind, it); target = "" } }, Modifier.fillMaxWidth(), Pixel.Green, enabled = (target.toIntOrNull() ?: 0) > 0)
        }
    }
    if (data.goals.isEmpty()) EmptyState("No goals", "Heal works fine without one.", icon = HealSection.GOALS.icon)
    data.goals.filter { it.status != GoalStatus.ARCHIVED }.forEach { g ->
        val p = HealRules.progress(g, data, model.store.today, model.data.settings.weekStart)
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("${g.kind.label}: ${g.target}", style = MaterialTheme.typography.bodyLarge)
                if (g.status == GoalStatus.PAUSED) Text("Paused", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                else {
                    PixelBar(p.fraction, color = if (p.met) Pixel.Green else Pixel.Blue, label = if (p.done == null) "Nothing logged yet" else "${p.done} of ${g.target} ${g.kind.unit} so far")
                    Text(if (p.done == null) "Nothing logged yet" else if (p.met) "Reached" else "${p.done} of ${g.target} ${g.kind.unit} so far", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton(if (g.status == GoalStatus.PAUSED) "Resume" else "Pause", { heal.setGoalStatus(g.id, if (g.status == GoalStatus.PAUSED) GoalStatus.ACTIVE else GoalStatus.PAUSED) }, Modifier.weight(1f), Pixel.Blue, compact = true)
                    PixelButton("Put away", { heal.setGoalStatus(g.id, GoalStatus.ARCHIVED) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Delete", { heal.deleteGoal(g.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

// ----- Settings: Field Research, reminders, your data -----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealSettingsView(model: AppModel) {
    val heal = model.heal
    val settings = model.healData.settings
    var target by remember { mutableStateOf(settings.hydrationTargetMl?.toString().orEmpty()) }
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<HealKinds?>(null) }
    var askingAll by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Field Research")
            ToggleRow(
                "Find Pokémon from what I log", settings.fieldResearch,
                detail = "On by default. A session of ${HealRules.RESEARCH_MIN_MINUTES} minutes or more, just logged, and the day's first drink can find a Field Egg. Weight, sleep and every other number never earn anything.",
            ) { heal.setFieldResearch(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Water")
            PixelField(
                target, { target = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = "Your own daily target (ml), optional", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                helper = "Heal never sets one for you.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Save target", { heal.setHydrationTarget(target.toIntOrNull()) }, Modifier.weight(1f), Pixel.Blue, compact = true)
                PixelButton("No target", { target = ""; heal.setHydrationTarget(null) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            PixelTitle("Reminders")
            Text("Off by default. Up to ${HealSettings.MAX_REMINDERS} a day, quiet during your quiet hours. They only ask if you would like a drink; they never say how much.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            settings.hydrationReminders.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(clockText(t), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("-15", { heal.setHydrationReminders(settings.hydrationReminders - t + shift(t, -15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("+15", { heal.setHydrationReminders(settings.hydrationReminders - t + shift(t, 15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("Remove", { heal.setHydrationReminders(settings.hydrationReminders - t) }, color = Pixel.InkSoft, compact = true)
                }
            }
            if (settings.hydrationReminders.size < HealSettings.MAX_REMINDERS) {
                val next = if (settings.hydrationReminders.isEmpty()) LocalTime(10, 0) else shift(settings.hydrationReminders.last(), 120)
                PixelButton("Add a reminder at ${clockText(next)}", { heal.setHydrationReminders(settings.hydrationReminders + next) }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TabButton("kg", settings.weightUnit == "kg", { heal.setWeightUnit("kg") }, Modifier.weight(1f))
                TabButton("lb", settings.weightUnit == "lb", { heal.setWeightUnit("lb") }, Modifier.weight(1f))
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            Text(
                "Your records stay on this phone. Export what you choose as a file you can read, or erase one kind at a time. Erasing Heal never touches your Pokémon, your Poké, your Bag, Plan or Care.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-heal.json", heal.export()) }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    HealKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-heal-${k.name.lowercase()}.json", heal.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                }
            } else Text("Exporting is not available on this device.", style = MaterialTheme.typography.bodyMedium)
            note?.let { Notice(it) }
            HealKinds.entries.forEach { k ->
                if (asking == k) {
                    Text("Erase all ${k.label.lowercase()} records? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelButton("Erase", { heal.erase(k); asking = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        PixelButton("Keep them", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                } else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) {
                Text("Erase every Heal record? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Erase all", { heal.eraseAll(); askingAll = false }, Modifier.weight(1f), Pixel.Danger, compact = true)
                    PixelButton("Keep them", { askingAll = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            } else PixelButton("Erase all Heal records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
        }
    }
    Text(
        "Connecting a watch or Health Connect is not available yet. Heal is complete without them.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}
