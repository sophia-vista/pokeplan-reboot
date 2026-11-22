package app.pokeplan.ui.screens

import androidx.compose.runtime.Composable
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.app.BuddyEventNow
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel

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
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it }, compact = true,
    )
}
