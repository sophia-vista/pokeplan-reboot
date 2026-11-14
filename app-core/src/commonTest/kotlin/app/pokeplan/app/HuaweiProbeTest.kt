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
}
