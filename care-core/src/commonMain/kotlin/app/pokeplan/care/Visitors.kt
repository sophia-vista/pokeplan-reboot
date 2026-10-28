package app.pokeplan.care

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.model.Rarity
import app.pokeplan.core.pokemon.Items
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A Pokémon visiting Care's scene, one the player may befriend (docs/ecosystem.md, section 6, "Befriending"). [points] is how
 * warm it is: one for each day the player spent the day's befriend on it, [FRIEND_POINTS] make it a friend.
 */
@Serializable
data class Visitor(
    val speciesId: Int,
    val formIdentifier: String,
    val arrivedDay: LocalDate,
    val points: Int = 0,
    val lastVisitDay: LocalDate? = null,
    /** The way of caring it likes best; the player finds it out by trying (the five love languages as a vocabulary). */
    val favorite: CareAction = CareAction.TIME,
    /** Whether its liked berry has given its one bonus day ([Visitors.likedBerry]). */
    val berryBonus: Boolean = false,
)

/**
 * The ways to care for a visitor, named after Chapman's five love languages. The evidence that matching a person's language
 * improves a relationship is weak (Impett and colleagues), so every action warms a visitor and its favourite only warms more.
 */
@Serializable
enum class CareAction { WORDS, TIME, GIFT, SERVICE, TOUCH }

/** A small moment a visitor offers during a visit, in Gottman's sense of a bid for connection; noticing it is "turning toward". */
@Serializable
enum class Bid { LOOK, NUDGE, SHOW, PLAY, SETTLE }

/** Where a visitor likes a gentle pat; the same for a species every time, so what is learned stays learned. */
@Serializable
enum class PetSpot { HEAD, BACK, CHIN }

/** How a visitor took an action: its favourite, a kind of care it likes, or one it already had this visit. */
enum class Reaction(val warmth: Int) { LOVED(2), LIKED(1), FLAT(0) }

/** A visit in progress: the actions done so far, and the bid waiting to be noticed, if any. */
@Serializable
data class Visit(
    val speciesId: Int,
    val day: LocalDate,
    val actions: List<CareAction> = emptyList(),
    val warmth: Int = 0,
    /** Whether the pat found its liked spot this visit (it warms once). */
    val spotFound: Boolean = false,
    val bidsNoticed: Int = 0,
    val pendingBid: Bid? = null,
    /** The berry offered as this visit's treat (an item id), if one was. */
    val berry: String? = null,
)

/** One step of a visit: how the visitor took it, the visit now, and once the visit is over, what it came to. */
data class VisitStep(val reaction: Reaction, val visit: Visit, val result: BefriendResult? = null)

/**
 * What a visit did: the visitor's days now (a visit that was not warm enough adds none), and whether it became a friend (then
 * [rewards] holds the Pokémon). [joined] is true when the visitor itself joined, rather than leaving an egg of its family.
 */
data class BefriendResult(val visitor: Visitor, val friend: Boolean, val rewards: List<app.pokeplan.core.reward.Reward>, val joined: Boolean = false)

/** Who visits, and how long they stay. *(tune)* */
object Visitors {
    /** Visitors offered on an ordinary day; a fuller choice after a break. */
    const val OFFERED = 2
    const val OFFERED_AFTER_BREAK = 3

    /**
     * Visitors in the meadow at once: new ones come only while there is room, so visiting every one each day stays a few
     * short visits, and friends come at most this many a day. *(tune)*
     */
    const val MAX_VISITORS = 4

    /** Days of befriending that make a visitor a friend. */
    const val FRIEND_POINTS = 4

    /** A warming visitor not visited for this many days wanders off, with no penalty. */
    const val WANDER_DAYS = 3

    /** Actions in a visit; a bid is offered before the second and the third. */
    const val ACTIONS_PER_VISIT = 3

    /** Warmth that makes a visit count as a day with the visitor: three different kinds of care alone (3) are not quite enough. *(tune)* */
    const val WARM_VISIT = 4

    private const val UNCOMMON_SHARE = 0.30
    private const val RARE_SHARE = 0.10

    /** Care actions done lately tilt the type pool a little: calm breaks lean Water and Fairy, routines Normal, skills Grass. */
    const val LEAN_WEIGHT = 1.5

    /** How many new visitors to offer on [today] when the last offer was on [lastOffer] and [staying] are still warming up. */
    fun count(lastOffer: LocalDate?, today: LocalDate, staying: Int = 0): Int =
        minOf(if (lastOffer != null && lastOffer.daysUntil(today) >= WANDER_DAYS) OFFERED_AFTER_BREAK else OFFERED, MAX_VISITORS - staying).coerceAtLeast(0)

    /**
     * Today's new visitors from the Care Region (Common to Rare; Super Rare and the patrons stay quest-only), the same for the
     * same device [seed] and day. [exclude] are species already visiting. [luck] is Care's Visitor luck in percent: each
     * visitor has that chance of coming one rarity higher. [lean] are the types to favour.
     */
    fun offer(seed: Int, today: LocalDate, count: Int, exclude: Set<Int>, luck: Double, lean: Set<PokemonType>): List<Visitor> {
        val rng = Random(seed.toLong() * 31 + today.toEpochDays())
        val taken = exclude.toMutableSet()
        return buildList {
            repeat(count) {
                var rarity = when (val roll = rng.nextDouble()) {
                    in 0.0..RARE_SHARE -> Rarity.RARE
                    in RARE_SHARE..(RARE_SHARE + UNCOMMON_SHARE) -> Rarity.UNCOMMON
                    else -> Rarity.COMMON
                }
                if (rarity != Rarity.RARE && rng.nextDouble() * 100 < luck) rarity = rarity.next()
                val option = pick(rarity, rng, taken, lean) ?: return@repeat
                taken += option.speciesId
                add(Visitor(option.speciesId, option.formIdentifier, today, favorite = CareAction.entries[rng.nextInt(CareAction.entries.size)]))
            }
        }
    }

    private fun pick(rarity: Rarity, rng: Random, taken: Set<Int>, lean: Set<PokemonType>): app.pokeplan.core.dex.HatchOption? {
        // A tier that has nothing left falls back to the one below.
        var tier: Rarity? = rarity
        while (tier != null) {
            val options = HatchRules.pool(Region.CARE, tier).filter { it.speciesId !in taken }
            if (options.isNotEmpty()) {
                val weights = options.map { o -> if (Dex.form(o.formIdentifier)!!.types.any { it in lean }) LEAN_WEIGHT else 1.0 }
                var pick = rng.nextDouble() * weights.sum()
                for ((i, w) in weights.withIndex()) {
                    pick -= w
                    if (pick < 0) return options[i]
                }
                return options.last()
            }
            tier = if (tier.ordinal == 0) null else Rarity.entries[tier.ordinal - 1]
        }
        return null
    }

    fun start(speciesId: Int, today: LocalDate) = Visit(speciesId, today)

    /** How [visitor] takes [action] in [visit]: a kind of care it already had this visit warms it no more. */
    fun reaction(visitor: Visitor, visit: Visit, action: CareAction): Reaction = when {
        action in visit.actions -> Reaction.FLAT
        action == visitor.favorite -> Reaction.LOVED
        else -> Reaction.LIKED
    }

    /** Whether all of [ACTIONS_PER_VISIT] are done. */
    fun finished(visit: Visit): Boolean = visit.actions.size >= ACTIONS_PER_VISIT

    /** Whether the visit warmed the visitor enough to count as a day. */
    fun warm(visit: Visit): Boolean = visit.warmth >= WARM_VISIT

    /**
     * Does [action]. An unnoticed bid is let go without cost, and after the first and second action the visitor makes a new
     * one (the same for the same visitor and day).
     */
    fun act(visitor: Visitor, visit: Visit, action: CareAction, spot: PetSpot? = null): Pair<Visit, Reaction> {
        if (finished(visit)) return visit to Reaction.FLAT
        val reaction = reaction(visitor, visit, action)
        val actions = visit.actions + action
        val bid = if (actions.size < ACTIONS_PER_VISIT) Bid.entries[((visit.speciesId + visit.day.toEpochDays() + actions.size) % Bid.entries.size).toInt()] else null
        // A pat on its liked spot warms one more, once a visit and only when the pat was new; a wrong spot costs nothing.
        val spotBonus = if (action == CareAction.TOUCH && reaction != Reaction.FLAT && spot == likedSpot(visitor.speciesId) && !visit.spotFound) 1 else 0
        return visit.copy(actions = actions, warmth = visit.warmth + reaction.warmth + spotBonus, spotFound = visit.spotFound || spotBonus > 0, pendingBid = bid) to reaction
    }

    fun likedSpot(speciesId: Int): PetSpot = PetSpot.entries[speciesId % PetSpot.entries.size]

    /** The berry a species likes, the same every time (one of the five flavours), so what is learned stays learned. */
    fun likedBerry(speciesId: Int): String {
        val flavors = Items.flavors.values.sorted()
        return Items.berry(flavors[(speciesId / PetSpot.entries.size) % flavors.size])
    }

    /**
     * The days a finished [visit] adds to [visitor]: one for a warm visit, and one more the first time it is given the berry it
     * likes as a treat (once per visitor, warm visit or not). *(tune)*
     */
    fun daysEarned(visitor: Visitor, visit: Visit): Int =
        (if (warm(visit)) 1 else 0) + (if (berryBonusDue(visitor, visit)) BERRY_BONUS else 0)

    fun berryBonusDue(visitor: Visitor, visit: Visit): Boolean = !visitor.berryBonus && visit.berry == likedBerry(visitor.speciesId)

    /** Days the liked berry adds, once per visitor. *(tune)* */
    const val BERRY_BONUS = 1

    /** Turns toward the visitor's bid: one more warmth. Nothing when there is none. */
    fun notice(visit: Visit): Visit =
        if (visit.pendingBid == null) visit else visit.copy(pendingBid = null, bidsNoticed = visit.bidsNoticed + 1, warmth = visit.warmth + 1)

    /** The types the care of the last 7 days leans towards. */
    fun lean(data: CareData, today: LocalDate): Set<PokemonType> = buildSet {
        val since = today.minus(DatePeriod(days = 7))
        if (data.breaks.any { it.paid && it.day > since }) addAll(listOf(PokemonType.WATER, PokemonType.FAIRY))
        if (data.routines.any { r -> r.completions.any { it > since } }) add(PokemonType.NORMAL)
        if (data.practices.any { it.paid && it.day > since }) add(PokemonType.GRASS)
    }

    private fun LocalDate.daysUntil(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()
}
