package app.pokeplan.world

data class NextStep(val title: String, val reason: String, val dungeonId: String? = null)

enum class PrimaryKind { REVIEW_FINDS, CLAIM_JOB, EXPLORE, CONTINUE }

data class PrimaryAction(val kind: PrimaryKind, val title: String, val reason: String, val dungeonId: String? = null)

object WorldGuide {
    fun primaryAction(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): PrimaryAction {
        val finds = data.finds.size
        if (finds > 0) return PrimaryAction(PrimaryKind.REVIEW_FINDS, "Recruit ${if (finds == 1) "a new friend" else "$finds new friends"}", "${if (finds == 1) "A Pokémon is" else "$finds Pokémon are"} waiting to join you. They stay in Finds until you decide.")
        val done = data.missions.count { it.done }
        if (done > 0) return PrimaryAction(PrimaryKind.CLAIM_JOB, "Report back: ${if (done == 1) "a job is done" else "$done jobs are done"}", "Thanks and a reward are waiting on the mission board.")
        if (data.slots().any { it.expedition == null }) {
            val next = nextStep(data, layer)
            return PrimaryAction(PrimaryKind.EXPLORE, next.title, next.reason, next.dungeonId)
        }
        return PrimaryAction(PrimaryKind.CONTINUE, "Your teams are exploring", "Every team is out. Look in on them, or come back later: they keep going while you are away, up to 12 hours.")
    }

    fun lockReasons(data: WorldData, dungeon: DungeonDef): List<String> = buildList {
        if (data.rank < dungeon.needs) add("Needs ${dungeon.needs.label} rank: ${dungeon.needs.points - data.xp} more Exploration XP (you have ${data.xp} of ${dungeon.needs.points}).")
        val layer = dungeon.layer
        if (Story.opened(data, layer) < dungeon.chapter) {
            val chapter = Story.chaptersOf(layer)[dungeon.chapter - 1]
            val cleared = Story.dungeonsCleared(data, layer)
            val parts = buildList {
                if (data.rank < chapter.needsRank) add("${chapter.needsRank.label} rank")
                if (cleared < chapter.needsDungeons) add("${chapter.needsDungeons} different ${layer.label} dungeons cleared (you have $cleared)")
            }
            add("Needs story chapter ${dungeon.chapter}, ${chapter.title}: ${parts.joinToString(" and ")}.")
        }
        if (dungeon.postStory && !data.layerDone(layer)) add("Opens when the story of ${layer.label} is complete.")
    }

    fun nextStep(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): NextStep {
        val dungeons = Dungeons.of(layer)
        fun isOpen(d: DungeonDef) = data.rank >= d.needs && Story.opened(data, layer) >= d.chapter && !(d.postStory && !data.layerDone(layer))
        dungeons.firstOrNull { isOpen(it) && (data.clears[it.id] ?: 0) == 0 }?.let { d ->
            return NextStep("Explore ${d.name}", if (dungeons.none { (data.clears[it.id] ?: 0) > 0 }) "It is the first place to explore." else "It is open at your rank and you have not cleared it yet.", d.id)
        }
        dungeons.firstOrNull { !isOpen(it) }?.let { d ->
            val reason = when {
                d.postStory && !data.layerDone(layer) -> "It opens when the story is complete."
                data.rank < d.needs -> "Reach ${d.needs.label} rank for ${d.name}: ${d.needs.points - data.xp} more Exploration XP."
                else -> {
                    val chapter = Story.chaptersOf(layer)[d.chapter - 1]
                    val cleared = Story.dungeonsCleared(data, layer)
                    "${d.name} needs story chapter ${d.chapter}, ${chapter.title}: clear ${(chapter.needsDungeons - cleared).coerceAtLeast(1)} more different dungeon${if (chapter.needsDungeons - cleared > 1) "s" else ""}."
                }
            }
            return NextStep("Work towards ${d.name}", reason)
        }
        return NextStep("Everything is cleared", "Keep exploring for finds, research, jobs and treasure.")
    }
}
