package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun OutbreakPanel(model: AppModel) {
    val pending = model.data.outbreaks
    if (pending.isEmpty()) return
    pending.forEach { outbreak ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelTitle("Outbreak: choose one")
                if (outbreak.shinyRolls > 1) Text("Shiny chance ×${outbreak.shinyRolls}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
                    outbreak.options.forEach { option ->
                        val species = Dex.species(option.speciesId)
                        val form = Dex.form(option.formIdentifier)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            if (form != null) AnimatedSprite(form.id, false, species.name, size = Sizes.SpriteM, animate = !model.data.settings.reduceMotion, ground = 4.dp)
                            Text(species.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            PixelButton("Catch", {
                                if (model.store.claimOutbreak(outbreak.id, option.speciesId) != null) { model.effect(Sfx.ITEM_GET); model.haptic() }
                            }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
                        }
                    }
                }
            }
        }
    }
}
