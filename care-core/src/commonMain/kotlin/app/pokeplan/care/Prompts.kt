package app.pokeplan.care

import kotlinx.datetime.LocalDate

/** One gratitude or reflection prompt. */
data class Prompt(val id: String, val text: String)

/** Optional prompts, one at a time and skippable. They turn with the day, so each day opens on a different one. */
object Prompts {
    val all: List<Prompt> = listOf(
        Prompt("thanks-small", "What small thing went well today?"),
        Prompt("thanks-person", "Who made your day a little better?"),
        Prompt("proud", "What are you quietly proud of this week?"),
        Prompt("rest", "What helped you rest lately?"),
        Prompt("kind", "What kind thing did you do for yourself?"),
        Prompt("enjoy", "What did you enjoy today, even for a minute?"),
        Prompt("learn", "What did you learn about yourself recently?"),
        Prompt("tomorrow", "What would make tomorrow a bit easier?"),
        Prompt("place", "Where do you feel most at ease?"),
        Prompt("comfort", "What comforts you when a day is hard?"),
    )

    /** Today's prompt, after [skipped] skips; null once every prompt has been skipped today. */
    fun forDay(day: LocalDate, skipped: Int = 0): Prompt? =
        if (skipped >= all.size) null else all[(day.toEpochDays().toLong() + skipped).mod(all.size.toLong()).toInt()]

    fun byId(id: String): Prompt? = all.firstOrNull { it.id == id }
}
