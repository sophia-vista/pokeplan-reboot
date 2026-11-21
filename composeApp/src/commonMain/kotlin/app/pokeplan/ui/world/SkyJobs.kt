package app.pokeplan.ui.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.world.MissionDef

internal object SkyJobs {
    val HELP_CALLS = listOf(
        "Help!", "I can't get out!", "I went exploring, but...", "I'm scared!", "I can't seem to get out.", "I was attacked!",
        "I'm too exhausted to continue!", "Where am I?", "I'm sad and lonely!", "I can't get home!", "Help me!", "I've lost my way!",
    )

    fun helpCall(m: MissionDef): String = HELP_CALLS[m.id.fold(7) { h, c -> h * 31 + c.code }.mod(HELP_CALLS.size)]
}

@Composable
internal fun RescueCall(m: MissionDef) {
    val species = m.rescued ?: return
    val name = Dex.species(species).name
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Portrait(Dex.defaultForm(species).id, false, name, emotion = Emotion.TEARY, size = 64.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(SkyJobs.helpCall(m), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
