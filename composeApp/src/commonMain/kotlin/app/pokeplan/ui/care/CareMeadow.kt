package app.pokeplan.ui.care

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.care.CareAction
import app.pokeplan.care.CarePlay
import app.pokeplan.care.PlayActivity
import app.pokeplan.care.PlayReaction
import app.pokeplan.care.Visitors
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.screens.PixelDropdown
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.VisitorScene
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

/**
 * The Meadow (docs/pokelife `MODULES/CARE.md` section 5): one fixed garden scene that fits the screen. The buddy stands at the centre, up to
 * four visitors at four fixed spots around it, and nobody roams. Tap is a light reaction with a short cooldown (a tap during it changes nothing
 * and plays no cry); long press, or the "Details" action TalkBack offers, opens a compact panel with the Pokémon's identity, what Care Play
 * found it likes and its activities. The controls are minimal: the visitor queue and the Care Egg progress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MeadowView(model: AppModel, goPlay: (String) -> Unit) {
    val care = model.care
    androidx.compose.runtime.LaunchedEffect(care.today) { care.arrive() }
    val buddy = model.store.careBuddy ?: model.data.player.buddy
    val meadow = Visitors.meadow(model.careData.visitors)
    val waiting = Visitors.queue(model.careData.visitors).size
    val reunion = model.careData.reunion
    var panel by remember { mutableStateOf<String?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    // A visitor is visited or played with right here in the Meadow (the visit scene), never sent to Care Play, which is for Pokémon you own.
    var playing by remember { mutableStateOf<Int?>(null) }
    val visit = model.careData.visit
    val cooldown = remember { mutableStateOf(mapOf<String, Long>()) }
    val calm = model.data.settings.reduceMotion

    fun tap(key: String, speciesId: Int) {
        val last = cooldown.value[key] ?: 0L
        val t = model.store.nowMillis
        if (t - last < TAP_COOLDOWN_MS) return // a tap during the cooldown changes nothing and plays no cry
        cooldown.value = cooldown.value + (key to t)
        model.haptic(); model.cry(speciesId)
    }

    // The four fixed spots for visitors, as shares of the scene (x, y of the sprite's bottom centre); the buddy has the centre.
    val spots = listOf(0.17f to 0.70f, 0.83f to 0.70f, 0.30f to 0.97f, 0.70f to 0.97f)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).clip(PixelShape).border(Sizes.Hairline, Pixel.Border, PixelShape)) {
            SceneImage(Module.CARE.look.scene, Modifier.matchParentSize(), 0f)
            @Composable
            fun Resident(id: Int, shiny: Boolean, name: String, key: String, speciesId: Int, x: Float, y: Float, size: Int) {
                val w = this@BoxWithConstraints.maxWidth
                val h = this@BoxWithConstraints.maxHeight
                AnimatedSprite(
                    id, shiny, name,
                    Modifier.align(Alignment.TopStart).padding(start = w * x - (size / 2).dp, top = h * y - size.dp)
                        .combinedClickable(
                            onClickLabel = "Hear $name", onLongClickLabel = "Details of $name",
                            onClick = { tap(key, speciesId) }, onLongClick = { panel = key },
                            role = Role.Button,
                        )
                        .semantics { customActions = listOf(CustomAccessibilityAction("Details of $name") { panel = key; true }) },
                    size = size.dp, animate = !calm,
                )
            }
            if (buddy != null) Resident(buddy.form.id, buddy.isShiny, buddy.nickname, buddy.id, buddy.speciesId, 0.5f, 0.90f, 144)
            meadow.zip(spots).forEach { (v, p) ->
                Resident(Dex.form(v.formIdentifier)?.id ?: Dex.defaultForm(v.speciesId).id, false, Dex.species(v.speciesId).name, v.id, v.speciesId, p.first, p.second, 72)
            }
            // The queue and the Care Egg stay small in the corners.
            Text(
                if (waiting == 0) "No one waiting" else "$waiting waiting",
                style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S).clip(PixelShape).background(Pixel.Scrim.copy(alpha = 0.5f)).padding(horizontal = Spacing.S, vertical = 2.dp)
                    .semantics { contentDescription = if (waiting == 0) "No visitors waiting" else "$waiting visitors waiting for a place" },
            )
            val points = model.store.careEggMeter()
            Text(
                "Care Egg $points/${app.pokeplan.core.reward.RewardEngine.CARE_EGG_METER_SIZE}",
                style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S).clip(PixelShape).background(Pixel.Scrim.copy(alpha = 0.5f)).padding(horizontal = Spacing.S, vertical = 2.dp),
            )
        }
        reunion?.let { r ->
            val name = Dex.species(r.friend.speciesId).name
            TextBox {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedSprite(Dex.form(r.friend.formIdentifier)?.id ?: Dex.defaultForm(r.friend.speciesId).id, false, name, Modifier.size(Sizes.SpriteS), size = Sizes.SpriteS, animate = !calm)
                    Column(Modifier.weight(1f)) {
                        Text("$name came back to say hello.", style = MaterialTheme.typography.bodyLarge)
                    }
                    PixelButton("Say hello", { line = model.care.greetReunion(); model.cry(r.friend.speciesId) }, color = Pixel.Tone, compact = true)
                }
            }
        }
        meadow.firstOrNull { it.speciesId == (visit?.speciesId ?: playing) }?.let { open ->
            VisitorScene(model, open, visit?.takeIf { it.speciesId == open.speciesId }, { line = it }) { playing = null }
        }
        line?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        // The compact Pokémon panel (long press, or Details from the accessibility actions).
        val key = panel
        if (key != null) {
            val visitor = meadow.firstOrNull { it.id == key }
            val owned = model.data.player.pokemon.firstOrNull { it.id == key }
            val speciesId = visitor?.speciesId ?: owned?.speciesId
            if (speciesId != null) {
                val action: Pair<String, () -> Unit>? = when {
                    visitor == null -> "Spend time" to { goPlay(key) }
                    visit?.speciesId == visitor.speciesId -> null
                    model.care.canVisit(visitor) -> "Visit" to {
                        if (model.care.startVisit(visitor.speciesId) != null) { playing = null; line = null; panel = null; model.haptic(); model.cry(visitor.speciesId) } else line = "Not now."
                    }
                    visit == null -> "Play" to { playing = visitor.speciesId; line = null; panel = null; model.haptic() }
                    else -> null
                }
                PokemonPlayPanel(model, key, Dex.species(speciesId).name, owned?.nickname, visitor?.let { it.progress to Visitors.FRIEND_PROGRESS }, { panel = null }, action)
            }
        }
    }
}

private const val TAP_COOLDOWN_MS = 900L

/** Identity, what Care Play found out (only what it has shown), and the way into the activities. */
@Composable
private fun PokemonPlayPanel(model: AppModel, key: String, species: String, nickname: String?, warmth: Pair<Int, Int>?, onClose: () -> Unit, action: Pair<String, () -> Unit>?) {
    val known = model.care.discoveredLikes(key)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(if (nickname != null && nickname != species) "$nickname, $species" else species)
            if (warmth != null) Text("Warmth ${warmth.first} of ${warmth.second}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(
                if (known.isEmpty()) "Likes: unknown yet"
                else "Likes: " + known.joinToString(", ") { it.playName() },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (action != null) PixelButton(action.first, action.second, color = Pixel.Tone, compact = true)
                PixelButton("Close", onClose, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

private fun CareAction.playName() = when (this) {
    CareAction.WORDS -> "encouragement"
    CareAction.TIME -> "toys and quiet time"
    CareAction.GIFT -> "treats"
    CareAction.SERVICE -> "grooming"
    CareAction.TOUCH -> "a gentle touch"
}

// ----- Care Play ----------------------------------------------------------------------------------------------------------

/**
 * Care Play (CARE.md section 4): time with the buddy, a visitor or any Pokémon invited from the collection (this changes nobody's party). Pick
 * an activity, suggested, from the browser or "Surprise me"; each is a short run of light taps that can be left at any time. Nothing is a
 * wrong answer, nothing is owed, and the first time with each Pokémon on a day pays through the shared world.
 */
@Composable
internal fun PlayView(model: AppModel, initial: String?) {
    val care = model.care
    val all = model.data.player.pokemon
    var chosen by remember { mutableStateOf(initial ?: model.store.careBuddy?.id ?: model.data.player.buddyId ?: all.firstOrNull()?.id) }
    var activity by remember { mutableStateOf<PlayActivity?>(null) }
    var taps by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }
    var invited by remember { mutableStateOf(24) }
    val pokemon = all.firstOrNull { it.id == chosen }
    if (pokemon == null) { EmptyState("No one to spend time with yet", action = "Start a focus session" to { model.open(Place.FOCUS, true) }); return }
    val name = pokemon.nickname
    val known = care.discoveredLikes(pokemon.id)
    fun finish(done: PlayActivity) {
        val (reaction, paid) = care.playWith(pokemon.id, done)
        result = "$name ${reaction.line}" + if (paid != null && paid.isNotEmpty()) " A little something came of it." else ""
        model.cry(pokemon.speciesId); activity = null; taps = 0
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                AnimatedSprite(pokemon.form.id, pokemon.isShiny, name, Modifier.size(Sizes.SpriteM), size = Sizes.SpriteM, animate = !model.data.settings.reduceMotion)
                Column(Modifier.weight(1f)) {
                    PixelTitle(name)
                    if (known.isNotEmpty()) Text("Likes: " + known.joinToString(", ") { it.playName() }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                PixelButton(if (inviting) "Done" else "Someone else", { inviting = !inviting }, color = Pixel.InkSoft, compact = true)
            }
            if (inviting) {
                SectionLabel("Invite from your collection")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    val buddyId = model.data.player.buddyId
                    // Anyone you own, the buddy first, then by friendship; "Show more" reaches the rest (a visitor is visited in the Meadow, not here).
                    val ordered = (listOfNotNull(all.firstOrNull { it.id == buddyId }) + all.sortedByDescending { it.friendship }).distinctBy { it.id }
                    PixelDropdown(
                        ordered.take(invited), ordered.firstOrNull { it.id == chosen }, { it?.nickname ?: "" },
                        { p -> if (p != null) { chosen = p.id; inviting = false; activity = null; result = null } },
                        Modifier.fillMaxWidth(),
                    )
                    if (ordered.size > invited) PixelButton("Show more", { invited += 24 }, color = Pixel.InkSoft, compact = true)
                }
            }
            val running = activity
            if (running == null) {
                val suggested = care.suggestion(pokemon.id)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelDropdown(
                        PlayActivity.entries.toList(), suggested, { it?.label ?: "" },
                        { a -> if (a != null) { activity = a; taps = 0; result = null } },
                        Modifier.fillMaxWidth(),
                    )
                    PixelButton("Surprise me", { activity = care.surprise(pokemon.id); taps = 0; result = null }, color = Pixel.Tone, compact = true)
                }
            } else {
                Text("${running.label}: ${running.verb}", style = MaterialTheme.typography.bodyLarge)
                PixelBar(taps.toFloat() / running.taps, color = Pixel.Tone, label = "${running.label}: $taps of ${running.taps}")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelButton(running.verb, { model.haptic(); taps++; if (taps >= running.taps) finish(running) }, color = Pixel.Tone)
                    PixelButton("Stop for now", { activity = null; taps = 0 }, color = Pixel.InkSoft, compact = true)
                }
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}
