package app.pokeplan.ui

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PmdAssetsTest {
    private val pmd = listOf("../assets/pmd", "assets/pmd").map(::File).first { it.isDirectory }

    private fun rows(file: File) = file.readLines().filter { it.isNotBlank() }.map { it.split('\t') }

    private fun size(file: File): Pair<Int, Int> {
        val image = ImageIO.read(file)
        return image.width to image.height
    }

    @Test
    fun everySheetMatchesItsRow() {
        val tables = mapOf(
            "" to "pmd", "sleep" to "sleep", "eat" to "eat", "hop" to "hop", "walk-down" to "walk-down", "walk-side" to "walk-side", "walk-up" to "walk-up",
            "idle-side" to "idle-side", "idle-up" to "idle-up", "rotate" to "rotate", "wake" to "wake", "deep-breath" to "deep-breath", "pose" to "pose",
        )
        for (a in SpriteAction.entries) a.folder?.let { assertTrue(it in tables.keys, "${a.name} reads $it, which this test does not know") }
        for ((folder, name) in tables) {
            val dir = File(pmd, folder)
            val table = File(dir, "$name.tsv")
            assertTrue(table.isFile, "$folder needs its table")
            for (r in rows(table)) {
                val (id, frames, w, h) = r.map { it.toIntOrNull() ?: 0 }
                val sheet = File(dir, "$id.png")
                assertTrue(sheet.isFile, "$folder/$id.png is listed")
                assertEquals(frames * w to h, size(sheet), "$folder/$id.png against its row")
                assertEquals(frames, r[4].split(',').size, "$folder/$id has a time for each frame")
                if (r[5] == "1") {
                    val shiny = File(dir, "$id-shiny.png")
                    assertTrue(shiny.isFile, "$folder/$id is listed with a shiny")
                    assertEquals(frames * w to h, size(shiny), "$folder/$id-shiny.png against its row")
                }
            }
        }
    }

    @Test
    fun everyPortraitStripHoldsItsFaces() {
        val known = Emotion.entries.map { it.file }.toSet()
        for (r in rows(File(pmd, "portraits/portraits.tsv"))) {
            val id = r[0]
            val faces = r[1].split(',')
            assertEquals("Normal", faces.first(), "$id starts with its Normal face")
            assertTrue(faces.all { it in known }, "$id lists a face the app does not know: $faces")
            assertEquals(40 * faces.size to 40, size(File(pmd, "portraits/$id.png")), "portrait $id against its faces")
            if (r[2] == "1") {
                val shinyFaces = r.getOrNull(4)?.takeIf { it.isNotBlank() }?.split(',') ?: faces
                assertEquals(40 * shinyFaces.size to 40, size(File(pmd, "portraits/$id-shiny.png")), "portrait $id-shiny against its faces")
            }
        }
    }

    @Test
    fun aDerivedShinyIsListedAndHasItsFiles() {
        val listed = File(pmd, "derived-shiny.tsv")
        if (!listed.isFile) return
        for (r in rows(listed)) for (folder in r[1].split(',')) {
            val dir = if (folder == "idle") pmd else File(pmd, folder)
            assertTrue(File(dir, "${r[0]}-shiny.png").isFile, "derived shiny ${r[0]} in $folder")
        }
    }
}
