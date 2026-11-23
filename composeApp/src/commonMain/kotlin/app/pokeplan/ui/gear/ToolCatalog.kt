package app.pokeplan.ui.gear

import app.pokeplan.core.pokemon.GearTool
import kotlin.math.abs

/**
 * The shelf a tool lives on. Grouped rather than flat so the Gear menu reads as a few short names
 * ("Everyday", "Time", "Sensors") instead of one long list of eighteen tools. Labels stay one word
 * so they still fit a category key at fontScale 1.3.
 */
enum class ToolCategory(val label: String) {
    EVERYDAY("Everyday"),
    NUMBERS("Numbers"),
    CONVERT("Convert"),
    MONEY("Money"),
    TIME("Time"),
    SENSORS("Sensors"),
    UTILITIES("Utilities"),
}

class ToolInfo(val tool: GearTool, val category: ToolCategory, val description: String, val keywords: List<String>)

/** One shelf of the catalogue, in catalogue order. */
data class ToolGroup(val category: ToolCategory, val tools: List<ToolInfo>)

object ToolCatalog {
    /** How many recent tools the menu shows beside the shelves (favourites have their own strip). */
    const val RECENTS_SHOWN = 3

    val all: List<ToolInfo> = listOf(
        ToolInfo(GearTool.CALCULATOR, ToolCategory.NUMBERS, "Sums with brackets and percentages, scientific functions, memory", listOf("calc", "math", "maths", "percent", "percentage", "tip", "sum", "add", "plus", "multiply", "sin", "cos", "sqrt", "scientific")),
        ToolInfo(GearTool.EVERYDAY, ToolCategory.EVERYDAY, "Tip and split, percent, sale and tax, a loan, savings, trip cost", listOf("tip", "split", "bill", "restaurant", "discount", "sale", "tax", "vat", "loan", "mortgage", "interest", "savings", "fuel", "gas", "petrol", "trip", "percent")),
        ToolInfo(GearTool.CURRENCY, ToolCategory.MONEY, "Money at today's rates, offline, with travel costs", listOf("money", "exchange", "convert", "converter", "rate", "rates", "yen", "peso", "dollar", "euro", "usd", "php", "jpy", "forex", "travel", "fx", "cost")),
        ToolInfo(GearTool.UNITS, ToolCategory.CONVERT, "Lengths, weights, cooking, temperature, data and more", listOf("convert", "converter", "lbs", "pounds", "kg", "miles", "km", "feet", "inches", "temperature", "celsius", "fahrenheit", "cup", "oven", "weight", "length", "area", "volume", "speed", "mbps", "bytes")),
        ToolInfo(GearTool.TIMER, ToolCategory.TIME, "Several timers that ring even when the app is closed", listOf("countdown", "alarm", "cooking", "egg", "tea", "remind", "minutes")),
        ToolInfo(GearTool.STOPWATCH, ToolCategory.TIME, "Time something, with laps", listOf("laps", "lap", "race", "split", "timing", "run")),
        ToolInfo(GearTool.WORLD_CLOCK, ToolCategory.TIME, "The time in other places, a time converted, and date sums", listOf("time zone", "timezone", "zone", "tokyo", "london", "new york", "utc", "gmt", "date", "days", "countdown", "difference", "jet lag", "meeting")),
        ToolInfo(GearTool.FLASHLIGHT, ToolCategory.UTILITIES, "The phone's torch, a screen light and an SOS signal", listOf("torch", "light", "lamp", "night", "dark", "sos", "red", "screen light")),
        ToolInfo(GearTool.PICKER, ToolCategory.UTILITIES, "Pick from your list, with weights", listOf("choose", "decide", "random", "list", "dinner", "who", "draw", "shuffle", "lottery", "eat")),
        ToolInfo(GearTool.COIN, ToolCategory.UTILITIES, "Heads or tails, and dice", listOf("flip", "toss", "heads", "tails", "dice", "die", "roll", "d20", "d6", "random")),
        ToolInfo(GearTool.DEVICE, ToolCategory.UTILITIES, "What this phone can do for Gear", listOf("sensors", "hardware", "capabilities", "permissions", "check", "support", "gps", "nfc", "camera", "battery")),
        ToolInfo(GearTool.WAYPOINTS, ToolCategory.UTILITIES, "Save where you are and see how far and which way it is", listOf("gps", "location", "place", "park", "car", "parked", "find", "way back", "coordinates", "latitude", "map", "hike", "trail")),
        ToolInfo(GearTool.QR, ToolCategory.UTILITIES, "Make a QR code from text, or scan one with the camera", listOf("qr", "barcode", "scan", "scanner", "camera", "wifi", "link", "share", "code")),
        ToolInfo(GearTool.NETWORK, ToolCategory.UTILITIES, "How you are connected and how quickly the internet answers", listOf("wifi", "wi-fi", "internet", "connection", "speed", "ping", "latency", "online", "signal", "mobile data", "network")),
        ToolInfo(GearTool.COMPASS, ToolCategory.SENSORS, "Which way you are facing, from the magnetic sensor", listOf("direction", "north", "heading", "bearing", "magnetic", "navigate", "sensor")),
        ToolInfo(GearTool.LEVEL, ToolCategory.SENSORS, "How far from flat the phone lies, in degrees", listOf("flat", "tilt", "spirit level", "bubble", "angle", "incline", "slope", "sensor", "shelf", "picture")),
        ToolInfo(GearTool.BAROMETER, ToolCategory.SENSORS, "Air pressure and a rough height", listOf("pressure", "weather", "altitude", "height", "hpa", "elevation", "sensor", "storm")),
        ToolInfo(GearTool.SOUND, ToolCategory.SENSORS, "How loud it is around you, and the pitch of a sound", listOf("noise", "decibel", "db", "loud", "microphone", "mic", "pitch", "tuner", "note", "frequency", "hearing")),
    )

    fun info(tool: GearTool): ToolInfo = all.first { it.tool == tool }

    /** The catalogue as shelves, in [ToolCategory] order; [category] narrows it to one shelf. Empty shelves are left out. */
    fun groups(category: ToolCategory? = null): List<ToolGroup> = ToolCategory.entries.mapNotNull { c ->
        if (category != null && c != category) return@mapNotNull null
        all.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { ToolGroup(c, it) }
    }

    /** Favourites in the order the person put them in; unknown saved names are skipped. */
    fun favorites(names: List<String>): List<ToolInfo> = names.mapNotNull { name -> all.firstOrNull { it.tool.name == name } }

    /** Recently used tools newest first, without the favourites (their own strip already shows them). */
    fun recents(names: List<String>, favorites: List<String> = emptyList(), limit: Int = RECENTS_SHOWN): List<ToolInfo> =
        names.filter { it !in favorites }.mapNotNull { name -> all.firstOrNull { it.tool.name == name } }.distinctBy { it.tool }.take(limit)

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
