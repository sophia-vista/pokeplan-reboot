package app.pokeplan.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.pokeplan.app.cloud.SyncStatus
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
import app.pokeplan.plan.FocusRules
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.display
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun SettingsView(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    var name by remember { mutableStateOf(model.data.trainerName) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Trainer name")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                val changed = name.isNotBlank() && name.trim() != model.data.trainerName
                OutlinedTextField(
                    name, { name = it.take(24) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (changed) store.rename(name) }),
                )
                PixelButton("Save", { store.rename(name) }, color = Pixel.Green, enabled = name.isNotBlank() && name.trim() != model.data.trainerName, compact = true)
            }
        }
    }
    if (store.sees(Feature.REGION_PICKER)) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Hatching region")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                TrainerRank.unlockedRegions(model.data.player.trainerPoints).forEach { r ->
                    TabButton(r.label, r == settings.region.open, { store.changeSettings { it.copy(region = r) } })
                }
            }
            Text("Eggs hatch from this region: the Plan Region's Pokémon, or the Care Region's gentle ones.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    // Settings come in small groups, one card each, so a setting is found by its group rather than by reading a long list.
    SettingsGroup("Modules") {
        ToggleRow(
            "Care", settings.careModule,
            detail = "Routines, calm breaks, a diary card, DBT skills and a journal, under Modules. Switched off, it is only hidden: what you saved in it stays.",
        ) { store.changeSettings { s -> s.copy(careModule = it) } }
        if (settings.careModule) CareCloudRow(model)
        if (settings.careModule) ToggleRow(
            "Routine reminders", model.careData.settings.reminders,
            detail = "A gentle note at a routine's own time, once, with Done. Routines with no time never remind.",
        ) { on -> model.care.changeSettings { it.copy(reminders = on) } }
    }
    SettingsGroup("Focus") {
        ToggleRow("Deep Focus by default", settings.deepFocus) { store.changeSettings { s -> s.copy(deepFocus = it) } }
        Stepper("Usual focus length", "${settings.defaultFocusMinutes} min", 5) { by ->
            store.changeSettings { s -> s.copy(defaultFocusMinutes = (s.defaultFocusMinutes + by).coerceIn(FocusRules.RETRY_MINUTES, FocusRules.MAX_MINUTES)) }
        }
    }
    SettingsGroup("Sound and touch") {
        VolumeRow("Sound effects and cries", settings.effectsVolume) { v -> store.changeSettings { s -> s.copy(effectsVolume = v, sound = true) } }
        ToggleRow("Vibration", settings.haptics) { store.changeSettings { s -> s.copy(haptics = it) } }
    }
    SettingsGroup("Look") {
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
        ToggleRow("Reduce motion", settings.reduceMotion, detail = "Sprites stand still, nothing wobbles and screens switch without sliding.") { store.changeSettings { s -> s.copy(reduceMotion = it) } }
    }
    SettingsGroup("Notifications and week") {
        ToggleRow("Task reminders", settings.reminders) { store.changeSettings { s -> s.copy(reminders = it) } }
        if (settings.reminders) {
            fun shift(t: LocalTime, by: Int) = LocalTime((t.hour + by + 24) % 24, 0)
            Stepper("Quiet from", hourText(settings.quietHours.start.hour), 1) { by ->
                store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(start = shift(s.quietHours.start, by))) }
            }
            Stepper("Quiet until", hourText(settings.quietHours.end.hour), 1) { by ->
                store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(end = shift(s.quietHours.end, by))) }
            }
            ToggleRow("Repeat until answered", settings.repeatReminders, detail = "An unanswered reminder comes back up to 3 times, 10 minutes apart.") { store.changeSettings { s -> s.copy(repeatReminders = it) } }
            Text("Reminders wait during quiet hours, a focus session and rest days.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        ToggleRow("Focus halfway alert", settings.focusHalfway, detail = "A quiet note halfway through sessions of 15 minutes or more.") { store.changeSettings { s -> s.copy(focusHalfway = it) } }
        ToggleRow("Focus end alert", settings.focusEnd, detail = "When a session's time is up. These two are the only notifications during focus.") { store.changeSettings { s -> s.copy(focusEnd = it) } }
        ToggleRow("Streak reminder", settings.streakReminder, detail = "In the evening, if nothing has counted yet today and you have a streak going.") { store.changeSettings { s -> s.copy(streakReminder = it) } }
        if (settings.streakReminder) {
            Stepper("Streak reminder at", hourText(settings.streakReminderHour), 1) { by ->
                store.changeSettings { s -> s.copy(streakReminderHour = (s.streakReminderHour + by + 24) % 24) }
            }
        }
        ToggleRow("Weekly recap", settings.weeklyRecap, detail = "Sunday evening: tasks finished, focus time and your top category. Skipped after an empty week.") { store.changeSettings { s -> s.copy(weeklyRecap = it) } }
        ToggleRow("Week starts on Monday", settings.weekStartsOnMonday) { store.changeSettings { s -> s.copy(weekStartsOnMonday = it) } }
    }
    CalendarsCard(model)
    AccountCard(model)
    DataCard(model)
    AboutCard()
}

/** Which of the phone's calendars show on Planner → Calendar. Only where the platform can read them (Android). */
@Composable
private fun CalendarsCard(model: AppModel) {
    val source = model.calendars ?: return
    var permitted by remember { mutableStateOf(source.permitted()) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>?>(null) }
    LaunchedEffect(permitted) { if (permitted) calendars = withContext(Dispatchers.Default) { source.calendars() } }
    val shown = model.data.shownCalendars

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Calendars")
            Text(
                "Show events from this phone's calendars, Google Calendar included, on Planner → Calendar. They're read-only and never pay.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            val list = calendars
            when {
                !permitted -> PixelButton("Choose calendars", { source.requestPermission { permitted = it } }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
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

/** The player's data: a copy to keep, deleting the account, starting over on this device. Each destructive step asks twice. */
@Composable
private fun DataCard(model: AppModel) {
    val store = model.store
    val sync = model.sync
    val signedIn = model.data.account != null
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Your data")
            model.files?.let { files ->
                PixelButton("Export my data", { note = files.save("pokeplan-export-${store.today}.json", store.exportJson()) }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
                PixelButton("Export Care data", { note = files.save("pokeplan-care-${store.today}.json", model.care.exportJson()) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            when (asking) {
                "account" -> {
                    Text("Delete your account? Your cloud save, friends and gifts are removed for good. This device keeps its progress as a guest.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Delete for good", {
                            busy = true
                            scope.launch {
                                note = sync?.deleteAccount() ?: "Your account was deleted."
                                busy = false
                                asking = null
                            }
                        }, Modifier.weight(1f), Pixel.Danger, enabled = !busy, compact = true)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                "care" -> {
                    val inCloud = model.careData.settings.cloudSync && model.careSync != null
                    Text(
                        "Erase Care's data on this device? Every routine, calm break, diary card, skill practised and journal entry is removed" +
                            (if (inCloud) ", and its copy in the cloud too" else "") + ". What they earned stays. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Erase", {
                            busy = true
                            scope.launch {
                                // The cloud copy goes first, so a failure leaves everything as it was.
                                val failed = if (inCloud) model.careSync?.turnOff() else null
                                if (failed == null) model.care.erase()
                                note = failed?.let { "Care's cloud copy could not be deleted: $it" } ?: "Care's data was erased."
                                busy = false
                                asking = null
                            }
                        }, Modifier.weight(1f), Pixel.Danger, enabled = !busy, compact = true)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                "device" -> {
                    Text("Erase this device? Every task, Pokémon, setting and Care entry here is removed and the app starts over. This cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Erase", { store.eraseDevice(); model.care.erase(); asking = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (signedIn && sync != null) PixelButton("Delete account", { asking = "account"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    else PixelButton("Erase this device", { asking = "device"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
        val colors = SliderDefaults.colors(thumbColor = Pixel.Red, activeTrackColor = Pixel.Red, inactiveTrackColor = Pixel.Shade, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = value / 100f, onValueChange = { onChange((it * 10).roundToInt() * 10) }, valueRange = 0f..1f, steps = 9,
            colors = colors, interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interaction, colors = colors, thumbSize = DpSize(24.dp, 24.dp)) },
            modifier = Modifier.semantics { contentDescription = "$label volume $value percent" },
        )
    }
}

/** The app's version, so a bug report or a friend's install can say exactly which build it is. */
@Composable
private fun AboutCard() {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            PixelTitle("About")
            Text("PokéLife ${AppVersion.display}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "A private fan project. Pokémon names, sprites and sounds belong to Nintendo, Game Freak and The Pokémon Company.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

/** Sign in, sign out and the state of the cloud save. Without a configured project it says online features are not set up. */
@Composable
private fun AccountCard(model: AppModel) {
    val sync = model.sync
    val account = model.data.account
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Account")
            when {
                sync == null -> Text(
                    "Online features are off in this build. Progress is saved on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                account == null -> {
                    Text("Sign in to back up your progress and use it on your other devices.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        label = { Text("Email") })
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        label = { Text("Password") },
                        // Say the rule up front instead of leaving the buttons grey without a reason.
                        supportingText = { Text("At least 6 characters", style = MaterialTheme.typography.bodyMedium) })
                    message?.let { Notice(it, tone = NoticeTone.WARNING) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Sign in", {
                            busy = true; message = null
                            scope.launch { message = sync.signIn(email.trim(), password); busy = false }
                        }, Modifier.weight(1f), Pixel.Blue, enabled = !busy && email.isNotBlank() && password.length >= 6, compact = true)
                        PixelButton("Create account", {
                            busy = true; message = null
                            scope.launch {
                                val result = sync.signUp(email.trim(), password)
                                message = if (result == "confirm") "Check your email to confirm your account, then sign in." else result
                                busy = false
                            }
                        }, Modifier.weight(1f), Pixel.Green, enabled = !busy && email.isNotBlank() && password.length >= 6, compact = true)
                    }
                }
                else -> {
                    Text("Signed in as ${account.email}", style = MaterialTheme.typography.bodyLarge)
                    val st = model.syncStatus
                    Notice(
                        when (st) {
                            SyncStatus.Syncing -> "Syncing..."
                            is SyncStatus.Offline -> "No connection. Your changes are kept and sent later."
                            is SyncStatus.Refused -> st.message
                            is SyncStatus.Conflict -> "This device and the cloud both have new progress."
                            else -> if (account.unsynced) "Changes waiting to sync" else "Everything is saved to the cloud."
                        },
                        tone = when (st) { is SyncStatus.Refused -> NoticeTone.DANGER; is SyncStatus.Offline, is SyncStatus.Conflict -> NoticeTone.WARNING; else -> NoticeTone.INFO },
                    )
                    val status = model.syncStatus
                    if (status is SyncStatus.Conflict) {
                        Text("Which should win?", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("This device", { scope.launch { sync.resolve(keepThisDevice = true) } }, Modifier.weight(1f), Pixel.Blue, compact = true)
                            PixelButton("The cloud", { scope.launch { sync.resolve(keepThisDevice = false) } }, Modifier.weight(1f), Pixel.Green, compact = true)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("Sync now", { scope.launch { sync.sync() } }, Modifier.weight(1f), Pixel.Blue, enabled = status != SyncStatus.Syncing, compact = true)
                            PixelButton("Sign out", { sync.signOut() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Care's opt-in cloud copy: off by default, so Care stays on this device. On, routines, diary cards and journal entries go to
 * the account's own Care copy (never to friends or leaderboards); off deletes that copy.
 */
@Composable
private fun CareCloudRow(model: AppModel) {
    val careSync = model.careSync ?: return
    val on = model.careData.settings.cloudSync
    val signedIn = model.data.account != null
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    ToggleRow(
        "Care copy in the cloud", on,
        detail = if (signedIn) "Off by default: Care stays on this device. On, it is kept with your account so another phone can have it, and never shown to friends. Off deletes the copy."
        else "Sign in under Account to keep a copy of Care with your account. Until then Care stays on this device.",
    ) { want ->
        if (busy || !signedIn) return@ToggleRow
        busy = true; message = null
        scope.launch {
            message = if (want) careSync.turnOn() else careSync.turnOff()
            busy = false
        }
    }
    if (on && signedIn) {
        val status = model.careSyncStatus
        Notice(
            when (status) {
                SyncStatus.Syncing -> "Syncing Care..."
                is SyncStatus.Offline -> "No connection. Care's changes are kept and sent later."
                is SyncStatus.Refused -> status.message
                is SyncStatus.Conflict -> "This device and the cloud both have new Care entries."
                else -> if (model.careData.cloud?.unsynced == true) "Care changes waiting to sync" else "Care is saved to the cloud."
            },
            tone = when (status) { is SyncStatus.Refused -> NoticeTone.DANGER; is SyncStatus.Offline, is SyncStatus.Conflict -> NoticeTone.WARNING; else -> NoticeTone.INFO },
        )
        if (status is SyncStatus.Conflict) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("This device", { scope.launch { careSync.resolve(keepThisDevice = true) } }, Modifier.weight(1f), Pixel.Blue, compact = true)
            PixelButton("The cloud", { scope.launch { careSync.resolve(keepThisDevice = false) } }, Modifier.weight(1f), Pixel.Green, compact = true)
        }
    }
    message?.let { Notice(it, tone = NoticeTone.WARNING) }
}

/** One group of settings in its own card, under a small heading. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
