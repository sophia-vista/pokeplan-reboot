package app.pokeplan.app.heal

/**
 * Which controls of Huawei Health's pages the reader found it could tap or scroll (INTEGRATIONS_AND_PRIVACY.md 5.2.2, debug aid), worked out from the captures
 * alone. Pure. It names ids, classes and the words on the page's own cards, with digits hidden by the caller (`HuaweiSeen.sample`) and in [lines].
 */
object HuaweiControls {
    private fun id(n: ScreenNode) = n.viewId.substringAfter(":id/")
    private fun name(n: ScreenNode) = id(n).ifBlank { n.type }

    /** Cards the Health page lists, and whether the walk opens them. */
    private val WALKED = HuaweiWalk.CARDS.flatMap { it.titles }.toSet()
    private val OTHERS = listOf("Health Clovers", "Skin temperature", "Activity rings", "Emotional wellbeing", "SpO2", "Cycle Calendar")

    /** One entry per card title element seen on any page: the card, whether the walk opens it, and what takes the tap ("" when nothing). */
    data class CardCheck(val title: String, val walked: Boolean, val tapVia: String)

    fun cards(seen: HuaweiSeen): List<CardCheck> {
        val found = LinkedHashMap<String, String>()
        for (s in seen.screens) for (n in s.nodes) if (id(n) == "function_set_items_title") {
            val t = n.text.trim().let { if (it == "SpO₂") "SpO2" else it }
            if (t.isNotEmpty() && (found[t].isNullOrEmpty())) found[t] = n.tapVia
        }
        return found.map { (t, via) -> CardCheck(t, t in WALKED, via) }
    }

    /** The report as text lines: per page the tappable and scrollable controls, then the card check. */
    fun lines(seen: HuaweiSeen): List<String> = buildList {
        if (seen.screens.isEmpty()) { add("No pages seen yet. Read Huawei Health first."); return@buildList }
        seen.screens.forEachIndexed { i, s ->
            val taps = s.nodes.filter { it.clickable }
            val labelled = s.nodes.filter { !it.clickable && it.tapVia.isNotBlank() && (it.text.isNotBlank() || it.description.isNotBlank()) }
            val scrollers = s.nodes.filter { it.scrollable }
            add("Page ${i + 1} ${s.title.ifBlank { "untitled" }}: ${taps.size} tappable, ${labelled.size} labels reach a tap, ${scrollers.size} scrollable")
            scrollers.forEach { add("  scroll  ${name(it)}") }
            taps.take(MAX_LISTED).forEach { add("  tap  ${name(it)}${it.text.takeIf { t -> t.isNotBlank() }?.let { t -> "  \"$t\"" }.orEmpty()}") }
        }
        val steps = seen.screens.flatMap { it.nodes }.filter { id(it) == HuaweiWalk.STEPS_ID }
        if (steps.isNotEmpty()) add("Steps summary: ${if (steps.any { it.tapVia.isNotBlank() }) "tap via ${steps.first { it.tapVia.isNotBlank() }.tapVia}" else "NOT tappable"}")
        val cards = cards(seen)
        if (cards.isNotEmpty()) {
            add("Cards:")
            cards.forEach { add("  ${if (it.walked) "WALK " else "other"}  ${it.title}: ${if (it.tapVia.isBlank()) "NOT tappable" else "tap via ${it.tapVia}"}") }
        }
        val missing = HuaweiWalk.CARDS.map { it.title }.filter { t -> cards.none { it.title == t } }
        if (missing.isNotEmpty()) add("Not seen on any page: ${missing.joinToString()}")
    }

    /** The "== Controls ==" section of the sample. */
    fun summary(seen: HuaweiSeen): String = buildString {
        append("== Controls ==\n")
        lines(seen).forEach { append(HuaweiHealth.hideNumbers(it)).append('\n') }
    }

    private const val MAX_LISTED = 25
}
