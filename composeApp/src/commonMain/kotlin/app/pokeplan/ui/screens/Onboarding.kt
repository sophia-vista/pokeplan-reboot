package app.pokeplan.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.app.GameStore
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox

/** Title, then pick a starter and a name. The first reward arrives within about two minutes of opening the app. */
@Composable
fun OnboardingScreen(store: GameStore) {
    var step by remember { mutableStateOf(0) }
    var starter by remember { mutableStateOf(GameStore.starters.first()) }
    var name by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextBox(Modifier.padding(top = 24.dp), accent = Pixel.Red) {
            Text("PokéPlan", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, color = Pixel.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (step == 0) {
            TextBox(Modifier.fillMaxWidth()) {
                Text(
                    "Plan your day, focus, and grow a team of Pokémon along the way. Every task and every focus session " +
                        "earns something.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            PixelButton("Start as guest", { step = 1 }, Modifier.fillMaxWidth(), Pixel.Red)
        } else {
            Text("Choose your first partner", style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameStore.starters.forEach { id ->
                    val form = Dex.defaultForm(id)
                    val chosen = id == starter
                    Box(
                        Modifier.padding(4.dp)
                            .border(if (chosen) 4.dp else 2.dp, if (chosen) Pixel.Red else Pixel.Shade, RoundedCornerShape(4.dp))
                            .clickable { starter = id }
                            .padding(6.dp),
                    ) { Sprite(form.id, false, Dex.species(id).name, size = 72.dp) }
                }
            }
            TextBox(Modifier.fillMaxWidth()) {
                Text("You chose ${Dex.species(starter).name}!", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedTextField(
                name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Your trainer name") },
            )
            PixelButton("Begin", { store.start(name, starter) }, Modifier.fillMaxWidth(), Pixel.Red)
        }
    }
}
