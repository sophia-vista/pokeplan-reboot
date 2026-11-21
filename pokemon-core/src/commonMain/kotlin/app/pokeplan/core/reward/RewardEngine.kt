package app.pokeplan.core.reward

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.Egg
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.CareBucket
import app.pokeplan.core.pokemon.CarePerks
import app.pokeplan.core.pokemon.HealBucket
import app.pokeplan.core.pokemon.HealPerks
import app.pokeplan.core.pokemon.BaseBucket
import app.pokeplan.core.pokemon.BasePerks
import app.pokeplan.core.pokemon.LinkBucket
import app.pokeplan.core.pokemon.LinkPerks
import app.pokeplan.core.pokemon.MintBucket
import app.pokeplan.core.pokemon.MintPerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.PerkContext
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Stat
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class PlayerState(
    val pokemon: List<OwnedPokemon> = emptyList(),
    val buddyId: String? = null,
    val inventory: Inventory = Inventory(),
    val coins: Int = 0,
    val eggMeter: Int = 0,
    val trainerPoints: Int = 0,
    val caught: Set<Int> = emptySet(),
    val guardians: List<String> = emptyList(),
    val moduleGuardians: Map<String, String> = emptyMap(),
    val careLeadId: String? = null,
    val healLeadId: String? = null,
    val mintLeadId: String? = null,
    @SerialName("homeLeadId") val baseLeadId: String? = null,
    val linkLeadId: String? = null,
    val planLeadId: String? = null,
    val rolesVersion: Int = 0,
) {
    val buddy: OwnedPokemon? get() = pokemon.firstOrNull { it.id == buddyId }
    fun guardianOf(partyId: String): OwnedPokemon? = moduleGuardians[partyId]?.let { id -> pokemon.firstOrNull { it.id == id } }

    val guardianPokemon: List<OwnedPokemon> get() = Guardians.MODULES.mapNotNull { guardianOf(it) }
    val party: List<OwnedPokemon> get() = partyOf(PartyIds.PLAN)

    fun lead(partyId: String): OwnedPokemon? {
        val members = partyOf(partyId)
        val chosen = when (partyId) { PartyIds.PLAN -> planLeadId; PartyIds.CARE -> careLeadId; PartyIds.HEAL -> healLeadId; PartyIds.MINT -> mintLeadId; PartyIds.BASE -> baseLeadId; PartyIds.LINK -> linkLeadId; else -> null }
        return members.firstOrNull { it.id == chosen } ?: members.firstOrNull()
    }

    val planLead: OwnedPokemon? get() = lead(PartyIds.PLAN)

    val careLead: OwnedPokemon? get() = lead(PartyIds.CARE)

    val healLead: OwnedPokemon? get() = lead(PartyIds.HEAL)

    val mintLead: OwnedPokemon? get() = lead(PartyIds.MINT)

    val baseLead: OwnedPokemon? get() = lead(PartyIds.BASE)

    val linkLead: OwnedPokemon? get() = lead(PartyIds.LINK)

    fun partySlots(partyId: String): List<OwnedPokemon> {
        val lead = lead(partyId) ?: return emptyList()
        return listOf(lead) + partyOf(partyId).filter { it.id != lead.id }
    }

    fun partyOf(partyId: String): List<OwnedPokemon> = pokemon.filter { it.inParty && it.partyId == partyId }

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
    data class Evs(val points: Int) : Reward
    @Serializable
    @SerialName("friendship")
    data class Friendship(val amount: Int) : Reward
    @Serializable
    @SerialName("poke")
    data class Poke(val amount: Int) : Reward
    @Serializable
    @SerialName("decoration")
    data class Decoration(val decorationId: String) : Reward
    @Serializable
    @SerialName("exp")
    data class Exp(val points: Int, val name: String) : Reward
}

@Serializable
data class Outcome(val state: PlayerState, val rewards: List<Reward>)

@Serializable
data class EffortEvent(
    val minutes: Int,
    val deepFocus: Boolean,
    val region: Region,
    val lure: PokemonType? = null,
    val epochDay: Long = 0,
    val newPokemonId: String,
    val nowMillis: Long,
)

@Serializable
data class BefriendEvent(
    val speciesId: Int,
    val formIdentifier: String,
    val newPokemonId: String,
    val nowMillis: Long,
    val visitorId: String = "",
)

@Serializable
enum class RewardFocus(val label: String) {
    EXP("Exp"), EGG("Egg"), ITEMS("Items");

    companion object {
        val DEFAULT = EXP
    }
}

@Serializable
data class AchievementEvent(
    val tier: Int,
    val region: Region,
    val hourOfDay: Int = 12,
    val firstOfDay: Boolean = false,
    val onTime: Boolean = false,
    val focusedOnIt: Boolean = false,
    val newPokemonId: String,
    val nowMillis: Long,
    val picked: Boolean = false,
    val friendshipTasksToday: Int = 0,
    val focus: RewardFocus = RewardFocus.EXP,
)

@Serializable
enum class CareKind {
    @SerialName("routine_completed") ROUTINE,
    @SerialName("calm_break_completed") CALM_BREAK,
    @SerialName("weekly_rhythm") WEEKLY_RHYTHM,
    @SerialName("skill_practised") SKILL,
    @SerialName("care_play") PLAY,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class CareEvent(
    val kind: CareKind,
    val nowMillis: Long,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val pokemonId: String? = null,
)

@Serializable
enum class HealKind {
    @SerialName("activity_logged") ACTIVITY,
    @SerialName("participation") PARTICIPATION,
    @SerialName("steps_reached") STEPS,
    @SerialName("records_imported") IMPORT,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class HealEvent(
    val id: String,
    val kind: HealKind,
    val nowMillis: Long,
    val ref: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = MODULE,
) {
    companion object { const val MODULE = "heal" }
}

@Serializable
enum class MintKind {
    @SerialName("account_reconciled") RECONCILED,
    @SerialName("financial_review_completed") REVIEW,
    @SerialName("record_logged") LOGGED,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class MintEvent(
    val id: String,
    val kind: MintKind,
    val nowMillis: Long,
    val ref: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = MODULE,
    val streak: Int = 0,
) {
    companion object { const val MODULE = "mint" }
}

@Serializable
enum class BaseKind {
    @SerialName("chore_completed") CHORE,
    @SerialName("maintenance_completed") MAINTENANCE,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class BaseEvent(
    val id: String,
    val kind: BaseKind,
    val nowMillis: Long,
    val ref: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = MODULE,
) {
    companion object { const val MODULE = "home" }
}

@Serializable
enum class LinkMoment {
    @SerialName("followup_completed") FOLLOW_UP,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class LinkEvent(
    val id: String,
    val kind: LinkMoment,
    val nowMillis: Long,
    val ref: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val policy: Int = POLICY,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = MODULE,
) {
    companion object { const val MODULE = "link"; const val POLICY = 1 }
}

object Spotlight {
    fun typeFor(epochDay: Long): PokemonType = PokemonType.entries[(((epochDay % PokemonType.entries.size) + PokemonType.entries.size) % PokemonType.entries.size).toInt()]
}

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

object ModuleDrops {
    const val PERCENT = 10

    const val COIN_HANDFUL = 10

    enum class Module { CARE, HEAL, MINT, BASE, LINK }

    private fun stones(vararg ids: String) = ids.filter { it in Items.evolutionItems }

    val tables: Map<Module, Map<String, Double>> by lazy {
        mapOf(
            Module.CARE to buildMap {
                put("strawberry-sweet", 4.0); put("oval-stone", 2.0); put(Items.EVERSTONE, 2.0); put("leftovers", 0.5)
                stones("moon-stone", "shiny-stone", "dawn-stone").forEach { put(it, 1.0) }
            },
            Module.HEAL to buildMap {
                Items.vitamins.keys.forEach { put(it, 3.0) }
                put("razor-claw", 1.0); put("razor-fang", 1.0)
                stones("sun-stone", "leaf-stone", "water-stone").forEach { put(it, 1.0) }
            },
            Module.MINT to mapOf(Items.NATURE_MINT to 6.0, Items.GIMMIGHOUL_COIN to 3.0, Items.BOTTLE_CAP to 1.0, "amulet-coin" to 1.0, "star-piece" to 1.0),
            Module.BASE to buildMap {
                stones("fire-stone", "water-stone", "thunder-stone", "leaf-stone").forEach { put(it, 2.0) }
                stones("ice-stone", "dusk-stone").forEach { put(it, 1.0) }
                put(Items.EVERSTONE, 2.0); put("oval-stone", 1.0)
            },
            Module.LINK to buildMap { put(Items.LINK_CORD, 4.0); Items.tradeItems.forEach { put(it, 1.0) } },
        )
    }

    fun roll(module: Module, rng: Random): String? {
        if (rng.nextInt(100) >= PERCENT) return null
        val table = tables.getValue(module)
        var pick = rng.nextDouble() * table.values.sum()
        for ((id, w) in table) { pick -= w; if (pick < 0) return id }
        return table.keys.last()
    }

    fun add(outcome: Outcome, module: Module, rng: Random, guaranteed: String? = null): Outcome {
        var state = outcome.state
        val rewards = outcome.rewards.toMutableList()
        for (id in listOfNotNull(guaranteed, roll(module, rng))) {
            val n = if (id == Items.GIMMIGHOUL_COIN) COIN_HANDFUL else 1
            val (bag, extra) = state.inventory.addCapped(id, n)
            state = state.copy(inventory = bag, coins = state.coins + extra)
            rewards += Reward.ItemDrop(id, n)
        }
        return outcome.copy(state = state, rewards = rewards)
    }
}

object RewardEngine {
    const val COINS_PER_FIVE_MINUTES = 5

    const val CANDIES_PER_FIVE_MINUTES = 2

    const val NEW_SPECIES_CANDIES = 5
    const val NEW_SPECIES_SUPER_CANDIES = 1

    const val COINS_PER_TIER = 10

    const val MODULE_POKE = 10

    const val RHYTHM_POKE = 30

    const val LURE_WEIGHT = 3.0

    const val UNCAUGHT_WEIGHT = 6.0
    const val SPOTLIGHT_WEIGHT = 2.0

    const val DROP_BASE_PERCENT = 8
    const val DROP_PER_TIER_PERCENT = 2

    const val EGG_METER_SIZE = 5

    const val FOCUS_CANDY_PERCENT = 50

    const val EGG_FOCUS_POINT_FACTOR = 1.5

    const val ITEMS_FOCUS_DROP_FACTOR = 2.0
    const val TASK_EGG_MINUTES = 15

    const val ON_TIME_BONUS = 1.10
    const val FOCUSED_BONUS = 1.10

    const val PICKED_BONUS = 1.10

    const val TASK_FRIENDSHIP_DAILY_CAP = 10

    const val CARE_POINTS = 1

    val CARE_CANDIES: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 3, CareKind.CALM_BREAK to 4, CareKind.SKILL to 4, CareKind.WEEKLY_RHYTHM to 15, CareKind.PLAY to 1)

    val CARE_EGG_POINTS: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 1, CareKind.CALM_BREAK to 2, CareKind.SKILL to 2, CareKind.WEEKLY_RHYTHM to 0, CareKind.PLAY to 0)
    const val CARE_EGG_METER_SIZE = 3

    const val BEFRIEND_POINTS = 3
    const val BEFRIEND_IV_FLOOR = 10
    const val BEFRIEND_LUCK_IV_BONUS = 6
    const val BEFRIEND_DIRECT_JOIN_PERCENT = 10.0
    const val BEFRIEND_LUCK_JOIN_SHARE = 0.2
    const val BEFRIEND_JOIN_FRIENDSHIP = 150
    const val RHYTHM_POINTS = 5
    const val RHYTHM_BERRIES = 3

    val HEAL_CANDIES: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to 4, HealKind.PARTICIPATION to 1, HealKind.STEPS to 1, HealKind.IMPORT to 1)

    val HEAL_EGG_POINTS: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to 2, HealKind.PARTICIPATION to 1, HealKind.STEPS to 0, HealKind.IMPORT to 0)
    const val HEAL_EGG_METER_SIZE = 4
    const val HEAL_POINTS = 1

    val HEAL_TRAINER_POINTS: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to HEAL_POINTS, HealKind.PARTICIPATION to HEAL_POINTS, HealKind.STEPS to 0, HealKind.IMPORT to 0)

    val MINT_CANDIES: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 3, MintKind.REVIEW to 5, MintKind.LOGGED to 1)

    val MINT_EGG_POINTS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 2, MintKind.REVIEW to 3, MintKind.LOGGED to 1)
    const val MINT_EGG_METER_SIZE = 4
    const val MINT_POINTS = 1

    val MINT_TRAINER_POINTS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to MINT_POINTS, MintKind.REVIEW to MINT_POINTS, MintKind.LOGGED to 0)

    val HOME_CANDIES: Map<BaseKind, Int> = mapOf(BaseKind.CHORE to 2, BaseKind.MAINTENANCE to 4)

    val HOME_EGG_POINTS: Map<BaseKind, Int> = mapOf(BaseKind.CHORE to 1, BaseKind.MAINTENANCE to 2)
    const val HOME_EGG_METER_SIZE = 5
    const val HOME_POINTS = 1

    val LINK_CANDIES: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to 2)

    val LINK_EGG_POINTS: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to 1)
    const val LINK_EGG_METER_SIZE = 3
    const val LINK_POINTS = 1

    private fun scaled(base: Int, multiplier: Double): Int = maxOf(base, (base * multiplier).roundToInt())

    private fun newSpecies(known: Set<Int>, species: Int, inventory: Inventory): Triple<Inventory, Int, List<Reward>> {
        if (species in known) return Triple(inventory, 0, emptyList())
        val (withRare, rareOverflow) = inventory.addCapped(Items.RARE_CANDY, NEW_SPECIES_CANDIES)
        val (bag, superOverflow) = withRare.addCapped(Items.SUPER_CANDY, NEW_SPECIES_SUPER_CANDIES)
        return Triple(bag, rareOverflow + superOverflow, listOf(
            Reward.Candies(CandyType.RARE, NEW_SPECIES_CANDIES), Reward.Candies(CandyType.SUPER, NEW_SPECIES_SUPER_CANDIES),
        ))
    }

    private fun luckyBump(rarity: app.pokeplan.core.dex.Rarity, eggLuckPercent: Double, rng: Random) =
        if (rng.nextDouble() * 100 < eggLuckPercent) rarity.next() else rarity

    fun effort(state: PlayerState, e: EffortEvent, rng: Random): Outcome {
        val buddy = state.planLead
        val perks = Perks.total(buddy, state.party, PerkContext(sessionMinutes = e.minutes, megaActive = buddy != null && Perks.megaReady(buddy)), listOfNotNull(state.guardianOf(PartyIds.PLAN)))
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

        var coins = scaled(e.minutes / 5 * COINS_PER_FIVE_MINUTES, perks.multiplier(PerkBucket.COINS))
        rewards += Reward.Coins(coins)
        val candies = scaled(e.minutes / 5 * CANDIES_PER_FIVE_MINUTES, perks.multiplier(PerkBucket.CANDY_YIELD))
        if (candies > 0) {
            val (bag, overflow) = inventory.addCapped(Items.RARE_CANDY, candies)
            inventory = bag
            coins += overflow
            rewards += Reward.Candies(CandyType.RARE, candies)
        }
        val (afterNew, newOverflow, newRewards) = newSpecies(caught, hatched.speciesId, inventory)
        inventory = afterNew
        coins += newOverflow
        rewards += newRewards
        val berry = Items.randomBerry(rng)
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
        val buddy = state.planLead
        val perks = Perks.total(buddy, state.party, PerkContext(taskRank = tier, hourOfDay = e.hourOfDay, firstTaskOfDay = e.firstOfDay), listOfNotNull(state.guardianOf(PartyIds.PLAN)))
        val rewards = mutableListOf<Reward>()
        var inventory = state.inventory
        var coins = state.coins

        var multiplier = perks.multiplier(PerkBucket.CANDY_YIELD)
        if (e.onTime) multiplier *= ON_TIME_BONUS
        if (e.focusedOnIt) multiplier *= FOCUSED_BONUS
        if (e.picked) multiplier *= PICKED_BONUS
        var overflow = 0
        for (roll in CandyRewards.rates(tier).roll(rng, perks[PerkBucket.SUPER_CANDY_CHANCE].roundToInt())) {
            val full = scaled(roll.amount, multiplier)
            val amount = if (e.focus == RewardFocus.EXP) full else maxOf(1, full * FOCUS_CANDY_PERCENT / 100)
            val candyId = if (roll.type == CandyType.SUPER) Items.SUPER_CANDY else Items.RARE_CANDY
            val (afterCandy, extra) = inventory.addCapped(candyId, amount)
            inventory = afterCandy
            overflow += extra
            rewards += Reward.Candies(roll.type, amount)
        }
        coins += overflow

        val earned = scaled(tier * COINS_PER_TIER, perks.multiplier(PerkBucket.COINS))
        coins += earned
        rewards += Reward.Coins(earned + overflow)

        val ordinaryDrop = (DROP_BASE_PERCENT + DROP_PER_TIER_PERCENT * tier + perks[PerkBucket.ITEM_FINDER]).toDouble()
        val dropPercent = if (e.focus == RewardFocus.ITEMS) minOf(ordinaryDrop * ITEMS_FOCUS_DROP_FACTOR, 100.0) else ordinaryDrop
        if (rng.nextDouble() * 100 < dropPercent) {
            val drop = DropTable.roll(rng)
            val (afterDrop, extra) = inventory.addCapped(drop, 1)
            inventory = afterDrop
            coins += extra
            rewards += Reward.ItemDrop(drop, 1)
        }

        val warms = e.friendshipTasksToday < TASK_FRIENDSHIP_DAILY_CAP
        var pokemon = state.pokemon.map { p -> if (warms && p.id == buddy?.id) p.gainFriendship(FriendshipRules.Source.TASK) else p }

        var meter = state.eggMeter + if (e.focus == RewardFocus.EGG) kotlin.math.ceil(tier * EGG_FOCUS_POINT_FACTOR).toInt() else tier
        var caught = state.caught + state.pokemon.map { it.speciesId }
        if (meter >= EGG_METER_SIZE) {
            meter -= EGG_METER_SIZE
            val partySize = pokemon.count { it.inParty && it.partyId == PartyIds.PLAN }
            val rarity = luckyBump(Egg.rates(TASK_EGG_MINUTES, false).roll(rng), perks[PerkBucket.EGG_LUCK], rng)
            val known = state.caught + pokemon.map { it.speciesId }
            val option = HatchRules.rollWeighted(e.region, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
            val egg = Hatching.create(e.newPokemonId, option, partySize, e.nowMillis, TASK_EGG_MINUTES, rng, perks.multiplier(PerkBucket.SHINY_HUNTER))
            pokemon = pokemon + egg
            caught = known + egg.speciesId
            rewards += Reward.Hatched(egg, fromTask = true)
            val (afterNew, newOverflow, newRewards) = newSpecies(known, egg.speciesId, inventory)
            inventory = afterNew
            coins += newOverflow
            rewards += newRewards
        }
        return Outcome(
            state.copy(
                pokemon = pokemon, inventory = inventory, coins = coins, eggMeter = meter,
                trainerPoints = state.trainerPoints + TrainerRank.pointsForAchievement(tier), caught = caught,
            ),
            rewards,
        )
    }

    fun befriend(state: PlayerState, e: BefriendEvent, rng: Random): Outcome {
        val luck = CarePerks.total(state.careLead, state.partyOf(PartyIds.CARE), state.guardianOf(PartyIds.CARE))[CareBucket.VISITOR_LUCK] ?: 0.0
        val careParty = state.partyOf(PartyIds.CARE).size
        val joins = rng.nextDouble() * 100 < BEFRIEND_DIRECT_JOIN_PERCENT + luck * BEFRIEND_LUCK_JOIN_SHARE
        val first = Dex.lineRoot(e.speciesId)
        val hatch = if (joins || first == e.speciesId) HatchOption(e.speciesId, e.formIdentifier) else HatchOption(first, Dex.defaultForm(first).identifier)
        var p = Hatching.create(e.newPokemonId, hatch, careParty, e.nowMillis, TASK_EGG_MINUTES, rng)
        val floor = BEFRIEND_IV_FLOOR + (luck / Perks.BUCKET_CAP * BEFRIEND_LUCK_IV_BONUS).toInt()
        p = p.copy(partyId = PartyIds.CARE, ivs = Stat.entries.fold(p.ivs) { ivs, stat -> ivs.with(stat, maxOf(ivs[stat], floor)) })
        if (joins) p = p.copy(friendship = BEFRIEND_JOIN_FRIENDSHIP) else p = VisitorInheritance.apply(p, VisitorInheritance.plan(e.visitorId, e.newPokemonId))
        val lead = state.careLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(state.caught + state.pokemon.map { it.speciesId }, p.speciesId, state.inventory)
        return Outcome(
            state.copy(
                pokemon = state.pokemon + p, careLeadId = lead, inventory = bag, coins = state.coins + overflow,
                trainerPoints = state.trainerPoints + BEFRIEND_POINTS, caught = state.caught + p.speciesId,
            ),
            listOf(Reward.Hatched(p, fromTask = false)) + newRewards,
        )
    }

    fun tutorialEgg(state: PlayerState, region: Region, partyId: String, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = app.pokeplan.core.dex.Rarity.COMMON
        while (rarity.ordinal > 0 && HatchRules.pool(region, rarity).isEmpty()) rarity = app.pokeplan.core.dex.Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(region, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(partyId).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = partyId)
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        var next = state.copy(pokemon = state.pokemon + p, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId)
        if (p.inParty) {
            next = when (partyId) {
                PartyIds.CARE -> if (state.careLead == null) next.copy(careLeadId = p.id) else next
                PartyIds.PLAN -> if (state.planLead == null) next.copy(planLeadId = p.id) else next
                PartyIds.HEAL -> if (state.healLead == null) next.copy(healLeadId = p.id) else next
                PartyIds.MINT -> if (state.mintLead == null) next.copy(mintLeadId = p.id) else next
                PartyIds.BASE -> if (state.baseLead == null) next.copy(baseLeadId = p.id) else next
                PartyIds.LINK -> if (state.linkLead == null) next.copy(linkLeadId = p.id) else next
                else -> next
            }
        }
        return Outcome(next, listOf(Reward.Hatched(p, fromTask = true)) + newRewards)
    }

    fun careEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.CARE, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.CARE, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val careParty = state.partyOf(PartyIds.CARE).size
        val p = Hatching.create(newPokemonId, option, careParty, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.CARE)
        val lead = state.careLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, careLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun care(state: PlayerState, e: CareEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val buddy = if (e.kind == CareKind.PLAY) state.pokemon.firstOrNull { it.id == e.pokemonId } ?: state.careLead else state.careLead
        val perks = CarePerks.total(state.careLead, state.partyOf(PartyIds.CARE), state.guardianOf(PartyIds.CARE))
        val source = when (e.kind) {
            CareKind.ROUTINE -> FriendshipRules.Source.ROUTINE
            CareKind.CALM_BREAK -> FriendshipRules.Source.CALM_BREAK
            CareKind.SKILL -> FriendshipRules.Source.SKILL
            CareKind.PLAY -> FriendshipRules.Source.PET
            CareKind.WEEKLY_RHYTHM -> null
        }
        if (buddy != null && source != null) {
            var grown = buddy.gainFriendship(source)
            val extra = perks[CareBucket.FRIENDSHIP_GAIN] ?: 0.0
            if (extra > 0 && rng.nextDouble() * 100 < extra) grown = grown.gainFriendship(source)
            pokemon = pokemon.map { if (it.id == buddy.id) grown else it }
            if (grown.friendship > buddy.friendship) rewards += Reward.Friendship(grown.friendship - buddy.friendship)
        }
        val berries = when (e.kind) {
            CareKind.ROUTINE, CareKind.PLAY -> 0
            CareKind.CALM_BREAK, CareKind.SKILL -> 1
            CareKind.WEEKLY_RHYTHM -> RHYTHM_BERRIES
        }
        var inventory = state.inventory
        var coins = state.coins
        val second = perks[CareBucket.BERRY_FINDER] ?: 0.0
        repeat(berries + if (berries > 0 && second > 0 && rng.nextDouble() * 100 < second) 1 else 0) {
            val berry = Items.randomBerry(rng)
            val (bag, extra) = inventory.addCapped(berry, 1)
            inventory = bag
            coins += extra
            rewards += Reward.ItemDrop(berry, 1)
        }
        val candies = CARE_CANDIES.getValue(e.kind)
        val (bag, overflow) = inventory.addCapped(Items.RARE_CANDY, candies)
        inventory = bag
        coins += overflow
        rewards += Reward.Candies(CandyType.RARE, candies)
        val poke = when (e.kind) { CareKind.PLAY -> 0; CareKind.WEEKLY_RHYTHM -> RHYTHM_POKE; else -> MODULE_POKE }
        coins += poke
        if (poke > 0) rewards += Reward.Coins(poke)
        val points = if (e.kind == CareKind.WEEKLY_RHYTHM) RHYTHM_POINTS else CARE_POINTS
        return ModuleDrops.add(Outcome(state.copy(pokemon = pokemon, inventory = inventory, coins = coins, trainerPoints = state.trainerPoints + points), rewards), ModuleDrops.Module.CARE, rng)
    }
    fun healEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.HEAL, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.HEAL, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.HEAL).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.HEAL)
        val lead = state.healLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, healLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun heal(state: PlayerState, e: HealEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val lead = state.healLead
        val perks = HealPerks.total(lead, state.partyOf(PartyIds.HEAL), state.guardianOf(PartyIds.HEAL))
        if (lead != null && (e.kind == HealKind.ACTIVITY || e.kind == HealKind.STEPS)) {
            var grown = lead.gainFriendship(FriendshipRules.Source.ACTIVITY)
            val extra = perks[HealBucket.FRIENDSHIP_GAIN] ?: 0.0
            if (extra > 0 && rng.nextDouble() * 100 < extra) grown = grown.gainFriendship(FriendshipRules.Source.ACTIVITY)
            pokemon = pokemon.map { if (it.id == lead.id) grown else it }
            if (grown.friendship > lead.friendship) rewards += Reward.Friendship(grown.friendship - lead.friendship)
        }
        var inventory = state.inventory
        var coins = state.coins
        if (e.kind == HealKind.ACTIVITY) {
            val second = perks[HealBucket.BERRY_FINDER] ?: 0.0
            repeat(1 + if (second > 0 && rng.nextDouble() * 100 < second) 1 else 0) {
                val berry = Items.randomBerry(rng)
                val (bag, extra) = inventory.addCapped(berry, 1)
                inventory = bag
                coins += extra
                rewards += Reward.ItemDrop(berry, 1)
            }
        }
        val candies = HEAL_CANDIES.getValue(e.kind)
        val (bag, overflow) = inventory.addCapped(Items.RARE_CANDY, candies)
        inventory = bag
        coins += overflow
        rewards += Reward.Candies(CandyType.RARE, candies)
        val poke = if (e.kind == HealKind.STEPS || e.kind == HealKind.IMPORT) 0 else MODULE_POKE
        coins += poke
        if (poke > 0) rewards += Reward.Coins(poke)
        val paid = Outcome(state.copy(pokemon = pokemon, inventory = inventory, coins = coins, trainerPoints = state.trainerPoints + HEAL_TRAINER_POINTS.getValue(e.kind)), rewards)
        return if (e.kind == HealKind.STEPS || e.kind == HealKind.IMPORT) paid else ModuleDrops.add(paid, ModuleDrops.Module.HEAL, rng)
    }

    fun mintEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.MINT, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.MINT, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.MINT).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.MINT)
        val lead = state.mintLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, mintLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun mint(state: PlayerState, e: MintEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val lead = state.mintLead
        val perks = MintPerks.total(lead, state.partyOf(PartyIds.MINT), state.guardianOf(PartyIds.MINT))
        if (lead != null) {
            var grown = lead.gainFriendship(FriendshipRules.Source.LEDGER)
            val extra = perks[MintBucket.FRIENDSHIP_GAIN] ?: 0.0
            if (extra > 0 && rng.nextDouble() * 100 < extra) grown = grown.gainFriendship(FriendshipRules.Source.LEDGER)
            pokemon = pokemon.map { if (it.id == lead.id) grown else it }
            if (grown.friendship > lead.friendship) rewards += Reward.Friendship(grown.friendship - lead.friendship)
        }
        val second = perks[MintBucket.CANDY_FINDER] ?: 0.0
        val candies = MINT_CANDIES.getValue(e.kind) + if (second > 0 && rng.nextDouble() * 100 < second) 1 else 0
        val (bag, overflow) = state.inventory.addCapped(Items.RARE_CANDY, candies)
        rewards += Reward.Candies(CandyType.RARE, candies)
        return ModuleDrops.add(Outcome(state.copy(pokemon = pokemon, inventory = bag, coins = state.coins + overflow + MODULE_POKE, trainerPoints = state.trainerPoints + MINT_TRAINER_POINTS.getValue(e.kind)), rewards + Reward.Coins(MODULE_POKE)), ModuleDrops.Module.MINT, rng, if (e.kind == MintKind.REVIEW) Items.NATURE_MINT else null)
    }

    val MINT_OUTBREAK_ROLLS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 2, MintKind.REVIEW to 3)

    const val MAX_OUTBREAK_ROLLS = 7
    const val OUTBREAK_OPTIONS = 3
    const val MAX_PENDING_OUTBREAKS = 5

    private val MINT_OUTBREAK_RARITY: Map<MintKind, List<Pair<Rarity, Int>>> = mapOf(
        MintKind.RECONCILED to listOf(Rarity.UNCOMMON to 60, Rarity.RARE to 30, Rarity.SUPER_RARE to 10),
        MintKind.REVIEW to listOf(Rarity.RARE to 70, Rarity.SUPER_RARE to 30),
    )

    fun mintOutbreak(state: PlayerState, e: MintEvent, id: String, rng: Random): Outbreak? {
        val table = MINT_OUTBREAK_RARITY[e.kind] ?: return null
        val known = state.caught + state.pokemon.map { it.speciesId }
        val chosen = LinkedHashMap<Int, OutbreakOption>()
        var tries = 0
        while (chosen.size < OUTBREAK_OPTIONS && tries++ < 60) {
            var pick = rng.nextInt(100)
            var rarity = table.last().first
            for ((r, percent) in table) { if (pick < percent) { rarity = r; break }; pick -= percent }
            while (rarity.ordinal > 0 && HatchRules.pool(Region.MINT, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
            val option = HatchRules.rollWeighted(Region.MINT, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
            if (option.speciesId !in chosen) chosen[option.speciesId] = OutbreakOption(option.speciesId, option.formIdentifier)
        }
        if (chosen.isEmpty()) return null
        val rolls = ((MINT_OUTBREAK_ROLLS[e.kind] ?: 1) + (e.streak - 1).coerceAtLeast(0)).coerceAtMost(MAX_OUTBREAK_ROLLS)
        return Outbreak(id, Region.MINT, chosen.values.toList(), rolls, e.nowMillis)
    }

    fun claimMintOutbreak(state: PlayerState, outbreak: Outbreak, speciesId: Int, newPokemonId: String, nowMillis: Long, rng: Random): Outcome? {
        val option = outbreak.options.firstOrNull { it.speciesId == speciesId } ?: return null
        val known = state.caught + state.pokemon.map { it.speciesId }
        val size = state.partyOf(PartyIds.MINT).size
        val p = Hatching.create(newPokemonId, HatchOption(option.speciesId, option.formIdentifier), size, nowMillis, 0, rng, outbreak.shinyRolls.toDouble()).copy(partyId = PartyIds.MINT)
        val lead = state.mintLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, mintLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun safariVisit(state: PlayerState, e: SafariEvent, newPokemonId: String, rng: Random): Outcome? {
        val pool = SafariRules.pool(e.ref, e.spread)
        if (pool.isEmpty()) return null
        val known = state.caught + state.pokemon.map { it.speciesId }
        val weights = pool.map { if (it.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        var roll = rng.nextDouble() * weights.sum()
        var met = pool.last()
        for ((i, o) in pool.withIndex()) { roll -= weights[i]; if (roll < 0) { met = o; break } }
        val size = state.partyOf(PartyIds.LINK).size
        val p = Hatching.create(newPokemonId, HatchOption(met.speciesId, met.formIdentifier), size, e.nowMillis, 0, rng, SafariRules.SHINY_ROLLS).copy(partyId = PartyIds.LINK)
        val lead = state.linkLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, linkLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun homeEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.BASE, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.BASE, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.BASE).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.BASE)
        val lead = state.baseLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, baseLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun base(state: PlayerState, e: BaseEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val lead = state.baseLead
        val perks = BasePerks.total(lead, state.partyOf(PartyIds.BASE), state.guardianOf(PartyIds.BASE))
        if (lead != null) {
            var grown = lead.gainFriendship(FriendshipRules.Source.HABITAT)
            val extra = perks[BaseBucket.FRIENDSHIP_GAIN] ?: 0.0
            if (extra > 0 && rng.nextDouble() * 100 < extra) grown = grown.gainFriendship(FriendshipRules.Source.HABITAT)
            pokemon = pokemon.map { if (it.id == lead.id) grown else it }
            if (grown.friendship > lead.friendship) rewards += Reward.Friendship(grown.friendship - lead.friendship)
        }
        val second = perks[BaseBucket.CANDY_FINDER] ?: 0.0
        val candies = HOME_CANDIES.getValue(e.kind) + if (second > 0 && rng.nextDouble() * 100 < second) 1 else 0
        val (bag, overflow) = state.inventory.addCapped(Items.RARE_CANDY, candies)
        rewards += Reward.Candies(CandyType.RARE, candies)
        return ModuleDrops.add(Outcome(state.copy(pokemon = pokemon, inventory = bag, coins = state.coins + overflow + MODULE_POKE, trainerPoints = state.trainerPoints + HOME_POINTS), rewards + Reward.Coins(MODULE_POKE)), ModuleDrops.Module.BASE, rng)
    }

    fun linkEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.LINK, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.LINK, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.LINK).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.LINK)
        val lead = state.linkLead?.id ?: p.id.takeIf { p.inParty }
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, linkLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    fun link(state: PlayerState, e: LinkEvent, rng: Random): Outcome {
        val rewards = mutableListOf<Reward>()
        var pokemon = state.pokemon
        val lead = state.linkLead
        val perks = LinkPerks.total(lead, state.partyOf(PartyIds.LINK), state.guardianOf(PartyIds.LINK))
        if (lead != null) {
            var grown = lead.gainFriendship(FriendshipRules.Source.FOLLOW_UP)
            val extra = perks[LinkBucket.FRIENDSHIP_GAIN] ?: 0.0
            if (extra > 0 && rng.nextDouble() * 100 < extra) grown = grown.gainFriendship(FriendshipRules.Source.FOLLOW_UP)
            pokemon = pokemon.map { if (it.id == lead.id) grown else it }
            if (grown.friendship > lead.friendship) rewards += Reward.Friendship(grown.friendship - lead.friendship)
        }
        val second = perks[LinkBucket.CANDY_FINDER] ?: 0.0
        val candies = LINK_CANDIES.getValue(e.kind) + if (second > 0 && rng.nextDouble() * 100 < second) 1 else 0
        val (bag, overflow) = state.inventory.addCapped(Items.RARE_CANDY, candies)
        rewards += Reward.Candies(CandyType.RARE, candies)
        return ModuleDrops.add(Outcome(state.copy(pokemon = pokemon, inventory = bag, coins = state.coins + overflow + MODULE_POKE, trainerPoints = state.trainerPoints + LINK_POINTS), rewards + Reward.Coins(MODULE_POKE)), ModuleDrops.Module.LINK, rng)
    }
}
