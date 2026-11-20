package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.HoldToConfirm
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.PixelSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import app.pokeplan.app.ThemeMode
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.heading
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.Spacing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.DeviceCalendar
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.display
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label

@Composable
internal fun SettingsView(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    var name by remember { mutableStateOf(model.data.trainerName) }
    val saveName by rememberUpdatedState { if (name.isNotBlank() && name.trim() != model.data.trainerName) store.rename(name) }
    DisposableEffect(Unit) { onDispose { saveName() } }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Trainer name", "user")
            // Saved when the keyboard says Done and when the page is left; no separate key.
            PixelField(
                name, { name = it.take(24) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveName() }),
            )
        }
    }
    // Settings come in small groups, one card each, so a setting is found by its group rather than by reading a long list.
    SettingsGroup("Sound and touch", "volume-2") {
        VolumeRow("Sound effects", settings.effectsVolume) { v -> store.changeSettings { s -> s.copy(effectsVolume = v, sound = true) } }
        VolumeRow("Pokémon cries", if (settings.criesVolume < 0) settings.effectsVolume else settings.criesVolume) { v -> store.changeSettings { s -> s.copy(criesVolume = v, sound = true) } }
        ToggleRow("Vibration", settings.haptics) { store.changeSettings { s -> s.copy(haptics = it) } }
    }
    SettingsGroup("Look", "sun") {
        FieldLabel("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ThemeMode.entries.forEach { mode -> TabButton(mode.label, settings.theme == mode, { store.changeSettings { s -> s.copy(theme = mode) } }) }
        }
        var choosing by remember { mutableStateOf(false) }
        val background = app.pokeplan.ui.AppBackgrounds.firstOrNull { it.first == settings.appBackground }?.second ?: "Day"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Background: $background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TabButton(if (choosing) "Done" else "Change", choosing, { choosing = !choosing })
        }
        if (choosing) app.pokeplan.ui.BackgroundChoices(settings.appBackground, store.now.hour, store.now.minute, Pixel.dark) { name -> store.changeSettings { s -> s.copy(appBackground = name) } }
        ToggleRow("Reduce motion", settings.reduceMotion) { store.changeSettings { s -> s.copy(reduceMotion = it) } }
    }
    SettingsGroup("Date and time", "calendar") {
        ToggleRow("Week starts on Monday", settings.weekStartsOnMonday) { store.changeSettings { s -> s.copy(weekStartsOnMonday = it) } }
        ToggleRow("12-hour clock (AM and PM)", settings.clock12Hour) { store.changeSettings { s -> s.copy(clock12Hour = it) } }
        FieldLabel("Date format")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            app.pokeplan.ui.launcher.DateStyle.entries.forEach { style ->
                TabButton(style.label, settings.dateFormat == style.name, { store.changeSettings { s -> s.copy(dateFormat = style.name) } })
            }
        }
    }
    SettingsGroup("Notifications", "bell") {
        model.phone?.let { phone ->
            // Read again when the player comes back from the phone's permission screen.
            var exact by remember { mutableStateOf(phone.exactAlarms) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { exact = phone.exactAlarms }
            if (!exact) {
                Text("Android is not letting PokéLife set exact alarms, so reminders and timers can arrive minutes late.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red) // copy-ok: warning or legal
                PixelButton("Allow exact alarms", { phone.allowExactAlarms() }, Modifier.fillMaxWidth(), Pixel.Tone)
            }
        }
        ToggleRow(
            "Routine reminders", model.careData.settings.reminders,
        ) { on -> model.care.changeSettings { it.copy(reminders = on) } }
        ToggleRow("World job alert", settings.worldJobAlerts) { store.changeSettings { s -> s.copy(worldJobAlerts = it) } }
        ToggleRow("Streak reminder", settings.streakReminder) { store.changeSettings { s -> s.copy(streakReminder = it) } }
        if (settings.streakReminder) {
            Stepper("Streak reminder at", hourText(settings.streakReminderHour), 1) { by ->
                store.changeSettings { s -> s.copy(streakReminderHour = (s.streakReminderHour + by + 24) % 24) }
            }
        }
        ToggleRow("Weekly recap", settings.weeklyRecap) { store.changeSettings { s -> s.copy(weeklyRecap = it) } }
    }
    PrivacyCard(model)
    DataCard(model)
    BackupsCard(model)
    AboutCard()
}

/** The screen lock in front of the private modules (Care's journal, Heal, Home, Link); Mint's switch is in Mint. Only where the phone has a lock to ask. */
@Composable
private fun PrivacyCard(model: AppModel) {
    val gate = model.gate ?: return
    if (!gate.available() && model.data.settings.lockedModules.isEmpty()) return
    SettingsGroup("Private modules", "storage-key") {
        gateableModules.forEach { m ->
            val on = m.name in model.data.settings.lockedModules
            ToggleRow("Lock ${m.label}", on) { want ->
                fun set(value: Boolean) = model.store.changeSettings { s -> s.copy(lockedModules = if (value) s.lockedModules + m.name else s.lockedModules - m.name) }
                if (!want) set(false)
                else gate.authenticate { ok -> if (ok) { set(true); model.moduleUnlockedAt[m.name] = model.store.nowMillis } }
            }
        }
    }
}

/** Which of the phone's calendars show on Planner → Calendar. Only where the platform can read them (Android). */
@Composable
internal fun CalendarsCard(model: AppModel) {
    val source = model.calendars ?: return
    var permitted by remember { mutableStateOf(source.permitted()) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>?>(null) }
    LaunchedEffect(permitted) { if (permitted) calendars = withContext(Dispatchers.Default) { source.calendars() } }
    val shown = model.data.shownCalendars

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Calendars", "calendar")
            // Help: phone calendars show on Planner, Calendar; they are read-only and never pay.
            val list = calendars
            when {
                !permitted -> PixelButton("Choose calendars", { source.requestPermission { permitted = it } }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                list == null -> Unit
                list.isEmpty() -> Text("No calendars on this phone yet.", style = MaterialTheme.typography.bodyMedium)
                else -> list.forEach { c ->
                    val label = if (c.account.isBlank() || c.account == c.name) c.name else "${c.name}, ${c.account}"
                    ToggleRow(label, c.id in shown) { on -> model.store.setShownCalendars(if (on) shown + c.id else shown - c.id) }
                }
            }
        }
    }
}

/**
 * Backups (Android): the app's own daily ones, a copy saved anywhere as one file, and restoring either. A chosen file is read and checked whole
 * before anything is asked; restoring replaces what is on the phone but keeps it aside, and asks first.
 */
@Composable
private fun BackupsCard(model: AppModel) {
    val backups = model.backups ?: return
    var note by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf(backups.automatic()) }
    var asking by remember { mutableStateOf<Long?>(null) }
    var chosen by remember { mutableStateOf<app.pokeplan.ui.StagedBackup?>(null) }
    var portable by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val hide = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    fun restored(text: String) { model.store.reload(); model.care.reload(); model.heal.reload(); model.mint.reload(); model.base.reload(); model.link.reload(); entries = backups.automatic(); note = text }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Backups", "download")
            if (backups.lowStorage()) Text("This phone is almost out of storage, so a backup was skipped and some changes may not be saved. Free some space and it carries on by itself.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
            // Help: a copy is made every day, sealed to this phone (the last seven days, then one a week for four weeks).
            Text(entries.firstOrNull()?.let { "Last backup: ${backupLabel(it.millis)}" } ?: "No backup yet", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Back up now", { note = backups.backupNow(); entries = backups.automatic() }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("Save a copy", { backups.saveCopy(null) { note = it } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            PixelButton(if (portable) "Not now" else "Export a backup", { portable = !portable; pass = ""; again = ""; shown = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (portable) {
                // Help: a passphrase seals Mint's and Heal's records in the copy so any phone can open it; it is stored nowhere, and if it is forgotten they cannot be opened.
                PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase (at least ${backups.minPassphrase} characters)", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                PixelField(again, { again = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase again", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    error = if (again.isNotEmpty() && again != pass) "The two do not match." else null)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Save the copy", {
                        busy = true
                        backups.saveCopy(null, pass) { busy = false; note = it; if (it == "Saved.") { portable = false; pass = ""; again = "" } }
                    }, Modifier.weight(1f), Pixel.Tone, enabled = !busy && pass.length >= backups.minPassphrase && pass == again, compact = true)
                    PixelButton(if (shown) "Hide" else "Show", { shown = !shown }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            entries.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(backupLabel(e.millis), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    PixelButton("Save", { backups.saveCopy(e.millis) { note = it } }, color = Pixel.InkSoft, compact = true)
                    PixelButton("Restore", { asking = e.millis; chosen = null }, color = Pixel.Warning, compact = true)
                }
            }
            asking?.let { millis ->
                Text("Put the backup from ${backupLabel(millis)} back? What is on this phone now is kept aside, so this can be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Restore", { restored(backups.restoreAutomatic(millis)); asking = null }, Modifier.weight(1f), Pixel.Warning, compact = true)
                    PixelButton("Keep what I have", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            PixelButton("Restore from a file", { asking = null; chosen = null; pass = ""; backups.chooseFile { staged, why -> chosen = staged; note = why } }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            chosen?.let { c ->
                Text("This file is a backup from ${backupLabel(c.createdAtMillis)} (${c.documents.joinToString(", ")}). Replace what is on this phone with it? What is here now is kept aside.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
                if (c.needsPassphrase) PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Backup passphrase", singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Restore", { busy = true; backups.applyChosen(pass.takeIf { c.needsPassphrase }) { busy = false; restored(it); if (it.startsWith("Restored")) { chosen = null; pass = "" } } }, Modifier.weight(1f), Pixel.Warning, enabled = !busy && (!c.needsPassphrase || pass.isNotEmpty()), compact = true)
                    PixelButton("Cancel", { chosen = null; pass = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            if (backups.hasOffPhone) OffPhoneSection(backups, nowMillis = model.store.nowMillis,
                onNote = { note = it; entries = backups.automatic() }, onStaged = { staged, why -> chosen = staged; note = why; asking = null; pass = "" })
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}

/**
 * Backup off the phone (REP-034): set up once with a folder and a passphrase, then a copy goes there by itself every day. Needs the passphrase to be typed twice, because
 * a copy that cannot be opened is worse than none. A quiet line says so when the last copy is more than three days old.
 */
@Composable
internal fun OffPhoneSection(backups: app.pokeplan.ui.BackupAccess, nowMillis: Long, onNote: (String) -> Unit, onStaged: (app.pokeplan.ui.StagedBackup?, String?) -> Unit, startSetting: Boolean = false) {
    var state by remember { mutableStateOf(backups.offPhone()) }
    var setting by remember { mutableStateOf(startSetting) }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    val hide = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    fun done(text: String) { busy = false; state = backups.offPhone(); onNote(text) }
    val current = state
    if (current == null || setting) {
        PixelButton(if (setting) "Not now" else "Back up off this phone", { setting = !setting; pass = ""; again = ""; shown = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (setting) {
            // Help: a copy goes to a folder you choose, such as one a cloud app syncs. Mint and Heal in it are sealed under this passphrase, which opens them on any phone and is kept nowhere else; if it is forgotten they cannot be opened.
            PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase (at least ${backups.minPassphrase} characters)", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            PixelField(again, { again = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase again", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                error = if (again.isNotEmpty() && again != pass) "The two do not match." else null)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Choose the folder", { busy = true; backups.setUpOffPhone(pass) { setting = false; pass = ""; again = ""; done(it) } }, Modifier.weight(1f), Pixel.Tone,
                    enabled = !busy && pass.length >= backups.minPassphrase && pass == again, compact = true)
                PixelButton(if (shown) "Hide" else "Show", { shown = !shown }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    } else {
        val last = current.lastCopyMillis
        Text(
            "Copies go to ${current.folderName}" + (last?.let { ", last one ${backupLabel(it)}" } ?: ", none yet"),
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
        val stale = last == null || nowMillis - last > 3 * 24 * 60 * 60 * 1000L
        current.problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) } // copy-ok: warning or legal
            ?: if (stale && last != null) Text("No copy for ${((nowMillis - last) / (24 * 60 * 60 * 1000L))} days.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning) else Unit
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Copy now", { busy = true; backups.backUpOffPhoneNow(::done) }, Modifier.weight(1f), Pixel.Tone, enabled = !busy, compact = true)
            PixelButton("Restore copy", { backups.chooseOffPhoneCopy(onStaged) }, Modifier.weight(1f), Pixel.Warning, enabled = !busy, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Change folder", { setting = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton(if (stopping) "Keep copying" else "Stop", { stopping = !stopping }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        if (stopping) {
            Text("Stop copying to ${current.folderName}? The copies already there stay.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
            PixelButton("Stop copying", { stopping = false; onNote(backups.stopOffPhone()); state = backups.offPhone() }, Modifier.fillMaxWidth(), Pixel.Warning, compact = true)
        }
    }
}

private fun backupLabel(millis: Long): String {
    val t = kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** The player's data: a copy to keep, starting over on this device. Each destructive step asks twice. */
@Composable
private fun DataCard(model: AppModel) {
    val store = model.store
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Your data", "download")
            model.files?.let { files ->
                PixelButton("Export my data", { note = files.save("pokeplan-export-${store.today}.json", store.exportJson()) }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                PixelButton("Export Care data", { note = files.save("pokeplan-care-${store.today}.json", model.care.exportJson()) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            when (asking) {
                "care" -> {
                    Text(
                        "Erase Care's data on this device? Every routine, calm break, diary card, skill practised and journal entry is removed. What they earned stays. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelIconButton("trash", "Erase", { model.care.erase(); note = "Care's data was erased."; asking = null }, Pixel.Danger)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                "device" -> {
                    Text("Erase this device? Everything here is removed and the app starts over: tasks, Pokémon, settings, and every Care, Heal, Mint and Base record, with the daily backups. A copy you saved as a file is not touched. This cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        HoldToConfirm("Hold to erase", { store.eraseDevice(); model.care.erase(); model.heal.eraseAll(); model.mint.eraseAll(); model.base.eraseAll(); note = model.backups?.eraseLeftovers()?.ifBlank { null }; asking = null }, Modifier.weight(1f))
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Erase this device", { asking = "device"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Erase Care data", { asking = "care"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        }
    }
}

/** A labelled volume slider, 0 to 100 in steps of 10. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$value%", style = MaterialTheme.typography.bodyMedium)
        }
        PixelSlider(
            value = value / 100f, onChange = { onChange((it * 10).roundToInt() * 10) }, steps = 9,
            modifier = Modifier.semantics { contentDescription = "$label volume $value percent" },
        )
    }
}

/** The app's version, so a bug report or a friend's install can say exactly which build it is. */
@Composable
private fun AboutCard() {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            GlyphTitle("About", "info-box")
            Text("PokéLife ${AppVersion.display}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "A private fan project. Pokémon names, sprites and sounds belong to Nintendo, Game Freak and The Pokémon Company.", // copy-ok: warning or legal
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            var credits by remember { mutableStateOf(false) }
            PixelButton(if (credits) "Hide credits" else "Credits", { credits = !credits }, Modifier.fillMaxWidth())
            if (credits) CreditsList()
        }
    }
}

/** One group of settings in its own card, under a small heading. */
@Composable
private fun SettingsGroup(title: String, glyph: String? = null, content: @Composable () -> Unit) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (glyph != null) app.pokeplan.ui.Glyph(glyph, Pixel.Ink, description = null)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

/** A value nudged down and up by [step] ("-5", "+5"), with its label at the start of the row. */
@Composable
private fun Stepper(label: String, value: String, step: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("-$step", { change(-step) }, color = Pixel.InkSoft, compact = true)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            PixelButton("+$step", { change(step) }, color = Pixel.InkSoft, compact = true)
        }
    }
}

/** An hour as the settings show it, "07:00". */
private fun hourText(hour: Int) = "${hour.toString().padStart(2, '0')}:00"

/**
 * Plan's own settings (Plan, More, Settings; PLAN.md section 13): its reminders (task reminders with the quiet hours they respect, repeat until
 * answered, the focus alerts) and which of the phone's calendars appear. The week start is a General setting; the usual focus length and
 * Deep Focus are what the last session was started with.
 */
@Composable
internal fun PlanSettings(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    SettingsGroup("Reminders", "bell") {
        ToggleRow("Task reminders", settings.reminders) { store.changeSettings { s -> s.copy(reminders = it) } }
        if (settings.reminders) {
            PixelTimeField(
                settings.quietHours.start, { t -> t?.let { store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(start = it)) } } },
                Pixel.Tone, label = "Quiet from",
            )
            PixelTimeField(
                settings.quietHours.end, { t -> t?.let { store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(end = it)) } } },
                Pixel.Tone, label = "Quiet until",
            )
            ToggleRow("Repeat until answered", settings.repeatReminders) { store.changeSettings { s -> s.copy(repeatReminders = it) } }
        }
        ToggleRow("Focus halfway alert", settings.focusHalfway) { store.changeSettings { s -> s.copy(focusHalfway = it) } }
        ToggleRow("Focus end alert", settings.focusEnd) { store.changeSettings { s -> s.copy(focusEnd = it) } }
    }
    SettingsGroup("Rewards", "zap") {
        // The focus a new task starts with; each task can change it (More options, or its editor). Exp is the ordinary payout.
        PixelChoice(
            app.pokeplan.core.reward.RewardFocus.entries.toList(), settings.defaultReward, { it?.label ?: "" },
            { r -> if (r != null) store.changeSettings { s -> s.copy(defaultReward = r) } }, label = "New tasks pay most in",
        )
    }
    CalendarsCard(model)
}

/** World's own settings (Modules, World, Settings). */
@Composable
internal fun WorldSettings(model: AppModel) {
    val settings = model.data.settings
    SettingsGroup("Animation", "zap") {
        ToggleRow(
            "Scrolling floors", settings.worldScroll,
        ) { model.store.changeSettings { s -> s.copy(worldScroll = it) } }
    }
}

/** A card's title with a small Pixelarticons glyph in front of it. */
@Composable
private fun GlyphTitle(text: String, glyph: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        app.pokeplan.ui.Glyph(glyph, Pixel.Ink, description = null)
        PixelTitle(text)
    }
}
