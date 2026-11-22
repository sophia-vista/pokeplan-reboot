package app.pokeplan.ui.plan

import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.Spacing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.theme.ChoiceRow
import app.pokeplan.ui.theme.ToggleRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.Phase
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Stage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.SpeakerBox
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long

@Composable
fun FocusScreen(model: AppModel, openPokemon: ((String) -> Unit)? = null, onBack: (() -> Unit)? = null) = app.pokeplan.ui.theme.ModuleTone(app.pokeplan.ui.theme.ModuleAccent.PLAN) { FocusBody(model, openPokemon, onBack) }

@Composable
private fun FocusBody(model: AppModel, openPokemon: ((String) -> Unit)?, onBack: (() -> Unit)?) {
    val session = model.data.focus
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Focus", subtitle = if (session?.deepFocus == true) "Deep Focus" else null, icon = "mystery-egg", onBack = onBack, color = ModuleAccent.PLAN.header)
        if (session == null) SetupView(model) { Ends(model, openPokemon) }
        else Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ends(model, openPokemon)
            RunningView(model)
        }
    }
}

@Composable
private fun Ends(model: AppModel, openPokemon: ((String) -> Unit)?) {
    val end = model.data.lastFocusEnd
    LaunchedEffect(end) { if (end != null) model.effect(if (!end.completed) Sfx.ERROR else if (model.data.rewardLog.lastOrNull()?.rewards?.any { it is Reward.Hatched } == true) Sfx.EGG_GET else Sfx.FOCUS_END) }
    if (end != null && !end.completed) FailedBox(model, end.failReason)
    if (end != null) SessionSummary(model, end)
    if (end != null && end.completed) {
        val hatched = model.data.rewardLog.asReversed().firstNotNullOfOrNull { e -> e.rewards.filterIsInstance<Reward.Hatched>().lastOrNull() }?.pokemon
        val owned = hatched?.takeIf { h -> model.data.player.pokemon.any { it.id == h.id } }
        val line = if (end.guarded) "The session ended early, but Magic Guard kept your egg safe, and it hatched! (Once a week.)" else "Your egg hatched! Have a look at your Pokémon." // copy-ok: result
        SpeakerBox(model, Emotion.JOYOUS) { Text(line, style = MaterialTheme.typography.bodyLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (owned != null && openPokemon != null) {
                PixelButton("Meet ${owned.nickname}", { model.store.dismissFocusEnd(); openPokemon(owned.id) }, Modifier.weight(1f))
                PixelButton("OK", { model.store.dismissFocusEnd() }, Modifier.weight(1f), Pixel.InkSoft)
            } else {
                PixelButton("OK", { model.store.dismissFocusEnd() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SessionSummary(model: AppModel, end: app.pokeplan.app.FocusEnd) {
    if (end.plannedMinutes <= 0) return
    val task = end.taskId?.let { id -> model.data.tasks.firstOrNull { it.id == id } }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            PixelTitle(if (end.completed) "${end.minutes} min focused" else "${end.minutes} of ${end.plannedMinutes} min focused")
            if (task != null) Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (end.deepFocus) Text("Deep Focus", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

@Composable
private fun FailedBox(model: AppModel, reason: FailReason?) {
    SpeakerBox(model, if (reason == FailReason.MOVED_PHONE) Emotion.DIZZY else Emotion.SIGH) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(FocusRules.FAILED_MESSAGE, style = MaterialTheme.typography.titleMedium)
            Text(
                when (reason) {
                    FailReason.LEFT_APP -> "You left the app for more than 10 seconds." // copy-ok: result
                    FailReason.MOVED_PHONE -> "The phone moved during Deep Focus."
                    else -> "You stopped early."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Just ${FocusRules.RETRY_MINUTES} min", {
                    model.effect(Sfx.FOCUS_START)
                    model.store.dismissFocusEnd()
                    model.store.startFocus(FocusRules.RETRY_MINUTES, deepFocus = false)
                }, Modifier.weight(1f), Pixel.Tone, compact = true)
                PixelButton("Not now", { model.store.dismissFocusEnd() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
private fun ColumnScope.SetupView(model: AppModel, top: @Composable () -> Unit) {
    val store = model.store
    val settings = model.data.settings
    var minutes by remember { mutableStateOf(settings.defaultFocusMinutes) }
    var deep by remember { mutableStateOf(settings.deepFocus) }
    var taskId by remember { mutableStateOf(model.focusTaskId) }
    val tasks = store.todayTasks().filter { it.id !in model.data.pending.map { p -> p.original.id } }

    val idle = rememberInfiniteTransition()
    val tilt by idle.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(1_800, easing = app.pokeplan.ui.Ease.InOut), RepeatMode.Reverse))
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Stage("bw11", 168.dp) {
            ItemIcon("mystery-egg", size = 128.dp, modifier = Modifier.align(Alignment.Center).rotate(if (model.data.settings.reduceMotion) 0f else tilt))
        }

        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                app.pokeplan.ui.theme.FieldLabel("How long?")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    listOf(FocusRules.RETRY_MINUTES, 15, 25, 45, 60).forEach { m -> TabButton("$m min", m == minutes, { minutes = m }, Modifier.weight(1f)) }
                }
                PixelDurationField(
                    minutes, { if (it != null) minutes = it }, Pixel.Tone, presets = emptyList(),
                    min = FocusRules.RETRY_MINUTES, max = FocusRules.MAX_MINUTES,
                )
            }
        }

        TextBox { ToggleRow("Deep Focus", deep) { deep = it } }

        val everyOpen = planTasks(model).filter { it.isOpen }
        if (everyOpen.isNotEmpty()) {
            var searching by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            val chosen = taskId?.let { id -> everyOpen.firstOrNull { it.id == id } }
            val shown = tasks.take(4).let { first -> if (chosen != null && first.none { it.id == chosen.id }) listOf(chosen) + first.take(3) else first }
            val found = if (!searching) emptyList() else everyOpen.filter { t -> query.lowercase().split(' ').filter { it.isNotEmpty() }.all { it in t.title.lowercase() } && shown.none { it.id == t.id } }.take(6)
            TextBox {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelTitle("Working on")
                    TaskChoice("Nothing in particular", null, taskId == null) { taskId = null }
                    shown.forEach { t -> TaskChoice(t.title, t.rank, taskId == t.id) { taskId = t.id } }
                    if (everyOpen.size > shown.size) PixelButton(if (searching) "Hide search" else "Another task", { searching = !searching }, color = Pixel.InkSoft, compact = true)
                    if (searching) {
                        app.pokeplan.ui.theme.PixelField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Search open tasks")
                        found.forEach { t -> TaskChoice(t.title, t.rank, taskId == t.id) { taskId = t.id } }
                    }
                }
            }
        }

        val recent = model.data.focusHistory.takeLast(5).asReversed()
        if (recent.isNotEmpty()) TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelTitle("Recent")
                recent.forEach { r ->
                    val title = r.taskId?.let { id -> model.data.tasks.firstOrNull { it.id == id }?.title }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("${r.minutes} min", style = MaterialTheme.typography.bodyLarge)
                        Text(title ?: "No task", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (r.completed) "Hatched" else "Stopped", style = MaterialTheme.typography.bodyMedium, color = if (r.completed) Pixel.Green else Pixel.InkSoft)
                    }
                }
            }
        }

    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelButton("Start $minutes min", {
            model.effect(Sfx.FOCUS_START)
            if (settings.defaultFocusMinutes != minutes || settings.deepFocus != deep) store.changeSettings { it.copy(defaultFocusMinutes = minutes, deepFocus = deep) }
            store.startFocus(minutes, deep, taskId); store.dismissFocusEnd()
        }, Modifier.fillMaxWidth(), ModuleAccent.PLAN.accent)
    }
}

@Composable
private fun TaskChoice(title: String, rank: app.pokeplan.plan.Rank?, selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(selected, onClick, lead = { if (rank != null) RankBadge(rank) }) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FocusWatch(model: AppModel) {
    val store = model.store
    val session = model.data.focus ?: return
    val phone = model.phone
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        store.focusEvent(
            when {
                phone?.inCall == true -> FocusEvent.CallStarted
                phone?.screenOff == true -> FocusEvent.ScreenLocked
                else -> FocusEvent.Left
            },
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (phone?.inCall == true) store.focusEvent(FocusEvent.CallStarted)
        else if (phone?.screenOff == true) store.focusEvent(FocusEvent.ScreenLocked)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        store.focusEvent(FocusEvent.CallEnded)
        store.focusEvent(FocusEvent.Returned)
    }

    if (session.deepFocus && model.motion != null) {
        DisposableEffect(session.id) {
            model.motion.start { store.focusEvent(FocusEvent.Moved) }
            onDispose { model.motion.stop() }
        }
    }
}

@Composable
private fun RunningView(model: AppModel) {
    val store = model.store
    val session = model.data.focus ?: return
    val now = model.now
    val remaining = session.remainingMillis(now).coerceAtLeast(0)
    val total = session.durationMillis
    val paused = session.phase is Phase.Paused
    val away = session.phase is Phase.Away

    val wobble = rememberInfiniteTransition()
    val angle by wobble.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
    )
    Box(Modifier.rotate(if (model.data.settings.reduceMotion || paused) 0f else angle)) { ItemIcon("mystery-egg", size = 200.dp) }

    SpeakerBox(model, if (away) Emotion.SIGH else if (paused) Emotion.NORMAL else Emotion.DETERMINED) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth()) {
            Text(
                clock(remaining), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val elapsed = 1f - remaining.toFloat() / total
            PixelBar(elapsed, color = Pixel.Tone, label = "${(elapsed * 100).toInt()} percent of the session done")
            val status = when {
                paused && (session.phase as Phase.Paused).reason == app.pokeplan.plan.PauseReason.CALL -> "Paused for a call"
                paused -> "Paused. Resume within ${FocusRules.EMERGENCY_PAUSE_MILLIS / 60_000} minutes."
                away -> "Come back! The egg is getting cold."
                else -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }

    val pausesLeft = FocusRules.EMERGENCY_PAUSES_PER_DAY - session.emergencyPausesUsed
    val emergency = (session.phase as? Phase.Paused)?.reason == app.pokeplan.plan.PauseReason.EMERGENCY
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (emergency) {
            PixelButton("Resume", { store.focusEvent(FocusEvent.EmergencyPauseEnded) }, Modifier.weight(1f), Pixel.Tone)
        } else {
            PixelButton("Pause", { store.focusEvent(FocusEvent.EmergencyPauseStarted) }, Modifier.weight(1f).semantics { contentDescription = "Pause, $pausesLeft left today" }, Pixel.Tone, enabled = pausesLeft > 0 && !paused)
        }
        var sure by remember { mutableStateOf(false) }
        LaunchedEffect(sure) { if (sure) { kotlinx.coroutines.delay(4_000); sure = false } }
        PixelButton(if (sure) "Really give up?" else "Give up", { if (sure) store.focusEvent(FocusEvent.GaveUp) else sure = true }, Modifier.weight(1f), if (sure) Pixel.Danger else Pixel.InkSoft)
    }
}

private fun clock(millis: Long): String {
    val total = (millis + 999) / 1000
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}
