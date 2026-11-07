package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.pokeplan.app.Tutorials
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * A module's first-run tutorial (docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` section 10.2.1): a 5-second demo of how the module gives a
 * Pokémon, which gives one, once. It shows only until it is done (or the real thing has been done), never blocks anything, and the demo
 * is not a focus session or a calm break and leaves no record. [id] is a [Tutorials] id.
 */
@Composable
internal fun TutorialCard(model: AppModel, id: String, careBreaks: Int = 0, healActivities: Int = 0) {
    model.data // observe the save: the store itself is not Compose state
    if (!model.store.tutorialOffered(id, careBreaks, healActivities)) return
    val plan = id == Tutorials.PLAN_FOCUS
    val heal = id == Tutorials.HEAL_WALK
    var running by remember(id) { mutableStateOf(false) }
    var seconds by remember(id) { mutableStateOf(0f) }
    var cancelled by remember(id) { mutableStateOf(false) }
    // The demo runs on a clock of its own: leaving the screen stops it, and nothing is saved until it has run to the end.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val steps = Tutorials.DEMO_SECONDS * 10
        for (i in 1..steps) { delay(100); seconds = i / 10f }
        model.store.completeTutorial(id)?.let { model.haptic() }
        running = false
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (plan) "Try a focus session" else if (heal) "Go on a short walk" else "Take a breath")
            if (!running) {
                Text(
                    if (plan) "A ${Tutorials.DEMO_SECONDS} second focus gives you a first Pokémon for Plan's party."
                    else if (heal) "A ${Tutorials.DEMO_SECONDS} second walk gives you a first Pokémon for Heal's party."
                    else "One breath gives you a first Pokémon for Care's party.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                PixelButton(if (plan) "Start the ${Tutorials.DEMO_SECONDS} second focus" else if (heal) "Walk for ${Tutorials.DEMO_SECONDS} seconds" else "Breathe for ${Tutorials.DEMO_SECONDS} seconds", { seconds = 0f; cancelled = false; running = true }, color = Pixel.Green, compact = true)
            } else {
                val left = (Tutorials.DEMO_SECONDS - seconds).coerceAtLeast(0f)
                Text(
                    if (plan) "Focusing… ${kotlin.math.ceil(left.toDouble()).toInt()}" else if (heal) "Walking… ${kotlin.math.ceil(left.toDouble()).toInt()}" else if (seconds < Tutorials.DEMO_SECONDS / 2f) "Breathe in…" else "…and out",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                PixelBar(seconds / Tutorials.DEMO_SECONDS, color = Pixel.Green, label = "Demo: ${kotlin.math.ceil(left.toDouble()).toInt()} seconds left")
                PixelButton("Not now", { running = false; cancelled = true }, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}
