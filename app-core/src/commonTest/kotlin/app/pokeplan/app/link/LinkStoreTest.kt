package app.pokeplan.app.link

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.PartialDate
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

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 7)

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

    private fun card(id: String, name: String, phone: String = "", email: String = "", org: String = "", b: app.pokeplan.link.ContactBirthday? = null) =
        app.pokeplan.link.ContactCard(id, name, listOfNotNull(phone.ifBlank { null }), listOfNotNull(email.ifBlank { null }), org, b)

    @Test
    fun importingMakesPeopleAndBirthdaysWithTheReminderChosenAndPaysNothing() {
        val s = setup()
        val result = s.link.importContacts(
            listOf(
                app.pokeplan.link.ImportPick(card("k1", "Ana Cruz", "0917 123 4567", "ana@x.com", "Acme", app.pokeplan.link.ContactBirthday(3, 14, 1990))),
                app.pokeplan.link.ImportPick(card("k2", "Ben", b = app.pokeplan.link.ContactBirthday(2, 29))),
                app.pokeplan.link.ImportPick(card("k3", "Cy")),
            ),
            remindDays = 1,
        )
        assertEquals(app.pokeplan.link.ImportResult(added = 3, linked = 0, birthdays = 2, skipped = 0), result)
        assertEquals(listOf("Ana Cruz", "Ben", "Cy"), s.link.data.people.map { it.name })
        assertEquals("k1", s.link.data.people[0].sourceId)
        assertEquals("Acme", s.link.data.people[0].organization)
        assertEquals(listOf(1, 1), s.link.data.dates.map { it.remindDays })
        assertTrue(s.link.data.settings.reminders, "choosing a reminder turns Link's reminders on")
        assertTrue(s.spy.events.isEmpty() && s.candies == 0, "importing is never a reward")
    }

    @Test
    fun noReminderChoiceLeavesRemindersOffAndASecondImportChangesNothing() {
        val s = setup()
        val pick = app.pokeplan.link.ImportPick(card("k1", "Ana", b = app.pokeplan.link.ContactBirthday(3, 14)))
        s.link.importContacts(listOf(pick))
        assertFalse(s.link.data.settings.reminders)
        assertNull(s.link.data.dates.single().remindDays)
        val before = s.link.data
        val again = s.link.importContacts(listOf(pick))
        assertEquals(before, s.link.data, "a second import of the same contact is a no-op")
        assertEquals(app.pokeplan.link.ImportResult(skipped = 1), again)
    }

    @Test
    fun linkingFillsEmptyFieldsOnlyAndNeverAddsASecondBirthday() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana", phone = "111", category = "Friend").record)
        s.link.addDate(DateKind.BIRTHDAY, "Ana's birthday", 5, 6, personId = ana.id)
        val result = s.link.importContacts(
            listOf(app.pokeplan.link.ImportPick(card("k1", "Ana C.", "999", "ana@x.com", b = app.pokeplan.link.ContactBirthday(3, 14)), linkTo = ana.id)),
            remindDays = 0,
        )
        assertEquals(app.pokeplan.link.ImportResult(added = 0, linked = 1, birthdays = 0, skipped = 0), result)
        val p = s.link.data.people.single()
        assertEquals("Ana", p.name); assertEquals("111", p.phone); assertEquals("Friend", p.category, "the main category stays a category, not a group")
        assertTrue(app.pokeplan.link.LinkRules.groupNames(s.link.data, p).isEmpty(), "adding a category makes no group")
        assertEquals("ana@x.com", p.email); assertEquals("k1", p.sourceId)
        assertEquals(1, s.link.data.dates.size, "she already had a birthday")
        assertEquals(5, s.link.data.dates.single().month)
        assertFalse(s.link.data.settings.reminders, "no birthday was added, so nothing asked for a reminder")
    }

    @Test
    fun linkingToAPersonWhoIsGoneIsSkippedAndAnImportCanBeUndone() {
        val s = setup()
        val snapshot = s.link.data
        val r = s.link.importContacts(listOf(app.pokeplan.link.ImportPick(card("k1", "Ana"), linkTo = "missing"), app.pokeplan.link.ImportPick(card("k2", "Ben"))))
        assertEquals(1, r.added); assertEquals(1, r.skipped)
        s.link.restore(snapshot)
        assertTrue(s.link.data.people.isEmpty())
    }

    @Test
    fun addingADateCanAskForItsReminderInTheSameStep() {
        val s = setup()
        assertFalse(s.link.data.settings.reminders)
        s.link.addDate(DateKind.CUSTOM, "Moving day", 5, 6)
        assertFalse(s.link.data.settings.reminders)
        val d = assertNotNull(s.link.addDate(DateKind.BIRTHDAY, "Birthday", 3, 4, remindDays = 3).record)
        assertEquals(3, d.remindDays)
        assertTrue(s.link.data.settings.reminders)
        assertNull(assertNotNull(s.link.addDate(DateKind.CUSTOM, "X", 1, 2, remindDays = 5).record).remindDays, "only the presets are accepted")
    }

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

    @Test
    fun aConvertedFollowUpIsPlansToFinishAndLinkNeverPaysForIt() {
        val s = setup()
        val f = s.followUp("Send the photos")
        val task = assertNotNull(s.link.convertToPlan(f.id))
        assertFalse(task.done)
        assertTrue(s.link.data.followUps.single().converted)
        assertEquals(task.taskId, s.link.convertToPlan(f.id)?.taskId, "the same follow-up gives the same task")
        assertEquals(1, s.game.data.tasks.count { !it.tutorial })
        assertEquals(LinkStore.IN_PLAN, s.link.complete(f.id).refused)
        s.link.cancel(f.id)
        assertEquals(FollowUpStatus.OPEN, s.link.data.followUps.single().status)
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

    @Test
    fun myCardIsOneAndPinnedFirstAndTheProfileSurvivesAReopen() {
        val s = setup()
        s.link.addPerson("Aaron")
        val me = assertNotNull(s.link.createMe("Sophia").record)
        assertTrue(me.isMe)
        assertEquals(me.id, s.link.createMe("Someone else").record!!.id)
        assertEquals(1, s.link.data.people.count { it.isMe })
        assertEquals(listOf("Sophia", "Aaron"), s.link.directory().map { it.name })
        val profile = app.pokeplan.link.Profile(
            socials = listOf(app.pokeplan.link.Social("Instagram", "@zorblax"), app.pokeplan.link.Social("TikTok", " ")),
            likes = listOf("Ramen", "ramen", " ", "Rain"), dislikes = listOf("Loud chewing"),
            answers = listOf(app.pokeplan.link.Answer("Motto", "Be kind"), app.pokeplan.link.Answer("Hobby", "")),
            mbti = "infp", enneagram = 4, enneagramWing = 5, loveLanguages = listOf(app.pokeplan.link.LoveLanguage.TIME, app.pokeplan.link.LoveLanguage.TIME, app.pokeplan.link.LoveLanguage.WORDS),
            attachment = app.pokeplan.link.Attachment.SECURE, disc = listOf(app.pokeplan.link.Disc.I),
            birth = app.pokeplan.link.Birth(minutes = 8 * 60 + 5, latitude = 14.6, longitude = 121.0, placeName = "Manila", kuaFormula = "B"),
        )
        val saved = assertNotNull(s.link.updateProfile(me.id, profile).record).profile
        assertEquals(listOf("Ramen", "Rain"), saved.likes)
        assertEquals(1, saved.socials.size)
        assertEquals(1, saved.answers.size)
        assertEquals("INFP", saved.mbti)
        assertEquals(5, saved.enneagramWing)
        assertEquals(listOf(app.pokeplan.link.LoveLanguage.TIME, app.pokeplan.link.LoveLanguage.WORDS), saved.loveLanguages)
        s.reopen()
        val again = s.link.data.people.first { it.isMe }
        assertEquals(saved, again.profile)
        assertEquals(listOf(me.id), s.link.directory("zorblax").map { it.id })
        assertTrue(assertNotNull(s.link.updatePerson(again.copy(phone = "0917 000 1234")).record).isMe)
        assertEquals(saved, s.link.data.people.first { it.isMe }.profile)
    }

    @Test
    fun aWingMustBeNextToTheTypeAndABirthdayIsKeptOnce() {
        val s = setup()
        val p = assertNotNull(s.link.addPerson("Mika").record)
        assertEquals(0, assertNotNull(s.link.updateProfile(p.id, app.pokeplan.link.Profile(enneagram = 4, enneagramWing = 7)).record).profile.enneagramWing)
        s.link.setBirthday(p.id, 11, 1, 1990)
        s.link.setBirthday(p.id, 11, 2, 1990)
        val birthdays = s.link.data.dates.filter { it.personId == p.id && it.kind == DateKind.BIRTHDAY }
        assertEquals(1, birthdays.size)
        assertEquals(2, birthdays.single().day)
        val insights = app.pokeplan.link.Insights.of(s.link.data, s.link.data.people.first { it.id == p.id })
        assertEquals(LocalDate(1990, 11, 2), insights.date)
        assertEquals("Horse", insights.zodiac!!.animal)
        assertNull(insights.western!!.rising)
    }

    @Test
    fun profilesAndBirthChartsPayNothingAndSendNoEvent() {
        val s = setup()
        val before = s.candies
        val me = assertNotNull(s.link.createMe("Sophia").record)
        s.link.updateProfile(me.id, app.pokeplan.link.Profile(likes = listOf("Zorblax-like"), mbti = "ENFP"))
        s.link.setBirthday(me.id, 5, 17, 1999)
        assertTrue(s.spy.events.isEmpty())
        assertEquals(before, s.candies)
        assertTrue("Zorblax-like" in s.link.export(setOf(LinkKinds.PEOPLE)))
        s.link.erase(LinkKinds.PEOPLE)
        assertTrue(s.link.data.people.isEmpty())
    }

    @Test
    fun groupsAreMadeRenamedAndDeletedWithoutTouchingPeople() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val club = assertNotNull(s.link.addGroup("Climbing club").record)
        assertNull(s.link.addGroup("climbing CLUB").record, "a name already used is refused")
        assertNotNull(s.link.addGroup("Family").record)
        s.link.setGroups(ana.id, listOf(club.id, "g-nope", club.id))
        assertEquals(listOf(club.id), s.link.data.people.single().groupIds, "unknown ids and repeats are dropped")
        assertEquals(listOf("Ana"), s.link.directory(group = club.id).map { it.name })
        assertNotNull(s.link.renameGroup(club.id, "Boulder crew").record)
        s.reopen()
        assertEquals(listOf("Boulder crew"), app.pokeplan.link.LinkRules.groupNames(s.link.data, s.link.data.people.single()), "groups survive a reopen")
        s.link.deleteGroup(club.id)
        assertEquals(listOf("Ana"), s.link.data.people.map { it.name }, "deleting a group deletes nobody")
        assertTrue(s.link.data.people.single().groupIds.isEmpty())
    }

    @Test
    fun myOrderIsTheMovesThePlayerMadeAndNeverPaysAnything() {
        val s = setup()
        val ids = listOf("Ana", "Bea", "Cy").map { assertNotNull(s.link.addPerson(it).record).id }
        val shown = { s.link.directory(sort = app.pokeplan.link.Sort.MANUAL).map { it.name } }
        assertEquals(listOf("Ana", "Bea", "Cy"), shown(), "before any move it follows the names")
        s.link.moveInOrder(ids[2], s.link.directory(sort = app.pokeplan.link.Sort.MANUAL).map { it.id }, -1)
        assertEquals(listOf("Ana", "Cy", "Bea"), shown())
        s.link.moveInOrder(ids[0], s.link.directory(sort = app.pokeplan.link.Sort.MANUAL).map { it.id }, -1)
        assertEquals(listOf("Ana", "Cy", "Bea"), shown(), "the first one cannot move up")
        assertNotNull(s.link.addPerson("Aaron").record)
        assertEquals(listOf("Ana", "Cy", "Bea", "Aaron"), shown(), "a new person is listed after the arranged ones")
        s.reopen()
        assertEquals(listOf("Ana", "Cy", "Bea", "Aaron"), shown(), "the order is saved")
        assertTrue(s.spy.events.isEmpty() && s.candies == 0, "arranging people is never a reward")
    }

    @Test
    fun erasingPeopleAlsoErasesTheGroupNamesAndTheOrder() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val g = assertNotNull(s.link.addGroup("Support circle").record)
        s.link.setGroups(ana.id, listOf(g.id))
        assertTrue("Support circle" in s.link.export(setOf(app.pokeplan.link.LinkKinds.PEOPLE)), "the export carries the group names with the people")
        s.link.erase(app.pokeplan.link.LinkKinds.PEOPLE)
        assertTrue(s.link.data.settings.groups.isEmpty() && s.link.data.settings.order.isEmpty())
    }

    @Test
    fun anOlderSaveGetsItsMainCategoryBackOnceAndKeepsItsGroups() {
        val seeded = app.pokeplan.link.LinkData(
            people = listOf(
                app.pokeplan.link.Person("p1", "Ana", groupIds = listOf("g-friend")),
                app.pokeplan.link.Person("p2", "Ben", groupIds = listOf("g-club")),
            ),
            settings = app.pokeplan.link.LinkSettings(
                groups = listOf(app.pokeplan.link.LinkGroup("g-friend", "Friend"), app.pokeplan.link.LinkGroup("g-club", "Climbing club")),
            ),
        )
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val storage = MemoryStorage(LinkStore.json.encodeToString(app.pokeplan.link.LinkData.serializer(), seeded))
        val link = LinkStore(storage, clock, Spy(game), utc, rng = Random(3))
        assertEquals("Friend", link.data.people.first { it.name == "Ana" }.category, "the word one of her groups held comes back as the main category")
        assertEquals("", link.data.people.first { it.name == "Ben" }.category, "a plain group is not a category")
        assertEquals(2, link.data.settings.groups.size, "the groups stay, the category does not replace them")
        assertTrue(link.data.settings.categoriesRestored)
        val reopened = LinkStore(storage, clock, Spy(game), utc, rng = Random(5))
        assertEquals(link.data, reopened.data, "the repair is saved, so a reopen changes nothing")
    }
    @Test
    fun memoriesAndErasAreKeptAndTheirAssociationsAreCleaned() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val college = assertNotNull(s.link.addEra("College", start = PartialDate(2020), end = PartialDate(2024)).record)
        val m = assertNotNull(s.link.addMemory("Lunch", PartialDate(2024, 3, 14), personIds = listOf(ana.id, "gone"), eraIds = listOf(college.id, "gone")).record)
        assertEquals(listOf(ana.id), m.personIds, "unknown people are dropped")
        assertEquals(listOf(college.id), m.eraIds, "unknown eras are dropped")
        assertNull(s.link.addMemory(" ", PartialDate()).record, "a memory needs a word")
        assertNotNull(s.link.addMemory("", note = "Only a note", date = PartialDate(2023)).record)
        val dinner = assertNotNull(s.link.updateMemory(m.copy(title = "Dinner", date = PartialDate(2024, 6), personIds = listOf(ana.id))).record)
        assertEquals("Dinner", dinner.title)
        assertEquals(PartialDate(2024, 6), s.link.memories().first { it.id == m.id }.date)
        s.link.deleteEra(college.id)
        assertTrue(s.link.eras().isEmpty())
        assertEquals(2, s.link.memories().size, "deleting an era keeps its memories")
        assertTrue(s.link.memories().first { it.id == m.id }.eraIds.isEmpty(), "and removes the era from them")
        s.reopen()
        assertEquals(2, s.link.memories().size, "memories and eras survive a reopen")
        s.link.deletePerson(ana.id, keepDates = true, keepFollowUps = true)
        assertTrue(s.link.memories().first { it.id == m.id }.personIds.isEmpty(), "erasing a person takes them out of memories")
        s.link.deleteMemory(m.id)
        assertEquals(1, s.link.memories().size)
    }

    @Test
    fun connectionsAreExplicitSharedBetweenTwoProfilesAndGoWithEitherPerson() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val ben = assertNotNull(s.link.addPerson("Ben").record)
        val cy = assertNotNull(s.link.addPerson("Cy").record)
        assertNull(s.link.addConnection(ana.id, ana.id, "Friend").record, "not to themselves")
        assertNull(s.link.addConnection(ana.id, ben.id, " ").record, "a connection needs a word")
        assertNull(s.link.addConnection(ana.id, "nobody", "Friend").record, "nor to someone who is not there")
        val c = assertNotNull(s.link.addConnection(ana.id, ben.id, " Sister ").record)
        assertEquals("Sister", c.label)
        assertEquals(listOf("Ben"), s.link.connectionsOf(ana.id).map { it.second.name }, "shown on one profile")
        assertEquals(listOf("Ana"), s.link.connectionsOf(ben.id).map { it.second.name }, "and the other")
        assertTrue(s.link.connectionsOf(cy.id).isEmpty(), "and on no one else's")
        assertNull(s.link.addConnection(ben.id, ana.id, "sister").record, "the same word twice is refused, from either side")
        assertNotNull(s.link.addConnection(ben.id, ana.id, "Flatmate").record, "a second word for the pair is fine")
        assertEquals("Colleague", assertNotNull(s.link.updateConnection(c.id, "Colleague").record).label)
        assertNull(s.link.updateConnection(c.id, "Flatmate").record, "a rename cannot repeat the pair's other word")
        assertTrue("Colleague" in s.link.export(setOf(LinkKinds.PEOPLE)), "connections export with the people")
        s.reopen()
        assertEquals(2, s.link.connectionsOf(ana.id).size, "connections survive a reopen")
        s.link.deletePerson(ben.id, keepDates = true, keepFollowUps = true)
        assertTrue(s.link.connectionsOf(ana.id).isEmpty() && s.link.data.connections.isEmpty(), "deleting a person removes their connections")
        assertNotNull(s.link.data.people.firstOrNull { it.id == ana.id }, "and changes nobody else")
        val d = assertNotNull(s.link.addConnection(ana.id, cy.id, "Friend").record)
        s.link.removeConnection(d.id)
        assertTrue(s.link.data.connections.isEmpty())
        s.link.addConnection(ana.id, cy.id, "Friend")
        s.link.erase(LinkKinds.PEOPLE)
        assertTrue(s.link.data.connections.isEmpty(), "erasing the people takes their connections")
    }

    @Test
    fun slambookQuestionSetsAreOneCopyPerCategoryAndNeverTouchAnswers() {
        val s = setup()
        val general = s.link.questionsFor("")
        assertEquals(app.pokeplan.link.ProfileKit.questions, general)
        assertTrue(s.link.questionsFor("Family").size < general.size, "a starting category begins with its own, shorter set")
        assertEquals(general, s.link.questionsFor("My climbing club"), "a category the player made starts with the general set")
        val ana = assertNotNull(s.link.addPerson("Ana", category = "Family").record)
        s.link.updateProfile(ana.id, app.pokeplan.link.Profile(answers = listOf(app.pokeplan.link.Answer("Hobby", "Chess"))))
        val family = s.link.questionsFor("Family")
        s.link.setQuestions("family", listOf("Nickname", " Favourite soup ", "nickname", ""))
        assertEquals(listOf("Nickname", "Favourite soup"), s.link.questionsFor("Family"), "set cleaned, matched without case")
        assertEquals(general, s.link.questionsFor(""), "no other set changes")
        assertEquals(app.pokeplan.link.ProfileKit.defaultQuestions("Work"), s.link.questionsFor("Work"))
        assertEquals("Chess", s.link.data.people.first { it.id == ana.id }.profile.answers.single().answer, "an answer to a hidden question stays")
        s.reopen()
        assertEquals(listOf("Nickname", "Favourite soup"), s.link.questionsFor("Family"), "the set survives a reopen")
        s.link.resetQuestions("Family")
        assertEquals(family, s.link.questionsFor("Family"))
        assertTrue(s.link.data.settings.questionSets.isEmpty())
    }

    @Test
    fun memoriesExportAndEraseAsTheirOwnKind() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        s.link.addEra("College")
        s.link.addMemory("Lunch", PartialDate(2024), personIds = listOf(ana.id))
        val exported = s.link.export(setOf(LinkKinds.MEMORIES))
        assertTrue("Lunch" in exported && "College" in exported, "the memories export carries memories and eras")
        assertFalse("\"people\"" in exported, "and nothing else")
        s.link.erase(LinkKinds.MEMORIES)
        assertTrue(s.link.memories().isEmpty() && s.link.eras().isEmpty())
        assertEquals(1, s.link.data.people.size, "erasing memories keeps the people")
        s.link.erase(LinkKinds.PEOPLE)
        assertEquals(0, s.link.data.people.size)
    }

    @Test
    fun thisDayAndTheLastMeetupComeFromTheMemoriesAlreadyRecorded() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        s.link.addMemory("Older lunch", PartialDate(2024, 11, 7), personIds = listOf(ana.id))
        s.link.addMemory("Dinner", PartialDate(2025, 11, 7), personIds = listOf(ana.id))
        s.link.addMemory("Year only", PartialDate(2022), personIds = listOf(ana.id))
        assertEquals(listOf("Dinner", "Older lunch"), s.link.onThisDay(LocalDate(2026, 11, 7)).map { it.title }, "earlier years on this day, newest first")
        assertEquals(LocalDate(2025, 11, 7), s.link.metUp(ana.id), "only a fully known date counts as a meetup")
        assertEquals(listOf("Dinner", "Older lunch", "Year only"), s.link.memoriesOf(ana.id).map { it.title })
    }
    @Test
    fun photosAreKeptWithTheirMemoryAndErasedWithIt() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val m = assertNotNull(s.link.addMemory("Lunch", PartialDate(2024), personIds = listOf(ana.id)).record)
        assertNull(s.link.addPhoto(m.id, image = "").record, "an empty picture is refused")
        val photo = assertNotNull(s.link.addPhoto(m.id, image = "AQID").record)
        assertEquals(1, s.link.photosOf(m.id).size)
        assertEquals(1, s.link.photosOfPerson(ana.id).size)
        assertNotNull(s.link.updatePhotoCaption(photo.id, "The choir room").record)
        s.reopen()
        assertEquals("The choir room", s.link.photosOf(m.id).single().caption, "photos survive a reopen")
        assertTrue("AQID" in s.link.export(setOf(LinkKinds.MEMORIES)), "the memories export carries the picture")
        s.link.deleteMemory(m.id)
        assertTrue(s.link.photosOf(m.id).isEmpty(), "deleting a memory deletes its photos")
    }

    private val picture = LinkStore.NewPhoto("AQID", "image/png")

    @Test
    fun aMemoryMayBeIncompleteAndStillSaves() {
        val s = setup()
        val photoOnly = assertNotNull(s.link.saveMemory(null, "", addPhotos = listOf(picture)).record, "a photo alone is a memory")
        assertEquals(1, s.link.photosOf(photoOnly.id).size)
        assertEquals("Photo memory", s.link.memoryLabel(photoOnly))
        val dateOnly = assertNotNull(s.link.saveMemory(null, "", date = PartialDate(2019, 8)).record, "a date alone is a memory")
        assertEquals("Aug 2019", s.link.memoryLabel(dateOnly))
        val noteOnly = assertNotNull(s.link.saveMemory(null, "", note = "We got rained on\nand laughed").record)
        assertEquals("We got rained on", s.link.memoryLabel(noteOnly))
        assertNotNull(s.link.saveMemory(null, "Just a title").record)
        assertNull(s.link.saveMemory(null, "", place = "Rizal", time = "evening").record, "a place or a time alone only describes nothing")
        assertNull(s.link.saveMemory(null, "").record, "an empty form is not a memory")
        // An incomplete memory can be finished later.
        val finished = assertNotNull(s.link.saveMemory(photoOnly.id, "Beach day", date = PartialDate(2021, 5, 2)).record)
        assertEquals(photoOnly.id, finished.id)
        assertEquals(1, s.link.photosOf(photoOnly.id).size, "finishing it keeps its photo")
    }

    @Test
    fun aMemoryAndItsPhotoChangesLandTogetherOrNotAtAll() {
        val s = setup()
        val made = assertNotNull(s.link.saveMemory(null, "Trip", addPhotos = listOf(picture.copy(caption = "one"), picture.copy(caption = "two"))).record)
        val other = assertNotNull(s.link.saveMemory(null, "Other", addPhotos = listOf(picture)).record)
        val (one, two) = s.link.photosOf(made.id).sortedBy { it.caption }
        val otherPhoto = s.link.photosOf(other.id).single()
        val before = s.link.data

        // Refusals change nothing: a bad new picture, too many pictures, nothing left in the memory.
        assertNull(s.link.saveMemory(made.id, "Renamed", addPhotos = listOf(LinkStore.NewPhoto("", "image/png")), removePhotoIds = setOf(one.id)).record)
        assertEquals(before, s.link.data, "a refused save leaves the text and the photos as they were")
        assertNull(s.link.saveMemory(made.id, "Renamed", addPhotos = List(LinkRules.PHOTOS_PER_MEMORY_MAX) { picture }).record)
        assertEquals(before, s.link.data)
        assertNull(s.link.saveMemory(made.id, "", removePhotoIds = setOf(one.id, two.id)).record, "removing the last photo of a memory with no words would empty it")
        assertEquals(before, s.link.data)

        // One good save applies text, a removal, a caption edit and an addition at once; another memory's photo is never touched.
        val saved = assertNotNull(
            s.link.saveMemory(
                made.id, "Trip to Baguio", addPhotos = listOf(picture.copy(caption = "three")),
                removePhotoIds = setOf(one.id, otherPhoto.id), captions = mapOf(two.id to " second ", otherPhoto.id to "hijack"),
            ).record,
        )
        assertEquals("Trip to Baguio", saved.title)
        assertEquals(listOf("second", "three"), s.link.photosOf(made.id).map { it.caption }.sorted())
        assertEquals(otherPhoto, s.link.photosOf(other.id).single(), "a photo of another memory is not removed or recaptioned")
        assertEquals(two.id, s.link.photosOf(made.id).first { it.caption == "second" }.id, "a kept photo keeps its id")
    }

    @Test
    fun deletingAPersonKeepsTheirMemoriesAndPhotosAndTheArchiveSurvivesAReopen() {
        val s = setup()
        val ana = assertNotNull(s.link.addPerson("Ana").record)
        val ben = assertNotNull(s.link.addPerson("Ben").record)
        s.link.moveInOrder(ana.id, listOf(ana.id, ben.id), 1)
        val chapter = assertNotNull(s.link.addEra("College").record)
        val made = assertNotNull(
            s.link.saveMemory(null, "Lunch", PartialDate(2024), personIds = listOf(ana.id, ben.id), eraIds = listOf(chapter.id), addPhotos = listOf(picture)).record,
        )
        val photoId = s.link.photosOf(made.id).single().id
        s.link.deletePerson(ana.id, keepDates = true, keepFollowUps = true)
        val kept = s.link.memories().single()
        assertEquals(made.id, kept.id, "the memory stays")
        assertEquals(listOf(ben.id), kept.personIds, "only the deleted person leaves it")
        assertEquals(listOf(chapter.id), kept.eraIds)
        assertEquals(photoId, s.link.photosOf(made.id).single().id, "its photo stays with it")
        assertFalse(ana.id in s.link.data.settings.order, "My order forgets a deleted person")
        s.reopen()
        assertEquals(kept, s.link.memories().single(), "ids and associations survive a reopen")
        assertEquals(photoId, s.link.photosOf(made.id).single().id)
        // Undo (a restore of the snapshot) brings the person and the association back.
        val withAna = s.link.data
        s.link.deletePerson(ben.id, keepDates = true, keepFollowUps = true)
        s.link.restore(withAna)
        assertEquals(withAna, s.link.data)
        // Deleting a chapter or a memory touches only what it owns.
        s.link.deleteEra(chapter.id)
        assertEquals(emptyList(), s.link.memories().single().eraIds)
        s.link.deleteMemory(made.id)
        assertTrue(s.link.photosOf(made.id).isEmpty())
        assertEquals(1, s.link.data.people.size, "deleting a memory leaves people alone")
    }
}
