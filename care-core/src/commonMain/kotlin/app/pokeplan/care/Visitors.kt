package app.pokeplan.care

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.pokemon.Items
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A Pokémon visiting Care's meadow, one the player may befriend (docs/pokelife `MODULES/CARE.md`, section 6). [progress] is how
 * warm it is: every kind of care it takes adds to it at once, and [Visitors.FRIEND_PROGRESS] makes it a friend. It is never reset
 * and never counts days: absence, a closed app or a missed day change nothing. Visitors stay until they are befriended or sent away.
 */
@Serializable
data class Visitor(
    /** Who it is, for as long as it stays: the device seed, the day it arrived and its place in that day's arrivals. Old saves are given one when loaded. */
    val id: String = "",
    val speciesId: Int,
    val formIdentifier: String,
    val arrivedDay: LocalDate,
    val progress: Int = 0,
    /** The warm days the old system counted (four made a friend). Read from an old save and moved into [progress] once ([Visitors.migrated]); 0 after. */
    val points: Int = 0,
    /** The way of caring it likes best; the player finds it out by trying (the five love languages as a vocabulary). */
    val favorite: CareAction = CareAction.TIME,
    /** Whether its liked berry has given its one bonus ([Visitors.likedBerry]). */
    val berryBonus: Boolean = false,
    /** After a visit it rests until this time (millis), so a visit is a calm moment and not something to repeat in a row; 0 when it is not resting. */
    val restUntilMillis: Long = 0,
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

/** A visit in progress: the kinds of care done so far, and the bid waiting to be noticed, if any. It stays until the player leaves it or it comes to its end. */
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
 * What a visit came to: the visitor as it is now, and whether it became a friend (then [rewards] holds the Pokémon). [joined] is true when the visitor itself joined, rather than leaving an egg of its family.
 */
data class BefriendResult(
    val visitor: Visitor, val friend: Boolean, val rewards: List<app.pokeplan.core.reward.Reward>, val joined: Boolean = false,
    /** The visitor is warm enough to be a friend but the world paid nothing now (its daily limit of friends): it stays, ready, and the next visit makes it one. */
    val waiting: Boolean = false,
)

/** Who visits, how many fit and how a visitor warms. *(tune)* */
object Visitors {
    /** Visitors in the meadow at once; the buddy is not one of them. */
    const val MAX_VISITORS = 4

    /** Visitors waiting for a place. They never expire; new arrivals pause while the queue is full. */
    const val MAX_QUEUE = 6

    /** Visitors the player sent away, kept so they can be asked back. The oldest are forgotten past this. */
    const val MAX_DISMISSED = 20

    /** The first arrivals on a new device, so the meadow is not empty. */
    const val FIRST_ARRIVALS = 2

    /** A day brings one arrival, and sometimes a second; a few days away are caught up, at most this many. Care activity changes none of it. */
    const val CATCH_UP_DAYS = 3
    private const val SECOND_ARRIVAL_CHANCE = 0.35

    /** Warmth that makes a visitor a friend. A visit of every kind of care and its small moments warms 6 to 11, so it takes about two visits. */
    const val FRIEND_PROGRESS = 16

    /** What one warm day of the old system is worth (four made a friend), so no visitor loses what it had. */
    const val LEGACY_DAY_PROGRESS = FRIEND_PROGRESS / 4

    /** A visitor rests this long after a visit before the next one counts. It does not stop playing with it. */
    const val REST_MILLIS = 3 * 60 * 60 * 1_000L

    /** A visit is every kind of care once; it ends when all are done, when the visitor is warm enough, or when the player leaves. */
    val KINDS = CareAction.entries.size

    private const val UNCOMMON_SHARE = 0.30
    private const val RARE_SHARE = 0.10

    /** Care actions done lately tilt the type pool a little: calm breaks lean Water and Fairy, routines Normal, skills Grass. */
    const val LEAN_WEIGHT = 1.5

    /** Visitors in the meadow (the first [MAX_VISITORS] by arrival) and in the queue (the rest). */
    fun meadow(visitors: List<Visitor>): List<Visitor> = visitors.take(MAX_VISITORS)
    fun queue(visitors: List<Visitor>): List<Visitor> = visitors.drop(MAX_VISITORS)

    fun resting(visitor: Visitor, nowMillis: Long): Boolean = nowMillis < visitor.restUntilMillis

    fun friend(visitor: Visitor): Boolean = visitor.progress >= FRIEND_PROGRESS

    /** A visitor from an old save: it gets an id, and the warm days it had become progress. Already migrated: unchanged. */
    fun migrated(visitor: Visitor): Visitor {
        val id = visitor.id.ifEmpty { "old-${visitor.speciesId}-${visitor.arrivedDay.toEpochDays()}" }
        return if (visitor.points == 0 && id == visitor.id) visitor else visitor.copy(id = id, progress = visitor.progress + visitor.points * LEGACY_DAY_PROGRESS, points = 0)
    }

    /**
     * The visitors that arrived since the last arrivals on [lastOffer] (null: this device's first): a day brings one, and
     * sometimes two, from the Care Region (Common to Rare; Super Rare and the patrons stay quest-only), the same for the same
     * device [seed] and day. Up to [CATCH_UP_DAYS] days are caught up, and no more than [room] come. [present] are the species
     * already here or waiting. [luck] is Care's Visitor luck in percent: each visitor has that chance of coming one rarity
     * higher. [lean] are the types to favour.
     */
    fun arrivals(seed: Int, lastOffer: LocalDate?, today: LocalDate, room: Int, present: Set<Int>, luck: Double, lean: Set<PokemonType>): List<Visitor> {
        val days = if (lastOffer == null) 1 else minOf(lastOffer.daysUntil(today), CATCH_UP_DAYS)
        val taken = present.toMutableSet()
        val out = mutableListOf<Visitor>()
        for (back in days - 1 downTo 0) {
            val day = today.minus(DatePeriod(days = back))
            val count = if (lastOffer == null) FIRST_ARRIVALS else 1 + if (Random(seed.toLong() * 17 + day.toEpochDays()).nextDouble() < SECOND_ARRIVAL_CHANCE) 1 else 0
            val left = room - out.size
            if (left <= 0) break
            val arrived = offer(seed, day, minOf(count, left), taken, luck, lean)
            taken += arrived.map { it.speciesId }
            out += arrived
        }
        return out
    }

    /** [count] new visitors for [day], the same for the same [seed] and day. [exclude] are species already here. */
    fun offer(seed: Int, day: LocalDate, count: Int, exclude: Set<Int>, luck: Double, lean: Set<PokemonType>): List<Visitor> {
        val rng = Random(seed.toLong() * 31 + day.toEpochDays())
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
                add(Visitor("v$seed-${day.toEpochDays()}-$size", option.speciesId, option.formIdentifier, day, favorite = CareAction.entries[rng.nextInt(CareAction.entries.size)]))
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

    /** Whether every kind of care has been done this visit. */
    fun finished(visit: Visit): Boolean = visit.actions.size >= KINDS

    /**
     * Does [action]. An unnoticed bid is let go without cost, and after each action but the last the visitor makes a new one
     * (the same for the same visitor and day).
     */
    fun act(visitor: Visitor, visit: Visit, action: CareAction, spot: PetSpot? = null): Pair<Visit, Reaction> {
        if (finished(visit)) return visit to Reaction.FLAT
        val reaction = reaction(visitor, visit, action)
        val actions = visit.actions + action
        val bid = if (actions.size < KINDS) Bid.entries[((visit.speciesId + visit.day.toEpochDays() + actions.size) % Bid.entries.size).toInt()] else null
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

    /** Whether giving [visit]'s berry gives [visitor] its one bonus: it is the berry the species likes and it has not had it before. */
    fun berryBonusDue(visitor: Visitor, berry: String?): Boolean = !visitor.berryBonus && berry != null && berry == likedBerry(visitor.speciesId)

    /** Warmth the liked berry adds, once per visitor. *(tune)* */
    const val BERRY_BONUS = 4

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
}
