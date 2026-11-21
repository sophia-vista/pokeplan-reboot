package app.pokeplan.app.heal

import app.pokeplan.heal.HealRules
import app.pokeplan.heal.ReadingGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * What the Huawei Health walk visits and when it has seen enough (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Pure, so the Android service only taps and scrolls.
 *
 * The walk is one pass down the Health page: every card is opened as it comes into view (then Back), so the page is not scrolled to its end first and back
 * up again. The wanted cards ([CARDS]) have their own budgets and are read into records; the others are looked at (captured) once. Every card's page has its own scroll budget, and the Exercise
 * records list stops as soon as it shows a session older than a read can pay for.
 */
object HuaweiWalk {
    /** A card the walk opens: its title on the Health page (any of [titles]), how many times its page is scrolled, and whether its Week tab is opened too, and whether its four-dots menu → "All data" is opened too, how many of that list's newest rows are opened one by one ([entries]), and the pills (tabs inside the page, each a list of the words it may be spelled with) tapped in turn, each captured and given its own All data. */
    data class Card(val titles: List<String>, val scrolls: Int, val week: Boolean = false, val allDataScrolls: Int = 0, val entries: Int = 0, val pills: List<List<String>> = emptyList()) {
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
        Card(listOf("Sleep"), scrolls = 2, week = true, allDataScrolls = ALL_DATA_SCROLLS, entries = SLEEP_ENTRIES),
        Card(listOf("Skin temperature"), scrolls = 1, allDataScrolls = ALL_DATA_SCROLLS),
        Card(listOf("SpO₂", "SpO2"), scrolls = 1, allDataScrolls = ALL_DATA_SCROLLS),
        // Emotional wellbeing has two pills inside its page, emotions and stress (owner, 21 November 2026): each is captured and has its own All data.
        Card(listOf("Emotional wellbeing"), scrolls = 1, allDataScrolls = 3, pills = listOf(listOf("Emotion", "Emotions"), listOf("Stress"))),
    )

    /** The All data lists of Sleep, Skin temperature and SpO2 are kept as history (owner, 21 November 2026): scrolled until a row is [ALL_DATA_DAYS] old, at most this many times. */
    const val ALL_DATA_SCROLLS = 14
    const val ALL_DATA_DAYS = 90

    /** How many of the newest Sleep All data rows (days) are opened one by one: each opens to that day's nights and naps with their bed and wake times. */
    const val SLEEP_ENTRIES = 14

    /** The Steps summary at the top of the Health page (not a card: its element is `time_strength_layout_middle`), opened first with two scrolls (owner, 21 November 2026). */
    val STEPS = Card(listOf("Steps"), scrolls = 1, allDataScrolls = ALL_DATA_SCROLLS)
    const val STEPS_ID = "time_strength_layout_middle"

    /**
     * Almost every card page, and the Steps page, has a four-dots menu with "All data" (owner, 21 November 2026). From the owner's capture of the same day the
     * button is the toolbar's last icon (`hwappbarpattern_ok_icon`) described **"More items"**; the icon beside it, `hwappbarpattern_menu_icon`, is Share or Stats
     * and must never be tapped, so the button is found by this description alone, never by an id.
     */
    val MENU_DESCRIPTIONS = listOf("more items", "more", "more options", "more actions", "overflow")
    const val ALL_DATA = "All data"

    /** Whether [description] names the four-dots menu button (exactly; "Share", "Stats", "Add" and "Edit" never do). */
    fun isMenuButton(description: String) = description.trim().lowercase() in MENU_DESCRIPTIONS

    /** Scrolls of the All data page of a look-only card. */
    const val LOOK_ALL_DATA_SCROLLS = 2

    /** Every other card on the Health page is opened too, to capture its page (owner, 21 November 2026): one scroll each, nothing parsed from it yet. */
    const val LOOK_SCROLLS = 1

    /** Cards never opened: the owner asked for every card, Cycle Calendar included, but not Health Clovers or Activity rings (21 November 2026). */
    val SKIP = setOf("Health Clovers", "Activity rings")

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

    /** Whether an All data capture already shows a row [ALL_DATA_DAYS] old or older: the list is newest first, so it is far enough. */
    fun allDataIsFarEnough(capture: ScreenCapture, nowMillis: Long, zone: TimeZone): Boolean {
        val oldest = HuaweiRecords.oldestAllDataDay(capture, nowMillis, zone) ?: return false
        return oldest < kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date.minus(ALL_DATA_DAYS, kotlinx.datetime.DateTimeUnit.DAY)
    }

    /**
     * Whether an Exercise records capture already shows a session older than a read can pay for ([HealRules.IMPORT_FRESH_MILLIS]): the list is newest first, so
     * everything further down is older still and is not worth another scroll.
     */
    fun exerciseIsOldEnough(capture: ScreenCapture, nowMillis: Long, zone: TimeZone): Boolean {
        val oldest = HuaweiRecords.oldestExerciseStart(capture, nowMillis, zone) ?: return false
        return oldest < nowMillis - HealRules.IMPORT_FRESH_MILLIS
    }

    // --- skipping what HEAL already has (owner, 21 November 2026) ---------------------------------------------------------------------

    /**
     * What HEAL already holds from Huawei Health, so a read does not scroll or open what has nothing new: the newest saved workout start, the wake days of saved nights,
     * the newest day of each reading group, and the day of the newest weight. [today] is the local day the read runs on.
     */
    data class Known(
        val today: LocalDate? = null,
        val newestWorkoutStart: Long? = null,
        val nightDays: Set<LocalDate> = emptySet(),
        val newestDay: Map<ReadingGroup, LocalDate> = emptyMap(),
        val newestWeightDay: LocalDate? = null,
    )

    /** Whether the whole card needs no visit: tonight's sleep is already saved, today's skin temperature or SpO2 is, or today's weight is. Exercise and the rest are cheap to look at. */
    fun skipCard(card: Card, known: Known): Boolean {
        val today = known.today ?: return false
        // An All data list gets a day's row the day after, so a newest saved row of yesterday means there is nothing new until tomorrow.
        val yesterday = today.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        return when (card.title) {
            "Sleep" -> today in known.nightDays
            "Skin temperature" -> known.newestDay[ReadingGroup.SKIN_TEMPERATURE]?.let { it >= yesterday } == true
            "SpO₂" -> known.newestDay[ReadingGroup.SPO2]?.let { it >= yesterday } == true
            "Steps" -> known.newestDay[ReadingGroup.STEPS]?.let { it >= yesterday } == true
            "Weight" -> known.newestWeightDay?.let { it >= today } == true
            else -> false
        }
    }

    /** The newest day saved for the All data list of [card], or null (a Sleep row is "known" when its wake day is a saved night). */
    fun knownDay(card: Card, known: Known): LocalDate? = when (card.title) {
        "Skin temperature" -> known.newestDay[ReadingGroup.SKIN_TEMPERATURE]
        "SpO₂" -> known.newestDay[ReadingGroup.SPO2]
        "Sleep" -> known.nightDays.maxOrNull()
        // The newest saved day of steps may be a partial count from the Health page, so the day before it is the newest one known to be whole.
        "Steps" -> known.newestDay[ReadingGroup.STEPS]?.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        else -> null
    }

    /** Whether an All data capture already shows a row on or before [knownDay]: the list is newest first, so everything below is saved already. */
    fun allDataIsKnown(capture: ScreenCapture, knownDay: LocalDate?, nowMillis: Long, zone: TimeZone): Boolean {
        knownDay ?: return false
        val oldest = HuaweiRecords.oldestAllDataDay(capture, nowMillis, zone) ?: return false
        return oldest <= knownDay
    }

    /** Whether an Exercise records capture already shows a session at or before the newest saved one: the list is newest first, so the rest is saved already. */
    fun exerciseIsKnown(capture: ScreenCapture, known: Known, nowMillis: Long, zone: TimeZone): Boolean {
        val newest = known.newestWorkoutStart ?: return false
        val oldest = HuaweiRecords.oldestExerciseStart(capture, nowMillis, zone) ?: return false
        return oldest <= newest
    }
}
