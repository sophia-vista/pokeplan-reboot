package app.pokeplan.ui.screens

import androidx.compose.runtime.Composable
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.app.BuddyEventNow
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel

/**
 * A module's lead in the module's own scene, at the top of its page, the same window as Care's meadow: the lead idles, a tap pets it
 * (friendship, with the day's cap shared by all), its level and progress sit small in the corner, and it celebrates what its module
 * paid ([event]; null for none). Nothing shows until the module has a lead, so a new player sees what to do first; the party panel
 * below says how to get one. The global buddy never appears here (it lives on Home), and there is no berry button: feeding belongs to
 * the buddy and Care's meadow.
 */
@Composable
internal fun ModuleLeadStage(model: AppModel, module: Module, lead: OwnedPokemon?, event: BuddyEventNow?, resting: Boolean = false, openPokemon: (String) -> Unit) {
    val pokemon = lead ?: return
    val store = model.store
    BuddyStage(
        buddy = pokemon, scene = module.look.scene, darkness = BuddyScene.darkness(store.now.hour, store.now.minute),
        progress = store.levelProgress(pokemon), event = event,
        calm = model.data.settings.reduceMotion, resting = resting, berry = null,
        onPet = { avoid -> store.pet(pokemon.id); model.cry(pokemon); BuddyReaction.pet(pokemon, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> BuddyReaction.feed(pokemon, avoid = avoid) },
        onOpen = { openPokemon(pokemon.id) }, onHaptic = { model.haptic() }, onEvent = { model.cry(pokemon) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
    )
}
