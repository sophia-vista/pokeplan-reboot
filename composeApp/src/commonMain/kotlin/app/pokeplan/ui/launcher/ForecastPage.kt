package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
internal fun ForecastPage(forecast: Forecast?, prefs: LauncherPrefs, pixel: Int, onPlace: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XL, bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine("Forecast", pixel, dim = true) {}
                }
            }
            if (forecast == null) {
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        PmdText(if (prefs.place == null) "No place chosen yet" else "No forecast yet: check the connection", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.8f)
                        PmdMenuLine(if (prefs.place == null) "Choose a place" else "Change place", pixel, onClick = onPlace)
                    }
                }
            } else {
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        PmdText(forecast.place, pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.8f)
                        PmdText("${degrees(forecast.temperature, prefs.fahrenheit)}  ${WeatherCodes.label(forecast.code)}", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S))
                    }
                }
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        forecast.days.forEachIndexed { i, day ->
                            if (i > 0) PmdRule(pixel)
                            Row(Modifier.fillMaxWidth().height(Spacing.Target + Spacing.S).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                                PmdText(dayLabel(day.date, i), pixel, Modifier.weight(0.7f))
                                PmdText(WeatherCodes.label(day.code), pixel, Modifier.weight(1.3f), alpha = 0.85f)
                                PmdText("${degrees(day.max, prefs.fahrenheit)}  ${degrees(day.min, prefs.fahrenheit)}", pixel, Modifier.weight(1f), alpha = 0.75f)
                            }
                        }
                    }
                }
                PmdBox(pixel, Modifier.fillMaxWidth()) { PmdMenuLine("Change place", pixel, onClick = onPlace) }
            }
        }
    }
}

private fun dayLabel(date: String, index: Int): String =
    if (index == 0) "Today" else runCatching { dayName(LocalDate.parse(date).dayOfWeek).take(3) }.getOrDefault(date)

@Composable
internal fun PlacePage(prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, host: LauncherHost, pixel: Int, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        scope.launch { results = host.searchPlaces(query.trim()); searching = false }
    }
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XL, bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine(prefs.place?.let { "Place: ${it.name}" } ?: "Choose a place", pixel, dim = true) {}
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PmdField(query, { query = it }, "Town or city")
                    PmdMenuLine(if (searching) "Searching" else "Search", pixel, dim = query.isBlank() || searching) { search() }
                }
            }
            results?.let { found ->
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        if (found.isEmpty()) PmdText("No place found: check the spelling and the connection", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M), alpha = 0.8f)
                        found.forEach { place -> PmdMenuLine(place.name, pixel) { onPrefs(prefs.withPlace(place)); onClose() } }
                    }
                }
            }
            if (prefs.place != null) PmdBox(pixel, Modifier.fillMaxWidth()) { PmdMenuLine("Forget the place", pixel, dim = true) { onPrefs(prefs.withPlace(null)); onClose() } }
        }
    }
}
