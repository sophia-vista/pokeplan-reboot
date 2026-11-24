package app.pokeplan.app.integrations

import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
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

/**
 * One event as the phone's Calendar Provider holds it, with no PLAN knowledge: the Android adapter writes and reads exactly
 * these fields. A repeating event carries [duration] and no [endMillis], as the provider requires.
 */
data class ProviderEvent(
    val title: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long?,
    val duration: String?,
    val timezone: String,
    val rrule: String?,
    val exdate: String?,
    /** Opaque ARGB colour as the provider stores it; only ever written, never read back into PLAN. */
    val color: Int? = null,
)

/**
 * PLAN event <-> Calendar Provider event, for the two-way sync. Only what PLAN keeps as an event syncs (title, day, time, length,
 * the days of an all-day event, one of six repeats, its end and skipped days); companion, people and rewards never leave
 * PokéLife. Anything the provider holds that PLAN cannot show reads back as null, and the sync then keeps PLAN's version.
 */
object CalendarEventMapping {
    private val UTC = TimeZone.UTC

    fun toProvider(e: PlanEvent, zone: TimeZone): ProviderEvent {
        val recurring = e.repeat != null
        val color = e.color?.let { (it or 0xFF000000L).toInt() }
        val rrule = e.repeat?.let { rrule(it, e.until, e.allDay, zone) }
        val exdate = e.skipped.sorted().takeIf { recurring && it.isNotEmpty() }?.joinToString(",") { exdateOf(it, e, zone) }
        if (e.allDay) {
            val start = e.day.atStartOfDayIn(UTC).toEpochMilliseconds()
            val end = e.endDay.plus(DatePeriod(days = 1)).atStartOfDayIn(UTC).toEpochMilliseconds()
            return ProviderEvent(e.title.trim(), true, start, if (recurring) null else end, if (recurring) "P${e.spanDays + 1}D" else null, UTC.id, rrule, exdate, color)
        }
        val start = LocalDateTime(e.day, e.start).toInstant(zone).toEpochMilliseconds()
        val end = start + e.minutes * 60_000L
        return ProviderEvent(e.title.trim(), false, start, if (recurring) null else end, if (recurring) durationOf(e.minutes) else null, zone.id, rrule, exdate, color)
    }

    /** The PLAN fields of a provider event, or null when it uses something PLAN cannot show (a count, a weekday set, a bad span). */
    fun fromProvider(p: ProviderEvent, zone: TimeZone, id: String = ""): PlanEvent? {
        val title = p.title.trim().ifEmpty { "Event" }
        val parsed = p.rrule?.takeIf { it.isNotBlank() }?.let { repeatOf(it) ?: return null }
        val repeat = parsed?.first
        val untilText = parsed?.second
        val skipped = parseExdate(p.exdate, p.allDay, zone)
        return if (p.allDay) {
            val day = Instant.fromEpochMilliseconds(p.startMillis).toLocalDateTime(UTC).date
            val last = when {
                p.endMillis != null -> Instant.fromEpochMilliseconds(p.endMillis).toLocalDateTime(UTC).date.minus(DatePeriod(days = 1))
                p.duration != null -> day.plus(DatePeriod(days = (daysOf(p.duration) ?: return null) - 1))
                else -> day
            }
            if (last < day) return null
            val until = untilText?.let { parseUntil(it, true, zone) }
            PlanEvent(id, title, day, LocalTime(0, 0), allDay = true, lastDay = last.takeIf { it > day }, repeat = repeat, until = until?.let { maxOf(it, day) }, skipped = skipped)
        } else {
            val begin = Instant.fromEpochMilliseconds(p.startMillis).toLocalDateTime(zone)
            val minutes = when {
                p.endMillis != null -> ((p.endMillis - p.startMillis) / 60_000L).toInt()
                p.duration != null -> minutesOf(p.duration) ?: return null
                else -> return null
            }
            if (minutes <= 0) return null
            val start = LocalTime(begin.hour, begin.minute)
            val until = untilText?.let { parseUntil(it, false, zone) }
            PlanEvent(id, title, begin.date, start, PlanEvent.fit(start.hour * 60 + start.minute, minutes), repeat = repeat, until = until?.let { maxOf(it, begin.date) }, skipped = skipped)
        }
    }

    /** What makes two events the same as far as the sync is concerned; colour is left out on purpose (it only goes one way). */
    fun fingerprint(e: PlanEvent): String = listOf(
        e.title.trim(), e.day, e.allDay, if (e.allDay) "" else e.start, if (e.allDay) 0 else e.minutes, e.endDay,
        e.repeat?.name.orEmpty(), if (e.repeat == null) "" else e.until ?: "", if (e.repeat == null) "" else e.skipped.sorted().joinToString(","),
    ).joinToString("|")

    // The rules the six repeats map to, and back. Anything else (COUNT, BYSETPOS, a weekday set) is not understood.
    private val WEEKDAYS = setOf("MO", "TU", "WE", "TH", "FR")

    private fun rrule(repeat: EventRepeat, until: LocalDate?, allDay: Boolean, zone: TimeZone): String {
        val base = when (repeat) {
            EventRepeat.DAILY -> "FREQ=DAILY"
            EventRepeat.WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            EventRepeat.WEEKLY -> "FREQ=WEEKLY"
            EventRepeat.FORTNIGHTLY -> "FREQ=WEEKLY;INTERVAL=2"
            EventRepeat.MONTHLY -> "FREQ=MONTHLY"
            EventRepeat.YEARLY -> "FREQ=YEARLY"
        }
        if (until == null) return base
        return base + ";UNTIL=" + if (allDay) dateText(until) else utcText(LocalDateTime(until, LocalTime(23, 59, 59)).toInstant(zone))
    }

    /** The repeat and the UNTIL text of an RRULE, or null if it is not one of ours. */
    private fun repeatOf(rule: String): Pair<EventRepeat, String?>? {
        val parts = rule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        if (parts.keys.any { it !in setOf("FREQ", "INTERVAL", "BYDAY", "UNTIL", "WKST") }) return null
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val days = parts["BYDAY"]?.split(',')?.toSet()
        val repeat = when (parts["FREQ"]) {
            "DAILY" -> if (interval == 1 && days == null) EventRepeat.DAILY else return null
            "WEEKLY" -> when {
                days == WEEKDAYS && interval == 1 -> EventRepeat.WEEKDAYS
                days != null -> return null
                interval == 1 -> EventRepeat.WEEKLY
                interval == 2 -> EventRepeat.FORTNIGHTLY
                else -> return null
            }
            "MONTHLY" -> if (interval == 1 && days == null) EventRepeat.MONTHLY else return null
            "YEARLY" -> if (interval == 1 && days == null) EventRepeat.YEARLY else return null
            else -> return null
        }
        return repeat to parts["UNTIL"]?.takeIf { it.isNotBlank() }
    }

    private fun parseUntil(text: String, allDay: Boolean, zone: TimeZone): LocalDate? {
        val date = dateOf(text) ?: return null
        if (allDay || !text.endsWith("Z") || text.length < 16) return date
        val h = text.substring(9, 11).toIntOrNull() ?: return date
        val m = text.substring(11, 13).toIntOrNull() ?: return date
        val s = text.substring(13, 15).toIntOrNull() ?: return date
        return LocalDateTime(date, LocalTime(h, m, s)).toInstant(UTC).toLocalDateTime(zone).date
    }

    private fun exdateOf(day: LocalDate, e: PlanEvent, zone: TimeZone): String =
        if (e.allDay) dateText(day) + "T000000Z" else utcText(LocalDateTime(day, e.start).toInstant(zone))

    private fun parseExdate(text: String?, allDay: Boolean, zone: TimeZone): Set<LocalDate> {
        if (text.isNullOrBlank()) return emptySet()
        return text.split(',').mapNotNull { raw ->
            val token = raw.trim().substringAfterLast(':')
            val date = dateOf(token) ?: return@mapNotNull null
            if (!allDay && token.endsWith("Z") && token.length >= 16) {
                val h = token.substring(9, 11).toIntOrNull() ?: return@mapNotNull date
                val m = token.substring(11, 13).toIntOrNull() ?: return@mapNotNull date
                LocalDateTime(date, LocalTime(h, m)).toInstant(UTC).toLocalDateTime(zone).date
            } else date
        }.toSet()
    }

    private fun dateOf(text: String): LocalDate? {
        if (text.length < 8) return null
        val y = text.substring(0, 4).toIntOrNull() ?: return null
        val m = text.substring(4, 6).toIntOrNull() ?: return null
        val d = text.substring(6, 8).toIntOrNull() ?: return null
        return runCatching { LocalDate(y, m, d) }.getOrNull()
    }

    private fun dateText(d: LocalDate): String = "${d.year.toString().padStart(4, '0')}${d.monthNumber.toString().padStart(2, '0')}${d.dayOfMonth.toString().padStart(2, '0')}"

    private fun utcText(at: Instant): String {
        val t = at.toLocalDateTime(UTC)
        return dateText(t.date) + "T" + t.hour.toString().padStart(2, '0') + t.minute.toString().padStart(2, '0') + t.second.toString().padStart(2, '0') + "Z"
    }

    private fun durationOf(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "PT" + (if (h > 0) "${h}H" else "") + (if (m > 0 || h == 0) "${m}M" else "")
    }

    /** `P3D`, `P1W` and `P1W2D` forms; null for anything with a time part, which an all-day event never has. */
    private fun daysOf(duration: String): Int? {
        if (!duration.startsWith("P") || 'T' in duration) return null
        var days = 0
        var number = ""
        for (c in duration.drop(1)) when {
            c.isDigit() -> number += c
            c == 'W' -> { days += (number.toIntOrNull() ?: return null) * 7; number = "" }
            c == 'D' -> { days += number.toIntOrNull() ?: return null; number = "" }
            else -> return null
        }
        return days.takeIf { it > 0 && number.isEmpty() }
    }

    /** `PT1H30M`, `PT45M`, `P3600S`, `PT1H`; null if it names days (a timed event that long is not one PLAN can hold). */
    private fun minutesOf(duration: String): Int? {
        if (!duration.startsWith("P") || 'D' in duration || 'W' in duration) return null
        var seconds = 0L
        var number = ""
        for (c in duration.drop(1)) when {
            c.isDigit() -> number += c
            c == 'T' -> {}
            c == 'H' -> { seconds += (number.toLongOrNull() ?: return null) * 3600; number = "" }
            c == 'M' -> { seconds += (number.toLongOrNull() ?: return null) * 60; number = "" }
            c == 'S' -> { seconds += number.toLongOrNull() ?: return null; number = "" }
            else -> return null
        }
        return (seconds / 60).toInt().takeIf { number.isEmpty() && it > 0 }
    }
}
