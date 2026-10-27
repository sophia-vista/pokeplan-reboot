package app.pokeplan.ui.screens

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
import app.pokeplan.core.dex.Region
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.Phase
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Stage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

/** [openPokemon] shows a Pokémon on the Pokémon tab: the one that just hatched. */
@Composable
fun FocusScreen(model: AppModel, openPokemon: ((String) -> Unit)? = null, onBack: (() -> Unit)? = null) {
    val session = model.data.focus
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Focus", subtitle = if (session == null) "Hatch an egg" else if (session.deepFocus) "Deep Focus" else "Egg incubating", icon = "mystery-egg", onBack = onBack)
        if (session == null) SetupView(model) { Ends(model, openPokemon) }
        else Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ends(model, openPokemon)
            RunningView(model)
        }
    }
}

/** How the last session ended: the rewards, then a way back in after a cold egg, or a way to meet the one that hatched. */
@Composable
private fun Ends(model: AppModel, openPokemon: ((String) -> Unit)?) {
    RewardBox(model)
    val end = model.data.lastFocusEnd
    if (end != null && !end.completed) FailedBox(model, end.failReason)
    if (end != null && end.completed) {
        // The hatch is the peak of the loop, so its end leads straight to the new Pokémon rather than to a bare OK.
        val hatched = model.data.rewardLog.asReversed().firstNotNullOfOrNull { e -> e.rewards.filterIsInstance<Reward.Hatched>().lastOrNull() }?.pokemon
        val owned = hatched?.takeIf { h -> model.data.player.pokemon.any { it.id == h.id } }
        val line = if (end.guarded) "The session ended early, but Magic Guard kept your egg safe, and it hatched! (Once a week.)" else "Your egg hatched! Have a look at your Pokémon."
        SpeakerBox(model, Emotion.JOYOUS) { Text(line, style = MaterialTheme.typography.bodyLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (owned != null && openPokemon != null) {
                PixelButton("Meet ${owned.nickname}", { model.store.dismissFocusEnd(); openPokemon(owned.id) }, Modifier.weight(1f), Pixel.Green)
                PixelButton("OK", { model.store.dismissFocusEnd() }, Modifier.weight(1f), Pixel.InkSoft)
            } else {
                PixelButton("OK", { model.store.dismissFocusEnd() }, Modifier.fillMaxWidth(), Pixel.Green)
            }
        }
    }
}

@Composable
private fun FailedBox(model: AppModel, reason: FailReason?) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(FocusRules.FAILED_MESSAGE, style = MaterialTheme.typography.titleMedium)
            Text(
                when (reason) {
                    FailReason.LEFT_APP -> "You left the app for more than 10 seconds."
                    FailReason.MOVED_PHONE -> "The phone moved during Deep Focus."
                    else -> "You stopped early."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Just ${FocusRules.RETRY_MINUTES} min", {
                    model.store.dismissFocusEnd()
                    model.store.startFocus(FocusRules.RETRY_MINUTES, deepFocus = false)
                }, Modifier.weight(1f), Pixel.Blue, compact = true)
                PixelButton("Not now", { model.store.dismissFocusEnd() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

/**
 * Choosing a session. The settings scroll; Start stays pinned at the bottom, big and near the thumb, so it never has to be
 * looked for below the fold.
 */
@Composable
private fun ColumnScope.SetupView(model: AppModel, top: @Composable () -> Unit) {
    val store = model.store
    val settings = model.data.settings
    var minutes by remember { mutableStateOf(settings.defaultFocusMinutes) }
    var deep by remember { mutableStateOf(settings.deepFocus) }
    var taskId by remember { mutableStateOf(model.focusTaskId) }
    val tasks = store.todayTasks()

    // A slow, small wobble so the egg feels alive while you set up (still when Reduce motion is on).
    val idle = rememberInfiniteTransition()
    val tilt by idle.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Stage("bw11", 168.dp) {
            ItemIcon("mystery-egg", size = 128.dp, modifier = Modifier.align(Alignment.Center).rotate(if (model.data.settings.reduceMotion) 0f else tilt))
        }

        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How long?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15, 25, 45, 60).forEach { m -> TabButton("$m min", m == minutes, { minutes = m }, Modifier.weight(1f)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("-5", { minutes = (minutes - 5).coerceAtLeast(FocusRules.RETRY_MINUTES) }, color = Pixel.InkSoft, compact = true)
                    Text("$minutes minutes", style = MaterialTheme.typography.titleMedium)
                    PixelButton("+5", { minutes = (minutes + 5).coerceAtMost(180) }, color = Pixel.InkSoft, compact = true)
                }
            }
        }

        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Deep Focus", deep, detail = if (deep) "Better eggs, one rarity band up. Picking up or moving the phone ends the session, so lay it flat."
                    else "Better eggs if the phone stays still. Off, the timer works as usual and the phone can move.") { deep = it }
                Text("Hatching in: ${regionName(settings.region)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }

        if (tasks.isNotEmpty()) {
            TextBox {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Working on", style = MaterialTheme.typography.titleMedium)
                    TaskChoice("Nothing in particular", null, taskId == null) { taskId = null }
                    tasks.take(4).forEach { t -> TaskChoice(t.title, t.rank, taskId == t.id) { taskId = t.id } }
                }
            }
        }

        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleMedium)
                val grace = FocusRules.GRACE_MILLIS / 1000
                val pause = FocusRules.EMERGENCY_PAUSE_MILLIS / 60_000
                listOf(
                    "The egg hatches when the timer reaches zero.",
                    "Leaving PokéPlan for more than $grace seconds makes the egg go cold.",
                    "Locking the screen is fine: the timer keeps going with the phone face down.",
                    "A phone call pauses the timer until you come back.",
                    "${FocusRules.EMERGENCY_PAUSES_PER_DAY} pauses of up to $pause minutes a day; paused time doesn't count. Give up stops at any time.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        PixelButton("Start $minutes min", { store.startFocus(minutes, deep, taskId); store.dismissFocusEnd() }, Modifier.fillMaxWidth(), Pixel.Red)
        // For when starting is the hard part: the shortest session there is, with no choices to make.
        val five = FocusRules.justFiveMinutes().minutesRoundedUp
        if (minutes != five) PixelButton("Just $five minutes", { store.startFocus(five, deepFocus = false, taskId = taskId); store.dismissFocusEnd() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    }
}

/** One of the tasks the session can be for: a radio row, so a choice never looks like an action button. */
@Composable
private fun TaskChoice(title: String, rank: app.pokeplan.plan.Rank?, selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(selected, onClick, lead = { if (rank != null) RankBadge(rank) }) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun regionName(region: Region) = region.label

/**
 * Watches a running session wherever the player is in the app, not only on the Focus tab: leaving the app, a call, a
 * locked screen and, for Deep Focus, the phone moving.
 */
@Composable
fun FocusWatch(model: AppModel) {
    val store = model.store
    val session = model.data.focus ?: return
    // Leaving the app starts the 10 second grace period; coming back stops it. A call pauses the clock instead, and a
    // locked screen is not leaving (the stop is checked too, since the screen can report off only after the pause).
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

    // Deep Focus: moving the phone ends the session.
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

    // The buddy keeps you company beside the clock: set on it while you work, surprised when you leave.
    SpeakerBox(model, if (away) Emotion.SURPRISED else if (paused) Emotion.NORMAL else Emotion.DETERMINED) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                clock(remaining), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val elapsed = 1f - remaining.toFloat() / total
            PixelBar(elapsed, color = Pixel.Green, label = "${(elapsed * 100).toInt()} percent of the session done")
            // Focused time is what counts; pauses stop the clock and are shown apart so the two are never mixed up.
            val pausedNow = session.pausedMillis + ((session.phase as? Phase.Paused)?.let { now - it.since } ?: 0)
            Text(
                "Focused ${minutes(session.focusedMillis(now))} of ${minutes(total)} min" +
                    if (pausedNow >= 60_000) ", paused ${minutes(pausedNow)} min" else "",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            Text(
                when {
                    paused && (session.phase as Phase.Paused).reason == app.pokeplan.plan.PauseReason.CALL -> "Paused for a call"
                    paused -> "Paused. Resume within ${FocusRules.EMERGENCY_PAUSE_MILLIS / 60_000} minutes."
                    away -> "Come back! The egg is getting cold."
                    session.deepFocus -> "Stay with it, and keep the phone still."
                    else -> "Stay with it."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    val pausesLeft = FocusRules.EMERGENCY_PAUSES_PER_DAY - session.emergencyPausesUsed
    val emergency = (session.phase as? Phase.Paused)?.reason == app.pokeplan.plan.PauseReason.EMERGENCY
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (emergency) {
            PixelButton("Resume", { store.focusEvent(FocusEvent.EmergencyPauseEnded) }, Modifier.weight(1f), Pixel.Green)
        } else {
            PixelButton("Pause ($pausesLeft)", { store.focusEvent(FocusEvent.EmergencyPauseStarted) }, Modifier.weight(1f), Pixel.Blue, enabled = pausesLeft > 0 && !paused)
        }
        // Giving up is the way out, not the thing to do, so it stays the quiet button.
        PixelButton("Give up", { store.focusEvent(FocusEvent.GaveUp) }, Modifier.weight(1f), Pixel.InkSoft)
    }
}

private fun minutes(millis: Long): Long = millis / 60_000

private fun clock(millis: Long): String {
    val total = (millis + 999) / 1000
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}
