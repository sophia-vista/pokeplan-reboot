package app.pokeplan.shared.pokemon

import app.pokeplan.shared.dex.Dex
import app.pokeplan.shared.dex.FormKind
import app.pokeplan.shared.model.PokemonType

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
    /** Extra EV gain in the buddy's training stats (Synchronize). */
    val evGainPercent: Double = 0.0,
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
        "Synchronize" to AbilityPerk(evGainPercent = 25.0),
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
    val evGainPercent: Double = 0.0,
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

    fun total(buddy: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), ctx: PerkContext = PerkContext()): PerkBonuses {
        if (buddy == null) return PerkBonuses()
        val sums = mutableMapOf<PerkBucket, Double>()
        fun add(bucket: PerkBucket, value: Double) { sums[bucket] = (sums[bucket] ?: 0.0) + value }

        var evGain = 0.0
        var keeps = false
        typePerks(buddy).forEach { (b, v) -> add(b, if (ctx.megaActive && (b == PerkBucket.EGG_LUCK || b == PerkBucket.SHINY_HUNTER)) v * MEGA_BOOST else v) }
        AbilityPerks.table[buddy.ability]?.let { perk ->
            if (applies(perk.condition, ctx)) perk.bucket?.let { add(it, perk.percent * FriendshipRules.perkMultiplier(buddy.friendship)) }
            evGain += perk.evGainPercent
            keeps = keeps || perk.keepsFailedEgg
        }
        buddy.heldItem?.let { HeldItems.perks[it] }?.let { add(it.bucket, it.percent) }
        party.filter { it.inParty && it.id != buddy.id }.take(MAX_ASSISTS).forEach { assist ->
            typePerks(assist).forEach { (b, v) -> add(b, v * ASSIST_SHARE) }
        }
        return PerkBonuses(sums.mapValues { it.value.coerceAtMost(BUCKET_CAP) }, evGain, keeps)
    }
}
