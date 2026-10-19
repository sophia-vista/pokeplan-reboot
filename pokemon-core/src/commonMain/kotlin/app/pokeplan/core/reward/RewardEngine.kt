package app.pokeplan.core.reward

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.CandyRewards
import app.pokeplan.core.model.CandyType
import app.pokeplan.core.model.Egg
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.PerkContext
import app.pokeplan.core.pokemon.Perks
import kotlin.math.roundToInt
import kotlin.random.Random

/** Everything the reward engine reads and changes about one player. */
data class PlayerState(
    val pokemon: List<OwnedPokemon> = emptyList(),
    val buddyId: String? = null,
    val inventory: Inventory = Inventory(),
    val coins: Int = 0,
    /** Points towards the next Task Egg. */
    val eggMeter: Int = 0,
    /** Trainer rank points, earned from rewards in every app. */
    val trainerPoints: Int = 0,
) {
    val buddy: OwnedPokemon? get() = pokemon.firstOrNull { it.id == buddyId }
    val party: List<OwnedPokemon> get() = pokemon.filter { it.inParty }

    /** The rank index (0 is the first rank). */
    val rank: Int get() = TrainerRank.rankFor(trainerPoints)
}

sealed interface Reward {
    data class Hatched(val pokemon: OwnedPokemon, val fromTask: Boolean) : Reward
    data class Candies(val type: CandyType, val amount: Int) : Reward
    data class Coins(val amount: Int) : Reward
    data class ItemDrop(val itemId: String, val amount: Int) : Reward
    data class Evs(val points: Int) : Reward
}

data class Outcome(val state: PlayerState, val rewards: List<Reward>)

/** A stretch of effort from any app: a focus session, a workout, ... *(see "Platform and apps" in docs/design.md)* */
data class EffortEvent(
    val minutes: Int,
    val deepFocus: Boolean,
    val region: Region,
    /** A Lure to use up for this egg, if any. */
    val lure: PokemonType? = null,
    /** The player's local day number, for the spotlight type. */
    val epochDay: Long = 0,
    val newPokemonId: String,
    val nowMillis: Long,
)

/** A finished piece of work of some [tier] (1 to 5): a task, a habit, a milestone... */
data class AchievementEvent(
    val tier: Int,
    val region: Region,
    val hourOfDay: Int = 12,
    val firstOfDay: Boolean = false,
    val onTime: Boolean = false,
    val focusedOnIt: Boolean = false,
    /** Id for a Task Egg if this achievement fills the Egg meter. */
    val newPokemonId: String,
    val nowMillis: Long,
)

/** Which type is in the spotlight on a given day: all 18 take turns. */
object Spotlight {
    fun typeFor(epochDay: Long): PokemonType = PokemonType.entries[(((epochDay % PokemonType.entries.size) + PokemonType.entries.size) % PokemonType.entries.size).toInt()]
}

/** What drops from finished work. Weights are relative. *(tune)* */
object DropTable {
    val weights: Map<String, Double> by lazy {
        buildMap {
            Items.vitamins.keys.forEach { put(it, 8.0) }
            Items.flavors.values.forEach { put(Items.berry(it), 4.0) }
            PokemonType.entries.forEach { put(Items.lure(it), 1.0) }
            Items.evolutionItems.forEach { put(it, 1.0) }
            put(Items.LINK_CORD, 2.0)
            put("lucky-egg", 1.0); put("oval-charm", 1.0); put("star-piece", 1.0); put("scope-lens", 1.0)
            put("shiny-charm", 0.5); put("leftovers", 1.0); put("amulet-coin", 1.0)
            put(Items.BOTTLE_CAP, 0.5); put(Items.NATURE_MINT, 0.5); put(Items.ABILITY_CAPSULE, 0.5)
            PokemonType.entries.forEach { put(Items.teraShard(it), 0.1) }
        }
    }

    fun roll(rng: Random): String {
        var pick = rng.nextDouble() * weights.values.sum()
        for ((id, w) in weights) {
            pick -= w
            if (pick < 0) return id
        }
        return weights.keys.last()
    }
}

/**
 * Turns activity into rewards. Pure: the same state, event and random source give the same outcome, so the server can
 * run this to grant rewards and clients can preview them. Daily limits and idempotency are the caller's job.
 */
object RewardEngine {
    /** Coins per 5 minutes of effort. *(tune)* */
    const val COINS_PER_FIVE_MINUTES = 1

    /** A coin per tier point of finished work, doubled. *(tune)* */
    const val COINS_PER_TIER = 2

    const val LURE_WEIGHT = 3.0
    const val SPOTLIGHT_WEIGHT = 2.0

    /** Base chance (percent) of an item drop, plus 2 per tier. *(tune)* */
    const val DROP_BASE_PERCENT = 8
    const val DROP_PER_TIER_PERCENT = 2

    /** Points that fill the Egg meter, and what a Task Egg is rolled on (a 15 minute session). *(tune)* */
    const val EGG_METER_SIZE = 10
    const val TASK_EGG_MINUTES = 15

    const val ON_TIME_BONUS = 1.10
    const val FOCUSED_BONUS = 1.10

    private fun scaled(base: Int, multiplier: Double): Int = maxOf(base, (base * multiplier).roundToInt())

    private fun luckyBump(rarity: app.pokeplan.core.model.Rarity, eggLuckPercent: Double, rng: Random) =
        if (rng.nextDouble() * 100 < eggLuckPercent) rarity.next() else rarity

    fun effort(state: PlayerState, e: EffortEvent, rng: Random): Outcome {
        val buddy = state.buddy
        val perks = Perks.total(buddy, state.party, PerkContext(sessionMinutes = e.minutes, megaActive = buddy != null && Perks.megaReady(buddy)))
        val rewards = mutableListOf<Reward>()
        var inventory = state.inventory

        if (e.lure != null) inventory = inventory.remove(Items.lure(e.lure))

        val rarity = luckyBump(Egg.rates(e.minutes, e.deepFocus).roll(rng), perks[PerkBucket.EGG_LUCK], rng)
        val spotlight = Spotlight.typeFor(e.epochDay)
        val option = HatchRules.rollWeighted(e.region, rarity, rng) { o ->
            val types = Dex.form(o.formIdentifier)!!.types
            (if (e.lure != null && e.lure in types) LURE_WEIGHT else 1.0) * (if (spotlight in types) SPOTLIGHT_WEIGHT else 1.0)
        }
        val hatched = Hatching.create(e.newPokemonId, option, state.party.size, e.nowMillis, e.minutes, rng, perks.multiplier(PerkBucket.SHINY_HUNTER))
        rewards += Reward.Hatched(hatched, fromTask = false)

        val coins = scaled(e.minutes / 5 * COINS_PER_FIVE_MINUTES, perks.multiplier(PerkBucket.COINS))
        rewards += Reward.Coins(coins)
        val berry = Items.berry(Items.flavors.values.toList()[rng.nextInt(Items.flavors.size)])
        inventory = inventory.add(berry)
        rewards += Reward.ItemDrop(berry, 1)

        val pokemon = (state.pokemon + hatched).map {
            if (it.id == buddy?.id) it.gainFriendship(FriendshipRules.Source.FOCUS_SESSION) else it
        }
        return Outcome(
            state.copy(pokemon = pokemon, inventory = inventory, coins = state.coins + coins, trainerPoints = state.trainerPoints + TrainerRank.pointsForEffort(e.minutes)),
            rewards,
        )
    }

    fun achievement(state: PlayerState, e: AchievementEvent, rng: Random): Outcome {
        val tier = e.tier
        val buddy = state.buddy
        val perks = Perks.total(buddy, state.party, PerkContext(taskRank = tier, hourOfDay = e.hourOfDay, firstTaskOfDay = e.firstOfDay))
        val rewards = mutableListOf<Reward>()
        var inventory = state.inventory
        var coins = state.coins

        // Candies
        val roll = CandyRewards.rates(tier).roll(rng, perks[PerkBucket.SUPER_CANDY_CHANCE].roundToInt())
        var multiplier = perks.multiplier(PerkBucket.CANDY_YIELD)
        if (e.onTime) multiplier *= ON_TIME_BONUS
        if (e.focusedOnIt) multiplier *= FOCUSED_BONUS
        val amount = scaled(roll.amount, multiplier)
        val candyId = if (roll.type == CandyType.SUPER) Items.SUPER_CANDY else Items.RARE_CANDY
        val (afterCandy, overflow) = inventory.addCapped(candyId, amount)
        inventory = afterCandy
        coins += overflow
        rewards += Reward.Candies(roll.type, amount)

        // Coins
        val earned = scaled(tier * COINS_PER_TIER, perks.multiplier(PerkBucket.COINS))
        coins += earned
        rewards += Reward.Coins(earned + overflow)

        // Item drop
        val dropPercent = DROP_BASE_PERCENT + DROP_PER_TIER_PERCENT * tier + perks[PerkBucket.ITEM_FINDER]
        if (rng.nextDouble() * 100 < dropPercent) {
            val drop = DropTable.roll(rng)
            val (afterDrop, extra) = inventory.addCapped(drop, 1)
            inventory = afterDrop
            coins += extra
            rewards += Reward.ItemDrop(drop, 1)
        }

        // EV training: the buddy gets the points, the rest of the party half.
        val evPoints = scaled(tier, 1.0 + perks.evGainPercent / 100.0)
        var pokemon = state.pokemon.map { p ->
            when {
                p.id == buddy?.id -> p.gainEvs(evPoints).gainFriendship(FriendshipRules.Source.TASK)
                p.inParty -> p.gainEvs(evPoints / 2)
                else -> p
            }
        }
        if (buddy != null && buddy.trainingPlan.isNotEmpty()) rewards += Reward.Evs(evPoints)

        // Egg meter
        var meter = state.eggMeter + tier
        if (meter >= EGG_METER_SIZE) {
            meter -= EGG_METER_SIZE
            val partySize = pokemon.count { it.inParty }
            val rarity = luckyBump(Egg.rates(TASK_EGG_MINUTES, false).roll(rng), perks[PerkBucket.EGG_LUCK], rng)
            val egg = Hatching.hatchAtRarity(e.newPokemonId, rarity, e.region, partySize, e.nowMillis, TASK_EGG_MINUTES, rng, perks.multiplier(PerkBucket.SHINY_HUNTER))
            pokemon = pokemon + egg
            rewards += Reward.Hatched(egg, fromTask = true)
        }
        return Outcome(
            state.copy(pokemon = pokemon, inventory = inventory, coins = coins, eggMeter = meter, trainerPoints = state.trainerPoints + TrainerRank.pointsForAchievement(tier)),
            rewards,
        )
    }
}
