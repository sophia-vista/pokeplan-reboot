package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakAndHabitTest {
    private val today = LocalDate(2026, 10, 21)
    private fun ago(n: Int) = today.plusDays(-n)
    private fun days(vararg ago: Int) = ago.map { ago(it) }.toSet()

    @Test
    fun consecutiveDaysCount() {
        assertEquals(5, Streaks.daily(days(0, 1, 2, 3, 4), emptySet(), today).current)
    }

    @Test
    fun todayDoesNotBreakTheStreakUntilItIsOver() {
        assertEquals(4, Streaks.daily(days(1, 2, 3, 4), emptySet(), today).current)
    }

    @Test
    fun oneMissedDayAWeekIsForgiven() {
        val active = days(0, 1, 3, 4)
        assertEquals(4, Streaks.daily(active, emptySet(), today).current)
    }

    @Test
    fun twoMissesInTheSameWeekEndTheStreak() {
        val active = days(0, 3, 4, 5)
        assertEquals(1, Streaks.daily(active, emptySet(), today).current)
    }

    @Test
    fun theStreakKeeperPerkForgivesAnotherDay() {
        val active = days(0, 3, 4, 5)
        assertEquals(4, Streaks.daily(active, emptySet(), today, extraFreeMisses = 1).current)
    }

    @Test
    fun restDaysAreNeutral() {
        val active = days(0, 3, 4)
        val rest = days(1, 2)
        assertEquals(3, Streaks.daily(active, rest, today).current, "rest days neither count nor break")
    }

    @Test
    fun longestStreakIsKept() {
        val active = days(0, 10, 11, 12, 13, 14, 15)
        val result = Streaks.daily(active, emptySet(), today)
        assertEquals(1, result.current)
        assertEquals(6, result.longest)
        assertEquals(Streaks.Result(0, 0), Streaks.daily(emptySet(), emptySet(), today))
    }

    @Test
    fun theWeeklyRingCountsActiveDaysThisWeek() {
        val active = days(0, 1, 3, 5)
        assertEquals(2, Streaks.activeDaysThisWeek(active, today), "a Monday week starts on the 19th")
        assertEquals(3, Streaks.activeDaysThisWeek(active, today, DayOfWeek.SUNDAY), "a Sunday week starts on the 18th")
    }

    @Test
    fun restDaysAreCappedPerMonth() {
        val fourteen = (1..14).map { LocalDate(2026, 10, it) }.toSet()
        assertTrue(Streaks.canAddRestDays(emptySet(), fourteen))
        assertFalse(Streaks.canAddRestDays(fourteen, setOf(LocalDate(2026, 10, 15))))
        assertTrue(Streaks.canAddRestDays(fourteen, setOf(LocalDate(2026, 11, 1))))
    }

    @Test
    fun welcomeBackAfterAWeekAway() {
        assertTrue(Streaks.welcomeBack(ago(7), today))
        assertFalse(Streaks.welcomeBack(ago(6), today))
        assertFalse(Streaks.welcomeBack(null, today))
    }

    private fun habit(target: HabitTarget, completions: Set<LocalDate>, created: LocalDate = ago(100)) =
        Habit(id = "h", title = "Read", target = target, createdOn = created, completions = completions)

    @Test
    fun habitsPayOncePerDay() {
        val h = habit(HabitTarget.Daily, emptySet())
        assertTrue(h.paysOn(today))
        val done = h.complete(today)
        assertFalse(done.paysOn(today))
        assertTrue(done.undo(today).paysOn(today), "an unpaid completion undone can still pay")
        val paid = h.complete(today).paid(today)
        assertFalse(paid.undo(today).paysOn(today), "a paid day never pays again after an undo")
        assertTrue(paid.paysOn(today.plusDays(1)))
    }

    @Test
    fun aDailyHabitUsesTheForgivingStreak() {
        assertEquals(4, habit(HabitTarget.Daily, days(0, 1, 3, 4)).streak(today))
        assertEquals(1, habit(HabitTarget.Daily, days(0, 3, 4, 5)).streak(today))
    }

    @Test
    fun aHabitOnChosenWeekdaysIgnoresOtherDays() {
        val mwf = HabitTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(4, habit(mwf, days(0, 2, 5, 7)).streak(today))
        assertTrue(habit(mwf, emptySet()).isScheduledOn(LocalDate(2026, 10, 19)))
        assertFalse(habit(mwf, emptySet()).isScheduledOn(LocalDate(2026, 10, 20)))
    }

    @Test
    fun aPerWeekHabitCountsWeeksAndForgivesOneShort() {
        val last = (0..2).map { LocalDate(2026, 10, 12 + it) }.toSet()
        val before = (0..1).map { LocalDate(2026, 10, 5 + it) }.toSet()
        assertEquals(2, habit(HabitTarget.PerWeek(3), last + before).streak(today))
        assertEquals(1, habit(HabitTarget.PerWeek(3), last).streak(today))
    }

    @Test
    fun aPerMonthHabitCountsMonths() {
        val september = (1..2).map { LocalDate(2026, 9, it) }.toSet()
        val august = setOf(LocalDate(2026, 8, 5))
        assertEquals(2, habit(HabitTarget.PerMonth(2), september + august, created = LocalDate(2026, 8, 1)).streak(today))
    }

    @Test
    fun habitTargetsAreValidated() {
        assertFailsWith<IllegalArgumentException> { habit(HabitTarget.Weekdays(emptySet()), emptySet()) }
        assertFailsWith<IllegalArgumentException> { habit(HabitTarget.PerWeek(8), emptySet()) }
        assertFailsWith<IllegalArgumentException> { habit(HabitTarget.PerMonth(0), emptySet()) }
    }
}
