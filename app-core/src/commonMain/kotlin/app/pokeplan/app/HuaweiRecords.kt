package app.pokeplan.app

import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.Imported
import app.pokeplan.heal.MeasureType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime

/**
 * Turns captures of Huawei Health's own pages into HEAL records (INTEGRATIONS_AND_PRIVACY.md 5.2.2), from the layout of the owner's capture of 14 November
 * 2026 (Huawei Health in English):
 * - **Exercise records** (`SportHistoryActivity`): each session's type, date, start time, duration (`h:mm:ss`) and distance in km. The year comes from the
 *   month headers ("November 2026, …", or the sticky "SEPTEMBER 2026"); without one, the latest such date not in the future.
 * - **Weight** (a web page): "Thu, July 3 at 07:12", then the value and `kg` or `lb`, then "Body fat 21.4%". The year is the latest one whose date falls on
 *   that weekday and is not in the future.
 * - **Sleep** (`KnitSleepDetailActivity`): only the date and the night's length are on the page, so a night is made only for **today**, ending at the Health
 *   page's get-up time; any other day has no times and is left out.
 * Every record carries the provider [HuaweiHealth.PROVIDER] and an id made from its own time, so reading the same page again adds nothing twice.
 * Anything that cannot be read whole is left out, never guessed.
 */
object HuaweiRecords {
    const val APP = HuaweiHealth.PACKAGE

    fun read(seen: HuaweiSeen, nowMillis: Long, zone: TimeZone): List<Imported> {
        val today = nowMillis.toLocalDateTime(zone).date
        val out = LinkedHashMap<String, Imported>()
        for (s in seen.screens) {
            val records = when {
                s.title.startsWith("SportHistory") -> exercises(s, today, zone)
                s.title.startsWith("KnitSleepDetail") -> listOfNotNull(sleep(s, today, seen.home.getUpMinute, zone))
                s.nodes.any { it.text == "Weight" } && s.nodes.any { weightDate.matches(it.text) } -> weight(s, today, zone)
                else -> emptyList()
            }
            records.forEach { out[it.id] = it }
        }
        return out.values.toList()
    }

    // --- dates ----------------------------------------------------------------------------------------------------------------

    private val months = Month.entries.associateBy { it.name.lowercase() } + Month.entries.associateBy { it.name.lowercase().take(3) }
    private fun month(word: String) = months[word.lowercase()]
    private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase().take(3) }

    /** "4:12 PM", "16:12" → the time of day, or null. */
    private fun timeOf(s: String): LocalTime? {
        val m = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?$""", RegexOption.IGNORE_CASE).matchEntire(s.trim()) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        when (m.groupValues[3].uppercase()) { "AM" -> if (h == 12) h = 0; "PM" -> if (h != 12) h += 12 }
        return if (h in 0..23 && min in 0..59) LocalTime(h, min) else null
    }

    /** The latest year (this one or up to six before) in which [month] [day] is a real date, not after [today], and on [weekday] when given. */
    private fun latestYear(month: Month, day: Int, today: LocalDate, weekday: DayOfWeek? = null): LocalDate? =
        (today.year downTo today.year - 6).firstNotNullOfOrNull { y ->
            runCatching { LocalDate(y, month, day) }.getOrNull()?.takeIf { it <= today && (weekday == null || it.dayOfWeek == weekday) }
        }

    private fun millis(date: LocalDate, time: LocalTime, zone: TimeZone) = LocalDateTime(date, time).toEpochMillis(zone)

    // --- exercise records -------------------------------------------------------------------------------------------------------

    private fun id(n: ScreenNode) = n.viewId.substringAfter(":id/")
    private val header = Regex("""^([A-Za-z]+) (\d{4})\b""")
    private val monthDay = Regex("""^([A-Za-z]+) (\d{1,2})$""")
    private val clock = Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})$""")

    fun activityType(name: String): ActivityType {
        val n = name.lowercase()
        return when {
            "walk" in n || "hik" in n -> ActivityType.WALK
            "run" in n -> ActivityType.RUN
            "cycl" in n || "bike" in n || "ride" in n -> ActivityType.CYCLE
            "swim" in n -> ActivityType.SWIM
            "strength" in n || "weight" in n -> ActivityType.STRENGTH
            "yoga" in n || "stretch" in n || "pilates" in n -> ActivityType.STRETCH
            else -> ActivityType.OTHER
        }
    }

    private fun exercises(s: ScreenCapture, today: LocalDate, zone: TimeZone): List<Imported> {
        // The sticky header (listed last) names the month of the sessions shown above the first group header.
        var year: Int? = s.nodes.lastOrNull { id(it) == "htv_sport_history_cut_time" }?.let { header.find(it.text)?.groupValues?.get(2)?.toIntOrNull() }
        val out = ArrayList<Imported>()
        var type = ""; var date = ""; var time = ""; var km: Double? = null; var unit = ""
        for (n in s.nodes) {
            val t = n.text.trim()
            when (id(n)) {
                "first_item_title" -> header.find(n.description.ifBlank { t })?.let { year = it.groupValues[2].toInt() }
                "hw_show_health_running_history_child_item_chief_sport_type_text" -> { type = t; date = ""; time = ""; km = null; unit = "" }
                "hw_show_health_running_history_child_item_date" -> date = t
                "hw_show_health_running_history_child_item_left_date_text" -> time = t
                "hw_show_health_running_history_child_item_chief_sport_data_text" -> km = t.replace(",", "").toDoubleOrNull()
                "hw_show_health_running_history_child_item_chief_sport_data_unit" -> unit = t
                "hw_show_health_running_history_child_item_center_text" -> {
                    session(type, date, time, t, km.takeIf { unit == "km" }, year, today, zone)?.let { out += it }
                    type = ""
                }
            }
        }
        return out
    }

    private fun session(type: String, date: String, time: String, duration: String, km: Double?, year: Int?, today: LocalDate, zone: TimeZone): Imported.Exercise? {
        if (type.isBlank()) return null
        val md = monthDay.matchEntire(date) ?: return null
        val month = month(md.groupValues[1]) ?: return null
        val day = md.groupValues[2].toInt()
        val on = year?.let { y -> runCatching { LocalDate(y, month, day) }.getOrNull() } ?: latestYear(month, day, today) ?: return null
        val at = timeOf(time) ?: return null
        val c = clock.matchEntire(duration.trim()) ?: return null
        val seconds = (c.groupValues[1].toIntOrNull() ?: 0) * 3600 + c.groupValues[2].toInt() * 60 + c.groupValues[3].toInt()
        if (seconds < 60) return null
        val start = millis(on, at, zone)
        return Imported.Exercise(HuaweiHealth.PROVIDER, APP, "exercise:$start", activityType(type), start, start + seconds * 1000L, km?.let { (it * 1000).toInt() })
    }

    // --- weight ------------------------------------------------------------------------------------------------------------------

    private val weightDate = Regex("""^([A-Za-z]{3}), ([A-Za-z]+) (\d{1,2})(?:, (\d{4}))? at (\d{1,2}:\d{2}(?:\s*[AP]M)?)$""", RegexOption.IGNORE_CASE)
    private val bodyFat = Regex("""^Body fat (\d{1,2}(?:\.\d)?)%$""", RegexOption.IGNORE_CASE)

    private fun weight(s: ScreenCapture, today: LocalDate, zone: TimeZone): List<Imported> {
        val i = s.nodes.indexOfFirst { weightDate.matches(it.text.trim()) }
        if (i < 0) return emptyList()
        val m = weightDate.matchEntire(s.nodes[i].text.trim())!!
        val month = month(m.groupValues[2]) ?: return emptyList()
        val day = m.groupValues[3].toInt()
        val on = m.groupValues[4].toIntOrNull()?.let { y -> runCatching { LocalDate(y, month, day) }.getOrNull() }
            ?: latestYear(month, day, today, weekdays[m.groupValues[1].lowercase()]) ?: return emptyList()
        val at = millis(on, timeOf(m.groupValues[5]) ?: return emptyList(), zone)
        val out = ArrayList<Imported>()
        // The value and its unit come right after the date (two elements); body fat follows within a few.
        val value = s.nodes.getOrNull(i + 1)?.text?.trim()?.toDoubleOrNull()
        val unit = s.nodes.getOrNull(i + 2)?.text?.trim()
        if (value != null && (unit == "kg" || unit == "lb")) out += Imported.Body(HuaweiHealth.PROVIDER, APP, "weight:$at", MeasureType.WEIGHT, value, unit, at)
        s.nodes.drop(i + 1).take(8).firstNotNullOfOrNull { bodyFat.matchEntire(it.text.trim()) }?.let { f ->
            out += Imported.Body(HuaweiHealth.PROVIDER, APP, "bodyfat:$at", MeasureType.BODY_FAT, f.groupValues[1].toDouble(), "%", at)
        }
        return out
    }

    // --- sleep -------------------------------------------------------------------------------------------------------------------

    private val sleepDate = Regex("""^([A-Za-z]{3}), ([A-Za-z]+) (\d{1,2})$""")
    private val hoursMinutes = Regex("""^(?:(\d{1,2})\s*h)?\s*(?:(\d{1,2})\s*min)?$""")

    private fun sleep(s: ScreenCapture, today: LocalDate, getUpMinute: Int?, zone: TimeZone): Imported.Sleep? {
        getUpMinute ?: return null
        val dateText = s.nodes.firstOrNull { id(it) == "fitness_detail_time_date_tv" }?.text?.trim() ?: return null
        val on = if (dateText.equals("Today", ignoreCase = true)) today else sleepDate.matchEntire(dateText)?.let { m ->
            month(m.groupValues[2])?.let { latestYear(it, m.groupValues[3].toInt(), today, weekdays[m.groupValues[1].lowercase()]) }
        }
        if (on != today) return null
        val length = s.nodes.firstOrNull { id(it) == "common_sleep_sleep_time" }?.text?.trim()?.let { hoursMinutes.matchEntire(it) } ?: return null
        if (length.groupValues[1].isEmpty() && length.groupValues[2].isEmpty()) return null
        val minutes = (length.groupValues[1].toIntOrNull() ?: 0) * 60 + (length.groupValues[2].toIntOrNull() ?: 0)
        if (minutes !in 30..16 * 60) return null
        val end = today.atStartOfDayIn(zone).toEpochMilliseconds() + getUpMinute * 60_000L
        return Imported.Sleep(HuaweiHealth.PROVIDER, APP, "sleep:$today", end - minutes * 60_000L, end)
    }
}
