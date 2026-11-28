package app.pokeplan.app.heal

import app.pokeplan.app.testing.FakeClock
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

    /** Probe 14's Weight All data page: a day header with the day's readings under it, each with its own time. */
    private val weightHistory = ScreenCapture(20, "HealthDataHistoryActivity", listOf(
        n("action_bar_title", "All data"),
        n("health_data_weight_father_ll", "11/14/2026, average of 54.8 kg per day"),
        n("child_item_weight", "54.9"), n("child_item_weight_unit", "kg"), n("child_item_fat", "28.4%"),
        n("child_item_skeletal_muscle", "21.2"), n("child_item_skeletal_muscle_unit", "kg"), n("child_item_time", "7:12 AM"),
        n("health_data_weight_father_ll", "11/13/2026, average of 55.0 kg per day"),
        n("child_item_weight", "55.0"), n("child_item_weight_unit", "kg"), n("child_item_time", "6:50 AM"),
    ))

    @Test fun theWeightAllDataPageGivesEachReadingItsOwnDateAndTime() {
        val bodies = HuaweiRecords.read(HuaweiSeen(listOf(weightHistory)), now, utc).filterIsInstance<Imported.Body>()
        assertEquals(listOf(MeasureType.WEIGHT to 54.9, MeasureType.BODY_FAT to 28.4, MeasureType.WEIGHT to 55.0), bodies.map { it.type to it.value })
        assertEquals(at(2026, 11, 14, 7, 12), bodies[0].atMillis)
        assertEquals(at(2026, 11, 13, 6, 50), bodies[2].atMillis, "a day without body fat still gives its weight")
        assertEquals(listOf("weight:${at(2026, 11, 14, 7, 12)}", "bodyfat:${at(2026, 11, 14, 7, 12)}", "weight:${at(2026, 11, 13, 6, 50)}"), bodies.map { it.id })
    }

    @Test fun eachPageGivesItsRecordsToItsOwnSource() {
        val home = ScreenCapture(21, "MainActivity", listOf(n("function_set_items_title", "Heart"), n("function_set_items_data", "Heart rate 72 bpm"), n("time_strength_layout_middle", description = "8,432 steps")))
        val parts = HuaweiRecords.sourceRecords(HuaweiSeen(listOf(exercise, weightPage, weightHistory, dayPage, allData, home)), now, utc)
        assertTrue(parts.getValue("exercise").filterIsInstance<Imported.Exercise>().isNotEmpty())
        assertTrue(parts.getValue("weight").filterIsInstance<Imported.Body>().isNotEmpty())
        assertTrue(parts.getValue("sleep").filterIsInstance<Imported.Sleep>().isNotEmpty(), "a Sleep day page is Sleep's")
        assertTrue(parts.getValue("sleep").filterIsInstance<Imported.Vital>().any { it.type == app.pokeplan.heal.VitalType.SLEEP_LENGTH }, "a duration row is Sleep's")
        assertTrue(parts.getValue("skin").filterIsInstance<Imported.Vital>().any { it.type == app.pokeplan.heal.VitalType.SKIN_TEMPERATURE_LOW })
        assertTrue(parts.getValue("spo2").filterIsInstance<Imported.Vital>().any { it.type == app.pokeplan.heal.VitalType.SPO2_LOW })
        assertTrue(parts.getValue("steps").filterIsInstance<Imported.Steps>().isNotEmpty())
        assertTrue(parts.getValue("health").filterIsInstance<Imported.Vital>().any { it.type == app.pokeplan.heal.VitalType.HEART_RATE_LOW }, "the Health page's cards are the read source")
    }

    @Test fun readingTheSamePagesTwiceGivesTheSameIds() {
        val seen = HuaweiSeen(listOf(exercise, weightPage, exercise.copy(atMillis = 9)))
        val ids = HuaweiRecords.read(seen, now, utc).map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(ids, HuaweiRecords.read(seen, now + 60_000, utc).map { it.id })
    }

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

    private fun rowOf(content: String, date: String) = listOf(n("privacy_content_title", content), n("privacy_time", date))
    private val allData = ScreenCapture(9, "PrivacyDataModelActivity", listOf(n("privacy_title", "November 2026")) +
        rowOf("5 h 9 min", "11/12") + rowOf("42 min", "11/12") + rowOf("20.1-23.4°C", "11/11") + rowOf("SpO₂ range 94%-99%", "November 10") + rowOf("nonsense", "11/9") +
        listOf(n("privacy_title", "October 2026")) + rowOf("6 h", "10/30") + rowOf("7 h 2 min", "11/30"))

    @Test fun allDataRowsBecomeHistoryRecordsOnTheirOwnDays() {
        val vitals = HuaweiRecords.read(HuaweiSeen(listOf(allData)), now, utc).filterIsInstance<Imported.Vital>().associateBy { it.id }
        fun noon(m: Int, d: Int, y: Int = 2026) = at(y, m, d, 12, 0)
        assertEquals(309.0, vitals["sleeplength:309:2026-11-12"]?.value); assertEquals(42.0, vitals["sleeplength:42:2026-11-12"]?.value, "a nap and a night are both kept")
        assertEquals(360.0, vitals["sleeplength:360:2026-10-30"]?.value, "the October header gives the year of the rows under it")
        assertEquals(noon(11, 12), vitals["sleeplength:309:2026-11-12"]?.atMillis)
        assertEquals(20.1, vitals["skintemplow:2026-11-11"]?.value); assertEquals(23.4, vitals["skintemphigh:2026-11-11"]?.value)
        assertEquals(94.0, vitals["spo2low:2026-11-10"]?.value); assertEquals(99.0, vitals["spo2high:2026-11-10"]?.value)
        assertTrue(vitals.keys.none { "11-09" in it }, "a row that is not a value is left out")
        assertTrue(vitals.keys.none { "2026-11-30" in it }, "a date in the future is left out")
        assertEquals(app.pokeplan.heal.VitalType.SLEEP_LENGTH, vitals["sleeplength:309:2026-11-12"]?.type)
    }

    @Test fun allDataRowsReadAgainAddNothingTwice() {
        val once = HuaweiRecords.read(HuaweiSeen(listOf(allData)), now, utc)
        val twice = HuaweiRecords.read(HuaweiSeen(listOf(allData, allData.copy(atMillis = 99))), now, utc)
        assertEquals(once.map { it.id }.sorted(), twice.map { it.id }.sorted())
        assertEquals(LocalDate(2026, 10, 30), HuaweiRecords.oldestAllDataDay(allData, now, utc))
    }

    private val dayPage = ScreenCapture(10, "PrivacyDataModelActivity", listOf(
        n("action_bar_title", "11/12/2026 data"),
        n("privacy_content_title", "6 h 20 min"), n("privacy_time", "11/11, 11:30 PM-11/12, 5:50 AM"),
        n("privacy_content_title", "42 min"), n("privacy_time", "11/12, 1:00 PM-1:42 PM"),
        n("privacy_content_title", "oops"), n("privacy_time", "11/12, 9:00 AM-8:00 AM"),
    ))

    @Test fun aSleepDayPageGivesNightsAndNapsWithTheirOwnTimes() {
        val sleeps = HuaweiRecords.read(HuaweiSeen(listOf(dayPage)), now, utc).filterIsInstance<Imported.Sleep>()
        assertEquals(listOf(at(2026, 11, 11, 23, 30) to at(2026, 11, 12, 5, 50), at(2026, 11, 12, 13, 0) to at(2026, 11, 12, 13, 42)), sleeps.map { it.startMillis to it.endMillis })
        assertTrue(sleeps.all { it.provider == HuaweiHealth.PROVIDER && it.id == "sleep:${it.startMillis}" })
    }

    @Test fun anAcrossYearsSpanAndAnOvernightWithoutADateEndRight() {
        val newYear = ScreenCapture(11, "PrivacyDataModelActivity", listOf(n("action_bar_title", "01/01/2026 data"), n("privacy_content_title", "7 h"), n("privacy_time", "12/31, 11:00 PM-6:00 AM")))
        val s = HuaweiRecords.read(HuaweiSeen(listOf(newYear)), now, utc).filterIsInstance<Imported.Sleep>().single()
        assertEquals(at(2025, 12, 31, 23, 0) to at(2026, 1, 1, 6, 0), s.startMillis to s.endMillis, "a date after the page's day is the year before; no end date means the next morning")
    }

    @Test fun realNightsReplaceTheLengthOnlyReadingAndTodaysNightFromTheSleepPage() {
        val today = ScreenCapture(12, "PrivacyDataModelActivity", listOf(n("action_bar_title", "11/14/2026 data"), n("privacy_content_title", "5 h 9 min"), n("privacy_time", "11/14, 2:00 AM-7:09 AM")))
        val list = ScreenCapture(13, "PrivacyDataModelActivity", listOf(n("privacy_title", "November 2026")) + rowOf("5 h 9 min", "11/14") + rowOf("6 h 2 min", "11/13"))
        val records = HuaweiRecords.read(HuaweiSeen(listOf(home, sleepPage("Today"), list, today)), now, utc)
        assertEquals(listOf("sleep:${at(2026, 11, 14, 2, 0)}"), records.filterIsInstance<Imported.Sleep>().map { it.id }, "the Sleep page's night for today is replaced")
        val lengths = records.filterIsInstance<Imported.Vital>().filter { it.type == app.pokeplan.heal.VitalType.SLEEP_LENGTH }.map { it.id }
        assertEquals(listOf("sleeplength:362:2026-11-13"), lengths, "a day with real times keeps no length-only reading")
    }

    @Test fun theStepsAllDataListBecomesADailyHistory() {
        val list = ScreenCapture(14, "PrivacyDataModelActivity", listOf(n("action_bar_title", "All recorded data"), n("privacy_title", "November 2026")) +
            rowOf("6,842 steps", "11/13") + rowOf("912 steps", "11/12") + listOf(n("privacy_title", "October 2026")) + rowOf("10,004 steps", "10/31"))
        val steps = HuaweiRecords.read(HuaweiSeen(listOf(list)), now, utc).filterIsInstance<Imported.Steps>().associate { it.id to it.steps }
        assertEquals(mapOf("steps:2026-11-13" to 6842, "steps:2026-11-12" to 912, "steps:2026-10-31" to 10004), steps)
    }

    @Test fun aDaysStepsKeepTheLargestCountSeen() {
        val list = ScreenCapture(14, "PrivacyDataModelActivity", listOf(n("privacy_title", "November 2026")) + rowOf("500 steps", "11/14"))
        val live = ScreenCapture(15, "MainActivity", listOf(n("time_strength_layout_middle", description = "8432 steps")))
        val steps = HuaweiRecords.read(HuaweiSeen(listOf(list, live.copy(atMillis = now))), now, utc).filterIsInstance<Imported.Steps>()
        assertEquals(listOf(8432), steps.map { it.steps }, "one record for the day, the larger count")
    }
}
