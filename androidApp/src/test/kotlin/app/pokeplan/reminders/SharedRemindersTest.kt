package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import app.pokeplan.AndroidTestBase
import app.pokeplan.LiveStore
import app.pokeplan.app.PlanReminderAdapter
import app.pokeplan.app.ReminderRequest
import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.widgetStore
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The shared reminder scheduler on Android (REP-033; INTEGRATIONS_AND_PRIVACY.md section 9): the alarms it sets, what an alarm shows, the buttons, quiet
 * hours, a reminder missed while the phone was off, and the boot and time-change receivers. These replace the phone checks of section 9.11.1 for what
 * Robolectric can reach (the alarm manager and the notification shade are its shadows, not a phone's).
 */
@RunWith(RobolectricTestRunner::class)
class SharedRemindersTest : AndroidTestBase() {
    private val title = "Call the dentist"
    private lateinit var zoneBefore: java.util.TimeZone
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val shade get() = context.getSystemService(NotificationManager::class.java)
    private val zone get() = TimeZone.currentSystemDefault()

    @Before fun setUp() {
        zoneBefore = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // No quiet hours unless a test sets them (a start equal to its end is never inside), so the tests do not depend on the hour they run at.
        quietHours(QuietHours(LocalTime(3, 0), LocalTime(3, 0)))
    }

    @After fun restoreZone() = java.util.TimeZone.setDefault(zoneBefore)

    private fun quietHours(q: QuietHours) = widgetStore(context).changeSettings { it.copy(quietHours = q, streakReminder = false, weeklyRecap = false) }

    /** A task with one reminder at the time it is planned for, [at] rounded down to the minute. */
    private fun taskAt(at: LocalDateTime): Task {
        val store = widgetStore(context)
        val task = store.addTask(title)
        store.updateTask(task.copy(planned = at.date, plannedTime = LocalTime(at.hour, at.minute), reminders = listOf(ReminderOffset.AtTime)))
        return store.data.tasks.single { it.id == task.id }
    }

    private fun minutesFromNow(minutes: Long): LocalDateTime = (System.currentTimeMillis() + minutes * 60_000).toLocalDateTime(zone)

    private class Alarm(val atMillis: Long, val type: Int, val intent: Intent)

    /** The alarms of the shared scheduler for [taskId] (the nightly refresh and other features' alarms left out). */
    private fun alarmsFor(taskId: String): List<Alarm> = alarms.scheduledAlarms.mapNotNull { a ->
        val intent = shadowOf(a.operation).savedIntent
        if (intent.action == SharedReminders.ACTION_FIRE && intent.getStringExtra("ref") == taskId) Alarm(a.triggerAtMs, a.type, intent) else null
    }

    private fun notificationFor(taskId: String) = shade.activeNotifications.firstOrNull { it.id == TaskReminders.notificationId(taskId) }

    private fun request(intent: Intent): ReminderRequest = assertNotNull(SharedReminders.requestOf(intent))

    /** Taps the button [label] of the reminder on show for [taskId], the way the shade would send it. */
    private fun tap(taskId: String, label: String) {
        val n = assertNotNull(notificationFor(taskId), "the reminder is on show").notification
        val action = n.actions.firstOrNull { it.title == label } ?: fail("no \"$label\" button: ${n.actions.map { it.title }}")
        val sent = shadowOf(action.actionIntent).savedIntent
        runBlocking { SharedReminders.act(context, request(sent), assertNotNull(SharedReminders.keyOf(sent))) }
    }

    @Test fun eachReminderGetsAnExactWakeUpAlarmThatCarriesNoTitle() {
        val at = minutesFromNow(30)
        val task = taskAt(at)
        SharedReminders.reschedule(context)

        val alarm = alarmsFor(task.id).single()
        assertEquals(LocalDateTime(at.date, LocalTime(at.hour, at.minute)).toEpochMillis(zone), alarm.atMillis)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        val everything = alarm.intent.toUri(Intent.URI_INTENT_SCHEME) + alarm.intent.extras?.keySet()?.joinToString { "$it=${alarm.intent.extras?.get(it)}" }
        assertFalse("dentist" in everything, "an alarm holds only the opaque request, never the title: $everything")
    }

    @Test fun theAlarmShowsTheReminderAndDoneCompletesTheTask() {
        val task = taskAt(minutesFromNow(30))
        SharedReminders.reschedule(context)
        SharedReminders.fire(context, request(alarmsFor(task.id).single().intent))

        val shown = assertNotNull(notificationFor(task.id)).notification
        assertEquals(title, shown.extras.getString("android.title"))
        assertEquals(listOf("Done", "10 min", "1 hour"), shown.actions.map { it.title.toString() })

        tap(task.id, "Done")
        assertEquals(TaskState.DONE, widgetStore(context).data.tasks.single { it.id == task.id }.state)
        assertEquals(null, notificationFor(task.id), "the reminder leaves the shade")
        assertTrue(alarmsFor(task.id).isEmpty(), "a done task has no alarm left")
    }

    @Test fun tenMinutesPutsItAwayAndBringsItBackTenMinutesLater() {
        val task = taskAt(minutesFromNow(30))
        SharedReminders.reschedule(context)
        SharedReminders.fire(context, request(alarmsFor(task.id).single().intent))
        val before = System.currentTimeMillis()
        tap(task.id, "10 min")

        assertEquals(null, notificationFor(task.id))
        val held = LiveStore.get(context)!!.reminders.all().single { it.sourceRef == task.id }
        assertTrue(held.atMillis - before in (10 * 60_000L - 1_000)..(10 * 60_000L + 5_000), "held for ten minutes: ${held.atMillis - before}")
        assertTrue(alarmsFor(task.id).any { it.atMillis == held.atMillis }, "and its alarm is set")
        assertEquals(TaskState.OPEN, widgetStore(context).data.tasks.single { it.id == task.id }.state)
    }

    @Test fun inQuietHoursTheAlarmShowsNothingAndWaitsForTheirEnd() {
        val task = taskAt(minutesFromNow(30))
        SharedReminders.reschedule(context)
        val alarm = alarmsFor(task.id).single()
        val hour = minutesFromNow(0).hour
        val quiet = QuietHours(LocalTime((hour + 23) % 24, 0), LocalTime((hour + 2) % 24, 0))
        quietHours(quiet)

        val now = System.currentTimeMillis()
        SharedReminders.fire(context, request(alarm.intent))
        assertEquals(null, notificationFor(task.id), "nothing is shown in quiet hours")
        val end = Reminders.outOfQuietHours(now.toLocalDateTime(zone), quiet).toEpochMillis(zone)
        assertEquals(end, LiveStore.get(context)!!.reminders.all().single { it.sourceRef == task.id }.atMillis, "held until the quiet hours end")
        assertTrue(alarmsFor(task.id).any { it.atMillis == end })
    }

    @Test fun aReminderThatCameDueWhileThePhoneWasOffShowsOnceWhenItIsBack() {
        val task = taskAt(minutesFromNow(-60))
        // The last plan was made two hours ago; the phone was off since then.
        context.getSharedPreferences("shared_reminders", Context.MODE_PRIVATE).edit().putLong("last_planned", System.currentTimeMillis() - 2 * 3_600_000L).commit()
        val now = System.currentTimeMillis()
        SharedReminders.reschedule(context)

        val owed = alarmsFor(task.id).single()
        assertTrue(owed.atMillis - now in 0..30_000L, "it goes off at once: ${owed.atMillis - now} ms")
        SharedReminders.fire(context, request(owed.intent))
        assertNotNull(notificationFor(task.id), "the missed reminder is shown")
        SharedReminders.reschedule(context)
        // What is left is the follow-up that repeats an unanswered reminder (Settings: repeat reminders), not the missed one again.
        assertEquals(listOf(PlanReminderAdapter.KIND_REPEAT), alarmsFor(task.id).map { request(it.intent).kind }, "the missed one is shown only once")
    }

    @Test fun afterARestartTheBootReceiverSetsTheAlarmsAgain() {
        val task = taskAt(minutesFromNow(45))
        assertTrue(alarmsFor(task.id).isEmpty())
        broadcast(Intent.ACTION_BOOT_COMPLETED)
        awaitAlarm(task.id)
    }

    @Test fun aTimeZoneChangeMovesTheAlarmToTheSameWallClockTime() {
        val tomorrow = minutesFromNow(0).date.plus(1, DateTimeUnit.DAY)
        val task = taskAt(LocalDateTime(tomorrow, LocalTime(9, 0)))
        SharedReminders.reschedule(context)
        assertEquals(LocalDateTime(tomorrow, LocalTime(9, 0)).toEpochMillis(TimeZone.UTC), alarmsFor(task.id).single().atMillis)

        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Manila"))
        broadcast(Intent.ACTION_TIMEZONE_CHANGED)
        val manila = LocalDateTime(tomorrow, LocalTime(9, 0)).toEpochMillis(TimeZone.of("Asia/Manila"))
        awaitAlarm(task.id) { it.atMillis == manila }
    }

    @Test fun theAlarmReceiverShowsTheReminder() {
        val task = taskAt(minutesFromNow(30))
        SharedReminders.reschedule(context)
        context.sendBroadcast(alarmsFor(task.id).single().intent)
        awaitUntil("the reminder is shown") { shadowOf(Looper.getMainLooper()).idle(); notificationFor(task.id) != null }
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setClass(context, ReminderBootReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitAlarm(taskId: String, ok: (Alarm) -> Boolean = { true }) =
        awaitUntil("an alarm for the task") { alarmsFor(taskId).any(ok) }

    /** The receivers do their work off the main thread (`goAsync`); this waits for it, up to ten seconds. */
    private fun awaitUntil(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) {
            if (done()) return
            Thread.sleep(50)
        }
        fail("timed out waiting: $what")
    }
}
