package app.pokeplan.world.tools

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Items
import app.pokeplan.world.Cafe
import app.pokeplan.world.Dungeons
import app.pokeplan.world.Facility
import app.pokeplan.world.Dojo
import app.pokeplan.world.MissionKind
import app.pokeplan.world.Missions
import app.pokeplan.world.SwapShop
import app.pokeplan.world.Story
import app.pokeplan.world.WorldSkill
import app.pokeplan.world.ExplorerRank
import app.pokeplan.world.Outcomes
import app.pokeplan.world.WorldData
import app.pokeplan.world.WorldEvent
import app.pokeplan.world.WorldSim
import java.io.File
import kotlin.random.Random

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
    Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

/** A team of [size] Pokémon, all at [level] (species 25 is Electric, so it favors Thunderwave Cave; the rest of the pool is neutral). */
private fun team(size: Int, level: Int, prefix: String = "t") = List(size) { mon("$prefix$it", if (it % 2 == 0) 25 else 74, level) }

private data class Totals(var floors: Int = 0, var setbacks: Int = 0, var retreats: Int = 0, var xp: Int = 0, var poke: Int = 0, var berries: Int = 0, var treasure: Int = 0, var finds: Int = 0, var candies: Int = 0)

/** Hourly yield of one team in one dungeon, measured over 24 h of staying (no cap hit because each settle is one hour). */
private fun hourly(dungeonId: String, size: Int, level: Int): Pair<Double, Totals> {
    val roster = team(size, level)
    var data = WorldData().copy(xp = 1_000_000, clears = Dungeons.all.associate { it.id to 1 }, guardianDone = true).assign(1, roster.map { it.id })!!.start(1, dungeonId, 0L)!!
    val t = Totals()
    for (h in 1..24 * 7) {
        val r = WorldSim.settle(data, roster, h * HOUR)
        data = r.data
        // A team that turned back is home (WORLD.md 3.6): the player sends it again, here at the next hour.
        if (data.team(1)?.expedition == null) data = data.start(1, dungeonId, h * HOUR) ?: data
        r.events.forEach { e ->
            when (e) {
                is WorldEvent.FloorCleared -> t.floors++
                is WorldEvent.Setback -> t.setbacks++
                is WorldEvent.Retreat -> t.retreats++
                is WorldEvent.Poke -> t.poke += e.amount
                is WorldEvent.Item -> t.berries += e.count
                is WorldEvent.Treasure -> t.treasure++
                is WorldEvent.Found -> t.finds++
                else -> {}
            }
        }
    }
    t.xp = data.xp - 1_000_000
    t.candies = data.items.filterKeys { it.contains("rare") }.values.sum()
    return 168.0 to t
}

/** A player who opens the app [opens] times a day, keeps one team of [size] at [level] out in the best dungeon the rank allows. */
private fun career(opens: Int, size: Int, level: Int, days: Int): List<String> {
    val roster = team(size, level)
    var data = WorldData().assign(1, roster.map { it.id })!!
    val gap = DAY / opens
    var now = 0L
    var dungeon = "tiny-woods"
    val rankDay = mutableMapOf<ExplorerRank, Int>()
    data = data.start(1, dungeon, 0L)!!
    val unlocked = mutableMapOf<String, Int>()
    var poke = 0
    repeat(days) { d ->
        repeat(opens) {
            now += gap
            data = WorldSim.settle(data, roster, now).data
            poke += data.pokeEarned; data = data.copy(pokeEarned = 0)
            val best = Dungeons.home.filter { data.rank >= it.needs && Outcomes.retreatsAt(WorldSim.power(roster, it), it) == null }.lastOrNull()?.id ?: "tiny-woods"
            if (best != dungeon || data.team(1)?.expedition == null) {
                dungeon = best
                data = (data.recall(1) ?: data).start(1, dungeon, now) ?: data
            }
        }
        rankDay.putIfAbsent(data.rank, d + 1)
        Dungeons.home.forEach { if (data.clears[it.id] != null) unlocked.putIfAbsent(it.id, d + 1) }
    }
    val lines = mutableListOf<String>()
    lines += "| ${opens}/day | $size × Lv $level | ${days} d | ${data.xp} | ${data.rank.label} | $poke | ${data.finds.size} | ${data.items.filterKeys { it.contains("rare") }.values.sum()} | ${data.treasure.values.sum()} |"
    lines += "|  | first reached |  | ${rankDay.entries.joinToString(", ") { "${it.key.label} d${it.value}" }} |  |  |  |  |  |"
    return lines
}


/** What a simulated player did over a long stretch, for the report. */
private class Journey(val label: String) {
    val milestoneDays = linkedMapOf<String, Int>()
    /** The Poké balance of the player: the rules hold none, so the simulation keeps it (earned is banked after every call that can earn). */
    var balance = 0
    var spentFacilities = 0
    var spentShop = 0
    var candiesBought = 0
    var jobsClaimed = 0
    val jobsByKind = linkedMapOf<MissionKind, Int>()
    var jobPoke = 0
    var jobXp = 0
    var jobCandies = 0
    var deliveries = 0
    var deliveriesMissed = 0
    var swaps = 0
    var dojoSessions = 0
    var spentDojo = 0
    var brews = 0
    var data = WorldData()
    fun mark(key: String, day: Int) { milestoneDays.putIfAbsent(key, day) }
}

/**
 * A player who opens the app [opens] times a day and keeps one team of [size] at [level] (a second team is not played). They take
 * the jobs that fit the dungeon they are in, claim jobs and research, build the Camp and Lab (the lower level first), brew at the Café
 * when they have three berries, and spend the rest of their Poké on shop Rare Candies. Deliver jobs and the Dojo are not played.
 */
private fun journey(opens: Int, size: Int, level: Int, days: Int, cafe: Boolean, dojo: Boolean = false): Journey {
    val roster = team(size, level)
    val j = Journey("${opens}/day, $size × Lv $level${if (cafe) ", Café" else ""}${if (dojo) ", Dojo" else ""}")
    var data = WorldData().assign(1, roster.map { it.id })!!
    val gap = DAY / opens
    fun bank() { j.balance += data.pokeEarned; data = data.copy(pokeEarned = 0) }
    var now = 0L
    var current: String? = null
    // The deepest dungeon the player can start and the team would not turn back in (a player reads the team page's explanation, WORLD.md 3.6; the roster's levels stay fixed here, which is
    // the pessimistic case, since the real members gain a level's worth of experience from the floors).
    fun bestDungeon(): String? = Dungeons.home.reversed().firstOrNull { (data.recall(1) ?: data).start(1, it.id, now) != null && Outcomes.retreatsAt(WorldSim.power(roster, it), it) == null }?.id
    repeat(days) { d ->
        repeat(opens) {
            now += gap
            data = WorldSim.settle(data, roster, now).data; bank()
            // Claim what is done, take the jobs that fit.
            for (m in data.missions.filter { it.done }) {
                val def = Missions.byId(m.id) ?: continue
                val give = def.give
                if (give != null) {
                    // A Deliver job hands over berries from the Bag; the Swap Shop turns three of another flavour into the one it needs.
                    if ((data.items[give] ?: 0) < def.giveCount) {
                        val other = SwapShop.flavors.map { Items.berry(it) }.filter { it != give }.firstOrNull { (data.items[it] ?: 0) >= SwapShop.GIVE }
                        if (other != null) {
                            data = data.copy(items = data.items + (other to data.items.getValue(other) - SwapShop.GIVE) + (give to (data.items[give] ?: 0) + 1)); j.swaps++
                        }
                    }
                    if ((data.items[give] ?: 0) < def.giveCount) { j.deliveriesMissed++; continue }
                    data = data.copy(items = data.items + (give to data.items.getValue(give) - def.giveCount)); j.deliveries++
                }
                val before = Triple(j.balance, data.xp, data.items[Items.RARE_CANDY] ?: 0)
                data.claimMission(m.id)?.let {
                    data = it; bank(); j.jobsClaimed++; j.jobsByKind[def.kind] = (j.jobsByKind[def.kind] ?: 0) + 1
                    j.jobPoke += j.balance - before.first; j.jobXp += data.xp - before.second; j.jobCandies += (data.items[Items.RARE_CANDY] ?: 0) - before.third
                }
            }
            data.researchReady().forEach { r -> data.claimResearch(r.species)?.let { data = it; bank() } }
            fun startable(id: String?) = id == null || (data.recall(1) ?: data).start(1, id, now) != null
            Missions.available(data).filter { m ->
                // Only the jobs this team can do: an Escort needs enough Pokémon and a Defeat enough power, and a job that cannot be done would sit in one of the three slots for ever.
                val dungeon = m.dungeonId?.let { Dungeons.byId(it) }
                startable(m.dungeonId) && (dungeon == null || m.floor <= 0 || Outcomes.of(WorldSim.power(roster, dungeon), dungeon, m.floor) == Outcomes.Outcome.CLEARED) && m.kind != MissionKind.INVESTIGATION && m.kind != MissionKind.CHALLENGE && m.kind != MissionKind.STORY && (m.kind != MissionKind.ESCORT || roster.size >= m.count) && (m.kind != MissionKind.DEFEAT || (dungeon != null && WorldSim.power(roster, dungeon) >= m.count))
            }.forEach { m -> data.acceptMission(m.id)?.let { data = it } }
            // Spend: facilities (lower level first), then shop candies.
            while (true) {
                val f = Facility.entries.sortedBy { data.facilityLevel(it) }.firstOrNull { data.facilityLevel(it) < Facility.MAX_LEVEL && j.balance >= it.cost(data.facilityLevel(it)) } ?: break
                val cost = f.cost(data.facilityLevel(f))
                data = data.upgrade(f, j.balance)!!; j.balance -= cost; j.spentFacilities += cost
            }
            val facilitiesDone = Facility.entries.all { data.facilityLevel(it) >= Facility.MAX_LEVEL }
            // The Dojo, when played, trains the lowest skill until all are at the top; then the shop.
            while (dojo && facilitiesDone && j.balance >= Dojo.COST) {
                val skill = WorldSkill.entries.minByOrNull { data.skillLevel(it) }!!
                data = data.train(skill, j.balance) ?: break; j.balance -= Dojo.COST; j.dojoSessions++; j.spentDojo += Dojo.COST
            }
            // Then Exp. Candies at the Poké Mart (the Kecleon Shop sells no candy since 0.187.0).
            if (facilitiesDone) { val each = app.pokeplan.core.pokemon.Shop.COINS_PER_RARE_CANDY; val n = j.balance / each; j.spentShop += n * each; j.balance -= n * each; j.candiesBought += n }
            if (cafe) {
                val berryId = data.items.keys.firstOrNull { it.startsWith("berry-") && (data.items[it] ?: 0) >= Cafe.BERRIES }
                if (berryId != null && data.brewUntil <= now) { data = (data.brew(now) ?: data).copy(items = data.items + (berryId to data.items.getValue(berryId) - Cafe.BERRIES)); j.brews++ }
            }
            // A job that is not finished sends the team to its dungeon first; otherwise the best open dungeon.
            val errand = data.missions.firstOrNull { !it.done }?.let { Missions.byId(it.id)?.dungeonId }
            val best = if (errand != null && startable(errand)) errand else bestDungeon()
            if (best != null && (best != current || data.team(1)?.expedition == null)) {
                current = best
                data = (data.recall(1) ?: data).start(1, best, now) ?: data
            }
        }
        val day = d + 1
        j.mark("Rank ${data.rank.label}", day)
        j.mark("Story chapter ${Story.opened(data)}", day)
        if (data.guardianDone) j.mark("Guardian faced", day)
        if (WorldSkill.entries.all { data.skillLevel(it) >= WorldSkill.MAX_LEVEL }) j.mark("All skills at the top", day)
        Dungeons.home.forEach { dg -> if ((data.clears[dg.id] ?: 0) > 0) j.mark("${dg.name} cleared", day) }
        Facility.entries.forEach { f -> if (data.facilityLevel(f) >= Facility.MAX_LEVEL) j.mark("${f.label} maxed", day) }
    }
    j.data = data
    return j
}

fun main(args: Array<String>) {
    val out = StringBuilder("# World economy report\n\nGenerated by `./gradlew :world-core:simulateWorldEconomy` (World numbers are *(tune)* first guesses, `docs/pokelife/MODULES/WORLD.md section 14`).\nNothing here pays through the server reward engine; items go to the Bag, Poké stays in `AppData.world`.\n\n")
    out.append("## Yield per team-hour (a team that stays out, measured over 7 days; one that turns back is sent again at the next hour)\n\n")
    out.append("| Dungeon | Team | Floors/h | Cleared/day | Setbacks/day | Turn-backs/day | XP/h | Poké/h | Berries/h | Boxes/h | Finds/day |\n|---|---|---|---|---|---|---|---|---|---|---|\n")
    for (dg in Dungeons.all) for ((size, lv) in listOf(2 to 5, 2 to 20, 4 to 20, 4 to 50)) {
        val (hours, t) = hourly(dg.id, size, lv)
        fun h(v: Int) = "%.1f".format(v / hours)
        out.append("| ${dg.name} (${dg.floors} fl) | $size × Lv $lv | ${h(t.floors)} | ${"%.1f".format(t.floors / hours * 24 / dg.floors)} | ${"%.1f".format(t.setbacks / hours * 24)} | ${"%.1f".format(t.retreats / hours * 24)} | ${h(t.xp)} | ${h(t.poke)} | ${h(t.berries)} | ${"%.2f".format(t.treasure / hours)} | ${"%.2f".format(t.finds / hours * 24)} |\n")
    }
    out.append("\nThe Kanto layer's dungeons are in the same table, so their Poké, XP and finds per hour can be compared with the home layer's at the same rank gate (Viridian Forest and Mt. Moon with Tiny Woods and Thunderwave Cave; Cerulean Cave with the Temporal Tower). The careers and the year below follow the home layer only.\n")
    out.append("\n## One team over a career (best open dungeon; the 12 h away cap applies per open)\n\n")
    out.append("| Opens | Team | Span | XP | Rank | Poké | Finds waiting | Rare Candies | Boxes |\n|---|---|---|---|---|---|---|---|---|\n")
    for (opens in listOf(1, 2, 4)) for ((size, lv) in listOf(2 to 10, 4 to 30)) for (d in listOf(30, 120)) career(opens, size, lv, d).forEach { out.append(it).append('\n') }
    out.append("\n## A year of play (one team; missions, research, facilities, Café, Swap Shop, Dojo and shop played)\n\n")
    out.append("Rare Candies: **free** is milestones, jobs and research; **boxes** is the expected value of the treasure boxes opened (silver 0.15, gold 0.35 each); **bought** is the shop.\n\n")
    out.append("| Player | Rank | XP | Species found | Free candies | Box candies | Bought | Poké earned | Facilities | Shop | Jobs claimed | Skills (E/F/S) |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n")
    val journeys = listOf(
        journey(1, 2, 10, 365, false), journey(2, 4, 30, 365, false), journey(2, 4, 30, 365, true), journey(4, 4, 30, 365, true), journey(2, 4, 30, 365, true, dojo = true),
    )
    for (j in journeys) {
        val d = j.data
        val free = d.items[Items.RARE_CANDY] ?: 0
        val boxes = (d.treasure[2] ?: 0) * 0.15 + (d.treasure[3] ?: 0) * 0.35
        val earned = j.balance + j.spentFacilities + j.spentShop + j.spentDojo
        out.append("| ${j.label} | ${d.rank.label} | ${d.xp} | ${d.discovered.size} | $free | ${"%.0f".format(boxes)} | ${j.candiesBought} | $earned | ${j.spentFacilities} | ${j.spentShop} | ${j.jobsClaimed} | ${WorldSkill.entries.joinToString("/") { d.skillLevel(it).toString() }} |\n")
    }
    out.append("\n### Jobs, the Swap Shop and the Dojo over that year\n\n")
    out.append("Jobs: ordinary jobs wait 7 days, daily ones 1. **Job Poké and XP** are what claiming paid; **Share** is the part of all the Poké (or Exploration XP) earned that came from jobs. Deliver jobs hand over berries from the Bag; **missed** is the ones that could not be handed over for lack of berries after a swap.\n\n")
    out.append("| Player | Jobs claimed | Explore | Find items | Rescue | Deliver | Escort | Defeat | Job Poké | Poké share | Job XP | XP share | Job candies | Delivered | Missed | Swaps | Dojo sessions | Dojo Poké |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
    for (j in journeys) {
        val d = j.data
        val earned = j.balance + j.spentFacilities + j.spentShop + j.spentDojo
        fun n(k: MissionKind) = j.jobsByKind[k] ?: 0
        out.append("| ${j.label} | ${j.jobsClaimed} | ${n(MissionKind.EXPLORE)} | ${n(MissionKind.COLLECT)} | ${n(MissionKind.RESCUE)} | ${n(MissionKind.DELIVER)} | ${n(MissionKind.ESCORT)} | ${n(MissionKind.DEFEAT)} | ${j.jobPoke} | ${"%.0f".format(100.0 * j.jobPoke / earned.coerceAtLeast(1))}% | ${j.jobXp} | ${"%.0f".format(100.0 * j.jobXp / d.xp.coerceAtLeast(1))}% | ${j.jobCandies} | ${j.deliveries} | ${j.deliveriesMissed} | ${j.swaps} | ${j.dojoSessions} | ${j.spentDojo} |\n")
    }
    out.append("\n### When things happen (day number)\n\n")
    for (j in journeys) out.append("- **${j.label}:** ${j.milestoneDays.entries.joinToString(", ") { "${it.key} d${it.value}" }}\n")
    out.append("\nRank XP: ${ExplorerRank.entries.joinToString(", ") { "${it.label} ${it.points}" }}.\n")
    File(args[0]).writeText(out.toString())
    println(out)
}
