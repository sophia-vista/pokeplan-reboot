package app.pokeplan.world

import app.pokeplan.core.pokemon.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KecleonShopTest {
    @Test
    fun evolutionEssentialsAreAlwaysSold() {
        (0L..30L).forEach { day ->
            Items.evolutionItems.forEach { assertNotNull(KecleonShop.offer(day, it), "$it on day $day") }
            assertNotNull(KecleonShop.offer(day, Items.LINK_CORD))
            assertNotNull(KecleonShop.offer(day, Items.EVERSTONE))
        }
    }

    @Test
    fun theShelfIsTheSameAllDayAndChangesTheNext() {
        assertEquals(KecleonShop.shelf(20_000), KecleonShop.shelf(20_000))
        assertEquals(KecleonShop.SHELF_SIZE, KecleonShop.shelf(20_000).size)
        assertNotEquals(KecleonShop.shelf(20_000), KecleonShop.shelf(20_001))
        val everything = (0L..400L).flatMap { KecleonShop.shelf(it) }.map { it.itemId }.toSet()
        assertEquals(KecleonShop.catalogue.map { it.itemId }.toSet(), everything, "every catalogue item comes round")
        assertTrue(KecleonShop.catalogue.none { c -> KecleonShop.fixed.any { it.itemId == c.itemId } }, "nothing is both")
        val notToday = KecleonShop.catalogue.first { c -> KecleonShop.shelf(5).none { it.itemId == c.itemId } }
        assertNull(KecleonShop.offer(5, notToday.itemId))
        assertTrue(KecleonShop.onSale(5).all { it.price > 0 && Items.get(it.itemId) != null })
    }
}
