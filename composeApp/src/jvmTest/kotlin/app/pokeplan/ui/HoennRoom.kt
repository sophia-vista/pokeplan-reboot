package app.pokeplan.ui

import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

internal object HoennRoom {
    const val TILE = 16
    private val hoenn = listOf("../assets/hoenn", "assets/hoenn").map(::File).first { it.isDirectory }
    private val pmd = listOf("../assets/pmd", "assets/pmd").map(::File).first { it.isDirectory }

    class Placed(val file: String, val centre: Int, val bottom: Int)
    class Mon(val formId: Int, val centre: Int, val bottom: Int)

    private fun palette(file: File): IntArray = file.readLines().drop(3).filter { it.isNotBlank() }.map { line ->
        val (r, g, b) = line.trim().split(' ').map(String::toInt)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }.toIntArray()

    private fun metatiles(file: File): List<IntArray> {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return List(buf.remaining() / 16) { IntArray(8) { buf.short.toInt() and 0xFFFF } }
    }

    private fun indices(file: File): Pair<Array<IntArray>, Int> {
        val img = ImageIO.read(file)
        val raster = img.raster
        return Array(img.height) { y -> IntArray(img.width) { x -> raster.getSample(x, y, 0) } } to img.width
    }

    fun floor(layout: String, tileset: String, width: Int, height: Int): BufferedImage {
        val primary = File(hoenn, "secret-base/primary")
        val secondary = File(hoenn, "secret-base/secondary/$tileset")
        val palettes = (0 until 16).map { palette(File(if (it < 6) primary else secondary, "palettes/%02d.pal".format(it))) }
        val (primaryTiles, primaryWidth) = indices(File(primary, "tiles.png"))
        val (secondaryTiles, secondaryWidth) = indices(File(secondary, "tiles.png"))
        val primaryMeta = metatiles(File(primary, "metatiles.bin"))
        val secondaryMeta = metatiles(File(hoenn, "secret-base/secondary/metatiles.bin"))
        val cells = ByteBuffer.wrap(File(hoenn, "secret-base/layouts/$layout/map.bin").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val out = BufferedImage(width * TILE, height * TILE, BufferedImage.TYPE_INT_ARGB)
        for (n in 0 until width * height) {
            val id = cells.short.toInt() and 0x3FF
            val meta = if (id < 512) primaryMeta[id] else secondaryMeta[id - 512]
            for (layer in 0..1) for (quarter in 0..3) {
                val entry = meta[layer * 4 + quarter]
                val tile = entry and 0x3FF
                val flipX = entry shr 10 and 1 == 1
                val flipY = entry shr 11 and 1 == 1
                val colours = palettes[entry shr 12]
                val (sheet, sheetWidth, index) = if (tile < 512) Triple(primaryTiles, primaryWidth, tile) else Triple(secondaryTiles, secondaryWidth, tile - 512)
                val sx = index % (sheetWidth / 8) * 8
                val sy = index / (sheetWidth / 8) * 8
                for (y in 0 until 8) for (x in 0 until 8) {
                    val colour = sheet[sy + (if (flipY) 7 - y else y)][sx + (if (flipX) 7 - x else x)]
                    if (layer == 1 && colour == 0) continue
                    out.setRGB(n % width * TILE + quarter % 2 * 8 + x, n / width * TILE + quarter / 2 * 8 + y, colours[colour])
                }
            }
        }
        return out
    }

    private fun decoration(file: String): BufferedImage {
        val img = ImageIO.read(File(hoenn, "decorations/$file.png"))
        val clear = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if (img.raster.getSample(x, y, 0) != 0) clear.setRGB(x, y, img.getRGB(x, y))
        }
        return clear
    }

    private fun pokemon(formId: Int): BufferedImage {
        val row = File(pmd, "pmd.tsv").readLines().map { it.split('\t') }.first { it[0] == formId.toString() }
        return ImageIO.read(File(pmd, "$formId.png")).getSubimage(0, 0, row[2].toInt(), row[3].toInt())
    }

    fun room(floor: BufferedImage, decorations: List<Placed>, mons: List<Mon>): BufferedImage {
        val out = BufferedImage(floor.width, floor.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(floor, 0, 0, null)
        val (flat, upright) = decorations.partition { it.file.endsWith("_mat") }
        flat.forEach { g.drawImage(decoration(it.file), it.centre - 12, it.bottom - 24, null) }
        val things = upright.map { Triple(it.bottom, decoration(it.file), it.centre) } + mons.map { Triple(it.bottom, pokemon(it.formId), it.centre) }
        things.sortedBy { it.first }.forEach { (bottom, image, centre) -> g.drawImage(image, centre - image.width / 2, bottom - image.height, null) }
        g.dispose()
        return out
    }
}
