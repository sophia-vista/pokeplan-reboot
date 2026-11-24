package app.pokeplan.app.integrations

import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUp
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkData
import app.pokeplan.link.Person
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntegrationsTest {
    private val today = LocalDate(2026, 11, 24)

    @Test
    fun peopleAreTiedToAnEventAndSurviveReopening() {
        val storage = MemoryStorage()
        val s = IntegrationStore(storage)
        s.setPeople("ev1", listOf("p1", "p2", "p1", ""))
        assertEquals(listOf("p1", "p2"), s.peopleOf("ev1"), "repeats and blanks are dropped")
        assertEquals(listOf("ev1"), s.eventsOf("p2"))
        assertEquals(listOf("p1", "p2"), IntegrationStore(storage).peopleOf("ev1"), "kept between openings")
        s.setPeople("ev1", emptyList())
        assertTrue(s.data.eventPeople.isEmpty(), "no people removes the link record")
        s.setPeople("ev2", (1..30).map { "p$it" })
        assertEquals(IntegrationStore.MAX_PEOPLE, s.peopleOf("ev2").size)
    }

    @Test
    fun linksToGoneEventsOrPeopleArePrunedAndALiveSaveIsNotRewritten() {
        val s = IntegrationStore(MemoryStorage())
        s.setPeople("ev1", listOf("p1", "gone"))
        s.setPeople("ev-gone", listOf("p1"))
        s.prune(eventIds = setOf("ev1"), personIds = setOf("p1"))
        assertEquals(listOf(EventPeople("ev1", listOf("p1"))), s.data.eventPeople)
        val before = s.data
        s.prune(setOf("ev1"), setOf("p1"))
        assertTrue(before === s.data, "nothing to drop writes nothing")
    }

    @Test
    fun theCalendarSwitchesAreKeptAndResetClearsEverything() {
        val s = IntegrationStore(MemoryStorage())
        assertTrue(s.data.linkDatesInCalendar && s.data.linkFollowUpsInCalendar)
        s.setLinkDates(false); s.setPeople("ev1", listOf("p1"))
        assertFalse(s.data.linkDatesInCalendar)
        s.resetAll()
        assertEquals(IntegrationData(), s.data)
    }

    private val ana = Person("p1", "Ana Cruz", preferred = "Ana")
    private fun data(vararg dates: ImportantDate, follow: List<FollowUp> = emptyList()) =
        LinkData(people = listOf(ana), dates = dates.toList(), followUps = follow)

    @Test
    fun anAnnualBirthdayShowsEveryYearInTheRangeWithTheAgeAndNamesThePerson() {
        val d = data(ImportantDate("d1", DateKind.BIRTHDAY, "Birthday", 12, 5, year = 1990, personId = "p1"))
        val december = LinkInPlan.entries(d, LocalDate(2026, 11, 30), LocalDate(2026, 12, 6), today, dates = true, followUps = true, color = 0x123456)
        val e = december.single()
        assertEquals("Ana: Birthday (36)", e.title)
        assertTrue(e.allDay && e.calendarId == LinkInPlan.CALENDAR_ID && e.start.date == LocalDate(2026, 12, 5))
        assertEquals(0x123456L, e.color)
        val across = LinkInPlan.entries(d, LocalDate(2026, 12, 1), LocalDate(2028, 12, 31), today, true, true)
        assertEquals(listOf(LocalDate(2026, 12, 5), LocalDate(2027, 12, 5), LocalDate(2028, 12, 5)), across.map { it.start.date })
        assertTrue(LinkInPlan.entries(d, LocalDate(2026, 12, 6), LocalDate(2026, 12, 31), today, true, true).isEmpty())
        assertTrue(LinkInPlan.entries(d, LocalDate(2026, 11, 30), LocalDate(2026, 12, 6), today, dates = false, followUps = true).isEmpty(), "the switch turns dates off")
    }

    @Test
    fun aLeapDayIsObservedOnThe28thInOtherYearsAndOneOffDatesShowOnce() {
        val leap = data(ImportantDate("d1", DateKind.CUSTOM, "Leap day", 2, 29))
        assertEquals(LocalDate(2027, 2, 28), LinkInPlan.entries(leap, LocalDate(2027, 2, 1), LocalDate(2027, 3, 1), today, true, true).single().start.date)
        val once = data(ImportantDate("d2", DateKind.MILESTONE, "Move day", 3, 3, year = 2027, annual = false))
        assertEquals(1, LinkInPlan.entries(once, LocalDate(2027, 1, 1), LocalDate(2028, 12, 31), today, true, true).size)
        assertTrue(LinkInPlan.entries(once, LocalDate(2028, 1, 1), LocalDate(2028, 12, 31), today, true, true).isEmpty())
    }

    @Test
    fun onlyOpenFollowUpsWithADueDayShowAndOneAlreadyInPlanDoesNot() {
        val open = FollowUp("f1", "Call back", personId = "p1", due = LocalDate(2026, 11, 26))
        val done = FollowUp("f2", "Done one", due = LocalDate(2026, 11, 26), status = FollowUpStatus.COMPLETED)
        val inPlan = FollowUp("f3", "In plan", due = LocalDate(2026, 11, 26), converted = true)
        val noDay = FollowUp("f4", "Someday")
        val shown = LinkInPlan.entries(data(follow = listOf(open, done, inPlan, noDay)), today, LocalDate(2026, 12, 31), today, dates = true, followUps = true)
        assertEquals(listOf("Ana: Follow up: Call back"), shown.map { it.title })
    }

    @Test
    fun aTapFindsItsPersonAndAnEntryNotOursIsLeftAlone() {
        val d = data(ImportantDate("d1", DateKind.BIRTHDAY, "Birthday", 12, 5, personId = "p1"), ImportantDate("d2", DateKind.CUSTOM, "Rent day", 1, 1))
        val ids = LinkInPlan.entries(d, LocalDate(2026, 12, 1), LocalDate(2027, 1, 2), today, true, true).map { it.id }
        assertEquals(2, ids.size)
        assertTrue(ids.all { LinkInPlan.owns(it) })
        assertEquals(LinkTarget("p1", followUp = false), LinkInPlan.target(d, ids.first { "d1" in it }))
        assertEquals(LinkTarget(null, followUp = false), LinkInPlan.target(d, ids.first { "d2" in it }))
        assertNull(LinkInPlan.target(d, "link:date:gone:2026-12-05"), "what it pointed at is gone")
        assertFalse(LinkInPlan.owns("ev1@2026-12-05"))
        assertNotNull(LinkInPlan.target(data(follow = listOf(FollowUp("f1", "x"))), "link:follow:f1"))
    }
}
