package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

@Composable
private fun Soon(title: String, line: String) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        ScreenHeader(title)
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            TextBox { Text(line, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
fun CollectionScreen(model: AppModel) = Soon("Pokémon", "Your party, PC and Pokédex are coming next.")

@Composable
fun MeScreen(model: AppModel) = Soon("Me", "Your trainer card and settings are coming next.")
