package app.pokeplan.ui.gear

import app.pokeplan.core.pokemon.GearTool
import kotlin.math.abs

/** The groups of tools on the Pokégear's home. */
enum class ToolCategory(val label: String) { CALCULATE("Calculate"), CONVERT("Convert"), TIME("Time"), LIGHT("Light"), CHOOSE("Choose"), MEASURE("Measure") }

/** A tool as the home lists it: its [category], a short [description] and the words people look for it by ([keywords]). Bundled, so searching needs no network. */
class ToolInfo(val tool: GearTool, val category: ToolCategory, val description: String, val keywords: List<String>)

/**
 * The Pokégear's tools in one place for the home screen: what groups they are in, what they are for, and a search that is tolerant of slips and
 * synonyms ("lbs" finds Units, "yen" finds Currency, "torch" finds the Flashlight). Favourites and the tools used lately come first.
 */
object ToolCatalog {
    val all: List<ToolInfo> = listOf(
        ToolInfo(GearTool.CALCULATOR, ToolCategory.CALCULATE, "Sums with brackets and percentages, scientific functions, memory", listOf("calc", "math", "maths", "percent", "percentage", "tip", "sum", "add", "plus", "multiply", "sin", "cos", "sqrt", "scientific")),
        ToolInfo(GearTool.UNITS, ToolCategory.CONVERT, "Lengths, weights, cooking, temperature, data and more", listOf("convert", "converter", "lbs", "pounds", "kg", "miles", "km", "feet", "inches", "temperature", "celsius", "fahrenheit", "cup", "oven", "weight", "length", "area", "volume", "speed", "mbps", "bytes")),
        ToolInfo(GearTool.CURRENCY, ToolCategory.CONVERT, "Money at today's rates, offline, with travel costs", listOf("money", "exchange", "rate", "rates", "yen", "peso", "dollar", "euro", "usd", "php", "jpy", "forex", "travel", "fx", "cost")),
        ToolInfo(GearTool.TIMER, ToolCategory.TIME, "Several timers that ring even when the app is closed", listOf("countdown", "alarm", "cooking", "egg", "tea", "remind", "minutes")),
        ToolInfo(GearTool.STOPWATCH, ToolCategory.TIME, "Time something, with laps", listOf("laps", "lap", "race", "split", "timing", "run")),
        ToolInfo(GearTool.FLASHLIGHT, ToolCategory.LIGHT, "The phone's torch, a screen light and an SOS signal", listOf("torch", "light", "lamp", "night", "dark", "sos", "red", "screen light")),
        ToolInfo(GearTool.PICKER, ToolCategory.CHOOSE, "Pick from your list, with weights", listOf("choose", "decide", "random", "list", "dinner", "who", "draw", "shuffle", "lottery", "eat")),
        ToolInfo(GearTool.EVERYDAY, ToolCategory.CALCULATE, "Tip and split, percent, sale and tax, a loan, savings, trip cost", listOf("tip", "split", "bill", "restaurant", "discount", "sale", "tax", "vat", "loan", "mortgage", "interest", "savings", "fuel", "gas", "petrol", "trip", "percent")),
        ToolInfo(GearTool.WORLD_CLOCK, ToolCategory.TIME, "The time in other places, a time converted, and date sums", listOf("time zone", "timezone", "zone", "tokyo", "london", "new york", "utc", "gmt", "date", "days", "countdown", "difference", "jet lag", "meeting")),
        ToolInfo(GearTool.COMPASS, ToolCategory.MEASURE, "Which way you are facing, from the magnetic sensor", listOf("direction", "north", "heading", "bearing", "magnetic", "navigate", "sensor")),
        ToolInfo(GearTool.LEVEL, ToolCategory.MEASURE, "How far from flat the phone lies, in degrees", listOf("flat", "tilt", "spirit level", "bubble", "angle", "incline", "slope", "sensor", "shelf", "picture")),
        ToolInfo(GearTool.BAROMETER, ToolCategory.MEASURE, "Air pressure and a rough height", listOf("pressure", "weather", "altitude", "height", "hpa", "elevation", "sensor", "storm")),
        ToolInfo(GearTool.DEVICE, ToolCategory.MEASURE, "What this phone can do for Gear", listOf("sensors", "hardware", "capabilities", "permissions", "check", "support", "gps", "nfc", "camera", "battery")),
        ToolInfo(GearTool.WAYPOINTS, ToolCategory.MEASURE, "Save where you are and see how far and which way it is", listOf("gps", "location", "place", "park", "car", "parked", "find", "way back", "coordinates", "latitude", "map", "hike", "trail")),
        ToolInfo(GearTool.SOUND, ToolCategory.MEASURE, "How loud it is around you, and the pitch of a sound", listOf("noise", "decibel", "db", "loud", "microphone", "mic", "pitch", "tuner", "note", "frequency", "hearing")),
        ToolInfo(GearTool.QR, ToolCategory.MEASURE, "Make a QR code from text, or scan one with the camera", listOf("qr", "barcode", "scan", "scanner", "camera", "wifi", "link", "share", "code")),
        ToolInfo(GearTool.NETWORK, ToolCategory.MEASURE, "How you are connected and how quickly the internet answers", listOf("wifi", "wi-fi", "internet", "connection", "speed", "ping", "latency", "online", "signal", "mobile data", "network")),
        ToolInfo(GearTool.COIN, ToolCategory.CHOOSE, "Heads or tails, and dice", listOf("flip", "toss", "heads", "tails", "dice", "die", "roll", "d20", "d6", "random")),
    )

    fun info(tool: GearTool): ToolInfo = all.first { it.tool == tool }

    /**
     * The tools matching [query] (name, keywords, category, one wrong letter in a longer word), best first; favourites and recents win ties. With no
     * query, [favorites] then [recents] then the rest in the catalogue's order.
     */
    fun search(query: String, favorites: List<String> = emptyList(), recents: List<String> = emptyList()): List<ToolInfo> {
        val q = query.trim().lowercase()
        fun bonus(t: ToolInfo) = (if (t.tool.name in favorites) 10 else 0) + (if (t.tool.name in recents) 5 - recents.indexOf(t.tool.name).coerceAtMost(4) else 0)
        if (q.isEmpty()) return all.sortedByDescending { bonus(it) }
        return all.map { t ->
            val names = listOf(t.tool.label.lowercase(), t.category.label.lowercase()) + t.keywords
            val score = names.maxOf { n -> match(q, n) }
            val desc = if (t.description.lowercase().contains(q) && q.length >= 3) 15 else 0
            (maxOf(score, desc) * 100 + bonus(t)) to t
        }.filter { it.first >= 100 }.sortedByDescending { it.first }.map { it.second }
    }

    private fun match(q: String, n: String): Int = when {
        n == q -> 100
        n.startsWith(q) -> 60
        q.length >= 2 && n.contains(q) -> 40
        q.length >= 4 && n.split(' ').any { w -> w.length >= 4 && withinOne(q, w) } -> 20
        else -> 0
    }

    private fun withinOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
