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
import androidx.compose.foundation.layout.Box
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
import app.pokeplan.ui.launcher.DateStyle
import app.pokeplan.ui.launcher.dateStyle
import kotlinx.datetime.DayOfWeek
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
import androidx.compose.runtime.withFrameNanos
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
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.LoadingOverlay
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.updates.UpdateRelease
import app.pokeplan.ui.updates.updateSize

@Composable
internal fun GeneralSettings(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    var name by remember { mutableStateOf(model.data.trainerName) }
    val saveName by rememberUpdatedState { if (name.isNotBlank() && name.trim() != model.data.trainerName) store.rename(name) }
    DisposableEffect(Unit) { onDispose { saveName() } }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Trainer name", "user")
            PixelField(
                name, { name = it.take(24) }, Modifier.fillMaxWidth(), label = "Name", singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveName() }),
            )
        }
    }
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
    }
    SettingsGroup("Accessibility", "eye") {
        ToggleRow("Reduce motion", settings.reduceMotion) { store.changeSettings { s -> s.copy(reduceMotion = it) } }
    }
    SettingsGroup("Date and time", "calendar") {
        FieldLabel("Week starts on")
        PixelDropdown(
            weekStartChoices, settings.weekStart, { it?.let(::fullDayName) ?: "" },
            { day -> if (day != null) store.changeSettings { s -> s.withWeekStart(day) } },
            Modifier.fillMaxWidth(),
        )
        ToggleRow("12-hour clock (AM and PM)", settings.clock12Hour) { store.changeSettings { s -> s.copy(clock12Hour = it) } }
        FieldLabel("Date format")
        PixelDropdown(
            DateStyle.entries, settings.dateStyle, { it?.pattern ?: "" },
            { style -> if (style != null) store.changeSettings { s -> s.copy(dateFormat = style.name) } },
            Modifier.fillMaxWidth(),
        )
    }
}

private val weekStartChoices = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY)

private fun fullDayName(day: DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
internal fun NotificationSettings(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    model.phone?.let { phone ->
        var notify by remember { mutableStateOf(phone.notificationsAllowed) }
        var exact by remember { mutableStateOf(phone.exactAlarms) }
        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { notify = phone.notificationsAllowed; exact = phone.exactAlarms }
        SettingsGroup("Permissions", "lock") {
            PermissionRow("Notifications", notify, "Open settings") { phone.openNotificationSettings() }
            PermissionRow("Exact alarms", exact, "Allow") { phone.allowExactAlarms() }
        }
    }
    SettingsGroup("Quiet hours", "moon") {
        PixelTimeField(
            settings.quietHours.start, { t -> t?.let { store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(start = it)) } } },
            Pixel.Tone, label = "Quiet from",
        )
        PixelTimeField(
            settings.quietHours.end, { t -> t?.let { store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(end = it)) } } },
            Pixel.Tone, label = "Quiet until",
        )
    }
    SettingsGroup("Reminders", "bell") {
        ToggleRow("Plan tasks", settings.reminders) { store.changeSettings { s -> s.copy(reminders = it) } }
        ToggleRow("Care routines", model.careData.settings.reminders) { on -> model.care.changeSettings { it.copy(reminders = on) } }
        ToggleRow("Base chores and upkeep", model.baseData.settings.reminders) { model.base.setReminders(it) }
        ToggleRow("Link dates and follow-ups", model.linkData.settings.reminders) { model.link.setReminders(it) }
        ToggleRow("Mint bills", model.mintData.settings.billReminders) { model.mint.setBillReminders(it) }
        ToggleRow("World job alert", settings.worldJobAlerts) { store.changeSettings { s -> s.copy(worldJobAlerts = it) } }
        ToggleRow("Streak reminder", settings.streakReminder) { store.changeSettings { s -> s.copy(streakReminder = it) } }
        if (settings.streakReminder) {
            Stepper("Streak reminder at", hourText(settings.streakReminderHour), 1) { by ->
                store.changeSettings { s -> s.copy(streakReminderHour = (s.streakReminderHour + by + 24) % 24) }
            }
        }
        ToggleRow("Weekly recap", settings.weeklyRecap) { store.changeSettings { s -> s.copy(weeklyRecap = it) } }
    }
}

@Composable
private fun PermissionRow(label: String, allowed: Boolean, action: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(if (allowed) "Allowed" else "Blocked", style = MaterialTheme.typography.bodyMedium, color = if (allowed) Pixel.Green else Pixel.Danger)
        if (!allowed) PixelButton(action, open, color = Pixel.Tone, compact = true)
    }
}

@Composable
internal fun PrivacySettings(model: AppModel) {
    val gate = model.gate
    if (gate == null || (!gate.available() && model.data.settings.lockedModules.isEmpty() && !model.mintData.settings.gate)) {
        SettingsGroup("Screen lock", "lock") { Text("No screen lock on this phone", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        return
    }
    SettingsGroup("Screen lock", "lock") {
        gateableModules.forEach { m ->
            val on = m.name in model.data.settings.lockedModules
            ToggleRow("Lock ${m.label}", on) { want ->
                fun set(value: Boolean) = model.store.changeSettings { s -> s.copy(lockedModules = if (value) s.lockedModules + m.name else s.lockedModules - m.name) }
                if (!want) set(false)
                else gate.authenticate { ok -> if (ok) { set(true); model.moduleUnlockedAt[m.name] = model.store.nowMillis } }
            }
        }
        ToggleRow("Lock ${Module.MINT.label}", model.mintData.settings.gate) { want ->
            if (!want) model.mint.setGate(false)
            else gate.authenticate { ok -> if (ok) { model.mint.setGate(true); model.moduleUnlockedAt[Module.MINT.name] = model.store.nowMillis } }
        }
    }
}

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

@Composable
internal fun BackupsCard(model: AppModel) {
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
    var copying by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Long?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hide = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    fun restored(text: String) { model.store.reload(); model.care.reload(); model.heal.reload(); model.mint.reload(); model.base.reload(); model.link.reload(); entries = backups.automatic(); note = text }
    Box {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                GlyphTitle("Complete backup", "download")
                if (backups.lowStorage()) Text("This phone is almost out of storage, so a backup was skipped and some changes may not be saved. Free some space and it carries on by itself.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
                Text(entries.firstOrNull()?.let { "Last backup: ${backupLabel(it.millis)}" } ?: "No backup yet", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton("Back up now", {
                        backingUp = true
                        // The backup is written on this thread; wait one frame so the key can show its pixels before it starts (5.6).
                        scope.launch { withFrameNanos { }; note = backups.backupNow(); entries = backups.automatic(); backingUp = false }
                    }, Modifier.weight(1f), Pixel.Tone, loading = backingUp, compact = true)
                    PixelButton("Save a copy", { copying = true; backups.saveCopy(null) { copying = false; note = it } }, Modifier.weight(1f), Pixel.InkSoft, loading = copying, compact = true)
                }
                PixelButton(if (portable) "Not now" else "Export a backup", { portable = !portable; pass = ""; again = ""; shown = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                if (portable) {
                    PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase (at least ${backups.minPassphrase} characters)", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    PixelField(again, { again = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase again", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        error = if (again.isNotEmpty() && again != pass) "The two do not match." else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelButton("Save the copy", {
                            busy = true
                            backups.saveCopy(null, pass) { busy = false; note = it; if (it == "Saved.") { portable = false; pass = ""; again = "" } }
                        }, Modifier.weight(1f), Pixel.Tone, enabled = !busy && pass.length >= backups.minPassphrase && pass == again, loading = busy, compact = true)
                        PixelButton(if (shown) "Hide" else "Show", { shown = !shown }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                entries.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(backupLabel(e.millis), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        PixelButton("Save", { saving = e.millis; backups.saveCopy(e.millis) { saving = null; note = it } }, color = Pixel.InkSoft, loading = saving == e.millis, compact = true)
                        PixelButton("Restore", { asking = e.millis; chosen = null }, color = Pixel.Warning, compact = true)
                    }
                }
                asking?.let { millis ->
                    Text("Put the backup from ${backupLabel(millis)} back? What is on this phone now is kept aside, so this can be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelButton("Restore", {
                            restoring = true
                            // Putting the save back runs on this thread; wait one frame so the blocking overlay is drawn first.
                            scope.launch { withFrameNanos { }; restored(backups.restoreAutomatic(millis)); asking = null; restoring = false }
                        }, Modifier.weight(1f), Pixel.Warning, loading = restoring, compact = true)
                        PixelButton("Keep what I have", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                PixelButton("Restore from a file", { asking = null; chosen = null; pass = ""; backups.chooseFile { staged, why -> chosen = staged; note = why } }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                chosen?.let { c ->
                    Text("This file is a backup from ${backupLabel(c.createdAtMillis)} (${c.documents.joinToString(", ")}). Replace what is on this phone with it? What is here now is kept aside.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) // copy-ok: warning or legal
                    if (c.needsPassphrase) PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Backup passphrase", singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelButton("Restore", {
                            restoring = true
                            backups.applyChosen(pass.takeIf { c.needsPassphrase }) { restored(it); restoring = false; if (it.startsWith("Restored")) { chosen = null; pass = "" } }
                        }, Modifier.weight(1f), Pixel.Warning, enabled = !restoring && (!c.needsPassphrase || pass.isNotEmpty()), loading = restoring, compact = true)
                        PixelButton("Cancel", { chosen = null; pass = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                if (backups.hasOffPhone) OffPhoneSection(backups, nowMillis = model.store.nowMillis,
                    onNote = { note = it; entries = backups.automatic() }, onStaged = { staged, why -> chosen = staged; note = why; asking = null; pass = "" })
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
        if (restoring) LoadingOverlay("Restoring your save…", Modifier.matchParentSize())
    }
}

@Composable
internal fun OffPhoneSection(backups: app.pokeplan.ui.BackupAccess, nowMillis: Long, onNote: (String) -> Unit, onStaged: (app.pokeplan.ui.StagedBackup?, String?) -> Unit, startSetting: Boolean = false) {
    var state by remember { mutableStateOf(backups.offPhone()) }
    var setting by remember { mutableStateOf(startSetting) }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val hide = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    fun done(text: String) { busy = false; state = backups.offPhone(); onNote(text) }
    val current = state
    if (current == null || setting) {
        PixelButton(if (setting) "Not now" else "Back up off this phone", { setting = !setting; pass = ""; again = ""; shown = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (setting) {
            PixelField(pass, { pass = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase (at least ${backups.minPassphrase} characters)", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            PixelField(again, { again = it.take(128) }, Modifier.fillMaxWidth(), label = "Passphrase again", singleLine = true, visualTransformation = hide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                error = if (again.isNotEmpty() && again != pass) "The two do not match." else null)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Choose the folder", { busy = true; backups.setUpOffPhone(pass) { setting = false; pass = ""; again = ""; done(it) } }, Modifier.weight(1f), Pixel.Tone,
                    enabled = !busy && pass.length >= backups.minPassphrase && pass == again, loading = busy, compact = true)
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
            PixelButton("Copy now", { busy = true; backups.backUpOffPhoneNow(::done) }, Modifier.weight(1f), Pixel.Tone, enabled = !busy, loading = busy, compact = true)
            PixelButton("Restore copy", { picking = true; backups.chooseOffPhoneCopy { staged, why -> picking = false; onStaged(staged, why) } }, Modifier.weight(1f), Pixel.Warning, enabled = !busy && !picking, loading = picking, compact = true)
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

@Composable
internal fun ModuleExportsCard(model: AppModel) {
    val files = model.files ?: return
    var note by remember { mutableStateOf<String?>(null) }
    val today = model.store.today
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Module exports", "download")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("Plan and Pokémon", { note = files.save("pokeplan-export-$today.json", model.store.exportJson()) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Care", { note = files.save("pokeplan-care-$today.json", model.care.exportJson()) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Heal", { note = files.save("pokelife-heal.json", model.heal.export()) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Mint", { note = files.save("pokelife-mint.json", model.mint.export()) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Base", { note = files.save("pokelife-base.json", model.base.export()) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Link", { note = files.save("pokelife-link.json", model.link.export()) }, color = Pixel.InkSoft, compact = true)
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        }
    }
}

@Composable
internal fun ResetCard(model: AppModel) {
    val store = model.store
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Reset", "trash")
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
                    Text("Erase this device? Everything here is removed and the app starts over: tasks, Pokémon, every module's records and settings, the launcher's layout, reminders, and the backups on this phone and the off-phone copy setup. A copy you saved as a file is not touched. This cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        HoldToConfirm("Hold to erase", {
                            store.eraseDevice(); model.care.erase(); model.heal.resetAll(); model.mint.resetAll(); model.base.resetAll(); model.link.resetAll()
                            note = model.backups?.eraseLeftovers()?.ifBlank { null }; asking = null
                        }, Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge)
            Text("$value%", Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium)
        }
        PixelSlider(
            value = value / 100f, onChange = { onChange((it * 10).roundToInt() * 10) }, steps = 9,
            modifier = Modifier.semantics { contentDescription = "$label volume $value percent" },
        )
    }
}

@Composable
internal fun AboutCard(model: AppModel) {
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
    UpdatesCard(model)
}

/** The states the Updates box is in while it reads GitHub, downloads the APK and hands it to the installer. */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object Latest : UpdateUi
    data class Available(val release: UpdateRelease) : UpdateUi
    data class Downloading(val release: UpdateRelease, val percent: Int) : UpdateUi
    data class Ready(val release: UpdateRelease) : UpdateUi
    data class Problem(val text: String) : UpdateUi
}

private fun updateStateFor(model: AppModel): UpdateUi {
    model.updateAvailable?.let { release ->
        val updates = model.updates
        return if (updates != null && updates.downloaded(release)) UpdateUi.Ready(release) else UpdateUi.Available(release)
    }
    model.updateProblem?.let { return UpdateUi.Problem(it) }
    return UpdateUi.Latest
}

/** In-app updates from the rolling "latest" GitHub release: one box in Me → Settings → About. */
@Composable
private fun UpdatesCard(model: AppModel) {
    val updates = model.updates ?: return
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    // "Install unknown apps" is granted outside the app; coming back to the screen picks it up.
    var allowed by remember { mutableStateOf(updates.canInstall()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { allowed = updates.canInstall() }
    val scope = rememberCoroutineScope()

    fun checkAgain() {
        scope.launch {
            ui = UpdateUi.Checking
            model.checkForUpdate()
            ui = updateStateFor(model)
        }
    }

    LaunchedEffect(Unit) {
        if (model.updateChecked) {
            ui = updateStateFor(model)
        } else {
            ui = UpdateUi.Checking
            model.checkForUpdate()
            ui = updateStateFor(model)
        }
    }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GlyphTitle("Updates", "download")
            when (val state = ui) {
                UpdateUi.Idle, UpdateUi.Checking -> Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                UpdateUi.Latest -> {
                    Text("Up to date.", style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Check again", { checkAgain() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
                is UpdateUi.Available -> {
                    val size = updateSize(state.release.sizeBytes)
                    Text(if (size.isEmpty()) "PokéLife ${state.release.version} is out." else "PokéLife ${state.release.version} is out, $size.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("Download", {
                            scope.launch {
                                ui = UpdateUi.Downloading(state.release, 0)
                                val ok = updates.download(state.release) { percent -> ui = UpdateUi.Downloading(state.release, percent) }
                                ui = if (ok) UpdateUi.Ready(state.release) else UpdateUi.Problem(updates.problem ?: "The download stopped.")
                            }
                        }, Modifier.weight(1f), Pixel.Tone, compact = true)
                        PixelButton("Release notes", { updates.openReleasePage() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                is UpdateUi.Downloading -> {
                    Text("Downloading ${state.percent}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    PixelBar(state.percent / 100f, label = "Downloading ${state.percent}%")
                }
                is UpdateUi.Ready -> {
                    Text("PokéLife ${state.release.version} is downloaded.", style = MaterialTheme.typography.bodyLarge)
                    if (allowed) {
                        PixelButton("Install update", {
                            if (!updates.install(state.release)) {
                                // The permission can be revoked between screens; then the box shows the settings key instead.
                                allowed = updates.canInstall()
                                if (allowed) ui = UpdateUi.Problem(updates.problem ?: "The installer did not open.")
                            }
                        }, Modifier.fillMaxWidth(), Pixel.Tone)
                    } else {
                        Text("Allow app installs first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
                        PixelButton("Open settings", { updates.openInstallSettings() }, Modifier.fillMaxWidth(), Pixel.Tone)
                    }
                }
                is UpdateUi.Problem -> {
                    Text(state.text, style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    PixelButton("Try again", { checkAgain() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}

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

private fun hourText(hour: Int) = "${hour.toString().padStart(2, '0')}:00"

@Composable
internal fun PlanSettings(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    SettingsGroup("Alerts", "bell") {
        if (settings.reminders) ToggleRow("Repeat until answered", settings.repeatReminders) { store.changeSettings { s -> s.copy(repeatReminders = it) } }
        ToggleRow("Focus halfway alert", settings.focusHalfway) { store.changeSettings { s -> s.copy(focusHalfway = it) } }
        ToggleRow("Focus end alert", settings.focusEnd) { store.changeSettings { s -> s.copy(focusEnd = it) } }
    }
    TextBox { ListsSettings(model) }
    SettingsGroup("Rewards", "zap") {
        PixelChoice(
            app.pokeplan.core.reward.RewardFocus.entries.toList(), settings.defaultReward, { it?.label ?: "" },
            { r -> if (r != null) store.changeSettings { s -> s.copy(defaultReward = r) } }, label = "New tasks pay most in",
        )
    }
    CalendarsCard(model)
}

@Composable
internal fun WorldSettings(model: AppModel) {
    val settings = model.data.settings
    SettingsGroup("Animation", "zap") {
        ToggleRow(
            "Scrolling floors", settings.worldScroll,
        ) { model.store.changeSettings { s -> s.copy(worldScroll = it) } }
    }
}

@Composable
private fun GlyphTitle(text: String, glyph: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        app.pokeplan.ui.Glyph(glyph, Pixel.Ink, description = null)
        PixelTitle(text)
    }
}

@Composable
private fun ListsSettings(model: AppModel) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        app.pokeplan.ui.plan.ListsSection(model, model.data.tasks, onMade = { editing = null }) { id -> editing = if (editing == id) null else id }
        model.data.lists.firstOrNull { it.id == editing }?.let { l -> app.pokeplan.ui.plan.ListEditor(model, l) { editing = null } }
    }
}
