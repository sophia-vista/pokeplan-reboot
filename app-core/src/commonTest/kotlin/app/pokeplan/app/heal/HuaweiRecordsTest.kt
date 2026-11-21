package app.pokeplan.app.heal

import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.Imported
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.ProviderRead
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class HuaweiRecordsTest {
    private val utc = TimeZone.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()
    private val now = at(2026, 11, 14, 21, 0)
    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, if (id.isEmpty()) "" else "com.huawei.health:id/$id")
    private fun item(type: String, date: String, time: String, km: String, unit: String, duration: String) = listOf(
        n("hw_show_health_running_history_child_item_chief_sport_type_text", type), n("hw_show_health_running_history_child_item_date", date),
        n("hw_show_health_running_history_child_item_left_date_text", time), n("hw_show_health_running_history_child_item_chief_sport_data_text", km),
        n("hw_show_health_running_history_child_item_chief_sport_data_unit", unit), n("hw_show_health_running_history_child_item_center_text", duration),
        n("hw_show_health_running_history_child_item_right_text", "12'30''"),
    )

    private val exercise = ScreenCapture(1, "SportHistoryActivity", listOf(
        n("first_item_title", description = "November 2026, 6.20 km  1.5 h  2 times  420 kcal (total)"),
        *item("Outdoor walk", "November 12", "5:40 PM", "3.10", "km", "00:41:05").toTypedArray(),
        *item("Strength", "November 10", "7:05 AM", "120", "kcal (total)", "00:25:00").toTypedArray(),
        n("first_item_title", description = "July 2026, 25 min 0 sec  1 time  90 kcal (total)"),
        *item("Indoor walk", "July 3", "12:00 PM", "1.00", "km", "15:20").toTypedArray(),
    ))

    private val weightPage = ScreenCapture(2, "H5proWebViewContainerActivity", listOf(
        n("", "Back"), n("", "Weight"), n("", "Syncing…"), n("", "Thu, July 3 at 07:12"), n("", "54.8"), n("", "kg"), n("", "BMI 24.1"), n("", "Overweight"), n("", "Body fat 28.4%"),
    ))

    private fun sleepPage(date: String) = ScreenCapture(3, "KnitSleepDetailActivity", listOf(
        n("action_bar_title", "Sleep"), n("fitness_detail_time_date_tv", date), n("day_time_period", "Night sleep"), n("common_sleep_sleep_time", "5 h 9 min"),
    ))
    private val home = ScreenCapture(0, "MainActivity", listOf(n("data_layout_step_layout", description = "07:12, Get-up time")))

    @Test fun exerciseRecordsBecomeSessions() {
        val records = HuaweiRecords.read(HuaweiSeen(listOf(exercise)), now, utc).filterIsInstance<Imported.Exercise>()
        assertEquals(3, records.size)
        val walk = records[0]
        assertEquals(ActivityType.WALK, walk.type)
        assertEquals(at(2026, 11, 12, 17, 40), walk.startMillis)
        assertEquals(41 * 60_000L + 5_000, walk.endMillis - walk.startMillis)
        assertEquals(3100, walk.distanceMeters)
        assertEquals(HuaweiHealth.PROVIDER, walk.provider)
        assertEquals(ActivityType.STRENGTH, records[1].type); assertNull(records[1].distanceMeters, "kcal is not a distance")
        assertEquals(at(2026, 7, 3, 12, 0), records[2].startMillis, "the month header gives the year; mm:ss is a duration too")
    }

    @Test fun theWeightPageGivesWeightAndBodyFatAtTheirOwnTime() {
        val records = HuaweiRecords.read(HuaweiSeen(listOf(weightPage)), now, utc).filterIsInstance<Imported.Body>()
        val time = at(2026, 7, 3, 7, 12)
        assertEquals(listOf(MeasureType.WEIGHT to 54.8, MeasureType.BODY_FAT to 28.4), records.map { it.type to it.value })
        assertTrue(records.all { it.atMillis == at(2025, 7, 3, 7, 12) }, "the weekday picks the year: ${records.map { it.atMillis }} vs $time")
        assertEquals(listOf("kg", "%"), records.map { it.unit })
    }

    @Test fun sleepIsSavedForTodayOnlyEndingAtTheGetUpTime() {
        val today = HuaweiRecords.read(HuaweiSeen(listOf(home, sleepPage("Sat, Nov 14"))), now, utc).single() as Imported.Sleep
        assertEquals(at(2026, 11, 14, 7, 12), today.endMillis)
        assertEquals(309 * 60_000L, today.endMillis - today.startMillis)
        assertTrue(HuaweiRecords.read(HuaweiSeen(listOf(home, sleepPage("Fri, Nov 13"))), now, utc).isEmpty(), "another night has no times on the page")
        assertTrue(HuaweiRecords.read(HuaweiSeen(listOf(sleepPage("Sat, Nov 14"))), now, utc).isEmpty(), "without the get-up time there is no end")
    }

    @Test fun readingTheSamePagesTwiceGivesTheSameIds() {
        val seen = HuaweiSeen(listOf(exercise, weightPage, exercise.copy(atMillis = 9)))
        val ids = HuaweiRecords.read(seen, now, utc).map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(ids, HuaweiRecords.read(seen, now + 60_000, utc).map { it.id })
    }

    private class FakeClock(var millis: Long) : Clock { override fun nowMillis() = millis }
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<HealEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun heal(event: HealEvent): List<Reward>? { events += event; return inner.heal(event) }
    }

    private fun store(): Pair<HealStore, Spy> {
        val clock = FakeClock(now)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val spy = Spy(game)
        return HealStore(MemoryStorage(), clock, spy, utc, rng = Random(3)) to spy
    }

    private fun session(id: String, daysAgo: Int, minutes: Int = 30, provider: String = HuaweiHealth.PROVIDER, hourOfDay: Int = 9): Imported.Exercise {
        val start = at(2026, 11, 14 - daysAgo, hourOfDay, 0)
        return Imported.Exercise(provider, HuaweiHealth.PACKAGE, id, ActivityType.WALK, start, start + minutes * 60_000L)
    }

    @Test fun huaweiSessionsFromTheLastWeekPayLikeManualOnes() {
        val (heal, spy) = store()
        heal.importFromProvider(ProviderRead(listOf(session("a", 3), session("b", 7), session("old", 9), session("short", 1, minutes = 5)), 0, now))
        assertEquals(setOf("a", "b"), heal.data.activities.filter { it.paid }.map { it.provenance!!.externalId }.toSet(), "a week back pays; nine days or five minutes do not")
        assertEquals(2, spy.events.size)
        assertEquals(0, heal.data.settings.healthLastSyncMillis, "a Huawei read is not a Health Connect sync")
    }

    @Test fun theDailyShareStillCountsOnTheSessionsOwnDay() {
        val (heal, spy) = store()
        heal.importFromProvider(ProviderRead((6..10).map { session("s$it", 2, hourOfDay = it) }, 0, now))
        assertEquals(3, spy.events.size, "three paid sessions a day, read in one go or not")
        assertEquals(listOf("s6", "s7", "s8"), heal.data.activities.filter { it.paid }.map { it.provenance!!.externalId }.sorted(), "oldest first")
    }

    @Test fun healthConnectAndRepeatsOfAManualSessionStillPayNothing() {
        val (heal, spy) = store()
        heal.importFromProvider(ProviderRead(listOf(session("hc", 1, provider = HealImport.PROVIDER_HEALTH_CONNECT)), 0, now))
        assertTrue(spy.events.all { it.kind == app.pokeplan.core.reward.HealKind.IMPORT }, "the session itself pays nothing; only the read does (rules version 25)")
        heal.logActivity(ActivityType.WALK, 30)
        val paidByHand = spy.events.size
        heal.importFromProvider(ProviderRead(listOf(Imported.Exercise(HuaweiHealth.PROVIDER, HuaweiHealth.PACKAGE, "dup", ActivityType.WALK, now - 30 * 60_000L, now)), 0, now))
        assertEquals(paidByHand, spy.events.size)
        assertTrue(heal.data.activities.single { it.provenance?.externalId == "dup" }.let { !it.paid && it.provenance!!.possibleDuplicateOf != null })
    }

    @Test fun theReminderComesNightlyOrWeeklyFromTheLastRead() {
        val (heal, _) = store()
        val app = AppData()
        assertNull(HealReminders.huaweiUpcoming(heal.data, app, now, utc), "off by default")
        heal.setHuaweiReminder(1, LocalTime(21, 30))
        assertEquals(at(2026, 11, 14, 21, 30), HealReminders.huaweiUpcoming(heal.data, app, now, utc)!!.atMillis, "never read: due today")
        heal.markHuaweiRead()
        assertEquals(LocalDate(2026, 11, 15), HealReminders.huaweiUpcoming(heal.data, app, now, utc)!!.day)
        heal.setHuaweiReminder(7, LocalTime(21, 30))
        val weekly = HealReminders.huaweiUpcoming(heal.data, app, now, utc)!!
        assertEquals(LocalDate(2026, 11, 21), weekly.day)
        assertTrue(!HealReminders.huaweiStillWanted(heal.data, LocalDate(2026, 11, 15), LocalDate(2026, 11, 15), utc), "read since: not wanted before it is due")
    }
}
