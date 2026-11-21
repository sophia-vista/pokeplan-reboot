package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlin.random.Random

object PickForMe {
    data class Pick(val task: Task)

    fun choose(candidates: List<Task>, rng: Random): Pick? {
        val open = candidates.filter { it.isOpen }
        if (open.isEmpty()) return null
        return Pick(open[rng.nextInt(open.size)])
    }
}

data class MiniQuest(val id: String, val text: String, val kind: Kind = Kind.PLAIN) {
    enum class Kind { PLAIN, AVOIDED_TASK }
}

object MiniQuests {
    const val PER_DAY = 3
    const val REROLLS_PER_DAY = 1

    const val ALL_THREE_BONUS_BERRIES = 1

    const val RARE_CANDIES = 2
    const val ALL_THREE_BONUS_CANDIES = 5

    const val PET_QUEST = "pet"

    val pool: List<MiniQuest> = listOf(
        MiniQuest("water", "Drink a glass of water"),
        MiniQuest("stretch", "Stand up and stretch for a minute"),
        MiniQuest("tidy", "Tidy one spot for 2 minutes"),
        MiniQuest("text-friend", "Send a friend a quick message"),
        MiniQuest("avoided", "Open the task you have been avoiding", MiniQuest.Kind.AVOIDED_TASK),
        MiniQuest("window", "Look out of a window for a minute"),
        MiniQuest("breathe", "Take five slow breaths"),
        MiniQuest("walk", "Walk around for 3 minutes"),
        MiniQuest("fruit", "Eat something fresh"),
        MiniQuest("bed", "Make your bed or smooth the sheets"),
        MiniQuest("dishes", "Wash or put away three dishes"),
        MiniQuest("inbox", "Clear three emails or messages"),
        MiniQuest("plan", "Write down tomorrow's top task"),
        MiniQuest("desk", "Clear your desk of one thing"),
        MiniQuest("sunlight", "Step outside for fresh air"),
        MiniQuest("posture", "Roll your shoulders and fix your posture"),
        MiniQuest("thanks", "Thank someone for something"),
        MiniQuest("song", "Listen to one song you love"),
        MiniQuest("laundry", "Start or put away a load of laundry"),
        MiniQuest("one-line", "Write one line about your day"),
        MiniQuest("phone-away", "Put your phone in another room for 10 minutes"),
        MiniQuest("eyes", "Rest your eyes: look far away for 20 seconds"),
        MiniQuest("small-step", "Do the first two minutes of something you are putting off"),
        MiniQuest(PET_QUEST, "Pet your buddy"),
        MiniQuest("snack", "Plan a healthy snack"),
        MiniQuest("bag", "Pack your bag for tomorrow"),
        MiniQuest("call", "Call or voice-message someone you miss"),
        MiniQuest("plants", "Water a plant or check on it"),
        MiniQuest("receipt", "File or bin one old receipt or paper"),
        MiniQuest("smile", "Do something that makes you smile"),
    )

    fun forDay(date: LocalDate, rerolled: Boolean = false): List<MiniQuest> {
        val first = pick(date, 0, emptySet())
        return if (rerolled) pick(date, 17, first.map { it.id }.toSet()) else first
    }

    private fun pick(date: LocalDate, salt: Int, not: Set<String>): List<MiniQuest> =
        pool.filter { it.id !in not }.shuffled(Random(date.toString().hashCode() * 31 + salt)).take(PER_DAY)
}
