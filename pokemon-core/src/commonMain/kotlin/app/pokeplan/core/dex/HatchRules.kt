package app.pokeplan.core.dex

import app.pokeplan.core.model.Rarity
import kotlin.random.Random

/**
 * Which species can hatch, how rare each one is, and what a region's egg pool contains.
 *
 * Rarity comes from the strength of the species' whole evolution line (the highest base stat total of
 * any default form in it):
 *  - lines at or above [SUPER_RARE_LINE_BST] (the pseudo-legendary families and a few others) are Super Rare;
 *  - the rest are split by rank into Common ([COMMON_SHARE]), Uncommon ([UNCOMMON_SHARE]) and Rare (the remainder).
 *
 * Legendary-class and mythical species have no rarity: they are quest and milestone rewards.
 */
object HatchRules {
    const val SUPER_RARE_LINE_BST = 580
    const val COMMON_SHARE = 0.30
    const val UNCOMMON_SHARE = 0.40

    /** Line-strength cutoffs (below the first is Common, below the second Uncommon) over all basic, non-special species. */
    private val cutoffs: Pair<Int, Int> by lazy {
        val strengths = Dex.species
            .filter { it.evolvesFrom == null && !it.isSpecial }
            .map { Dex.lineStrength(it.id) }
            .filter { it < SUPER_RARE_LINE_BST }
            .sorted()
        strengths[(strengths.size * COMMON_SHARE).toInt()] to strengths[(strengths.size * (COMMON_SHARE + UNCOMMON_SHARE)).toInt()]
    }

    /** The rarity of [speciesId], or null when it cannot hatch (legendary-class or mythical). */
    fun rarity(speciesId: Int): Rarity? {
        if (Dex.species(speciesId).isSpecial) return null
        val strength = Dex.lineStrength(speciesId)
        val (commonBelow, uncommonBelow) = cutoffs
        return when {
            strength >= SUPER_RARE_LINE_BST -> Rarity.SUPER_RARE
            strength < commonBelow -> Rarity.COMMON
            strength < uncommonBelow -> Rarity.UNCOMMON
            else -> Rarity.RARE
        }
    }

    /**
     * Where a region's eggs start: species that are not special and whose pre-evolution is not in the region's
     * dex (so a region that has Pikachu but not Pichu can hatch Pikachu, and no region needs another to complete).
     */
    private val entryPoints: Map<Region, List<SpeciesInfo>> by lazy {
        Region.entries.associateWith { region ->
            val dex = Dex.regionDex(region)
            val ids = dex.map { it.id }.toSet()
            dex.filter { !it.isSpecial && (it.evolvesFrom == null || it.evolvesFrom !in ids) }
        }
    }

    /** The form shown when [species] hatches in [region]: the region's own variant if there is one, else the default. */
    private fun formFor(species: SpeciesInfo, region: Region): FormInfo {
        val variant = Dex.formsOf(species.id).firstOrNull { Dex.regionOfForm(it) == region }
        val usable = variant != null && (region != Region.SINNOH || species.id in Dex.hisuiSpecies)
        return if (usable) variant!! else Dex.defaultForm(species.id)
    }

    /** Everything that can hatch in [region] at [rarity]. */
    fun pool(region: Region, rarity: Rarity): List<HatchOption> =
        entryPoints.getValue(region)
            .filter { rarity(it.id) == rarity }
            .map { HatchOption(it.id, formFor(it, region).identifier) }

    fun roll(region: Region, rarity: Rarity, rng: Random = Random): HatchOption {
        val options = pool(region, rarity)
        return options[rng.nextInt(options.size)]
    }

    /** Like [roll], but each option is picked in proportion to [weight] (lures and spotlight days). */
    fun rollWeighted(region: Region, rarity: Rarity, rng: Random, weight: (HatchOption) -> Double): HatchOption {
        val options = pool(region, rarity)
        val weights = options.map { weight(it).coerceAtLeast(0.0) }
        var pick = rng.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            pick -= w
            if (pick < 0) return options[i]
        }
        return options.last()
    }
}
