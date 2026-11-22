package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import kotlinx.serialization.Serializable
import kotlin.random.Random

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

object SafariRules {
    const val PEOPLE_CAP = 20

    const val BASE_SLOTS = 2
    const val PEOPLE_PER_SLOT = 6
    const val MAX_SLOTS = 5

    private val SLOT_RARITY = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.RARE)

    const val SHINY_ROLLS = 1.0

    fun slots(spread: Int): Int = (BASE_SLOTS + spread.coerceIn(0, PEOPLE_CAP) / PEOPLE_PER_SLOT).coerceAtMost(MAX_SLOTS)

    fun seed(ref: String): Long {
        var h = -3750763034362895579L
        for (c in ref) { h = h xor c.code.toLong(); h *= 1099511628211L }
        return h
    }

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
