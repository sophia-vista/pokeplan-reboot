package app.pokeplan.ui.pokemon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pokeplan.core.pokemon.DeviceAction
import app.pokeplan.core.pokemon.EvolutionOption
import app.pokeplan.core.pokemon.EvolutionTrials
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.time.TimeSource
import app.pokeplan.ui.screens.long

/** How long the phone must stay upside down, and how fast a spin must be, as in the games (Alcremie: a full turn in under 5 seconds). *(tune)* */
private const val UPSIDE_DOWN_MILLIS = 1_500L
private const val SPIN_MILLIS = 5_000L
private const val FULL_TURN = 2 * PI

/**
 * An evolution done with the phone ([DeviceAction]), shown under its option on the Pokémon's page while it is open: the phone upside down
 * (Malamar), a quick spin (Alcremie) or a walk (Pawmot, Brambleghast, Rabsca). Sensors listen only while this is on screen. Once done, the store
 * keeps it ([app.pokeplan.app.GameStore.deviceTrialDone]) and the option can evolve. A walk keeps counting with the app closed: the phone's
 * step counter runs on, and the next reading adds the steps since the last one.
 */
@Composable
internal fun DeviceTrial(model: AppModel, p: OwnedPokemon, option: EvolutionOption, action: DeviceAction) {
    val feed = model.sensors
    var message by remember(p.id, action) { mutableStateOf<String?>(null) }
    var progress by remember(p.id, action) { mutableStateOf(0f) }
    val soft = MaterialTheme.typography.bodyMedium
    fun done() {
        model.store.deviceTrialDone(p.id, option)
        model.effect(Sfx.CONFIRM)
        model.haptic()
    }
    when (action) {
        DeviceAction.UPSIDE_DOWN -> DisposableEffect(p.id, feed) {
            var since: TimeSource.Monotonic.ValueTimeMark? = null
            var finished = false
            val stop = feed?.gravity { g ->
                if (finished) return@gravity
                // Upside down: gravity pulls toward the phone's top edge (y well below zero), whichever way it faces.
                if (g[1] < -7f) {
                    val mark = since ?: TimeSource.Monotonic.markNow().also { since = it }
                    val held = mark.elapsedNow().inWholeMilliseconds
                    progress = (held.toFloat() / UPSIDE_DOWN_MILLIS).coerceAtMost(1f)
                    if (held >= UPSIDE_DOWN_MILLIS) { finished = true; done() }
                } else { since = null; progress = 0f }
            }
            if (stop == null) message = "This phone has no motion sensor."
            onDispose { stop?.invoke() }
        }
        DeviceAction.SPIN -> DisposableEffect(p.id, feed) {
            var turned = 0.0
            var start: TimeSource.Monotonic.ValueTimeMark? = null
            var last: TimeSource.Monotonic.ValueTimeMark? = null
            var finished = false
            val stop = feed?.rotation { r ->
                if (finished) return@rotation
                val now = TimeSource.Monotonic.markNow()
                val dt = last?.let { (now - it).inWholeMilliseconds / 1000.0 } ?: 0.0
                last = now
                // The turn around the axis through the screen (the phone held flat), in one direction; a pause or a long spin starts again.
                if (abs(r[2]) < 1.0f) { turned = 0.0; start = null; progress = 0f; return@rotation }
                val began = start ?: now.also { start = it }
                if ((now - began).inWholeMilliseconds > SPIN_MILLIS) { turned = 0.0; start = now }
                turned += abs(r[2]) * dt
                progress = (turned / FULL_TURN).toFloat().coerceAtMost(1f)
                if (turned >= FULL_TURN) { finished = true; done() }
            }
            if (stop == null) message = "This phone has no gyroscope."
            onDispose { stop?.invoke() }
        }
        DeviceAction.STEPS -> DisposableEffect(p.id, feed) {
            var sent = -1L
            progress = (model.data.stepWalks[p.id]?.walked ?: 0).toFloat() / EvolutionTrials.STEPS
            val stop = feed?.steps({ counter ->
                // A reading a few steps apart is saved, not every step.
                if (sent < 0 || counter - sent >= 20 || counter < sent) {
                    sent = counter
                    val walked = model.store.stepReading(p.id, option, counter)
                    progress = (walked.toFloat() / EvolutionTrials.STEPS).coerceAtMost(1f)
                    if (walked >= EvolutionTrials.STEPS) { model.effect(Sfx.CONFIRM); model.haptic() }
                }
            }, { why -> message = why })
            if (stop == null) message = "This phone cannot count steps."
            onDispose { stop?.invoke() }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(action.howTo + ".", style = soft)
        PixelBar(progress, Modifier.fillMaxWidth(), Pixel.Green)
        if (action == DeviceAction.STEPS) {
            val walked = model.data.stepWalks[p.id]?.walked ?: 0
            Text("$walked of ${EvolutionTrials.STEPS} steps", style = soft, color = Pixel.InkSoft)
            Text("Or ${app.pokeplan.world.EvolutionTrialSites.WALK_FLOORS} World floors on a team", style = soft, color = Pixel.InkSoft)
        }
        message?.let { Text(it, style = soft, color = Pixel.InkSoft) }
    }
}
