package app.pokeplan.ui.launcher

import kotlinx.datetime.LocalDate

/** How the clock widget writes the date. The dialogue font has no slash, so the numeric style is the ISO one with dashes. */
enum class DateStyle(val label: String) {
    SHORT("Sat 31 Oct"), LONG("Saturday 31 October"), DAY_MONTH_YEAR("31 Oct 2026"), MONTH_DAY("Oct 31"), ISO("2026-10-31");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** [date] written in [style]: English day and month names, the month and day names cut to three letters in the short styles. */
fun formatDate(date: LocalDate, style: DateStyle): String {
    val day = dayName(date.dayOfWeek)
    val month = monthName(date.month)
    return when (style) {
        DateStyle.SHORT -> "${day.take(3)} ${date.day} ${month.take(3)}"
        DateStyle.LONG -> "$day ${date.day} $month"
        DateStyle.DAY_MONTH_YEAR -> "${date.day} ${month.take(3)} ${date.year}"
        DateStyle.MONTH_DAY -> "${month.take(3)} ${date.day}"
        DateStyle.ISO -> "${date.year}-${two(date.monthNumber)}-${two(date.day)}"
    }
}

/** A time of day: `13:05` or `13:05:09` on a 24-hour clock, `1:05 PM` or `1:05:09 PM` on a 12-hour one; [second] null leaves the seconds out. */
fun formatTime(hour: Int, minute: Int, second: Int? = null, hour12: Boolean = false): String {
    val seconds = second?.let { ":${two(it)}" }.orEmpty()
    if (!hour12) return "${two(hour)}:${two(minute)}$seconds"
    val shown = if (hour % 12 == 0) 12 else hour % 12
    return "$shown:${two(minute)}$seconds ${if (hour < 12) "AM" else "PM"}"
}

/** The date style the user chose in Settings → Date and time. */
internal val app.pokeplan.app.Settings.dateStyle: DateStyle get() = DateStyle.entries.firstOrNull { it.name == dateFormat } ?: DateStyle.SHORT
