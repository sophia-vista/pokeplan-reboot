package app.pokeplan.world

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One way to answer a choice: what it says and what it gives. */
class ChoiceOption(val label: String, val reward: EventReward)

/** The kinds of floor choice: two safe options, each a reward (never a loss). The first is the default, taken if the player does not answer. */
enum class ChoiceKind(val title: String, val story: String, val first: ChoiceOption, val second: ChoiceOption) {
    FORK("A fork in the path", "The path splits in two.", ChoiceOption("The wide, safe path", EventReward.POKE), ChoiceOption("The narrow, winding path", EventReward.TREASURE)),
    SPRING("A quiet spring", "A spring bubbles in a hollow.", ChoiceOption("Rest and drink", EventReward.XP), ChoiceOption("Look around", EventReward.BERRIES)),
    ;

    fun option(n: Int): ChoiceOption = if (n == 0) first else second
}

/** A choice waiting for an answer: where it came up, when, and when the default is taken instead. A record of its own, never a change to the floor it came from. */
@Serializable
data class PendingChoice(val id: String, val kind: String, val dungeonId: String, val floor: Int, val at: Long, val expiresAt: Long) {
    val choice: ChoiceKind? get() = ChoiceKind.entries.firstOrNull { it.name == kind }
}

/**
 * Floor choices (docs/pokelife/MODULES/WORLD.md section 14): now and then a floor offers two ways on. The team does not wait: the choice is kept for
 * [WAIT_MILLIS] and, unanswered, the first option is taken, so nothing is lost and the app never has to be open. Whether a floor offers one
 * is worked out from the floor alone; what a choice gives is worked out from its own id, so the answer is the same whenever it is given.
 */
object Choices {
    /** Percent of floors from the third that offer a choice. *(tune)* */
    const val PERCENT = 4

    /** How many can wait at once; one that comes up when that many are waiting is answered with the default at once. */
    const val MAX_PENDING = 3

    /** How long the player has: 12 hours, the same as the longest time away World counts. */
    const val WAIT_MILLIS = 12L * 3_600_000

    fun kindAt(teamId: Int, trip: Expedition, floor: Int, index: Int): ChoiceKind? {
        if (floor < 3) return null
        val random = Random(WorldSim.eventSeed(teamId, trip, index) xor 0x2468ACEL)
        if (random.nextInt(100) >= PERCENT) return null
        return ChoiceKind.entries[random.nextInt(ChoiceKind.entries.size)]
    }

    fun idFor(teamId: Int, trip: Expedition, index: Int, dungeonId: String): String = "$dungeonId:${trip.startedAt}:$teamId:$index"

    private fun seedOf(id: String): Long = id.fold(7L) { h, c -> h * 31 + c.code }

    /** Answers [pending] with [option] (0 the first, 1 the second): pays it into [data], logs it, and removes it. */
    internal fun answer(data: WorldData, pending: PendingChoice, option: Int, at: Long): WorldData {
        val kind = pending.choice ?: return data.copy(choices = data.choices.filter { it.id != pending.id })
        val dungeon = Dungeons.byId(pending.dungeonId) ?: return data.copy(choices = data.choices.filter { it.id != pending.id })
        val chosen = kind.option(option.coerceIn(0, 1))
        val (paid, gave) = grantEventReward(data, chosen.reward, dungeon, pending.floor, Random(seedOf(pending.id)))
        val line = JournalLine(at, "${kind.title}: ${chosen.label} on floor ${pending.floor} of ${dungeon.name} ($gave).", JournalLine.KIND_EVENT, dungeon.name)
        return paid.copy(choices = paid.choices.filter { it.id != pending.id }, journal = (paid.journal + line).takeLast(WorldData.JOURNAL_SIZE))
    }

    /** Takes the default for every choice whose time is up at [now]. */
    fun expire(data: WorldData, now: Long): WorldData = data.choices.filter { it.expiresAt <= now }.fold(data) { d, p -> answer(d, p, 0, p.expiresAt) }
}
