package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.Items
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleDropsTest {
    @Test
    fun everyModuleDropsItsOwnKnownItemsAtASmallChance() {
        ModuleDrops.Module.entries.forEach { m ->
            val table = ModuleDrops.tables.getValue(m)
            assertTrue(table.isNotEmpty(), m.name)
            table.keys.forEach { assertTrue(Items.get(it) != null, "$m drops $it, which is not a bag item") }
            val rolls = (0 until 10_000).mapNotNull { ModuleDrops.roll(m, Random(it)) }
            assertTrue(rolls.size in 700..1_300, "$m: about ${ModuleDrops.PERCENT}% (${rolls.size})")
            assertTrue(rolls.all { it in table }, m.name)
        }
        assertTrue(Items.NATURE_MINT in ModuleDrops.tables.getValue(ModuleDrops.Module.MINT), "Mints in MINT")
        assertTrue(Items.LINK_CORD in ModuleDrops.tables.getValue(ModuleDrops.Module.LINK))
    }

    @Test
    fun aFinishedMintReviewAlwaysPaysANatureMint() {
        (1..20).forEach { seed ->
            val out = RewardEngine.mint(PlayerState(), MintEvent("mint:r:$seed", MintKind.REVIEW, 1_000L, "k$seed"), Random(seed))
            assertTrue(out.state.inventory[Items.NATURE_MINT] >= 1)
            assertTrue(out.rewards.any { it is Reward.ItemDrop && it.itemId == Items.NATURE_MINT })
        }
        val reconciled = (1..200).map { RewardEngine.mint(PlayerState(), MintEvent("mint:a:$it", MintKind.RECONCILED, 1_000L, "k$it"), Random(it)) }
        assertTrue(reconciled.any { it.state.inventory.counts.keys.any { k -> k != Items.RARE_CANDY } }, "some reconciliations drop something")
        assertEquals(0, PlayerState().inventory[Items.NATURE_MINT])
    }
}
