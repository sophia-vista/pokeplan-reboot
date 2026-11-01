package app.pokeplan.ui.gear

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Places for the World clock: a name and the zone's id (IANA), bundled so the list works offline. */
class ClockPlace(val id: String, val name: String)

/** The time elsewhere and simple date sums, as pure rules. A zone this platform does not know is skipped, never a crash. */
object WorldClock {
    val places: List<ClockPlace> = listOf(
        ClockPlace("UTC", "UTC"), ClockPlace("Asia/Manila", "Manila"), ClockPlace("Asia/Tokyo", "Tokyo"), ClockPlace("Asia/Seoul", "Seoul"),
        ClockPlace("Asia/Shanghai", "Shanghai"), ClockPlace("Asia/Hong_Kong", "Hong Kong"), ClockPlace("Asia/Singapore", "Singapore"), ClockPlace("Asia/Bangkok", "Bangkok"),
        ClockPlace("Asia/Kolkata", "Delhi"), ClockPlace("Asia/Dubai", "Dubai"), ClockPlace("Europe/Moscow", "Moscow"), ClockPlace("Europe/Istanbul", "Istanbul"),
        ClockPlace("Europe/London", "London"), ClockPlace("Europe/Paris", "Paris"), ClockPlace("Europe/Berlin", "Berlin"), ClockPlace("Europe/Madrid", "Madrid"),
        ClockPlace("Africa/Cairo", "Cairo"), ClockPlace("Africa/Lagos", "Lagos"), ClockPlace("Africa/Johannesburg", "Johannesburg"),
        ClockPlace("America/Sao_Paulo", "São Paulo"), ClockPlace("America/New_York", "New York"), ClockPlace("America/Chicago", "Chicago"),
        ClockPlace("America/Denver", "Denver"), ClockPlace("America/Los_Angeles", "Los Angeles"), ClockPlace("America/Mexico_City", "Mexico City"),
        ClockPlace("Pacific/Honolulu", "Honolulu"), ClockPlace("Australia/Sydney", "Sydney"), ClockPlace("Pacific/Auckland", "Auckland"),
    )

    /** The places shown the first time: the home time (Manila by default) and a few others. */
    val starting: List<String> = listOf("Asia/Manila", "UTC", "Europe/London", "America/New_York", "Asia/Tokyo")

    fun place(id: String): ClockPlace? = places.firstOrNull { it.id == id }

    fun zone(id: String): TimeZone? = try { TimeZone.of(id) } catch (e: Exception) { null }

    /** A place's time at [instant]. [offsetMinutes] is its distance from UTC. */
    data class Reading(val id: String, val name: String, val time: LocalDateTime, val offsetMinutes: Int)

    fun reading(id: String, instant: Instant): Reading? {
        val z = zone(id) ?: return null
        val name = place(id)?.name ?: id
        return Reading(id, name, instant.toLocalDateTime(z), z.offsetAt(instant).totalSeconds / 60)
    }

    /** "UTC+8", "UTC-3:30" or "UTC". */
    fun offsetText(minutes: Int): String {
        if (minutes == 0) return "UTC"
        val a = kotlin.math.abs(minutes)
        return "UTC" + (if (minutes < 0) "-" else "+") + a / 60 + if (a % 60 != 0) ":" + (a % 60).toString().padStart(2, '0') else ""
    }

    /** "09:05" (24 hour) or "9:05 PM". */
    fun clockText(t: LocalDateTime, hour12: Boolean): String {
        val mm = t.minute.toString().padStart(2, '0')
        if (!hour12) return "${t.hour.toString().padStart(2, '0')}:$mm"
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$h:$mm ${if (t.hour < 12) "AM" else "PM"}"
    }

    /** How a place's date compares with [home]'s: "today", "tomorrow" or "yesterday" (or the number of days). */
    fun dayWord(date: LocalDate, home: LocalDate): String = when (val d = home.daysUntil(date)) {
        0 -> "today"; 1 -> "tomorrow"; -1 -> "yesterday"
        else -> if (d > 0) "in $d days" else "${-d} days ago"
    }

    /** A wall-clock time in one place as the same moment in another; null when a zone is unknown. */
    fun convert(time: LocalDateTime, fromId: String, toId: String): LocalDateTime? {
        val from = zone(fromId) ?: return null
        val to = zone(toId) ?: return null
        return time.toInstant(from).toLocalDateTime(to)
    }

    /** Whole days from [a] to [b] (negative when [b] is earlier). */
    fun daysBetween(a: LocalDate, b: LocalDate): Int = a.daysUntil(b)

    fun addDays(date: LocalDate, days: Int): LocalDate = date.plus(days, DateTimeUnit.DAY)

    /** The day of the year (1 to 366). */
    fun dayOfYear(date: LocalDate): Int = date.dayOfYear

    /** The start of [date] in [id] as an instant, for a countdown to a date; null for an unknown zone. */
    fun startOf(date: LocalDate, id: String): Instant? = zone(id)?.let { date.atStartOfDayIn(it) }
}
