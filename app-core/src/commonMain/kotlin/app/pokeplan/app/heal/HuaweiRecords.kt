package app.pokeplan.app.heal

import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.Imported
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.VitalType
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
                s.title.startsWith("PrivacyDataModel") && s.nodes.any { id(it) == "action_bar_title" && dayTitle.matches(it.text.trim()) } -> sleepDay(s, nowMillis, zone)
                s.title.startsWith("PrivacyDataModel") -> allData(s, today, nowMillis, zone)
                s.nodes.any { it.text == "Weight" } && s.nodes.any { weightDate.matches(it.text) } -> weight(s, today, zone)
                else -> emptyList()
            }
            records.forEach { out[it.id] = it }
        }
        // A night with real times (a Sleep All data day page) replaces today's night from the Sleep page and the length-only reading of the same day.
        val nights = out.values.filterIsInstance<Imported.Sleep>().filter { it.id != "sleep:$today" }
        val nightDays = nights.map { it.endMillis.toLocalDateTime(zone).date.toString() }.toSet()
        if (nights.any { it.endMillis.toLocalDateTime(zone).date == today }) out.remove("sleep:$today")
        out.values.removeAll { it is Imported.Vital && it.type == VitalType.SLEEP_LENGTH && it.id.substringAfterLast(':') in nightDays }
        // One record per day: a day's steps only grow, so the largest count seen wins (the live count of today, or a history row).
        val stepRecords = (out.values.filterIsInstance<Imported.Steps>() + steps(seen, zone).filterIsInstance<Imported.Steps>()).groupBy { it.id }.values.map { l -> l.maxWith(compareBy({ it.steps }, { it.atMillis })) }
        out.values.removeAll { it is Imported.Steps }
        return out.values.toList() + stepRecords + vitals(seen, today, zone)
    }

    // --- steps and vitals (0.186.0) -----------------------------------------------------------------------------------------------

    private fun dayOf(millis: Long, zone: TimeZone) = millis.toLocalDateTime(zone).date

    /**
     * A day's steps from the Health page's summary and from the step notification, each at the time it was captured. One record per day: the largest count seen
     * wins (a day's steps only grow) and its capture time is kept. A notification only says "N steps" and the Health page "N steps" for the same day.
     */
    private fun steps(seen: HuaweiSeen, zone: TimeZone): List<Imported> {
        val found = seen.screens.mapNotNull { s -> HuaweiHome.read(s).steps?.takeIf { it > 0 }?.let { it to s.atMillis } } + seen.notifications.mapNotNull { n -> n.steps?.takeIf { it > 0 }?.let { it to n.atMillis } }
        return found.groupBy { dayOf(it.second, zone) }.map { (day, list) ->
            val best = list.maxWith(compareBy({ it.first }, { it.second }))
            Imported.Steps(HuaweiHealth.PROVIDER, APP, "steps:$day", best.first, best.second)
        }
    }

    /**
     * The readings the Health page shows (each card of the 14 November capture) and the sleep score on the Sleep page. The Health page has no time for a reading, so it is
     * stamped with the capture and its day: a day keeps one resting heart rate, SpO2, skin temperature, stress and sleep score (a later read of the same day that shows
     * another value updates it), and one heart rate per value (a re-read of the same bpm adds nothing). The Sleep page's score belongs to the night it shows, not today.
     */
    private fun vitals(seen: HuaweiSeen, today: LocalDate, zone: TimeZone): List<Imported> {
        val out = LinkedHashMap<String, Imported.Vital>()
        fun put(type: VitalType, value: Double, day: LocalDate, at: Long, key: String) {
            val v = Imported.Vital(HuaweiHealth.PROVIDER, APP, key, type, value, at)
            val old = out[key]
            if (old == null || at >= old.atMillis) out[key] = v
        }
        // Heart rate is a range for the day (owner, 21 November 2026): the lowest and the highest bpm seen that day, stamped with the capture that showed each.
        val hr = LinkedHashMap<LocalDate, MutableList<Pair<Int, Long>>>()
        for (s in seen.screens) {
            val day = dayOf(s.atMillis, zone)
            val h = HuaweiHome.read(s)
            h.heartRate?.let { hr.getOrPut(day) { ArrayList() } += it to s.atMillis }
            h.restingHeartRate?.let { put(VitalType.RESTING_HEART_RATE, it.toDouble(), day, s.atMillis, "restingheartrate:$day") }
            h.spo2?.let { put(VitalType.SPO2, it.toDouble(), day, s.atMillis, "spo2:$day") }
            h.skinTemperature?.let { put(VitalType.SKIN_TEMPERATURE, it, day, s.atMillis, "skintemperature:$day") }
            h.stress?.let { put(VitalType.STRESS, it.toDouble(), day, s.atMillis, "stress:$day") }
            h.sleepScore?.let { put(VitalType.SLEEP_SCORE, it.toDouble(), day, s.atMillis, "sleepscore:$day") }
            if (s.title.startsWith("KnitSleepDetail")) sleepScore(s, today, zone)?.let { (night, score) ->
                // Stamped with the capture, or noon of that morning if the capture is later: it lands on the night's own day.
                put(VitalType.SLEEP_SCORE, score.toDouble(), night, minOf(s.atMillis, millis(night, LocalTime(12, 0), zone)), "sleepscore:$night")
            }
        }
        for ((day, list) in hr) {
            val low = list.minBy { it.first }; val high = list.maxBy { it.first }
            put(VitalType.HEART_RATE_LOW, low.first.toDouble(), day, low.second, "heartlow:$day")
            put(VitalType.HEART_RATE_HIGH, high.first.toDouble(), day, high.second, "hearthigh:$day")
        }
        return out.values.toList()
    }

    /** The night a Sleep page shows (its date, "Today" or "Sat, Nov 14") and the score it gives, "## points"; null when either is missing. */
    private fun sleepScore(s: ScreenCapture, today: LocalDate, zone: TimeZone): Pair<LocalDate, Int>? {
        val dateText = s.nodes.firstOrNull { id(it) == "fitness_detail_time_date_tv" }?.text?.trim() ?: return null
        val on = if (dateText.equals("Today", ignoreCase = true)) today else sleepDate.matchEntire(dateText)?.let { m ->
            month(m.groupValues[2])?.let { latestYear(it, m.groupValues[3].toInt(), today, weekdays[m.groupValues[1].lowercase()]) }
        } ?: return null
        val score = s.nodes.firstOrNull { id(it) == "scoring_value" }?.text?.trim()?.toIntOrNull()?.takeIf { it in 1..100 } ?: return null
        return on to score
    }

    // --- All data pages (history) --------------------------------------------------------------------------------------------------

    /** One row of an All data page: its date, and what it says; null for a row that is not a date and a value. */
    data class AllDataRow(val date: LocalDate, val content: String)

    private val monthYear = Regex("""^([A-Za-z]+) (\d{4})$""")
    private val slashDate = Regex("""^(\d{1,2})/(\d{1,2})$""")
    private val stepsRow = Regex("""^(\d{1,3}(?:,\d{3})+|\d+)\s*steps?$""", RegexOption.IGNORE_CASE)
    private val skinRange = Regex("""^(\d{2}(?:\.\d)?)\s*-\s*(\d{2}(?:\.\d)?)\s*°\s*C$""", RegexOption.IGNORE_CASE)
    private val spo2Range = Regex("""^SpO\S*\s*range\s*(\d{2,3})\s*%\s*-\s*(\d{2,3})\s*%$""", RegexOption.IGNORE_CASE)

    /**
     * The rows of an All data page (`PrivacyDataModelActivity`: month headers `privacy_title`, then per row a value `privacy_content_title` and its date `privacy_time`,
     * "11/14" or "November 14"). The year is the header's; a row above any header takes the latest year in which the date is not in the future.
     */
    fun allDataRows(s: ScreenCapture, today: LocalDate): List<AllDataRow> {
        var year: Int? = null
        var content = ""
        val out = ArrayList<AllDataRow>()
        for (n in s.nodes) when (id(n)) {
            "privacy_title" -> monthYear.matchEntire(n.text.trim())?.let { year = it.groupValues[2].toInt() }
            "privacy_content_title" -> content = n.text.trim()
            "privacy_time" -> {
                val t = n.text.trim()
                val parsed = slashDate.matchEntire(t)?.let { m -> Month.entries.getOrNull(m.groupValues[1].toInt() - 1)?.let { it to m.groupValues[2].toInt() } }
                    ?: monthDay.matchEntire(t)?.let { m -> month(m.groupValues[1])?.let { it to m.groupValues[2].toInt() } }
                val date = parsed?.let { (mo, d) -> year?.let { y -> runCatching { LocalDate(y, mo, d) }.getOrNull() } ?: latestYear(mo, d, today) }
                if (date != null && date <= today && content.isNotEmpty()) out += AllDataRow(date, content)
                content = ""
            }
        }
        return out
    }

    private val dayTitle = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})\s+data$""", RegexOption.IGNORE_CASE)
    private val sleepSpan = Regex("""^(\d{1,2})/(\d{1,2}),\s*(\d{1,2}:\d{2}\s*[AP]?M?)\s*-\s*(?:(\d{1,2})/(\d{1,2}),\s*)?(\d{1,2}:\d{2}\s*[AP]?M?)$""", RegexOption.IGNORE_CASE)

    /**
     * A Sleep All data day page (the page a row of the list opens; its title is "MM/DD/YYYY data"): one session per row, a length ("5 h 9 min") and its span
     * ("11/11, 11:30 PM-11/12, 6:50 AM", or "11/12, 1:00 PM-2:10 PM" within one day). These are the times the list lacks, so they become real nights and naps
     * ([Imported.Sleep], id from the start). A date later than the page's day is the year before; a span that does not end after it starts is left out.
     */
    private fun sleepDay(s: ScreenCapture, nowMillis: Long, zone: TimeZone): List<Imported> {
        val t = s.nodes.firstNotNullOfOrNull { n -> if (id(n) == "action_bar_title") dayTitle.matchEntire(n.text.trim()) else null } ?: return emptyList()
        val page = runCatching { LocalDate(t.groupValues[3].toInt(), t.groupValues[1].toInt(), t.groupValues[2].toInt()) }.getOrNull() ?: return emptyList()
        fun dateOf(m: String, d: String): LocalDate? = (page.year downTo page.year - 1).firstNotNullOfOrNull { y -> runCatching { LocalDate(y, m.toInt(), d.toInt()) }.getOrNull()?.takeIf { it <= page } }
        val out = ArrayList<Imported>()
        for (n in s.nodes) if (id(n) == "privacy_time") {
            val m = sleepSpan.matchEntire(n.text.trim()) ?: continue
            val startDate = dateOf(m.groupValues[1], m.groupValues[2]) ?: continue
            val startTime = timeOf(m.groupValues[3]) ?: continue
            val endTime = timeOf(m.groupValues[6]) ?: continue
            val endDate = if (m.groupValues[4].isNotEmpty()) dateOf(m.groupValues[4], m.groupValues[5]) ?: continue else startDate
            val start = millis(startDate, startTime, zone)
            var end = millis(endDate, endTime, zone)
            if (m.groupValues[4].isEmpty() && end <= start) end += 24 * 3_600_000L // "11:30 PM-6:50 AM" with no date for the end
            val minutes = (end - start) / 60_000
            if (minutes !in 30..16 * 60 || end > nowMillis + 3_600_000L) continue
            out += Imported.Sleep(HuaweiHealth.PROVIDER, APP, "sleep:$start", start, end)
        }
        return out
    }

    /** The day an All data row's date text means ("11/12" or "November 12": the latest such date not in the future), or null. Used to see whether a row is already saved. */
    fun rowDate(text: String, today: LocalDate): LocalDate? {
        val t = text.trim()
        val parsed = slashDate.matchEntire(t)?.let { m -> Month.entries.getOrNull(m.groupValues[1].toInt() - 1)?.let { it to m.groupValues[2].toInt() } }
            ?: monthDay.matchEntire(t)?.let { m -> month(m.groupValues[1])?.let { it to m.groupValues[2].toInt() } }
        return parsed?.let { (mo, d) -> latestYear(mo, d, today) }
    }

    /** The oldest day an All data capture shows, or null when it shows none: the list is newest first, so a read can stop scrolling once this is old enough. */
    fun oldestAllDataDay(s: ScreenCapture, nowMillis: Long, zone: TimeZone): LocalDate? =
        if (!s.title.startsWith("PrivacyDataModel")) null else allDataRows(s, nowMillis.toLocalDateTime(zone).date).minOfOrNull { it.date }

    /**
     * An All data page as history, one record per row (owner, 21 November 2026, "keep all"): a night's **length** (Sleep), the **range** of skin temperature and of
     * SpO2 for a day (each end its own reading). Each is stamped at noon of its own day and is never judged. A sleep row has no bedtime, so it is a length, not a night with times.
     */
    private fun allData(s: ScreenCapture, today: LocalDate, nowMillis: Long, zone: TimeZone): List<Imported> {
        val out = ArrayList<Imported>()
        for ((date, text) in allDataRows(s, today)) {
            val at = minOf(millis(date, LocalTime(12, 0), zone), nowMillis)
            fun put(type: VitalType, value: Double, key: String) { out += Imported.Vital(HuaweiHealth.PROVIDER, APP, "$key:$date", type, value, at) }
            stepsRow.matchEntire(text)?.let { m ->
                m.groupValues[1].replace(",", "").toIntOrNull()?.let { out += Imported.Steps(HuaweiHealth.PROVIDER, APP, "steps:$date", it, at) }
            } ?: skinRange.matchEntire(text)?.let { m ->
                put(VitalType.SKIN_TEMPERATURE_LOW, m.groupValues[1].toDouble(), "skintemplow"); put(VitalType.SKIN_TEMPERATURE_HIGH, m.groupValues[2].toDouble(), "skintemphigh")
            } ?: spo2Range.matchEntire(text)?.let { m ->
                put(VitalType.SPO2_LOW, m.groupValues[1].toDouble(), "spo2low"); put(VitalType.SPO2_HIGH, m.groupValues[2].toDouble(), "spo2high")
            } ?: hoursMinutes.matchEntire(text)?.takeIf { it.groupValues[1].isNotEmpty() || it.groupValues[2].isNotEmpty() }?.let { m ->
                val minutes = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
                put(VitalType.SLEEP_LENGTH, minutes.toDouble(), "sleeplength:$minutes")
            }
        }
        return out
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

    /** The start of the oldest session [s] shows (an Exercise records capture), or null when it is another page or shows none. */
    fun oldestExerciseStart(s: ScreenCapture, nowMillis: Long, zone: TimeZone): Long? {
        if (!s.title.startsWith("SportHistory")) return null
        return exercises(s, nowMillis.toLocalDateTime(zone).date, zone).filterIsInstance<Imported.Exercise>().minOfOrNull { it.startMillis }
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
