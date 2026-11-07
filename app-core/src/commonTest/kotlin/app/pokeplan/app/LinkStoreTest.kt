package app.pokeplan.app

import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.LinkKinds
import app.pokeplan.link.LinkRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 7)

    /** A world that records every event it is sent, so a test can see exactly what crosses the boundary. */
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<LinkEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun link(event: LinkEvent): List<Reward>? { events += event; return inner.link(event) }
    }

    private class Setup(val game: GameStore, val storage: MemoryStorage, var link: LinkStore, val spy: Spy, val clock: FakeClock)

    private fun setup(started: Boolean = true): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val spy = Spy(game)
        val storage = MemoryStorage()
        return Setup(game, storage, LinkStore(storage, clock, spy, utc, rng = Random(3)), spy, clock)
    }

    private fun Setup.reopen() { link = LinkStore(storage, clock, spy, utc, rng = Random(11)) }

    private val Setup.candies get() = game.data.player.inventory[Items.RARE_CANDY] ?: 0

    private fun Setup.followUp(title: String) = assertNotNull(link.addFollowUp(title).record)

    private val secrets = listOf("Zorblax Quux", "+63 917 000 1234", "zorblax@example.com", "Quuxville", "zorblax-tag", "Zorblax-note-77", "Zorblax-follow-55", "Zorblax-date-9")

    // --- people ------------------------------------------------------------------------------------------------------------------

    @Test
    fun aPersonNeedsOnlyANameAndNothingPays() {
        val s = setup()
        assertNull(s.link.addPerson(" ").record)
        assertNotNull(s.link.addPerson("Ana").record)
        assertTrue(s.spy.events.isEmpty(), "adding people is never an event")
        assertEquals(0, s.candies)
    }

    @Test
    fun deletingAPersonAsksWhatToDoWithTheirDatesAndFollowUps() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson("Ana").record)
        s.link.addNote(p.id, "likes tea")
        s.link.addDate(DateKind.BIRTHDAY, "Birthday", 3, 4, personId = p.id)
        s.link.addFollowUp("Call", personId = p.id)
        s.link.deletePerson(p.id, keepDates = true, keepFollowUps = true)
        assertTrue(s.link.data.people.isEmpty() && s.link.data.notes.isEmpty())
        assertEquals(1, s.link.data.dates.size); assertNull(s.link.data.dates.single().personId)
        assertEquals(1, s.link.data.followUps.size); assertNull(s.link.data.followUps.single().personId)
        val q = assertNotNull(s.link.addPerson("Ben").record)
        s.link.addDate(DateKind.CUSTOM, "Moving day", 5, 6, personId = q.id)
        s.link.addFollowUp("Visit", personId = q.id)
        s.link.deletePerson(q.id, keepDates = false, keepFollowUps = false)
        assertEquals(1, s.link.data.dates.size, "Ben's date went with him")
        assertEquals(1, s.link.data.followUps.size, "Ben's follow-up went with him")
    }

    @Test
    fun aNoteNeedsAPersonAndSomeWords() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson("Ana").record)
        assertNull(s.link.addNote(p.id, "  ").record)
        assertNull(s.link.addNote("missing", "text").record)
        val n = assertNotNull(s.link.addNote(p.id, "Prefers texts").record)
        assertEquals(listOf(n), s.link.notesOf(p.id))
        assertEquals("Prefers calls", s.link.updateNote(n.id, "Prefers calls").record?.text)
    }

    @Test
    fun anImpossibleDateIsRefusedWithAReason() {
        val s = setup()
        assertNotNull(s.link.addDate(DateKind.BIRTHDAY, "Leap", 2, 29).record)
        val bad = s.link.addDate(DateKind.BIRTHDAY, "Bad", 2, 30)
        assertNull(bad.record); assertNotNull(bad.refused)
    }

    // --- follow-ups and Postcard Trail ----------------------------------------------------------------------------------------------

    @Test
    fun aFinishedFollowUpPaysOnceHoweverOftenItIsReopenedAndFinished() {
        val s = setup()
        val f = s.followUp("Send photos")
        assertEquals(0, s.candies)
        val first = s.link.complete(f.id)
        assertTrue(assertNotNull(first.record).paid)
        val paid = s.candies
        assertTrue(paid >= RewardEngine.LINK_CANDIES.getValue(LinkMoment.FOLLOW_UP))
        s.link.reopen(f.id)
        assertEquals(FollowUpStatus.OPEN, s.link.data.followUps.single().status)
        val again = s.link.complete(f.id)
        assertTrue(again.rewards.isEmpty())
        assertEquals(paid, s.candies, "no second payment")
        assertEquals(1, s.spy.events.size, "the second completion never even reached the game")
    }

    @Test
    fun cancellingEditingAndReschedulingNeverPay() {
        val s = setup()
        val f = s.followUp("Maybe")
        s.link.updateFollowUp(f.copy(title = "Maybe later", due = today.let { LocalDate(2026, 11, 20) }))
        s.link.cancel(f.id)
        assertEquals(FollowUpStatus.CANCELLED, s.link.data.followUps.single().status)
        assertEquals(0, s.candies)
        assertTrue(s.spy.events.isEmpty())
        assertNull(s.link.complete(f.id).record, "a cancelled follow-up must be reopened first")
        assertTrue(s.link.data.followUps.single().let { !it.paid })
    }

    @Test
    fun postcardTrailOffPaysNothingAndKeepsEveryRecord() {
        val s = setup()
        s.link.setPostcardTrail(false)
        val f = s.followUp("Quiet one")
        val done = s.link.complete(f.id)
        assertEquals(FollowUpStatus.COMPLETED, done.record?.status)
        assertFalse(done.record!!.paid)
        assertTrue(s.spy.events.isEmpty())
        assertEquals(0, s.candies)
        s.link.setPostcardTrail(true)
        s.link.reopen(f.id)
        assertTrue(assertNotNull(s.link.complete(f.id).record).paid, "turned on again, it was never paid, so it pays now")
    }

    @Test
    fun todaysShareIsLimitedAndTomorrowIsANewDay() {
        val s = setup()
        val limit = LinkRules.FOLLOW_UPS_PAID_PER_DAY
        val ids = (1..limit + 1).map { s.followUp("Item $it").id }
        ids.forEach { s.link.complete(it) }
        assertEquals(limit, s.link.data.followUps.count { it.paid })
        s.clock.advanceDays(1)
        s.link.reopen(ids.last())
        assertTrue(assertNotNull(s.link.complete(ids.last()).record).paid)
    }

    @Test
    fun theEventLeavingLinkCarriesNothingButTheContract() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson(secrets[0], phone = secrets[1], email = secrets[2], organization = secrets[3], tags = listOf(secrets[4])).record)
        s.link.addNote(p.id, secrets[5])
        s.link.addDate(DateKind.CUSTOM, secrets[7], 1, 2, personId = p.id)
        val f = assertNotNull(s.link.addFollowUp(secrets[6], personId = p.id, note = secrets[6]).record)
        s.link.complete(f.id)
        val event = s.spy.events.single()
        val text = Json.encodeToString(LinkEvent.serializer(), event)
        secrets.forEach { assertFalse(it in text, "the event must not hold $it") }
        val shared = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        secrets.forEach { assertFalse(it in shared, "the shared save must not hold $it") }
        val log = s.game.data.rewardLog.joinToString("\n") { it.source }
        secrets.forEach { assertFalse(it in log, "the reward log must not hold $it") }
        assertEquals(LinkEvent.MODULE, event.module)
        assertEquals("link:followup:${f.id}", event.id)
    }

    @Test
    fun theLogSaysOnlyLinkAndTheKindOfMoment() {
        val s = setup()
        s.link.complete(s.followUp("A").id)
        assertTrue(s.game.data.rewardLog.any { it.source == "Link: Postcard Trail" })
    }

    // --- the track and the egg -----------------------------------------------------------------------------------------------------

    @Test
    fun threeFinishedFollowUpsFindAPostcardEggIntoLinksPartyAndTheTrackWraps() {
        val s = setup()
        val size = RewardEngine.LINK_EGG_METER_SIZE / RewardEngine.LINK_EGG_POINTS.getValue(LinkMoment.FOLLOW_UP)
        assertEquals(3, size)
        for (i in 1..size) {
            assertTrue(assertNotNull(s.link.complete(s.followUp("F$i").id).record).paid)
            if (i < size) {
                assertEquals(i, s.game.linkEggMeter())
                assertEquals(0, s.game.data.player.partyOf(PartyIds.LINK).size)
            }
        }
        val egg = s.game.data.player.partyOf(PartyIds.LINK).single()
        assertEquals(Origin.How.POSTCARD_EGG, s.game.data.origins[egg.id]?.how)
        assertEquals(Region.LINK, s.game.data.origins[egg.id]?.region)
        assertEquals(egg.id, s.game.data.player.linkLead?.id)
        assertEquals(0, s.game.linkEggMeter())
    }

    // --- the tutorial ------------------------------------------------------------------------------------------------------------

    @Test
    fun thePostcardTutorialIsOfferedOnlyWhileLinkHasNoRecords() {
        assertFalse(setup(started = false).game.tutorialOffered(Tutorials.LINK_POSTCARD, linkRecords = 0))
        val s = setup()
        assertTrue(s.game.tutorialOffered(Tutorials.LINK_POSTCARD, linkRecords = 0))
        assertFalse(s.game.tutorialOffered(Tutorials.LINK_POSTCARD, linkRecords = 1))
    }

    @Test
    fun thePostcardTutorialGivesOnePokemonIntoLinksPartyAsLeadOnceWithNoRecordMeterOrCap() {
        val s = setup()
        val egg = assertNotNull(s.game.completeTutorial(Tutorials.LINK_POSTCARD)).filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(egg.id, s.game.data.player.partyOf(PartyIds.LINK).single().id)
        assertEquals(Origin.How.POSTCARD_EGG, s.game.data.origins[egg.id]?.how)
        assertEquals(egg.id, s.game.data.player.linkLead?.id)
        assertEquals(0, s.game.linkEggMeter())
        assertTrue(s.game.data.linkPaid.isEmpty() && s.game.data.activeDays.isEmpty())
        assertTrue(s.link.data.people.isEmpty() && s.link.data.followUps.isEmpty())
        assertNull(s.game.completeTutorial(Tutorials.LINK_POSTCARD))
    }

    // --- export and erase -------------------------------------------------------------------------------------------------------------

    @Test
    fun exportAndEraseWorkByKindAndTouchNothingElse() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson("Ana").record)
        s.link.addNote(p.id, "n")
        s.link.addDate(DateKind.BIRTHDAY, "B", 1, 1, personId = p.id)
        val f = s.followUp("F")
        s.link.complete(f.id)
        val before = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        val people = Json.parseToJsonElement(s.link.export(setOf(LinkKinds.PEOPLE))).jsonObject
        assertTrue("people" in people && "notes" in people && "dates" !in people && "followUps" !in people)
        s.link.erase(LinkKinds.PEOPLE)
        assertTrue(s.link.data.people.isEmpty() && s.link.data.notes.isEmpty())
        assertEquals(1, s.link.data.dates.size); assertNull(s.link.data.dates.single().personId)
        assertEquals(1, s.link.data.followUps.size)
        s.link.eraseAll()
        assertTrue(s.link.data.dates.isEmpty() && s.link.data.followUps.isEmpty())
        assertEquals(before, GameStore.json.encodeToString(AppData.serializer(), s.game.data), "erasing Link leaves the game as it was")
    }

    @Test
    fun theDocumentIsKeptBetweenOpenings() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson("Ana", tags = listOf("choir")).record)
        s.link.addFollowUp("Call", personId = p.id)
        s.link.addCategory("Neighbour")
        s.link.markTutorialSeen()
        val before = s.link.data
        s.reopen()
        assertEquals(before, s.link.data)
    }

    @Test
    fun searchAndDuplicatesGoThroughTheStore() {
        val s = setup()
        s.link.addPerson("Ana Cruz", phone = "0917 123 4567")
        assertEquals(1, s.link.search("cruz").size)
        assertEquals(1, s.link.possibleDuplicates("Someone", phone = "0917 123 4567").size)
    }

    @Test
    fun aFollowUpConvertedToAPlanTaskIsNeverPaidByLink() {
        val s = setup()
        val f = s.followUp("Ask about the visit")
        val task = assertNotNull(s.game.convertFollowUp(f.id, f.title))
        assertEquals(task.id, s.game.linkedTask(app.pokeplan.plan.LinkKind.LINK_FOLLOW_UP, f.id)?.id)
        assertEquals(task.id, assertNotNull(s.game.convertFollowUp(f.id, f.title)).id, "converting again gives the same task")
        val done = s.link.complete(f.id)
        assertFalse(done.record!!.paid, "LINK pays nothing for a converted follow-up")
        assertEquals(0, s.candies)
        assertTrue(s.game.data.linkPaid.isEmpty())
    }
}
