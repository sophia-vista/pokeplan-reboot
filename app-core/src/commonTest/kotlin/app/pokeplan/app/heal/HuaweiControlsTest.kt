package app.pokeplan.app.heal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The in-app controls report: which cards the reader could tap and what took the tap (INTEGRATIONS_AND_PRIVACY.md 5.2.2, debug aid). */
class HuaweiControlsTest {
    private fun title(text: String, via: String) = ScreenNode(text, "", "com.huawei.health:id/function_set_items_title", "TextView", 2, tapVia = via)
    private val page = ScreenCapture(1, "MainActivity", listOf(
        ScreenNode("", "", "com.huawei.health:id/id_recyclerview", "RecyclerView", 1, scrollable = true),
        ScreenNode("", "", "com.huawei.health:id/card", "LinearLayout", 1, clickable = true),
        title("Heart", "card"), title("Sleep", ""), title("SpO₂", "card"),
    ))

    @Test fun cardsSaySoWhenTheyCanBeTappedAndWhatTakesTheTap() {
        val cards = HuaweiControls.cards(HuaweiSeen(listOf(page)))
        assertEquals(listOf("Heart", "Sleep", "SpO2"), cards.map { it.title })
        assertEquals(listOf(true, true, false), cards.map { it.walked })
        assertEquals(listOf("card", "", "card"), cards.map { it.tapVia })
    }

    @Test fun theReportListsScrollersTapsAndTheCardsTheWalkNeverSaw() {
        val text = HuaweiControls.lines(HuaweiSeen(listOf(page))).joinToString("\n")
        assertTrue("1 tappable" in text && "1 scrollable" in text, text)
        assertTrue("Sleep: NOT tappable" in text && "Heart: tap via card" in text, text)
        assertTrue("Not seen on any page: Exercise records, Weight" in text, text)
        assertEquals("No pages seen yet. Read Huawei Health first.", HuaweiControls.lines(HuaweiSeen()).single())
    }

    @Test fun theStepsSummaryIsCheckedToo() {
        val steps = ScreenCapture(2, "MainActivity", listOf(ScreenNode("", "8432 steps", "com.huawei.health:id/${HuaweiWalk.STEPS_ID}", "LinearLayout", 2, tapVia = "data_layout")))
        assertTrue("Steps summary: tap via data_layout" in HuaweiControls.lines(HuaweiSeen(listOf(steps))).joinToString("\n"))
    }

    @Test fun theSampleCarriesTheControlsAndTheWalkLogWithDigitsHidden() {
        val probe = HuaweiProbe()
        probe.addScreen(page)
        probe.addLog("Heart: opened SportHistory7")
        val sample = probe.seen.sample()
        assertTrue("== Controls ==" in sample && "== Walk log ==" in sample, sample)
        assertTrue("Heart: opened SportHistory#" in sample)
        assertFalse(probe.seen.isEmpty)
        probe.clear(); assertTrue(probe.seen.isEmpty)
    }

    @Test fun theWalkLogKeepsOnlyTheNewestLines() {
        val probe = HuaweiProbe(maxLog = 3)
        (1..5).forEach { probe.addLog("step") ; probe.addLog("line $it") }
        assertEquals(3, probe.seen.log.size); assertEquals("line 5", probe.seen.log.last())
    }
}
