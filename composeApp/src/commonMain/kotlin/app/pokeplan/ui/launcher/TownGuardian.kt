package app.pokeplan.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.StatusKind
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal fun shownGuardian(all: List<OwnedPokemon>, guardianIds: Collection<String>, buddyId: String?, rotation: Int = 0): OwnedPokemon? {
    val guardians = all.filter { it.id in guardianIds }
    guardians.firstOrNull { it.id == buddyId }?.let { return it }
    val order = guardians.sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenBy { it.id })
    return if (order.isEmpty()) null else order[rotation.mod(order.size)]
}

internal fun guardianWeather(speciesIdentifier: String?): Weather? = when (speciesIdentifier) {
    "kyogre", "suicune", "thundurus", "zapdos" -> Weather.RAIN
    "articuno", "glastrier" -> Weather.SNOW
    "kyurem", "regice" -> Weather.BLIZZARD
    "regirock", "landorus" -> Weather.SANDSTORM
    "lugia", "rayquaza", "tornadus", "cresselia", "enamorus" -> Weather.CLOUDS
    else -> null
}

internal fun guardianWeather(guardian: OwnedPokemon?): Weather = guardianWeather(guardian?.let { Dex.speciesOrNull(it.speciesId)?.identifier }) ?: Weather.CLEAR

@Composable
internal fun TownGuardianAwe(life: TownLife, residents: List<OwnedPokemon>, guardian: OwnedPokemon?, buddyId: String?, calm: Boolean, mood: BuddyMood?, home: Int) {
    if (guardian == null || calm) return
    LaunchedEffect(residents.map { it.id }, guardian.id) {
        val random = Random(guardian.id.hashCode())
        val glanced = mutableMapOf<String, TimeMark>()
        var nextAwe = TimeSource.Monotonic.markNow() + (60 + random.nextInt(60)).seconds
        while (true) {
            delay(700)
            val at = life.spots[guardian.id]?.invoke() ?: continue
            if (at.x !in (home + 0.05f)..(home + 0.95f)) continue
            val near = residents.filter { other ->
                if (other.id == guardian.id || other.id in life.holds) return@filter false
                life.near(guardian.id, other.id, AWE_TILES, AWE_RANGE_X, AWE_RANGE_Y)
            }
            if (near.isEmpty()) continue
            val looker = near.firstOrNull { glanced[it.id]?.let { t -> t.elapsedNow() < 90.seconds } != true && random.nextInt(100) < 18 }
            if (looker != null && (guardian.id in life.holds || !nextAwe.hasPassedNow() || random.nextInt(100) < 70)) {
                glanced[looker.id] = TimeSource.Monotonic.markNow()
                val p = life.spots[looker.id]?.invoke() ?: continue
                try {
                    life.holds[looker.id] = Hold(null, faceLeft = at.x < p.x)
                    delay(1300)
                } finally {
                    life.holds.remove(looker.id)
                }
                continue
            }
            if (!nextAwe.hasPassedNow() || guardian.id in life.holds || random.nextInt(100) >= 35) continue
            val startled = near.random(random)
            val p = life.spots[startled.id]?.invoke() ?: continue
            nextAwe = TimeSource.Monotonic.markNow() + (AWE_COOLDOWN_MIN + random.nextInt(AWE_COOLDOWN_MAX - AWE_COOLDOWN_MIN)).seconds
            val guardianIsLeft = at.x < p.x
            try {
                life.holds[startled.id] = Hold(StatusKind.ALERT, hop = 1, faceLeft = guardianIsLeft)
                life.holds[guardian.id] = Hold(StatusKind.TWINKLE, faceLeft = !guardianIsLeft)
                if (startled.id == buddyId) mood?.meet(guardian, Emotion.NORMAL, Emotion.SURPRISED)
                delay(1600)
            } finally {
                life.holds.remove(startled.id)
                life.holds.remove(guardian.id)
            }
        }
    }
}

private const val AWE_TILES = 3
private const val AWE_RANGE_X = 0.14f
private const val AWE_RANGE_Y = 0.14f
private const val AWE_COOLDOWN_MIN = 150
private const val AWE_COOLDOWN_MAX = 300

@Composable
internal fun TownWeatherReactions(model: AppModel, life: TownLife, residents: List<OwnedPokemon>, weather: Weather, enabled: Boolean) {
    if (!enabled || weather == Weather.CLEAR || residents.isEmpty()) return
    LaunchedEffect(residents.map { it.id }, weather) {
        val random = Random(residents.hashCode() + weather.ordinal)
        while (true) {
            delay(9_000L + random.nextLong(14_000))
            val dark = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
            val feeling = residents.filter { it.id !in life.holds && !TownBehavior.asleep(it, dark) }
                .mapNotNull { m -> TownBehavior.feeling(m.form.types, weather)?.let { m to it } }
            if (feeling.isEmpty()) continue
            val (pick, how) = feeling.random(random)
            try {
                life.holds[pick.id] = if (how == Feeling.ENJOYS) Hold(StatusKind.TWINKLE, hop = 1) else Hold(StatusKind.SWEAT)
                delay(1_500)
            } finally {
                life.holds.remove(pick.id)
            }
        }
    }
}
