package app.pokeplan.widgets

import android.app.AlarmManager
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.GlanceId
import app.pokeplan.AndroidTestBase
import app.pokeplan.plan.TaskState
import app.pokeplan.reminders.AppAlerts
import app.pokeplan.widgetStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WidgetSaveTest : AndroidTestBase() {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun tickOnTheWidget(taskId: String) = runBlocking {
        CompleteTaskAction().onAction(context, object : GlanceId {}, actionParametersOf(ActionParameters.Key<String>("taskId") to taskId))
    }

    @Test fun aTickOnTheWidgetIsSeenByTheAppAndNotWrittenOver() {
        val app = widgetStore(context)
        val first = app.addTask("Ticked on the widget")
        tickOnTheWidget(first.id)

        val second = app.addTask("Added in the app afterwards")
        app.reload()
        assertEquals(TaskState.DONE, app.data.tasks.single { it.id == first.id }.state)
        assertTrue(app.data.pending.any { it.done.id == first.id }, "the reward waits for the app, as the widget leaves it")

        processDeath()
        val after = widgetStore(context).data.tasks
        assertEquals(TaskState.DONE, after.single { it.id == first.id }.state, "the widget's tick is on the disk")
        assertNotNull(after.singleOrNull { it.id == second.id }, "and so is the app's later change")
    }

    @Test fun aFocusSessionSurvivesAProcessDeathAndItsAlarmIsSetAgain() {
        val session = widgetStore(context).startFocus(25, deepFocus = false)
        AppAlerts.rescheduleFromSave(context)
        val end = session.startedAtMillis + 25 * 60_000L
        assertTrue(end in focusEndAlarms(), "the end alarm is set: ${focusEndAlarms()}")

        processDeath()
        shadowOf(alarmManager).scheduledAlarms.toList().forEach { a -> a.operation?.let { alarmManager.cancel(it) } }
        val back = assertNotNull(widgetStore(context).data.focus, "the session is still running after the process died")
        assertEquals(session.id, back.id)
        assertEquals(session.startedAtMillis, back.startedAtMillis)
        AppAlerts.rescheduleFromSave(context)
        assertEquals(listOf(end), focusEndAlarms(), "and its end alarm is set again from the save")
    }

    private fun focusEndAlarms(): List<Long> = shadowOf(alarmManager).scheduledAlarms
        .filter { shadowOf(it.operation).savedIntent.getStringExtra(AppAlerts.EXTRA_KIND) == AppAlerts.Kind.END.name }
        .map { it.triggerAtMs }
}
