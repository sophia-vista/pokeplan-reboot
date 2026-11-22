package app.pokeplan.plan

import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.PokemonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusSessionTest {
    private val minute = 60_000L
    private val second = 1_000L
    private fun start(minutes: Int = 25, deep: Boolean = false, used: Int = 0, lure: PokemonType? = null) =
        FocusRules.start("s", minutes, 0, deep, Region.KANTO, "buddy", taskId = "task", lure = lure, emergencyPausesUsedToday = used)

    @Test
    fun aQuietSessionCompletes() {
        val s = start().tick(24 * minute)
        assertIs<Phase.Running>(s.phase)
        assertEquals(minute, s.remainingMillis(24 * minute))
        val done = s.tick(25 * minute)
        assertEquals(Phase.Completed(25 * minute), done.phase)
        assertTrue(done.isFinished)
    }

    @Test
    fun returningWithinTheGracePeriodKeepsTheSession() {
        val s = start().apply(FocusEvent.Left, 5 * minute).apply(FocusEvent.Returned, 5 * minute + 9 * second)
        assertIs<Phase.Running>(s.phase)
        assertEquals(5 * minute + 9 * second, s.focusedMillis(5 * minute + 9 * second), "time in grace still counts")
    }

    @Test
    fun aTimerThatRunsOutInsideTheGracePeriodIsAFinishedSession() {
        val left = start().apply(FocusEvent.Left, 25 * minute - 5 * second)
        assertEquals(Phase.Completed(25 * minute), left.tick(40 * minute).phase)
        assertEquals(Phase.Completed(25 * minute), left.apply(FocusEvent.Returned, 40 * minute).phase)
        val early = start().apply(FocusEvent.Left, 25 * minute - 11 * second)
        assertEquals(Phase.Failed(FailReason.LEFT_APP, 25 * minute - 1 * second), early.tick(40 * minute).phase)
    }

    @Test
    fun stayingAwayPastTenSecondsFailsTheEgg() {
        val s = start().apply(FocusEvent.Left, 5 * minute)
        val failed = s.tick(5 * minute + 10 * second + 1)
        assertEquals(Phase.Failed(FailReason.LEFT_APP, 5 * minute + 10 * second), failed.phase)
        assertEquals(failed.phase, start().apply(FocusEvent.Left, 5 * minute).apply(FocusEvent.Returned, 5 * minute + 11 * second).phase)
    }

    @Test
    fun lockingTheScreenIsNotLeaving() {
        val s = start().apply(FocusEvent.ScreenLocked, 5 * minute)
        assertIs<Phase.Running>(s.phase)
        assertEquals(Phase.Completed(25 * minute), s.tick(26 * minute).phase)
        val late = start().apply(FocusEvent.Left, 5 * minute).apply(FocusEvent.ScreenLocked, 5 * minute + 200)
        assertIs<Phase.Running>(late.tick(10 * minute).phase)
        assertEquals(10 * minute, late.focusedMillis(10 * minute), "time with the screen off counts")
    }

    @Test
    fun aPhoneCallPausesTheTimer() {
        val s = start().apply(FocusEvent.CallStarted, 10 * minute)
        assertIs<Phase.Paused>(s.phase)
        assertEquals(10 * minute, s.focusedMillis(30 * minute), "the clock is stopped during the call")
        assertIs<Phase.Paused>(s.tick(40 * minute).phase, "a call can be as long as it likes")
        val back = s.apply(FocusEvent.CallEnded, 30 * minute)
        assertIs<Phase.Running>(back.phase)
        assertEquals(20 * minute, back.pausedMillis)
        assertEquals(Phase.Completed(45 * minute), back.tick(45 * minute).phase, "15 minutes were left")
    }

    @Test
    fun aCallDuringTheGraceWindowRescuesTheSession() {
        val s = start().apply(FocusEvent.Left, 5 * minute).apply(FocusEvent.CallStarted, 5 * minute + 3 * second)
        assertIs<Phase.Paused>(s.phase)
    }

    @Test
    fun twoEmergencyPausesAreAllowedPerDay() {
        var s = start()
        s = s.apply(FocusEvent.EmergencyPauseStarted, 2 * minute)
        assertEquals(1, s.emergencyPausesUsed)
        s = s.apply(FocusEvent.EmergencyPauseEnded, 4 * minute)
        assertIs<Phase.Running>(s.phase)
        assertEquals(2 * minute, s.pausedMillis)
        s = s.apply(FocusEvent.EmergencyPauseStarted, 10 * minute)
        s = s.apply(FocusEvent.EmergencyPauseEnded, 11 * minute)
        assertEquals(2, s.emergencyPausesUsed)
        val third = s.apply(FocusEvent.EmergencyPauseStarted, 12 * minute)
        assertIs<Phase.Running>(third.phase, "no third pause")
        assertEquals(2, third.emergencyPausesUsed)
        val alreadyUsed = start(used = 2).apply(FocusEvent.EmergencyPauseStarted, minute)
        assertIs<Phase.Running>(alreadyUsed.phase, "the day's pauses can be used in earlier sessions")
    }

    @Test
    fun anEmergencyPauseLongerThanThreeMinutesBecomesTimeAway() {
        val s = start().apply(FocusEvent.EmergencyPauseStarted, 2 * minute)
        val later = s.tick(5 * minute + 20 * second)
        assertEquals(Phase.Failed(FailReason.LEFT_APP, 5 * minute + 10 * second), later.phase)
        val returned = s.tick(5 * minute + 5 * second)
        assertEquals(Phase.Away(5 * minute), returned.phase, "the pause ended after 3 minutes; now the 10 second grace runs")
        assertEquals(3 * minute, returned.pausedMillis)
    }

    @Test
    fun movingThePhoneFailsOnlyDeepFocusSessions() {
        assertEquals(Phase.Failed(FailReason.MOVED_PHONE, 3 * minute), start(deep = true).apply(FocusEvent.Moved, 3 * minute).phase)
        assertIs<Phase.Running>(start(deep = false).apply(FocusEvent.Moved, 3 * minute).phase)
    }

    @Test
    fun givingUpEndsTheSession() {
        assertEquals(Phase.Failed(FailReason.GAVE_UP, 2 * minute), start().apply(FocusEvent.GaveUp, 2 * minute).phase)
    }

    @Test
    fun finishedSessionsIgnoreFurtherEvents() {
        val failed = start().apply(FocusEvent.GaveUp, minute)
        assertEquals(failed, failed.apply(FocusEvent.Returned, 2 * minute))
        assertEquals(failed, failed.tick(100 * minute))
    }

    @Test
    fun halfwayNotificationOnlyForLongerSessions() {
        assertEquals(12 * minute + 30 * second, start(25).halfwayAtMillis)
        assertNull(start(10).halfwayAtMillis)
        assertEquals(7 * minute + 30 * second, FocusRules.start("x", 15, 0, false, Region.PLAN, null).halfwayAtMillis)
    }

    @Test
    fun aCompletedSessionBecomesAnEffortEventWithTheLockedInChoices() {
        val s = start(25, deep = true, lure = PokemonType.WATER).tick(25 * minute)
        val e = s.toEffortEvent("egg-1", 1_000, 7)
        assertEquals(25, e.minutes)
        assertTrue(e.deepFocus)
        assertEquals(PokemonType.WATER, e.lure)
        assertEquals(Region.KANTO, e.region)
        assertEquals("buddy", s.leadIdAtStart)
        assertFailsWith<IllegalStateException> { start().toEffortEvent("x", 0, 0) }
    }

    @Test
    fun theNoBlameRetryIsFiveMinutes() {
        assertEquals(5, FocusRules.RETRY_MINUTES)
        assertEquals(FocusDuration(0, 5, 0), FocusRules.justFiveMinutes())
        assertEquals("That egg went cold. Try again?", FocusRules.FAILED_MESSAGE)
        assertFailsWith<IllegalArgumentException> { FocusRules.start("x", 4, 0, false, Region.PLAN, null) }
        assertFalse(FocusRules.justFiveMinutes().eggMinutes < 5)
    }
}
