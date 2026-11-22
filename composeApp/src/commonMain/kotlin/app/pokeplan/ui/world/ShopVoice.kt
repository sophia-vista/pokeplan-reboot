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
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.screens.short

internal object ShopLines {
    const val KECLEON_WELCOME = "Welcome to the Kecleon Shop! ♪"
    const val KECLEON_BOUGHT = "Thank you! All yours! ♪"
    const val KECLEON_BROKE = "What? You don't seem to have the money." // copy-ok: game
    const val KECLEON_FAREWELL = "Thanks! Please come again! ♪"

    const val SPINDA_WELCOME = "Welcome to the fresh and friendly Spinda's Juice Bar!" // copy-ok: game
    const val SPINDA_BREWED = "I've got a good feeling about this drink!" // copy-ok: game
    const val SPINDA_NO_FOOD = "I'm sorry to say this, but in this café you must provide your own ingredients. You don't appear to have any food items." // copy-ok: game
    const val SPINDA_FAREWELL = "Have a great day."

    const val SWAP_WELCOME = "Hello! Welcome to the Swap Shop, where everyone is happy! Hey, why not?" // copy-ok: game
    const val SWAP_DONE = "Thanks for swapping! Come back anytime!"
    const val SWAP_SHORT = "You don't have enough items to trade for this!" // copy-ok: game

    fun itemText(name: String): String? = when (name) {
        "Protein" -> "A drink that permanently raises the Pokémon's Attack. It also slightly fills the Pokémon's Belly." // copy-ok: game
        "Calcium" -> "A drink that permanently raises the Pokémon's Special Attack. It also slightly fills the Pokémon's Belly." // copy-ok: game
        "Iron" -> "A drink that permanently raises the Pokémon's Defense. It also slightly fills the Pokémon's Belly." // copy-ok: game
        "Zinc" -> "A drink that permanently raises the Pokémon's Special Defense. It also slightly fills the Pokémon's Belly." // copy-ok: game
        else -> null
    }
}

@Composable
internal fun ShopVoice(speciesId: Int, name: String, line: String, emotion: Emotion = Emotion.NORMAL) {
    TextBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Portrait(speciesId, false, name, emotion = emotion, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

internal object Keepers {
    const val KECLEON = 352
    const val SPINDA = 327
    const val CROAGUNK = 453
}
