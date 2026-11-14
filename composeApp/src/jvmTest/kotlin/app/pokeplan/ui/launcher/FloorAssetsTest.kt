package app.pokeplan.ui.launcher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dungeon floor's pictures (REP-052) are where the code looks for them: every Explorers of Sky atlas has its full autotile set, and the item sheet is in the code's order. */
class FloorAssetsTest {
    private val tiles = listOf("../assets/tiles", "assets/tiles").map(::File).first { it.isDirectory }

    @Test
    fun theFloorItemSheetIsInTheCodesOrder() {
        val rows = File(tiles, "floor-items.tsv").readLines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
        assertEquals(FLOOR_ITEM_COLUMNS, rows.sortedBy { it[1].toInt() }.map { it[0] })
    }

    @Test
    fun everyOrganicTownHasItsFullAutotileSet() {
        (TownScenes.dungeons + TownScenes.explorers).map { it.first }.filter(TownTiles::organic).forEach { scene ->
            val auto = File(tiles, TownTiles.source(scene) + "-auto.png")
            assertTrue(auto.isFile, "$scene needs ${auto.name}")
            val image = javax.imageio.ImageIO.read(auto)
            assertEquals(Autotile.masks.size * 24, image.width)
            assertEquals(6 * 24, image.height)
        }
        assertTrue(File(tiles, "floor-traps.png").isFile)
    }
}
