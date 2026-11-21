package app.pokeplan.app.heal

import app.pokeplan.heal.HealRules
import kotlinx.datetime.TimeZone

/**
 * What the Huawei Health walk visits and when it has seen enough (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Pure, so the Android service only taps and scrolls.
 *
 * The walk is one pass down the Health page: every card is opened as it comes into view (then Back), so the page is not scrolled to its end first and back
 * up again. The wanted cards ([CARDS]) have their own budgets and are read into records; the others are looked at (captured) once. Every card's page has its own scroll budget, and the Exercise
 * records list stops as soon as it shows a session older than a read can pay for.
 */
object HuaweiWalk {
    /** A card the walk opens: its title on the Health page (any of [titles]), how many times its page is scrolled, and whether its Week tab is opened too, and whether its four-dots menu → "All data" is opened too. */
    data class Card(val titles: List<String>, val scrolls: Int, val week: Boolean = false, val allDataScrolls: Int = 0) {
        /** Whether the card's page has a four-dots menu → "All data" worth opening (almost every one does; Exercise records is a list already). */
        val allData: Boolean get() = allDataScrolls > 0
        val title: String get() = titles.first()
        fun matches(text: String) = text.trim() in titles
    }

    /** Exercise records is a list, so it gets the most scrolls, but it also stops at [exerciseIsOldEnough]. */
    const val EXERCISE_SCROLLS = 8

    /** In the order the cards sit on the Health page (top to bottom), which is the order the single pass meets them. */
    val CARDS = listOf(
        Card(listOf("Exercise records"), EXERCISE_SCROLLS), // a list already: no All data
        Card(listOf("Heart"), scrolls = 1, allDataScrolls = 3), // captured so its page can be parsed (resting heart rate, the day's range): not parsed yet
        Card(listOf("Weight"), scrolls = 2, allDataScrolls = 3),
        Card(listOf("Sleep"), scrolls = 2, week = true, allDataScrolls = 3),
    )

    /** The Steps summary at the top of the Health page (not a card: its element is `time_strength_layout_middle`), opened first with two scrolls (owner, 21 November 2026). */
    val STEPS = Card(listOf("Steps"), scrolls = 2, allDataScrolls = 4)
    const val STEPS_ID = "time_strength_layout_middle"

    /** Almost every card page, and the Steps page, has a four-dots menu with "All data" (owner, 21 November 2026): the menu button is found by one of these descriptions, the item by its text. */
    val MENU_DESCRIPTIONS = listOf("more", "more options", "more actions", "menu", "overflow", "options")
    const val ALL_DATA = "All data"

    /** Scrolls of the All data page of a look-only card. */
    const val LOOK_ALL_DATA_SCROLLS = 2

    /** Whether [description] names a four-dots / overflow menu button. */
    fun isMenuButton(description: String) = description.trim().lowercase() in MENU_DESCRIPTIONS

    /** Every other card on the Health page is opened too, to capture its page (owner, 21 November 2026): one scroll each, nothing parsed from it yet. */
    const val LOOK_SCROLLS = 1

    /** Cards never opened. Empty: the owner asked for every card, Cycle Calendar included (21 November 2026); its page is only held in memory like the rest. */
    val SKIP = emptySet<String>()

    /** The scrolls of the Health page itself: a safety bound; the pass ends sooner, at the end of the list. */
    const val HOME_SCROLLS = 14

    /** The wanted [CARDS] that have not been opened yet. */
    fun remaining(opened: Set<String>) = CARDS.filter { it.title !in opened }

    /** The card whose title element says [text]: a wanted one, any other card as a look-only visit, or null for an empty title or a skipped card. */
    fun cardFor(text: String): Card? {
        val t = text.trim()
        if (t.isEmpty() || t in SKIP) return null
        return CARDS.firstOrNull { it.matches(t) } ?: Card(listOf(t), LOOK_SCROLLS, allDataScrolls = LOOK_ALL_DATA_SCROLLS)
    }

    /** Whether [card] is one of the wanted [CARDS] (its page is read into records) rather than a look-only visit. */
    fun isWanted(card: Card) = CARDS.any { it.title == card.title }

    /**
     * Whether an Exercise records capture already shows a session older than a read can pay for ([HealRules.IMPORT_FRESH_MILLIS]): the list is newest first, so
     * everything further down is older still and is not worth another scroll.
     */
    fun exerciseIsOldEnough(capture: ScreenCapture, nowMillis: Long, zone: TimeZone): Boolean {
        val oldest = HuaweiRecords.oldestExerciseStart(capture, nowMillis, zone) ?: return false
        return oldest < nowMillis - HealRules.IMPORT_FRESH_MILLIS
    }
}
