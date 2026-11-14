package app.pokeplan.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HuaweiProbeTest {
    @Test fun stepsAreFoundInTheUsualShapes() {
        assertEquals(8432, HuaweiHealth.stepsIn("8,432 steps"))
        assertEquals(8432, HuaweiHealth.stepsIn("Today", "8432 Steps · 5.1 km"))
        assertEquals(12045, HuaweiHealth.stepsIn("Steps: 12 045"))
        assertEquals(1, HuaweiHealth.stepsIn("1 step"))
        assertEquals(0, HuaweiHealth.stepsIn("0 steps"), "a real zero shown by Huawei Health is a zero")
    }

    @Test fun missingOrImpossibleIsNotACount() {
        assertNull(HuaweiHealth.stepsIn(null, "", "Huawei Health is running"))
        assertNull(HuaweiHealth.stepsIn("5.1 km, 312 kcal"))
        assertNull(HuaweiHealth.stepsIn("999999 steps"))
    }

    @Test fun hidingNumbersKeepsTheLayout() {
        assertEquals("#,### steps · #.# km", HuaweiHealth.hideNumbers("8,432 steps · 5.1 km"))
    }

    @Test fun theSampleCarriesNoValueFromHuaweiHealth() {
        val probe = HuaweiProbe()
        probe.addScreen(ScreenCapture(1, "Health 7", listOf(ScreenNode("8,432", "Steps 8432", "com.huawei.health:id/tv_step2", "TextView", 2, clickable = true))))
        probe.addNotification(NotificationCapture(2, "8,432 steps", "Goal 10,000", bigText = "Burned 312 kcal"))
        val sample = probe.seen.sample("0.183.0")
        assertFalse(Regex("""\d""").containsMatchIn(sample.lines().filterNot { it.startsWith("== ") || it.startsWith("PokéLife") }.joinToString("\n").replace("tv_step2", "")), sample)
        assertTrue("tv_step2" in sample, "view ids are layout, not values")
        assertTrue("#,### | Steps ####" in sample)
        assertTrue("steps found: yes" in sample)
        assertEquals(8432, probe.seen.latestSteps)
    }

    @Test fun samePageAndRepeatedNotificationAreKeptOnce() {
        val probe = HuaweiProbe(maxScreens = 2, maxNotifications = 2)
        val page = ScreenCapture(1, "Home", listOf(ScreenNode("Sleep")))
        assertTrue(probe.addScreen(page))
        assertFalse(probe.addScreen(page.copy(atMillis = 5)))
        assertFalse(probe.addScreen(ScreenCapture(6, "Blank", listOf(ScreenNode(viewId = "x")))), "a page with no words is not kept")
        probe.addScreen(ScreenCapture(7, "B", listOf(ScreenNode("Workouts"))))
        probe.addScreen(ScreenCapture(8, "C", listOf(ScreenNode("Weight"))))
        assertEquals(listOf("B", "C"), probe.seen.screens.map { it.title }, "only the newest pages stay")

        probe.addNotification(NotificationCapture(1, "100 steps", ""))
        probe.addNotification(NotificationCapture(2, "100 steps", ""))
        assertEquals(1, probe.seen.notifications.size)
        probe.addNotification(NotificationCapture(3, "120 steps", ""))
        probe.addNotification(NotificationCapture(4, "150 steps", ""))
        assertEquals(listOf(120, 150), probe.seen.notifications.map { it.steps })
        probe.clear()
        assertTrue(probe.seen.isEmpty)
    }

    private fun n(id: String, text: String = "", description: String = "") = ScreenNode(text, description, "com.huawei.health:id/$id")

    /** The Health page as Huawei Health 14 November 2026 lays it out (owner's capture, values made up). */
    private val healthPage = ScreenCapture(1, "MainActivity", listOf(
        n("action_bar_title", "Health"),
        n("tv_steps", "Sleep"), n("data_layout_step_layout", description = "07:12, Get-up time"), n("stepsText", "07:12"), n("hw_health_steps", "Get-up time"),
        n("tv_sport_intensity", "Steps"), n("time_strength_layout_middle", description = "4916 steps"), n("activity_hours", "Mood"),
        n("function_set_items_title", "Exercise records"), n("function_set_items_data", "1.91"), n("function_set_items_unit", "km"), n("theme", "Get Smart Fit Plan"),
        n("function_set_items_title", "Health Clovers"), n("function_set_items_data", "1/7"), n("function_set_items_unit", "Tasks completed"),
        n("function_set_items_title", "Heart"), n("function_set_items_data", "Heart rate 83 bpm"), n("function_set_bottom_left", "11/14 | November 14"),
        n("function_set_items_title", "Weight"), n("function_set_items_data", "54.8"), n("function_set_items_unit", "kg"), n("function_set_bottom_left", "Deficit: 1,085 kcal"),
        n("function_set_items_title", "Sleep"), n("function_set_items_data", "5 h 9 min"), n("theme", "Take a 2-minute break"),
        n("function_set_items_title", "Skin temperature"), n("function_set_items_data", "34.1°C | 34.1 degrees Celsius"),
        n("function_set_items_title", "Cycle Calendar"), n("function_set_items_data", "Period expected to start in 11 days"),
        n("content", "Devices"), n("equipment_name", "HUAWEI WATCH FIT 4 Pro"),
    ))

    @Test fun theHealthPageIsRead() {
        val home = HuaweiHome.read(healthPage)
        assertEquals(HuaweiHome(steps = 4916, sleepMinutes = 309, getUpMinute = 7 * 60 + 12, weight = 54.8, weightUnit = "kg", heartRate = 83, exerciseKm = 1.91), home)
    }

    @Test fun anotherPageReadsAsNothing() {
        assertTrue(HuaweiHome.read(ScreenCapture(1, "Me", listOf(n("content_title", "My data"), n("vip_text", "HUAWEI Health+")))).isEmpty)
        val odd = ScreenCapture(1, "", listOf(n("function_set_items_title", "Sleep"), n("function_set_items_data", "--"), n("function_set_items_title", "Weight"), n("function_set_items_data", "--"), n("function_set_items_unit", "kg")))
        assertTrue(HuaweiHome.read(odd).isEmpty, "a card with no value is missing, not zero")
    }

    @Test fun theNewestPageWinsFieldByField() {
        val later = ScreenCapture(2, "MainActivity", listOf(n("function_set_items_title", "Weight"), n("function_set_items_data", "54.2"), n("function_set_items_unit", "kg")))
        val seen = HuaweiSeen(listOf(healthPage, later))
        assertEquals(54.2, seen.home.weight)
        assertEquals(309, seen.home.sleepMinutes, "a field the newer page lacks keeps the older value")
    }
}
