package app.pokeplan.app.link

import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUpStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.reminders.ActionResult
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderRouter
import app.pokeplan.app.reminders.ReminderSnooze
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class LinkRemindersTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
        fun advanceHours(n: Int) { millis += n * 3_600_000L }
    }

    private val utc = TimeZone.UTC
    private val saturdayEvening = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val link: LinkStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(saturdayEvening)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return Setup(game, LinkStore(MemoryStorage(), clock, game, utc, rng = Random(3)), clock)
    }

    private fun Setup.upcoming() = LinkReminders.upcoming(link.data, game.data, clock.nowMillis(), utc)
    private fun Setup.adapter() = LinkReminderAdapter({ link.data }, { game.data }, { link.today }) { id -> link.complete(id).record != null }
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = LocalDateTime(y, m, d, h, 0).toInstant(utc).toEpochMilliseconds()

    @Test fun `nothing asks until Link and the record are both turned on`() {
        val s = setup()
        val date = s.link.addDate(DateKind.BIRTHDAY, "Mum", 11, 9).record!!
        assertTrue(s.upcoming().isEmpty(), "no reminder was chosen")
        s.link.setDateRemind(date.id, 1)
        assertTrue(s.link.data.settings.reminders, "choosing a reminder turns Link's reminders on")
        s.link.setReminders(false)
        assertTrue(s.upcoming().isEmpty(), "the master switch is off")
        s.link.setReminders(true)
        val r = s.upcoming().single()
        assertEquals(LinkReminderKind.DATE, r.kind)
        assertEquals(at(2026, 11, 8), r.atMillis, "one day before 9 November, at 09:00")
        assertEquals("2026-11-09", r.occurrence)
        s.link.setDateRemind(date.id, null)
        assertTrue(s.upcoming().isEmpty())
        s.link.setDateRemind(date.id, 2)
        assertNull(s.link.data.dates.single().remindDays)
    }

    @Test fun `a date reminds on the day, a passed one asks nothing and the next year is waited for`() {
        val s = setup()
        s.link.setReminders(true)
        val day = s.link.addDate(DateKind.ANNIVERSARY, "Us", 11, 8).record!!
        s.link.setDateRemind(day.id, 0)
        assertEquals(at(2026, 11, 8), s.upcoming().single().atMillis)
        s.clock.advanceDays(2)
        assertTrue(s.upcoming().isEmpty(), "this year's day has passed and next year's is far away")
        val once = s.link.addDate(DateKind.MILESTONE, "Graduation", 1, 5, year = 2026, annual = false).record!!
        s.link.setDateRemind(once.id, 0)
        assertTrue(s.upcoming().isEmpty())
    }

    @Test fun `29 February reminds on the 28th in a year without one`() {
        val s = setup()
        s.link.setReminders(true)
        val leap = s.link.addDate(DateKind.BIRTHDAY, "Leap", 2, 29).record!!
        s.link.setDateRemind(leap.id, 0)
        s.clock.millis = at(2027, 2, 27, 12)
        val r = s.upcoming().single()
        assertEquals("2027-02-28", r.occurrence)
        assertEquals(at(2027, 2, 28), r.atMillis)
    }

    @Test fun `a follow-up asks on its day only while open, with a day, and not once it is a Plan task`() {
        val s = setup()
        s.link.setReminders(true)
        val f = s.link.addFollowUp("Send the photos", due = LocalDate(2026, 11, 9)).record!!
        s.link.setFollowUpRemind(f.id, true)
        assertEquals(at(2026, 11, 9), s.upcoming().single().atMillis)
        val noDay = s.link.addFollowUp("Call back").record!!
        s.link.setFollowUpRemind(noDay.id, true)
        assertEquals(1, s.upcoming().size, "no day, no reminder")
        assertNotNull(s.link.convertToPlan(f.id))
        assertTrue(s.upcoming().isEmpty())
        s.link.setFollowUpRemind(f.id, true)
        assertTrue(s.upcoming().isEmpty())
    }

    @Test fun `an overdue or finished follow-up asks nothing`() {
        val s = setup()
        s.link.setReminders(true)
        val late = s.link.addFollowUp("Late", due = LocalDate(2026, 11, 3)).record!!
        s.link.setFollowUpRemind(late.id, true)
        assertTrue(s.upcoming().isEmpty(), "an overdue follow-up never nags")
        val f = s.link.addFollowUp("Soon", due = LocalDate(2026, 11, 8)).record!!
        s.link.setFollowUpRemind(f.id, true)
        assertEquals(1, s.upcoming().size)
        s.link.cancel(f.id)
        assertTrue(s.upcoming().isEmpty())
        s.link.reopen(f.id)
        assertEquals(1, s.upcoming().size)
        s.link.complete(f.id)
        assertTrue(s.upcoming().isEmpty())
    }

    @Test fun `moving the day, deleting or turning Link off stops a reminder that is already planned`() {
        val s = setup()
        s.link.setReminders(true)
        val f = s.link.addFollowUp("Send the photos", due = LocalDate(2026, 11, 9)).record!!
        s.link.setFollowUpRemind(f.id, true)
        val d = s.link.addDate(DateKind.BIRTHDAY, "Mum", 11, 9).record!!
        s.link.setDateRemind(d.id, 1)
        val adapter = s.adapter()
        val requests = adapter.requests(s.clock.nowMillis(), utc)
        assertEquals(2, requests.size)
        val followUp = requests.first { it.kind == "followup" }
        val date = requests.first { it.kind == "date" }
        assertTrue(adapter.isStillWanted(followUp) && adapter.isStillWanted(date))
        s.link.updateFollowUp(s.link.data.followUps.single().copy(due = LocalDate(2026, 11, 12)))
        assertFalse(adapter.isStillWanted(followUp))
        assertNull(adapter.present(followUp))
        s.link.updateDate(s.link.data.dates.single().copy(day = 20))
        assertFalse(adapter.isStillWanted(date))
        s.link.updateDate(s.link.data.dates.single().copy(day = 9))
        assertTrue(adapter.isStillWanted(date))
        s.link.setReminders(false)
        assertFalse(adapter.isStillWanted(date))
        s.link.setReminders(true)
        s.link.deleteDate(d.id)
        assertFalse(adapter.isStillWanted(date))
    }

    @Test fun `a request fires late is stale once its date has passed`() {
        val s = setup()
        s.link.setReminders(true)
        val d = s.link.addDate(DateKind.BIRTHDAY, "Mum", 11, 9).record!!
        s.link.setDateRemind(d.id, 1)
        val adapter = s.adapter()
        val request = adapter.requests(s.clock.nowMillis(), utc).single()
        s.clock.advanceDays(3)
        assertFalse(adapter.isStillWanted(request), "the 9th has gone by")
    }

    @Test fun `the request and the lock screen carry no name and titles need the user's yes`() {
        val s = setup()
        s.link.setReminders(true)
        val person = s.link.addPerson("Grandma Rosa").record!!
        val d = s.link.addDate(DateKind.BIRTHDAY, "Secret party", 11, 9, personId = person.id).record!!
        s.link.setDateRemind(d.id, 1)
        val f = s.link.addFollowUp("Ask about the surprise", personId = person.id, due = LocalDate(2026, 11, 9)).record!!
        s.link.setFollowUpRemind(f.id, true)
        val adapter = s.adapter()
        val requests = adapter.requests(s.clock.nowMillis(), utc)
        requests.forEach {
            assertFalse(it.id.contains("Secret") || it.id.contains("Rosa") || it.id.contains("surprise"), it.id)
            assertEquals("You have an upcoming reminder", adapter.present(it)?.title)
        }
        s.link.setReminderNames(true)
        assertEquals("Secret party, tomorrow", adapter.present(requests.first { it.kind == "date" })?.title)
        assertEquals("Follow up: Ask about the surprise", adapter.present(requests.first { it.kind == "followup" })?.title)
        assertFalse(adapter.present(requests.first { it.kind == "followup" })?.title.orEmpty().contains("Rosa"))
    }

    @Test fun `Done on a follow-up reminder is the follow-up's own completion and paid once, a date has no Done`() {
        val s = setup()
        s.link.setReminders(true)
        val f = s.link.addFollowUp("Send the photos", due = LocalDate(2026, 11, 8)).record!!
        s.link.setFollowUpRemind(f.id, true)
        val d = s.link.addDate(DateKind.BIRTHDAY, "Mum", 11, 8).record!!
        s.link.setDateRemind(d.id, 0)
        s.clock.advanceHours(-10)
        s.clock.millis = at(2026, 11, 8, 8)
        val adapter = s.adapter()
        val router = ReminderRouter(listOf(adapter))
        val requests = adapter.requests(s.clock.nowMillis(), utc)
        val followUp = requests.first { it.kind == "followup" }
        val date = requests.first { it.kind == "date" }
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(date, ReminderActions.DONE))
        assertEquals(ActionResult.DONE, router.handle(followUp, ReminderActions.DONE))
        assertEquals(FollowUpStatus.COMPLETED, s.link.data.followUps.single().status)
        assertTrue(s.link.data.followUps.single().paid)
        assertEquals(ActionResult.STALE, router.handle(followUp, ReminderActions.DONE))
        assertEquals(ActionResult.DISMISSED, router.handle(date, ReminderActions.DISMISS))
        assertEquals(1, s.link.data.dates.size)
        assertNotNull(ReminderSnooze.untilMillis(ReminderModule.LINK, ReminderActions.TOMORROW, s.clock.nowMillis(), utc))
    }

    @Test fun `quiet hours move a reminder out and an old save loads with every reminder off`() {
        val s = setup()
        s.link.setReminders(true)
        s.link.setReminderHour(23)
        val f = s.link.addFollowUp("Send the photos", due = LocalDate(2026, 11, 8)).record!!
        s.link.setFollowUpRemind(f.id, true)
        assertEquals(at(2026, 11, 9, 7), s.upcoming().single().atMillis, "23:00 is inside the default quiet hours (22:00 to 07:00)")
        val old = LinkStore.json.decodeFromString(app.pokeplan.link.LinkData.serializer(), """{"people":[],"dates":[{"id":"a","kind":"BIRTHDAY","title":"T","month":1,"day":2}],"followUps":[{"id":"b","title":"x"}]}""")
        assertFalse(old.settings.reminders)
        assertNull(old.dates.single().remindDays)
        assertFalse(old.followUps.single().remind)
    }
}
