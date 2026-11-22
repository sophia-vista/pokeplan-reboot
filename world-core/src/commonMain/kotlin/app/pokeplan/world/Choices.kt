package app.pokeplan.world

import kotlinx.serialization.Serializable
import kotlin.random.Random

class ChoiceOption(val label: String, val reward: EventReward)

enum class ChoiceKind(val title: String, val story: String, val first: ChoiceOption, val second: ChoiceOption) {
    FORK("A fork in the path", "The path splits in two.", ChoiceOption("The wide, safe path", EventReward.POKE), ChoiceOption("The narrow, winding path", EventReward.TREASURE)),
    SPRING("A quiet spring", "A spring bubbles in a hollow.", ChoiceOption("Rest and drink", EventReward.XP), ChoiceOption("Look around", EventReward.BERRIES)),
    ;

    fun option(n: Int): ChoiceOption = if (n == 0) first else second
}

@Serializable
data class PendingChoice(val id: String, val kind: String, val dungeonId: String, val floor: Int, val at: Long, val expiresAt: Long) {
    val choice: ChoiceKind? get() = ChoiceKind.entries.firstOrNull { it.name == kind }
}

object Choices {
    const val PERCENT = 4

    const val MAX_PENDING = 3

    const val WAIT_MILLIS = 12L * 3_600_000

    fun kindAt(teamId: Int, trip: Expedition, floor: Int, index: Int): ChoiceKind? {
        if (floor < 3) return null
        val random = Random(WorldSim.eventSeed(teamId, trip, index) xor 0x2468ACEL)
        if (random.nextInt(100) >= PERCENT) return null
        return ChoiceKind.entries[random.nextInt(ChoiceKind.entries.size)]
    }

    fun idFor(teamId: Int, trip: Expedition, index: Int, dungeonId: String): String = "$dungeonId:${trip.startedAt}:$teamId:$index"

    private fun seedOf(id: String): Long = id.fold(7L) { h, c -> h * 31 + c.code }

    internal fun answer(data: WorldData, pending: PendingChoice, option: Int, at: Long): WorldData {
        val kind = pending.choice ?: return data.copy(choices = data.choices.filter { it.id != pending.id })
        val dungeon = Dungeons.byId(pending.dungeonId) ?: return data.copy(choices = data.choices.filter { it.id != pending.id })
        val chosen = kind.option(option.coerceIn(0, 1))
        val (paid, gave) = grantEventReward(data, chosen.reward, dungeon, pending.floor, Random(seedOf(pending.id)))
        val line = JournalLine(at, "${kind.title}: ${chosen.label} on floor ${pending.floor} of ${dungeon.name} ($gave).", JournalLine.KIND_EVENT, dungeon.name)
        return paid.copy(choices = paid.choices.filter { it.id != pending.id }, journal = (paid.journal + line).takeLast(WorldData.JOURNAL_SIZE))
    }

    fun expire(data: WorldData, now: Long): WorldData = data.choices.filter { it.expiresAt <= now }.fold(data) { d, p -> answer(d, p, 0, p.expiresAt) }
}
