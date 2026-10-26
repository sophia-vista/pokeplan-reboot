package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlin.random.Random

/** "Pick for me": a random task from today's list, for when choosing is the hard part. */
object PickForMe {
    /** Taking the pick earns a small bonus on the task's candies (the engine's `RewardEngine.PICKED_BONUS`). *(tune)* */
    const val BONUS_MULTIPLIER = 1.10

    data class Pick(val task: Task, val bonusMultiplier: Double)

    fun choose(candidates: List<Task>, rng: Random): Pick? {
        val open = candidates.filter { it.isOpen }
        if (open.isEmpty()) return null
        return Pick(open[rng.nextInt(open.size)], BONUS_MULTIPLIER)
    }
}

/** A small action the app suggests each day; each is worth a berry. */
data class MiniQuest(val id: String, val text: String, val kind: Kind = Kind.PLAIN) {
    enum class Kind { PLAIN, AVOIDED_TASK }
}

object MiniQuests {
    const val PER_DAY = 3
    const val REROLLS_PER_DAY = 1

    /** Finishing all of a day's quests adds a small bonus. *(tune)* */
    const val ALL_THREE_BONUS_BERRIES = 1

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
        MiniQuest("pet", "Pet your buddy"),
        MiniQuest("snack", "Plan a healthy snack"),
        MiniQuest("bag", "Pack your bag for tomorrow"),
        MiniQuest("call", "Call or voice-message someone you miss"),
        MiniQuest("plants", "Water a plant or check on it"),
        MiniQuest("receipt", "File or bin one old receipt or paper"),
        MiniQuest("smile", "Do something that makes you smile"),
    )

    /** The day's quests: the same for everyone on a date, with a reroll that shifts the picks. */
    fun forDay(date: LocalDate, rerolled: Boolean = false): List<MiniQuest> {
        val seed = date.toString().hashCode() * 31 + if (rerolled) 17 else 0
        val rng = Random(seed)
        return pool.shuffled(rng).take(PER_DAY)
    }
}
