package app.pokeplan.plan

import app.pokeplan.core.dex.Region
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Everything the app saves must survive a trip through JSON unchanged. */
class SerializationTest {
    private val json = Json { encodeDefaults = true }

    private inline fun <reified T> roundTrip(value: T) {
        val text = json.encodeToString(serializer<T>(), value)
        assertEquals(value, json.decodeFromString(serializer<T>(), text), text)
    }

    @Test
    fun tasksAndSubtasks() = roundTrip(
        Task(
            id = "t1", title = "Write report", notes = "draft", rank = Rank.B, listId = "l1",
            planned = LocalDate(2026, 10, 19), plannedTime = LocalTime(9, 30), due = LocalDateTime(2026, 10, 20, 17, 0),
            estimateMinutes = 45, seriesId = "s1", subtasks = listOf(Subtask("a", "outline", true, 1L, true)),
            state = TaskState.DONE, createdAtMillis = 1L, completedAtMillis = 2L, focusedMinutes = 25, paid = true,
        ),
    )

    @Test
    fun recurrences() {
        val start = LocalDate(2026, 1, 1)
        roundTrip(Recurrence(Repeat.Daily(2), start))
        roundTrip(Recurrence(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), start, RepeatMode.AFTER_COMPLETION, RepeatEnd.After(10)))
        roundTrip(Recurrence(Repeat.Monthly(1, MonthlyRule.NthWeekday(2, DayOfWeek.TUESDAY)), start, end = RepeatEnd.On(LocalDate(2027, 1, 1)), skipped = setOf(start)))
        roundTrip(Recurrence(Repeat.Monthly(3, MonthlyRule.LastDay), start))
        roundTrip(Recurrence(Repeat.Yearly(), start, end = RepeatEnd.Never))
    }

    @Test
    fun habits() {
        val on = LocalDate(2026, 10, 1)
        listOf<HabitTarget>(HabitTarget.Daily, HabitTarget.Weekdays(setOf(DayOfWeek.SATURDAY)), HabitTarget.PerWeek(3), HabitTarget.PerMonth(8))
            .forEach { roundTrip(Habit("h", "Stretch", Rank.D, it, on, setOf(on))) }
    }

    @Test
    fun goals() = roundTrip(
        Goal("g", "Learn guitar", LocalDate(2027, 1, 1), listOf(Milestone("m", "First chord", 1L, 2L, true)), setOf("t1"), setOf("h"), "l", 1L, null, false),
    )

    @Test
    fun reviewsAndSettings() {
        roundTrip(WeeklyReview(LocalDate(2026, 10, 12), 5L, "What helped?", null, listOf("t1"), true))
        roundTrip(QuietHours())
        roundTrip<ReminderOffset>(ReminderOffset.Before(15))
        roundTrip<ReminderOffset>(ReminderOffset.AtTime)
    }

    @Test
    fun focusSessions() {
        listOf<Phase>(Phase.Running, Phase.Away(1), Phase.Paused(PauseReason.CALL, 2), Phase.Completed(3), Phase.Failed(FailReason.MOVED_PHONE, 4))
            .forEach { roundTrip(FocusSession("f", 0, 60_000, true, Region.KANTO, "p1", "t1", null, it, 5, 1)) }
    }
}
