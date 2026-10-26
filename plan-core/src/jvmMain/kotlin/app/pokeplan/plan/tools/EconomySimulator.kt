package app.pokeplan.plan.tools

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.Rarity
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.PlayerState
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.core.reward.TrainerRank
import java.io.File
import kotlin.random.Random

/** A kind of player: how many tasks and sessions a day, and how hard the tasks are. */
data class Profile(
    val name: String,
    val activeDayChance: Double,
    val tasksPerDay: IntRange,
    /** Weights for ranks E, D, C, B, A. */
    val rankWeights: List<Double>,
    val sessionsPerDay: IntRange,
    /** (minutes, weight) */
    val sessionMinutes: List<Pair<Int, Double>>,
)

val profiles = listOf(
    Profile("Light", 5 / 7.0, 1..3, listOf(3.0, 3.0, 2.0, 1.0, 1.0), 0..1, listOf(15 to 30.0, 25 to 50.0, 45 to 20.0)),
    Profile("Typical", 6 / 7.0, 3..7, listOf(2.0, 3.0, 3.0, 2.0, 1.0), 1..3, listOf(25 to 40.0, 45 to 40.0, 60 to 20.0)),
    Profile("Heavy", 0.95, 6..12, listOf(1.0, 2.0, 3.0, 3.0, 2.0), 2..5, listOf(25 to 30.0, 60 to 40.0, 90 to 30.0)),
)

/** What it takes to evolve everything in a region once. */
data class Demand(
    val evolutions: Int,
    val superCandies: Int,
    val rareCandiesLow: Int,
    val rareCandiesHigh: Int,
    val stones: Int,
    val linkCords: Int,
    val trials: Int,
)

fun evolutionDemand(region: Region): Demand {
    val dex = Dex.regionDex(region).filter { !it.isSpecial }
    val ids = dex.map { it.id }.toSet()
    var evolutions = 0; var supers = 0; var stones = 0; var cords = 0; var trials = 0
    val maxLevelByLine = mutableMapOf<Int, Int>()
    for (s in dex) {
        val from = s.evolvesFrom ?: continue
        if (from !in ids) continue
        val defaultFrom = Dex.defaultForm(from).identifier
        val row = Dex.evolutionsInto(s.id).firstOrNull { it.from == from && (it.requiredForm == null || it.requiredForm == defaultFrom) } ?: continue
        evolutions++
        when (row.kind) {
            EvolutionKind.STONE -> stones++
            EvolutionKind.TRADE -> cords++
            EvolutionKind.GENDER -> if (row.item != null) stones++ else supers++
            EvolutionKind.TRIAL -> { supers++; trials++ }
            else -> supers++
        }
        val level = EvolutionRules.requiredLevel(row) ?: 0
        val root = Dex.lineRoot(s.id)
        maxLevelByLine[root] = maxOf(maxLevelByLine[root] ?: 0, level)
    }
    val low = maxLevelByLine.values.sumOf { CandyRules.candiesBetween(1, maxOf(it, 1), catchUp = true) }
    val high = maxLevelByLine.values.sumOf { CandyRules.candiesBetween(1, maxOf(it, 1)) }
    return Demand(evolutions, supers, low, high, stones, cords, trials)
}

private class Run(val profile: Profile, val seed: Int, val days: Int) {
    val rng = Random(seed)
    var state = PlayerState()
    var nextId = 0
    val owned = mutableSetOf<Int>()
    val homeEntries: Set<Int> = Rarity.entries.flatMap { HatchRules.pool(Region.HOME, it) }.map { it.speciesId }.toSet()
    var homeDay: Int? = null
    val regionUnlockDay = mutableMapOf<Region, Int>()
    var eggs = 0; var taskEggs = 0; var shinies = 0
    var rare = 0; var superCandy = 0; var coinsFromTransfers = 0
    var drops = 0; var transfers = 0
    val checkpoints = mutableMapOf<Int, Snapshot>()

    data class Snapshot(val rank: Int, val points: Int, val eggs: Int, val taskEggs: Int, val rare: Int, val superCandy: Int, val coins: Int, val drops: Int, val shinies: Int, val buddyLevel: Int, val homeSeen: Int, val species: Int)

    fun id() = "sim-${nextId++}"

    fun pickRegion(): Region {
        val open = TrainerRank.unlockedRegions(state.trainerPoints)
        val unseen = open.associateWith { r ->
            Rarity.entries.flatMap { HatchRules.pool(r, it) }.map { it.speciesId }.toSet().count { it !in owned }
        }
        // A collector finishes the Home dex first, then goes where the most is still missing.
        if (unseen.getValue(Region.HOME) > 0) return Region.HOME
        val best = open.maxByOrNull { unseen.getValue(it) * 1000 - it.ordinal }!!
        return if (unseen.getValue(best) > 0) best else open[rng.nextInt(open.size)]
    }

    fun weighted(weights: List<Double>): Int {
        var pick = rng.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) { pick -= w; if (pick < 0) return i }
        return weights.lastIndex
    }

    fun handleRewards(rewards: List<Reward>) {
        for (r in rewards) when (r) {
            is Reward.Hatched -> {
                eggs++
                if (r.fromTask) taskEggs++
                if (r.pokemon.isShiny) shinies++
            }
            is Reward.Candies -> if (r.type == app.pokeplan.core.model.CandyType.SUPER) superCandy += r.amount else rare += r.amount
            is Reward.ItemDrop -> drops++
            else -> {}
        }
    }

    /** Keep the first of each species, transfer duplicates; spend Rare Candies on the buddy. */
    fun housekeeping() {
        var inventory = state.inventory
        var coins = state.coins
        val kept = mutableListOf<OwnedPokemon>()
        for (p in state.pokemon) {
            if (p.species.isSpecial || p.id == state.buddyId || owned.add(p.speciesId)) {
                kept += p
            } else {
                val q = Transfer.quote(p)
                inventory = inventory.add(Items.RARE_CANDY, q.rareCandies)
                coins += q.coins
                rare += q.rareCandies
                coinsFromTransfers += q.coins
                transfers++
            }
        }
        var pokemon: List<OwnedPokemon> = kept
        var buddy = pokemon.firstOrNull { it.id == state.buddyId }
        if (buddy != null) {
            val partner = pokemon.maxOf { it.level }
            while (inventory[Items.RARE_CANDY] > 0 && buddy!!.canFeed) {
                inventory = inventory.remove(Items.RARE_CANDY)
                buddy = buddy.feedRareCandy(partner)
            }
            pokemon = pokemon.map { if (it.id == buddy!!.id) buddy else it }
        }
        state = state.copy(pokemon = pokemon, inventory = inventory, coins = coins)
    }

    fun snapshot(day: Int) {
        checkpoints[day] = Snapshot(
            rank = state.rank, points = state.trainerPoints, eggs = eggs, taskEggs = taskEggs, rare = rare, superCandy = superCandy,
            coins = state.coins, drops = drops, shinies = shinies, buddyLevel = state.buddy?.level ?: 0,
            homeSeen = homeEntries.count { it in owned }, species = owned.size,
        )
    }

    fun play(): Run {
        val starter = Hatching.create(id(), HatchOption(25, "pikachu"), 0, 0, 25, rng).copy(level = 5)
        owned += starter.speciesId
        state = PlayerState(pokemon = listOf(starter), buddyId = starter.id)
        for (day in 1..days) {
            if (rng.nextDouble() < profile.activeDayChance) {
                val sessions = rng.nextInt(profile.sessionsPerDay.first, profile.sessionsPerDay.last + 1)
                repeat(sessions) {
                    val minutes = profile.sessionMinutes[weighted(profile.sessionMinutes.map { it.second })].first
                    val region = pickRegion()
                    val out = RewardEngine.effort(state, EffortEvent(minutes, deepFocus = rng.nextDouble() < 0.3, region = region, epochDay = day.toLong(), newPokemonId = id(), nowMillis = day * 86_400_000L), rng)
                    state = out.state; handleRewards(out.rewards)
                }
                val tasks = rng.nextInt(profile.tasksPerDay.first, profile.tasksPerDay.last + 1)
                repeat(tasks) { n ->
                    val tier = weighted(profile.rankWeights) + 1
                    val out = RewardEngine.achievement(state, AchievementEvent(tier, pickRegion(), hourOfDay = 9 + n, firstOfDay = n == 0, onTime = rng.nextDouble() < 0.3, focusedOnIt = rng.nextDouble() < 0.2, newPokemonId = id(), nowMillis = day * 86_400_000L), rng)
                    state = out.state; handleRewards(out.rewards)
                }
            }
            housekeeping()
            for (r in TrainerRank.unlockedRegions(state.trainerPoints)) regionUnlockDay.putIfAbsent(r, day)
            if (homeDay == null && homeEntries.all { it in owned }) homeDay = day
            if (day in listOf(30, 60, 90, 180)) snapshot(day)
        }
        return this
    }
}

private fun pct(values: List<Double>, p: Double): Double = values.sorted().let { it[((it.size - 1) * p).toInt()] }

private fun fmt(v: Double) = if (v >= 100) "%.0f".format(v) else "%.1f".format(v)

fun simulateReport(runsPerProfile: Int = 60, days: Int = 180): String {
    val sb = StringBuilder()
    sb.appendLine("# Economy simulation")
    sb.appendLine()
    sb.appendLine("Generated by `./gradlew :plan-core:simulateEconomy`. Do not edit by hand.")
    sb.appendLine()
    sb.appendLine("Plays $days days for $runsPerProfile simulated players per profile through the real reward engine: sessions hatch eggs (greedy region choice, 30% Deep Focus), tasks pay candies, drops and Egg meter points, duplicates are transferred, Rare Candies go to the buddy. Medians, with the 10th to 90th percentile in brackets.")
    sb.appendLine()
    sb.appendLine("| Profile | Days a week | Tasks a day | Sessions a day |")
    sb.appendLine("|---|---|---|---|")
    for (p in profiles) sb.appendLine("| ${p.name} | ${fmt(p.activeDayChance * 7)} | ${p.tasksPerDay.first}-${p.tasksPerDay.last} | ${p.sessionsPerDay.first}-${p.sessionsPerDay.last} |")
    sb.appendLine()
    val home = Rarity.entries.flatMap { HatchRules.pool(Region.HOME, it) }.size
    val demand = evolutionDemand(Region.HOME)
    sb.appendLine("## Targets")
    sb.appendLine()
    sb.appendLine("- Basic Home dex ($home hatchable species) in about 60 days for a typical player.")
    sb.appendLine("- Ranks quick at first, slower after: Apprentice (Kanto) within about 2 days and Ace within about 2 weeks for a typical player; each rank takes longer than the one before.")
    sb.appendLine("- Evolving everything in the Home Region once needs ${demand.evolutions} evolutions: ${demand.superCandies} Super Candies, ${demand.rareCandiesLow} to ${demand.rareCandiesHigh} Rare Candies (with and without the catch-up discount), ${demand.stones} stones, ${demand.linkCords} Link Cords, ${demand.trials} trials.")
    sb.appendLine()
    for (p in profiles) {
        val runs = (1..runsPerProfile).map { Run(p, it, days).play() }
        sb.appendLine("## ${p.name}")
        sb.appendLine()
        fun row(label: String, values: List<Double>) = sb.appendLine("| $label | ${fmt(pct(values, .5))} (${fmt(pct(values, .1))} to ${fmt(pct(values, .9))}) |")
        sb.appendLine("| Measure | Value |")
        sb.appendLine("|---|---|")
        val homeDays = runs.map { (it.homeDay ?: (days + 1)).toDouble() }
        row("Day the basic Home dex is complete (${days + 1} = not within $days)", homeDays)
        for (region in Region.entries.drop(1).take(4)) row("Day ${region.label} unlocks", runs.map { (it.regionUnlockDay[region] ?: (days + 1)).toDouble() })
        for (d in listOf(30, 60, 90)) {
            row("Rank after $d days", runs.map { it.checkpoints[d]!!.rank.toDouble() })
            row("Eggs after $d days (incl. Task Eggs)", runs.map { it.checkpoints[d]!!.eggs.toDouble() })
        }
        row("Task Eggs after 60 days", runs.map { it.checkpoints[60]!!.taskEggs.toDouble() })
        row("Rare Candies earned in 60 days (incl. transfers)", runs.map { it.checkpoints[60]!!.rare.toDouble() })
        row("Super Candies earned in 60 days", runs.map { it.checkpoints[60]!!.superCandy.toDouble() })
        row("Coins after 60 days", runs.map { it.checkpoints[60]!!.coins.toDouble() })
        row("Item drops in 60 days", runs.map { it.checkpoints[60]!!.drops.toDouble() })
        row("Buddy level after 60 days", runs.map { it.checkpoints[60]!!.buddyLevel.toDouble() })
        row("Shinies in $days days", runs.map { it.shinies.toDouble() })
        row("Species owned after 90 days", runs.map { it.checkpoints[90]!!.species.toDouble() })
        val superIncome = pct(runs.map { it.checkpoints[60]!!.superCandy.toDouble() }, .5)
        val rareIncome = pct(runs.map { it.checkpoints[60]!!.rare.toDouble() }, .5)
        sb.appendLine()
        sb.appendLine("Evolving the whole Home Region needs ${demand.superCandies} Super Candies: at the 60-day rate (${fmt(superIncome)}) that is about ${fmt(demand.superCandies / maxOf(superIncome, 0.01) * 60)} days. Rare Candies: ${demand.rareCandiesLow} to ${demand.rareCandiesHigh} needed, ${fmt(rareIncome)} earned in 60 days (about ${fmt(demand.rareCandiesLow / maxOf(rareIncome, 0.01) * 60)} to ${fmt(demand.rareCandiesHigh / maxOf(rareIncome, 0.01) * 60)} days).")
        sb.appendLine()
    }
    return sb.toString()
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/economy-report.md")
    out.parentFile?.mkdirs()
    out.writeText(simulateReport())
    println("wrote ${out.path}")
}
