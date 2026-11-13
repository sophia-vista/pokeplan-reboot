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

/**
 * The widgets share the app's one save (REP-033): a tick on the Today widget is the app's own completion, an app store that had not seen it does not write
 * over it, and a focus session survives a process death with its alarms set again from the save.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSaveTest : AndroidTestBase() {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** A tap on [taskId]'s tick in the Today widget (the action reads only the task id, not which widget it came from). */
    private fun tickOnTheWidget(taskId: String) = runBlocking {
        CompleteTaskAction().onAction(context, object : GlanceId {}, actionParametersOf(ActionParameters.Key<String>("taskId") to taskId))
    }

    @Test fun aTickOnTheWidgetIsSeenByTheAppAndNotWrittenOver() {
        val app = widgetStore(context)
        val first = app.addTask("Ticked on the widget")
        tickOnTheWidget(first.id)

        // The app had not reloaded yet; its next change must not put the task back as open.
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
        // The process died and took nothing with it but memory; the alarms are cleared too, as an update or a force stop does.
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
