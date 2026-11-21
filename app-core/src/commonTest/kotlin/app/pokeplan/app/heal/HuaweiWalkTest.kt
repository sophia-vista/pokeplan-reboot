package app.pokeplan.app.heal

import app.pokeplan.heal.ReadingGroup
import kotlinx.datetime.LocalDate
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
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()
    private val now = LocalDateTime(2026, 11, 14, 21, 0).toInstant(utc).toEpochMilliseconds()
    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, if (id.isEmpty()) "" else "com.huawei.health:id/$id")
    private fun item(date: String) = listOf(
        n("hw_show_health_running_history_child_item_chief_sport_type_text", "Outdoor walk"), n("hw_show_health_running_history_child_item_date", date),
        n("hw_show_health_running_history_child_item_left_date_text", "5:40 PM"), n("hw_show_health_running_history_child_item_chief_sport_data_text", "3.10"),
        n("hw_show_health_running_history_child_item_chief_sport_data_unit", "km"), n("hw_show_health_running_history_child_item_center_text", "00:41:05"),
    )
    private fun page(vararg dates: String) = ScreenCapture(1, "SportHistoryActivity", listOf(n("first_item_title", description = "November 2026, 6.20 km")) + dates.flatMap { item(it) })

    @Test fun cardsAreVisitedInPageOrderAndOnlyTheWantedOnes() {
        assertEquals(listOf("Exercise records", "Heart", "Weight", "Sleep", "Skin temperature", "SpO₂", "Cycle Calendar", "Emotional wellbeing"), HuaweiWalk.CARDS.map { it.title })
        assertEquals(listOf("Heart", "Weight", "Sleep", "Skin temperature", "SpO₂", "Cycle Calendar", "Emotional wellbeing"), HuaweiWalk.remaining(setOf("Exercise records")).map { it.title })
        assertEquals(HuaweiWalk.CARDS.size, HuaweiWalk.remaining(emptySet()).size)
        assertTrue(HuaweiWalk.remaining(HuaweiWalk.CARDS.map { it.title }.toSet()).isEmpty(), "every card opened ends the pass")
        assertEquals(HuaweiWalk.LOOK_SCROLLS, HuaweiWalk.cardFor("Cycle Calendar")?.scrolls, "every card is opened, Cycle Calendar too"); assertNull(HuaweiWalk.cardFor("  "))
        val look = HuaweiWalk.cardFor("Something new")!!
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
        assertEquals(listOf("Heart", "Weight", "Sleep", "Skin temperature", "SpO₂", "Cycle Calendar", "Emotional wellbeing"), HuaweiWalk.CARDS.filter { it.allData }.map { it.title }, "Exercise records is a list already")
        assertEquals("SpO₂", HuaweiWalk.cardFor("SpO2")?.title, "either spelling is the wanted SpO₂ card")
        assertNull(HuaweiWalk.cardFor("Health Clovers")); assertNull(HuaweiWalk.cardFor("Activity rings"))
        assertEquals(listOf("Period records"), HuaweiWalk.cardFor("Cycle Calendar")!!.menuItems, "its menu also has Period records")
        assertTrue(HuaweiWalk.cardFor("Health Clovers") == null && HuaweiWalk.cardFor("Something new")!!.allData, "almost every other card has All data too")
        assertEquals(listOf(listOf("Emotion", "Emotions"), listOf("Stress")), HuaweiWalk.cardFor("Emotional wellbeing")!!.pills, "emotions and stress are pills inside that page")
        assertTrue(HuaweiWalk.isMenuButton("More items") && HuaweiWalk.isMenuButton(" More options "), "the owner's capture calls the button More items")
        listOf("Share", "Stats", "Add", "Edit", "Go back", "Menu").forEach { assertFalse(HuaweiWalk.isMenuButton(it), it) }
        assertEquals("All data", HuaweiWalk.ALL_DATA)
    }

    @Test fun sleepOpensItsNewestEntriesAndAllDataStopsAtNinetyDays() {
        assertEquals(HuaweiWalk.SLEEP_ENTRIES, HuaweiWalk.CARDS.single { it.title == "Sleep" }.entries)
        assertTrue(HuaweiWalk.CARDS.filter { it.title != "Sleep" }.all { it.entries == 0 })
        fun row(content: String, date: String) = listOf(n("privacy_content_title", content), n("privacy_time", date))
        fun list(vararg rows: List<ScreenNode>) = ScreenCapture(1, "PrivacyDataModelActivity", listOf(n("privacy_title", "November 2026")) + rows.flatMap { it })
        assertFalse(HuaweiWalk.allDataIsFarEnough(list(row("5 h 9 min", "11/12"), row("6 h 2 min", "11/5")), now, utc))
        assertTrue(HuaweiWalk.allDataIsFarEnough(ScreenCapture(1, "PrivacyDataModelActivity", listOf(n("privacy_title", "July 2026")) + row("5 h 9 min", "7/3")), now, utc), "134 days back")
        assertFalse(HuaweiWalk.allDataIsFarEnough(ScreenCapture(1, "KnitSleepDetailActivity", row("5 h", "7/3")), now, utc), "only an All data list counts")
    }

    @Test fun aReadSkipsWhatHealAlreadyHas() {
        val today = LocalDate(2026, 11, 14)
        val known = HuaweiWalk.Known(today = today, nightDays = setOf(today, LocalDate(2026, 11, 13)),
            newestDay = mapOf(ReadingGroup.SPO2 to today, ReadingGroup.SKIN_TEMPERATURE to LocalDate(2026, 11, 13)), newestWorkoutStart = at(2026, 11, 12, 17, 40))
        fun card(t: String) = HuaweiWalk.cardFor(t)!!
        assertTrue(HuaweiWalk.skipCard(card("Sleep"), known), "tonight's sleep is saved")
        assertTrue(HuaweiWalk.skipCard(card("SpO2"), known), "today's SpO2 is saved")
        assertTrue(HuaweiWalk.skipCard(card("Skin temperature"), known), "yesterday is the newest row: a row comes the day after, so nothing is new until tomorrow")
        assertFalse(HuaweiWalk.skipCard(card("Skin temperature"), known.copy(newestDay = mapOf(ReadingGroup.SKIN_TEMPERATURE to LocalDate(2026, 11, 12)))), "two days old: look for yesterday")
        assertTrue(HuaweiWalk.skipCard(card("Steps"), known.copy(newestDay = mapOf(ReadingGroup.STEPS to LocalDate(2026, 11, 13)))))
        assertEquals(LocalDate(2026, 11, 12), HuaweiWalk.knownDay(HuaweiWalk.STEPS, known.copy(newestDay = mapOf(ReadingGroup.STEPS to LocalDate(2026, 11, 13)))), "the newest saved steps day may be partial: the day before is whole")
        assertFalse(HuaweiWalk.skipCard(card("Exercise records"), known)); assertFalse(HuaweiWalk.skipCard(card("Sleep"), HuaweiWalk.Known()), "nothing known: visit everything")
        assertEquals(LocalDate(2026, 11, 13), HuaweiWalk.knownDay(card("Skin temperature"), known))
        fun list(vararg dates: String) = ScreenCapture(1, "PrivacyDataModelActivity", listOf(n("privacy_title", "November 2026")) + dates.flatMap { listOf(n("privacy_content_title", "20.0-21.0°C"), n("privacy_time", it)) })
        assertFalse(HuaweiWalk.allDataIsKnown(list("11/14"), LocalDate(2026, 11, 13), now, utc), "a new day is on top")
        assertTrue(HuaweiWalk.allDataIsKnown(list("11/14", "11/13"), LocalDate(2026, 11, 13), now, utc), "a row already saved: everything below is too")
        assertFalse(HuaweiWalk.allDataIsKnown(list("11/14"), null, now, utc), "nothing saved yet")
        assertTrue(HuaweiWalk.exerciseIsKnown(page("November 12", "November 10"), known, now, utc))
        assertFalse(HuaweiWalk.exerciseIsKnown(page("November 14"), known, now, utc).also { }, "a newer session than the newest saved one is not known")
    }
}
