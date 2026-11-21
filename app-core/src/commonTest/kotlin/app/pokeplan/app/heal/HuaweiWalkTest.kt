package app.pokeplan.app.heal

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Huawei Health walk's plan: which cards, in page order, and when the Exercise records list is far enough (INTEGRATIONS_AND_PRIVACY.md 5.2.2). */
class HuaweiWalkTest {
    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 11, 14, 21, 0).toInstant(utc).toEpochMilliseconds()
    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, if (id.isEmpty()) "" else "com.huawei.health:id/$id")
    private fun item(date: String) = listOf(
        n("hw_show_health_running_history_child_item_chief_sport_type_text", "Outdoor walk"), n("hw_show_health_running_history_child_item_date", date),
        n("hw_show_health_running_history_child_item_left_date_text", "5:40 PM"), n("hw_show_health_running_history_child_item_chief_sport_data_text", "3.10"),
        n("hw_show_health_running_history_child_item_chief_sport_data_unit", "km"), n("hw_show_health_running_history_child_item_center_text", "00:41:05"),
    )
    private fun page(vararg dates: String) = ScreenCapture(1, "SportHistoryActivity", listOf(n("first_item_title", description = "November 2026, 6.20 km")) + dates.flatMap { item(it) })

    @Test fun cardsAreVisitedInPageOrderAndOnlyTheWantedOnes() {
        assertEquals(listOf("Exercise records", "Heart", "Weight", "Sleep"), HuaweiWalk.CARDS.map { it.title })
        assertEquals(listOf("Heart", "Weight", "Sleep"), HuaweiWalk.remaining(setOf("Exercise records")).map { it.title })
        assertEquals(HuaweiWalk.CARDS.size, HuaweiWalk.remaining(emptySet()).size)
        assertTrue(HuaweiWalk.remaining(HuaweiWalk.CARDS.map { it.title }.toSet()).isEmpty(), "every card opened ends the pass")
        assertEquals(HuaweiWalk.LOOK_SCROLLS, HuaweiWalk.cardFor("Cycle Calendar")?.scrolls, "every card is opened, Cycle Calendar too"); assertNull(HuaweiWalk.cardFor("  "))
        val look = HuaweiWalk.cardFor("SpO2")!!
        assertEquals(HuaweiWalk.LOOK_SCROLLS, look.scrolls); assertFalse(HuaweiWalk.isWanted(look), "any other card is looked at, not read into records")
        assertTrue(HuaweiWalk.isWanted(HuaweiWalk.cardFor("Heart")!!))
        assertEquals("Sleep", HuaweiWalk.cardFor(" Sleep ")?.title)
    }

    @Test fun onlyTheSleepPageOpensItsWeekTab() {
        assertEquals(listOf("Sleep"), HuaweiWalk.CARDS.filter { it.week }.map { it.title })
        assertTrue(HuaweiWalk.CARDS.single { it.title == "Exercise records" }.scrolls > HuaweiWalk.CARDS.single { it.title == "Weight" }.scrolls, "the list gets the most scrolls")
    }

    @Test fun exerciseListStopsAtASessionTooOldToPay() {
        assertFalse(HuaweiWalk.exerciseIsOldEnough(page("November 12", "November 8"), now, utc), "6 days back still pays")
        assertTrue(HuaweiWalk.exerciseIsOldEnough(page("November 12", "November 2"), now, utc), "12 days back: no need to scroll further")
        assertFalse(HuaweiWalk.exerciseIsOldEnough(ScreenCapture(1, "SportHistoryActivity", emptyList()), now, utc), "nothing shown yet: keep scrolling")
        assertFalse(HuaweiWalk.exerciseIsOldEnough(ScreenCapture(1, "MainActivity", item("November 1")), now, utc), "another page is never the Exercise list")
    }

    @Test fun theStepsPageOpensItsFourDotsMenuAndAllData() {
        assertTrue(HuaweiWalk.STEPS.allData)
        assertEquals(listOf("Heart", "Weight", "Sleep"), HuaweiWalk.CARDS.filter { it.allData }.map { it.title }, "Exercise records is a list already")
        assertTrue(HuaweiWalk.cardFor("SpO2")!!.allData, "almost every other card has All data too")
        assertTrue(HuaweiWalk.isMenuButton("More items") && HuaweiWalk.isMenuButton(" More options "), "the owner's capture calls the button More items")
        listOf("Share", "Stats", "Add", "Edit", "Go back", "Menu").forEach { assertFalse(HuaweiWalk.isMenuButton(it), it) }
        assertEquals("All data", HuaweiWalk.ALL_DATA)
    }
}
