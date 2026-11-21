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

/** Everything the reward engine reads and changes about one player. */
@Serializable
data class PlayerState(
    val pokemon: List<OwnedPokemon> = emptyList(),
    /** The global buddy: the main companion, shown across the app. Cosmetic: it never changes a reward (docs/pokelife POKEMON_ECOSYSTEM.md, section 2.3). */
    val buddyId: String? = null,
    val inventory: Inventory = Inventory(),
    val coins: Int = 0,
    /** Points towards the next Task Egg. */
    val eggMeter: Int = 0,
    /** Trainer rank points, earned from rewards in every app. */
    val trainerPoints: Int = 0,
    /** Every species ever owned (the Pokédex's "caught"); it never shrinks, even after a transfer. */
    val caught: Set<Int> = emptySet(),
    /** The old global Guardian slots (before 0.158.0): the app moves them into [moduleGuardians] once on load and empties this. */
    val guardians: List<String> = emptyList(),
    /** Each module's Guardian (a legendary or mythical), by party id (`PartyIds`): one slot per module, its signature perk applying to that module only. */
    val moduleGuardians: Map<String, String> = emptyMap(),
    /** The lead of Care's party (slot 1); null means its first member. */
    val careLeadId: String? = null,
    /** The lead of Heal's party (slot 1); null means its first member. */
    val healLeadId: String? = null,
    /** The lead of Mint's party (slot 1); null means its first member. */
    val mintLeadId: String? = null,
    /** The lead of Base's party (slot 1); null means its first member. */
    @SerialName("homeLeadId") val baseLeadId: String? = null,
    /** The lead of Link's party (slot 1); null means its first member. */
    val linkLeadId: String? = null,
    /** The lead of Plan's party (slot 1); null means its first member. Before roles were split (0) this was the buddy; see [rolesVersion]. */
    val planLeadId: String? = null,
    /** 0 for a save from before the buddy and the party leads were separate roles; 1 once [planLeadId] has been set from the old buddy. */
    val rolesVersion: Int = 0,
) {
    val buddy: OwnedPokemon? get() = pokemon.firstOrNull { it.id == buddyId }
    /** The module's Guardian, if it has one and still owns it. */
    fun guardianOf(partyId: String): OwnedPokemon? = moduleGuardians[partyId]?.let { id -> pokemon.firstOrNull { it.id == id } }

    /** Every module's Guardian, in slot order. */
    val guardianPokemon: List<OwnedPokemon> get() = Guardians.MODULES.mapNotNull { guardianOf(it) }
    /** Plan's party, the one whose lead and perks the reward engine uses for Plan's rewards. */
    val party: List<OwnedPokemon> get() = partyOf(PartyIds.PLAN)

    /** A module party's lead (its slot 1): the one chosen if it is still in the party, else the party's first member, else none. */
    fun lead(partyId: String): OwnedPokemon? {
        val members = partyOf(partyId)
        val chosen = when (partyId) { PartyIds.PLAN -> planLeadId; PartyIds.CARE -> careLeadId; PartyIds.HEAL -> healLeadId; PartyIds.MINT -> mintLeadId; PartyIds.BASE -> baseLeadId; PartyIds.LINK -> linkLeadId; else -> null }
        return members.firstOrNull { it.id == chosen } ?: members.firstOrNull()
    }

    /** Plan's lead: the Pokémon whose perks and friendship Plan's rewards use. Not the global buddy. */
    val planLead: OwnedPokemon? get() = lead(PartyIds.PLAN)

    val careLead: OwnedPokemon? get() = lead(PartyIds.CARE)

    val healLead: OwnedPokemon? get() = lead(PartyIds.HEAL)

    val mintLead: OwnedPokemon? get() = lead(PartyIds.MINT)

    val baseLead: OwnedPokemon? get() = lead(PartyIds.BASE)

    val linkLead: OwnedPokemon? get() = lead(PartyIds.LINK)

    /** A party in slot order: the lead first, then the members in the order they joined. */
    fun partySlots(partyId: String): List<OwnedPokemon> {
        val lead = lead(partyId) ?: return emptyList()
        return listOf(lead) + partyOf(partyId).filter { it.id != lead.id }
    }

    fun partyOf(partyId: String): List<OwnedPokemon> = pokemon.filter { it.inParty && it.partyId == partyId }

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
    /** Poké, World's own money. Only World's entries in the reward log carry it; the reward engine never pays it. */
    @Serializable
    @SerialName("poke")
    data class Poke(val amount: Int) : Reward
    /** A Secret Base decoration (`app.pokeplan.base.Decorations`, BASE.md 9.5), found by Base's upkeep. Only its id: the catalogue lives in base-core. */
    @Serializable
    @SerialName("decoration")
    data class Decoration(val decorationId: String) : Reward
    /** Exp a PLAN event's companion gained when an occurrence ended (PLAN.md 8.2): [points] of exp for the Pokémon called [name]. */
    @Serializable
    @SerialName("exp")
    data class Exp(val points: Int, val name: String) : Reward
}

@Serializable
data class Outcome(val state: PlayerState, val rewards: List<Reward>)

/** A stretch of effort from any app: a focus session, a workout, ... *(see "Platform and modules" in docs/design.md)* */
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

/**
 * A Care visitor became a friend (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, "Befriending"): the species the player chose to befriend.
 * Carries nothing but the visitor, so no routine, mood or note ever reaches the world.
 */
@Serializable
data class BefriendEvent(
    val speciesId: Int,
    val formIdentifier: String,
    val newPokemonId: String,
    val nowMillis: Long,
    /** The visitor's own id: an egg it leaves may take light traits from it ([VisitorInheritance]); empty for a friend of an old save. */
    val visitorId: String = "",
)

/**
 * What a task pays most of (owner, 20 November 2026; docs/economy.md "Task reward focus"): [EXP] is the ordinary payout, exactly as it was
 * before there was a choice; [EGG] and [ITEMS] trade half the candies for more Egg points or a better item chance. Poké and trainer points never change.
 */
@Serializable
enum class RewardFocus(val label: String) {
    EXP("Exp"), EGG("Egg"), ITEMS("Items");

    companion object {
        /** The focus a task read from an old save has: the ordinary payout. */
        val DEFAULT = EXP
    }
}

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
    /** The task was the one "Pick for me" chose. */
    val picked: Boolean = false,
    /** Paid work that already warmed Plan's lead today; past [RewardEngine.TASK_FRIENDSHIP_DAILY_CAP] it adds no friendship. */
    val friendshipTasksToday: Int = 0,
    /** What the task pays most of; [RewardFocus.EXP] pays the ordinary amounts. */
    val focus: RewardFocus = RewardFocus.EXP,
)

/**
 * What a Care moment was (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md). Diary cards, journal entries and notes are never events: they earn
 * nothing. A practised skill carries no word of which skill it was.
 */
@Serializable
enum class CareKind {
    @SerialName("routine_completed") ROUTINE,
    @SerialName("calm_break_completed") CALM_BREAK,
    @SerialName("weekly_rhythm") WEEKLY_RHYTHM,
    @SerialName("skill_practised") SKILL,
    /** Care Play: time spent with a Pokémon ([CareEvent.pokemonId]), at most once per Pokémon per day (CARE.md section 4). */
    @SerialName("care_play") PLAY,
}

/** A Care moment, with no other field: no titles, no mood, no health values. Daily caps are the caller's job. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class CareEvent(
    val kind: CareKind,
    val nowMillis: Long,
    /** For [CareKind.PLAY]: the Pokémon played with, who takes the friendship. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val pokemonId: String? = null,
)

/**
 * What a HEAL moment was (docs/pokelife `MODULES/HEAL.md` section 12). Only participation counts: a logged activity session, or one
 * hydration entry a day for a player who has opted in. Weight, sleep, calories, heart rate, symptoms, medication, notes and
 * every other health value are never events (section 12.3).
 */
@Serializable
enum class HealKind {
    @SerialName("activity_logged") ACTIVITY,
    @SerialName("participation") PARTICIPATION,
    /** One full amount of steps (`HealRules.STEPS_PER_REWARD`) reached today: a count, never the figure itself (owner, 14 November 2026). */
    @SerialName("steps_reached") STEPS,
    /** Reading records in from outside (Health Connect, Huawei Health's pages) that brought something new: a family of record, once a day, never a value. */
    @SerialName("records_imported") IMPORT,
}

/**
 * The whole of what HEAL tells the shared world (HEAL.md section 12.4, INTEGRATIONS_AND_PRIVACY.md section 13): a stable event [id] (so a
 * replay is recognised and never pays twice), the source [module], an opaque record reference [ref] (a random key that means nothing outside HEAL), the
 * broad [kind] and the [nowMillis] it happened. No duration, distance, volume, type of activity or any measurement.
 */
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

/**
 * What a MINT moment was (docs/pokelife `MODULES/MINT.md` section 16). Only administrative organisation counts: reconciling an account, or finishing
 * a periodic financial review. Entering a purchase, receiving income, a balance, a budget outcome or any amount is never an event (section 16.2).
 */
@Serializable
enum class MintKind {
    @SerialName("account_reconciled") RECONCILED,
    @SerialName("financial_review_completed") REVIEW,
    /** A purchase, a payment or a transfer the player logged (MINT.md 16.2, 20 November 2026): one flat reward, never based on an amount. */
    @SerialName("record_logged") LOGGED,
}

/**
 * The whole of what MINT tells the shared world (MINT.md section 16.3): a stable event [id] (so a replay is recognised and never pays twice), the
 * source [module], an opaque record reference [ref] (a random key that means nothing outside MINT), the broad [kind] and the [nowMillis] it
 * happened. No amount, currency, account, provider, merchant, category, budget, bill or note.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class MintEvent(
    val id: String,
    val kind: MintKind,
    val nowMillis: Long,
    val ref: String,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = MODULE,
    /** Only for a review: how many weeks in a row (this one included) had one. A count of weeks, no figure; it adds shiny rolls to the outbreak (POKEMON_ECOSYSTEM.md 10.0). */
    val streak: Int = 0,
) {
    companion object { const val MODULE = "mint" }
}

/**
 * What a HOME moment was (docs/pokelife `MODULES/BASE.md` section 9). Only finished work counts: a chore done, or a piece of maintenance done.
 * Creating or editing a record, a quantity, a warranty, a note, a restocking entry or a reminder is never an event (section 9.2).
 */
@Serializable
enum class BaseKind {
    @SerialName("chore_completed") CHORE,
    @SerialName("maintenance_completed") MAINTENANCE,
}

/**
 * The whole of what HOME tells the shared world (BASE.md section 9.4): a stable event [id] (so a replay is recognised and never pays twice), the
 * source [module], an opaque record reference [ref], the broad [kind] and the [nowMillis] it happened. No title, area, supply, asset, serial,
 * cost, note or attachment.
 */
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

/**
 * What a LINK moment was (docs/pokelife `MODULES/LINK.md` section 9.2). Only a follow-up the user finished counts. Adding or importing people, notes,
 * dates, tags, favourites, and cancelling, reopening, editing or rescheduling a follow-up are never events.
 */
@Serializable
enum class LinkMoment {
    @SerialName("followup_completed") FOLLOW_UP,
}

/**
 * The whole of what LINK tells the shared world (LINK.md section 4.2): a stable event [id] (so a replay is recognised and never pays twice), the
 * source [module], an opaque record reference [ref] (a random key that means nothing outside LINK), the broad [kind], the [nowMillis] it happened and the
 * eligibility policy [policy] version. No name, number, category, tag, note, date, follow-up title or context, and not who it was about.
 */
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
 * The life modules' item drops (owner, 14 November 2026: "themed small chance", and Nature Mints in MINT): each paid moment of Care, Heal, Mint,
 * Base and Link has a [PERCENT] chance of one item from its own table, so every module has a way to evolution items, and the shop sells the
 * essential ones (a sure path). MINT's finished review also pays one Nature Mint every time. Weights are relative. *(tune)*
 */
object ModuleDrops {
    const val PERCENT = 10

    /** Gimmighoul Coins come by the handful (Gimmighoul needs 999). */
    const val COIN_HANDFUL = 10

    enum class Module { CARE, HEAL, MINT, BASE, LINK }

    private fun stones(vararg ids: String) = ids.filter { it in Items.evolutionItems }

    val tables: Map<Module, Map<String, Double>> by lazy {
        mapOf(
            // Care: gentle things. Sweets, the stones of soft evolutions, and the Everstone for a Pokémon that should stay as it is.
            Module.CARE to buildMap {
                put("strawberry-sweet", 4.0); put("oval-stone", 2.0); put(Items.EVERSTONE, 2.0); put("leftovers", 0.5)
                stones("moon-stone", "shiny-stone", "dawn-stone").forEach { put(it, 1.0) }
            },
            // Heal: training. Vitamins, and the claws and fangs of the outdoors, the sun and the leaf.
            Module.HEAL to buildMap {
                Items.vitamins.keys.forEach { put(it, 3.0) }
                put("razor-claw", 1.0); put("razor-fang", 1.0)
                stones("sun-stone", "leaf-stone", "water-stone").forEach { put(it, 1.0) }
            },
            // Mint: Nature Mints above all, and things of value: Gimmighoul Coins, a Bottle Cap, an Amulet Coin, a Star Piece.
            Module.MINT to mapOf(Items.NATURE_MINT to 6.0, Items.GIMMIGHOUL_COIN to 3.0, Items.BOTTLE_CAP to 1.0, "amulet-coin" to 1.0, "star-piece" to 1.0),
            // Base: the house. The stones of fire, water, thunder, leaf and ice, and the Everstone.
            Module.BASE to buildMap {
                stones("fire-stone", "water-stone", "thunder-stone", "leaf-stone").forEach { put(it, 2.0) }
                stones("ice-stone", "dusk-stone").forEach { put(it, 1.0) }
                put(Items.EVERSTONE, 2.0); put("oval-stone", 1.0)
            },
            // Link: what friends pass along. The Link Cord and the items a trade evolution needs.
            Module.LINK to buildMap { put(Items.LINK_CORD, 4.0); Items.tradeItems.forEach { put(it, 1.0) } },
        )
    }

    /** One roll: an item, or null (most of the time). */
    fun roll(module: Module, rng: Random): String? {
        if (rng.nextInt(100) >= PERCENT) return null
        val table = tables.getValue(module)
        var pick = rng.nextDouble() * table.values.sum()
        for ((id, w) in table) { pick -= w; if (pick < 0) return id }
        return table.keys.last()
    }

    /** [outcome] with [module]'s drop roll added (an item over the bag limit becomes Poké, as everywhere). */
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

/**
 * Turns activity into rewards. Pure: the same state, event and random source give the same outcome, so the server can
 * run this to grant rewards and clients can preview them. Daily limits and idempotency are the caller's job.
 */
object RewardEngine {
    /** Poké per 5 minutes of effort: 1 a minute (rules version 26; it was 1 per 5 minutes). *(tune)* */
    const val COINS_PER_FIVE_MINUTES = 5

    /** Rare Candies per 5 minutes of effort (a 25 minute session pays 10). *(tune)* */
    const val CANDIES_PER_FIVE_MINUTES = 2

    /** Rare Candies and a Super Candy the first time a species joins, from any source, so it can evolve. *(tune)* */
    const val NEW_SPECIES_CANDIES = 5
    const val NEW_SPECIES_SUPER_CANDIES = 1

    /** Poké per tier point of finished work (rules version 26; it was 2). *(tune)* */
    const val COINS_PER_TIER = 10

    /**
     * Poké for each deliberate paid moment of a life module (rules version 26, owner 14 November 2026: the economy was stingy outside World).
     * Care Play, Heal's step and import payments and a befriended visitor pay none, as they drop no item either. *(tune)*
     */
    const val MODULE_POKE = 10

    /** Care's weekly rhythm pays three moments' worth. */
    const val RHYTHM_POKE = 30

    const val LURE_WEIGHT = 3.0

    /**
     * Soft pity: within a rarity, species you have not caught yet are this many times as likely. High on purpose: filling
     * the Pokédex should be easy; a shiny or a Pokémon with great IVs is what stays hard. *(tune)*
     */
    const val UNCAUGHT_WEIGHT = 6.0
    const val SPOTLIGHT_WEIGHT = 2.0

    /** Base chance (percent) of an item drop, plus 2 per tier. *(tune)* */
    const val DROP_BASE_PERCENT = 8
    const val DROP_PER_TIER_PERCENT = 2

    /** Points that fill the Egg meter, and what a Task Egg is rolled on (a 15 minute session). *(tune)* */
    const val EGG_METER_SIZE = 5

    /** A task with the Egg or Items focus pays this percent of each candy roll (rounded down, at least 1) (docs/economy.md, task reward focus) *(tune)*. */
    const val FOCUS_CANDY_PERCENT = 50

    /** With the Egg focus a task fills the Egg meter by `tier` times this, rounded up *(tune)*. */
    const val EGG_FOCUS_POINT_FACTOR = 1.5

    /** With the Items focus the item-drop chance is multiplied by this, up to 100% *(tune)*. */
    const val ITEMS_FOCUS_DROP_FACTOR = 2.0
    const val TASK_EGG_MINUTES = 15

    const val ON_TIME_BONUS = 1.10
    const val FOCUSED_BONUS = 1.10

    /** Taking the task "Pick for me" chose: candies × this. *(tune)* */
    const val PICKED_BONUS = 1.10

    /** Finished work raises Plan's lead's friendship this many times a day at most. *(tune)* */
    const val TASK_FRIENDSHIP_DAILY_CAP = 10

    /** Care pays bond, berries, Rare Candies, Care Eggs and a small share of trainer rank. *(tune)* */
    const val CARE_POINTS = 1

    /** Rare Candies for each Care moment. *(tune)* */
    val CARE_CANDIES: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 3, CareKind.CALM_BREAK to 4, CareKind.SKILL to 4, CareKind.WEEKLY_RHYTHM to 15, CareKind.PLAY to 1)

    /**
     * Care's Egg meter, kept by the app (it never reaches the server): each Care moment adds its points, and a full meter
     * gives a Care Egg ([careEgg]). With every routine, calm break and skill done that is 5 eggs a day; a few moments, 1 or 2.
     */
    val CARE_EGG_POINTS: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 1, CareKind.CALM_BREAK to 2, CareKind.SKILL to 2, CareKind.WEEKLY_RHYTHM to 0, CareKind.PLAY to 0)
    const val CARE_EGG_METER_SIZE = 3

    /**
     * Befriending a visitor: trainer points, the IV floor of what joins (visitor luck adds up to the bonus), the chance the
     * visitor itself joins rather than leaving an egg of its family's first stage, and its friendship then. *(tune)*
     */
    const val BEFRIEND_POINTS = 3
    const val BEFRIEND_IV_FLOOR = 10
    const val BEFRIEND_LUCK_IV_BONUS = 6
    const val BEFRIEND_DIRECT_JOIN_PERCENT = 10.0
    /** Each percent of Visitor luck adds this share of a percent to the direct-join chance. */
    const val BEFRIEND_LUCK_JOIN_SHARE = 0.2
    const val BEFRIEND_JOIN_FRIENDSHIP = 150
    const val RHYTHM_POINTS = 5
    const val RHYTHM_BERRIES = 3

    /** Rare Candies for each HEAL moment (Field Research). *(tune)* */
    val HEAL_CANDIES: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to 4, HealKind.PARTICIPATION to 1, HealKind.STEPS to 1, HealKind.IMPORT to 1)

    /** Field Research's meter, kept by the app: each moment adds its points and a full meter discovers a Field Egg ([healEgg]). *(tune)* */
    val HEAL_EGG_POINTS: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to 2, HealKind.PARTICIPATION to 1, HealKind.STEPS to 0, HealKind.IMPORT to 0)
    const val HEAL_EGG_METER_SIZE = 4
    const val HEAL_POINTS = 1

    /** Trainer points for each HEAL moment: steps and imports pay candies (and steps friendship) only, so Heal's trainer points stay at most 4 a day. *(tune)* */
    val HEAL_TRAINER_POINTS: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to HEAL_POINTS, HealKind.PARTICIPATION to HEAL_POINTS, HealKind.STEPS to 0, HealKind.IMPORT to 0)

    /** Rare Candies for each MINT moment (Ledger Research). *(tune)* */
    val MINT_CANDIES: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 3, MintKind.REVIEW to 5, MintKind.LOGGED to 1)

    /** Ledger Research's meter, kept by the app: each moment adds its points and a full meter discovers a Ledger Egg ([mintEgg]). *(tune)* */
    val MINT_EGG_POINTS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 2, MintKind.REVIEW to 3, MintKind.LOGGED to 1)
    const val MINT_EGG_METER_SIZE = 4
    const val MINT_POINTS = 1

    /** Trainer points for each MINT moment: logging a record pays none, so Mint's trainer points stay at most 3 a day. *(tune)* */
    val MINT_TRAINER_POINTS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to MINT_POINTS, MintKind.REVIEW to MINT_POINTS, MintKind.LOGGED to 0)

    /** Rare Candies for each HOME moment (Habitat Discovery). *(tune)* */
    val HOME_CANDIES: Map<BaseKind, Int> = mapOf(BaseKind.CHORE to 2, BaseKind.MAINTENANCE to 4)

    /** Habitat Discovery's meter, kept by the app: each moment adds its points and a full meter discovers a Habitat Egg ([homeEgg]). *(tune)* */
    val HOME_EGG_POINTS: Map<BaseKind, Int> = mapOf(BaseKind.CHORE to 1, BaseKind.MAINTENANCE to 2)
    const val HOME_EGG_METER_SIZE = 5
    const val HOME_POINTS = 1

    /** Rare Candies for each LINK moment (Postcard Trail). *(tune)* */
    val LINK_CANDIES: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to 2)

    /** Postcard Trail's track, kept by the app: each finished follow-up adds its points and a full track discovers a Postcard Egg ([linkEgg]); three follow-ups, as LINK.md 9.3 asks. *(tune)* */
    val LINK_EGG_POINTS: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to 1)
    const val LINK_EGG_METER_SIZE = 3
    const val LINK_POINTS = 1

    private fun scaled(base: Int, multiplier: Double): Int = maxOf(base, (base * multiplier).roundToInt())

    /**
     * Adds [NEW_SPECIES_CANDIES] and [NEW_SPECIES_SUPER_CANDIES] when [species] was not in [known] (the first of its kind).
     * Returns the bag, the coins of any overflow, and the rewards to show (none for a species already had).
     */
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

        // Candies: always Rare Candies, sometimes Super Candies on top
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

    /**
     * A Care moment: a routine gives the buddy friendship, a calm break or a practised skill friendship and a berry, a
     * weekly rhythm [RHYTHM_BERRIES] berries; each pays [CARE_CANDIES] Rare Candies and a little trainer rank. Only Care's
     * own perks apply.
     */
    /**
     * A friend made through Care. Usually it leaves an egg of its family's first stage, like an egg from the Day Care (a
     * Pikachu leaves a Pichu egg), which hatches at once into Care's party if there is room (else the PC); rarely the
     * visitor itself joins, with a high starting friendship. Both have a floor on their IVs. Visitor luck from Care's party
     * raises the floor and the chance a little. [rng] rolls the egg.
     */
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
        val lead = state.careLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has (chosen or implicit: its first member) keeps leading; a newcomer only leads an empty party
        val (bag, overflow, newRewards) = newSpecies(state.caught + state.pokemon.map { it.speciesId }, p.speciesId, state.inventory)
        return Outcome(
            state.copy(
                pokemon = state.pokemon + p, careLeadId = lead, inventory = bag, coins = state.coins + overflow,
                trainerPoints = state.trainerPoints + BEFRIEND_POINTS, caught = state.caught + p.speciesId,
            ),
            listOf(Reward.Hatched(p, fromTask = false)) + newRewards,
        )
    }

    /**
     * The one Pokémon a module's first-run tutorial gives (docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` section 10.2.1): an egg of
     * [region]'s Common pool, hatched at once into [partyId]'s party if there is room (else the PC), leading it if it has no lead yet. Species
     * not caught yet are more likely, as for any egg. It is paid once per module by the caller; it adds no trainer points, friendship or
     * meter, and the first of its species pays the usual new-species candies.
     */
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

    /**
     * A Care Egg from a full Care Egg meter ([CARE_EGG_POINTS]): rolled like a Task Egg from the Care Region (species not
     * caught yet more likely), it hatches at once into Care's party if there is room (else the PC), and leads it if Care
     * has no lead yet. Shiny odds and IVs are the ordinary ones: a Care Egg fills the Pokédex, it does not make a better
     * Pokémon.
     */
    fun careEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.CARE, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.CARE, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val careParty = state.partyOf(PartyIds.CARE).size
        val p = Hatching.create(newPokemonId, option, careParty, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.CARE)
        val lead = state.careLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has (chosen or implicit: its first member) keeps leading; a newcomer only leads an empty party
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
    /**
     * A Field Egg from a full Field Research meter ([HEAL_EGG_POINTS]): rolled like a Task Egg from the Heal Region (species not caught yet
     * more likely), it hatches at once into Heal's party if there is room (else the PC), and leads it if Heal has no lead yet. Shiny odds and
     * IVs are the ordinary ones.
     */
    fun healEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.HEAL, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.HEAL, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.HEAL).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.HEAL)
        val lead = state.healLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has (chosen or implicit: its first member) keeps leading; a newcomer only leads an empty party
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, healLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    /**
     * A HEAL moment (Field Research): a logged activity gives Heal's lead friendship and a berry, each pays [HEAL_CANDIES] Rare Candies and a
     * little trainer rank. Only Heal's own perks apply. Nothing about the activity is read: the event has no such field.
     */
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
        // Only deliberate moments can drop an item: up to five step payments and three imports a day would otherwise drop one most days.
        return if (e.kind == HealKind.STEPS || e.kind == HealKind.IMPORT) paid else ModuleDrops.add(paid, ModuleDrops.Module.HEAL, rng)
    }

    /**
     * A Ledger Egg from a full Ledger Research meter ([MINT_EGG_POINTS]): rolled like a Task Egg from the Mint Region (species not caught yet
     * more likely), it hatches at once into Mint's party if there is room (else the PC), and leads it if Mint has no lead yet.
     */
    fun mintEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.MINT, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.MINT, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.MINT).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.MINT)
        val lead = state.mintLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has (chosen or implicit: its first member) keeps leading; a newcomer only leads an empty party
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, mintLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    /**
     * A MINT moment (Ledger Research): Mint's lead gets a little friendship and the player [MINT_CANDIES] Rare Candies, a trainer point, and
     * Mint's own perks may add a second candy or more friendship. Nothing about the records is read: the event has no such field.
     */
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

    /** Shiny rolls an outbreak gets for each MINT moment, before any streak. *(tune)* */
    val MINT_OUTBREAK_ROLLS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to 2, MintKind.REVIEW to 3)

    /** One more shiny roll for each week in a row with a review, up to [MAX_OUTBREAK_ROLLS] in all. *(tune)* */
    const val MAX_OUTBREAK_ROLLS = 7
    const val OUTBREAK_OPTIONS = 3
    const val MAX_PENDING_OUTBREAKS = 5

    /** The rarity an outbreak option is drawn from, by percent: a reconciled account mostly Uncommon and Rare, a review Rare and Super Rare. *(tune)* */
    private val MINT_OUTBREAK_RARITY: Map<MintKind, List<Pair<Rarity, Int>>> = mapOf(
        MintKind.RECONCILED to listOf(Rarity.UNCOMMON to 60, Rarity.RARE to 30, Rarity.SUPER_RARE to 10),
        MintKind.REVIEW to listOf(Rarity.RARE to 70, Rarity.SUPER_RARE to 30),
    )

    /**
     * The outbreak a rare MINT moment opens (MINT.md 16.2A): [OUTBREAK_OPTIONS] different Pokémon of the Mint Region, drawn from better rarities than an egg gives
     * (species not caught yet more likely), and [MINT_OUTBREAK_ROLLS] shiny rolls plus one for each week of a review streak. Null for a kind that opens none.
     */
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

    /**
     * Catches the option [speciesId] of [outbreak] directly (no egg): it joins Mint's party if there is room (else the PC), leads it if Mint has no lead, pays the
     * usual new-species candies, and is shiny with [Outbreak.shinyRolls] times the ordinary chance. Null when the outbreak does not offer that species.
     */
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

    /**
     * A visit to a person's Friend Safari (LINK.md 9.5): one Pokémon of [SafariRules.pool] is met, species not caught yet more likely (so the pool's rate per slot
     * falls as it widens), and caught directly (no egg, no ball): it joins Link's party if there is room (else the PC), leads it if Link has no lead, pays the usual
     * new-species candies and is shiny at the ordinary chance. Nothing else is paid: no Poké, no trainer point, no track point. Null for an empty pool.
     */
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

    /**
     * A Habitat Egg from a full Habitat Discovery meter ([HOME_EGG_POINTS]): rolled like a Task Egg from the Base Region (species not caught yet
     * more likely), it hatches at once into Base's party if there is room (else the PC), and leads it if Base has no lead yet.
     */
    fun homeEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.BASE, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.BASE, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.BASE).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.BASE)
        val lead = state.baseLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has (chosen or implicit: its first member) keeps leading; a newcomer only leads an empty party
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, baseLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    /**
     * A HOME moment (Habitat Discovery): Base's lead gets a little friendship and the player [HOME_CANDIES] Rare Candies, a trainer point, and
     * Base's own perks may add a second candy or more friendship. Nothing about the records is read: the event has no such field.
     */
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

    /**
     * A Postcard Egg from a full Postcard Trail track ([LINK_EGG_POINTS]): rolled like a Task Egg from the Link Region (species not caught yet more
     * likely), it hatches at once into Link's party if there is room (else the PC), and leads it if Link has no lead yet.
     */
    fun linkEgg(state: PlayerState, newPokemonId: String, nowMillis: Long, rng: Random): Outcome {
        val known = state.caught + state.pokemon.map { it.speciesId }
        var rarity = Egg.rates(TASK_EGG_MINUTES, false).roll(rng)
        while (rarity.ordinal > 0 && HatchRules.pool(Region.LINK, rarity).isEmpty()) rarity = Rarity.entries[rarity.ordinal - 1]
        val option = HatchRules.rollWeighted(Region.LINK, rarity, rng) { o -> if (o.speciesId !in known) UNCAUGHT_WEIGHT else 1.0 }
        val size = state.partyOf(PartyIds.LINK).size
        val p = Hatching.create(newPokemonId, option, size, nowMillis, TASK_EGG_MINUTES, rng).copy(partyId = PartyIds.LINK)
        val lead = state.linkLead?.id ?: p.id.takeIf { p.inParty } // the lead the party already has keeps leading; a newcomer only leads an empty party
        val (bag, overflow, newRewards) = newSpecies(known, p.speciesId, state.inventory)
        return Outcome(
            state.copy(pokemon = state.pokemon + p, linkLeadId = lead, inventory = bag, coins = state.coins + overflow, caught = known + p.speciesId),
            listOf(Reward.Hatched(p, fromTask = true)) + newRewards,
        )
    }

    /**
     * A LINK moment (Postcard Trail): Link's lead gets a little friendship and the player [LINK_CANDIES] Rare Candies and a trainer point; Link's own
     * perks may add a second candy or more friendship. Nothing about a person or a follow-up is read: the event has no such field.
     */
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
