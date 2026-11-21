package app.pokeplan.app.heal

import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.Imported
import app.pokeplan.heal.ProviderRead
import app.pokeplan.heal.VitalType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.pokeplan.app.Counters
import app.pokeplan.app.GameStore
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class HealStepsAndVitalsTest {
    private class FakeClock(var millis: Long) : Clock { override fun nowMillis() = millis }
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<HealEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun heal(event: HealEvent): List<Reward>? { events += event; return inner.heal(event) }
    }

    private val utc = TimeZone.UTC
    private fun at(h: Int, mi: Int, day: Int = 14) = LocalDateTime(2026, 11, day, h, mi).toInstant(utc).toEpochMilliseconds()
    private val clock = FakeClock(at(21, 0))
    private lateinit var game: GameStore
    private fun setup(): Pair<HealStore, Spy> {
        game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val spy = Spy(game)
        return HealStore(MemoryStorage(), clock, spy, utc, rng = Random(3)) to spy
    }

    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, if (id.isEmpty()) "" else "com.huawei.health:id/$id")
    private fun summary(steps: Int, stress: Int) = listOf(
        n("tv_sport_intensity", "Steps"), n("time_strength_layout_middle", description = "%,d steps".format(steps)),
        n("data_layout_emotion_pressure", description = "Stress, $stress, Normal"), n("pressure_title", "Stress"), n("pressure_value", "$stress"), n("pressure_des", "Normal"),
    )
    private fun cards(bpm: Int = 72, skin: String = "34.1", spo2: Int = 97) = listOf(
        n("function_set_items_title", "Heart"), n("function_set_items_data", "Heart rate $bpm bpm"), n("function_set_bottom_left", "11/14 | November 14"),
        n("function_set_items_title", "Skin temperature"), n("function_set_items_data", "$skin°C | $skin degrees Celsius"),
        n("function_set_items_title", "SpO₂"), n("function_set_items_data", "$spo2%"), n("function_set_bottom_left", "11/14 | November 14"),
    )
    private val insights = listOf(n("insights_text_one", "Your Sleep score is 81 points."), n("tag_one", "Abnormal"))
    private fun page(atMillis: Long, vararg nodes: List<ScreenNode>) = ScreenCapture(atMillis, "MainActivity", nodes.toList().flatten())
    private fun sleepPage(atMillis: Long, date: String, score: Int) = ScreenCapture(atMillis, "KnitSleepDetailActivity", listOf(
        n("action_bar_title", "Sleep"), n("fitness_detail_time_date_tv", date), n("common_sleep_sleep_time", "6 h 5 min"), n("scoring_value", "$score"), n("scoring_unit", "points"),
    ))

    private fun seen(vararg s: ScreenCapture, notes: List<NotificationCapture> = emptyList()) = HuaweiSeen(s.toList(), notes)

    @Test fun theHealthPageGivesStepsAndEveryReading() {
        val rs = HuaweiRecords.read(seen(page(at(20, 0), summary(5_120, 31), cards(), insights)), clock.millis, utc)
        val steps = rs.filterIsInstance<Imported.Steps>().single()
        assertEquals(5_120, steps.steps); assertEquals("steps:2026-11-14", steps.id)
        val v = rs.filterIsInstance<Imported.Vital>().associate { it.type to it.value }
        assertEquals(mapOf(VitalType.HEART_RATE to 72.0, VitalType.SKIN_TEMPERATURE to 34.1, VitalType.SPO2 to 97.0, VitalType.STRESS to 31.0, VitalType.SLEEP_SCORE to 81.0), v)
    }

    @Test fun theNotificationAndThePageMakeOneRecordWithTheLargerCount() {
        val note = NotificationCapture(at(20, 30), "6,010 steps", "250 kcal")
        val rs = HuaweiRecords.read(seen(page(at(20, 0), summary(5_120, 31)), notes = listOf(note)), clock.millis, utc)
        assertEquals(listOf(6_010), rs.filterIsInstance<Imported.Steps>().map { it.steps })
    }

    @Test fun theSleepPagesScoreBelongsToThatNightAndAWeekViewMakesNothing() {
        val rs = HuaweiRecords.read(seen(sleepPage(at(20, 0), "Fri, Nov 13", 77), sleepPage(at(20, 1), "Nov 8 - Nov 14", 70)), clock.millis, utc)
        val score = rs.filterIsInstance<Imported.Vital>().single()
        assertEquals("sleepscore:2026-11-13", score.id); assertEquals(77.0, score.value)
        assertTrue(score.atMillis in at(0, 0, 13)..at(23, 59, 13), "stamped inside the night's own day")
    }

    @Test fun missingIsNotZero() {
        assertTrue(HuaweiRecords.read(seen(page(at(20, 0), summary(0, 0))), clock.millis, utc).isEmpty())
        val odd = page(at(20, 0), listOf(n("function_set_items_title", "SpO₂"), n("function_set_items_data", "--"), n("function_set_items_title", "Skin temperature"), n("function_set_items_data", "--")))
        assertTrue(HuaweiRecords.read(seen(odd), clock.millis, utc).isEmpty())
    }

    @Test fun readingTwiceAddsNothingAndNothingIsDuplicated() {
        val (heal, _) = setup()
        val s = seen(page(at(20, 0), summary(5_120, 31), cards(), insights), sleepPage(at(20, 1), "Today", 81))
        val first = heal.readHuawei(s)
        heal.importFromProvider(first)
        val before = heal.data
        val again = heal.importFromProvider(heal.readHuawei(s))
        assertTrue(again.isEmpty, "the same captures bring nothing new")
        assertEquals(before.steps, heal.data.steps); assertEquals(before.vitals, heal.data.vitals)
        assertEquals(1, heal.data.steps.size)
        assertEquals(1, heal.data.vitals.count { it.type == VitalType.SLEEP_SCORE }, "the Sleep page and the insights line are the same day's score")
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 40), summary(5_120, 31), cards(), insights))))
        assertEquals(before.vitals.size, heal.data.vitals.size)
    }

    @Test fun aLaterReadUpdatesTheDaysRecordInsteadOfAddingOne() {
        val (heal, _) = setup()
        heal.importFromProvider(heal.readHuawei(seen(page(at(12, 0), summary(2_300, 20), cards(bpm = 70, spo2 = 96)))))
        val plan = heal.importFromProvider(heal.readHuawei(seen(page(at(20, 0), summary(7_400, 35), cards(bpm = 88, spo2 = 98)))))
        assertTrue(plan.updated >= 2)
        assertEquals(listOf(7_400), heal.data.steps.map { it.steps })
        assertEquals(35.0, HealRules.latestVital(heal.data, VitalType.STRESS)!!.value)
        assertEquals(1, heal.data.vitals.count { it.type == VitalType.STRESS }); assertEquals(1, heal.data.vitals.count { it.type == VitalType.SPO2 })
        assertEquals(2, heal.data.vitals.count { it.type == VitalType.HEART_RATE }, "a different bpm is a different reading")
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 30), summary(900, 35)))))
        assertEquals(listOf(7_400), heal.data.steps.map { it.steps })
    }

    @Test fun stepsPayPerAmountOnceAndNeverTwice() {
        val (heal, spy) = setup()
        heal.importFromProvider(heal.readHuawei(seen(page(at(12, 0), summary(1_999, 20)))))
        assertTrue(spy.events.none { it.kind == HealKind.STEPS }, "under 2,000 pays nothing")
        heal.importFromProvider(heal.readHuawei(seen(page(at(15, 0), summary(4_100, 20)))))
        assertEquals(2, spy.events.count { it.kind == HealKind.STEPS })
        heal.importFromProvider(heal.readHuawei(seen(page(at(18, 0), summary(4_100, 20)))))
        assertEquals(2, spy.events.count { it.kind == HealKind.STEPS }, "the same count pays nothing more")
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 0), summary(25_000, 20)))))
        assertEquals(HealRules.STEPS_PAID_PER_DAY, spy.events.count { it.kind == HealKind.STEPS }, "capped at 5 amounts a day, 10,000 steps")
        assertEquals(HealRules.STEPS_PAID_PER_DAY, heal.data.steps.single().paidBuckets)
        heal.deleteSteps(heal.data.steps.single().id)
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 10), summary(25_000, 20)))))
        assertEquals(HealRules.STEPS_PAID_PER_DAY, spy.events.count { it.kind == HealKind.STEPS })
        assertEquals(HealRules.STEPS_PAID_PER_DAY, Counters.heal(HealKind.STEPS).get(game.data.counters, game.today))
    }

    @Test fun stepsOfAnotherDayAndFieldResearchOffPayNothing() {
        val (heal, spy) = setup()
        val yesterday = HuaweiRecords.read(seen(page(at(20, 0, day = 13), summary(9_000, 20))), clock.millis, utc)
        heal.importFromProvider(ProviderRead(yesterday, 0, clock.millis))
        assertTrue(spy.events.none { it.kind == HealKind.STEPS }, "only today's steps pay: the daily limit counts today")
        heal.setFieldResearch(false)
        val before = spy.events.size
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 0), summary(9_000, 20)))))
        assertEquals(before, spy.events.size, "Field Research off: nothing is sent to the world")
        assertEquals(0, heal.data.steps.first { it.day == LocalDate(2026, 11, 14) }.paidBuckets)
    }

    @Test fun aReadPaysOncePerFamilyPerDay() {
        val (heal, spy) = setup()
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 0), summary(1_000, 31), cards()))))
        assertEquals(1, spy.events.count { it.kind == HealKind.IMPORT }, "readings are one family")
        heal.importFromProvider(heal.readHuawei(seen(page(at(20, 30), summary(1_000, 40), cards(bpm = 90)))))
        assertEquals(1, spy.events.count { it.kind == HealKind.IMPORT }, "a second read the same day pays the family nothing more")
        heal.importFromProvider(ProviderRead(listOf(Imported.Sleep(HealImport.PROVIDER_HEALTH_CONNECT, "com.x", "n1", at(0, 0), at(7, 0))), 0, clock.millis))
        assertEquals(2, spy.events.count { it.kind == HealKind.IMPORT }, "sleep is another family")
        assertEquals(0, spy.events.count { it.kind == HealKind.STEPS })
    }

    @Test fun oldHistoryAndAReadWithNothingNewPayNothing() {
        val (heal, spy) = setup()
        heal.importFromProvider(ProviderRead(listOf(Imported.Sleep(HealImport.PROVIDER_HEALTH_CONNECT, "com.x", "old", at(0, 0, 1), at(7, 0, 1))), 0, clock.millis))
        assertTrue(spy.events.isEmpty(), "a night from two weeks ago is history, not a reward")
        val s = seen(page(at(20, 0), summary(1_000, 31), cards()))
        heal.importFromProvider(heal.readHuawei(s))
        val paid = spy.events.size
        clock.millis = at(22, 0)
        heal.importFromProvider(heal.readHuawei(s))
        assertEquals(paid, spy.events.size)
    }

    @Test fun anImplausibleReadingIsRejectedNotRepaired() {
        val (heal, _) = setup()
        val bad = ProviderRead(listOf(
            Imported.Vital(HuaweiHealth.PROVIDER, HuaweiHealth.PACKAGE, "spo2:x", VitalType.SPO2, 140.0, clock.millis - 1000),
            Imported.Vital(HuaweiHealth.PROVIDER, HuaweiHealth.PACKAGE, "hr:x", VitalType.HEART_RATE, 5.0, clock.millis - 1000),
            Imported.Steps(HuaweiHealth.PROVIDER, HuaweiHealth.PACKAGE, "steps:x", 900_000, clock.millis - 1000),
        ), 0, clock.millis)
        val plan = heal.importFromProvider(bad)
        assertEquals(3, plan.rejected); assertTrue(heal.data.vitals.isEmpty() && heal.data.steps.isEmpty())
    }
}
