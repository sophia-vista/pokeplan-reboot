package app.pokeplan.app

import kotlin.random.Random

/** How the buddy feels right now. The design: it sleeps during focus, cheers when the day went well and droops when it was left alone. */
enum class BuddyMood(val line: String) {
    FOCUSING("Zzz... focusing with you"),
    CHEERFUL("Great day!"),
    CONTENT("Nice work!"),
    WAITING("Ready when you are"),
    LONELY("Missed you! Let's do one small thing"),
}

/** What the buddy says when you pet it or feed it. */
object BuddyLines {
    val pet = listOf("That feels nice!", "Hehe!", "More, please!", "You're the best!", "Happy to be here!")
    val feed = listOf("Yum!", "Delicious!", "Thank you!")
    const val FRIENDSHIP_FULL = "We're the best of friends!"

    fun pet(random: Random = Random): String = pet.random(random)
    fun feed(random: Random = Random): String = feed.random(random)
}
