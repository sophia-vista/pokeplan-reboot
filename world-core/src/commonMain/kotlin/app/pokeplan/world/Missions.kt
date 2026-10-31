package app.pokeplan.world

import app.pokeplan.core.pokemon.Items
import kotlinx.serialization.Serializable

/** What a mission asks (docs/world.md, section 8). Escort, Deliver and Defeat wait for the mechanics they need. */
enum class MissionKind(val label: String) { EXPLORE("Explore"), COLLECT("Find items"), RESCUE("Rescue"), DELIVER("Deliver") }

/**
 * A job on the board. [EXPLORE] is done when any team reaches [floor] of [dungeonId] while it is accepted; [COLLECT] when
 * [count] berries have been found anywhere; [RESCUE] when a team reaches [floor] of [dungeonId], and the lost Pokémon
 * ([rescued], a species id) then waits among the Finds. A job that is [once] never comes back; the others are repeatable.
 * Rewards are paid when the player claims the finished job. Numbers are *(tune)*.
 */
data class MissionDef(
    val id: String, val title: String, val story: String, val kind: MissionKind, val needs: ExplorerRank, val once: Boolean,
    val dungeonId: String? = null, val floor: Int = 0, val count: Int = 0, val rescued: Int? = null,
    val poke: Int, val xp: Int, val itemId: String? = null, val itemCount: Int = 0,
    /** A [DELIVER] job reaches [floor] like an Explore job, and is claimed by handing over [giveCount] of [give] from the Bag. */
    val give: String? = null, val giveCount: Int = 0,
    /** Days before the job can be claimed again: 1 for a daily job, 7 for the ordinary jobs (so repeating them is no faster than exploring), 0 for a one-time job. */
    val period: Int = 0,
)

/** Progress on an accepted mission. A finished one stays until it is claimed. */
@Serializable
data class MissionProgress(val id: String, val progress: Int = 0, val done: Boolean = false)

object Missions {
    /** The most jobs a team of explorers can hold at once. *(tune)* */
    const val MAX_ACTIVE = 3

    /** The mission board opens with Story chapter 2. */
    const val BOARD_CHAPTER = 2

    val all: List<MissionDef> = listOf(
        MissionDef("explore-woods-3", "Scout the Tiny Woods", "A trader wants to know how far the path goes.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "tiny-woods", floor = 3, poke = 60, xp = 30, period = 7),
        MissionDef("explore-woods-5", "Cross the Tiny Woods", "Travellers ask for the way through to the far side.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "tiny-woods", floor = 5, poke = 120, xp = 60, itemId = Items.berry("sweet"), itemCount = 2, period = 7),
        MissionDef("explore-cave-6", "Light the cave", "The cave is dark past the sixth floor. Someone has to look.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "thunderwave-cave", floor = 6, poke = 180, xp = 90, period = 7),
        MissionDef("explore-steel-8", "Climb Mt. Steel", "The mountain pass is blocked. Find out how high the trouble goes.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "mt-steel", floor = 8, poke = 320, xp = 160, itemId = Items.berry("spicy"), itemCount = 3, period = 7),
        MissionDef("gather-10", "Gather berries", "The town fair needs berries.", MissionKind.COLLECT, ExplorerRank.NORMAL, false, count = 10, poke = 80, xp = 40, itemId = Items.berry("dry"), itemCount = 2, period = 7),
        MissionDef("gather-40", "A big berry order", "A café orders berries by the basket.", MissionKind.COLLECT, ExplorerRank.SILVER, false, count = 40, poke = 300, xp = 150, period = 7),
        MissionDef("rescue-vulpix", "Rescue a lost Vulpix", "A Vulpix wandered into the Tiny Woods and cannot find its way out.", MissionKind.RESCUE, ExplorerRank.NORMAL, true, "tiny-woods", floor = 4, rescued = 37, poke = 100, xp = 80),
        MissionDef("rescue-marill", "Rescue a stranded Marill", "A Marill is stuck deep in Thunderwave Cave.", MissionKind.RESCUE, ExplorerRank.BRONZE, true, "thunderwave-cave", floor = 6, rescued = 183, poke = 200, xp = 140),
        MissionDef("rescue-eevee", "Rescue a frightened Eevee", "An Eevee is hiding on Mt. Steel and will not come down alone.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "mt-steel", floor = 8, rescued = 133, poke = 400, xp = 250, itemId = Items.RARE_CANDY, itemCount = 1),
        MissionDef("rescue-totodile", "Rescue a Totodile", "A Totodile swam too far upstream in Waterfall Cave.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "waterfall-cave", floor = 9, rescued = 158, poke = 500, xp = 300),
        MissionDef("rescue-abra", "Rescue a sleeping Abra", "An Abra teleported into the Ancient Ruins and cannot teleport back.", MissionKind.RESCUE, ExplorerRank.GOLD, true, "ancient-ruins", floor = 10, rescued = 63, poke = 800, xp = 450),
        MissionDef("rescue-bagon", "Rescue a Bagon", "A young Bagon is stuck high in the Sky Tower.", MissionKind.RESCUE, ExplorerRank.PLATINUM, true, "sky-tower", floor = 12, rescued = 371, poke = 1_200, xp = 700, itemId = Items.RARE_CANDY, itemCount = 1),
        MissionDef("explore-waterfall-12", "Reach the waterfall's source", "A scholar wants to know where the water begins.", MissionKind.EXPLORE, ExplorerRank.SILVER, false, "waterfall-cave", floor = 12, poke = 600, xp = 300, period = 7),
        MissionDef("explore-glacier-14", "Cross the Glacier Palace", "A merchant wants a safe path over the ice.", MissionKind.EXPLORE, ExplorerRank.GOLD, false, "glacier-palace", floor = 14, poke = 900, xp = 450, period = 7),
        MissionDef("explore-ruins-16", "Map the Ancient Ruins", "Scholars need a map of the deepest hall.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "ancient-ruins", floor = 16, poke = 1_300, xp = 650, period = 7),
        MissionDef("explore-sky-18", "Climb the Sky Tower", "The wind has changed at the top. Go and see why.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "sky-tower", floor = 18, poke = 1_800, xp = 900, period = 7),
        MissionDef("deliver-berries", "Deliver berries to a friend", "A Pokémon waiting deep in Tiny Woods asks for sweet berries.", MissionKind.DELIVER, ExplorerRank.NORMAL, false, "tiny-woods", floor = 5, poke = 150, xp = 70, give = Items.berry("sweet"), giveCount = 3, period = 7),
        MissionDef("deliver-spicy", "Deliver spicy berries to the Dojo", "The Dojo master wants spicy berries delivered to a camp on Mt. Steel.", MissionKind.DELIVER, ExplorerRank.BRONZE, false, "mt-steel", floor = 6, poke = 350, xp = 180, give = Items.berry("spicy"), giveCount = 3, period = 7),
        MissionDef("daily-berries", "Daily berry run", "Today's berry order.", MissionKind.COLLECT, ExplorerRank.NORMAL, false, count = 15, poke = 150, xp = 60, period = 1),
        MissionDef("weekly-steel", "Weekly patrol of Mt. Steel", "The mountain needs a patrol once a week.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "mt-steel", floor = 10, poke = 900, xp = 400, itemId = Items.RARE_CANDY, itemCount = 1, period = 7),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): MissionDef? = byId[id]

    /** Whether the board is open at all for [data]. */
    fun boardOpen(data: WorldData): Boolean = Story.opened(data) >= BOARD_CHAPTER

    /** The jobs [data] can take now: the board is open, the rank is reached, and a one-time job is not done or already taken. */
    fun available(data: WorldData): List<MissionDef> =
        if (!boardOpen(data)) emptyList()
        else all.filter { m ->
            data.rank >= m.needs && data.missions.none { it.id == m.id } && !(m.once && m.id in data.missionsDone) &&
                (m.period == 0 || data.missionClaimedDay[m.id].let { it == null || day(data) - it >= m.period })
        }

    /** The day number World thinks it is: the last time it settled, in whole days. */
    fun day(data: WorldData): Long = data.lastSeenAt / 86_400_000L

    /** What one climbed floor does to the accepted jobs. [berries] is how many berries it gave. Reports each job it finishes. */
    internal fun advance(data: WorldData, dungeonId: String, floor: Int, berries: Int, at: Long, events: MutableList<WorldEvent>): WorldData {
        if (data.missions.isEmpty()) return data
        var d = data
        val updated = data.missions.map { p ->
            val m = byId[p.id]
            if (m == null || p.done) return@map p
            val reached = m.dungeonId == dungeonId && floor >= m.floor
            val next = when (m.kind) {
                MissionKind.EXPLORE, MissionKind.RESCUE, MissionKind.DELIVER -> if (reached) p.copy(progress = 1, done = true) else p
                MissionKind.COLLECT -> p.copy(progress = p.progress + berries).let { if (it.progress >= m.count) it.copy(done = true) else it }
            }
            if (next.done) {
                events += WorldEvent.MissionDone(m.id)
                if (m.kind == MissionKind.RESCUE && m.rescued != null) d = d.copy(finds = d.finds + Find(m.rescued, dungeonId, floor, at), discovered = d.discovered + m.rescued)
            }
            next
        }
        return d.copy(missions = updated)
    }
}
