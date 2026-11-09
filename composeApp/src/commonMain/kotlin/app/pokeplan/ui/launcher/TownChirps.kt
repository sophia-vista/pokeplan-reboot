package app.pokeplan.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.CryVoice
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Now and then one of the Pokémon in the town chirps, like the ones that walk about in the newest games: its own cry from the game, said quietly and a little
 * short, with a small hop. Not often (one every half a minute to a minute and a half, never two at once), only while the town is in sight and on Home, never
 * in Reduce motion, in the battery saver, during a call or when Sound is off, and not from one that is asleep for the night. The buddy chirps more
 * often and the Guardian more rarely and lower, as a big one would. A tap still gives the full cry.
 */
@Composable
internal fun TownChirps(model: AppModel, life: TownLife, residents: List<OwnedPokemon>, guardianId: String?, buddyId: String?, enabled: Boolean) {
    if (!enabled || residents.isEmpty()) return
    LaunchedEffect(residents.map { it.id }) {
        val random = Random(residents.hashCode())
        while (true) {
            delay(CHIRP_MIN_MILLIS + random.nextLong(CHIRP_SPREAD_MILLIS))
            if (model.phone?.inCall == true || model.phone?.screenOff == true || model.data.settings.criesLevel <= 0f) continue
            val night = BuddyScene.darkness(model.store.now.hour, model.store.now.minute) >= NIGHT
            val awake = residents.filter { it.id !in life.holds && !(night && sleepsAtNight(it)) }
            if (awake.isEmpty()) continue
            // The buddy is picked twice as often as another Pokémon and the Guardian half as often.
            fun weight(m: OwnedPokemon) = when (m.id) { buddyId -> 4; guardianId -> 1; else -> 2 }
            var roll = random.nextInt(awake.sumOf(::weight))
            val pick = awake.first { m -> roll -= weight(m); roll < 0 }
            val big = pick.id == guardianId
            model.cry(pick.speciesId, CryVoice.Voice(pitch = if (big) 0.9f else 1.05f, speed = if (big) 0.9f else 1.1f, gain = if (big) CHIRP_GAIN_BIG else CHIRP_GAIN))
            // Taken off in a finally: the effect is cancelled when the town stops being calm, and a hold left behind would keep this Pokémon standing for good.
            try {
                life.holds[pick.id] = Hold(null, hop = 1)
                delay(CHIRP_HOLD_MILLIS)
            } finally {
                life.holds.remove(pick.id)
            }
        }
    }
}

private const val CHIRP_MIN_MILLIS = 30_000L
private const val CHIRP_SPREAD_MILLIS = 60_000L
private const val CHIRP_HOLD_MILLIS = 700L
private const val CHIRP_GAIN = 0.5f
private const val CHIRP_GAIN_BIG = 0.6f
