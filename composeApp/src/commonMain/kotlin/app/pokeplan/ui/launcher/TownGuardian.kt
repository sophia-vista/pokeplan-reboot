package app.pokeplan.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.StatusKind
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The one Guardian the town shows. A player can have up to six (one per module), and six legendaries in a town of wandering Pokémon is a crowd: the buddy
 * if it is a Guardian, else the Guardian of the highest level (then the smallest id, so the pick never flickers). The others are still Guardians, in
 * their modules; they just do not walk the town.
 */
internal fun shownGuardian(all: List<OwnedPokemon>, guardianIds: Collection<String>, buddyId: String?): OwnedPokemon? {
    val guardians = all.filter { it.id in guardianIds }
    return guardians.firstOrNull { it.id == buddyId } ?: guardians.sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenBy { it.id }).firstOrNull()
}

/**
 * The weather a Guardian brings when the town is set to "Guardian's weather", by species: a Guardian of the sea brings rain, one of ice brings snow or a
 * blizzard, one of the earth a sandstorm, one of the sky cloud shadows. A Guardian without a weather of its own brings none (a clear sky).
 */
internal fun guardianWeather(speciesIdentifier: String?): Weather? = when (speciesIdentifier) {
    "kyogre", "suicune", "thundurus", "zapdos" -> Weather.RAIN
    "articuno", "glastrier" -> Weather.SNOW
    "kyurem", "regice" -> Weather.BLIZZARD
    "regirock", "landorus" -> Weather.SANDSTORM
    "lugia", "rayquaza", "tornadus", "cresselia", "enamorus" -> Weather.CLOUDS
    else -> null
}

/** The weather the town shows for [guardian] under "Guardian's weather". */
internal fun guardianWeather(guardian: OwnedPokemon?): Weather = guardianWeather(guardian?.let { Dex.speciesOrNull(it.speciesId)?.identifier }) ?: Weather.CLEAR

/**
 * The others notice the Guardian. When a Pokémon walks close to it, the Pokémon stops, turns towards it and is startled (the alert mark and a hop), and the
 * Guardian answers with a sparkle; then both go on. Each Pokémon does this once in a while, not every pass, and nothing happens in Reduce motion or while the
 * launcher is out of sight. A buddy that notices it makes the buddy's own face show it.
 */
@Composable
internal fun TownGuardianAwe(life: TownLife, residents: List<OwnedPokemon>, guardian: OwnedPokemon?, buddyId: String?, calm: Boolean, mood: BuddyMood?, home: Int) {
    if (guardian == null || calm) return
    LaunchedEffect(residents.map { it.id }, guardian.id) {
        val random = Random(guardian.id.hashCode())
        val noticed = mutableMapOf<String, TimeMark>()
        while (true) {
            delay(600)
            if (guardian.id in life.holds) continue
            val at = life.spots[guardian.id]?.invoke() ?: continue
            if (at.x !in (home + 0.05f)..(home + 0.95f)) continue
            val near = residents.firstOrNull { other ->
                if (other.id == guardian.id || other.id in life.holds) return@firstOrNull false
                val p = life.spots[other.id]?.invoke() ?: return@firstOrNull false
                kotlin.math.abs(p.x - at.x) < AWE_RANGE_X && kotlin.math.abs(p.y - at.y) < AWE_RANGE_Y &&
                    noticed[other.id]?.let { it.elapsedNow() < 150.seconds } != true && random.nextInt(10) < 6
            } ?: continue
            noticed[near.id] = TimeSource.Monotonic.markNow()
            val p = life.spots[near.id]?.invoke() ?: continue
            val guardianIsLeft = at.x < p.x
            // The holds are taken off in a finally: this effect is cancelled when the town stops being calm (the page is swiped away, the launcher goes out of sight),
            // and a hold left behind would keep the Guardian and the Pokémon it met standing for good.
            try {
                life.holds[near.id] = Hold(StatusKind.ALERT, hop = 1, faceLeft = guardianIsLeft)
                life.holds[guardian.id] = Hold(StatusKind.TWINKLE, faceLeft = !guardianIsLeft)
                if (near.id == buddyId) mood?.meet(guardian, Emotion.NORMAL, Emotion.SURPRISED)
                delay(1600)
            } finally {
                life.holds.remove(near.id)
                life.holds.remove(guardian.id)
            }
        }
    }
}

private const val AWE_RANGE_X = 0.14f
private const val AWE_RANGE_Y = 0.14f
