package app.pokeplan.app

import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.SafariEvent

data class SafariDay(val visited: Boolean, val left: Int)

interface World {
    fun care(event: CareEvent): List<Reward>?

    fun heal(event: HealEvent): List<Reward>? = null

    fun mint(event: MintEvent): List<Reward>? = null

    fun base(event: BaseEvent): List<Reward>? = null

    fun ownedDecorations(): Set<String> = emptySet()

    fun link(event: LinkEvent): List<Reward>? = null

    fun safari(event: SafariEvent): List<Reward>? = null

    fun safariDay(eventId: String): SafariDay = SafariDay(false, 0)

    fun followUpTask(followUpId: String): FollowUpTask? = null

    fun planFollowUp(followUpId: String, title: String, planned: kotlinx.datetime.LocalDate?): FollowUpTask? = null

    fun befriend(event: BefriendEvent): List<Reward>? = null

    fun visitorLuck(): Double = 0.0

    fun berries(): Map<String, Int> = emptyMap()

    fun spendBerry(berry: String): Boolean = false
}

data class FollowUpTask(val taskId: String, val done: Boolean, val doneAtMillis: Long? = null)
