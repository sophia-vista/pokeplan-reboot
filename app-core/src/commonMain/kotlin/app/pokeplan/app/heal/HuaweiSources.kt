package app.pokeplan.app.heal

import app.pokeplan.heal.Imported
import kotlinx.datetime.TimeZone

/**
 * Why one Huawei Health page or metric did or did not give HEAL anything (HEAL.md 22B, INTEGRATIONS_AND_PRIVACY.md 5.2.2): the four answers a read must
 * tell apart, so the owner can see which metrics were read and which are simply not supported.
 */
enum class HuaweiOutcome(val label: String) {
    /** HEAL read records from it. */
    READ("Read"),

    /** The page opens and shows content, but HEAL's parser or model does not support that content. */
    UNSUPPORTED("Not supported"),

    /** The page cannot be read at all: a blank window, a web view with no accessible words, or no menu to reach its data. */
    INACCESSIBLE("Can't open"),

    /** A reliable metric the page shows for which HEAL has no record type, so it is reported and never saved. */
    NO_MODEL("No record model"),
}

/**
 * One row of the Huawei Health capability table: a card or an area of the Health page, how the walk reaches it, what HEAL can extract from it, and its
 * support status. This table is the single place a page's status lives: the walk opens a page it cannot read at most once and reports it, instead of
 * retrying its menu and pills for the whole budget.
 *
 * [blocked] is a probe-proven fact about the owner's phone, not a capability: the page is known not to open at all (probe 14, `docs/pokelife/MODULES/HEAL.md`
 * 22B), whatever HEAL could extract if it did.
 */
data class HuaweiSource(
    /** Stable id for the report and the tests. */
    val id: String,
    /** The card's title on the Health page, as the walk matches its visits; blank for an area of the Health page itself. */
    val card: String,
    /** What the player sees in the report. */
    val name: String,
    /** How the walk reaches it, in plain words. */
    val opens: String,
    /** What HEAL reads from it when it opens, in plain words. */
    val reads: String,
    /** What HEAL does with readable content. */
    val support: HuaweiOutcome,
    /** Set when the page is known not to open at all on the owner's phone ([HuaweiOutcome.INACCESSIBLE]). */
    val blocked: HuaweiOutcome? = null,
    /** The Android activity classes a readable page of this source opens; a visit to any other page is a page HEAL does not read. */
    val pages: List<String> = emptyList(),
    /** Why a row that is not READ is not read, in plain words and never Huawei's own text. */
    val why: String = "",
)

/** What one source gave in a read: the outcome, how many records the preview would add from it, and a short reason. */
data class HuaweiSourceReport(
    val id: String,
    val name: String,
    val outcome: HuaweiOutcome,
    /** READ only: records the preview would add from this source (0 when everything is already here). */
    val newRecords: Int = 0,
    /** A short reason: what is read from it, or why it is not read. Never Huawei's own text. */
    val reason: String = "",
    /** What HEAL reads from it when it can, for the expanded rows of the report. */
    val reads: String = "",
)

/**
 * What the Android walk saw of one card, recorded in memory for the read report ([HuaweiSeen.visits]): the card's title, the page class it opened and
 * what it could do there. Never Huawei's text: only outcomes and the page's own Android class name.
 */
data class HuaweiVisit(
    val title: String,
    /** The Android activity class the card opened (an overview window is "FrameLayout"); blank when nothing opened. */
    val page: String = "",
    /** The page showed any words at all (a blank window or a web view with no accessible nodes shows none). */
    val words: Boolean = false,
    /** Its four-dots menu was found and opened (its All data, or its own menu item). */
    val menu: Boolean = false,
    /** The card was not opened because a new-only read already has everything from it. */
    val skipped: Boolean = false,
    /** Why it could not be read, in plain words (never Huawei's own text). */
    val reason: String = "",
)

object HuaweiSources {
    private val BLANK = "It opened a blank window: no words the phone can read and no menu."

    /**
     * The capability table, in the order the walk meets the pages (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Every card of [HuaweiWalk.CARDS] and the Steps
     * summary has a row; the cards the walk deliberately skips and the areas it never reads have one too, so the report can say why.
     */
    val SOURCES: List<HuaweiSource> = listOf(
        HuaweiSource(
            "health", "", "Health page", "the page Huawei Health opens on",
            "the cards' readings for today, the get-up time and the step count",
            HuaweiOutcome.READ, pages = listOf("MainActivity"),
        ),
        HuaweiSource(
            "steps", HuaweiWalk.STEPS.title, "Steps", "the summary, its page and its All data",
            "today's steps and the All data steps history",
            HuaweiOutcome.READ, pages = listOf("FitnessStepDetailActivity", "PrivacyDataModelActivity"),
        ),
        HuaweiSource(
            "exercise", "Exercise records", "Exercise records", "the list of sessions",
            "workouts with their type, start, length and distance",
            HuaweiOutcome.READ, pages = listOf("SportHistoryActivity"),
        ),
        HuaweiSource(
            "heart", "Heart", "Heart", "the Heart page and its All data",
            "the day's heart-rate lows and highs and the resting heart rate",
            HuaweiOutcome.UNSUPPORTED, blocked = HuaweiOutcome.INACCESSIBLE,
            why = "It opens a blank window on the owner's phone: no words and no menu, so there is nothing for HEAL to parse. Today's heart rate still comes from the Health page card.",
        ),
        HuaweiSource(
            "weight", "Weight", "Weight", "the page and its All data",
            "weight and body fat, each with its own date and time",
            HuaweiOutcome.READ, pages = listOf("H5proWebViewContainerActivity", "HealthDataHistoryActivity"),
        ),
        HuaweiSource(
            "sleep", "Sleep", "Sleep", "the Day view and the All data day pages",
            "today's night, nights and naps with their real times, and the sleep score",
            HuaweiOutcome.READ, pages = listOf("KnitSleepDetailActivity", "PrivacyDataModelActivity"),
        ),
        HuaweiSource(
            "sleep_stages", "", "Sleep stages", "the Sleep page's stage ring",
            "the stages of a night (deep, light, REM, awake)",
            HuaweiOutcome.NO_MODEL,
            why = "HEAL keeps a night as one start and one end: it has no stage record, so the stage minutes are not read.",
        ),
        HuaweiSource(
            "sleep_week", "", "Sleep week and month views", "the Sleep page's Week and Month tabs",
            "a week's or a month's totals and averages",
            HuaweiOutcome.UNSUPPORTED,
            why = "They are totals and averages, not one night, and HEAL does not read them as a record.",
        ),
        HuaweiSource(
            "skin", "Skin temperature", "Skin temperature", "the page and its All data",
            "today's skin temperature and the day's low-high range",
            HuaweiOutcome.READ, pages = listOf("KnitTemperatureActivity", "PrivacyDataModelActivity"),
        ),
        HuaweiSource(
            "emotional", "Emotional wellbeing", "Emotional wellbeing", "the page and its Emotion and Stress pills",
            "the day's emotion and the stress number",
            HuaweiOutcome.UNSUPPORTED, blocked = HuaweiOutcome.INACCESSIBLE,
            why = "It opens a blank window on the owner's phone and its pills show no words, so there is nothing for HEAL to parse. The stress number still comes from the Health page card.",
        ),
        HuaweiSource(
            "spo2", "SpO₂", "SpO₂", "the page and its All data",
            "today's blood oxygen and the day's low-high range",
            HuaweiOutcome.READ, pages = listOf("KnitBloodOxygenDetailActivity", "PrivacyDataModelActivity"),
        ),
        HuaweiSource(
            "cycle", "Cycle Calendar", "Cycle Calendar", "the calendar page and its menu",
            "period days, flow and symptoms",
            HuaweiOutcome.NO_MODEL,
            why = "HEAL has no record for a period, flow or symptoms, so they are not read or saved.",
        ),
        HuaweiSource(
            "clovers", "Health Clovers", "Health Clovers", "not opened (the walk skips it)",
            "the day's completed tasks",
            HuaweiOutcome.NO_MODEL,
            why = "HEAL has no record for Health Clovers, so the card is never opened.",
        ),
        HuaweiSource(
            "rings", "Activity rings", "Activity rings", "not opened (the walk skips it)",
            "the day's goals reached",
            HuaweiOutcome.NO_MODEL,
            why = "HEAL has no record for Activity rings, so the card is never opened.",
        ),
        HuaweiSource(
            "insights", "", "Health insights", "the Health insights area of the Health page",
            "Huawei's own insight lines",
            HuaweiOutcome.UNSUPPORTED,
            why = "They are Huawei's summaries, not records, and HEAL keeps none of them.",
        ),
    )

    /** The capability row for a Health page card title, or null for a card HEAL does not know. */
    fun sourceOf(title: String): HuaweiSource? {
        val wanted = HuaweiWalk.canonical(title)
        if (wanted.isEmpty()) return null
        return SOURCES.firstOrNull { it.card.isNotBlank() && HuaweiWalk.canonical(it.card) == wanted }
    }

    fun byId(id: String): HuaweiSource? = SOURCES.firstOrNull { it.id == id }

    /**
     * The table as the report reads before any walk: what HEAL can get and what it cannot, from the probe evidence. A source the owner's phone cannot
     * open at all ([HuaweiSource.blocked]) shows as that, not as its extraction status.
     */
    fun capabilities(): List<HuaweiSourceReport> = SOURCES.map { source ->
        val outcome = source.blocked ?: source.support
        HuaweiSourceReport(source.id, source.name, outcome, 0, if (outcome == HuaweiOutcome.READ) source.reads else source.why, source.reads)
    }

    /**
     * The report of one read: each row of the table with what the walk saw, plus any card the walk met that HEAL does not know (reported as not
     * supported rather than silently visited). [newIds] are the record ids the preview would add, so a READ row can say how many are new; a record two
     * pages both show is counted once, on the first row of the table that found it.
     */
    fun report(seen: HuaweiSeen, newIds: Set<String>, nowMillis: Long, zone: TimeZone): List<HuaweiSourceReport> {
        val parts = HuaweiRecords.sourceRecords(seen, nowMillis, zone)
        val visits = LinkedHashMap<String, HuaweiVisit>()
        for (v in seen.visits) if (v.title.isNotBlank()) visits[HuaweiWalk.canonical(v.title)] = v
        val claimed = HashSet<String>()
        val rows = SOURCES.map { source ->
            val visit = source.card.takeIf { it.isNotBlank() }?.let { visits[HuaweiWalk.canonical(it)] }
            val found = parts[source.id].orEmpty()
            val fresh = if (source.support == HuaweiOutcome.READ) {
                found.map { it.id }.distinct().filter { it in newIds && claimed.add(it) }
            } else emptyList()
            when {
                visit?.skipped == true -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.READ, 0, "Not opened: Heal already has everything here.", source.reads)
                visit != null && !visit.words -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.INACCESSIBLE, 0, visit.reason.ifBlank { BLANK }, source.reads)
                source.support == HuaweiOutcome.READ -> readRow(source, visit, found, fresh)
                else -> HuaweiSourceReport(source.id, source.name, source.support, 0, source.why, source.reads)
            }
        }
        // A card the walk met but HEAL does not know: opened once (the walk never retries it), and reported as not supported when it shows words.
        val skippedCards = HuaweiWalk.SKIP.map(HuaweiWalk::canonical).toSet()
        val unknown = visits.values.filter { sourceOf(it.title) == null && HuaweiWalk.canonical(it.title) !in skippedCards }
        return rows + unknown.map { v ->
            HuaweiSourceReport(
                "card:${HuaweiWalk.canonical(v.title).lowercase()}", v.title,
                if (v.words) HuaweiOutcome.UNSUPPORTED else HuaweiOutcome.INACCESSIBLE, 0,
                if (v.words) "A card Heal does not know: it was opened once and not read." else v.reason.ifBlank { BLANK },
            )
        }
    }

    private fun readRow(source: HuaweiSource, visit: HuaweiVisit?, found: List<Imported>, fresh: List<String>): HuaweiSourceReport {
        // A page HEAL does not read is unsupported even if other captured pages gave this card records: what the walk
        // opened was not the page this row says HEAL can read.
        val unexpected = visit != null && visit.words &&
            visit.page.isNotBlank() && source.pages.isNotEmpty() && visit.page !in source.pages
        return when {
            unexpected -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.UNSUPPORTED, 0, "It opened a page Heal does not read.", source.reads)
            visit != null && found.isEmpty() && fresh.isEmpty() -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.READ, 0, "Nothing new this time.", source.reads)
            visit == null && source.card.isNotBlank() -> HuaweiSourceReport(source.id, source.name, source.blocked ?: HuaweiOutcome.READ, 0,
                if (source.blocked != null) source.why else "Not reached this read.", source.reads)
            fresh.isNotEmpty() -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.READ, fresh.size, source.reads, source.reads)
            else -> HuaweiSourceReport(source.id, source.name, HuaweiOutcome.READ, 0, if (found.isEmpty()) source.reads else "Nothing new this time.", source.reads)
        }
    }
}
