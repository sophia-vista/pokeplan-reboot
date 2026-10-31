package app.pokeplan.ui.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.Place
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Who is meeting the buddy in the town, with the face each makes. */
internal class Visitor(val pokemon: OwnedPokemon, val emotion: Emotion)

/**
 * How the buddy feels, shown only as faces on the Buddy widget (no words): its own portrait changes with what happens, and
 * the Pokémon it meets in the town shows up beside it with its own face for a few seconds. The town and a tap on the portrait set it.
 */
internal class BuddyMood {
    var emotion by mutableStateOf(Emotion.NORMAL)
        private set
    var visitor by mutableStateOf<Visitor?>(null)
        private set
    var version by mutableIntStateOf(0)
        private set

    fun feel(emotion: Emotion) { this.emotion = emotion; version++ }

    fun meet(pokemon: OwnedPokemon, theirs: Emotion, mine: Emotion) { visitor = Visitor(pokemon, theirs); emotion = mine; version++ }

    fun settle() { emotion = Emotion.NORMAL; visitor = null }
}

/** The buddy's portrait, its name and level, and, while one is near, the portrait of the Pokémon it is meeting. A tap on the portrait gets a cry and a smile; the name opens PokéLife. */
@Composable
internal fun BuddyWidget(env: WidgetEnv) {
    val data = env.model.data
    val buddy = data.player.buddy
    if (!data.onboarded || buddy == null) return
    val mood = env.mood
    // A face lasts a few seconds, then goes back to a calm one.
    LaunchedEffect(mood.version) { if (mood.version > 0) { delay(4_000); mood.settle() } }
    val density = LocalDensity.current.density
    // A face is 40 art pixels; drawn at twice the town's pixel scale it stays crisp, and the frame hugs it (no room inside but the frame's own).
    val face = (40 * 2 * env.pixel / density).dp
    // The frame is 3 art pixels of room above and below the face, so the name box is made that tall (no intrinsic sizes: the text is measured lazily).
    val frameHeight = ((40 * 2 + 2 * 3) * env.pixel / density).dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
        PmdBox(env.pixel, Modifier.clickable(role = Role.Button, onClickLabel = "Pet ${buddy.nickname}") { env.model.cry(buddy.speciesId); mood.feel(Emotion.HAPPY) }, inset = 0, compact = true) {
            Portrait(buddy.form.id, buddy.isShiny, "${buddy.nickname}, level ${buddy.level}", emotion = mood.emotion, size = face, framed = false)
        }
        PmdBox(env.pixel, Modifier.weight(1f).height(frameHeight).clickable(role = Role.Button, onClickLabel = "Open PokéLife") { env.host.openApp(Place.HOME) }, inset = 0, compact = true) {
            Column(Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(horizontal = pmdRowPad(env.pixel, density)), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PmdText(buddy.nickname, env.pixel)
                PmdText(if (data.focus != null) "Focusing" else "Lv. ${buddy.level}", env.pixel, alpha = 0.75f)
            }
        }
        mood.visitor?.let { v ->
            PmdBox(env.pixel, inset = 0, compact = true) {
                Portrait(v.pokemon.form.id, v.pokemon.isShiny, v.pokemon.nickname, emotion = v.emotion, size = face, framed = false)
            }
        }
    }
}
