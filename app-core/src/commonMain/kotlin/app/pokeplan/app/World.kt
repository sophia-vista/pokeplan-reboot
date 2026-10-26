package app.pokeplan.app

import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.Reward

/**
 * The shared world as a module sees it: a module sends its effort events and gets the rewards back, and never touches
 * the Pokémon, the bag or the rank itself (docs/ecosystem.md, section 2). [GameStore] is the world on this device.
 */
interface World {
    /** Pays a Care moment; null when nothing was paid (not started yet, or today's share is used up). */
    fun care(event: CareEvent): List<Reward>?
}
