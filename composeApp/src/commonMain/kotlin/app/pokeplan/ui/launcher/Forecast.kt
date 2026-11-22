package app.pokeplan.ui.launcher

import kotlin.math.roundToInt

data class DayForecast(val date: String, val code: Int, val max: Double, val min: Double)

data class Forecast(val place: String, val temperature: Double, val code: Int, val days: List<DayForecast>, val fetchedAt: Long) {
    fun encode(): String = buildString {
        append(place.replace('\t', ' ')).append('\t').append(temperature).append('\t').append(code).append('\t').append(fetchedAt).append('\n')
        days.forEach { append(it.date).append('\t').append(it.code).append('\t').append(it.max).append('\t').append(it.min).append('\n') }
    }

    companion object {
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

    fun town(code: Int): Weather = when (code) {
        0 -> Weather.CLEAR
        1, 2, 3, 45, 48 -> Weather.CLOUDS
        in 51..67, in 80..82, in 95..99 -> Weather.RAIN
        in 71..77, 85, 86 -> Weather.SNOW
        else -> Weather.CLEAR
    }
}

fun degrees(celsius: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()} F" else "${celsius.roundToInt()} C"
