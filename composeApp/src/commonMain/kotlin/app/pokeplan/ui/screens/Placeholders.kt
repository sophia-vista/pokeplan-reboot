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
import app.pokeplan.ui.theme.TextBox

@Composable
private fun Soon(title: String, line: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        TextBox {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(line, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PlannerScreen(model: AppModel) = Soon("Planner", "Tasks, calendar and habits are coming next.")

@Composable
fun FocusScreen(model: AppModel) = Soon("Focus", "The focus timer is coming next.")

@Composable
fun CollectionScreen(model: AppModel) = Soon("Pokémon", "Your party, PC and Pokédex are coming next.")

@Composable
fun MeScreen(model: AppModel) = Soon("Me", "Your trainer card and settings are coming next.")
