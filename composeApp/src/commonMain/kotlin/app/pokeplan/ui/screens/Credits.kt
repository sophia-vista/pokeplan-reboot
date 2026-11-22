package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing

internal data class Credit(val what: String, val by: String, val terms: String)

internal val CREDITS: List<Credit> = listOf(
    Credit("Mystery Dungeon sprites and portraits", "PMD SpriteCollab: Chunsoft's sprites and the community artists who drew the rest (credits per Pokémon in the project's repository)", "CC BY-NC 4.0, non-commercial"),
    Credit("Pokémon sprites, item icons, badges, cries", "PokeAPI sprites and cries", "Game Freak's art and audio, private use"),
    Credit("Scenes and battle backdrops", "Pokémon Showdown client", "Game Freak's art, private use"),
    Credit("Maps, Hoenn Secret Base art and tilesets", "pret decompilations (pokeemerald, pokefirered, pokeplatinum)", "Game Freak's art, private use"),
    Credit("Mystery Dungeon maps, tiles, items, effects and sounds", "Pokémon Mystery Dungeon: Red Rescue Team and Explorers of Sky (Chunsoft), item sheet ripped by redblueyellow", "Private use"),
    Credit("PC box wallpapers", "PKHeX", "Game Freak's art, private use"),
    Credit("Dialogue font", "Ripped by John2k4", "Game Freak's art, private use"),
    Credit("Display font and sound effects", "hexparrot and itsjavi pokemon-assets", "MIT for the package, game files private use"),
    Credit("Control glyphs", "Pixelarticons by Gerrit Halfmann", "MIT"),
)

@Composable
internal fun CreditsList() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        CREDITS.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(c.what, style = MaterialTheme.typography.bodyLarge)
                Text(c.by, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Text(c.terms, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
}
