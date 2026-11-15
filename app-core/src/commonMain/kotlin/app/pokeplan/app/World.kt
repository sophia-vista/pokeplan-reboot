package app.pokeplan.app

import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.Reward

/**
 * The shared world as a module sees it: a module sends its effort events and gets the rewards back, and never touches
 * the Pokémon, the bag or the rank itself (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md). [GameStore] is the world on this device.
 */
interface World {
    /** Pays a Care moment; null when nothing was paid (not started yet, or today's share is used up). */
    fun care(event: CareEvent): List<Reward>?

    /**
     * Pays a HEAL moment (Field Research) from the minimal event; null when nothing was paid (not started, already paid, or today's share is used).
     * The world learns nothing but the event's id, module, opaque reference, kind and time.
     */
    fun heal(event: HealEvent): List<Reward>? = null

    /**
     * Pays a MINT moment (Ledger Research) from the minimal event; null when nothing was paid (not started, already paid, or today's share is used).
     * The world learns nothing but the event's id, module, opaque reference, kind and time: no amount, account, merchant or note.
     */
    fun mint(event: MintEvent): List<Reward>? = null

    /**
     * Pays a HOME moment (Habitat Discovery) from the minimal event; null when nothing was paid (not started, already paid, or today's share is used).
     * The world learns nothing but the event's id, module, opaque reference, kind and time: no chore, area, supply, asset or note.
     */
    fun base(event: BaseEvent): List<Reward>? = null

    /** The Secret Base decorations the player owns (HOME.md 9.5): what Base's room may place. Empty for a store that does not know the game (a widget). */
    fun ownedDecorations(): Set<String> = emptySet()

    /**
     * Pays a LINK moment (Postcard Trail) from the minimal event; null when nothing was paid (not started, already paid, or today's share is used).
     * The world learns nothing but the event's id, module, opaque reference, kind, time and policy: no person, number, note, date or follow-up words.
     */
    fun link(event: LinkEvent): List<Reward>? = null

    /** The PLAN task a LINK follow-up was converted into (LINK.md 2.4), if one still exists: PLAN owns it, its completion and its reward. */
    fun followUpTask(followUpId: String): FollowUpTask? = null

    /** Converts a follow-up into a PLAN task by the user's choice; the same follow-up gives the same task. Null when there is no Plan to receive it. */
    fun planFollowUp(followUpId: String, title: String, planned: kotlinx.datetime.LocalDate?): FollowUpTask? = null

    /** Makes a friend of a Care visitor (one a day); null when nothing was paid (not started, or today's is used). */
    fun befriend(event: BefriendEvent): List<Reward>? = null

    /** Care's Visitor luck in percent, from the lead and party of Care's party. */
    fun visitorLuck(): Double = 0.0

    /** The berries in the shared bag (item id to count), which Care can offer a visitor as a treat. */
    fun berries(): Map<String, Int> = emptyMap()

    /** Takes one [berry] from the shared bag for a visitor's treat; false when there is none. */
    fun spendBerry(berry: String): Boolean = false
}

/** What LINK may know of the PLAN task a follow-up became: that it exists, whether it is done and when. Never the task's other fields. */
data class FollowUpTask(val taskId: String, val done: Boolean, val doneAtMillis: Long? = null)
