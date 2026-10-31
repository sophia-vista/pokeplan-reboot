package app.pokeplan.ui.launcher

import kotlin.math.roundToInt

/** One day of the forecast: its date (`2026-10-31`), WMO weather [code] and the highest and lowest temperature, in Celsius. */
data class DayForecast(val date: String, val code: Int, val max: Double, val min: Double)

/** The forecast for a place: the temperature and WMO weather [code] now (Celsius) and the days ahead, read at [fetchedAt] (epoch milliseconds). */
data class Forecast(val place: String, val temperature: Double, val code: Int, val days: List<DayForecast>, val fetchedAt: Long) {
    /** One text for the cache: a header line, then a line a day. */
    fun encode(): String = buildString {
        append(place.replace('\t', ' ')).append('\t').append(temperature).append('\t').append(code).append('\t').append(fetchedAt).append('\n')
        days.forEach { append(it.date).append('\t').append(it.code).append('\t').append(it.max).append('\t').append(it.min).append('\n') }
    }

    companion object {
        /** Reads [encode]'s text back; null for anything damaged. */
        fun decode(text: String?): Forecast? {
            val lines = text.orEmpty().lines().filter { it.isNotBlank() }
            val head = lines.firstOrNull()?.split('\t') ?: return null
            if (head.size != 4) return null
            val temperature = head[1].toDoubleOrNull() ?: return null
            val code = head[2].toIntOrNull() ?: return null
            val at = head[3].toLongOrNull() ?: return null
            val days = lines.drop(1).mapNotNull { l ->
                val f = l.split('\t')
                val c = f.getOrNull(1)?.toIntOrNull(); val hi = f.getOrNull(2)?.toDoubleOrNull(); val lo = f.getOrNull(3)?.toDoubleOrNull()
                if (f.size == 4 && c != null && hi != null && lo != null) DayForecast(f[0], c, hi, lo) else null
            }
            return Forecast(head[0], temperature, code, days, at)
        }
    }
}

/** What the WMO weather codes (the ones Open-Meteo reports) mean for the launcher. */
object WeatherCodes {
    fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    /** The town's weather effect for a code: rain, snow, cloud shadows or a clear sky. */
    fun town(code: Int): Weather = when (code) {
        0 -> Weather.CLEAR
        1, 2, 3, 45, 48 -> Weather.CLOUDS
        in 51..67, in 80..82, in 95..99 -> Weather.RAIN
        in 71..77, 85, 86 -> Weather.SNOW
        else -> Weather.CLEAR
    }
}

/** A temperature as the font can draw it (no degree sign): `28 C`, or `82 F` in Fahrenheit. */
fun degrees(celsius: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()} F" else "${celsius.roundToInt()} C"
