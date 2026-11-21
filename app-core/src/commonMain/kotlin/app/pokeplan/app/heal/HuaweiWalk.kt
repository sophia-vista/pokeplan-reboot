package app.pokeplan.app.heal

import app.pokeplan.heal.HealRules
import kotlinx.datetime.TimeZone

/**
 * What the Huawei Health walk visits and when it has seen enough (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Pure, so the Android service only taps and scrolls.
 *
 * The walk is one pass down the Health page: each wanted card is opened as it comes into view (then Back), and the pass ends when every wanted card
 * has been opened, so the page is not scrolled to its end first and back up again. Every card's page has its own scroll budget, and the Exercise
 * records list stops as soon as it shows a session older than a read can pay for.
 */
object HuaweiWalk {
    /** A card the walk opens: its title on the Health page (any of [titles]), how many times its page is scrolled, and whether its Week tab is opened too. */
    data class Card(val titles: List<String>, val scrolls: Int, val week: Boolean = false) {
        val title: String get() = titles.first()
        fun matches(text: String) = text.trim() in titles
    }

    /** Exercise records is a list, so it gets the most scrolls, but it also stops at [exerciseIsOldEnough]. */
    const val EXERCISE_SCROLLS = 8

    /** In the order the cards sit on the Health page (top to bottom), which is the order the single pass meets them. */
    val CARDS = listOf(
        Card(listOf("Exercise records"), EXERCISE_SCROLLS),
        Card(listOf("Heart"), scrolls = 1), // captured so its page can be parsed (resting heart rate, the day's range): not parsed yet
        Card(listOf("Weight"), scrolls = 2),
        Card(listOf("Sleep"), scrolls = 2, week = true),
    )

    /** The scrolls of the Health page itself: a safety bound; the pass normally ends sooner, when every card has been opened. */
    const val HOME_SCROLLS = 10

    /** The titles in [CARDS] that have not been opened yet. */
    fun remaining(opened: Set<String>) = CARDS.filter { it.title !in opened }

    /** The wanted card whose title element says [text], or null. */
    fun cardFor(text: String) = CARDS.firstOrNull { it.matches(text) }

    /**
     * Whether an Exercise records capture already shows a session older than a read can pay for ([HealRules.IMPORT_FRESH_MILLIS]): the list is newest first, so
     * everything further down is older still and is not worth another scroll.
     */
    fun exerciseIsOldEnough(capture: ScreenCapture, nowMillis: Long, zone: TimeZone): Boolean {
        val oldest = HuaweiRecords.oldestExerciseStart(capture, nowMillis, zone) ?: return false
        return oldest < nowMillis - HealRules.IMPORT_FRESH_MILLIS
    }
}
