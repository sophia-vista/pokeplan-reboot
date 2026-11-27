package app.pokeplan.app.integrations

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** What the calendar event panel edits (PLAN 8.1B), in the player's own terms. For an all-day event [last] is the last day (inclusive). */
data class ProviderFields(
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val day: LocalDate,
    val last: LocalDate,
    val start: LocalTime,
    val minutes: Int,
)

/**
 * Edits to an event PLAN does not hold (an invite, a COUNT repeat, a series with edited occurrences, any event that was not
 * brought in): read the fields the panel shows out of the provider's row, and write them back changing nothing else. The repeat
 * rule, its skipped days and the colour are kept exactly as the provider has them, so a rule PLAN cannot express survives an edit.
 */
object ProviderEdit {
    private val UTC = TimeZone.UTC
    private const val MINUTE = 60_000L

    fun repeats(p: ProviderEvent): Boolean = !p.rrule.isNullOrBlank()

    /** The fields of [p] as the player sees them: timed events in [zone], all-day events as whole days. Null if the row has no usable length. */
    fun fields(p: ProviderEvent, zone: TimeZone): ProviderFields? {
        val base = Instant.fromEpochMilliseconds(p.startMillis)
        if (p.allDay) {
            val day = base.toLocalDateTime(UTC).date
            val last = when {
                p.endMillis != null -> Instant.fromEpochMilliseconds(p.endMillis).toLocalDateTime(UTC).date.minus(DatePeriod(days = 1))
                p.duration != null -> day.plus(DatePeriod(days = (CalendarEventMapping.daysOf(p.duration) ?: 1) - 1))
                else -> day
            }
            return ProviderFields(p.title, p.description.orEmpty(), p.location.orEmpty(), true, day, maxOf(day, last), LocalTime(0, 0), 24 * 60)
        }
        val minutes = when {
            p.endMillis != null -> ((p.endMillis - p.startMillis) / MINUTE).toInt()
            p.duration != null -> CalendarEventMapping.minutesOf(p.duration) ?: return null
            else -> return null
        }.takeIf { it > 0 } ?: return null
        val local = base.toLocalDateTime(zone)
        return ProviderFields(p.title, p.description.orEmpty(), p.location.orEmpty(), false, local.date, local.date, LocalTime(local.hour, local.minute), minutes)
    }

    /**
     * The whole series changed: title, words, time of day and length move; the series keeps its start day, its repeat rule, its
     * skipped days and its colour. (Moving the first day is a day change on the first occurrence, done by [series] with a new [ProviderFields.day].)
     */
    fun series(old: ProviderEvent, f: ProviderFields, zone: TimeZone): ProviderEvent {
        val repeating = repeats(old)
        return if (f.allDay) {
            val start = f.day.atStartOfDayIn(UTC).toEpochMilliseconds()
            val days = f.day.until(f.last) + 1
            old.copy(
                title = f.title.trim().ifEmpty { old.title }, description = f.description.trim().ifEmpty { null }, location = f.location.trim().ifEmpty { null },
                allDay = true, startMillis = start, timezone = UTC.id,
                endMillis = if (repeating) null else f.last.plus(DatePeriod(days = 1)).atStartOfDayIn(UTC).toEpochMilliseconds(),
                duration = if (repeating) "P${days}D" else null,
            )
        } else {
            val start = LocalDateTime(f.day, f.start).toInstant(zone).toEpochMilliseconds()
            old.copy(
                title = f.title.trim().ifEmpty { old.title }, description = f.description.trim().ifEmpty { null }, location = f.location.trim().ifEmpty { null },
                allDay = false, startMillis = start, timezone = zone.id,
                endMillis = if (repeating) null else start + f.minutes * MINUTE,
                duration = if (repeating) CalendarEventMapping.durationText(f.minutes) else null,
            )
        }
    }

    /** One occurrence's own fields as an exception row: no repeat, a plain start and end. */
    fun occurrence(old: ProviderEvent, f: ProviderFields, zone: TimeZone): ProviderEvent {
        val single = series(old.copy(rrule = null, exdate = null), f, zone)
        return single.copy(rrule = null, exdate = null, duration = null)
    }

    /** The first day of the series in [zone] (all-day: UTC), which a series edit keeps. */
    fun seriesDay(p: ProviderEvent, zone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(p.startMillis).toLocalDateTime(if (p.allDay) UTC else zone).date

    private fun LocalDate.until(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()
}
