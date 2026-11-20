package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import kotlinx.serialization.Serializable

/**
 * An outbreak (docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` 10.0, owner, 20 November 2026): a rare action in a module opens a short list of Pokémon to
 * choose from, each caught directly (no egg), with [shinyRolls] times the ordinary shiny chance. It waits in `AppData.outbreaks` until the player picks one.
 * It holds no figure, name or note of the action that opened it.
 */
@Serializable
data class Outbreak(
    val id: String,
    val region: Region,
    val options: List<OutbreakOption>,
    /** How many times the ordinary shiny chance the caught Pokémon gets (1 is the ordinary odds). */
    val shinyRolls: Int,
    val atMillis: Long,
)

/** One Pokémon an outbreak offers: a species and the form to show. */
@Serializable
data class OutbreakOption(val speciesId: Int, val formIdentifier: String)
