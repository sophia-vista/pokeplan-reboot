package app.pokeplan.world

/** What to do next in World, and why. [dungeonId] is the dungeon to go to when the step is to explore one that is open. */
data class NextStep(val title: String, val reason: String, val dungeonId: String? = null)

/**
 * The World landing's one suggestion (docs/pokelife/MODULES/WORLD.md section 14, "show the next recommended destination and why"): the first open dungeon
 * not yet cleared, else what the nearest closed dungeon is waiting for, else a line about what an all-cleared World still offers.
 * Plain data in, plain words out, so it is tested without a screen.
 */
/** What the World landing's one big button does. */
enum class PrimaryKind { REVIEW_FINDS, CLAIM_JOB, EXPLORE, CONTINUE }

/** The one thing worth doing now ([title], why, and the dungeon for [PrimaryKind.EXPLORE]). */
data class PrimaryAction(val kind: PrimaryKind, val title: String, val reason: String, val dungeonId: String? = null)

object WorldGuide {
    /**
     * The landing's primary action, in this order: Pokémon waiting to be recruited, a finished job to claim, a free team to send to the
     * next dungeon of [layer] (see [nextStep]), else everything is out exploring (go and look). Plain data in, words out.
     */
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

    /**
     * Every requirement of [dungeon] the player has not met yet, each in words with how far along it is (empty when the dungeon is open):
     * the rank, the story chapter of its layer (its own rank and how many different dungeons of the layer are cleared) and, for a
     * place after the story, the story itself. All are listed, not only the first.
     */
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
