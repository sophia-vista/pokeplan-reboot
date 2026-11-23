package app.pokeplan.app.heal

import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealImport
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Huawei Health capability table and the per-page read report (INTEGRATIONS_AND_PRIVACY.md 5.2.2): read, unsupported, can't open and no record model,
 * with the record counts each page gave. Fixtures follow the owner's probe 14.
 */
class HuaweiSourcesTest {
    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 11, 14, 21, 0).toInstant(utc).toEpochMilliseconds()
    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, if (id.isEmpty()) "" else "com.huawei.health:id/$id")

    private val healthPage = ScreenCapture(1, "MainActivity", listOf(
        n("time_strength_layout_middle", description = "8,432 steps"),
        n("function_set_items_title", "Heart"), n("function_set_items_data", "Heart rate 72 bpm"),
        n("function_set_items_title", "Skin temperature"), n("function_set_items_data", "34.1°C | 34.1 degrees Celsius"),
        n("function_set_items_title", "Weight"), n("function_set_items_data", "54.8"), n("function_set_items_unit", "kg"),
        n("function_set_items_title", "Sleep"), n("function_set_items_data", "5 h 9 min"),
        n("data_layout_step_layout", description = "07:12, Get-up time"),
    ))

    /** Probe 14's Weight All data: a day header and the day's readings under it. */
    private val weightHistory = ScreenCapture(2, "HealthDataHistoryActivity", listOf(
        n("action_bar_title", "All data"),
        n("health_data_weight_father_ll", "11/14/2026, average of 54.8 kg per day"),
        n("child_item_weight", "54.9"), n("child_item_weight_unit", "kg"), n("child_item_fat", "28.4%"),
        n("child_item_skeletal_muscle", "21.2"), n("child_item_skeletal_muscle_unit", "kg"), n("child_item_time", "7:12 AM"),
        n("health_data_weight_father_ll", "11/13/2026, average of 55.0 kg per day"),
        n("child_item_weight", "55.0"), n("child_item_weight_unit", "kg"), n("child_item_time", "6:50 AM"),
    ))

    private fun reportOf(visits: List<HuaweiVisit>, screens: List<ScreenCapture> = listOf(healthPage, weightHistory)): Map<String, HuaweiSourceReport> {
        val seen = HuaweiSeen(screens, log = listOf("Walk started"), visits = visits)
        val plan = HealImport.plan(HealData(), HuaweiRecords.read(seen, now, utc), now)
        val newIds = plan.candidates.filter { it.updates == null }.mapTo(HashSet()) { it.record.id }
        return HuaweiSources.report(seen, newIds, now, utc).associateBy { it.id }
    }

    @Test fun theTableCoversEveryVisitedCardAndSaysWhatIsSupported() {
        HuaweiWalk.CARDS.forEach { assertNotNull(HuaweiSources.sourceOf(it.title), it.title) }
        assertNotNull(HuaweiSources.sourceOf(HuaweiWalk.STEPS.title))
        assertNull(HuaweiSources.sourceOf("Something new"), "a card HEAL does not know has no row")
        val byId = HuaweiSources.capabilities().associateBy { it.id }
        listOf("health", "steps", "exercise", "weight", "sleep", "skin", "spo2").forEach { assertEquals(HuaweiOutcome.READ, byId.getValue(it).outcome, it) }
        assertEquals(HuaweiOutcome.INACCESSIBLE, byId.getValue("heart").outcome, "probe 14: the Heart page opens as a blank window")
        assertEquals(HuaweiOutcome.INACCESSIBLE, byId.getValue("emotional").outcome, "probe 14: Emotional wellbeing is blank too")
        assertEquals(HuaweiOutcome.NO_MODEL, byId.getValue("cycle").outcome)
        assertEquals(HuaweiOutcome.NO_MODEL, byId.getValue("sleep_stages").outcome)
        assertEquals(HuaweiOutcome.UNSUPPORTED, byId.getValue("sleep_week").outcome)
        assertEquals(HuaweiOutcome.UNSUPPORTED, byId.getValue("insights").outcome)
        assertEquals(setOf("Read", "Not supported", "Can't open", "No record model"), HuaweiOutcome.entries.map { it.label }.toSet())
    }

    @Test fun aReadSaysWhichCardsWereReadAndWhichCouldNotBeOpened() {
        val byId = reportOf(listOf(
            HuaweiVisit("Steps", "FitnessStepDetailActivity", words = true, menu = true),
            HuaweiVisit("Exercise records", skipped = true),
            HuaweiVisit("Heart", "FrameLayout", words = false),
            HuaweiVisit("Weight", "H5proWebViewContainerActivity", words = true, menu = true),
            HuaweiVisit("Sleep", "KnitSleepDetailActivity", words = false),
            HuaweiVisit("Emotional wellbeing", "FrameLayout", words = false),
            HuaweiVisit("Cycle Calendar", "H5proWebViewContainerActivity", words = true),
        ))
        assertEquals(HuaweiOutcome.READ, byId.getValue("health").outcome, "the Health page's cards are read")
        assertTrue(byId.getValue("health").newRecords > 0)
        assertEquals(HuaweiOutcome.READ, byId.getValue("weight").outcome)
        assertEquals(3, byId.getValue("weight").newRecords, "two weights and one body fat")
        assertEquals(HuaweiOutcome.READ, byId.getValue("exercise").outcome)
        assertEquals(0, byId.getValue("exercise").newRecords)
        assertTrue("Not opened" in byId.getValue("exercise").reason)
        assertEquals(HuaweiOutcome.INACCESSIBLE, byId.getValue("heart").outcome)
        assertEquals(HuaweiOutcome.INACCESSIBLE, byId.getValue("sleep").outcome, "blank this time: can't open, not a parser question")
        assertEquals(HuaweiOutcome.INACCESSIBLE, byId.getValue("emotional").outcome)
        assertEquals(HuaweiOutcome.NO_MODEL, byId.getValue("cycle").outcome, "readable periods, and no HEAL record for them")
        assertEquals(0, byId.getValue("cycle").newRecords)
        assertTrue(byId.getValue("cycle").reason.isNotBlank())
    }

    @Test fun aPageHealDoesNotReadIsUnsupportedNotRead() {
        val byId = reportOf(listOf(HuaweiVisit("Weight", "SomethingNewActivity", words = true, menu = true)))
        assertEquals(HuaweiOutcome.UNSUPPORTED, byId.getValue("weight").outcome)
        assertEquals("It opened a page Heal does not read.", byId.getValue("weight").reason)
    }

    @Test fun anUnknownCardIsReportedOnceAndNotSupported() {
        val byId = reportOf(listOf(HuaweiVisit("Mood", "MoodActivity", words = true)))
        val mood = byId.getValue("card:mood")
        assertEquals(HuaweiOutcome.UNSUPPORTED, mood.outcome)
        assertEquals("Mood", mood.name)
        val blank = reportOf(listOf(HuaweiVisit("Stress", "FrameLayout", words = false))).getValue("card:stress")
        assertEquals(HuaweiOutcome.INACCESSIBLE, blank.outcome)
    }

    @Test fun theReportCarriesNoHuaweiTextOnlyReasons() {
        val text = HuaweiSources.capabilities().joinToString("\n") { "${it.name} ${it.outcome.label} ${it.reason} ${it.reads}" }
        assertTrue(text.lines().none { line -> line.any { it in '0'..'9' } }, "no digit of Huawei's own values reaches the report: $text")
    }
}
