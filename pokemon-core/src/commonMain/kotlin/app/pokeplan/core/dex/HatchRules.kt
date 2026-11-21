package app.pokeplan.core.dex

import kotlin.random.Random

object HatchRules {
    const val SUPER_RARE_LINE_BST = 580
    const val COMMON_SHARE = 0.30
    const val UNCOMMON_SHARE = 0.40

    private val cutoffs: Pair<Int, Int> by lazy {
        val strengths = Dex.species
            .filter { it.evolvesFrom == null && !it.isSpecial }
            .map { Dex.lineStrength(it.id) }
            .filter { it < SUPER_RARE_LINE_BST }
            .sorted()
        strengths[(strengths.size * COMMON_SHARE).toInt()] to strengths[(strengths.size * (COMMON_SHARE + UNCOMMON_SHARE)).toInt()]
    }

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

    private val entryPoints: Map<Region, List<SpeciesInfo>> by lazy {
        Region.entries.associateWith { region ->
            val dex = Dex.regionDex(region)
            val ids = dex.map { it.id }.toSet()
            dex.filter { !it.isSpecial && (it.evolvesFrom == null || it.evolvesFrom !in ids) }
        }
    }

    private fun formFor(species: SpeciesInfo, region: Region): FormInfo {
        val variant = Dex.formsOf(species.id).firstOrNull { Dex.regionOfForm(it) == region }
        val usable = variant != null && (region != Region.SINNOH || species.id in Dex.hisuiSpecies)
        return if (usable) variant!! else Dex.defaultForm(species.id)
    }

    fun pool(region: Region, rarity: Rarity): List<HatchOption> =
        entryPoints.getValue(region)
            .filter { rarity(it.id) == rarity }
            .map { HatchOption(it.id, formFor(it, region).identifier) }

    fun roll(region: Region, rarity: Rarity, rng: Random = Random): HatchOption {
        val options = pool(region, rarity)
        return options[rng.nextInt(options.size)]
    }

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
