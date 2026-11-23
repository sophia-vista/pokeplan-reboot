package app.pokeplan.ui.heal

import app.pokeplan.ui.theme.Accordion
import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.app.Tutorials
import app.pokeplan.heal.ActivityRecord
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.DoseStatus
import app.pokeplan.heal.GoalKind
import app.pokeplan.heal.GoalStatus
import app.pokeplan.heal.HealKinds
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.HealSettings
import app.pokeplan.heal.HealUnits
import app.pokeplan.heal.HydrationEntry
import app.pokeplan.heal.Measurement
import app.pokeplan.heal.Medication
import app.pokeplan.heal.MedicationStatus
import app.pokeplan.heal.SleepRecord
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.ReadingGroup
import app.pokeplan.heal.ReadingGroups
import app.pokeplan.heal.VitalType
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.ItemIcon
import androidx.compose.ui.unit.dp
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
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.plan.duration
import app.pokeplan.ui.pokemon.HealPartyPanel
import app.pokeplan.ui.pokemon.label
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
import app.pokeplan.ui.screens.ModuleLeadStage
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDatePicker
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelMomentField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

enum class HealSection(val label: String, val icon: String) {
    ACTIVITY("Activity", "x-speed"), WELLNESS("Wellness", "hp-up"), RECORDS("Records", "potion"),
    GOALS("Goals", "star-piece"), CONNECTIONS("Connections", "town-map"), SETTINGS("Settings", "storage-key"),
}

private val zone: TimeZone get() = TimeZone.currentSystemDefault()
private fun clockText(t: LocalTime) = t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
private fun shift(t: LocalTime, minutes: Int): LocalTime { val m = ((t.hour * 60 + t.minute + minutes) % 1440 + 1440) % 1440; return LocalTime(m / 60, m % 60) }
private fun Long.localTime(): LocalTime = toLocalDateTime(zone).time
private fun ml(v: Int) = if (v >= 1000) HealRules.format(v / 1000.0) + " L" else "$v ml"

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
            HealSection.ACTIVITY -> ActivityPlace(model)
            HealSection.WELLNESS -> WellnessPlace(model)
            HealSection.RECORDS -> RecordsPlace(model)
            HealSection.GOALS -> GoalsHealView(model)
            HealSection.CONNECTIONS -> HealConnectionsView(model)
            HealSection.SETTINGS -> HealSettingsView(model)
        }
    }
}

@Composable
private fun HealMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val data = model.healData
    val sections = listOf(
        MenuEntry(HealSection.ACTIVITY.name, HealSection.ACTIVITY.icon, "Activity", "steps, exercise, sleep", tint = ModuleAccent.HEAL.accent),
        MenuEntry(HealSection.WELLNESS.name, HealSection.WELLNESS.icon, "Wellness", "water, body, readings", tint = ModuleAccent.HEAL.accent),
        MenuEntry(HealSection.RECORDS.name, HealSection.RECORDS.icon, "Records", "medications, doses", tint = ModuleAccent.HEAL.accent),
        MenuEntry(HealSection.GOALS.name, HealSection.GOALS.icon, "Goals", tint = ModuleAccent.HEAL.accent),
        MenuEntry(HealSection.CONNECTIONS.name, HealSection.CONNECTIONS.icon, "Connections", "import, Health Connect", tint = ModuleAccent.HEAL.accent),
        MenuEntry(HealSection.SETTINGS.name, HealSection.SETTINGS.icon, "Settings", tint = Pixel.InkSoft),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.HEAL.label, onBack = onBack, color = Module.HEAL.look.header, trailing = { SectionsKey(Module.HEAL.label, sections) { key -> model.healSection = HealSection.valueOf(key) } })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            ModuleLeadStage(model, Module.HEAL, model.data.player.healLead, model.store.moduleEvent(app.pokeplan.app.GameStore.HEAL_LOG_PREFIX), openPokemon = openPokemon)
            FirstRecordEgg(model, Tutorials.HEAL_WALK, data.activities.size + data.hydration.size + data.sleep.size)
            TodayCard(model)
            HealPartyPanel(model, openPokemon)
        }
    }
}

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
            StatRow("Activity", activity?.let { HealRules.duration(it) } ?: "—")
            StatRow("Water", water?.let { ml(it) } ?: "—")
            StatRow("Last sleep", sleep?.let { HealRules.duration(it.minutes) } ?: "—")
            if (data.settings.showMeasurementsOnToday) {
                val w = HealRules.latestMeasurement(data, MeasureType.WEIGHT)
                if (w != null) StatRow("Weight", HealUnits.show(w, data.settings.weightUnit))
            }
            val scheduled = HealRules.medicationScheduledOn(data, today)
            if (scheduled > 0) StatRow("Medications", "${HealRules.medicationTakenOn(data, today)} of $scheduled taken")
            if (data.settings.fieldResearch) {
                val size = app.pokeplan.core.reward.RewardEngine.HEAL_EGG_METER_SIZE
                val points = model.store.healEggMeter()
                PixelBar(points.toFloat() / size, color = Pixel.Tone, label = "Field Egg $points of $size")
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Quick actions")
            // Import first: where the phone has a provider, reading it in is the first action, and logging by hand follows (HEAL.md 10).
            if (model.health != null || model.huawei != null) {
                val importLabel = if (model.health != null) "Import from Health Connect" else "Read Huawei Health"
                PixelButton(importLabel, { model.healSection = HealSection.CONNECTIONS }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Log activity", { model.healActivityTab = 0; model.healSection = HealSection.ACTIVITY }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("Add a drink", { model.healWellnessTab = 0; model.healSection = HealSection.WELLNESS }, Modifier.weight(1f), Pixel.Tone, compact = true)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryBox(content: @Composable () -> Unit) = TextBox { Column { content() } }

@Composable
private fun HistoryRow(onClick: (() -> Unit)? = null, onClickLabel: String? = null, trail: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, onClick = onClick) else it }.padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Column(Modifier.weight(1f)) { content() }
        trail()
    }
}

/** Activity is one place with two halves (HEAL.md 2.1): the exercise and activity log, and sleep. */
@Composable
private fun ActivityPlace(model: AppModel) {
    val sleep = model.healActivityTab == 1
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        TabButton("Exercise", !sleep, { model.healActivityTab = 0 }, Modifier.weight(1f))
        TabButton("Sleep", sleep, { model.healActivityTab = 1 }, Modifier.weight(1f))
    }
    if (sleep) SleepView(model) else ActivityView(model)
}

/** Wellness is one place with three halves (HEAL.md 2.1): hydration, body measurements and the vitals read from a provider or logged by hand. */
@Composable
private fun WellnessPlace(model: AppModel) {
    val tab = model.healWellnessTab
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        TabButton("Water", tab == 0, { model.healWellnessTab = 0 }, Modifier.weight(1f))
        TabButton("Body", tab == 1, { model.healWellnessTab = 1 }, Modifier.weight(1f))
        TabButton("Readings", tab == 2, { model.healWellnessTab = 2 }, Modifier.weight(1f))
    }
    when (tab) {
        1 -> BodyView(model)
        2 -> ReadingsView(model)
        else -> HydrationView(model)
    }
}

/** Records is the private health history (HEAL.md 8): today the medication and dose log, and the timeline, symptoms and illnesses join it as further views when they are built. */
@Composable
private fun RecordsPlace(model: AppModel) = MedicationView(model)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityView(model: AppModel) {
    val heal = model.heal
    var type by remember { mutableStateOf(ActivityType.WALK) }
    var minutes by remember { mutableStateOf<Int?>(30) }
    var endedAt by remember { mutableStateOf<Long?>(null) }
    var distance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    fun save() {
        val km = distance.replace(',', '.').toDoubleOrNull()
        val out = heal.logActivity(type, minutes ?: 0, endedAtMillis = endedAt ?: model.store.nowMillis, distanceMeters = km?.let { (it * 1000).roundToInt() }, note = note)
        if (out.record == null) message = (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
        else { message = null; model.say("Saved."); note = ""; distance = ""; more = false; endedAt = null; model.haptic(); model.effect(Sfx.HEAL) }
    }
    FormBox {
        FormStack {
            FormGroup {
                PixelDropdown(ActivityType.entries.toList(), type, { it?.label ?: "" }, { t -> if (t != null) type = t }, Modifier.fillMaxWidth(), label = "What did you do?")
                PixelDurationField(minutes, { minutes = it }, ModuleAccent.HEAL.accent, label = "How long", presets = listOf(15, 30, 45, 60))
            }
            FormDisclosure("Additional details", more, { more = !more }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelMomentField(endedAt ?: model.store.nowMillis, { endedAt = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "It ended")
                    PixelField(
                        distance, { distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }, Modifier.fillMaxWidth(), label = "Distance (km)", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
            FormSave("Save", ::save, ModuleAccent.HEAL.accent, enabled = (minutes ?: 0) > 0)
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
    val activities = model.healData.activities
    val all = remember(activities) { activities.sortedByDescending { it.startMillis } }
    var shown by remember { mutableStateOf(PAGE) }
    if (all.isEmpty()) EmptyState("Nothing logged yet", icon = HealSection.ACTIVITY.icon)
    else {
        SectionLabel("History")
        HistoryBox {
            all.take(shown).forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                ActivityRow(a) { open = if (open == a.id) null else a.id }
                if (open == a.id) ActivityOptions(model, a) { open = null }
            }
        }
        if (all.size > shown) ShowOlder { shown += PAGE }
    }
}

@Composable
private fun ActivityRow(a: ActivityRecord, onClick: () -> Unit) {
    HistoryRow(onClick = onClick, onClickLabel = "Show options") {
        Text("${a.type.label} · ${HealRules.duration(a.minutes)}", style = MaterialTheme.typography.bodyLarge)
        val extra = listOfNotNull(a.distanceMeters?.let { HealRules.format(it / 1000.0) + " km" }, a.note.takeIf { it.isNotBlank() }, a.provenance?.let { "imported" })
        Text((listOf(a.day.short() + " " + clockText(a.startMillis.localTime())) + extra).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityOptions(model: AppModel, a: ActivityRecord, onDone: () -> Unit) {
    var editing by remember(a.id) { mutableStateOf(false) }
    var type by remember(a.id) { mutableStateOf(a.type) }
    var minutes by remember(a.id) { mutableStateOf<Int?>(a.minutes) }
    var started by remember(a.id) { mutableStateOf(a.startMillis) }
    var note by remember(a.id) { mutableStateOf(a.note) }
    var error by remember(a.id) { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (editing) {
            PixelDropdown(ActivityType.entries.toList(), type, { it?.label ?: "" }, { t -> if (t != null) type = t }, Modifier.fillMaxWidth(), label = "Activity")
            PixelDurationField(minutes, { minutes = it }, ModuleAccent.HEAL.accent, label = "How long", presets = listOf(15, 30, 45, 60))
            PixelMomentField(started, { started = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "It started")
            error?.let { Report(model, it, NoticeTone.WARNING) }
            PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", {
                    val out = model.heal.updateActivity(a.copy(type = type, minutes = minutes ?: 0, startMillis = started, note = note))
                    if (out.record == null) error = out.refused else onDone()
                }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("Cancel", { editing = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.Tone, compact = true)
            PixelButton("Delete", { val before = model.healData; model.heal.deleteActivity(a.id); model.offerUndo("Deleted: activity") { model.heal.restore(before) }; onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Close", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HydrationView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    val today = model.store.today
    var custom by remember { mutableStateOf("") }
    var otherAmount by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var timing by remember { mutableStateOf(false) }
    fun add(v: Int) {
        val at = time?.let { LocalDateTime(day, it).toInstant(zone).toEpochMilliseconds() } ?: if (day == today) model.store.nowMillis else heal.noonOf(day)
        val out = heal.logDrink(v, atMillis = at)
        message = if (out.record == null) out.refused else null
        if (out.record != null) { model.haptic(); model.effect(Sfx.HEAL); custom = "" }
    }
    val total = HealRules.hydrationOn(data, day)
    FormBox {
        FormStack {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelTitle(if (day == today) "Today" else day.short())
                Text(if (total == null) "—" else ml(total), style = MaterialTheme.typography.titleSmall)
                data.settings.hydrationTargetMl?.let { target ->
                    PixelBar(((total ?: 0).toFloat() / target).coerceIn(0f, 1f), color = Pixel.Tone, label = "Your own target: ${ml(target)}")
                }
            }
            FormGroup {
                FormLabel("Add a drink")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(150, 250, 330, 500).forEach { v -> PixelButton("+$v ml", { add(v) }, color = Pixel.Tone, compact = true) }
                    if (!otherAmount) PixelButton("Other amount", { otherAmount = true }, color = Pixel.InkSoft, compact = true)
                }
                if (otherAmount) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                    PixelField(
                        custom, { custom = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), singleLine = true, label = "Another amount (ml)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    PixelButton("Add", { custom.toIntOrNull()?.let(::add) }, color = Pixel.Tone, enabled = custom.toIntOrNull() != null, compact = true)
                }
                message?.let { Report(model, it, NoticeTone.WARNING) }
            }
            FormDisclosure("Additional details", timing, { timing = !timing; if (!timing) { day = today; time = null } }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelDatePicker(day, today, ModuleAccent.HEAL.accent, { day = it })
                    PixelTimeField(
                        time, { time = it }, ModuleAccent.HEAL.accent, label = "At", noneLabel = if (day == today) "Now" else "Noon",
                        reject = { t -> if (day == today && t > model.store.nowMillis.localTime()) "That time has not happened yet" else null },
                    )
                }
            }
        }
    }
    val drinks = data.hydration.filter { it.day == day }.sortedBy { it.atMillis }
    if (drinks.isEmpty()) EmptyState(if (day == today) "No drinks today" else "No drinks on ${day.short()}", icon = HealSection.WELLNESS.icon)
    else Accordion(if (day == today) "Today's drinks" else "Drinks", summary = "${drinks.size} ${if (drinks.size == 1) "drink" else "drinks"}", startOpen = drinks.size <= 3) {
        HistoryBox {
            drinks.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                DrinkRow(model, d)
            }
        }
    }
    val earlier = (1..7).mapNotNull { back ->
        val earlierDay = LocalDate.fromEpochDays(today.toEpochDays() - back)
        HealRules.hydrationOn(data, earlierDay)?.let { earlierDay to it }
    }
    if (earlier.isNotEmpty()) {
        SectionLabel("Earlier this week")
        HistoryBox {
            earlier.forEachIndexed { i, (d, v) ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                Box(Modifier.padding(vertical = Spacing.S)) { StatRow(d.short(), ml(v)) }
            }
        }
    }
}

@Composable
private fun DrinkRow(model: AppModel, d: HydrationEntry) {
    val heal = model.heal
    var editing by remember(d.id) { mutableStateOf(false) }
    var ml by remember(d.id) { mutableStateOf(d.ml.toString()) }
    var name by remember(d.id) { mutableStateOf(d.drink) }
    var at by remember(d.id) { mutableStateOf(d.atMillis) }
    var error by remember(d.id) { mutableStateOf<String?>(null) }
    HistoryRow(
        trail = {
            PixelButton(if (editing) "Close" else "Edit", { editing = !editing }, color = Pixel.Tone, compact = true)
            PixelButton("Delete", { val before = model.healData; heal.deleteDrink(d.id); model.offerUndo("Deleted: drink") { heal.restore(before) } }, color = Pixel.InkSoft, compact = true)
        },
    ) {
        Text(ml(d.ml) + d.drink.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        Text(clockText(d.atMillis.localTime()), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    if (editing) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(ml, { ml = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = "Amount (ml)", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        PixelField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), label = "What was it?", singleLine = true)
        PixelMomentField(at, { at = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "When")
        error?.let { Report(model, it, NoticeTone.WARNING) }
        PixelButton("Save", {
            val out = heal.updateDrink(d.copy(ml = ml.toIntOrNull() ?: 0, drink = name, atMillis = at))
            if (out.record == null) error = out.refused else { error = null; editing = false; model.say("Saved.") }
        }, Modifier.fillMaxWidth(), Pixel.Tone)
    }
}

@Composable
private fun SleepView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    val nowMillis = model.store.nowMillis
    val usual = remember {
        val now = nowMillis.toLocalDateTime(zone)
        val seven = LocalDateTime(now.date, LocalTime(7, 0)).toInstant(zone).toEpochMilliseconds()
        if (seven <= nowMillis) seven else seven - 86_400_000L
    }
    var woke by remember { mutableStateOf(usual) }
    var asleep by remember { mutableStateOf(usual - 8 * 3_600_000L) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    fun save() {
        val out = heal.logSleep(asleep, woke)
        message = if (out.record == null) (out.refused ?: "That could not be saved.") to NoticeTone.WARNING else { model.effect(Sfx.HEAL); model.say("Saved."); null }
    }
    FormBox {
        FormStack {
            FormGroup {
                PixelMomentField(asleep, { asleep = it }, nowMillis, ModuleAccent.HEAL.accent, label = "Fell asleep", dayPresets = listOf("Today" to 0, "Yesterday" to -1))
                PixelMomentField(woke, { woke = it }, nowMillis, ModuleAccent.HEAL.accent, label = "Woke up")
                Text(
                    if (woke > asleep) "Slept for ${HealRules.duration(((woke - asleep) / 60_000L).toInt())}." else "Waking up has to be after falling asleep.", // copy-ok: result
                    style = MaterialTheme.typography.bodyLarge, color = if (woke > asleep) Pixel.Ink else Pixel.Danger,
                )
            }
            FormSave("Save", ::save, ModuleAccent.HEAL.accent, enabled = woke > asleep)
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
    model.phone?.takeIf { it.canSetAlarm }?.let { phone ->
        var alarm by remember { mutableStateOf(LocalTime(7, 0)) }
        FormBox {
            FormStack {
                PixelTimeField(alarm, { it?.let { t -> alarm = t } }, ModuleAccent.HEAL.accent, label = "Wake-up alarm: ring at")
                FormSave("Set the alarm", { phone.setAlarm(alarm.hour, alarm.minute, "PokéLife") }, ModuleAccent.HEAL.accent)
            }
        }
    }
    val nights = remember(data.sleep) { data.sleep.sortedByDescending { it.endMillis } }
    var shown by remember { mutableStateOf(PAGE) }
    if (nights.isEmpty()) EmptyState("No nights logged", icon = HealSection.ACTIVITY.icon)
    else {
        SectionLabel("History")
        HistoryBox {
            nights.take(shown).forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                NightRow(model, s)
            }
        }
        if (nights.size > shown) ShowOlder { shown += PAGE }
    }
}

@Composable
private fun NightRow(model: AppModel, s: SleepRecord) {
    val heal = model.heal
    var editing by remember(s.id) { mutableStateOf(false) }
    var asleep by remember(s.id) { mutableStateOf(s.startMillis) }
    var woke by remember(s.id) { mutableStateOf(s.endMillis) }
    var note by remember(s.id) { mutableStateOf(s.note) }
    var error by remember(s.id) { mutableStateOf<String?>(null) }
    HistoryRow(
        trail = {
            PixelButton(if (editing) "Close" else "Edit", { editing = !editing }, color = Pixel.Tone, compact = true)
            PixelButton("Delete", { val before = model.healData; heal.deleteSleep(s.id); model.offerUndo("Deleted: sleep") { heal.restore(before) } }, color = Pixel.InkSoft, compact = true)
        },
    ) {
        Text(HealRules.duration(s.minutes), style = MaterialTheme.typography.bodyLarge)
        Text("${clockText(s.startMillis.localTime())} to ${clockText(s.endMillis.localTime())}, woke ${s.day.short()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    if (editing) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelMomentField(asleep, { asleep = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "Fell asleep")
        PixelMomentField(woke, { woke = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "Woke up")
        PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note")
        error?.let { Report(model, it, NoticeTone.WARNING) }
        PixelButton("Save", {
            val out = heal.updateSleep(s.copy(startMillis = asleep, endMillis = woke, note = note))
            if (out.record == null) error = out.refused else { error = null; editing = false; model.say("Saved.") }
        }, Modifier.fillMaxWidth(), Pixel.Tone)
    }
}

private const val PAGE = 30

@Composable
private fun ShowOlder(onClick: () -> Unit) = PixelButton("Show older", onClick, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    var type by remember { mutableStateOf(MeasureType.WEIGHT) }
    var at by remember { mutableStateOf<Long?>(null) }
    var unit by remember { mutableStateOf(data.settings.weightUnit) }
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    fun pick(t: MeasureType) { type = t; unit = if (t == MeasureType.WEIGHT) data.settings.weightUnit else t.units.first() }
    fun save() {
        val v = value.replace(',', '.').toDoubleOrNull()
        val out = if (v == null) null else heal.logMeasurement(type, v, unit, label, atMillis = at ?: model.store.nowMillis)
        message = when {
            out == null -> "Enter a number." to NoticeTone.WARNING
            out.record == null -> (out.refused ?: "That could not be saved.") to NoticeTone.WARNING
            else -> null
        }
        if (out?.record != null) { value = ""; label = ""; at = null; model.say("Saved.") }
    }
    FormBox {
        FormStack {
            FormGroup {
                PixelChoice(MeasureType.entries.toList(), type, { it?.label ?: "" }, { t -> if (t != null) pick(t) }, label = "What did you measure?", key = ModuleAccent.HEAL.accent)
                if (type == MeasureType.OTHER) PixelField(label, { label = it.take(40) }, Modifier.fillMaxWidth(), label = "What is it?", singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                PixelField(
                    value, { value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, Modifier.weight(1f), label = "Value", placeholder = "0", singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true,
                )
                if (type.units.size > 1) type.units.forEach { u -> TabButton(u, u == unit, { unit = u }) }
                else if (type.units.first().isNotEmpty()) Text(type.units.first(), style = MaterialTheme.typography.bodyLarge)
            }
            FormDivider()
            PixelMomentField(at ?: model.store.nowMillis, { at = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "When")
            FormSave("Save", ::save, ModuleAccent.HEAL.accent, enabled = value.isNotBlank())
            message?.let { (text, tone) -> Report(model, text, tone) }
        }
    }
    if (data.measurements.isEmpty()) EmptyState("Nothing here", icon = HealSection.WELLNESS.icon)
    else {
        var shown by remember { mutableStateOf(10) }
        MeasureType.entries.forEach { t ->
            val own = data.measurements.filter { it.type == t }
            if (own.isEmpty()) return@forEach
            SectionLabel(t.label)
            own.map { it.unit }.distinct().forEach { u ->
                HealRules.trendText(data, t, u)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            }
            HistoryBox {
                own.sortedByDescending { it.atMillis }.take(shown).forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    MeasurementRow(model, m)
                }
            }
        }
        if (MeasureType.entries.any { t -> data.measurements.count { it.type == t } > shown }) ShowOlder { shown += PAGE }
    }
}

/**
 * Heal's readings as modules (owner, 21 November 2026): one tile per kind of reading (steps, heart rate, blood oxygen, skin temperature, sleep length and score, stress) with its
 * newest day, and tapping a tile opens that reading's own list, one line per day. History only (HEAL.md section 22B): values as read, never called good, bad, high or low, and
 * never edited; a wrong day is deleted. Heart rate, blood oxygen and skin temperature are a range for the day.
 */
@Composable
private fun ReadingsView(model: AppModel) {
    val data = model.healData
    val groups = ReadingGroups.withData(data)
    var open by remember { mutableStateOf<ReadingGroup?>(null) }
    val group = open?.takeIf { it in groups }
    if (group != null) { BackHandler(enabled = true, onBack = { open = null }); ReadingList(model, group, onBack = { open = null }); return }
    if (groups.isEmpty()) { EmptyState("Nothing here", icon = HealSection.WELLNESS.icon); return }
    groups.forEach { g ->
        val latest = ReadingGroups.latest(data, g)
        HistoryBox {
            HistoryRow(onClick = { open = g }, onClickLabel = g.label, trail = { Text(">", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    ItemIcon(g.icon, size = 32.dp, description = null)
                    Column(Modifier.weight(1f)) {
                        Text(g.label, style = MaterialTheme.typography.bodyLarge)
                        if (latest != null) Text(latest.line + " \u00B7 " + latest.day.short(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
            }
        }
    }
}

/** One reading's own list: a line per day, newest first, with Delete for a day. */
@Composable
private fun ReadingList(model: AppModel, group: ReadingGroup, onBack: () -> Unit) {
    val heal = model.heal
    val days = ReadingGroups.daily(model.healData, group)
    var shown by remember { mutableStateOf(PAGE) }
    PixelTitle(group.label)
    HistoryBox {
        days.take(shown).forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = Pixel.Border)
            HistoryRow(trail = {
                PixelButton("Delete", { val before = model.healData; heal.deleteReadingDay(group, d.day); model.offerUndo("Deleted: ${group.label.lowercase()}") { heal.restore(before) } }, color = Pixel.InkSoft, compact = true)
            }) {
                Text(d.line, style = MaterialTheme.typography.bodyLarge)
                Text(d.day.short(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    if (days.size > shown) ShowOlder { shown += PAGE }
    PixelButton("Back to readings", onBack, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

@Composable
private fun MeasurementRow(model: AppModel, m: Measurement) {
    val heal = model.heal
    var editing by remember(m.id) { mutableStateOf(false) }
    var value by remember(m.id) { mutableStateOf(HealRules.format(m.value)) }
    var unit by remember(m.id) { mutableStateOf(m.unit) }
    var at by remember(m.id) { mutableStateOf(m.atMillis) }
    var error by remember(m.id) { mutableStateOf<String?>(null) }
    HistoryRow(
        trail = {
            PixelButton(if (editing) "Close" else "Edit", { editing = !editing }, color = Pixel.Tone, compact = true)
            PixelButton("Delete", { val before = model.healData; heal.deleteMeasurement(m.id); model.offerUndo("Deleted: measurement") { heal.restore(before) } }, color = Pixel.InkSoft, compact = true)
        },
    ) {
        Text((m.label.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()) + HealUnits.show(m, m.unit), style = MaterialTheme.typography.bodyLarge)
        Text(m.day.short() + " " + clockText(m.atMillis.localTime()), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    if (editing) Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(value, { value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, Modifier.weight(1f), label = "Value", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (m.type.units.size > 1) m.type.units.forEach { u -> TabButton(u, u == unit, { unit = u }) }
        }
        PixelMomentField(at, { at = it }, model.store.nowMillis, ModuleAccent.HEAL.accent, label = "When")
        error?.let { Report(model, it, NoticeTone.WARNING) }
        PixelButton("Save", {
            val v = value.replace(',', '.').toDoubleOrNull()
            val out = if (v == null) null else heal.updateMeasurement(m.copy(value = v, unit = unit, atMillis = at))
            if (out?.record != null) { error = null; editing = false; model.say("Saved.") } else error = out?.refused ?: "Enter a number."
        }, Modifier.fillMaxWidth(), Pixel.Tone)
    }
}

private val GoalKind.title: String get() = label.substringBefore(" (")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsHealView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(GoalKind.WEEKLY_ACTIVE_MINUTES) }
    var target by remember { mutableStateOf("") }
    val goals = data.goals.filter { it.status != GoalStatus.ARCHIVED }
    if (goals.isEmpty()) {
        if (!adding) EmptyState("No goals", icon = HealSection.GOALS.icon, action = "New goal" to { adding = true })
    } else {
        HistoryBox {
            goals.forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val p = HealRules.progress(g, data, model.store.today, model.data.settings.weekStart)
                val figure = if (g.status == GoalStatus.PAUSED) "Paused" else "${p.done ?: "—"} / ${g.target} ${g.kind.unit}"
                Column(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Show options") { open = if (open == g.id) null else g.id }.padding(vertical = Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text(g.kind.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(figure, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (g.status != GoalStatus.PAUSED) PixelBar(p.fraction, color = if (p.met) Pixel.Green else Pixel.Tone, label = figure)
                }
                if (open == g.id) Row(Modifier.padding(bottom = Spacing.S), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton(if (g.status == GoalStatus.PAUSED) "Resume" else "Pause", { heal.setGoalStatus(g.id, if (g.status == GoalStatus.PAUSED) GoalStatus.ACTIVE else GoalStatus.PAUSED) }, Modifier.weight(1f), Pixel.Tone, compact = true)
                    PixelButton("Delete", { val before = model.healData; heal.deleteGoal(g.id); model.offerUndo("Deleted: goal") { heal.restore(before) }; open = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
        }
        if (!adding) PixelButton("New goal", { adding = true }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
    }
    if (adding) FormBox {
        FormStack {
            FormGroup {
                PixelDropdown(GoalKind.entries.toList(), kind, { it?.title ?: "" }, { k -> if (k != null) kind = k }, Modifier.fillMaxWidth(), label = "New goal")
                PixelField(target, { target = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = "Target (${kind.unit})", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Cancel", { adding = false; target = "" }, Modifier.weight(1f), Pixel.InkSoft)
                FormSave("Add goal", { target.toIntOrNull()?.let { heal.addGoal(kind, it); target = ""; adding = false } }, ModuleAccent.HEAL.accent, Modifier.weight(1f), enabled = HealRules.goalTargetOk(kind, target.toIntOrNull() ?: 0))
            }
        }
    }
}


@Composable
private fun MedicationView(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    val today = model.store.today
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    val medications = data.medications.sortedWith(compareBy({ it.status != MedicationStatus.ACTIVE }, { it.name.lowercase() }))
    val edited = editing?.let { id -> data.medications.firstOrNull { it.id == id } }
    if (medications.isEmpty()) {
        if (!adding) EmptyState("No medications yet", icon = HealSection.RECORDS.icon, action = "Add one" to { adding = true })
    } else {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                medications.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    MedicationRow(model, m, today, open == m.id, { open = if (open == m.id) null else m.id }, { editing = m.id }, { message = it })
                }
            }
        }
        if (!adding && editing == null) PixelButton("Add a medication", { adding = true }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
        MedicationLog(model)
    }
    if (adding) MedicationForm(model, null, { adding = false }, { message = it })
    if (edited != null) MedicationForm(model, edited, { editing = null }, { message = it })
    message?.let { (text, tone) -> Report(model, text, tone) }
}

private fun medicationFigure(m: Medication): String = when {
    m.status == MedicationStatus.STOPPED -> "Stopped"
    m.status == MedicationStatus.PAUSED -> "Paused"
    m.supplyLeft != null -> "${m.supplyLeft} left"
    else -> ""
}

@Composable
private fun MedicationRow(
    model: AppModel,
    m: Medication,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMessage: (Pair<String, NoticeTone>) -> Unit,
) {
    val heal = model.heal
    val data = model.healData
    val times = HealRules.medicationTimesOn(m, today)
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Show options", onClick = onToggle).padding(vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (m.times.isEmpty()) "As needed" else m.times.joinToString(", ") { clockText(it) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(medicationFigure(m), style = MaterialTheme.typography.bodyMedium)
        }
        if (times.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                times.forEach { time ->
                    val dose = data.doses.firstOrNull { it.medicationId == m.id && it.day == today && it.time == time }
                    if (dose != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Text("${clockText(time)} ${dose.status.label.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                            PixelButton("Delete", {
                                val before = model.healData
                                heal.deleteDose(dose.id)
                                model.offerUndo("Deleted: dose") { heal.restore(before) }
                            }, color = Pixel.InkSoft, compact = true)
                        }
                    } else {
                        PixelButton("${clockText(time)} taken", {
                            val out = heal.logDose(m.id, today, time, DoseStatus.TAKEN)
                            if (out.record == null) onMessage((out.refused ?: "That could not be saved.") to NoticeTone.WARNING) else model.haptic()
                        }, color = ModuleAccent.HEAL.accent, compact = true)
                        PixelButton("${clockText(time)} skip", {
                            val out = heal.logDose(m.id, today, time, DoseStatus.SKIPPED)
                            if (out.record == null) onMessage((out.refused ?: "That could not be saved.") to NoticeTone.WARNING)
                        }, color = Pixel.InkSoft, compact = true)
                    }
                }
            }
        } else if (m.status == MedicationStatus.ACTIVE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                val nowTime = model.store.nowMillis.localTime()
                PixelButton("Taken now", {
                    val out = heal.logDose(m.id, today, nowTime, DoseStatus.TAKEN)
                    if (out.record == null) onMessage((out.refused ?: "That could not be saved.") to NoticeTone.WARNING) else model.haptic()
                }, color = ModuleAccent.HEAL.accent, compact = true)
                PixelButton("Skip now", {
                    val out = heal.logDose(m.id, today, nowTime, DoseStatus.SKIPPED)
                    if (out.record == null) onMessage((out.refused ?: "That could not be saved.") to NoticeTone.WARNING)
                }, color = Pixel.InkSoft, compact = true)
            }
        }
        if (expanded) MedicationOptions(model, m, onEdit, onMessage)
    }
}

@Composable
private fun MedicationOptions(model: AppModel, m: Medication, onEdit: () -> Unit, onMessage: (Pair<String, NoticeTone>) -> Unit) {
    val heal = model.heal
    var refillText by remember(m.id, m.supplyLeft) { mutableStateOf(m.supplyLeft?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (m.instructions.isNotBlank()) Text(m.instructions, style = MaterialTheme.typography.bodyMedium)
        if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Started ${m.startDay}" + (m.endDay?.let { " · ends $it" } ?: "") + (m.refillAt?.let { " · warn at $it left" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (m.times.isNotEmpty()) ToggleRow("Remind me", m.remind) { heal.setMedicationRemind(m.id, it) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                refillText, { refillText = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = "Doses left", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            PixelButton("Save count", {
                val count = refillText.toIntOrNull()
                if (count == null) onMessage("Enter a count." to NoticeTone.WARNING) else heal.refillMedication(m.id, count)
            }, color = Pixel.Tone, compact = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            PixelButton("Edit", onEdit, color = Pixel.Tone, compact = true)
            when (m.status) {
                MedicationStatus.ACTIVE -> PixelButton("Pause", { heal.setMedicationStatus(m.id, MedicationStatus.PAUSED) }, color = Pixel.InkSoft, compact = true)
                MedicationStatus.PAUSED, MedicationStatus.STOPPED -> PixelButton("Resume", { heal.setMedicationStatus(m.id, MedicationStatus.ACTIVE) }, color = Pixel.Tone, compact = true)
            }
            if (m.status != MedicationStatus.STOPPED) {
                PixelButton("Stop", { heal.setMedicationStatus(m.id, MedicationStatus.STOPPED) }, color = Pixel.InkSoft, compact = true)
            }
            PixelButton("Delete", {
                val before = model.healData
                heal.deleteMedication(m.id)
                model.offerUndo("Deleted: medication") { heal.restore(before) }
            }, color = Pixel.Danger, compact = true)
        }
    }
}

@Composable
private fun MedicationLog(model: AppModel) {
    val heal = model.heal
    val data = model.healData
    val doses = data.doses.sortedByDescending { it.atMillis }.take(15)
    if (doses.isEmpty()) return
    Accordion("Dose log") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            doses.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(vertical = Spacing.XS)) {
                    Column(Modifier.weight(1f)) {
                        Text(data.medications.firstOrNull { it.id == d.medicationId }?.name ?: "Removed medication", style = MaterialTheme.typography.bodyMedium)
                        Text("${d.day} ${clockText(d.time)} · ${d.status.label.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    PixelButton("Delete", {
                        val before = model.healData
                        heal.deleteDose(d.id)
                        model.offerUndo("Deleted: dose") { heal.restore(before) }
                    }, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

@Composable
private fun MedicationForm(model: AppModel, editing: Medication?, onDone: () -> Unit, onMessage: (Pair<String, NoticeTone>) -> Unit) {
    val heal = model.heal
    val today = model.store.today
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var strength by remember(editing?.id) { mutableStateOf(editing?.strength.orEmpty()) }
    var instructions by remember(editing?.id) { mutableStateOf(editing?.instructions.orEmpty()) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var times by remember(editing?.id) { mutableStateOf(editing?.times.orEmpty()) }
    var startDay by remember(editing?.id) { mutableStateOf(editing?.startDay ?: today) }
    var endDay by remember(editing?.id) { mutableStateOf(editing?.endDay) }
    var supply by remember(editing?.id) { mutableStateOf(editing?.supplyLeft?.toString().orEmpty()) }
    var refillAt by remember(editing?.id) { mutableStateOf(editing?.refillAt?.toString().orEmpty()) }
    var remind by remember(editing?.id) { mutableStateOf(editing?.remind ?: true) }
    var more by remember(editing?.id) { mutableStateOf(editing != null) }
    fun save() {
        val supplyCount = supply.toIntOrNull()
        val refillCount = refillAt.toIntOrNull()
        val out = if (editing == null) {
            heal.addMedication(name, strength, instructions, times, startDay, endDay, note, remind, supplyCount, refillCount)
        } else {
            heal.updateMedication(
                editing.copy(
                    name = name, strength = strength, instructions = instructions, note = note, times = times,
                    startDay = startDay, endDay = endDay, remind = remind, supplyLeft = supplyCount, refillAt = refillCount,
                ),
            )
        }
        if (out.record == null) onMessage((out.refused ?: "That could not be saved.") to NoticeTone.WARNING)
        else { model.say("Saved."); onDone() }
    }
    FormBox {
        FormStack {
            FormGroup {
                PixelField(name, { name = it.take(HealRules.MEDICATION_NAME_MAX) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true)
                times.forEach { time ->
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelTimeField(time, { nt -> nt?.let { times = (times - time + it).distinct().sorted() } }, ModuleAccent.HEAL.accent, Modifier.weight(1f), label = "Time")
                        PixelButton("Remove", { times = times - time }, color = Pixel.InkSoft, compact = true)
                    }
                }
                if (times.size < HealRules.MEDICATION_TIMES_MAX) {
                    var next by remember(times) { mutableStateOf(if (times.isEmpty()) LocalTime(9, 0) else shift(times.last(), 120)) }
                    PixelTimeField(next, { it?.let { t -> next = t } }, ModuleAccent.HEAL.accent, label = "New time")
                    PixelButton("Add a time at ${clockText(next)}", { times = (times + next).distinct().sorted() }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                }
                if (times.isNotEmpty()) ToggleRow("Remind me", remind) { remind = it }
            }
            FormDisclosure("Additional details", more, { more = !more }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(strength, { strength = it.take(HealRules.MEDICATION_STRENGTH_MAX) }, Modifier.fillMaxWidth(), label = "Strength", singleLine = true)
                    PixelField(instructions, { instructions = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Instructions from your clinician")
                    PixelField(note, { note = it.take(HealRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Notes")
                    FormLabel("Started")
                    PixelDateField(startDay, today, ModuleAccent.HEAL.accent, { it?.let { d -> startDay = d } })
                    FormLabel("Ends")
                    PixelDateField(endDay, today, ModuleAccent.HEAL.accent, { d -> endDay = d }, noneLabel = "No end date")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelField(
                            supply, { supply = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = "Doses left", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        PixelField(
                            refillAt, { refillAt = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = "Warn at", singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft)
                FormSave(if (editing == null) "Add" else "Save", { save() }, ModuleAccent.HEAL.accent, Modifier.weight(1f), enabled = name.isNotBlank())
            }
        }
    }
}

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
            ToggleRow("Field Research", settings.fieldResearch) { heal.setFieldResearch(it) }
            ToggleRow("Show my weight on Today", settings.showMeasurementsOnToday) { heal.setShowMeasurementsOnToday(it) }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Water")
            PixelField(
                target, { target = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = "Daily target (ml)", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save target", { heal.setHydrationTarget(target.toIntOrNull()) }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("No target", { target = ""; heal.setHydrationTarget(null) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            PixelTitle("Reminders")
            settings.hydrationReminders.forEach { t ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelTimeField(t, { nt -> nt?.let { heal.setHydrationReminders(settings.hydrationReminders - t + it) } }, ModuleAccent.HEAL.accent, Modifier.weight(1f), label = "Reminder")
                    PixelButton("Remove", { heal.setHydrationReminders(settings.hydrationReminders - t) }, color = Pixel.InkSoft, compact = true)
                }
            }
            if (settings.hydrationReminders.size < HealSettings.MAX_REMINDERS) {
                var next by remember(settings.hydrationReminders) { mutableStateOf(if (settings.hydrationReminders.isEmpty()) LocalTime(10, 0) else shift(settings.hydrationReminders.last(), 120)) }
                PixelTimeField(next, { it?.let { t -> next = t } }, ModuleAccent.HEAL.accent, label = "New reminder")
                PixelButton("Add a reminder at ${clockText(next)}", { heal.setHydrationReminders(settings.hydrationReminders + next) }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelChoice(listOf("kg", "lb"), settings.weightUnit, { it ?: "" }, { u -> if (u != null) heal.setWeightUnit(u) }, label = "Weight unit", key = ModuleAccent.HEAL.accent)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Your data")
            val files = model.files
            if (files != null) {
                PixelButton("Export everything", { note = files.save("pokelife-heal.json", heal.export()) }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                Accordion("Export") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        HealKinds.entries.forEach { k -> PixelButton("Export ${k.label.lowercase()}", { note = files.save("pokelife-heal-${k.name.lowercase()}.json", heal.export(setOf(k))) }, color = Pixel.InkSoft, compact = true) }
                    }
                }
            }
            note?.let { Report(model, it) }
            Accordion("Erase data", startOpen = asking != null || askingAll) {
              Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            HealKinds.entries.forEach { k ->
                if (asking == k) {
                    Text("Erase all ${k.label.lowercase()} records? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("Erase", { heal.erase(k); asking = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        PixelButton("Keep them", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                } else PixelButton("Erase ${k.label.lowercase()}", { asking = k; askingAll = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (askingAll) {
                Text("Erase every Heal record? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Erase all", { heal.eraseAll(); askingAll = false }, Modifier.weight(1f), Pixel.Danger, compact = true)
                    PixelButton("Keep them", { askingAll = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            } else PixelButton("Erase all Heal records", { askingAll = true; asking = null }, Modifier.fillMaxWidth(), Pixel.Danger, compact = true)
              }
            }
        }
    }
}
