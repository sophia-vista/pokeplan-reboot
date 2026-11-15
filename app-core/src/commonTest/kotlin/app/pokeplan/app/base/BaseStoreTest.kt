package app.pokeplan.app.base

import kotlinx.datetime.plus
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.base.ChoreState
import app.pokeplan.base.EntryStatus
import app.pokeplan.base.HitKind
import app.pokeplan.base.BaseKinds
import app.pokeplan.base.BaseRules
import app.pokeplan.base.RecordStatus
import app.pokeplan.base.Recurrence
import app.pokeplan.base.RepeatKind
import app.pokeplan.base.RestockLine
import app.pokeplan.base.SchedulePolicy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import app.pokeplan.app.Place
import app.pokeplan.app.Tutorials
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class BaseStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 7)

    /** A world that records every event it is sent, so a test can see exactly what crosses the boundary. */
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<BaseEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun base(event: BaseEvent): List<Reward>? { events += event; return inner.base(event) }
    }

    private class Setup(val game: GameStore, val storage: MemoryStorage, var home: BaseStore, val spy: Spy, val clock: FakeClock)

    private fun setup(started: Boolean = true): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val spy = Spy(game)
        val storage = MemoryStorage()
        return Setup(game, storage, BaseStore(storage, clock, spy, utc, rng = Random(3)), spy, clock)
    }

    private fun Setup.reopen() { home = BaseStore(storage, clock, spy, utc, rng = Random(11)) }

    private val Setup.candies get() = game.data.player.inventory[Items.RARE_CANDY] ?: 0

    private fun Setup.chore(title: String, recurrence: Recurrence = Recurrence(), firstDue: LocalDate? = null) = assertNotNull(home.addChore(title, recurrence, firstDue).record)

    private val daily = Recurrence(RepeatKind.DAILY)
    private val weekly = Recurrence(RepeatKind.WEEKLY)

    private val secrets = listOf("Zorblax-9000 sweep", "Quuxville-Annex", "Zorblax-instr-77", "Zorblax-note-55", "Zorblax-supply", "Zorblax-asset", "SN-ZORBLAX-0042", "Zorblax-cost-4500")

    // --- areas ------------------------------------------------------------------------------------------------------------------

    @Test
    fun theStartingAreasAreSeededOnceAndDeletingThemAllDoesNotBringThemBack() {
        val s = setup()
        assertEquals(BaseRules.STARTING_AREAS, s.home.data.areas.map { it.name })
        assertTrue(s.home.data.settings.areasSeeded)
        s.home.data.areas.map { it.id }.forEach { s.home.deleteArea(it) }
        assertTrue(s.home.data.areas.isEmpty())
        s.reopen()
        assertTrue(s.home.data.areas.isEmpty(), "deleted starting areas stay deleted after reopening")
        // Reopening an untouched store does not duplicate them either.
        val t = setup()
        t.reopen(); t.reopen()
        assertEquals(BaseRules.STARTING_AREAS.size, t.home.data.areas.size)
    }

    // --- chores -----------------------------------------------------------------------------------------------------------------

    @Test
    fun aTitleOnlyChoreIsAWheneverChoreAndARecurringOneStartsToday() {
        val s = setup()
        val whenever = s.chore("Water the plants")
        assertNull(whenever.firstDue)
        assertEquals(ChoreState.ANYTIME, s.home.choreView(whenever.id)!!.state)
        val repeating = assertNotNull(s.home.addChore("Sweep", weekly).record)
        assertEquals(today, repeating.firstDue)
        assertEquals(ChoreState.DUE, s.home.choreView(repeating.id)!!.state)
        assertTrue(s.spy.events.isEmpty(), "making chores never pays")
    }

    @Test
    fun anEmptyTitleAndABadRepeatAreRefusedWithAReason() {
        val s = setup()
        for (bad in listOf(
            s.home.addChore("   "),
            s.home.addChore("x", Recurrence(RepeatKind.DAILY, every = 0)),
            s.home.addChore("x", Recurrence(RepeatKind.DAILY, every = BaseRules.EVERY_MAX + 1)),
            s.home.addChore("x", Recurrence(RepeatKind.WEEKLY, weekdays = listOf(8))),
            s.home.addChore("x".repeat(BaseRules.TITLE_MAX + 1)),
        )) { assertNull(bad.record); assertNotNull(bad.refused) }
        assertTrue(s.home.data.chores.isEmpty())
    }

    @Test
    fun completingAChorePaysOnceAndTheEventAndTheSharedSaveCarryNothingPrivate() {
        val s = setup()
        val area = assertNotNull(s.home.addArea("Quuxville-Annex"))
        val c = assertNotNull(s.home.addChore("Zorblax-9000 sweep", weekly, null, "Zorblax-instr-77", area.id).record)
        val sup = assertNotNull(s.home.addSupply("Zorblax-supply", "bottles", 3000, 1000, notes = "Zorblax-note-55").record)
        val asset = assertNotNull(s.home.addAsset("Zorblax-asset", serial = "SN-ZORBLAX-0042").record)
        s.home.addReference("Zorblax-9000 sweep", "Zorblax-note-55", areaId = area.id, assetId = asset.id)
        s.home.setQuantity(sup.id, 500)
        assertTrue(s.spy.events.isEmpty())
        val before = s.candies
        val out = s.home.complete(c.id, note = "Zorblax-note-55")
        val entry = assertNotNull(out.record)
        assertTrue(entry.paid && out.rewards.isNotEmpty() && out.rewards.any { it is Reward.Candies })
        assertTrue(s.candies > before)
        val e = s.spy.events.single()
        assertEquals(BaseKind.CHORE, e.kind)
        val text = GameStore.json.encodeToString(BaseEvent.serializer(), e)
        assertEquals(setOf("id", "kind", "nowMillis", "ref", "module"), GameStore.json.parseToJsonElement(text).jsonObject.keys)
        assertEquals("home", GameStore.json.parseToJsonElement(text).jsonObject.getValue("module").jsonPrimitive.content)
        val shared = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        val log = s.game.data.rewardLog.joinToString("\n") { it.source }
        for (secret in secrets + listOf(area.id, sup.id, asset.id)) {
            assertTrue(secret !in text, "the event leaked $secret: $text")
            assertTrue(secret !in shared, "the shared save holds $secret")
            assertTrue(secret !in log, "the reward log holds $secret")
        }
        assertTrue("Habitat Discovery" in log)
    }

    @Test
    fun undoingAndCompletingTheSameOccurrenceAgainPaysNothingTheSecondTime() {
        val s = setup()
        val c = s.chore("Dishes", daily)
        val first = s.home.complete(c.id)
        assertTrue(assertNotNull(first.record).paid)
        val afterFirst = s.candies
        s.home.undo(first.record!!.id)
        assertEquals(ChoreState.DUE, s.home.choreView(c.id)!!.state, "undo makes it wait again")
        val second = s.home.complete(c.id)
        assertFalse(assertNotNull(second.record).paid)
        assertTrue(second.rewards.isEmpty())
        assertEquals(afterFirst, s.candies, "no second candy")
        assertEquals(1, s.game.data.basePaid.size)
        assertEquals(2, s.spy.events.size)
        assertEquals(s.spy.events[0].id, s.spy.events[1].id, "the event names the occurrence")
    }

    @Test
    fun theSixthChoreOfADayIsNotPaidButIsStillRecorded() {
        val s = setup()
        val ids = (1..6).map { s.chore("Chore $it").id }
        val results = ids.map { s.home.complete(it) }
        assertEquals(List(BaseRules.CHORES_PAID_PER_DAY) { true } + false, results.map { assertNotNull(it.record).paid })
        assertTrue(results.last().rewards.isEmpty())
        assertEquals(6, s.home.data.entries.count { it.status == EntryStatus.DONE })
        assertEquals(BaseRules.CHORES_PAID_PER_DAY, s.spy.events.size, "no event for the unpaid sixth")
        assertTrue(ids.all { s.home.choreView(it)!!.state == ChoreState.DONE })
    }

    @Test
    fun withProgressionOffNothingIsPaidAndNoEventIsSent() {
        val s = setup()
        s.home.setProgression(false)
        val c = s.chore("Dishes")
        val before = s.candies
        val m = assertNotNull(s.home.addSchedule("Filter", today).record)
        val out = s.home.complete(c.id)
        assertFalse(assertNotNull(out.record).paid)
        assertTrue(out.rewards.isEmpty())
        assertFalse(assertNotNull(s.home.completeMaintenance(m.id).record).paid)
        assertFalse(assertNotNull(s.home.logMaintenance("Fixed the door").record).paid)
        assertTrue(s.spy.events.isEmpty())
        assertEquals(before, s.candies)
    }

    @Test
    fun skippingAndDeferringNeverPayAndDeferToTodayOrBeforeIsRefused() {
        val s = setup()
        val a = s.chore("Skip me", daily)
        val b = s.chore("Defer me", daily)
        assertTrue(s.home.skip(a.id))
        assertEquals(ChoreState.SKIPPED, s.home.choreView(a.id)!!.state)
        assertFalse(s.home.skip(a.id), "nothing is waiting any more")
        assertFalse(s.home.defer(b.id, today))
        assertFalse(s.home.defer(b.id, LocalDate(2026, 11, 1)))
        assertTrue(s.home.defer(b.id, LocalDate(2026, 11, 9)))
        val v = s.home.choreView(b.id)!!
        assertEquals(ChoreState.UPCOMING, v.state)
        assertEquals(LocalDate(2026, 11, 9), v.dueDay)
        assertTrue(s.spy.events.isEmpty())
        assertTrue(s.home.data.entries.none { it.paid })
    }

    @Test
    fun aRecurringChoreAMonthLaterShowsOneOverdueOccurrenceAndCompletingItWorks() {
        val s = setup()
        val c = s.chore("Bins", weekly, today)
        s.clock.advanceDays(17) // Tuesday 24 November
        val views = s.home.choreViews()
        assertEquals(1, views.size)
        val v = views.single()
        assertEquals(ChoreState.OVERDUE, v.state)
        assertEquals(LocalDate(2026, 11, 21), v.occurrence)
        val out = s.home.complete(c.id)
        val entry = assertNotNull(out.record)
        assertEquals(LocalDate(2026, 11, 21), entry.dueDay)
        assertEquals(LocalDate(2026, 11, 24), entry.day)
        assertTrue(entry.paid)
        val after = s.home.choreView(c.id)!!
        assertEquals(ChoreState.UPCOMING, after.state, "the overdue occurrence is settled; the next one is simply upcoming")
        assertEquals(LocalDate(2026, 11, 28), after.dueDay)
    }

    @Test
    fun aChoreDoneEarlyCountsAndPays() {
        val s = setup()
        val tomorrow = LocalDate(2026, 11, 8)
        val c = s.chore("Tax papers", firstDue = tomorrow)
        assertEquals(ChoreState.UPCOMING, s.home.choreView(c.id)!!.state)
        val entry = assertNotNull(s.home.complete(c.id).record)
        assertEquals(tomorrow, entry.dueDay)
        assertTrue(entry.paid)
        assertEquals(ChoreState.DONE, s.home.choreView(c.id)!!.state)
    }

    @Test
    fun pausingAndArchivingKeepHistoryAndDeletingRemovesIt() {
        val s = setup()
        val c = s.chore("Mop", daily)
        assertNotNull(s.home.complete(c.id).record)
        s.home.setChoreStatus(c.id, RecordStatus.PAUSED)
        assertEquals(ChoreState.PAUSED, s.home.choreView(c.id)!!.state)
        assertNull(s.home.complete(c.id).record, "a paused chore is not waiting")
        s.home.setChoreStatus(c.id, RecordStatus.ARCHIVED)
        assertTrue(s.home.choreViews().isEmpty(), "archived is out of the active views")
        assertEquals(1, s.home.data.entries.size)
        assertEquals(1, BaseRules.history(s.home.data, c.id).size)
        s.home.setChoreStatus(c.id, RecordStatus.ACTIVE)
        assertEquals(ChoreState.DONE, s.home.choreView(c.id)!!.state)
        val other = s.chore("Other")
        s.home.complete(other.id)
        s.home.deleteChore(c.id)
        assertTrue(s.home.data.chores.none { it.id == c.id } && s.home.data.entries.none { it.choreId == c.id })
        assertEquals(1, s.home.data.entries.size, "another chore's history stays")
    }

    @Test
    fun editingAChoreNeverRewritesWhatWasDone() {
        val s = setup()
        val c = s.chore("Vacuum", weekly, today)
        assertNotNull(s.home.complete(c.id).record)
        val entries = s.home.data.entries
        val edited = assertNotNull(s.home.updateChore(c.copy(title = "Vacuum the floor", recurrence = daily)).record)
        assertEquals("Vacuum the floor", edited.title)
        assertEquals(daily, edited.recurrence)
        assertEquals(entries, s.home.data.entries)
        assertNotNull(s.home.updateChore(edited.copy(title = "")).refused)
        assertEquals("Vacuum the floor", s.home.data.chores.single().title)
    }

    // --- supplies ---------------------------------------------------------------------------------------------------------------

    @Test
    fun quantityChangesLeaveATraceAndGoingBelowZeroIsRefused() {
        val s = setup()
        val sup = assertNotNull(s.home.addSupply("Soap", "bottles", 3000, 2000).record)
        assertEquals("bottles", sup.unit); assertEquals(2000L, sup.lowMilli)
        assertNull(s.home.adjust(sup.id, -1000, "used one"))
        assertEquals(2000L, s.home.data.supplies.single().quantityMilli)
        val adj = s.home.data.adjustments.single()
        assertEquals(3000L, adj.beforeMilli); assertEquals(2000L, adj.afterMilli); assertEquals("used one", adj.reason)
        assertNotNull(s.home.adjust(sup.id, -5000))
        assertEquals(2000L, s.home.data.supplies.single().quantityMilli, "unchanged after a refusal")
        assertEquals(1, s.home.data.adjustments.size)
        assertNull(s.home.setQuantity(sup.id, 7000))
        assertEquals(2, s.home.data.adjustments.size)
        assertNotNull(s.home.setQuantity(sup.id, -1))
        assertNull(s.home.setQuantity(sup.id, null), "unknown is allowed")
        assertNull(s.home.data.supplies.single().quantityMilli)
        assertNotNull(s.home.adjust(sup.id, 1000), "an unknown quantity cannot change by a difference")
        assertNotNull(s.home.adjust("nope", 1000))
        assertTrue(s.spy.events.isEmpty())
    }

    @Test
    fun lowStockIsDerivedDismissalIsClearedByAChangeAndUnknownIsNeverLow() {
        val s = setup()
        val sup = assertNotNull(s.home.addSupply("Soap", "bottles", 2000, 2000).record)
        val unknown = assertNotNull(s.home.addSupply("Mystery", "pieces", null, 2000).record)
        assertEquals(listOf(sup.id), BaseRules.lowSupplies(s.home.data).map { it.id })
        assertFalse(BaseRules.isLow(unknown))
        s.home.dismissLow(sup.id)
        assertTrue(BaseRules.lowSupplies(s.home.data).isEmpty())
        assertEquals(1, BaseRules.lowSupplies(s.home.data, includeQuieted = true).size)
        assertTrue(BaseRules.restockList(s.home.data).single() is RestockLine.Low, "still on the restocking list")
        s.home.setQuantity(sup.id, 1000)
        assertFalse(s.home.data.supplies.first { it.id == sup.id }.lowDismissed)
        assertEquals(1, BaseRules.lowSupplies(s.home.data).size, "low again after a change")
    }

    @Test
    fun theRestockListIsLowSuppliesPlusManualEntriesAndRestockedClearsTheEntry() {
        val s = setup()
        val soap = assertNotNull(s.home.addSupply("Soap", "bottles", 1000, 2000).record)
        val rice = assertNotNull(s.home.addSupply("Rice", "kg", 5000, 1000).record)
        val manual = assertNotNull(s.home.addRestock("Batteries"))
        val byHand = assertNotNull(s.home.addRestock("", rice.id))
        assertEquals("Rice", byHand.name)
        assertNull(s.home.addRestock("again", rice.id), "one entry per supply")
        val list = BaseRules.restockList(s.home.data)
        assertEquals(3, list.size)
        assertEquals(soap.id, (list[0] as RestockLine.Low).supply.id)
        assertEquals(setOf("Batteries", "Rice"), list.drop(1).map { (it as RestockLine.Manual).entry.name }.toSet())
        assertEquals("Enter how much there is now.", s.home.restocked(soap.id, null), "a low supply leaves the list only with a quantity")
        assertNull(s.home.restocked(soap.id, 6000))
        assertEquals(6000L, s.home.data.supplies.first { it.id == soap.id }.quantityMilli)
        assertNull(s.home.restocked(rice.id, 8000, byHand.id))
        s.home.removeRestock(manual.id)
        assertTrue(BaseRules.restockList(s.home.data).isEmpty())
        assertTrue(s.home.data.adjustments.all { it.reason == "Restocked" })
        assertTrue(s.spy.events.isEmpty(), "supply, quantity and restock edits never send an event")
    }

    // --- belongings, warranties, maintenance --------------------------------------------------------------------------------------

    @Test
    fun aWarrantyThatEndsBeforeItStartsIsRefused() {
        val s = setup()
        val asset = assertNotNull(s.home.addAsset("Fridge").record)
        assertNotNull(s.home.addWarranty(asset.id, "Shop", startDay = LocalDate(2026, 1, 1), expiryDay = LocalDate(2025, 12, 31)).refused)
        assertNotNull(s.home.addWarranty("nope", "Shop").refused)
        assertNotNull(s.home.addWarranty(asset.id, "Shop", startDay = LocalDate(2026, 1, 1), expiryDay = LocalDate(2028, 1, 1)).record)
        assertEquals(1, s.home.data.warranties.size)
        assertNotNull(s.home.addAsset(" ").refused)
        assertTrue(s.spy.events.isEmpty(), "creating schedules, warranties and notes never pays")
    }

    @Test
    fun editingAScheduleKeepsTheDateItCountsFromUnlessTheDateOrTheKindOfRepeatChanged() {
        val s = setup()
        val made = assertNotNull(s.home.addSchedule("Filter", LocalDate(2026, 1, 31), Recurrence(RepeatKind.MONTHLY)).record)
        val moved = made.copy(nextDue = LocalDate(2026, 2, 28)) // where a month-end schedule stands after its first service
        s.home.restore(s.home.data.copy(schedules = listOf(moved)))
        val renamed = assertNotNull(s.home.updateSchedule(moved.copy(title = "Water filter", firstDue = LocalDate(2026, 2, 28))).record)
        assertEquals(LocalDate(2026, 1, 31), renamed.firstDue, "a rename does not make the 31st drift to the 28th")
        assertEquals(LocalDate(2026, 3, 31), BaseRules.advance(renamed, LocalDate(2026, 2, 28)).nextDue)
        val redated = assertNotNull(s.home.updateSchedule(renamed.copy(nextDue = LocalDate(2026, 3, 10))).record)
        assertEquals(LocalDate(2026, 3, 10), redated.firstDue, "a new date is a new start")
        val weekly = assertNotNull(s.home.updateSchedule(redated.copy(recurrence = Recurrence(RepeatKind.WEEKLY))).record)
        assertEquals(weekly.nextDue, weekly.firstDue)
    }

    @Test
    fun completingMaintenanceRecordsItMovesTheNextDatePaysOnceAndNeverMakesCostAnythingElse() {
        val s = setup()
        val asset = assertNotNull(s.home.addAsset("Zorblax-asset", serial = "SN-ZORBLAX-0042").record)
        val sched = assertNotNull(s.home.addSchedule("Descale", today, Recurrence(RepeatKind.MONTHLY), SchedulePolicy.FIXED, asset.id, "Zorblax-instr-77").record)
        assertTrue(s.spy.events.isEmpty())
        val out = s.home.completeMaintenance(sched.id, "Zorblax-note-55", "Zorblax-cost-4500")
        val rec = assertNotNull(out.record)
        assertTrue(rec.paid && out.rewards.isNotEmpty())
        assertEquals("Zorblax-cost-4500", rec.cost)
        assertEquals(sched.id, rec.scheduleId); assertEquals(asset.id, rec.assetId)
        assertEquals(LocalDate(2026, 12, 7), s.home.data.schedules.single().nextDue)
        val e = s.spy.events.single()
        assertEquals(BaseKind.MAINTENANCE, e.kind)
        val text = GameStore.json.encodeToString(BaseEvent.serializer(), e)
        val shared = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        for (secret in secrets) { assertTrue(secret !in text, "event leaked $secret"); assertTrue(secret !in shared, "save holds $secret") }
        // Put the schedule back on the same due date: the second completion of that date is not paid again.
        val back = s.home.data.schedules.single().copy(nextDue = today)
        assertNotNull(s.home.updateSchedule(back).record)
        val again = s.home.completeMaintenance(sched.id)
        assertFalse(assertNotNull(again.record).paid)
        assertTrue(again.rewards.isEmpty())
        assertEquals(2, s.home.data.maintenance.size)
        assertEquals(s.spy.events[0].id, s.spy.events[1].id, "the event names schedule and due date")
        assertEquals(1, s.game.data.basePaid.size)
    }

    @Test
    fun aOneTimeScheduleIsArchivedAfterItsWorkAndAFutureDayIsRefused() {
        val s = setup()
        val sched = assertNotNull(s.home.addSchedule("Replace the filter", today).record)
        assertNotNull(s.home.completeMaintenance(sched.id, day = LocalDate(2026, 11, 9)).refused)
        assertTrue(assertNotNull(s.home.completeMaintenance(sched.id).record).paid)
        assertEquals(RecordStatus.ARCHIVED, s.home.data.schedules.single().status)
        assertNotNull(s.home.completeMaintenance(sched.id).refused)
        assertEquals(1, s.home.data.maintenance.size)
    }

    @Test
    fun anAfterDoneScheduleCountsFromTheDayItWasDone() {
        val s = setup()
        val sched = assertNotNull(s.home.addSchedule("Oil the hinges", LocalDate(2026, 11, 1), Recurrence(RepeatKind.WEEKLY), SchedulePolicy.AFTER_DONE).record)
        assertNotNull(s.home.completeMaintenance(sched.id).record)
        assertEquals(LocalDate(2026, 11, 14), s.home.data.schedules.single().nextDue)
    }

    @Test
    fun loggingOneOffMaintenancePaysAndTheDailyShareIsTwo() {
        val s = setup()
        val a = assertNotNull(s.home.logMaintenance("Fixed the door", notes = "Zorblax-note-55", cost = "Zorblax-cost-4500").record)
        assertTrue(a.paid); assertNull(a.scheduleId); assertEquals("Zorblax-cost-4500", a.cost)
        assertTrue(assertNotNull(s.home.logMaintenance("Fixed the tap").record).paid)
        assertFalse(assertNotNull(s.home.logMaintenance("Fixed the lamp").record).paid, "past the day's share")
        assertEquals(BaseRules.MAINTENANCE_PAID_PER_DAY, s.spy.events.size)
        assertNotNull(s.home.logMaintenance(" ").refused)
        s.clock.advanceDays(1)
        assertTrue(assertNotNull(s.home.logMaintenance("Fixed the fan").record).paid, "the next day pays again")
    }

    @Test
    fun deletingAnAssetRemovesItsWarrantiesButKeepsSchedulesRecordsAndNotesUnlinked() {
        val s = setup()
        val asset = assertNotNull(s.home.addAsset("Fridge").record)
        s.home.addWarranty(asset.id, "Shop")
        val sched = assertNotNull(s.home.addSchedule("Defrost", today, Recurrence(RepeatKind.MONTHLY), assetId = asset.id).record)
        s.home.completeMaintenance(sched.id)
        s.home.addReference("Manual", "Page 3", assetId = asset.id)
        s.home.deleteAsset(asset.id)
        val d = s.home.data
        assertTrue(d.assets.isEmpty() && d.warranties.isEmpty())
        assertEquals(1, d.schedules.size); assertNull(d.schedules.single().assetId)
        assertEquals(1, d.maintenance.size); assertNull(d.maintenance.single().assetId)
        assertEquals(1, d.references.size); assertNull(d.references.single().assetId)
    }

    // --- references and search ------------------------------------------------------------------------------------------------------

    @Test
    fun referencesAreAddedUpdatedDeletedAndSearchFindsAcrossKinds() {
        val s = setup()
        val r = assertNotNull(s.home.addReference("Zorblax wifi", "password is on the router").record)
        val edited = assertNotNull(s.home.updateReference(r.copy(title = "Zorblax router", text = "reset with a pin")).record)
        assertEquals("Zorblax router", s.home.data.references.single().title)
        assertTrue(edited.updatedMillis >= r.updatedMillis)
        assertNotNull(s.home.addReference("").refused)
        assertNotNull(s.home.updateReference(r.copy(id = "nope")).refused)
        s.chore("Zorblax dusting")
        s.home.addSupply("Zorblax soap", "bottles")
        val asset = assertNotNull(s.home.addAsset("Zorblax lamp").record)
        s.home.addSchedule("Zorblax service", today)
        s.home.addWarranty(asset.id, provider = "Zorblax Co")
        val kinds = s.home.search("ZORBLAX").map { it.kind }.toSet()
        assertEquals(setOf(HitKind.CHORE, HitKind.SUPPLY, HitKind.ASSET, HitKind.MAINTENANCE, HitKind.WARRANTY, HitKind.REFERENCE), kinds)
        assertTrue(s.home.search("   ").isEmpty())
        assertEquals(listOf(HitKind.REFERENCE), s.home.search("pin").map { it.kind })
        s.home.deleteReference(r.id)
        assertTrue(s.home.data.references.isEmpty())
        assertTrue(s.spy.events.isEmpty())
    }

    // --- export and erase ---------------------------------------------------------------------------------------------------------

    private fun Setup.fill(): String {
        val area = assertNotNull(home.addArea("Quuxville-Annex")).id
        val c = assertNotNull(home.addChore("Zorblax-9000 sweep", daily, null, areaId = area).record)
        home.complete(c.id)
        val sup = assertNotNull(home.addSupply("Zorblax-supply", "bottles", 3000, 1000, areaId = area).record)
        home.setQuantity(sup.id, 4000)
        home.addRestock("Batteries")
        val asset = assertNotNull(home.addAsset("Zorblax-asset", serial = "SN-ZORBLAX-0042", areaId = area).record)
        home.addWarranty(asset.id, "Shop")
        val sc = assertNotNull(home.addSchedule("Service", today, Recurrence(RepeatKind.MONTHLY), assetId = asset.id).record)
        home.completeMaintenance(sc.id)
        home.addReference("Manual", "Zorblax-note-55", areaId = area, assetId = asset.id)
        return area
    }

    @Test
    fun exportHoldsOnlyTheChosenKindsAndTheFormatMarker() {
        val s = setup()
        s.fill()
        val json = GameStore.json
        fun keys(text: String) = json.parseToJsonElement(text).jsonObject.keys
        val chores = s.home.export(setOf(BaseKinds.CHORES))
        assertEquals(setOf("format", "unit", "chores", "entries"), keys(chores))
        assertEquals("pokelife-home-export", json.parseToJsonElement(chores).jsonObject.getValue("format").jsonPrimitive.content)
        assertTrue("Zorblax-9000 sweep" in chores && "Zorblax-supply" !in chores && "Quuxville-Annex" !in chores && "SN-ZORBLAX-0042" !in chores)
        val assets = s.home.export(setOf(BaseKinds.ASSETS, BaseKinds.AREAS))
        assertEquals(setOf("format", "unit", "areas", "assets", "schedules", "maintenance", "warranties"), keys(assets))
        assertTrue("SN-ZORBLAX-0042" in assets && "Zorblax-9000 sweep" !in assets)
        val all = s.home.export()
        assertEquals(setOf("format", "unit", "areas", "chores", "entries", "supplies", "adjustments", "restock", "assets", "schedules", "maintenance", "warranties", "references"), keys(all))
        assertTrue("settings" !in all && "pokemon" !in all.lowercase() && "Sophia" !in all)
        assertEquals(setOf("format", "unit"), keys(s.home.export(emptySet())))
    }

    @Test
    fun erasingAKindErasesOnlyThatKindAndUnlinksPointersAndLeavesTheGameAlone() {
        val s = setup()
        val area = s.fill()
        val player = s.game.data.player
        val settings = s.home.data.settings
        s.home.erase(BaseKinds.AREAS)
        var d = s.home.data
        assertTrue(d.areas.isEmpty())
        assertEquals("Zorblax-9000 sweep", d.chores.single().title); assertNull(d.chores.single().areaId)
        assertNull(d.supplies.single().areaId); assertNull(d.assets.single().areaId); assertNull(d.references.single().areaId)
        assertEquals(1, d.entries.size)
        assertTrue(area !in s.home.export())
        s.home.erase(BaseKinds.ASSETS)
        d = s.home.data
        assertTrue(d.assets.isEmpty() && d.schedules.isEmpty() && d.maintenance.isEmpty() && d.warranties.isEmpty())
        assertNull(d.references.single().assetId); assertEquals("Manual", d.references.single().title)
        assertEquals(1, d.chores.size, "other kinds stay")
        s.home.erase(BaseKinds.CHORES)
        assertTrue(s.home.data.chores.isEmpty() && s.home.data.entries.isEmpty())
        assertEquals(1, s.home.data.supplies.size)
        s.home.erase(BaseKinds.SUPPLIES)
        assertTrue(s.home.data.supplies.isEmpty() && s.home.data.adjustments.isEmpty() && s.home.data.restock.isEmpty())
        assertEquals(1, s.home.data.references.size)
        s.home.eraseAll()
        assertEquals(s.home.data.copy(settings = settings), s.home.data)
        assertTrue(s.home.data.run { areas.isEmpty() && chores.isEmpty() && supplies.isEmpty() && assets.isEmpty() && references.isEmpty() })
        assertEquals(settings, s.home.data.settings)
        assertEquals(player, s.game.data.player, "Pokémon, Bag and Poké are untouched")
    }

    @Test
    fun eraseAllKeepsTheSettingsAndTheStartingAreasDoNotComeBack() {
        val s = setup()
        s.home.setProgression(false)
        s.home.addUnit("crates")
        s.home.eraseAll()
        assertTrue(s.home.data.areas.isEmpty())
        s.reopen()
        assertTrue(s.home.data.areas.isEmpty())
        assertFalse(s.home.data.settings.progression)
        assertTrue("crates" in s.home.data.settings.units)
    }

    // --- persistence ----------------------------------------------------------------------------------------------------------------

    @Test
    fun aReopenedStoreReadsBackIdenticalData() {
        val s = setup()
        s.fill()
        s.home.setSectionHidden("supplies", true)
        s.home.markTutorialSeen()
        val before = s.home.data
        assertTrue(before.chores.isNotEmpty() && before.entries.isNotEmpty() && before.maintenance.isNotEmpty())
        s.reopen()
        assertEquals(before, s.home.data)
        assertEquals(before, BaseStore(MemoryStorage(BaseStore.json.encodeToString(app.pokeplan.base.BaseData.serializer(), before)), s.clock, s.spy, utc).data)
    }

    // --- the tutorial -------------------------------------------------------------------------------------------------------------------

    @Test
    fun theTidyTutorialIsOfferedAfterOnboardingOnlyWhileHomeHasNoRecords() {
        assertFalse(setup(started = false).game.tutorialOffered(Tutorials.BASE_TIDY, homeRecords = 0))
        val s = setup()
        assertTrue(s.game.tutorialOffered(Tutorials.BASE_TIDY, homeRecords = 0))
        assertFalse(s.game.tutorialOffered(Tutorials.BASE_TIDY, homeRecords = 1))
    }

    @Test
    fun theTidyTutorialGivesOneHouseholdPokemonIntoBasesPartyAsLeadOnceWithNoRecordMeterOrCap() {
        val s = setup()
        val rewards = assertNotNull(s.game.completeTutorial(Tutorials.BASE_TIDY))
        val egg = rewards.filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(egg.id, s.game.data.player.partyOf(PartyIds.BASE).single().id)
        assertEquals(Place.Party(PartyIds.BASE), s.game.placeOf(egg.id))
        assertEquals(Region.BASE, s.game.data.origins[egg.id]?.region)
        assertEquals(Origin.How.HABITAT_EGG, s.game.data.origins[egg.id]?.how)
        assertEquals(egg.id, s.game.data.player.baseLead?.id)
        assertEquals(0, s.game.homeEggMeter())
        assertTrue(s.game.data.basePaid.isEmpty() && s.game.data.activeDays.isEmpty())
        assertTrue(s.home.data.chores.isEmpty() && s.home.data.entries.isEmpty())
        assertFalse(s.game.tutorialOffered(Tutorials.BASE_TIDY))
        assertNull(s.game.completeTutorial(Tutorials.BASE_TIDY))
        assertEquals(1, s.game.data.player.partyOf(PartyIds.BASE).size)
        assertEquals(2, s.game.data.player.pokemon.size)
    }

    // --- the Habitat Egg ----------------------------------------------------------------------------------------------------------

    @Test
    fun enoughPaidChoresFindAHabitatEggIntoBasesPartyAndTheMeterWraps() {
        val s = setup()
        val c = s.chore("Dishes", daily)
        val points = RewardEngine.HOME_EGG_POINTS.getValue(BaseKind.CHORE)
        val needed = RewardEngine.HOME_EGG_METER_SIZE / points
        assertEquals(0, s.game.data.player.partyOf(PartyIds.BASE).size)
        for (day in 1..needed) {
            val entry = assertNotNull(s.home.complete(c.id).record)
            assertTrue(entry.paid, "day $day pays")
            if (day < needed) {
                assertEquals(day * points, s.game.homeEggMeter())
                assertEquals(0, s.game.data.player.partyOf(PartyIds.BASE).size, "no egg yet on day $day")
                s.clock.advanceDays(1)
            }
        }
        val egg = s.game.data.player.partyOf(PartyIds.BASE).single()
        assertEquals(Origin.How.HABITAT_EGG, s.game.data.origins[egg.id]?.how)
        assertEquals(Region.BASE, s.game.data.origins[egg.id]?.region)
        assertEquals(egg.id, s.game.data.player.baseLead?.id)
        assertEquals(0, s.game.homeEggMeter(), "the meter wraps")
        s.clock.advanceDays(1)
        assertTrue(assertNotNull(s.home.complete(c.id).record).paid)
        assertEquals(points, s.game.homeEggMeter())
        assertEquals(1, s.game.data.player.partyOf(PartyIds.BASE).size)
    }

    @Test
    fun aRecordOfWorkAndAWarrantyCanBeCorrected() {
        val s = setup()
        val asset = assertNotNull(s.home.addAsset("Kettle").record)
        val w = assertNotNull(s.home.addWarranty(asset.id, "Shop", startDay = LocalDate(2026, 1, 1), expiryDay = LocalDate(2027, 1, 1)).record)
        val fixed = assertNotNull(s.home.updateWarranty(w.copy(expiryDay = LocalDate(2028, 3, 4), provider = " Other shop ")).record)
        assertEquals(LocalDate(2028, 3, 4), fixed.expiryDay)
        assertEquals("Other shop", fixed.provider)
        assertNotNull(s.home.updateWarranty(w.copy(expiryDay = LocalDate(2025, 1, 1))).refused)

        val r = assertNotNull(s.home.logMaintenance("Descaled").record)
        val moved = assertNotNull(s.home.updateMaintenanceRecord(r.copy(day = LocalDate(2026, 11, 1), title = " Descaled the kettle ", cost = "PHP 50")).record)
        assertEquals(LocalDate(2026, 11, 1), moved.day)
        assertEquals("Descaled the kettle", moved.title)
        assertEquals(r.paid, moved.paid, "a correction never pays and never takes back")
        assertNotNull(s.home.updateMaintenanceRecord(r.copy(day = today.plus(kotlinx.datetime.DatePeriod(days = 1)))).refused)
        assertNotNull(s.home.updateMaintenanceRecord(r.copy(title = " ")).refused)
    }
}
