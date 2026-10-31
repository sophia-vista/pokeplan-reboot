package app.pokeplan.launcher

import android.content.Context
import app.pokeplan.ui.launcher.DayForecast
import app.pokeplan.ui.launcher.Forecast
import app.pokeplan.ui.launcher.GeoPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The launcher's forecast, from Open-Meteo (no account or key; it is sent only the coordinates of the place the user
 * chose, never the phone's location). A forecast is kept on the device for [FRESH_MS]; when the network is down the last
 * one is shown however old it is.
 */
internal class WeatherClient(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    suspend fun forecast(place: GeoPlace): Forecast? = withContext(Dispatchers.IO) {
        val key = "forecast_${place.latitude}_${place.longitude}"
        val kept = Forecast.decode(prefs.getString(key, null))
        if (kept != null && System.currentTimeMillis() - kept.fetchedAt < FRESH_MS) return@withContext kept
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto"
            val json = JSONObject(get(url))
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val days = (0 until dates.length()).map { i ->
                DayForecast(dates.getString(i), daily.getJSONArray("weather_code").getInt(i), daily.getJSONArray("temperature_2m_max").getDouble(i), daily.getJSONArray("temperature_2m_min").getDouble(i))
            }
            Forecast(place.name, current.getDouble("temperature_2m"), current.getInt("weather_code"), days, System.currentTimeMillis()).also { prefs.edit().putString(key, it.encode()).apply() }
        } catch (e: Exception) {
            kept
        }
    }

    suspend fun search(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=5&language=en&format=json&name=${URLEncoder.encode(query, "UTF-8")}"))
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).map { i ->
                val r = results.getJSONObject(i)
                val name = listOf(r.optString("name"), r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
                GeoPlace(name, r.getDouble("latitude"), r.getDouble("longitude"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val FRESH_MS = 30 * 60_000L
    }
}
