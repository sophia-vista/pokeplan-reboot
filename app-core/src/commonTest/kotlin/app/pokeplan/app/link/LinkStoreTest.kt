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
}
