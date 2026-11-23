package app.pokeplan.ui.screens

/** What a live line's key opens, and which module's Home card carries it; the screen owns the routing, the selection stays pure. */
enum class HomeLiveAction(val module: Module) {
    WORLD_REPORT(Module.WORLD), WORLD_BOARD(Module.WORLD), WORLD_FINDS(Module.WORLD),
    MINT_OUTBREAK(Module.MINT), CARE_DREAM(Module.CARE), LINK_SAFARI(Module.LINK), PLAN_CALENDAR(Module.PLAN),
}

/** One thing that is live now and will not wait: a short line and the one key that deals with it. */
data class HomeLiveLine(val id: String, val text: String, val action: String, val urgency: Int, val kind: HomeLiveAction) {
    /** The module whose Home card shows this line. */
    val module: Module get() = kind.module
}

/**
 * The save-derived facts the module cards' live lines read, gathered once and flattened to counts and flags so the
 * selection below is a pure function that can be tested without a store, a clock or a device.
 */
data class HomeLive(
    val reportFloors: Int = 0,
    val floorDue: Boolean = false,
    val floorDueWhere: String = "",
    val jobsReady: Int = 0,
    val finds: Int = 0,
    val outbreaks: Int = 0,
    val dreamers: Int = 0,
    val safariVisits: Int = 0,
    val companionCandies: Int = 0,
    val companionTitle: String = "",
)

/**
 * Home's time-sensitive lines. Only things that are actionable now and will expire are listed, one line
 * each, most urgent first, and nothing at all when the save has nothing live: it is not a dashboard.
 * Each line belongs to one module and is shown on that module's card, under its main step.
 */
object HomeOpportunities {
    fun select(live: HomeLive): List<HomeLiveLine> {
        val lines = buildList {
            if (live.reportFloors > 0) add(HomeLiveLine("world-report", "Expedition report: ${count(live.reportFloors, "floor", "floors")} climbed", "Open", 5, HomeLiveAction.WORLD_REPORT))
            if (live.jobsReady > 0) add(HomeLiveLine("world-jobs", "${count(live.jobsReady, "job", "jobs")} ready to report", "Report", 5, HomeLiveAction.WORLD_BOARD))
            if (live.outbreaks > 0) add(HomeLiveLine("mint-outbreak", if (live.outbreaks == 1) "An outbreak is ready to claim" else "${count(live.outbreaks, "outbreak", "outbreaks")} ready to claim", "Claim", 4, HomeLiveAction.MINT_OUTBREAK))
            if (live.floorDue) {
                val where = live.floorDueWhere.takeIf { it.isNotBlank() }?.let { " in $it" } ?: ""
                add(HomeLiveLine("world-floor", "A new floor is waiting$where", "Open", 3, HomeLiveAction.WORLD_REPORT))
            }
            if (live.finds > 0) add(HomeLiveLine("world-finds", "${count(live.finds, "Pokémon is", "Pokémon are")} waiting in Finds", "Meet", 3, HomeLiveAction.WORLD_FINDS))
            if (live.dreamers > 0) add(HomeLiveLine("care-dream", "${count(live.dreamers, "dreamer is", "dreamers are")} waiting to visit", "Visit", 2, HomeLiveAction.CARE_DREAM))
            if (live.safariVisits > 0) add(HomeLiveLine("link-safari", "${count(live.safariVisits, "Safari visit is", "Safari visits are")} open", "Visit", 2, HomeLiveAction.LINK_SAFARI))
            if (live.companionCandies > 0) {
                val who = live.companionTitle.takeIf { it.isNotBlank() }?.let { "$it: " } ?: ""
                add(HomeLiveLine("plan-candies", "${who}${count(live.companionCandies, "candy", "candies")} waiting", "Open", 1, HomeLiveAction.PLAN_CALENDAR))
            }
        }
        return lines.sortedByDescending { it.urgency }
    }

    /** The lines grouped by the module card that carries them, each group most urgent first. */
    fun byModule(live: HomeLive): Map<Module, List<HomeLiveLine>> = select(live).groupBy { it.module }

    /** How loudly a card with this line should float above the quiet ones (the same 0 to 3 scale as the card's own step). */
    fun severityOf(line: HomeLiveLine): Int = when {
        line.urgency >= 4 -> 3
        line.urgency >= 3 -> 2
        else -> 1
    }

    private fun count(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"
}
