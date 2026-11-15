package app.pokeplan.app.link

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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.Origin
import app.pokeplan.app.Tutorials
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class LinkStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        fun advanceMinutes(n: Int) { millis += n * 60_000L }
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
        override fun followUpTask(followUpId: String) = inner.followUpTask(followUpId)
        override fun planFollowUp(followUpId: String, title: String, planned: LocalDate?) = inner.planFollowUp(followUpId, title, planned)
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
        assertNull(done.record, "one real action, one completion: PLAN owns it while its task exists")
        assertEquals(LinkStore.IN_PLAN, done.refused)
        assertEquals(0, s.candies)
        assertTrue(s.game.data.linkPaid.isEmpty())
    }

    // --- the follow-up that became a PLAN task (LINK.md 2.4, 9.2) ----------------------------------------

    @Test
    fun aConvertedFollowUpIsPlansToFinishAndLinkNeverPaysForIt() {
        val s = setup()
        val f = s.followUp("Send the photos")
        val task = assertNotNull(s.link.convertToPlan(f.id))
        assertFalse(task.done)
        assertTrue(s.link.data.followUps.single().converted)
        assertEquals(task.taskId, s.link.convertToPlan(f.id)?.taskId, "the same follow-up gives the same task")
        assertEquals(1, s.game.data.tasks.count { !it.tutorial })
        // One real action, one completion: LINK refuses to finish what PLAN owns.
        assertEquals(LinkStore.IN_PLAN, s.link.complete(f.id).refused)
        s.link.cancel(f.id)
        assertEquals(FollowUpStatus.OPEN, s.link.data.followUps.single().status)
        // Finishing it in PLAN is mirrored in LINK, with no Postcard Trail event.
        s.clock.advanceMinutes(30)
        s.game.completeTask(task.taskId)
        s.clock.advanceMinutes(1); s.game.tick()
        s.link.syncFromPlan()
        val done = s.link.data.followUps.single()
        assertEquals(FollowUpStatus.COMPLETED, done.status)
        assertFalse(done.paid)
        assertTrue(s.spy.events.isEmpty())
        assertEquals(0, s.game.linkEggMeter())
    }

    @Test
    fun aFollowUpPlanFinishedCannotBeReopenedHereAndFollowsItsTaskBackToOpen() {
        val s = setup()
        val f = s.followUp("Send the photos")
        val task = assertNotNull(s.link.convertToPlan(f.id))
        s.clock.advanceMinutes(30)
        s.game.completeTask(task.taskId)
        s.link.syncFromPlan()
        assertEquals(FollowUpStatus.COMPLETED, s.link.data.followUps.single().status)
        s.link.reopen(f.id)
        assertEquals(FollowUpStatus.COMPLETED, s.link.data.followUps.single().status, "PLAN owns the completion, so it is reopened there")
        s.game.reopen(task.taskId)
        s.link.syncFromPlan()
        assertEquals(FollowUpStatus.OPEN, s.link.data.followUps.single().status, "LINK shows what the task is")
        assertTrue(s.spy.events.isEmpty())
    }

    @Test
    fun dueNowLeavesOutWhatPlanOwnsAndWhatIsNotDueYet() {
        val s = setup()
        val a = assertNotNull(s.link.addFollowUp("Late one", due = today.minus(2, DateTimeUnit.DAY)).record)
        val b = assertNotNull(s.link.addFollowUp("In Plan", due = today).record)
        s.link.addFollowUp("Later", due = today.plus(3, DateTimeUnit.DAY))
        s.link.addFollowUp("No day")
        assertEquals(setOf(a.id, b.id), s.link.dueNow().map { it.id }.toSet())
        s.link.convertToPlan(b.id)
        assertEquals(listOf(a.id), s.link.dueNow().map { it.id })
    }

    @Test
    fun aConvertedFollowUpStaysUnpaidEvenWhenItsTaskIsGone() {
        val s = setup()
        val f = s.followUp("Send the photos")
        val task = assertNotNull(s.link.convertToPlan(f.id))
        s.game.deleteTask(task.taskId)
        assertNull(s.link.planTask(f.id))
        val done = s.link.complete(f.id)
        assertEquals(FollowUpStatus.COMPLETED, done.record?.status)
        assertTrue(done.rewards.isEmpty())
        assertTrue(s.spy.events.isEmpty(), "the converted flag is never cleared")
    }

    @Test
    fun onlyAnOpenFollowUpCanBeHandedToPlan() {
        val s = setup()
        val f = s.followUp("Call about the trip")
        s.link.complete(f.id)
        assertNull(s.link.convertToPlan(f.id))
        assertNull(s.link.convertToPlan("nope"))
        assertTrue(s.game.data.tasks.none { it.link != null })
    }

    @Test
    fun aLinkRewardLeavesItsOwnEventInTheLedger() {
        val s = setup()
        s.link.complete(s.followUp("Send the photos").id)
        val note = s.game.data.ledgerNotes.single()
        assertEquals("LINK", note.module)
        assertTrue(note.eventId.startsWith("link:"))
        assertTrue(secrets.none { it in note.toString() })
    }
}
