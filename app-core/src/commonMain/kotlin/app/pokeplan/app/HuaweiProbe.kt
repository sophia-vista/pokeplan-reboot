package app.pokeplan.app

/**
 * Reading Huawei Health on the phone, probe stage (INTEGRATIONS_AND_PRIVACY.md 5.2.2): what the Android screen reader and notification listener saw, held in
 * memory only. Nothing here is saved, paid or sent anywhere; the parsers that turn it into HEAL records come once a real sample has been captured.
 * Pure, so the Android services only fill it in.
 */
object HuaweiHealth {
    /** The only package either reader looks at. */
    const val PACKAGE = "com.huawei.health"

    /** The provenance provider of the records the parsers will save. */
    const val PROVIDER = "huawei-screen"

    /** A day's step count is never above this; anything larger is not a step count. */
    const val MAX_STEPS = 200_000

    private val stepsAfter = Regex("""(\d{1,3}(?:[,.  ]\d{3})+|\d+)\s*steps?\b""", RegexOption.IGNORE_CASE)
    private val stepsBefore = Regex("""\bsteps?\s*[:\-]?\s*(\d{1,3}(?:[,.  ]\d{3})+|\d+)""", RegexOption.IGNORE_CASE)

    /** The step count in a notification's or a screen's text ("8,432 steps", "Steps: 8432"), or null when there is none or it cannot be one. Missing is never zero. */
    fun stepsIn(vararg texts: String?): Int? {
        for (t in texts) {
            if (t.isNullOrBlank()) continue
            val m = stepsAfter.find(t) ?: stepsBefore.find(t) ?: continue
            val n = m.groupValues[1].filter { it.isDigit() }.toIntOrNull() ?: continue
            if (n in 0..MAX_STEPS) return n
        }
        return null
    }

    /** [s] with every digit replaced by `#`: the layout of a capture without its values, the only form that leaves the phone's screen. */
    fun hideNumbers(s: String): String = buildString(s.length) { s.forEach { append(if (it.isDigit()) '#' else it) } }
}

/** One element of another app's screen as the accessibility service saw it (no Android object). [viewId] is the resource name, [type] the short class name. */
data class ScreenNode(
    val text: String = "",
    val description: String = "",
    val viewId: String = "",
    val type: String = "",
    val depth: Int = 0,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
) {
    /** One line of the capture: the depth as indent, what it says, then its id and kind. */
    fun line(): String = buildString {
        append("  ".repeat(depth.coerceAtMost(20)))
        val words = listOf(text, description.takeIf { it != text } ?: "").filter { it.isNotBlank() }.joinToString(" | ")
        append(words.ifBlank { "-" })
        val tags = listOfNotNull(viewId.substringAfter(":id/").takeIf { it.isNotBlank() }, type.takeIf { it.isNotBlank() },
            "tap".takeIf { clickable }, "scroll".takeIf { scrollable })
        if (tags.isNotEmpty()) append("  [").append(tags.joinToString(", ")).append(']')
    }
}

/** One captured page of Huawei Health: when, the window's title, and its elements in tree order. */
data class ScreenCapture(val atMillis: Long, val title: String, val nodes: List<ScreenNode>) {
    /** What makes two captures the same page in the same state (the text, ids and order, not the time). */
    val signature: String get() = nodes.joinToString("\u0001") { it.text + "\u0002" + it.description + "\u0002" + it.viewId }
}

/** One Huawei Health notification: its visible fields, and the step count found in them (null when none). [customLayout] is true when it draws its own view and gives no text. */
data class NotificationCapture(val atMillis: Long, val title: String, val text: String, val subText: String = "", val bigText: String = "", val customLayout: Boolean = false) {
    val steps: Int? get() = HuaweiHealth.stepsIn(title, text, bigText, subText)
}

/** Everything the probe saw since it was last cleared. */
data class HuaweiSeen(val screens: List<ScreenCapture> = emptyList(), val notifications: List<NotificationCapture> = emptyList()) {
    val latestSteps: Int? get() = notifications.lastOrNull { it.steps != null }?.steps

    /** The Health page as the latest captures show it, each field from the newest page that had it. */
    val home: HuaweiHome get() = screens.fold(HuaweiHome()) { acc, s -> acc.then(HuaweiHome.read(s)) }
    val isEmpty: Boolean get() = screens.isEmpty() && notifications.isEmpty()

    /**
     * The captures as text, for the owner to share. Every digit of what Huawei Health shows (texts, descriptions, titles, notification fields) is hidden
     * ([HuaweiHealth.hideNumbers]); view ids and the probe's own headings keep theirs. There is no version with the numbers.
     */
    fun sample(version: String = ""): String = buildString {
        fun h(s: String) = HuaweiHealth.hideNumbers(s)
        append("PokéLife Huawei Health probe").append(if (version.isNotBlank()) " ($version)" else "").append('\n')
        screens.forEachIndexed { i, s ->
            append("\n== Screen ${i + 1}: ${h(s.title).ifBlank { "untitled" }} (${s.nodes.size} elements) ==\n")
            s.nodes.forEach { append(it.copy(text = h(it.text), description = h(it.description)).line()).append('\n') }
        }
        notifications.forEachIndexed { i, n ->
            append("\n== Notification ${i + 1}${if (n.customLayout) " (custom layout)" else ""} ==\n")
            listOf("title" to n.title, "text" to n.text, "sub" to n.subText, "big" to n.bigText).filter { it.second.isNotBlank() }
                .forEach { (k, v) -> append(k).append(": ").append(h(v)).append('\n') }
            append("steps found: ").append(if (n.steps != null) "yes" else "no").append('\n')
        }
    }
}

/** Collects captures in memory: the same page twice is kept once, and only the newest [maxScreens] pages and [maxNotifications] notifications stay. */
class HuaweiProbe(private val maxScreens: Int = 40, private val maxNotifications: Int = 5) {
    var seen: HuaweiSeen = HuaweiSeen()
        private set

    /** Adds [capture] unless it is empty or the same page as one already kept; returns whether it was new. */
    fun addScreen(capture: ScreenCapture): Boolean {
        if (capture.nodes.none { it.text.isNotBlank() || it.description.isNotBlank() }) return false
        val sig = capture.signature
        if (seen.screens.any { it.signature == sig }) return false
        seen = seen.copy(screens = (seen.screens + capture).takeLast(maxScreens))
        return true
    }

    /** Adds [n] unless it repeats the newest one word for word (an ongoing step notification updates often). */
    fun addNotification(n: NotificationCapture) {
        val last = seen.notifications.lastOrNull()
        val list = if (last != null && last.title == n.title && last.text == n.text && last.subText == n.subText && last.bigText == n.bigText) seen.notifications.dropLast(1) else seen.notifications
        seen = seen.copy(notifications = (list + n).takeLast(maxNotifications))
    }

    fun clear() { seen = HuaweiSeen() }
}

/**
 * What Huawei Health's own Health page shows, read from a capture (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Each field is null when the page did not show it:
 * missing is never zero. The page gives no times (sleep is a length and a get-up time, weight has no date, exercise is the day's distance), so nothing here
 * becomes a HEAL record by itself; the detail pages carry the times.
 */
data class HuaweiHome(
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
    /** The get-up time on the summary, as minutes after midnight. */
    val getUpMinute: Int? = null,
    val weight: Double? = null,
    val weightUnit: String = "",
    val heartRate: Int? = null,
    val exerciseKm: Double? = null,
) {
    val isEmpty: Boolean get() = this == HuaweiHome()

    /** [other]'s fields where it has them, this one's where it does not. */
    fun then(other: HuaweiHome) = HuaweiHome(
        other.steps ?: steps, other.sleepMinutes ?: sleepMinutes, other.getUpMinute ?: getUpMinute,
        other.weight ?: weight, if (other.weight != null) other.weightUnit else weightUnit, other.heartRate ?: heartRate, other.exerciseKm ?: exerciseKm,
    )

    companion object {
        private fun id(n: ScreenNode) = n.viewId.substringAfter(":id/")
        private val getUp = Regex("""^(\d{1,2}):(\d{2}),?\s*Get-up time$""", RegexOption.IGNORE_CASE)
        private val hoursMinutes = Regex("""^\s*(?:(\d{1,2})\s*h)?\s*(?:(\d{1,2})\s*min)?\s*$""", RegexOption.IGNORE_CASE)
        private val bpm = Regex("""(\d{2,3})\s*bpm""", RegexOption.IGNORE_CASE)
        private fun number(s: String) = s.replace(",", "").trim().toDoubleOrNull()

        /** Reads the Health page's summary and cards from [capture]; an empty [HuaweiHome] when it is not that page. */
        fun read(capture: ScreenCapture): HuaweiHome {
            var home = HuaweiHome()
            var card = ""
            var data = ""
            fun closeCard() {
                if (card.isEmpty()) return
                home = when (card) {
                    "Sleep" -> hoursMinutes.matchEntire(data)?.takeIf { it.groupValues[1].isNotEmpty() || it.groupValues[2].isNotEmpty() }?.let { m ->
                        val min = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
                        if (min in 1..24 * 60) home.copy(sleepMinutes = min) else null
                    } ?: home
                    "Heart" -> bpm.find(data)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 25..250 }?.let { home.copy(heartRate = it) } ?: home
                    "Exercise records" -> number(data)?.takeIf { it in 0.0..500.0 }?.let { home.copy(exerciseKm = it) } ?: home
                    else -> home
                }
                card = ""; data = ""
            }
            for (n in capture.nodes) {
                when (id(n)) {
                    "function_set_items_title" -> { closeCard(); card = n.text.trim() }
                    "function_set_items_data" -> if (card.isNotEmpty()) data = n.text.trim()
                    "function_set_items_unit" -> if (card == "Weight") {
                        val unit = n.text.trim()
                        number(data)?.takeIf { unit == "kg" || unit == "lb" }?.let { home = home.copy(weight = it, weightUnit = unit) }
                    }
                }
                getUp.matchEntire(n.description.ifBlank { n.text }.trim())?.let { m ->
                    val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
                    if (h in 0..23 && mi in 0..59) home = home.copy(getUpMinute = h * 60 + mi)
                }
                if (id(n) == "time_strength_layout_middle" || n.text.trim().endsWith(" steps", ignoreCase = true)) HuaweiHealth.stepsIn(n.text, n.description)?.let { home = home.copy(steps = it) }
            }
            closeCard()
            return home
        }
    }
}
