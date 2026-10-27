package app.pokeplan.care

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.model.Rarity
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
)

/** What befriending a visitor did: its new warmth, and whether it became a friend (then [rewards] holds the Pokémon). */
data class BefriendResult(val visitor: Visitor, val friend: Boolean, val rewards: List<app.pokeplan.core.reward.Reward>)

/** Who visits, and how long they stay. *(tune)* */
object Visitors {
    /** Visitors offered on an ordinary day; a fuller choice after a break. */
    const val OFFERED = 2
    const val OFFERED_AFTER_BREAK = 3

    /** Days of befriending that make a visitor a friend. */
    const val FRIEND_POINTS = 4

    /** A warming visitor not visited for this many days wanders off, with no penalty. */
    const val WANDER_DAYS = 3

    private const val UNCOMMON_SHARE = 0.30
    private const val RARE_SHARE = 0.10

    /** Care actions done lately tilt the type pool a little: calm breaks lean Water and Fairy, routines Normal, skills Grass. */
    const val LEAN_WEIGHT = 1.5

    /** How many visitors to offer on [today] when the last offer was on [lastOffer]. */
    fun count(lastOffer: LocalDate?, today: LocalDate): Int =
        if (lastOffer != null && lastOffer.daysUntil(today) >= WANDER_DAYS) OFFERED_AFTER_BREAK else OFFERED

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
                add(Visitor(option.speciesId, option.formIdentifier, today))
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

    /** The types the care of the last 7 days leans towards. */
    fun lean(data: CareData, today: LocalDate): Set<PokemonType> = buildSet {
        val since = today.minus(DatePeriod(days = 7))
        if (data.breaks.any { it.paid && it.day > since }) addAll(listOf(PokemonType.WATER, PokemonType.FAIRY))
        if (data.routines.any { r -> r.completions.any { it > since } }) add(PokemonType.NORMAL)
        if (data.practices.any { it.paid && it.day > since }) add(PokemonType.GRASS)
    }

    private fun LocalDate.daysUntil(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()
}
