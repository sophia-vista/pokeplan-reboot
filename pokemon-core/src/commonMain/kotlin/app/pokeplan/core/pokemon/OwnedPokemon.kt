package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.MoveInfo
import app.pokeplan.core.dex.SpeciesInfo
import app.pokeplan.core.dex.PokemonType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * A Pokémon in the player's collection. Changes return a new instance.
 *
 * [personality] is a number rolled at hatch that fixes random choices such as Wurmple's evolution.
 * [candyProgress] counts Rare Candies fed towards the next level.
 * [xp] is experience from a World expedition, in points: [CandyRules.XP_PER_CANDY] of them move [candyProgress] on by one (a candy of
 * progress, without a candy spent), so there is one level curve however a Pokémon is trained. It is always below that and is never lost.
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
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val xp: Int = 0,
    val friendship: Int = FriendshipRules.START,
    val isShiny: Boolean = false,
    val heldItem: String? = null,
    /** The moves chosen for it, as move identifiers (at most [Moveset.MAX]); none stored means its natural moves ([Moveset]). */
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val moves: List<String> = emptyList(),
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

    /** The moves it has now ([Moveset.active]): the chosen ones, or its natural moves. */
    val activeMoves: List<MoveInfo> get() = Moveset.active(this)

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

    /**
     * Gains [points] of expedition experience, once per floor and member (WORLD.md 5.2): every [CandyRules.XP_PER_CANDY] points are a
     * step of candy progress, so a level comes at the same cost as with Rare Candies (with the same catch-up discount). A Guardian and a
     * Pokémon at the top level gain nothing, as they take no candies. [partnerLevel] is the level of the player's strongest partner.
     */
    fun gainXp(points: Int, partnerLevel: Int = level): OwnedPokemon {
        if (points <= 0 || !canFeed) return this
        var p = this
        var pending = xp + points
        while (pending >= CandyRules.XP_PER_CANDY && p.canFeed) {
            pending -= CandyRules.XP_PER_CANDY
            val progress = p.candyProgress + 1
            p = if (progress >= CandyRules.candiesNeeded(p.level, partnerLevel)) p.copy(level = p.level + 1, candyProgress = 0).gainFriendship(FriendshipRules.Source.LEVEL_UP)
            else p.copy(candyProgress = progress)
        }
        return p.copy(xp = if (p.canFeed) pending else 0)
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

    /** The same Pokémon with [identifiers] as its moves (an empty list: back to the natural moves). Check [Moveset.canSet] first. */
    fun withMoves(identifiers: List<String>): OwnedPokemon = copy(moves = identifiers)

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
    const val MINT = "mint"
    const val BASE = "home"
    const val LINK = "link"
}
