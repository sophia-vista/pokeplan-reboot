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

@Serializable
data class Visitor(
    val id: String = "",
    val speciesId: Int,
    val formIdentifier: String,
    val arrivedDay: LocalDate,
    val progress: Int = 0,
    val points: Int = 0,
    val favorite: CareAction = CareAction.TIME,
    val berryBonus: Boolean = false,
    val restUntilMillis: Long = 0,
)

@Serializable
enum class CareAction { WORDS, TIME, GIFT, SERVICE, TOUCH }

@Serializable
enum class Bid { LOOK, NUDGE, SHOW, PLAY, SETTLE }

@Serializable
enum class PetSpot { HEAD, BACK, CHIN }

enum class Reaction(val warmth: Int) { LOVED(2), LIKED(1), FLAT(0) }

@Serializable
data class Visit(
    val speciesId: Int,
    val day: LocalDate,
    val actions: List<CareAction> = emptyList(),
    val warmth: Int = 0,
    val spotFound: Boolean = false,
    val bidsNoticed: Int = 0,
    val pendingBid: Bid? = null,
    val berry: String? = null,
)

data class VisitStep(val reaction: Reaction, val visit: Visit, val result: BefriendResult? = null)

data class BefriendResult(
    val visitor: Visitor, val friend: Boolean, val rewards: List<app.pokeplan.core.reward.Reward>, val joined: Boolean = false,
    val waiting: Boolean = false,
)

object Visitors {
    const val MAX_VISITORS = 4

    const val MAX_QUEUE = 6

    const val MAX_DISMISSED = 20

    const val FIRST_ARRIVALS = 2

    const val CATCH_UP_DAYS = 3
    private const val SECOND_ARRIVAL_CHANCE = 0.35

    const val FRIEND_PROGRESS = 16

    const val LEGACY_DAY_PROGRESS = FRIEND_PROGRESS / 4

    const val REST_MILLIS = 3 * 60 * 60 * 1_000L

    val KINDS = CareAction.entries.size

    private const val UNCOMMON_SHARE = 0.30
    private const val RARE_SHARE = 0.10

    const val LEAN_WEIGHT = 1.5

    fun meadow(visitors: List<Visitor>): List<Visitor> = visitors.take(MAX_VISITORS)
    fun queue(visitors: List<Visitor>): List<Visitor> = visitors.drop(MAX_VISITORS)

    fun resting(visitor: Visitor, nowMillis: Long): Boolean = nowMillis < visitor.restUntilMillis

    fun friend(visitor: Visitor): Boolean = visitor.progress >= FRIEND_PROGRESS

    fun migrated(visitor: Visitor): Visitor {
        val id = visitor.id.ifEmpty { "old-${visitor.speciesId}-${visitor.arrivedDay.toEpochDays()}" }
        return if (visitor.points == 0 && id == visitor.id) visitor else visitor.copy(id = id, progress = visitor.progress + visitor.points * LEGACY_DAY_PROGRESS, points = 0)
    }

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

    fun reaction(visitor: Visitor, visit: Visit, action: CareAction): Reaction = when {
        action in visit.actions -> Reaction.FLAT
        action == visitor.favorite -> Reaction.LOVED
        else -> Reaction.LIKED
    }

    fun finished(visit: Visit): Boolean = visit.actions.size >= KINDS

    fun act(visitor: Visitor, visit: Visit, action: CareAction, spot: PetSpot? = null): Pair<Visit, Reaction> {
        if (finished(visit)) return visit to Reaction.FLAT
        val reaction = reaction(visitor, visit, action)
        val actions = visit.actions + action
        val bid = if (actions.size < KINDS) Bid.entries[((visit.speciesId + visit.day.toEpochDays() + actions.size) % Bid.entries.size).toInt()] else null
        val spotBonus = if (action == CareAction.TOUCH && reaction != Reaction.FLAT && spot == likedSpot(visitor.speciesId) && !visit.spotFound) 1 else 0
        return visit.copy(actions = actions, warmth = visit.warmth + reaction.warmth + spotBonus, spotFound = visit.spotFound || spotBonus > 0, pendingBid = bid) to reaction
    }

    fun likedSpot(speciesId: Int): PetSpot = PetSpot.entries[speciesId % PetSpot.entries.size]

    fun likedBerry(speciesId: Int): String {
        val flavors = Items.flavors.values.sorted()
        return Items.berry(flavors[(speciesId / PetSpot.entries.size) % flavors.size])
    }

    fun berryBonusDue(visitor: Visitor, berry: String?): Boolean = !visitor.berryBonus && berry != null && berry == likedBerry(visitor.speciesId)

    const val BERRY_BONUS = 4

    fun notice(visit: Visit): Visit =
        if (visit.pendingBid == null) visit else visit.copy(pendingBid = null, bidsNoticed = visit.bidsNoticed + 1, warmth = visit.warmth + 1)

    fun lean(data: CareData, today: LocalDate): Set<PokemonType> = buildSet {
        val since = today.minus(DatePeriod(days = 7))
        if (data.breaks.any { it.paid && it.day > since }) addAll(listOf(PokemonType.WATER, PokemonType.FAIRY))
        if (data.routines.any { r -> r.completions.any { it > since } }) add(PokemonType.NORMAL)
        if (data.practices.any { it.paid && it.day > since }) add(PokemonType.GRASS)
    }
}
