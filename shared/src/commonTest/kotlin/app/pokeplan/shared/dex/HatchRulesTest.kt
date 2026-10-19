package app.pokeplan.shared.dex

import app.pokeplan.shared.model.Rarity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HatchRulesTest {
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
        for (region in Region.entries) for (r in Rarity.entries) {
            assertTrue(HatchRules.pool(region, r).isNotEmpty(), "$region has nothing at $r")
        }
    }

    @Test
    fun regionSizesMatchTheGames() {
        val sizes = Region.entries.associateWith { Dex.regionDex(it).size }
        assertTrue(sizes.getValue(Region.HOME) >= 30, "the Home Region is the player's own list (tools/species/home-region.txt)")
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
    fun homeRegionIsTheFavouritesListInOrder() {
        val home = Dex.regionDex(Region.HOME)
        assertTrue(home.size >= 30)
        assertEquals(home.size, home.map { it.id }.toSet().size, "no duplicates")
        // Every rarity tier must still have something to hatch once legendary-class species are removed.
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.HOME, r).isNotEmpty(), "Home has nothing at $r")
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
            val region = Region.entries[rng.nextInt(Region.entries.size)]
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
