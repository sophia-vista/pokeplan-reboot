package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkRulesTest {
    private val today = LocalDate(2026, 11, 8)
    private fun person(id: String, name: String, vararg more: Pair<String, String>) = Person(
        id, name, preferred = more.toMap()["preferred"].orEmpty(), phone = more.toMap()["phone"].orEmpty(), email = more.toMap()["email"].orEmpty(),
        organization = more.toMap()["org"].orEmpty(), category = more.toMap()["category"].orEmpty(),
    )
    private fun date(month: Int, day: Int, year: Int? = null, annual: Boolean = true, kind: DateKind = DateKind.BIRTHDAY, title: String = "Birthday") =
        ImportantDate("d$month-$day", kind, title, month, day, year, annual)

    @Test
    fun aPersonNeedsOnlyAName() {
        assertEquals(Check.Ok, LinkRules.checkPerson(Person("1", "Ana")))
        assertIs<Check.Refused>(LinkRules.checkPerson(Person("1", "  ")))
        assertIs<Check.Refused>(LinkRules.checkPerson(Person("1", "x".repeat(LinkRules.NAME_MAX + 1))))
        assertIs<Check.Refused>(LinkRules.checkPerson(Person("1", "Ana", phone = "1".repeat(LinkRules.FIELD_MAX + 1))))
    }

    @Test
    fun tagsAreTrimmedDeduplicatedAndLimited() {
        assertEquals(listOf("work", "Hiking"), LinkRules.tags(" work, #Hiking ,, WORK "))
        assertEquals(LinkRules.TAGS_MAX, LinkRules.tags((1..30).joinToString(",")).size)
    }

    @Test
    fun theDirectorySortsByNameOrRecentAndNeverByAnythingElse() {
        val data = LinkData(people = listOf(
            person("1", "Zed").copy(updatedMillis = 30, favorite = true),
            person("2", "Ana").copy(updatedMillis = 10),
            person("3", "Mo", "preferred" to "Bea").copy(updatedMillis = 20),
        ))
        assertEquals(listOf("Ana", "Mo", "Zed"), LinkRules.directory(data).map { it.name }, "by display name: Ana, Bea (Mo), Zed")
        assertEquals(listOf("Zed", "Mo", "Ana"), LinkRules.directory(data, sort = Sort.RECENT).map { it.name })
        assertEquals(listOf("Zed"), LinkRules.directory(data, favoritesOnly = true).map { it.name })
    }

    @Test
    fun theDirectoryFiltersByWordCategoryAndTag() {
        val data = LinkData(people = listOf(
            person("1", "Ana", "category" to "Family", "org" to "Acme").copy(tags = listOf("choir")),
            person("2", "Ben", "category" to "Friend"),
        ))
        assertEquals(listOf("Ana"), LinkRules.directory(data, query = "acme").map { it.name })
        assertEquals(listOf("Ben"), LinkRules.directory(data, category = "friend").map { it.name })
        assertEquals(listOf("Ana"), LinkRules.directory(data, tag = "Choir").map { it.name })
        assertEquals(listOf("Family", "Friend"), LinkRules.categories(data).filter { it in listOf("Family", "Friend") })
    }

    @Test
    fun duplicatesAreSuggestedByNameNumberOrEmailAndNeverMerged() {
        val data = LinkData(people = listOf(person("1", "Ana Cruz", "phone" to "+63 917 123 4567", "email" to "Ana@x.com")))
        assertEquals(1, LinkRules.possibleDuplicates(data, "ana cruz").size)
        assertEquals(1, LinkRules.possibleDuplicates(data, "Someone", phone = "+63 (917) 123-4567").size)
        assertEquals(1, LinkRules.possibleDuplicates(data, "Someone", email = " ana@X.com ").size)
        assertTrue(LinkRules.possibleDuplicates(data, "Ana Cruz", exceptId = "1").isEmpty(), "the person being edited is not their own duplicate")
        assertTrue(LinkRules.possibleDuplicates(data, "Other", phone = "12345").isEmpty(), "a short number is not a match")
    }

    @Test
    fun anAnnualDateWithoutAYearIsValidAndComesEveryYear() {
        val d = date(11, 20)
        assertEquals(Check.Ok, LinkRules.checkDate(d))
        val n = LinkRules.next(d, today)!!
        assertEquals(LocalDate(2026, 11, 20), n.day)
        assertEquals(12, n.daysAway)
        assertNull(n.years)
        assertEquals(LocalDate(2027, 3, 1), LinkRules.next(date(3, 1), today)!!.day, "already passed this year: next year")
        assertEquals(0, LinkRules.next(date(11, 8), today)!!.daysAway, "today counts")
    }

    @Test
    fun aKnownYearGivesTheNumberItMarks() {
        assertEquals(30, LinkRules.next(date(11, 20, 1996), today)!!.years)
        assertEquals(31, LinkRules.next(date(3, 1, 1996), today)!!.years, "next year's is the 31st")
        assertNull(LinkRules.next(date(11, 20, 2026), today)!!.years, "the year it began is not a number to mark")
    }

    @Test
    fun aLeapDayIsObservedOnTheTwentyEighthOutsideLeapYears() {
        val d = date(2, 29)
        assertEquals(Check.Ok, LinkRules.checkDate(d))
        assertEquals(LocalDate(2027, 2, 28), LinkRules.next(d, today)!!.day)
        assertEquals(LocalDate(2028, 2, 29), LinkRules.next(d, LocalDate(2028, 1, 10))!!.day)
        assertEquals(LocalDate(2028, 2, 29), LinkRules.next(d, LocalDate(2028, 2, 29))!!.day)
        assertEquals(LocalDate(2029, 2, 28), LinkRules.next(d, LocalDate(2028, 3, 1))!!.day)
        assertIs<Check.Refused>(LinkRules.checkDate(date(2, 29, 2027)), "2027 has no 29th")
        assertEquals(Check.Ok, LinkRules.checkDate(date(2, 29, 2028)))
        assertEquals(LocalDate(2100, 2, 28), LinkRules.observed(2, 29, 2100), "2100 is not a leap year")
    }

    @Test
    fun aOneTimeDateNeedsItsYearAndIsOnlyUpcomingUntilItPasses() {
        assertIs<Check.Refused>(LinkRules.checkDate(date(12, 1, null, annual = false)))
        val d = date(12, 1, 2026, annual = false)
        assertEquals(23, LinkRules.next(d, today)!!.daysAway)
        assertNull(LinkRules.next(d, LocalDate(2026, 12, 2)))
    }

    @Test
    fun datesWithImpossibleDaysAreRefused() {
        assertIs<Check.Refused>(LinkRules.checkDate(date(13, 1)))
        assertIs<Check.Refused>(LinkRules.checkDate(date(4, 31)))
        assertIs<Check.Refused>(LinkRules.checkDate(date(2, 30)))
        assertIs<Check.Refused>(LinkRules.checkDate(date(1, 1, 1500)))
        assertIs<Check.Refused>(LinkRules.checkDate(date(1, 1, title = " ")))
    }

    @Test
    fun upcomingListsDatesWithinTheWindowSoonestFirst() {
        val data = LinkData(dates = listOf(date(12, 1, title = "B"), date(11, 9, title = "A"), date(3, 3, title = "Far"), date(11, 9, title = "0")))
        assertEquals(listOf("0", "A", "B"), LinkRules.upcoming(data, today).map { it.date.title })
        assertEquals(listOf("0", "A"), LinkRules.upcoming(data, today, withinDays = 5).map { it.date.title })
    }

    @Test
    fun allDatesPutsPastOneTimeDatesLast() {
        val data = LinkData(dates = listOf(date(1, 1, 2020, annual = false, title = "Gone"), date(11, 9, title = "Soon"), date(5, 5, title = "Later")))
        assertEquals(listOf("Soon", "Later", "Gone"), LinkRules.allDates(data, today).map { it.first.title })
    }

    @Test
    fun aFollowUpNeedsOnlyATitle() {
        assertEquals(Check.Ok, LinkRules.checkFollowUp(FollowUp("1", "Call back")))
        assertIs<Check.Refused>(LinkRules.checkFollowUp(FollowUp("1", " ")))
    }

    @Test
    fun aFollowUpIsDueFromItsDayAndNeverMoreThanThat() {
        assertEquals(FollowUpState.UPCOMING, LinkRules.state(FollowUp("1", "a"), today))
        assertEquals(FollowUpState.UPCOMING, LinkRules.state(FollowUp("1", "a", due = today.let { LocalDate(2026, 11, 9) }), today))
        assertEquals(FollowUpState.DUE, LinkRules.state(FollowUp("1", "a", due = today), today))
        assertEquals(FollowUpState.DUE, LinkRules.state(FollowUp("1", "a", due = LocalDate(2026, 1, 1)), today), "a late one is just Due: no escalation")
        assertEquals(FollowUpState.COMPLETED, LinkRules.state(FollowUp("1", "a", status = FollowUpStatus.COMPLETED), today))
        assertEquals(FollowUpState.CANCELLED, LinkRules.state(FollowUp("1", "a", status = FollowUpStatus.CANCELLED), today))
    }

    @Test
    fun openFollowUpsListDatedOnesFirstThenTheRest() {
        val data = LinkData(followUps = listOf(
            FollowUp("a", "undated old", createdMillis = 1), FollowUp("b", "later", due = LocalDate(2026, 12, 1), createdMillis = 2),
            FollowUp("c", "soon", due = LocalDate(2026, 11, 9), createdMillis = 3), FollowUp("d", "done", status = FollowUpStatus.COMPLETED),
            FollowUp("e", "undated new", createdMillis = 5),
        ))
        assertEquals(listOf("c", "b", "a", "e"), LinkRules.open(data, today).map { it.id })
        assertEquals(listOf("d"), LinkRules.history(data).map { it.id })
    }

    @Test
    fun openFollowUpsCanBeFilteredToOnePerson() {
        val data = LinkData(followUps = listOf(FollowUp("a", "x", personId = "p1"), FollowUp("b", "y", personId = "p2"), FollowUp("c", "z")))
        assertEquals(listOf("a"), LinkRules.open(data, today, "p1").map { it.id })
    }

    @Test
    fun aFollowUpPaysOnceAndOnlyWhilePostcardTrailIsOn() {
        val f = FollowUp("1", "a", status = FollowUpStatus.COMPLETED, completedDay = today)
        assertTrue(LinkRules.pays(LinkData(followUps = listOf(f)), f, today))
        assertTrue(!LinkRules.pays(LinkData(followUps = listOf(f.copy(paid = true))), f.copy(paid = true), today), "already paid: a reopen and a second completion pay nothing")
        assertTrue(!LinkRules.pays(LinkData(settings = LinkSettings(postcardTrail = false)), f, today))
    }

    @Test
    fun todaysShareIsLimited() {
        val paid = (1..LinkRules.FOLLOW_UPS_PAID_PER_DAY).map { FollowUp("p$it", "x", status = FollowUpStatus.COMPLETED, completedDay = today, paid = true) }
        val next = FollowUp("n", "y", status = FollowUpStatus.COMPLETED, completedDay = today)
        assertTrue(!LinkRules.pays(LinkData(followUps = paid + next), next, today))
        assertTrue(LinkRules.pays(LinkData(followUps = paid + next), next, LocalDate(2026, 11, 9)), "tomorrow is a new day")
    }

    @Test
    fun searchFindsPeopleNotesDatesAndFollowUpsAndAnEmptyQueryNothing() {
        val data = LinkData(
            people = listOf(person("1", "Ana Cruz", "org" to "Acme")),
            notes = listOf(PersonNote("n1", "1", "Loves hiking\nand tea")),
            dates = listOf(date(11, 20, title = "Ana's birthday").copy(personId = "1")),
            followUps = listOf(FollowUp("f1", "Send the hiking photos", personId = "1")),
        )
        assertTrue(LinkRules.search(data, "  ").isEmpty())
        assertEquals(listOf(HitKind.PERSON, HitKind.DATE, HitKind.FOLLOW_UP), LinkRules.search(data, "ana").map { it.kind }, "a date or follow-up is found by the name of its person too")
        val hiking = LinkRules.search(data, "HIKING")
        assertEquals(listOf(HitKind.NOTE, HitKind.FOLLOW_UP), hiking.map { it.kind })
        assertEquals("1", hiking.first().id, "a note's hit opens its person")
        assertEquals(listOf(HitKind.PERSON), LinkRules.search(data, "acme").map { it.kind })
    }

    @Test
    fun aPersonIsFoundByPhoneEmailOrAddressHoweverThePhoneIsSpaced() {
        val ana = Person("1", "Ana Cruz", phone = "+63 917 000 1234", email = "ana@example.com", address = "12 Mabini Street")
        val data = LinkData(people = listOf(ana, Person("2", "Ben")))
        fun ids(q: String) = LinkRules.directory(data, q).map { it.id }
        assertEquals(listOf("1"), ids("917000"), "digits match across spaces")
        assertEquals(listOf("1"), ids("0001234"))
        assertEquals(listOf("1"), ids("ana@example"))
        assertEquals(listOf("1"), ids("mabini"))
        assertEquals(listOf("1"), LinkRules.search(data, "91700").map { it.id })
        assertTrue(ids("99").isEmpty() && LinkRules.search(data, "99").isEmpty(), "one or two digits are not a phone search")
    }

    @Test
    fun theDocumentSurvivesARoundTrip() {
        val data = LinkData(
            people = listOf(Person("1", "Ana", tags = listOf("a"))), notes = listOf(PersonNote("n", "1", "t", 5)), dates = listOf(date(2, 29, 2000)),
            followUps = listOf(FollowUp("f", "x", due = today, paid = true)), settings = LinkSettings(postcardTrail = false, categories = listOf("Neighbour")),
        )
        val json = Json { encodeDefaults = true }
        assertEquals(data, json.decodeFromString(LinkData.serializer(), json.encodeToString(LinkData.serializer(), data)))
    }

    private fun groupData() = LinkData(
        people = listOf(person("1", "Zed"), person("2", "Ana"), person("3", "Mo"), person("4", "Bea")),
        settings = LinkSettings(groups = listOf(LinkGroup("g-club", "Climbing club"))),
    ).let { d -> d.copy(people = d.people.map { if (it.id in setOf("1", "3")) it.copy(groupIds = listOf("g-club")) else it }) }

    @Test
    fun aPersonCanBeInSeveralGroupsAndTheDirectoryFiltersByOne() {
        val d = groupData()
        assertEquals(listOf("Mo", "Zed"), LinkRules.directory(d, group = "g-club").map { it.name })
        val both = d.copy(people = d.people.map { if (it.id == "2") it.copy(groupIds = listOf("g-club", "g-other")) else it })
        assertEquals(listOf("Ana", "Mo", "Zed"), LinkRules.directory(both, group = "g-club").map { it.name })
        assertEquals(listOf("Climbing club"), LinkRules.groupNames(d, d.people.first { it.id == "1" }))
    }

    @Test
    fun myOrderPutsTheListedPeopleFirstThenTheRestByName() {
        val d = groupData().let { it.copy(settings = it.settings.copy(order = listOf("3", "1"))) }
        assertEquals(listOf("Mo", "Zed", "Ana", "Bea"), LinkRules.directory(d, sort = Sort.MANUAL).map { it.name })
        assertEquals(listOf("Ana", "Bea", "Mo", "Zed"), LinkRules.directory(d).map { it.name }, "name sort is unchanged")
    }

    @Test
    fun movingSwapsWithTheNeighbourOnScreenAndNobodyElseMoves() {
        val d = groupData()
        // No saved order yet: the full order is by name, Ana Bea Mo Zed.
        assertEquals(listOf("2", "4", "3", "1"), LinkRules.fullOrder(d))
        val shown = LinkRules.directory(d, sort = Sort.MANUAL).map { it.id }
        assertEquals(listOf("4", "2", "3", "1"), LinkRules.moved(d, shown, "4", -1), "Bea up one swaps with Ana")
        assertEquals(listOf("4", "2", "3", "1"), LinkRules.moved(d, shown, "2", +1), "Ana down one swaps with Bea")
        assertEquals(LinkRules.fullOrder(d), LinkRules.moved(d, shown, "2", -1), "the first person cannot move up")
        // Moving inside a group view swaps with the neighbour in that view; people outside it keep their places.
        val club = LinkRules.directory(d, group = "g-club", sort = Sort.MANUAL).map { it.id }
        assertEquals(listOf("2", "4", "1", "3"), LinkRules.moved(d, club, "3", +1), "Mo and Zed swap, Ana and Bea stay")
    }

    @Test
    fun theGroupSortListsPeopleInThePlayersGroupOrderAndPeopleWithNoGroupLast() {
        val data = LinkData(
            people = listOf(
                person("1", "Zed").copy(groupIds = listOf("g-b")),
                person("2", "Ana").copy(groupIds = listOf("g-a")),
                person("3", "Mo").copy(groupIds = listOf("g-b", "g-a")),
                person("4", "Bea"),
                person("5", "Me").copy(isMe = true, groupIds = listOf("g-b")),
            ),
            settings = LinkSettings(groups = listOf(LinkGroup("g-a", "Family"), LinkGroup("g-b", "Climbing club"))),
        )
        assertEquals(listOf("Me", "Ana", "Mo", "Zed", "Bea"), LinkRules.directory(data, sort = Sort.GROUP).map { it.name }, "Me stays first, then the first group in the player's order, then the second, then nobody's group")
        assertEquals("Family", LinkRules.groupOf(data, data.people.first { it.id == "2" }), "the first group in the player's order")
        assertEquals("Family", LinkRules.groupOf(data, data.people.first { it.id == "3" }), "the heading follows the player's group order, not the order the groups were ticked")
        assertNull(LinkRules.groupOf(data, data.people.first { it.id == "4" }))
        assertEquals(listOf("Me", "Ana", "Bea", "Mo", "Zed"), LinkRules.directory(data).map { it.name }, "name sort is unchanged apart from Me pinned first")
    }

    @Test
    fun groupNamesAreCheckedAndTheMainCategoryComesBackOnceFromOneCategoryGroup() {
        val d = groupData()
        assertIs<Check.Refused>(LinkRules.checkGroup(d, "  "))
        assertIs<Check.Refused>(LinkRules.checkGroup(d, "climbing CLUB"))
        assertEquals(Check.Ok, LinkRules.checkGroup(d, "climbing CLUB", exceptId = "g-club"), "renaming to its own name differing in case is fine")
        val old = LinkData(
            people = listOf(
                person("1", "Ana").copy(groupIds = listOf("g-friend")),
                person("2", "Ben").copy(groupIds = listOf("g-club")),
                person("3", "Cy").copy(groupIds = listOf("g-friend", "g-family")),
                person("4", "Me").copy(isMe = true, category = "Me"),
            ),
            settings = LinkSettings(groups = listOf(LinkGroup("g-friend", "Friend"), LinkGroup("g-club", "Climbing club"), LinkGroup("g-family", "Family"))),
        )
        val back = LinkRules.withCategoriesBack(old)
        assertEquals("Friend", back.people.first { it.id == "1" }.category, "one old category word gives the main category back")
        assertEquals("", back.people.first { it.id == "2" }.category, "a group that is not a relationship word is not a category")
        assertEquals("", back.people.first { it.id == "3" }.category, "two possible words: left without one rather than guessed")
        assertEquals("Me", back.people.first { it.id == "4" }.category, "Me is not touched")
        assertEquals(old.settings.groups, back.settings.groups, "the groups stay: a group never replaces the category again")
        assertEquals(listOf("g-friend"), back.people.first { it.id == "1" }.groupIds)
        assertTrue(back.settings.categoriesRestored)
        assertEquals(back, LinkRules.withCategoriesBack(back), "running it again changes nothing")
        val kept = LinkData(people = listOf(person("1", "Ana", "category" to "Friend")), settings = LinkSettings(categories = listOf("Neighbours")))
        assertEquals("Friend", LinkRules.withCategoriesBack(kept).people.single().category, "a category that is still there is kept")
        assertEquals(listOf("Neighbours"), LinkRules.withCategoriesBack(kept).settings.categories)
    }
}
