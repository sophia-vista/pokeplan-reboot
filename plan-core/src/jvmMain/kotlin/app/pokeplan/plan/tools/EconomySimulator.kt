package app.pokeplan.plan.tools

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.Rarity
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

data class Profile(
    val name: String,
    val activeDayChance: Double,
    val tasksPerDay: IntRange,
    val rankWeights: List<Double>,
    val sessionsPerDay: IntRange,
    val sessionMinutes: List<Pair<Int, Double>>,
)

val profiles = listOf(
    Profile("Light", 5 / 7.0, 1..3, listOf(3.0, 3.0, 2.0, 1.0, 1.0), 0..1, listOf(15 to 30.0, 25 to 50.0, 45 to 20.0)),
    Profile("Typical", 6 / 7.0, 3..7, listOf(2.0, 3.0, 3.0, 2.0, 1.0), 1..3, listOf(25 to 40.0, 45 to 40.0, 60 to 20.0)),
    Profile("Heavy", 0.95, 6..12, listOf(1.0, 2.0, 3.0, 3.0, 2.0), 2..5, listOf(25 to 30.0, 60 to 40.0, 90 to 30.0)),
)

data class Walker(val name: String, val steps: Int, val daysPerWeek: Double, val readFamilies: Int)

val walkers = listOf(
    Walker("Light walker", 3_500, 4.0, 1),
    Walker("Typical walker", 7_000, 6.0, 1),
    Walker("Active walker", 11_000, 7.0, 2),
)

private fun walk(w: Walker, days: Int, seed: Int): Pair<Int, Int> {
    val rng = Random(seed)
    var state = PlayerState(pokemon = listOf(Hatching.create("w", HatchOption(25, "pikachu"), 0, 0, 25, rng).copy(level = 5)))
    var candies = 0; var points = 0; var n = 0
    fun pay(kind: app.pokeplan.core.reward.HealKind) {
        val out = RewardEngine.heal(state, app.pokeplan.core.reward.HealEvent("w-${n++}", kind, 0, "r"), rng)
        state = out.state
        candies += out.rewards.filterIsInstance<Reward.Candies>().sumOf { it.amount }
        points += RewardEngine.HEAL_EGG_POINTS.getValue(kind)
    }
    for (day in 1..days) {
        if (rng.nextDouble() >= w.daysPerWeek / 7) continue
        val steps = (w.steps * (0.7 + rng.nextDouble() * 0.6)).toInt()
        repeat((steps / 2_000).coerceAtMost(5)) { pay(app.pokeplan.core.reward.HealKind.STEPS) }
        repeat(w.readFamilies.coerceAtMost(3)) { pay(app.pokeplan.core.reward.HealKind.IMPORT) }
    }
    return candies to points
}

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
    val planEntries: Set<Int> = Rarity.entries.flatMap { HatchRules.pool(Region.PLAN, it) }.map { it.speciesId }.toSet()
    var planDay: Int? = null
    val careEntries: Set<Int> = Rarity.entries.flatMap { HatchRules.pool(Region.CARE, it) }.map { it.speciesId }.toSet()
    var careDay: Int? = null
    var eggs = 0; var taskEggs = 0; var shinies = 0
    var rare = 0; var superCandy = 0; var coinsFromTransfers = 0
    var drops = 0; var transfers = 0
    val checkpoints = mutableMapOf<Int, Snapshot>()

    data class Snapshot(val rank: Int, val points: Int, val eggs: Int, val taskEggs: Int, val rare: Int, val superCandy: Int, val coins: Int, val drops: Int, val shinies: Int, val buddyLevel: Int, val planSeen: Int, val species: Int)

    fun id() = "sim-${nextId++}"

    fun pickRegion(): Region {
        val open = TrainerRank.unlockedRegions(state.trainerPoints)
        val unseen = open.associateWith { r ->
            Rarity.entries.flatMap { HatchRules.pool(r, it) }.map { it.speciesId }.toSet().count { it !in owned }
        }
        if (unseen.getValue(Region.PLAN) > 0) return Region.PLAN
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
            is Reward.Candies -> if (r.type == app.pokeplan.core.reward.CandyType.SUPER) superCandy += r.amount else rare += r.amount
            is Reward.ItemDrop -> drops++
            else -> {}
        }
    }

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
            planSeen = planEntries.count { it in owned }, species = owned.size,
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
                repeat(tasks) {
                    val tier = weighted(profile.rankWeights) + 1
                    val out = RewardEngine.achievement(state, AchievementEvent(tier, pickRegion(), onTime = rng.nextDouble() < 0.3, focusedOnIt = rng.nextDouble() < 0.2, newPokemonId = id(), nowMillis = day * 86_400_000L), rng)
                    state = out.state; handleRewards(out.rewards)
                }
            }
            housekeeping()
            if (careDay == null && careEntries.all { it in owned }) careDay = day
            if (planDay == null && planEntries.all { it in owned }) planDay = day
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
    val plan = Rarity.entries.flatMap { HatchRules.pool(Region.PLAN, it) }.size
    val demand = evolutionDemand(Region.PLAN)
    sb.appendLine("## Targets")
    sb.appendLine()
    sb.appendLine("- Basic Plan dex ($plan hatchable species) in about 45 days for a typical player (rules version 12: easy to fill, hard to perfect).")
    sb.appendLine("- Ranks quick at first, slower after: Apprentice within about 2 days and Ace within about 2 weeks for a typical player; each rank takes longer than the one before.")
    sb.appendLine("- Evolving everything in the Plan Region once needs ${demand.evolutions} evolutions: ${demand.superCandies} Super Candies, ${demand.rareCandiesLow} to ${demand.rareCandiesHigh} Rare Candies (with and without the catch-up discount), ${demand.stones} stones, ${demand.linkCords} Link Cords, ${demand.trials} trials.")
    sb.appendLine()
    var typicalRare60 = 0.0
    for (p in profiles) {
        val runs = (1..runsPerProfile).map { Run(p, it, days).play() }
        sb.appendLine("## ${p.name}")
        sb.appendLine()
        fun row(label: String, values: List<Double>) = sb.appendLine("| $label | ${fmt(pct(values, .5))} (${fmt(pct(values, .1))} to ${fmt(pct(values, .9))}) |")
        sb.appendLine("| Measure | Value |")
        sb.appendLine("|---|---|")
        val planDays = runs.map { (it.planDay ?: (days + 1)).toDouble() }
        row("Day the basic Plan dex is complete (${days + 1} = not within $days)", planDays)
        row("Day the basic Care dex is complete (${days + 1} = not within $days)", runs.map { (it.careDay ?: (days + 1)).toDouble() })
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
        if (p.name == "Typical") typicalRare60 = pct(runs.map { it.checkpoints[60]!!.rare.toDouble() }, .5)
        sb.appendLine("Evolving the whole Plan Region needs ${demand.superCandies} Super Candies: at the 60-day rate (${fmt(superIncome)}) that is about ${fmt(demand.superCandies / maxOf(superIncome, 0.01) * 60)} days. Rare Candies: ${demand.rareCandiesLow} to ${demand.rareCandiesHigh} needed, ${fmt(rareIncome)} earned in 60 days (about ${fmt(demand.rareCandiesLow / maxOf(rareIncome, 0.01) * 60)} to ${fmt(demand.rareCandiesHigh / maxOf(rareIncome, 0.01) * 60)} days).")
        sb.appendLine()
    }
    sb.appendLine("## Heal: steps and reading records in (rules version 25)")
    sb.appendLine()
    sb.appendLine("Steps pay one payment for each full 2,000 steps of the day, at most 5 a day (`HealRules.STEPS_PER_REWARD`, `STEPS_PAID_PER_DAY`): ${RewardEngine.HEAL_CANDIES.getValue(app.pokeplan.core.reward.HealKind.STEPS)} Rare Candy and a friendship step for Heal's lead each, no berry, no Egg meter point, no trainer point, no item drop. Reading records in pays ${RewardEngine.HEAL_CANDIES.getValue(app.pokeplan.core.reward.HealKind.IMPORT)} Rare Candy for each family of record that brought something new, once a day, at most 3 a day (`IMPORTS_PAID_PER_DAY`). Medians over $runsPerProfile simulated walkers of $days days; a day's steps vary from 70% to 130% of the usual. The last column compares with the Typical Plan player's Rare Candies in 60 days (${fmt(typicalRare60)}, which includes transfers).")
    sb.appendLine()
    sb.appendLine("| Walker | Steps on a walking day | Days a week | Families read a day | Rare Candies a day | Rare Candies in 60 days | Compared with a Typical Plan player |")
    sb.appendLine("|---|---|---|---|---|---|---|")
    for (w in walkers) {
        val got = (1..runsPerProfile).map { walk(w, 60, it).first.toDouble() }
        val median = pct(got, .5)
        sb.appendLine("| ${w.name} | ${"%,d".format(w.steps)} | ${fmt(w.daysPerWeek)} | ${w.readFamilies} | ${fmt(median / 60)} | ${fmt(median)} | ${fmt(median / maxOf(typicalRare60, 0.01) * 100)}% |")
    }
    sb.appendLine()
    sb.appendLine("Steps add no Egg meter points and no trainer points, so they cannot make Field Eggs or ranks faster; a full day of walking (5 payments) plus two reads is 7 Rare Candies, about two logged activities.")
    sb.appendLine()
    return sb.toString()
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/economy-report.md")
    out.parentFile?.mkdirs()
    out.writeText(simulateReport())
    println("wrote ${out.path}")
}
