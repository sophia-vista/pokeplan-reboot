package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.PokemonType

/** What a perk improves. See "Buddy and perks" in docs/design.md. */
enum class PerkBucket {
    EGG_LUCK,
    CANDY_YIELD,
    SUPER_CANDY_CHANCE,
    ITEM_FINDER,
    SHINY_HUNTER,
    STREAK_KEEPER,
    COINS,
}

data class HeldPerk(val bucket: PerkBucket, val percent: Double)

object HeldItems {
    /** Held items give one small perk each. *(tune)* */
    val perks: Map<String, HeldPerk> = mapOf(
        "oval-charm" to HeldPerk(PerkBucket.EGG_LUCK, 4.0),
        "lucky-egg" to HeldPerk(PerkBucket.CANDY_YIELD, 4.0),
        "star-piece" to HeldPerk(PerkBucket.SUPER_CANDY_CHANCE, 4.0),
        "scope-lens" to HeldPerk(PerkBucket.ITEM_FINDER, 4.0),
        "shiny-charm" to HeldPerk(PerkBucket.SHINY_HUNTER, 4.0),
        "leftovers" to HeldPerk(PerkBucket.STREAK_KEEPER, 4.0),
        "amulet-coin" to HeldPerk(PerkBucket.COINS, 10.0),
    )
}

/** When an ability perk applies. */
enum class PerkCondition { ALWAYS, FIRST_TASK_BEFORE_9AM, SHORT_SESSION, TOP_RANK_TASK }

data class AbilityPerk(
    val bucket: PerkBucket? = null,
    val percent: Double = 0.0,
    val condition: PerkCondition = PerkCondition.ALWAYS,
    /** A failed session keeps its egg once a week (Magic Guard). */
    val keepsFailedEgg: Boolean = false,
)

object AbilityPerks {
    /** Curated abilities that give a perk; every other ability falls back to the type perk only. *(tune)* */
    val table: Map<String, AbilityPerk> = mapOf(
        "Serene Grace" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Compound Eyes" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Super Luck" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Victory Star" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Huge Power" to AbilityPerk(PerkBucket.CANDY_YIELD, 5.0),
        "Pure Power" to AbilityPerk(PerkBucket.CANDY_YIELD, 5.0),
        "Hustle" to AbilityPerk(PerkBucket.CANDY_YIELD, 3.0),
        "Guts" to AbilityPerk(PerkBucket.CANDY_YIELD, 8.0, PerkCondition.TOP_RANK_TASK),
        "Early Bird" to AbilityPerk(PerkBucket.CANDY_YIELD, 10.0, PerkCondition.FIRST_TASK_BEFORE_9AM),
        "Technician" to AbilityPerk(PerkBucket.COINS, 10.0, PerkCondition.SHORT_SESSION),
        "Trace" to AbilityPerk(PerkBucket.SUPER_CANDY_CHANCE, 3.0),
        "Inner Focus" to AbilityPerk(PerkBucket.SUPER_CANDY_CHANCE, 3.0),
        "Telepathy" to AbilityPerk(PerkBucket.SUPER_CANDY_CHANCE, 3.0),
        "Magic Bounce" to AbilityPerk(PerkBucket.SUPER_CANDY_CHANCE, 3.0),
        "Pickup" to AbilityPerk(PerkBucket.ITEM_FINDER, 6.0),
        "Honey Gather" to AbilityPerk(PerkBucket.ITEM_FINDER, 6.0),
        "Harvest" to AbilityPerk(PerkBucket.ITEM_FINDER, 6.0),
        "Frisk" to AbilityPerk(PerkBucket.ITEM_FINDER, 4.0),
        "Forewarn" to AbilityPerk(PerkBucket.ITEM_FINDER, 4.0),
        "Illuminate" to AbilityPerk(PerkBucket.SHINY_HUNTER, 5.0),
        "Magnet Pull" to AbilityPerk(PerkBucket.SHINY_HUNTER, 3.0),
        "Natural Cure" to AbilityPerk(PerkBucket.STREAK_KEEPER, 4.0),
        "Shed Skin" to AbilityPerk(PerkBucket.STREAK_KEEPER, 4.0),
        "Regenerator" to AbilityPerk(PerkBucket.STREAK_KEEPER, 4.0),
        "Synchronize" to AbilityPerk(PerkBucket.SUPER_CANDY_CHANCE, 3.0),
        "Magic Guard" to AbilityPerk(keepsFailedEgg = true),
    )
}

object TypePerks {
    fun bucket(type: PokemonType): PerkBucket = when (type) {
        PokemonType.NORMAL, PokemonType.FLYING, PokemonType.FAIRY -> PerkBucket.EGG_LUCK
        PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.GROUND, PokemonType.STEEL -> PerkBucket.CANDY_YIELD
        PokemonType.PSYCHIC, PokemonType.DRAGON, PokemonType.GHOST -> PerkBucket.SUPER_CANDY_CHANCE
        PokemonType.GRASS, PokemonType.BUG, PokemonType.DARK -> PerkBucket.ITEM_FINDER
        PokemonType.ELECTRIC, PokemonType.ICE -> PerkBucket.SHINY_HUNTER
        PokemonType.WATER, PokemonType.FIRE, PokemonType.POISON -> PerkBucket.STREAK_KEEPER
    }

    /** The stat that powers each bucket's type perk. */
    fun stat(bucket: PerkBucket): Stat? = when (bucket) {
        PerkBucket.EGG_LUCK -> Stat.SPEED
        PerkBucket.CANDY_YIELD -> Stat.ATTACK
        PerkBucket.SUPER_CANDY_CHANCE -> Stat.SP_ATTACK
        PerkBucket.ITEM_FINDER -> Stat.DEFENSE
        PerkBucket.SHINY_HUNTER -> Stat.SP_DEFENSE
        PerkBucket.STREAK_KEEPER -> Stat.HP
        PerkBucket.COINS -> null
    }
}

/** The moment a perk is being applied: a task, a session, or neither. */
data class PerkContext(
    val sessionMinutes: Int? = null,
    /** The finished task's difficulty rank as 1 (E) to 5 (A). */
    val taskRank: Int? = null,
    val hourOfDay: Int? = null,
    val firstTaskOfDay: Boolean = false,
    /** The buddy is in its Mega or Gigantamax form for this focus session. */
    val megaActive: Boolean = false,
)

data class PerkBonuses(
    /** Percent bonus per bucket (4.0 means +4%). */
    val percent: Map<PerkBucket, Double> = emptyMap(),
    val keepsFailedEgg: Boolean = false,
) {
    operator fun get(bucket: PerkBucket): Double = percent[bucket] ?: 0.0

    /** Extra missed days forgiven per week from the Streak keeper perk. */
    val extraForgivenDays: Int get() = if (this[PerkBucket.STREAK_KEEPER] >= Perks.STREAK_KEEPER_THRESHOLD) 1 else 0

    /** As a multiplier, e.g. +4% is 1.04. */
    fun multiplier(bucket: PerkBucket): Double = 1.0 + this[bucket] / 100.0
}

/** Works out the perks of the buddy, its held item, its ability and the rest of the party. */
object Perks {
    /** Largest type perk a single Pokémon can give, before friendship and nature. *(tune)* */
    const val TYPE_PERK_MAX = 15.0

    /** The second type gives half, assists a quarter. *(tune)* */
    const val SECOND_TYPE_SHARE = 0.5
    const val ASSIST_SHARE = 0.25

    /** Total bonus per bucket stays below this. *(tune)* */
    const val BUCKET_CAP = 25.0

    const val MEGA_BOOST = 1.25
    const val STREAK_KEEPER_THRESHOLD = 10.0
    private const val STAT_REFERENCE = 250.0
    private const val MAX_ASSISTS = 5

    /** How strong [p]'s type perk is, 0..1, from its level and the relevant stat. */
    private fun power(p: OwnedPokemon, bucket: PerkBucket): Double {
        val stat = TypePerks.stat(bucket) ?: return 0.0
        val statFactor = (p.stats[stat] / STAT_REFERENCE).coerceIn(0.0, 1.0)
        return 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * statFactor
    }

    /** One Pokémon's type perks as percent per bucket (primary type full, second type half). */
    fun typePerks(p: OwnedPokemon): Map<PerkBucket, Double> {
        val result = mutableMapOf<PerkBucket, Double>()
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = TypePerks.bucket(type)
            val stat = TypePerks.stat(bucket)
            val natureMultiplier = when (stat) {
                null -> 1.0
                p.nature.raised -> 1.1
                p.nature.lowered -> 0.9
                else -> 1.0
            }
            val share = if (index == 0) 1.0 else SECOND_TYPE_SHARE
            val value = TYPE_PERK_MAX * power(p, bucket) * FriendshipRules.perkMultiplier(p.friendship) * natureMultiplier * share
            result[bucket] = (result[bucket] ?: 0.0) + value
        }
        return result
    }

    /** True if the buddy holds the Mega Stone of one of its own Mega forms. */
    fun megaReady(p: OwnedPokemon): Boolean {
        val held = p.heldItem ?: return false
        return Dex.formsOf(p.speciesId).any { it.kind == FormKind.MEGA && Items.megaStone(it.identifier) == held }
    }

    private fun applies(condition: PerkCondition, ctx: PerkContext): Boolean = when (condition) {
        PerkCondition.ALWAYS -> true
        PerkCondition.FIRST_TASK_BEFORE_9AM -> ctx.firstTaskOfDay && (ctx.hourOfDay ?: 12) < 9
        PerkCondition.SHORT_SESSION -> ctx.sessionMinutes != null && ctx.sessionMinutes in 5..20
        PerkCondition.TOP_RANK_TASK -> ctx.taskRank == 5
    }

    /**
     * The perks from the buddy, its held item and ability, and the party, plus the slotted [guardians]. Guardian perks are
     * added on top of the ordinary cap (see [Guardians]), so they work even with no buddy.
     */
    fun total(
        buddy: OwnedPokemon?,
        party: List<OwnedPokemon> = emptyList(),
        ctx: PerkContext = PerkContext(),
        guardians: List<OwnedPokemon> = emptyList(),
    ): PerkBonuses {
        val guardian = Guardians.perks(guardians)
        if (buddy == null) return PerkBonuses(guardian)
        val sums = mutableMapOf<PerkBucket, Double>()
        fun add(bucket: PerkBucket, value: Double) { sums[bucket] = (sums[bucket] ?: 0.0) + value }

        var keeps = false
        typePerks(buddy).forEach { (b, v) -> add(b, if (ctx.megaActive && (b == PerkBucket.EGG_LUCK || b == PerkBucket.SHINY_HUNTER)) v * MEGA_BOOST else v) }
        AbilityPerks.table[buddy.ability]?.let { perk ->
            if (applies(perk.condition, ctx)) perk.bucket?.let { add(it, perk.percent * FriendshipRules.perkMultiplier(buddy.friendship)) }
            keeps = keeps || perk.keepsFailedEgg
        }
        buddy.heldItem?.let { HeldItems.perks[it] }?.let { add(it.bucket, it.percent) }
        party.filter { it.inParty && it.id != buddy.id }.take(MAX_ASSISTS).forEach { assist ->
            typePerks(assist).forEach { (b, v) -> add(b, v * ASSIST_SHARE) }
        }
        val ordinary = sums.mapValues { it.value.coerceAtMost(BUCKET_CAP) }
        val combined = (ordinary.keys + guardian.keys).associateWith { (ordinary[it] ?: 0.0) + (guardian[it] ?: 0.0) }
        return PerkBonuses(combined, keeps)
    }

    /**
     * Where each perk comes from, for showing the player: the same parts [total] adds up, before the cap. Ability perks
     * that only apply sometimes are listed with their [PerkSource.condition].
     */
    fun sources(
        buddy: OwnedPokemon?,
        party: List<OwnedPokemon> = emptyList(),
        guardians: List<OwnedPokemon> = emptyList(),
    ): List<PerkSource> = buildList {
        if (buddy != null) {
            typePerks(buddy).forEach { (b, v) -> add(PerkSource(buddy.nickname, PerkSource.Kind.BUDDY_TYPE, b, v)) }
            AbilityPerks.table[buddy.ability]?.let { perk ->
                perk.bucket?.let { add(PerkSource(buddy.ability, PerkSource.Kind.ABILITY, it, perk.percent * FriendshipRules.perkMultiplier(buddy.friendship), perk.condition)) }
            }
            buddy.heldItem?.let { item -> HeldItems.perks[item]?.let { add(PerkSource(Items.name(item), PerkSource.Kind.HELD_ITEM, it.bucket, it.percent)) } }
            party.filter { it.inParty && it.id != buddy.id }.take(MAX_ASSISTS).forEach { assist ->
                typePerks(assist).forEach { (b, v) -> add(PerkSource(assist.nickname, PerkSource.Kind.ASSIST, b, v * ASSIST_SHARE)) }
            }
        }
        guardians.forEach { g -> Guardians.signature(g).forEach { (b, v) -> add(PerkSource(g.nickname, PerkSource.Kind.GUARDIAN, b, v)) } }
    }
}

/** One part of the perk total: who or what gives it, to which bucket, and how much (in percent). */
data class PerkSource(
    val from: String,
    val kind: Kind,
    val bucket: PerkBucket,
    val percent: Double,
    val condition: PerkCondition = PerkCondition.ALWAYS,
) {
    enum class Kind { BUDDY_TYPE, ABILITY, HELD_ITEM, ASSIST, GUARDIAN }
}

/**
 * The Guardian role of legendary-class and mythical Pokémon: slotted by trainer rank, a big perk each, outside the usual cap.
 * See "Guardians" in docs/design.md. Numbers are *(tune)*.
 */
object Guardians {
    /** The signature perk of a Guardian, percent. */
    const val SIGNATURE_PERCENT = 10.0

    /** A second type gives this share of the signature perk. */
    const val SECOND_TYPE_SHARE = 0.5

    /** The most Guardians can add to one bucket, on top of the ordinary cap. */
    const val BUCKET_CAP = 15.0

    /** Where lore, rather than type, decides the signature bucket (by species identifier). */
    private val lore: Map<String, PerkBucket> = mapOf(
        "uxie" to PerkBucket.SUPER_CANDY_CHANCE, // knowledge
        "mesprit" to PerkBucket.STREAK_KEEPER, // emotion
        "azelf" to PerkBucket.CANDY_YIELD, // willpower
        "jirachi" to PerkBucket.EGG_LUCK, // wishes
        "celebi" to PerkBucket.STREAK_KEEPER, // time
    )

    /** How many Guardian slots a trainer of [rankIndex] (0 is the first rank) has. */
    fun slots(rankIndex: Int): Int = when {
        rankIndex >= 9 -> 3
        rankIndex >= 6 -> 2
        rankIndex >= 4 -> 1
        else -> 0
    }

    /** Only legendary-class and mythical Pokémon can be Guardians. */
    fun canBe(p: OwnedPokemon): Boolean = p.species.isSpecial

    /** One Guardian's signature perk(s): percent per bucket. */
    fun signature(p: OwnedPokemon): Map<PerkBucket, Double> {
        val types = p.form.types
        val primary = lore[p.species.identifier] ?: TypePerks.bucket(types.first())
        val result = mutableMapOf(primary to SIGNATURE_PERCENT)
        types.getOrNull(1)?.let { second ->
            val bucket = TypePerks.bucket(second)
            result[bucket] = (result[bucket] ?: 0.0) + SIGNATURE_PERCENT * SECOND_TYPE_SHARE
        }
        return result
    }

    /** The combined Guardian perks, each bucket capped at [BUCKET_CAP]. Anything that is not a Guardian species is ignored. */
    fun perks(guardians: List<OwnedPokemon>): Map<PerkBucket, Double> {
        val sums = mutableMapOf<PerkBucket, Double>()
        guardians.filter(::canBe).forEach { g -> signature(g).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v } }
        return sums.mapValues { it.value.coerceAtMost(BUCKET_CAP) }
    }
}

/** What Care's party can improve (docs/design.md, "Parties per module"). Perks only change what a kind of Care action pays. */
enum class CareBucket(val label: String, val effect: String) {
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from routines, breaks and skills"),
    BERRY_FINDER("Berry finder", "a chance of a second berry"),
    VISITOR_LUCK("Visitor luck", "rarer, better befriended eggs"),
}

/** Care's perks: the lead gives its type perk in full, the rest of Care's party a quarter, each bucket capped like Plan's. */
object CarePerks {
    fun bucket(type: PokemonType): CareBucket? = when (type) {
        PokemonType.NORMAL, PokemonType.FAIRY, PokemonType.WATER -> CareBucket.FRIENDSHIP_GAIN
        PokemonType.GRASS, PokemonType.BUG, PokemonType.FIGHTING -> CareBucket.BERRY_FINDER
        PokemonType.FLYING, PokemonType.PSYCHIC, PokemonType.GHOST -> CareBucket.VISITOR_LUCK
        else -> null
    }

    /** One Pokémon's Care perks as percent per bucket (primary type full, second type half), from its level and friendship. */
    fun typePerks(p: OwnedPokemon): Map<CareBucket, Double> {
        val result = mutableMapOf<CareBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    /** The percent per bucket for a party led by [lead]; empty with no lead. */
    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList()): Map<CareBucket, Double> {
        if (lead == null) return emptyMap()
        val sums = mutableMapOf<CareBucket, Double>()
        typePerks(lead).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }
    }
}

/** What Heal's party can improve (POKEMON_ECOSYSTEM.md section 2.2.1). Perks only change what Field Research pays, never anything in HEAL's records. */
enum class HealBucket(val label: String, val effect: String) {
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from a logged activity"),
    BERRY_FINDER("Berry finder", "a chance of a second berry from a logged activity"),
}

/** Heal's perks: the lead gives its type perk in full, the rest of Heal's party a quarter, each bucket capped like Plan's. */
object HealPerks {
    fun bucket(type: PokemonType): HealBucket? = when (type) {
        PokemonType.FIGHTING, PokemonType.NORMAL, PokemonType.FIRE, PokemonType.ELECTRIC, PokemonType.FLYING, PokemonType.DRAGON -> HealBucket.FRIENDSHIP_GAIN
        PokemonType.WATER, PokemonType.GRASS, PokemonType.GROUND, PokemonType.ICE, PokemonType.BUG, PokemonType.FAIRY -> HealBucket.BERRY_FINDER
        else -> null
    }

    /** One Pokémon's Heal perks as percent per bucket (primary type full, second type half), from its level and friendship. */
    fun typePerks(p: OwnedPokemon): Map<HealBucket, Double> {
        val result = mutableMapOf<HealBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    /** The percent per bucket for a party led by [lead]; empty with no lead. */
    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList()): Map<HealBucket, Double> {
        if (lead == null) return emptyMap()
        val sums = mutableMapOf<HealBucket, Double>()
        typePerks(lead).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }
    }
}
