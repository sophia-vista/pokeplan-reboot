package app.pokeplan.app.reminders

import app.pokeplan.core.dex.Region
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusWindowTest {
    private fun session(phase: Phase, paused: Long = 0) =
        FocusSession("s", startedAtMillis = 1_000, durationMillis = 25 * 60_000, deepFocus = false, region = Region.PLAN, leadIdAtStart = null, phase = phase, pausedMillis = paused)

    @Test fun noSessionMeansNoWindow() = assertNull(ReminderPolicy.focusWindow(null))

    @Test fun aRunningSessionHoldsItsLengthPlusPauses() =
        assertEquals(FocusWindow(1_000, 1_000 + 25 * 60_000 + 30_000), ReminderPolicy.focusWindow(session(Phase.Running, paused = 30_000)))

    @Test fun aFinishedSessionHoldsNothing() = assertNull(ReminderPolicy.focusWindow(session(Phase.Completed(at = 5_000))))
}
