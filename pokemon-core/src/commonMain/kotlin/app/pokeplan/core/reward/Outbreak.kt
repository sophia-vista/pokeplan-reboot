package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import kotlinx.serialization.Serializable

@Serializable
data class Outbreak(
    val id: String,
    val region: Region,
    val options: List<OutbreakOption>,
    val shinyRolls: Int,
    val atMillis: Long,
)

@Serializable
data class OutbreakOption(val speciesId: Int, val formIdentifier: String)
