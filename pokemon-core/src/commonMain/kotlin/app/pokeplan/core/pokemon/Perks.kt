package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.PokemonType

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
    val perks: Map<String, HeldPerk> = mapOf(
        "oval-charm" to HeldPerk(PerkBucket.EGG_LUCK, 4.0),
        "lucky-egg" to HeldPerk(PerkBucket.CANDY_YIELD, 4.0),
        "star-piece" to HeldPerk(PerkBucket.SUPER_CANDY_CHANCE, 4.0),
        "scope-lens" to HeldPerk(PerkBucket.ITEM_FINDER, 4.0),
        "shiny-charm" to HeldPerk(PerkBucket.SHINY_HUNTER, 4.0),
        "leftovers" to HeldPerk(PerkBucket.STREAK_KEEPER, 4.0),
        "amulet-coin" to HeldPerk(PerkBucket.COINS, 10.0),
    )

    val battle: Map<String, Int> = mapOf(
        "leftovers" to 4,
        "scope-lens" to 4,
    )

    const val MEGA_STONE_BATTLE_PERCENT = 6
}

enum class PerkCondition { ALWAYS, SHORT_SESSION, TOP_RANK_TASK }

data class AbilityPerk(
    val bucket: PerkBucket? = null,
    val percent: Double = 0.0,
    val condition: PerkCondition = PerkCondition.ALWAYS,
    val keepsFailedEgg: Boolean = false,
)

object AbilityPerks {
    val table: Map<String, AbilityPerk> = mapOf(
        "Serene Grace" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Compound Eyes" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Super Luck" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Victory Star" to AbilityPerk(PerkBucket.EGG_LUCK, 4.0),
        "Huge Power" to AbilityPerk(PerkBucket.CANDY_YIELD, 5.0),
        "Pure Power" to AbilityPerk(PerkBucket.CANDY_YIELD, 5.0),
        "Hustle" to AbilityPerk(PerkBucket.CANDY_YIELD, 3.0),
        "Guts" to AbilityPerk(PerkBucket.CANDY_YIELD, 8.0, PerkCondition.TOP_RANK_TASK),
        "Early Bird" to AbilityPerk(PerkBucket.CANDY_YIELD, 5.0),
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

data class PerkContext(
    val sessionMinutes: Int? = null,
    val taskRank: Int? = null,
    val megaActive: Boolean = false,
)

data class PerkBonuses(
    val percent: Map<PerkBucket, Double> = emptyMap(),
    val keepsFailedEgg: Boolean = false,
) {
    operator fun get(bucket: PerkBucket): Double = percent[bucket] ?: 0.0

    val extraForgivenDays: Int get() = if (this[PerkBucket.STREAK_KEEPER] >= Perks.STREAK_KEEPER_THRESHOLD) 1 else 0

    fun multiplier(bucket: PerkBucket): Double = 1.0 + this[bucket] / 100.0
}

object Perks {
    const val TYPE_PERK_MAX = 15.0

    const val SECOND_TYPE_SHARE = 0.5
    const val ASSIST_SHARE = 0.25

    const val BUCKET_CAP = 25.0

    const val MEGA_BOOST = 1.25
    const val STREAK_KEEPER_THRESHOLD = 10.0
    private const val STAT_REFERENCE = 250.0
    private const val MAX_ASSISTS = 5

    private fun power(p: OwnedPokemon, bucket: PerkBucket): Double {
        val stat = TypePerks.stat(bucket) ?: return 0.0
        val statFactor = (p.stats[stat] / STAT_REFERENCE).coerceIn(0.0, 1.0)
        return 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * statFactor
    }

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

    fun megaReady(p: OwnedPokemon): Boolean {
        val held = p.heldItem ?: return false
        return Dex.formsOf(p.speciesId).any { it.kind == FormKind.MEGA && Items.megaStone(it.identifier) == held }
    }

    private fun applies(condition: PerkCondition, ctx: PerkContext): Boolean = when (condition) {
        PerkCondition.ALWAYS -> true
        PerkCondition.SHORT_SESSION -> ctx.sessionMinutes != null && ctx.sessionMinutes in 5..20
        PerkCondition.TOP_RANK_TASK -> ctx.taskRank == 5
    }

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

data class PerkSource(
    val from: String,
    val kind: Kind,
    val bucket: PerkBucket,
    val percent: Double,
    val condition: PerkCondition = PerkCondition.ALWAYS,
) {
    enum class Kind { BUDDY_TYPE, ABILITY, HELD_ITEM, ASSIST, GUARDIAN }
}

object Guardians {
    const val SIGNATURE_PERCENT = 10.0

    const val SECOND_TYPE_SHARE = 0.5

    const val BUCKET_CAP = 15.0

    private val lore: Map<String, PerkBucket> = mapOf(
        "uxie" to PerkBucket.SUPER_CANDY_CHANCE,
        "mesprit" to PerkBucket.STREAK_KEEPER,
        "azelf" to PerkBucket.CANDY_YIELD,
        "jirachi" to PerkBucket.EGG_LUCK,
        "celebi" to PerkBucket.STREAK_KEEPER,
    )

    fun canBe(p: OwnedPokemon): Boolean = p.species.isSpecial

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

    val MODULES: List<String> = listOf(PartyIds.PLAN, PartyIds.CARE, PartyIds.HEAL, PartyIds.MINT, PartyIds.BASE, PartyIds.LINK)

    fun <B> signatureIn(p: OwnedPokemon, bucketOf: (PokemonType) -> B?): Map<B, Double> {
        val result = mutableMapOf<B, Double>()
        p.form.types.forEachIndexed { i, type ->
            val bucket = bucketOf(type) ?: return@forEachIndexed
            result[bucket] = (result[bucket] ?: 0.0) + SIGNATURE_PERCENT * if (i == 0) 1.0 else SECOND_TYPE_SHARE
        }
        return result
    }

    fun <B> addTo(base: Map<B, Double>, guardian: OwnedPokemon?, bucketOf: (PokemonType) -> B?): Map<B, Double> {
        if (guardian == null || !canBe(guardian)) return base
        val out = base.toMutableMap()
        signatureIn(guardian, bucketOf).forEach { (b, v) -> out[b] = (out[b] ?: 0.0) + v.coerceAtMost(BUCKET_CAP) }
        return out
    }

    fun perks(guardians: List<OwnedPokemon>): Map<PerkBucket, Double> {
        val sums = mutableMapOf<PerkBucket, Double>()
        guardians.filter(::canBe).forEach { g -> signature(g).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v } }
        return sums.mapValues { it.value.coerceAtMost(BUCKET_CAP) }
    }
}

enum class CareBucket(val label: String, val effect: String) {
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from routines, breaks and skills"),
    BERRY_FINDER("Berry finder", "a chance of a second berry"),
    VISITOR_LUCK("Visitor luck", "rarer, better befriended eggs"),
}

object CarePerks {
    fun bucket(type: PokemonType): CareBucket? = when (type) {
        PokemonType.NORMAL, PokemonType.FAIRY, PokemonType.WATER -> CareBucket.FRIENDSHIP_GAIN
        PokemonType.GRASS, PokemonType.BUG, PokemonType.FIGHTING -> CareBucket.BERRY_FINDER
        PokemonType.FLYING, PokemonType.PSYCHIC, PokemonType.GHOST -> CareBucket.VISITOR_LUCK
        else -> null
    }

    fun typePerks(p: OwnedPokemon): Map<CareBucket, Double> {
        val result = mutableMapOf<CareBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), guardian: OwnedPokemon? = null): Map<CareBucket, Double> {
        if (lead == null && guardian == null) return emptyMap()
        val sums = mutableMapOf<CareBucket, Double>()
        lead?.let { typePerks(it) }?.forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead?.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return Guardians.addTo(sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }, guardian, ::bucket)
    }
}

enum class HealBucket(val label: String, val effect: String) {
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from a logged activity"),
    BERRY_FINDER("Berry finder", "a chance of a second berry from a logged activity"),
}

object HealPerks {
    fun bucket(type: PokemonType): HealBucket? = when (type) {
        PokemonType.FIGHTING, PokemonType.NORMAL, PokemonType.FIRE, PokemonType.ELECTRIC, PokemonType.FLYING, PokemonType.DRAGON -> HealBucket.FRIENDSHIP_GAIN
        PokemonType.WATER, PokemonType.GRASS, PokemonType.GROUND, PokemonType.ICE, PokemonType.BUG, PokemonType.FAIRY -> HealBucket.BERRY_FINDER
        else -> null
    }

    fun typePerks(p: OwnedPokemon): Map<HealBucket, Double> {
        val result = mutableMapOf<HealBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), guardian: OwnedPokemon? = null): Map<HealBucket, Double> {
        if (lead == null && guardian == null) return emptyMap()
        val sums = mutableMapOf<HealBucket, Double>()
        lead?.let { typePerks(it) }?.forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead?.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return Guardians.addTo(sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }, guardian, ::bucket)
    }
}

enum class MintBucket(val label: String, val effect: String) {
    CANDY_FINDER("Candy finder", "a chance of an extra Exp. Candy from a review or a reconciled account"),
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from a review or a reconciled account"),
}

object MintPerks {
    fun bucket(type: PokemonType): MintBucket? = when (type) {
        PokemonType.STEEL, PokemonType.ROCK, PokemonType.GROUND, PokemonType.NORMAL, PokemonType.ELECTRIC, PokemonType.PSYCHIC -> MintBucket.CANDY_FINDER
        PokemonType.GHOST, PokemonType.DARK, PokemonType.POISON, PokemonType.FIRE, PokemonType.FAIRY, PokemonType.DRAGON -> MintBucket.FRIENDSHIP_GAIN
        else -> null
    }

    fun typePerks(p: OwnedPokemon): Map<MintBucket, Double> {
        val result = mutableMapOf<MintBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), guardian: OwnedPokemon? = null): Map<MintBucket, Double> {
        if (lead == null && guardian == null) return emptyMap()
        val sums = mutableMapOf<MintBucket, Double>()
        lead?.let { typePerks(it) }?.forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead?.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return Guardians.addTo(sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }, guardian, ::bucket)
    }
}

enum class BaseBucket(val label: String, val effect: String) {
    CANDY_FINDER("Candy finder", "a chance of an extra Exp. Candy from a chore or a piece of maintenance done"),
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from a chore or a piece of maintenance done"),
}

object BasePerks {
    fun bucket(type: PokemonType): BaseBucket? = when (type) {
        PokemonType.GRASS, PokemonType.NORMAL, PokemonType.BUG, PokemonType.GROUND, PokemonType.FIRE, PokemonType.WATER -> BaseBucket.CANDY_FINDER
        PokemonType.FAIRY, PokemonType.FLYING, PokemonType.ICE, PokemonType.ELECTRIC, PokemonType.PSYCHIC, PokemonType.GHOST -> BaseBucket.FRIENDSHIP_GAIN
        else -> null
    }

    fun typePerks(p: OwnedPokemon): Map<BaseBucket, Double> {
        val result = mutableMapOf<BaseBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), guardian: OwnedPokemon? = null): Map<BaseBucket, Double> {
        if (lead == null && guardian == null) return emptyMap()
        val sums = mutableMapOf<BaseBucket, Double>()
        lead?.let { typePerks(it) }?.forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead?.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return Guardians.addTo(sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }, guardian, ::bucket)
    }
}

enum class LinkBucket(val label: String, val effect: String) {
    CANDY_FINDER("Candy finder", "a chance of an extra Exp. Candy from a finished follow-up"),
    FRIENDSHIP_GAIN("Friendship gain", "a chance of extra friendship from a finished follow-up"),
}

object LinkPerks {
    fun bucket(type: PokemonType): LinkBucket? = when (type) {
        PokemonType.NORMAL, PokemonType.FLYING, PokemonType.WATER, PokemonType.GRASS, PokemonType.BUG, PokemonType.GROUND -> LinkBucket.CANDY_FINDER
        PokemonType.FAIRY, PokemonType.FIGHTING, PokemonType.ELECTRIC, PokemonType.PSYCHIC, PokemonType.FIRE, PokemonType.DRAGON -> LinkBucket.FRIENDSHIP_GAIN
        else -> null
    }

    fun typePerks(p: OwnedPokemon): Map<LinkBucket, Double> {
        val result = mutableMapOf<LinkBucket, Double>()
        val power = 0.5 * p.level / CandyRules.MAX_LEVEL + 0.5 * p.friendship / FriendshipRules.MAX
        for ((index, type) in p.perkTypes.withIndex()) {
            val bucket = bucket(type) ?: continue
            result[bucket] = (result[bucket] ?: 0.0) + Perks.TYPE_PERK_MAX * power * if (index == 0) 1.0 else Perks.SECOND_TYPE_SHARE
        }
        return result
    }

    fun total(lead: OwnedPokemon?, party: List<OwnedPokemon> = emptyList(), guardian: OwnedPokemon? = null): Map<LinkBucket, Double> {
        if (lead == null && guardian == null) return emptyMap()
        val sums = mutableMapOf<LinkBucket, Double>()
        lead?.let { typePerks(it) }?.forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v }
        party.filter { it.id != lead?.id }.take(5).forEach { a ->
            typePerks(a).forEach { (b, v) -> sums[b] = (sums[b] ?: 0.0) + v * Perks.ASSIST_SHARE }
        }
        return Guardians.addTo(sums.mapValues { it.value.coerceAtMost(Perks.BUCKET_CAP) }, guardian, ::bucket)
    }
}
