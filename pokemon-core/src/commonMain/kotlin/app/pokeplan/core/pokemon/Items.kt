package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.PokemonType
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class ItemCategory { CANDY, VITAMIN, TRAINING, EVOLUTION, HELD, BERRY, LURE, TERA, FORM, KEY }

data class Item(val id: String, val name: String, val category: ItemCategory)

/** The item catalogue. Item ids are plain strings so they match the ids used in the evolution data. */
object Items {
    const val RARE_CANDY = "rare-candy"
    const val SUPER_CANDY = EvolutionRules.SUPER_CANDY
    const val LINK_CORD = EvolutionRules.LINK_CORD
    const val NATURE_MINT = "nature-mint"
    const val ABILITY_CAPSULE = "ability-capsule"
    const val ABILITY_PATCH = "ability-patch"
    const val BOTTLE_CAP = "bottle-cap"
    const val DYNAMAX_BAND = "dynamax-band"

    /** Vitamins: +10 EVs in one stat. */
    val vitamins: Map<String, Stat> = mapOf(
        "hp-up" to Stat.HP, "protein" to Stat.ATTACK, "iron" to Stat.DEFENSE,
        "calcium" to Stat.SP_ATTACK, "zinc" to Stat.SP_DEFENSE, "carbos" to Stat.SPEED,
    )

    /** The five berry flavours; a nature likes the flavour of its raised stat and dislikes that of its lowered stat. */
    val flavors: Map<Stat, String> = mapOf(
        Stat.ATTACK to "spicy", Stat.DEFENSE to "sour", Stat.SPEED to "sweet",
        Stat.SP_ATTACK to "dry", Stat.SP_DEFENSE to "bitter",
    )

    fun berry(flavor: String) = "berry-$flavor"

    /** A berry of a random flavour. */
    fun randomBerry(rng: Random): String = berry(flavors.values.toList()[rng.nextInt(flavors.size)])
    fun lure(type: PokemonType) = "lure-${type.name.lowercase()}"
    fun teraShard(type: PokemonType) = "tera-shard-${type.name.lowercase()}"
    fun megaStone(formIdentifier: String) = "mega-stone-$formIdentifier"

    private val special = mapOf(
        RARE_CANDY to ("Exp. Candy" to ItemCategory.CANDY),
        SUPER_CANDY to ("Super Candy" to ItemCategory.CANDY),
        LINK_CORD to ("Link Cord" to ItemCategory.EVOLUTION),
        NATURE_MINT to ("Nature Mint" to ItemCategory.TRAINING),
        ABILITY_CAPSULE to ("Ability Capsule" to ItemCategory.TRAINING),
        ABILITY_PATCH to ("Ability Patch" to ItemCategory.TRAINING),
        BOTTLE_CAP to ("Bottle Cap" to ItemCategory.TRAINING),
        DYNAMAX_BAND to ("Dynamax Band" to ItemCategory.KEY),
        "hp-up" to ("HP Up" to ItemCategory.VITAMIN), "protein" to ("Protein" to ItemCategory.VITAMIN),
        "iron" to ("Iron" to ItemCategory.VITAMIN), "calcium" to ("Calcium" to ItemCategory.VITAMIN),
        "zinc" to ("Zinc" to ItemCategory.VITAMIN), "carbos" to ("Carbos" to ItemCategory.VITAMIN),
    )

    private fun pretty(id: String): String {
        // "berry-sweet" reads "Sweet Berry"; "lure-fire" "Fire Lure"; "tera-shard-fire" "Fire Tera Shard".
        val words = when {
            id.startsWith("berry-") -> id.removePrefix("berry-") + "-berry"
            id.startsWith("lure-") -> id.removePrefix("lure-") + "-lure"
            id.startsWith("tera-shard-") -> id.removePrefix("tera-shard-") + "-tera-shard"
            else -> id
        }
        return words.split('-').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /** Evolution items taken from the evolution data (stones and the like). */
    val evolutionItems: List<String> by lazy {
        Dex.evolutions.filter { it.kind == EvolutionKind.STONE || (it.kind == EvolutionKind.GENDER && it.item != null) }
            .mapNotNull { it.item }.distinct().sorted()
    }

    /** Items a Pokémon holds to evolve when traded (a Metal Coat, a King's Rock...), taken from the evolution data. */
    val tradeItems: List<String> by lazy {
        Dex.evolutions.filter { it.kind == EvolutionKind.TRADE }.mapNotNull { it.heldItem }.distinct().sorted()
    }

    /** Whether a Pokémon can hold [id]: a perk item, a Mega Stone, or an item it needs to hold for a trade evolution. */
    fun holdable(id: String): Boolean = id in HeldItems.perks || id.startsWith("mega-stone-") || id in tradeItems

    fun category(id: String): ItemCategory? = when {
        id in special -> special.getValue(id).second
        id.startsWith("berry-") -> ItemCategory.BERRY
        id.startsWith("lure-") -> ItemCategory.LURE
        id.startsWith("tera-shard-") -> ItemCategory.TERA
        id.startsWith("mega-stone-") -> ItemCategory.FORM
        id in HeldItems.perks -> ItemCategory.HELD
        id in evolutionItems || id in tradeItems -> ItemCategory.EVOLUTION
        else -> null
    }

    fun get(id: String): Item? = category(id)?.let { Item(id, special[id]?.first ?: pretty(id), it) }

    fun name(id: String): String = get(id)?.name ?: pretty(id)
}

/** What the player owns. Immutable: every change returns a new inventory. */
@Serializable
data class Inventory(val counts: Map<String, Int> = emptyMap()) {
    operator fun get(id: String): Int = counts[id] ?: 0

    fun has(id: String, n: Int = 1): Boolean = this[id] >= n

    fun add(id: String, n: Int = 1): Inventory {
        require(n >= 0) { "cannot add a negative amount" }
        return if (n == 0) this else Inventory(counts + (id to this[id] + n))
    }

    fun remove(id: String, n: Int = 1): Inventory {
        require(n >= 0) { "cannot remove a negative amount" }
        check(has(id, n)) { "not enough ${Items.name(id)}: have ${this[id]}, need $n" }
        val left = this[id] - n
        return Inventory(if (left == 0) counts - id else counts + (id to left))
    }

    fun removeAll(items: Map<String, Int>): Inventory = items.entries.fold(this) { inv, (id, n) -> inv.remove(id, n) }

    /**
     * Adds with the stack limits: 999 per item, 9,999 for candies. What does not fit becomes coins 1:1 *(tune)*;
     * returns the new inventory and the coins gained.
     */
    fun addCapped(id: String, n: Int): Pair<Inventory, Int> {
        val limit = if (Items.category(id) == ItemCategory.CANDY) MAX_CANDIES else MAX_STACK
        val room = (limit - this[id]).coerceAtLeast(0)
        val stored = minOf(n, room)
        return add(id, stored) to (n - stored)
    }

    fun asContext(hourOfDay: Int, completedTrials: Set<String> = emptySet()) = EvolutionContext(hourOfDay, counts, completedTrials)

    companion object {
        const val MAX_STACK = 999
        const val MAX_CANDIES = 9_999
    }
}

/** One use of a bag item on a Pokémon, with the choice the item needs: the stat a Bottle Cap perfects, a Mint's nature. */
sealed interface ItemUse {
    val itemId: String

    /** An item that needs no choice: a vitamin, a berry, the Ability Capsule or Patch, a Tera Shard (it sets its own type). */
    data class Plain(override val itemId: String) : ItemUse

    data class BottleCap(val stat: Stat) : ItemUse {
        override val itemId: String get() = Items.BOTTLE_CAP
    }

    data class NatureMint(val nature: Nature) : ItemUse {
        override val itemId: String get() = Items.NATURE_MINT
    }
}

/** What using an item does to a Pokémon. Each function returns the changed Pokémon; the caller removes the item. */
object ItemEffects {
    const val VITAMIN_EVS = 10

    fun useVitamin(p: OwnedPokemon, itemId: String): OwnedPokemon =
        p.gainEv(requireNotNull(Items.vitamins[itemId]) { "$itemId is not a vitamin" }, VITAMIN_EVS)

    /** A Bottle Cap raises one IV to the maximum. */
    fun useBottleCap(p: OwnedPokemon, stat: Stat): OwnedPokemon {
        check(p.ivs[stat] < StatCalculator.MAX_IV) { "${stat.label} is already perfect" }
        return p.withIv(stat, StatCalculator.MAX_IV)
    }

    fun useNatureMint(p: OwnedPokemon, nature: Nature): OwnedPokemon {
        check(p.nature != nature) { "${p.nickname} already has the ${nature.label} nature" }
        return p.withNature(nature)
    }

    /** Swaps to the other regular ability (only for forms with two). */
    fun useAbilityCapsule(p: OwnedPokemon): OwnedPokemon {
        val options = p.form.abilities
        check(options.size == 2 && p.ability in options) { "${p.nickname} has no other regular ability" }
        return p.withAbility(options.first { it != p.ability })
    }

    fun useAbilityPatch(p: OwnedPokemon): OwnedPokemon {
        val hidden = checkNotNull(p.form.hiddenAbility) { "${p.nickname} has no hidden ability" }
        check(p.ability != hidden) { "${p.nickname} already has its hidden ability" }
        return p.withAbility(hidden)
    }

    fun useTeraShard(p: OwnedPokemon, type: PokemonType): OwnedPokemon {
        check(p.teraType != type) { "${p.nickname} is already ${type.label} Tera" }
        return p.withTeraType(type)
    }

    /** [p] after [use], or null when it would do nothing (an IV already perfect, the nature it has, not a usable item). */
    fun apply(p: OwnedPokemon, use: ItemUse): OwnedPokemon? = when (use) {
        is ItemUse.BottleCap -> if (p.ivs[use.stat] < StatCalculator.MAX_IV) useBottleCap(p, use.stat) else null
        is ItemUse.NatureMint -> if (p.nature != use.nature) useNatureMint(p, use.nature) else null
        is ItemUse.Plain -> applyPlain(p, use.itemId)
    }

    /** Whether [itemId] can do anything for [p] with some choice, for the bag's list of who can use it. */
    fun canUse(itemId: String, p: OwnedPokemon): Boolean = when (itemId) {
        Items.BOTTLE_CAP -> p.ivs.values.any { it < StatCalculator.MAX_IV }
        Items.NATURE_MINT -> true
        else -> applyPlain(p, itemId) != null
    }

    private fun applyPlain(p: OwnedPokemon, itemId: String): OwnedPokemon? = when {
        itemId in Items.vitamins -> useVitamin(p, itemId)
        Items.category(itemId) == ItemCategory.BERRY -> feedBerry(p)
        itemId == Items.ABILITY_CAPSULE -> if (p.form.abilities.size == 2 && p.ability in p.form.abilities) useAbilityCapsule(p) else null
        itemId == Items.ABILITY_PATCH -> if (p.form.hiddenAbility.let { it != null && it != p.ability }) useAbilityPatch(p) else null
        else -> PokemonType.entries.firstOrNull { Items.teraShard(it) == itemId }?.takeIf { it != p.teraType }?.let { useTeraShard(p, it) }
    }

    /** Feeds a berry: a little friendship now (and, with Contests, a condition boost). */
    fun feedBerry(p: OwnedPokemon): OwnedPokemon = p.gainFriendship(FriendshipRules.Source.BERRY)

    /** Whether [p] likes, dislikes or is neutral about [flavor]. */
    fun taste(p: OwnedPokemon, flavor: String): Int = when (flavor) {
        Items.flavors[p.nature.raised] -> 1
        Items.flavors[p.nature.lowered] -> -1
        else -> 0
    }
}
