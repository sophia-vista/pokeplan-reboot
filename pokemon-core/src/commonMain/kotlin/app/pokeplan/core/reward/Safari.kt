package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * What a Friend Safari visit tells the shared world (docs/pokelife `MODULES/LINK.md` 9.5): a stable event [id] (one visit to one person's Safari a day, so a
 * replay never pays twice), the source [module], an opaque reference [ref] (a hash of the person's random record key: it seeds that person's Safari and
 * means nothing outside LINK), [spread] (how many people LINK holds, already capped by LINK: it only widens the Safari) and the time. No name, number,
 * birthday, personality, chart or note exists in it to be read, and nothing is carried about who the person is.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class SafariEvent(
    val id: String,
    val nowMillis: Long,
    val ref: String,
    val spread: Int,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val policy: Int = POLICY,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    val module: String = LinkEvent.MODULE,
) {
    companion object { const val POLICY = 1 }
}

/**
 * A person's Friend Safari (LINK.md 9.5): a small pool of Link Region Pokémon that only that person's opaque [SafariEvent.ref] decides, like a friend code.
 * Each slot is fixed by the seed alone, so adding people never reshuffles a Safari: it only unlocks more slots, and with more slots each visit meets any one
 * of them less often (more friends, more species, each rarer). The count that unlocks slots is capped, so a pile of made-up contacts buys nothing.
 */
object SafariRules {
    /** The people count that stops widening the pool. *(tune)* Keep equal to LINK's `LinkRules.SAFARI_PEOPLE_CAP`. */
    const val PEOPLE_CAP = 20

    /** Slots with no one else in LINK, and how many more people unlock each further slot, up to [MAX_SLOTS]. *(tune)* */
    const val BASE_SLOTS = 2
    const val PEOPLE_PER_SLOT = 6
    const val MAX_SLOTS = 5

    /** The rarity each slot draws from: the first two are common-ish, the later ones rarer. *(tune)* */
    private val SLOT_RARITY = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.RARE)

    /** Shiny rolls a Safari catch gets: the ordinary chance, nothing more. */
    const val SHINY_ROLLS = 1.0

    /** How many slots a Safari has when LINK holds [spread] people. */
    fun slots(spread: Int): Int = (BASE_SLOTS + spread.coerceIn(0, PEOPLE_CAP) / PEOPLE_PER_SLOT).coerceAtMost(MAX_SLOTS)

    /** A stable 64-bit seed from [ref] (FNV-1a over its characters, the same on every platform). */
    fun seed(ref: String): Long {
        var h = -3750763034362895579L // 0xcbf29ce484222325
        for (c in ref) { h = h xor c.code.toLong(); h *= 1099511628211L }
        return h
    }

    /** The Safari's pool for [ref] with [spread] people: the first [slots] of its fixed slots, each a different species. Stable as [spread] grows. */
    fun pool(ref: String, spread: Int): List<OutbreakOption> {
        val seed = seed(ref)
        val chosen = LinkedHashMap<Int, OutbreakOption>()
        for (i in 0 until slots(spread)) {
            val rng = Random(seed + i * 7919L)
            var rarity = SLOT_RARITY[i]
            var options = HatchRules.pool(Region.LINK, rarity)
            while (options.isEmpty() && rarity.ordinal > 0) { rarity = Rarity.entries[rarity.ordinal - 1]; options = HatchRules.pool(Region.LINK, rarity) }
            val fresh = options.filter { it.speciesId !in chosen }.ifEmpty { options }
            if (fresh.isEmpty()) continue
            val pick = fresh[rng.nextInt(fresh.size)]
            if (pick.speciesId !in chosen) chosen[pick.speciesId] = OutbreakOption(pick.speciesId, pick.formIdentifier)
        }
        return chosen.values.toList()
    }
}
