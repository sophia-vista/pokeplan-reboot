package app.pokeplan.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.pokeplan.app.GameStore
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.NpcPortrait
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.GameBox
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

private enum class Step { GREETING, NAME, CONFIRM_NAME, STARTER, CONFIRM_STARTER, FAREWELL }

/** What the Professor says before asking for a name. Written for this app, in the cadence of the games' intro. */
private val greeting = listOf(
    "Hello there! Welcome to the world of PokéPlan!",
    "I am Professor Oak. People around here just call me the Professor.",
    "Pokémon live alongside people everywhere. And they love to help.",
    "Finish your tasks and keep your focus, and they will grow beside you.",
    "Eggs will hatch. Friends will join your team. It all starts with one small step.",
    "But first, tell me a little about yourself.",
)

/** An intro in the style of the old games: a Professor, a text box that types itself out, a name, a first partner. */
@Composable
fun OnboardingScreen(store: GameStore, startStep: Int = 0) {
    var step by remember { mutableStateOf(Step.entries[startStep]) }
    var line by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(if (startStep > 1) "Sophia" else "") }
    var starter by remember { mutableStateOf(GameStore.starters.first()) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        // The upper part: the Professor, or the Pokémon being considered.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (step) {
                Step.STARTER -> StarterPicker(starter) { starter = it }
                Step.CONFIRM_STARTER -> Sprite(Dex.defaultForm(starter).id, false, Dex.species(starter).name, size = 240.dp)
                else -> NpcPortrait("oak", size = 280.dp)
            }
        }

        when (step) {
            Step.GREETING -> Dialogue(greeting[line]) {
                if (line < greeting.lastIndex) line++ else step = Step.NAME
            }
            Step.NAME -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dialogue("What is your name?") {}
                TextBox {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            name, { name = it.take(24) }, Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text("Your name", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
                        )
                        PixelButton("OK", { step = Step.CONFIRM_NAME }, color = Pixel.Green, enabled = name.isNotBlank())
                    }
                }
            }
            Step.CONFIRM_NAME -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dialogue("So your name is ${name.trim()}. Is that right?") {}
                YesNo(onYes = { line = 0; step = Step.STARTER }, onNo = { step = Step.NAME })
            }
            Step.STARTER -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dialogue("Now, ${name.trim()}, choose your first partner.") {}
                PixelButton("Choose ${Dex.species(starter).name}", { step = Step.CONFIRM_STARTER }, Modifier.fillMaxWidth(), Pixel.Red)
            }
            Step.CONFIRM_STARTER -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dialogue("So, you want ${Dex.species(starter).name}? It will be yours.") {}
                YesNo(onYes = { step = Step.FAREWELL }, onNo = { step = Step.STARTER })
            }
            Step.FAREWELL -> Dialogue("Wonderful! Your first task is waiting. Finish it, and your adventure begins!") {
                store.start(name, starter)
                store.markVersionSeen(AppVersion.NAME)
            }
        }
    }
}

@Composable
private fun YesNo(onYes: () -> Unit, onNo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PixelButton("Yes", onYes, Modifier.weight(1f), Pixel.Green)
        PixelButton("No", onNo, Modifier.weight(1f), Pixel.InkSoft)
    }
}

/** All the starters, by game, as tappable sprites under a big preview of the one picked. */
@Composable
private fun StarterPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The pick stays on show; only the list of starters scrolls.
        Sprite(Dex.defaultForm(selected).id, false, Dex.species(selected).name, size = 128.dp)
        Text(Dex.species(selected).name, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GameStore.starterGroups.forEach { (game, ids) ->
            Text(game, style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ids.forEach { id ->
                    val chosen = id == selected
                    Box(
                        Modifier.background(if (chosen) Pixel.Yellow else Pixel.Panel, RoundedCornerShape(12.dp))
                            .clickable { onSelect(id) }
                            .padding(4.dp),
                    ) { Sprite(Dex.defaultForm(id).id, false, Dex.species(id).name, size = 56.dp) }
                }
            }
        }
        }
    }
}


/**
 * A text box that types its line out. Tap while it types to show the rest; tap when it is complete to go on.
 * A small Poké Ball blinks at the corner when there is more.
 */
@Composable
private fun Dialogue(text: String, onNext: () -> Unit) {
    var shown by remember(text) { mutableStateOf(0) }
    val done = shown >= text.length
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(28)
            shown++
        }
    }
    val blink = rememberInfiniteTransition().animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse)).value
    GameBox(Modifier.clickable { if (!done) shown = text.length else onNext() }) {
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(text.take(shown), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(end = 28.dp, bottom = 12.dp))
            if (done) ItemIcon("poke-ball", Modifier.align(Alignment.BottomEnd).alpha(blink), size = 24.dp)
        }
    }
}
