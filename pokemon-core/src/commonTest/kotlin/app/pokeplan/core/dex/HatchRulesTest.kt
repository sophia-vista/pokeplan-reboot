package app.pokeplan.core.dex

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HatchRulesTest {
    /** Regions with a species list (a module region not curated yet has none). */
    private val curated = Region.entries.filter { Dex.regionDex(it).isNotEmpty() }

    private fun rarityOf(name: String) = HatchRules.rarity(Dex.species.first { it.name == name }.id)

    @Test
    fun legendaryMythicalAndSpecialSpeciesNeverHatch() {
        for (s in Dex.species.filter { it.isSpecial }) assertNull(HatchRules.rarity(s.id), s.name)
        for (region in Region.entries) for (r in Rarity.entries) {
            for (o in HatchRules.pool(region, r)) assertTrue(!Dex.species(o.speciesId).isSpecial, "${o.formIdentifier} in $region")
        }
    }

    @Test
    fun pseudoLegendaryLinesAreSuperRare() {
        for (n in listOf("Dratini", "Larvitar", "Bagon", "Beldum", "Gible", "Deino", "Goomy", "Jangmo-o", "Dreepy", "Frigibax")) {
            assertEquals(Rarity.SUPER_RARE, rarityOf(n), n)
        }
        assertEquals(Rarity.COMMON, rarityOf("Caterpie"))
    }

    @Test
    fun rarityFollowsTheLineNotTheStage() {
        // Every member of a line shares the line's rarity.
        for (s in Dex.species.filter { it.evolvesFrom != null && !it.isSpecial }) {
            assertEquals(HatchRules.rarity(Dex.lineRoot(s.id)), HatchRules.rarity(s.id), s.name)
        }
    }

    @Test
    fun nonSuperRareBasicsSplitRoughlyThirtyFortyThirty() {
        val basics = Dex.species.filter { it.evolvesFrom == null && !it.isSpecial }
        val rarities = basics.mapNotNull { HatchRules.rarity(it.id) }.filter { it != Rarity.SUPER_RARE }
        val share = rarities.groupingBy { it }.eachCount().mapValues { it.value * 100.0 / rarities.size }
        assertTrue(share.getValue(Rarity.COMMON) in 20.0..40.0, "common ${share[Rarity.COMMON]}")
        assertTrue(share.getValue(Rarity.UNCOMMON) in 30.0..50.0, "uncommon ${share[Rarity.UNCOMMON]}")
        assertTrue(share.getValue(Rarity.RARE) in 20.0..40.0, "rare ${share[Rarity.RARE]}")
        val superRare = basics.count { HatchRules.rarity(it.id) == Rarity.SUPER_RARE }
        assertTrue(superRare in 8..25, "super rare basics $superRare")
    }

    @Test
    fun everyRegionHasSomethingToHatchInEveryTier() {
        for (region in curated) for (r in Rarity.entries) {
            assertTrue(HatchRules.pool(region, r).isNotEmpty(), "$region has nothing at $r")
        }
    }

    @Test
    fun regionSizesMatchTheGames() {
        val sizes = Region.entries.associateWith { Dex.regionDex(it).size }
        assertTrue(sizes.getValue(Region.PLAN) >= 30, "the Plan Region is a curated list (tools/species/regions/plan.txt)")
        assertEquals(151, sizes.getValue(Region.KANTO))
        assertEquals(256, sizes.getValue(Region.JOHTO))
        assertEquals(211, sizes.getValue(Region.HOENN))
        assertEquals(301, sizes.getValue(Region.UNOVA))
        assertEquals(403, sizes.getValue(Region.ALOLA))
        assertTrue(sizes.getValue(Region.SINNOH) >= 210)
        assertTrue(sizes.getValue(Region.KALOS) >= 400)
        assertTrue(sizes.getValue(Region.GALAR) >= 400)
        assertTrue(sizes.getValue(Region.PALDEA) >= 400)
    }

    @Test
    fun everySpeciesOfARegionCanBeReachedThere() {
        for (region in Region.entries) {
            val dex = Dex.regionDex(region)
            val ids = dex.map { it.id }.toSet()
            val hatchable = HatchRulesAccess.entryIds(region)
            for (s in dex.filter { !it.isSpecial }) {
                var cur = s
                while (cur.id !in hatchable) {
                    val parent = cur.evolvesFrom
                    assertNotNull(parent, "${s.name} cannot be reached in $region")
                    assertTrue(parent in ids, "${s.name} in $region needs ${Dex.species(parent).name}")
                    cur = Dex.species(parent)
                }
            }
        }
    }

    @Test
    fun planRegionIsTheCuratedListInOrder() {
        val plan = Dex.regionDex(Region.PLAN)
        assertTrue(plan.size >= 30)
        assertEquals(plan.size, plan.map { it.id }.toSet().size, "no duplicates")
        // Every rarity tier must still have something to hatch once legendary-class species are removed.
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.PLAN, r).isNotEmpty(), "Plan has nothing at $r")
    }

    @Test
    fun moduleRegionsNeverShareASpecies() {
        val modules = Region.entries.filter { it.isModule }.map { r -> Dex.regionDex(r).map { it.id } }
        val all = modules.flatten()
        assertEquals(all.size, all.toSet().size, "a species is in at most one module region")
    }

    @Test
    fun moduleRegionsCoverTheWholeDexAndEndWithTheirPatrons() {
        val modules = Region.entries.filter { it.isModule }
        assertEquals(6, modules.size, "Plan, Care, Heal, Mint, Home and Link")
        // Every species is in exactly one module region (the "no duplicates" half is moduleRegionsNeverShareASpecies).
        assertEquals(Dex.species.size, modules.sumOf { Dex.regionDex(it).size }, "every species belongs to a module region")
        val patrons = mapOf(
            Region.PLAN to listOf("Uxie", "Mesprit", "Azelf"), Region.CARE to listOf("Cresselia", "Shaymin"),
            Region.HEAL to listOf("Raikou", "Entei", "Suicune"), Region.MINT to listOf("Diancie", "Melmetal"),
            Region.BASE to listOf("Tapu Bulu", "Ogerpon"), Region.LINK to listOf("Latias", "Latios"),
        )
        for (region in modules) {
            val dex = Dex.regionDex(region)
            assertTrue(dex.size in 150..200, "${region.label} has ${dex.size}")
            assertEquals(patrons.getValue(region), dex.takeLast(patrons.getValue(region).size).map { it.name }, "${region.label} ends with its patrons")
            // The quest and milestone species (legendary, mythical, ultra beast, paradox) come after every species that hatches.
            val firstSpecial = dex.indexOfFirst { it.isSpecial }
            assertTrue(dex.drop(firstSpecial).all { it.isSpecial }, "${region.label} puts its quest species last")
            for (r in Rarity.entries) assertTrue(HatchRules.pool(region, r).isNotEmpty(), "${region.label} has nothing at $r")
            assertEquals(2, Dex.regionDex(region).count { s -> HatchRules.rarity(s.id) == Rarity.SUPER_RARE && s.evolvesFrom == null }, "${region.label} has two Super Rare lines")
        }
    }

    @Test
    fun regionalVariantsHatchInTheirRegion() {
        val alola = HatchRules.pool(Region.ALOLA, Rarity.COMMON) + HatchRules.pool(Region.ALOLA, Rarity.UNCOMMON) +
            HatchRules.pool(Region.ALOLA, Rarity.RARE) + HatchRules.pool(Region.ALOLA, Rarity.SUPER_RARE)
        val rattata = alola.first { it.speciesId == 19 }
        assertEquals("rattata-alola", rattata.formIdentifier)
        val kanto = Rarity.entries.flatMap { HatchRules.pool(Region.KANTO, it) }
        assertEquals("rattata", kanto.first { it.speciesId == 19 }.formIdentifier)
    }

    @Test
    fun questSpeciesComeFromTheRegionsLegendaries() {
        val kanto = Dex.legendaryQuestSpecies(Region.KANTO).map { it.name }
        assertTrue(listOf("Articuno", "Zapdos", "Moltres", "Mewtwo").all { it in kanto }, "$kanto")
        assertTrue("Mew" !in kanto, "Mew is a mythical reward")
        assertTrue(Dex.mythicals.any { it.name == "Mew" })
    }

    @Test
    fun rollingReturnsAPoolMember() {
        val rng = Random(9)
        repeat(300) {
            val region = curated[rng.nextInt(curated.size)]
            val rarity = Rarity.entries[rng.nextInt(4)]
            assertTrue(HatchRules.roll(region, rarity, rng) in HatchRules.pool(region, rarity))
        }
    }
}

/** Test-only view of a region's egg entry points. */
internal object HatchRulesAccess {
    fun entryIds(region: Region): Set<Int> =
        Rarity.entries.flatMap { HatchRules.pool(region, it) }.map { it.speciesId }.toSet()
}
