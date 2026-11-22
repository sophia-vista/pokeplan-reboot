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

class ClockPlace(val id: String, val name: String)

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

    val starting: List<String> = listOf("Asia/Manila", "UTC", "Europe/London", "America/New_York", "Asia/Tokyo")

    fun place(id: String): ClockPlace? = places.firstOrNull { it.id == id }

    fun zone(id: String): TimeZone? = try { TimeZone.of(id) } catch (e: Exception) { null }

    data class Reading(val id: String, val name: String, val time: LocalDateTime, val offsetMinutes: Int)

    fun reading(id: String, instant: Instant): Reading? {
        val z = zone(id) ?: return null
        val name = place(id)?.name ?: id
        return Reading(id, name, instant.toLocalDateTime(z), z.offsetAt(instant).totalSeconds / 60)
    }

    fun offsetText(minutes: Int): String {
        if (minutes == 0) return "UTC"
        val a = kotlin.math.abs(minutes)
        return "UTC" + (if (minutes < 0) "-" else "+") + a / 60 + if (a % 60 != 0) ":" + (a % 60).toString().padStart(2, '0') else ""
    }

    fun clockText(t: LocalDateTime, hour12: Boolean): String {
        val mm = t.minute.toString().padStart(2, '0')
        if (!hour12) return "${t.hour.toString().padStart(2, '0')}:$mm"
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$h:$mm ${if (t.hour < 12) "AM" else "PM"}"
    }

    fun dayWord(date: LocalDate, home: LocalDate): String = when (val d = home.daysUntil(date)) {
        0 -> "today"; 1 -> "tomorrow"; -1 -> "yesterday"
        else -> if (d > 0) "in $d days" else "${-d} days ago"
    }

    fun convert(time: LocalDateTime, fromId: String, toId: String): LocalDateTime? {
        val from = zone(fromId) ?: return null
        val to = zone(toId) ?: return null
        return time.toInstant(from).toLocalDateTime(to)
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Int = a.daysUntil(b)

    fun addDays(date: LocalDate, days: Int): LocalDate = date.plus(days, DateTimeUnit.DAY)

    fun dayOfYear(date: LocalDate): Int = date.dayOfYear

    fun startOf(date: LocalDate, id: String): Instant? = zone(id)?.let { date.atStartOfDayIn(it) }
}
