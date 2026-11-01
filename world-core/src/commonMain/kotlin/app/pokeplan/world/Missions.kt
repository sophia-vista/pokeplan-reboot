package app.pokeplan.world

import app.pokeplan.core.pokemon.Items
import kotlinx.serialization.Serializable

/** What a mission asks (docs/world.md, section 8). */
enum class MissionKind(val label: String) { EXPLORE("Explore"), COLLECT("Find items"), RESCUE("Rescue"), DELIVER("Deliver"), ESCORT("Escort"), DEFEAT("Defeat") }

/**
 * A job on the board. [EXPLORE] is done when any team reaches [floor] of [dungeonId] while it is accepted; [COLLECT] when
 * [count] berries have been found anywhere; [RESCUE] when a team reaches [floor] of [dungeonId], and the lost Pokémon
 * ([rescued], a species id) then waits among the Finds; [ESCORT] is an Explore job that only counts for a team of at least [count]
 * Pokémon, and [DEFEAT] one that only counts for a team whose power is at least [count] (docs/world.md, section 8). A job that is [once] never comes back; the others are repeatable.
 * Rewards are paid when the player claims the finished job. Numbers are *(tune)*.
 */
data class MissionDef(
    val id: String, val title: String, val story: String, val kind: MissionKind, val needs: ExplorerRank, val once: Boolean,
    val dungeonId: String? = null, val floor: Int = 0, val count: Int = 0, val rescued: Int? = null,
    val poke: Int, val xp: Int, val itemId: String? = null, val itemCount: Int = 0,
    /** A [DELIVER] job reaches [floor] like an Explore job, and is claimed by handing over [giveCount] of [give] from the Bag. */
    val give: String? = null, val giveCount: Int = 0,
    /** Days before the job can be claimed again: 1 for a daily job, 7 for the weekly one, 14 for the ordinary jobs (so repeating them is no faster than exploring), 0 for a one-time job. */
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
        MissionDef("explore-woods-3", "Scout the Tiny Woods", "A trader wants to know how far the path goes.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "tiny-woods", floor = 3, poke = 40, xp = 20, period = 14),
        MissionDef("explore-woods-5", "Cross the Tiny Woods", "Travellers ask for the way through to the far side.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "tiny-woods", floor = 5, poke = 70, xp = 40, itemId = Items.berry("sweet"), itemCount = 2, period = 14),
        MissionDef("explore-cave-6", "Light the cave", "The cave is dark past the sixth floor. Someone has to look.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "thunderwave-cave", floor = 6, poke = 110, xp = 50, period = 14),
        MissionDef("explore-steel-8", "Climb Mt. Steel", "The mountain pass is blocked. Find out how high the trouble goes.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "mt-steel", floor = 8, poke = 190, xp = 100, itemId = Items.berry("spicy"), itemCount = 3, period = 14),
        MissionDef("gather-10", "Gather berries", "The town fair needs berries.", MissionKind.COLLECT, ExplorerRank.NORMAL, false, count = 10, poke = 50, xp = 20, itemId = Items.berry("dry"), itemCount = 2, period = 14),
        MissionDef("gather-40", "A big berry order", "A café orders berries by the basket.", MissionKind.COLLECT, ExplorerRank.SILVER, false, count = 40, poke = 180, xp = 90, period = 14),
        MissionDef("rescue-vulpix", "Rescue a lost Vulpix", "A Vulpix wandered into the Tiny Woods and cannot find its way out.", MissionKind.RESCUE, ExplorerRank.NORMAL, true, "tiny-woods", floor = 4, rescued = 37, poke = 100, xp = 80),
        MissionDef("rescue-marill", "Rescue a stranded Marill", "A Marill is stuck deep in Thunderwave Cave.", MissionKind.RESCUE, ExplorerRank.BRONZE, true, "thunderwave-cave", floor = 6, rescued = 183, poke = 200, xp = 140),
        MissionDef("rescue-eevee", "Rescue a frightened Eevee", "An Eevee is hiding on Mt. Steel and will not come down alone.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "mt-steel", floor = 8, rescued = 133, poke = 400, xp = 250, itemId = Items.RARE_CANDY, itemCount = 1),
        MissionDef("rescue-totodile", "Rescue a Totodile", "A Totodile swam too far upstream in Waterfall Cave.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "waterfall-cave", floor = 9, rescued = 158, poke = 500, xp = 300),
        MissionDef("rescue-abra", "Rescue a sleeping Abra", "An Abra teleported into the Ancient Ruins and cannot teleport back.", MissionKind.RESCUE, ExplorerRank.GOLD, true, "ancient-ruins", floor = 10, rescued = 63, poke = 800, xp = 450),
        MissionDef("rescue-bagon", "Rescue a Bagon", "A young Bagon is stuck high in the Sky Tower.", MissionKind.RESCUE, ExplorerRank.PLATINUM, true, "sky-tower", floor = 12, rescued = 371, poke = 1_200, xp = 700, itemId = Items.RARE_CANDY, itemCount = 1),
        MissionDef("explore-waterfall-12", "Reach the waterfall's source", "A scholar wants to know where the water begins.", MissionKind.EXPLORE, ExplorerRank.SILVER, false, "waterfall-cave", floor = 12, poke = 360, xp = 180, period = 14),
        MissionDef("explore-glacier-14", "Cross the Glacier Palace", "A merchant wants a safe path over the ice.", MissionKind.EXPLORE, ExplorerRank.GOLD, false, "glacier-palace", floor = 14, poke = 540, xp = 270, period = 14),
        MissionDef("explore-ruins-16", "Map the Ancient Ruins", "Scholars need a map of the deepest hall.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "ancient-ruins", floor = 16, poke = 780, xp = 390, period = 14),
        MissionDef("explore-sky-18", "Climb the Sky Tower", "The wind has changed at the top. Go and see why.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "sky-tower", floor = 18, poke = 1_080, xp = 540, period = 14),
        MissionDef("deliver-berries", "Deliver berries to a friend", "A Pokémon waiting deep in Tiny Woods asks for sweet berries.", MissionKind.DELIVER, ExplorerRank.NORMAL, false, "tiny-woods", floor = 5, poke = 90, xp = 40, give = Items.berry("sweet"), giveCount = 3, period = 14),
        MissionDef("deliver-spicy", "Deliver spicy berries to the Dojo", "The Dojo master wants spicy berries delivered to a camp on Mt. Steel.", MissionKind.DELIVER, ExplorerRank.BRONZE, false, "mt-steel", floor = 6, poke = 210, xp = 110, give = Items.berry("spicy"), giveCount = 3, period = 14),
        MissionDef("escort-cubone", "Escort a Cubone home", "A Cubone must cross Thunderwave Cave to its family. It is safe only in a crowd.", MissionKind.ESCORT, ExplorerRank.BRONZE, false, "thunderwave-cave", floor = 8, count = 3, poke = 240, xp = 120, period = 14),
        MissionDef("escort-wooper", "Escort a Wooper to the falls", "A Wooper wants to see the waterfall's source, with company.", MissionKind.ESCORT, ExplorerRank.SILVER, false, "waterfall-cave", floor = 12, count = 3, poke = 420, xp = 210, period = 14),
        MissionDef("defeat-geodude", "Drive off the Geodude gang", "Rowdy Geodude block the pass on Mt. Steel. A strong team can scare them away.", MissionKind.DEFEAT, ExplorerRank.BRONZE, false, "mt-steel", floor = 10, count = 150, poke = 300, xp = 150, period = 14),
        MissionDef("defeat-glalie", "Calm the Glalie", "A Glalie is freezing the palace halls. Only a mighty team can get near.", MissionKind.DEFEAT, ExplorerRank.GOLD, false, "glacier-palace", floor = 14, count = 250, poke = 600, xp = 300, period = 14),
        MissionDef("defeat-storm", "Face the Sky Tower's storm", "A storm gathers at the top. Face it with a team of great power.", MissionKind.DEFEAT, ExplorerRank.PLATINUM, false, "sky-tower", floor = 18, count = 350, poke = 1_080, xp = 540, period = 14),
        // Regional jobs: Kanto, then Johto, then Hoenn. They only count while the region's story lets a team into the dungeon.
        MissionDef("kanto-forest-5", "Scout Viridian Forest", "Young trainers keep getting lost among the trees.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "viridian-forest", floor = 5, poke = 75, xp = 38, period = 14),
        MissionDef("kanto-moon-8", "Cross Mt. Moon", "A shop in Pewter wants the cave's far exit mapped.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "mt-moon", floor = 8, poke = 190, xp = 95, period = 14),
        MissionDef("kanto-rescue-clefairy", "Rescue a lost Clefairy", "A Clefairy wandered off under Mt. Moon and cannot find the others.", MissionKind.RESCUE, ExplorerRank.BRONZE, true, "mt-moon", floor = 6, rescued = 35, poke = 200, xp = 140),
        MissionDef("kanto-tunnel-10", "Clear the Rock Tunnel", "Rowdy Pokémon block the dark passage. A strong team can scare them off.", MissionKind.DEFEAT, ExplorerRank.BRONZE, false, "rock-tunnel", floor = 10, count = 150, poke = 300, xp = 150, period = 14),
        MissionDef("kanto-tower-12", "Visit Pokémon Tower", "The old tower is restless tonight. Someone should look.", MissionKind.EXPLORE, ExplorerRank.SILVER, false, "pokemon-tower", floor = 12, poke = 430, xp = 215, period = 14),
        MissionDef("kanto-plant-12", "Escort the electrician", "A repairman must reach the Power Plant, and does not like to go alone.", MissionKind.ESCORT, ExplorerRank.SILVER, false, "power-plant", floor = 12, count = 3, poke = 430, xp = 215, period = 14),
        MissionDef("kanto-seafoam-14", "Cross the Seafoam Islands", "Currents on the far side are strange. Find out why.", MissionKind.EXPLORE, ExplorerRank.GOLD, false, "seafoam-islands", floor = 14, poke = 590, xp = 295, period = 14),
        MissionDef("kanto-road-18", "Walk Victory Road", "The last road before the League needs a patrol.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "victory-road", floor = 18, poke = 970, xp = 485, period = 14),
        MissionDef("johto-sprout-5", "Climb the Sprout Tower", "The monks want the swaying pillar checked.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "sprout-tower", floor = 5, poke = 75, xp = 38, period = 14),
        MissionDef("johto-ilex-8", "Cross Ilex Forest", "The forest's shrine is overgrown. Find the way through.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "ilex-forest", floor = 8, poke = 190, xp = 95, period = 14),
        MissionDef("johto-rescue-slowpoke", "Rescue a Slowpoke", "A Slowpoke is stuck at the bottom of the well.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "slowpoke-well", floor = 8, rescued = 79, poke = 200, xp = 140),
        MissionDef("johto-union-10", "Calm Union Cave", "Wild Pokémon crowd the narrow tunnels. A strong team can calm them.", MissionKind.DEFEAT, ExplorerRank.BRONZE, false, "union-cave", floor = 10, count = 150, poke = 300, xp = 150, period = 14),
        MissionDef("johto-alph-12", "Map the Ruins of Alph", "Scholars need the inner chambers drawn.", MissionKind.EXPLORE, ExplorerRank.SILVER, false, "ruins-of-alph", floor = 12, poke = 430, xp = 215, period = 14),
        MissionDef("johto-mortar-14", "Escort a pilgrim to Mt. Mortar", "A pilgrim wants to see the waterfall behind the mountain, with company.", MissionKind.ESCORT, ExplorerRank.GOLD, false, "mt-mortar", floor = 14, count = 3, poke = 590, xp = 295, period = 14),
        MissionDef("johto-whirl-18", "Cross the Whirl Islands", "The whirlpools hide a path. Chart it.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "whirl-islands", floor = 18, poke = 970, xp = 485, period = 14),
        MissionDef("johto-tin-20", "Climb the Tin Tower", "The bell tower rings by itself. Go to the top and see why.", MissionKind.EXPLORE, ExplorerRank.DIAMOND, false, "tin-tower", floor = 20, poke = 1200, xp = 600, period = 14),
        MissionDef("hoenn-woods-5", "Scout Petalburg Woods", "A researcher wants the woods' paths mapped.", MissionKind.EXPLORE, ExplorerRank.NORMAL, false, "petalburg-woods", floor = 5, poke = 75, xp = 38, period = 14),
        MissionDef("hoenn-granite-8", "Explore Granite Cave", "The cave's lower floors are unmapped.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "granite-cave", floor = 8, poke = 190, xp = 95, period = 14),
        MissionDef("hoenn-rescue-spheal", "Rescue a Spheal", "A Spheal rolled into Shoal Cave and cannot roll back out.", MissionKind.RESCUE, ExplorerRank.SILVER, true, "shoal-cave", floor = 8, rescued = 363, poke = 200, xp = 140),
        MissionDef("hoenn-meteor-10", "Calm Meteor Falls", "Dragons are restless above the falls. A strong team can settle them.", MissionKind.DEFEAT, ExplorerRank.BRONZE, false, "meteor-falls", floor = 10, count = 150, poke = 300, xp = 150, period = 14),
        MissionDef("hoenn-chimney-12", "Climb Mt. Chimney", "The volcano's ash has changed. Find out why.", MissionKind.EXPLORE, ExplorerRank.SILVER, false, "mt-chimney", floor = 12, poke = 430, xp = 215, period = 14),
        MissionDef("hoenn-seafloor-14", "Dive the Seafloor Cavern", "Something stirs on the cavern floor.", MissionKind.EXPLORE, ExplorerRank.GOLD, false, "seafloor-cavern", floor = 14, poke = 590, xp = 295, period = 14),
        MissionDef("hoenn-road-18", "Walk Victory Road", "The road to the League needs a patrol.", MissionKind.EXPLORE, ExplorerRank.PLATINUM, false, "victory-road-hoenn", floor = 18, poke = 970, xp = 485, period = 14),
        MissionDef("hoenn-pillar-20", "Climb the Sky Pillar", "The pillar's top is said to hold an old secret.", MissionKind.EXPLORE, ExplorerRank.DIAMOND, false, "sky-pillar", floor = 20, poke = 1200, xp = 600, period = 14),
        MissionDef("daily-berries", "Daily berry run", "Today's berry order.", MissionKind.COLLECT, ExplorerRank.NORMAL, false, count = 15, poke = 100, xp = 40, period = 1),
        MissionDef("weekly-steel", "Weekly patrol of Mt. Steel", "The mountain needs a patrol once a week.", MissionKind.EXPLORE, ExplorerRank.BRONZE, false, "mt-steel", floor = 10, poke = 900, xp = 400, itemId = Items.RARE_CANDY, itemCount = 1, period = 7),
    )

    private val byId = all.associateBy { it.id }

    /** The region a job belongs to: its dungeon's layer, or the home region for a job that is not tied to a dungeon (berries). */
    fun layerOf(m: MissionDef): WorldLayer = m.dungeonId?.let { Dungeons.byId(it)?.layer } ?: WorldLayer.HOME

    fun byId(id: String): MissionDef? = byId[id]

    /** Whether the board is open at all for [data]. */
    fun boardOpen(data: WorldData): Boolean = Story.opened(data) >= BOARD_CHAPTER

    /** Whether a team could enter [m]'s dungeon now (its story chapter is open and a post-story place is unlocked); jobs with no dungeon are always open. */
    fun dungeonOpen(data: WorldData, m: MissionDef): Boolean {
        val d = m.dungeonId?.let { Dungeons.byId(it) } ?: return true
        return Story.opened(data, d.layer) >= d.chapter && (!d.postStory || data.layerDone(d.layer))
    }

    /** The jobs [data] can take at [now] (the settle time unless given: the screens pass the clock, which a quiet World would not have stored): the board is open, the rank is reached, and a one-time job is not done or already taken. */
    fun available(data: WorldData, now: Long = data.lastSeenAt, utcOffsetMillis: Long = 0): List<MissionDef> =
        if (!boardOpen(data)) emptyList()
        else all.filter { m ->
            data.rank >= m.needs && dungeonOpen(data, m) && data.missions.none { it.id == m.id } && !(m.once && m.id in data.missionsDone) &&
                (m.period == 0 || data.missionClaimedDay[m.id].let { it == null || day(now, utcOffsetMillis) - it >= m.period })
        }

    /** A job's place on the board for the player: can it be taken, is it held or done, or why not. */
    enum class BoardState { AVAILABLE, HELD, DONE, UNAVAILABLE }

    /** One line of the board: the job, its [state] and, when it cannot be taken, the [reason]; [repeats] says how often it comes back. */
    data class BoardEntry(val mission: MissionDef, val state: BoardState, val reason: String? = null) {
        val repeats: String get() = when (mission.period) { 0 -> "One time"; 1 -> "Daily"; 7 -> "Weekly"; else -> "Repeats every ${mission.period} days" }
    }

    /**
     * The whole board for [data] at [now], every job with its state: held (taken, not done), done (to claim), available, or unavailable with
     * the reason (rank not reached, a one-time job already done, or the days before a repeatable one comes back). Empty when the board is shut.
     */
    fun board(data: WorldData, now: Long = data.lastSeenAt, utcOffsetMillis: Long = 0): List<BoardEntry> {
        if (!boardOpen(data)) return emptyList()
        return all.map { m ->
            val held = data.missions.firstOrNull { it.id == m.id }
            val claimed = data.missionClaimedDay[m.id]
            when {
                held != null -> BoardEntry(m, if (held.done) BoardState.DONE else BoardState.HELD)
                m.once && m.id in data.missionsDone -> BoardEntry(m, BoardState.UNAVAILABLE, "Done for good.")
                data.rank < m.needs -> BoardEntry(m, BoardState.UNAVAILABLE, "Needs ${m.needs.label} rank.")
                !dungeonOpen(data, m) -> BoardEntry(m, BoardState.UNAVAILABLE, "Go further in the ${m.dungeonId?.let { Dungeons.byId(it)?.layer?.label }} story first.")
                m.period > 0 && claimed != null && day(now, utcOffsetMillis) - claimed < m.period -> {
                    val left = (m.period - (day(now, utcOffsetMillis) - claimed)).toInt()
                    BoardEntry(m, BoardState.UNAVAILABLE, "Back in $left ${if (left == 1) "day" else "days"}.")
                }
                else -> BoardEntry(m, BoardState.AVAILABLE)
            }
        }
    }

    /** The day number World thinks it is: the last time it settled, in whole days. */
    fun day(data: WorldData): Long = data.lastSeenAt / 86_400_000L

    /** The day number at [now] for a player [utcOffsetMillis] from UTC, so a daily job comes back at the player's midnight. */
    fun day(now: Long, utcOffsetMillis: Long): Long = (now + utcOffsetMillis).floorDiv(86_400_000L)

    /** What one climbed floor does to the accepted jobs. [berries] is how many berries it gave. Reports each job it finishes. */
    internal fun advance(data: WorldData, dungeonId: String, floor: Int, berries: Int, at: Long, teamSize: Int, power: Double, events: MutableList<WorldEvent>): WorldData {
        if (data.missions.isEmpty()) return data
        var d = data
        val updated = data.missions.map { p ->
            val m = byId[p.id]
            if (m == null || p.done) return@map p
            val reached = m.dungeonId == dungeonId && floor >= m.floor &&
                (m.kind != MissionKind.ESCORT || teamSize >= m.count) && (m.kind != MissionKind.DEFEAT || power >= m.count)
            val next = when (m.kind) {
                MissionKind.EXPLORE, MissionKind.RESCUE, MissionKind.DELIVER, MissionKind.ESCORT, MissionKind.DEFEAT -> if (reached) p.copy(progress = 1, done = true) else p
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
