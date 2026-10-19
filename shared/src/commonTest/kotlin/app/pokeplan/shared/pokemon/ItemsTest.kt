package app.pokeplan.shared.pokemon

import app.pokeplan.shared.dex.Dex
import app.pokeplan.shared.model.PokemonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemsTest {
    @Test
    fun vitaminsAddTenEvsInTheirStatAndRespectCaps() {
        val p = pokemon(Ids.PIKACHU)
        assertEquals(10, ItemEffects.useVitamin(p, "protein").evs.attack)
        assertEquals(10, ItemEffects.useVitamin(p, "carbos").evs.speed)
        val near = pokemon(Ids.PIKACHU, evs = StatSpread(attack = 248))
        assertEquals(252, ItemEffects.useVitamin(near, "protein").evs.attack)
        val fullBudget = pokemon(Ids.PIKACHU, evs = StatSpread(hp = 252, defense = 252, spDefense = 6))
        assertEquals(0, ItemEffects.useVitamin(fullBudget, "carbos").evs.speed)
        assertFailsWith<IllegalArgumentException> { ItemEffects.useVitamin(p, "rare-candy") }
    }

    @Test
    fun bottleCapMaxesOneIv() {
        val p = pokemon(Ids.PIKACHU, ivs = StatSpread.of(10))
        assertEquals(31, ItemEffects.useBottleCap(p, Stat.SPEED).ivs.speed)
        assertEquals(10, ItemEffects.useBottleCap(p, Stat.SPEED).ivs.attack)
        assertFailsWith<IllegalStateException> { ItemEffects.useBottleCap(pokemon(Ids.PIKACHU), Stat.SPEED) }
    }

    @Test
    fun natureMintChangesTheNature() {
        val p = pokemon(Ids.PIKACHU, nature = Nature.HARDY)
        assertEquals(Nature.TIMID, ItemEffects.useNatureMint(p, Nature.TIMID).nature)
        assertFailsWith<IllegalStateException> { ItemEffects.useNatureMint(p, Nature.HARDY) }
    }

    @Test
    fun abilityCapsuleSwapsBetweenTwoRegularAbilities() {
        val rattata = pokemon(Ids.RATTATA) // Run Away / Guts
        val swapped = ItemEffects.useAbilityCapsule(rattata)
        assertEquals(Dex.form("rattata")!!.abilities.last(), swapped.ability)
        assertEquals(rattata.ability, ItemEffects.useAbilityCapsule(swapped).ability)
        val single = pokemon(Ids.GARCHOMP).takeIf { Dex.form("garchomp")!!.abilities.size == 1 }
        if (single != null) assertFailsWith<IllegalStateException> { ItemEffects.useAbilityCapsule(single) }
    }

    @Test
    fun abilityPatchGivesTheHiddenAbility() {
        val p = pokemon(Ids.RATTATA)
        val hidden = Dex.form("rattata")!!.hiddenAbility!!
        assertEquals(hidden, ItemEffects.useAbilityPatch(p).ability)
        assertFailsWith<IllegalStateException> { ItemEffects.useAbilityPatch(ItemEffects.useAbilityPatch(p)) }
    }

    @Test
    fun teraShardAndBerry() {
        val p = pokemon(Ids.PIKACHU, nature = Nature.MODEST)
        assertEquals(PokemonType.FIRE, ItemEffects.useTeraShard(p, PokemonType.FIRE).teraType)
        assertEquals(FriendshipRules.START + FriendshipRules.amount(FriendshipRules.Source.BERRY, BondStyle.GIFTS), ItemEffects.feedBerry(p).friendship)
        assertEquals(1, ItemEffects.taste(p, "dry"), "Modest raises Sp. Atk: likes dry")
        assertEquals(-1, ItemEffects.taste(p, "spicy"), "Modest lowers Attack: dislikes spicy")
        assertEquals(0, ItemEffects.taste(p, "sweet"))
    }

    @Test
    fun inventoryAddsRemovesAndCaps() {
        var inv = Inventory().add("thunder-stone", 2).add(Items.RARE_CANDY, 9_990)
        assertEquals(2, inv["thunder-stone"])
        inv = inv.remove("thunder-stone")
        assertEquals(1, inv["thunder-stone"])
        assertFailsWith<IllegalStateException> { inv.remove("thunder-stone", 2) }
        assertEquals(emptyMap(), Inventory().add("x", 1).remove("x").counts)
        val (capped, coins) = inv.addCapped(Items.RARE_CANDY, 25)
        assertEquals(9_999, capped[Items.RARE_CANDY])
        assertEquals(16, coins, "the overflow becomes coins")
        val (stack, overflow) = Inventory().add("thunder-stone", 995).addCapped("thunder-stone", 10)
        assertEquals(999, stack["thunder-stone"])
        assertEquals(6, overflow)
    }

    @Test
    fun catalogueNamesAndCategories() {
        assertEquals("Rare Candy", Items.name(Items.RARE_CANDY))
        assertEquals(ItemCategory.VITAMIN, Items.category("protein"))
        assertEquals(ItemCategory.LURE, Items.category(Items.lure(PokemonType.FIRE)))
        assertEquals(ItemCategory.EVOLUTION, Items.category("fire-stone"))
        assertEquals(ItemCategory.HELD, Items.category("lucky-egg"))
        assertEquals("Fire Stone", Items.name("fire-stone"))
        assertTrue("thunder-stone" in Items.evolutionItems && "dawn-stone" in Items.evolutionItems)
        assertNotNull(Items.get("amulet-coin"))
    }
}
