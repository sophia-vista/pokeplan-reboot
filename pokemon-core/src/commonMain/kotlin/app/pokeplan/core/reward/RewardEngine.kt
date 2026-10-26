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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Everything the reward engine reads and changes about one player. */
@Serializable
data class PlayerState(
    val pokemon: List<OwnedPokemon> = emptyList(),
    val buddyId: String? = null,
    val inventory: Inventory = Inventory(),
    val coins: Int = 0,
    /** Points towards the next Task Egg. */
    val eggMeter: Int = 0,
    /** Trainer rank points, earned from rewards in every app. */
    val trainerPoints: Int = 0,
    /** Every species ever owned (the Pokédex's "caught"); it never shrinks, even after a transfer. */
    val caught: Set<Int> = emptySet(),
    /** Ids of the Pokémon slotted as Guardians (legendaries and mythicals), at most as many as the rank has slots. */
    val guardians: List<String> = emptyList(),
) {
    val buddy: OwnedPokemon? get() = pokemon.firstOrNull { it.id == buddyId }
    val guardianPokemon: List<OwnedPokemon> get() = guardians.mapNotNull { id -> pokemon.firstOrNull { it.id == id } }
    val party: List<OwnedPokemon> get() = pokemon.filter { it.inParty }

    /** The rank index (0 is the first rank). */
    val rank: Int get() = TrainerRank.rankFor(trainerPoints)
}

@Serializable
sealed interface Reward {
    @Serializable
    @SerialName("hatched")
    data class Hatched(val pokemon: OwnedPokemon, val fromTask: Boolean) : Reward
    @Serializable
    @SerialName("candies")
    data class Candies(val type: CandyType, val amount: Int) : Reward
    @Serializable
    @SerialName("coins")
    data class Coins(val amount: Int) : Reward
    @Serializable
    @SerialName("item")
    data class ItemDrop(val itemId: String, val amount: Int) : Reward
    @Serializable
    @SerialName("evs")
    /** EVs from a training plan. Training plans are gone (rules version 4); kept so older reward logs still load. */
    data class Evs(val points: Int) : Reward
    @Serializable
    @SerialName("friendship")
    data class Friendship(val amount: Int) : Reward
}

@Serializable
data class Outcome(val state: PlayerState, val rewards: List<Reward>)

/** A stretch of effort from any app: a focus session, a workout, ... *(see "Platform and apps" in docs/design.md)* */
@Serializable
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
@Serializable
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

/** What a Care moment was (docs/ecosystem.md, section 6). Mood, notes and reflections are never events: they earn nothing. */
@Serializable
enum class CareKind {
    @SerialName("routine_completed") ROUTINE,
    @SerialName("calm_break_completed") CALM_BREAK,
    @SerialName("weekly_rhythm") WEEKLY_RHYTHM,
}

/** A Care moment, with no other field: no titles, no mood, no health values. Daily caps are the caller's job. */
@Serializable
data class CareEvent(
    val kind: CareKind,
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
            Items.tradeItems.forEach { put(it, 1.0) }
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

    /** Soft pity: within a rarity, species you have not caught yet are this many times as likely. *(tune)* */
    const val UNCAUGHT_WEIGHT = 3.0
    const val SPOTLIGHT_WEIGHT = 2.0

    /** Base chance (percent) of an item drop, plus 2 per tier. *(tune)* */
    const val DROP_BASE_PERCENT = 8
    const val DROP_PER_TIER_PERCENT = 2

    /** Points that fill the Egg meter, and what a Task Egg is rolled on (a 15 minute session). *(tune)* */
    const val EGG_METER_SIZE = 10
    const val TASK_EGG_MINUTES = 15

    const val ON_TIME_BONUS = 1.10
    const val FOCUSED_BONUS = 1.10

    /** Care pays bond, berries and a small share of trainer rank; never candies or eggs. *(tune)* */
    const val CARE_POINTS = 1
    const val RHYTHM_POINTS = 5
    const val RHYTHM_BERRIES = 3

    private fun scaled(base: Int, multiplier: Double): Int = maxOf(base, (base * multiplier).roundToInt())

    private fun luckyBump(rarity: app.pokeplan.core.model.Rarity, eggLuckPercent: Double, rng: Random) =
        if (rng.nextDouble() * 100 < eggLuckPercent) rarity.next() else rarity

    fun effort(state: PlayerState, e: EffortEvent, rng: Random): Outcome {
        val buddy = state.buddy
        val perks = Perks.total(buddy, state.party, PerkContext(sessionMinutes = e.minutes, megaActive = buddy != null && Perks.megaReady(buddy)), state.guardianPokemon)
        val rewards = mutableListOf<Reward>()
        var inventory = state.inventory

        if (e.lure != null) inventory = inventory.remove(Items.lure(e.lure))

        val rarity = luckyBump(Egg.rates(e.minutes, e.deepFocus).roll(rng), perks[PerkBucket.EGG_LUCK], rng)
        val spotlight = Spotlight.typeFor(e.epochDay)
        val caught = state.caught + state.pokemon.map { it.speciesId }
        val option = HatchRules.rollWeighted(e.region, rarity, rng) { o ->
            val types = Dex.form(o.formIdentifier)!!.types
            (if (e.lure != null && e.lure in types) LURE_WEIGHT else 1.0) *
                (if (spotlight in types) SPOTLIGHT_WEIGHT else 1.0) *
                (if (o.speciesId !in caught) UNCAUGHT_WEIGHT else 1.0)
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
            state.copy(
                pokemon = pokemon, inventory = inventory, coins = state.coins + coins,
                trainerPoints = state.trainerPoints + TrainerRank.pointsForEffort(e.minutes), caught = caught + hatched.speciesId,
            ),
            rewards,
        )
    }

    fun achievement(state: PlayerState, e: AchievementEvent, rng: Random): Outcome {
        val tier = e.tier
        val buddy = state.buddy
        val perks = Perks.total(buddy, state.party, PerkContext(taskRank = tier, hourOfDay = e.hourOfDay, firstTaskOfDay = e.firstOfDay), state.guardianPokemon)
        val rewards = mutableListOf<Reward>()
        var inventory = state.inventory
        var coins = state.coins

        // Candies: always Rare Candies, sometimes Super Candies on top
        var multiplier = perks.multiplier(PerkBucket.CANDY_YIELD)
        if (e.onTime) multiplier *= ON_TIME_BONUS
        if (e.focusedOnIt) multiplier *= FOCUSED_BONUS
        var overflow = 0
        for (roll in CandyRewards.rates(tier).roll(rng, perks[PerkBucket.SUPER_CANDY_CHANCE].roundToInt())) {
            val amount = scaled(roll.amount, multiplier)
            val candyId = if (roll.type == CandyType.SUPER) Items.SUPER_CANDY else Items.RARE_CANDY
            val (afterCandy, extra) = inventory.addCapped(candyId, amount)
            inventory = afterCandy
            overflow += extra
            rewards += Reward.Candies(roll.type, amount)
        }
        coins += overflow

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

        var pokemon = state.pokemon.map { p -> if (p.id == buddy?.id) p.gainFriendship(FriendshipRules.Source.TASK) else p }

        // Egg meter
        var meter = state.eggMeter + tier
        var caught = state.caught + state.pokemon.map { it.speciesId }
        if (meter >= EGG_METER_SIZE) {
            meter -= EGG_METER_SIZE
            val partySize = pokemon.count { it.inParty }
            val rarity = luckyBump(Egg.rates(TASK_EGG_MINUTES, false).roll(rng), perks[PerkBucket.EGG_LUCK], rng)
            val known = state.caught + pokemon.map { it.speciesId }
            val option = HatchRules.rollWeighted(e.region, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
            val egg = Hatching.create(e.newPokemonId, option, partySize, e.nowMillis, TASK_EGG_MINUTES, rng, perks.multiplier(PerkBucket.SHINY_HUNTER))
            pokemon = pokemon + egg
            caught = known + egg.speciesId
            rewards += Reward.Hatched(egg, fromTask = true)
        }
        return Outcome(
            state.copy(
                pokemon = pokemon, inventory = inventory, coins = coins, eggMeter = meter,
                trainerPoints = state.trainerPoints + TrainerRank.pointsForAchievement(tier), caught = caught,
            ),
            rewards,
        )
    }

    /**
     * A Care moment: a routine gives the buddy friendship, a calm break friendship and a berry, a weekly rhythm
     * [RHYTHM_BERRIES] berries; each adds a little trainer rank. Perks don't apply: Care is about the bond, not the haul.
     */
    fun care(state: PlayerState, e: CareEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val buddy = state.buddy
        val source = when (e.kind) {
            CareKind.ROUTINE -> FriendshipRules.Source.ROUTINE
            CareKind.CALM_BREAK -> FriendshipRules.Source.CALM_BREAK
            CareKind.WEEKLY_RHYTHM -> null
        }
        if (buddy != null && source != null) {
            val grown = buddy.gainFriendship(source)
            pokemon = pokemon.map { if (it.id == buddy.id) grown else it }
            if (grown.friendship > buddy.friendship) rewards += Reward.Friendship(grown.friendship - buddy.friendship)
        }
        val berries = when (e.kind) {
            CareKind.ROUTINE -> 0
            CareKind.CALM_BREAK -> 1
            CareKind.WEEKLY_RHYTHM -> RHYTHM_BERRIES
        }
        var inventory = state.inventory
        var coins = state.coins
        repeat(berries) {
            val berry = Items.berry(Items.flavors.values.toList()[rng.nextInt(Items.flavors.size)])
            val (bag, extra) = inventory.addCapped(berry, 1)
            inventory = bag
            coins += extra
            rewards += Reward.ItemDrop(berry, 1)
        }
        val points = if (e.kind == CareKind.WEEKLY_RHYTHM) RHYTHM_POINTS else CARE_POINTS
        return Outcome(state.copy(pokemon = pokemon, inventory = inventory, coins = coins, trainerPoints = state.trainerPoints + points), rewards)
    }
}
