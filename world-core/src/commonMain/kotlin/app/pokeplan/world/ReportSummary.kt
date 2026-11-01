package app.pokeplan.world

/**
 * What the teams brought back since the player last looked, built from the events of one or more settles (a report, not a record: it is
 * never saved, the shared reward log stays the one record). Rewards are in three kinds so a chance is never read as a promise:
 * **every floor** ([xp], [poke]), **milestones** (a fixed reward for reaching a floor the first time) and **chance finds** ([berries],
 * [treasure], wild Pokémon in [finds]). Berries and Poké are already in the Bag; [finds] are waiting to be recruited.
 */
data class ReportSummary(
    val floors: Int = 0,
    val byDungeon: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    val poke: Int = 0,
    val berries: Int = 0,
    /** Treasure boxes found, by tier (1 plain, 2 silver, 3 gold). */
    val treasure: Map<Int, Int> = emptyMap(),
    val milestones: List<Pair<String, MilestoneReward>> = emptyList(),
    /** Wild species found (they wait in Finds). */
    val finds: List<Int> = emptyList(),
    val cleared: List<String> = emptyList(),
    val jobsDone: List<String> = emptyList(),
    val guardianFaced: Boolean = false,
) {
    val isEmpty: Boolean get() = floors == 0 && milestones.isEmpty() && finds.isEmpty() && jobsDone.isEmpty() && !guardianFaced

    operator fun plus(o: ReportSummary): ReportSummary = ReportSummary(
        floors + o.floors, (byDungeon.keys + o.byDungeon.keys).associateWith { (byDungeon[it] ?: 0) + (o.byDungeon[it] ?: 0) },
        xp + o.xp, poke + o.poke, berries + o.berries,
        (treasure.keys + o.treasure.keys).associateWith { (treasure[it] ?: 0) + (o.treasure[it] ?: 0) },
        milestones + o.milestones, finds + o.finds, cleared + o.cleared, jobsDone + o.jobsDone, guardianFaced || o.guardianFaced,
    )

    companion object {
        /** The summary of [report]: the events of every team, with the Exploration XP the settle gave ([xpGained], from the data before and after). */
        fun of(report: WorldReport, xpGained: Int): ReportSummary {
            var s = ReportSummary(xp = xpGained)
            for (e in report.events) s = when (e) {
                is WorldEvent.FloorCleared -> s.copy(floors = s.floors + 1, byDungeon = s.byDungeon + (e.dungeonId to (s.byDungeon[e.dungeonId] ?: 0) + 1))
                is WorldEvent.Poke -> s.copy(poke = s.poke + e.amount)
                is WorldEvent.Item -> s.copy(berries = s.berries + e.count)
                is WorldEvent.Treasure -> s.copy(treasure = s.treasure + (e.tier to (s.treasure[e.tier] ?: 0) + 1))
                is WorldEvent.Milestone -> s.copy(milestones = s.milestones + (e.dungeonId to e.reward))
                is WorldEvent.Found -> s.copy(finds = s.finds + e.speciesId)
                is WorldEvent.Cleared -> s.copy(cleared = s.cleared + e.dungeonId)
                is WorldEvent.MissionDone -> s.copy(jobsDone = s.jobsDone + e.missionId)
                WorldEvent.GuardianFaced, is WorldEvent.LayerGuardianFaced -> s.copy(guardianFaced = true)
            }
            return s
        }
    }
}
