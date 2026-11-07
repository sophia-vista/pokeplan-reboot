package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.SpeciesInfo
import app.pokeplan.core.dex.PokemonType
import kotlinx.serialization.Serializable

/**
 * A Pokémon in the player's collection. Changes return a new instance.
 *
 * [personality] is a number rolled at hatch that fixes random choices such as Wurmple's evolution.
 * [candyProgress] counts Rare Candies fed towards the next level.
 */
@Serializable
data class OwnedPokemon(
    val id: String,
    val speciesId: Int,
    val formIdentifier: String,
    val nickname: String,
    val nature: Nature,
    val ability: String,
    val gender: Gender?,
    val ivs: StatSpread,
    val evs: StatSpread = StatSpread.ZERO,
    val level: Int = 1,
    val candyProgress: Int = 0,
    val totalCandies: Int = 0,
    val friendship: Int = FriendshipRules.START,
    val isShiny: Boolean = false,
    val heldItem: String? = null,
    val teraType: PokemonType? = null,
    val cosmeticForm: String? = null,
    val personality: Int = 0,
    val inParty: Boolean,
    val caughtAtMillis: Long,
    /** Which module's party it is in while [inParty] (docs/design.md, "Parties per module"); old saves read as Plan's. */
    val partyId: String = PartyIds.PLAN,
) {
    init {
        require(level in 1..CandyRules.MAX_LEVEL) { "level $level" }
        require(ivs.values.all { it in 0..StatCalculator.MAX_IV }) { "IVs $ivs" }
        require(evs.values.all { it in 0..StatCalculator.MAX_EV_PER_STAT } && evs.total <= StatCalculator.MAX_EV_TOTAL) { "EVs $evs" }
        require(friendship in 0..FriendshipRules.MAX) { "friendship $friendship" }
    }

    val species: SpeciesInfo get() = Dex.species(speciesId)

    val form: FormInfo get() = checkNotNull(Dex.form(formIdentifier)) { "unknown form $formIdentifier" }

    val stats: StatSpread get() = StatCalculator.compute(form.stats, ivs, evs, level, nature)

    /** The types that decide this Pokémon's type perk: its Tera type if it has one, otherwise its own types. */
    val perkTypes: List<PokemonType> get() = teraType?.let { listOf(it) } ?: form.types

    val bondStyle: BondStyle get() = nature.bondStyle

    /** Guardians (legendaries and mythicals) do not take candies: they are already at full power. */
    val canFeed: Boolean get() = level < CandyRules.MAX_LEVEL && !species.isSpecial

    /**
     * Feeds one Rare Candy. [partnerLevel] is the level of the player's strongest partner, for the catch-up discount.
     * Reaching the candy target raises the level by one and adds a little friendship.
     */
    fun feedRareCandy(partnerLevel: Int = level): OwnedPokemon {
        check(canFeed) { "$nickname is already level ${CandyRules.MAX_LEVEL}" }
        val progress = candyProgress + 1
        val fed = copy(totalCandies = totalCandies + 1)
        return if (progress >= CandyRules.candiesNeeded(level, partnerLevel)) {
            fed.copy(level = level + 1, candyProgress = 0)
                .gainFriendship(FriendshipRules.Source.LEVEL_UP)
        } else {
            fed.copy(candyProgress = progress)
        }
    }

    fun gainFriendship(source: FriendshipRules.Source): OwnedPokemon =
        copy(friendship = (friendship + FriendshipRules.amount(source, bondStyle)).coerceAtMost(FriendshipRules.MAX))

    /** Adds up to [points] EVs to one [stat] (a Vitamin), respecting the caps. */
    fun gainEv(stat: Stat, points: Int): OwnedPokemon {
        val room = minOf(StatCalculator.MAX_EV_PER_STAT - evs[stat], StatCalculator.MAX_EV_TOTAL - evs.total)
        return copy(evs = evs.with(stat, evs[stat] + points.coerceIn(0, maxOf(room, 0))))
    }

    fun withNickname(name: String): OwnedPokemon = copy(nickname = name.trim().ifEmpty { species.name })

    fun withHeldItem(item: String?): OwnedPokemon = copy(heldItem = item)

    fun withNature(nature: Nature): OwnedPokemon = copy(nature = nature)

    fun withAbility(ability: String): OwnedPokemon = copy(ability = ability)

    fun withIv(stat: Stat, value: Int): OwnedPokemon = copy(ivs = ivs.with(stat, value))

    fun withTeraType(type: PokemonType?): OwnedPokemon = copy(teraType = type)

    /**
     * The same Pokémon after evolving into [speciesId] / [formIdentifier]. IVs, EVs, nature, friendship and shininess
     * are kept; the ability keeps its slot; a nickname still equal to the old species name follows the evolution.
     */
    fun evolvedInto(speciesId: Int, formIdentifier: String): OwnedPokemon {
        val old = form
        val next = checkNotNull(Dex.form(formIdentifier)) { "unknown form $formIdentifier" }
        val slot = old.abilities.indexOf(ability)
        val newAbility = when {
            slot in next.abilities.indices -> next.abilities[slot]
            ability in next.abilities -> ability
            ability == old.hiddenAbility && next.hiddenAbility != null -> next.hiddenAbility
            else -> next.abilities.firstOrNull() ?: next.hiddenAbility ?: ability
        }
        val oldName = Dex.species(this.speciesId).name
        return copy(
            speciesId = speciesId,
            formIdentifier = formIdentifier,
            nickname = if (nickname == oldName) Dex.species(speciesId).name else nickname,
            ability = newAbility,
            cosmeticForm = null,
        )
    }

    companion object {
        const val PARTY_SIZE = 6
    }
}

/** The ids of the parties: one per module, over one shared PC and Pokédex. */
object PartyIds {
    const val PLAN = "plan"
    const val CARE = "care"
    const val HEAL = "heal"
}
