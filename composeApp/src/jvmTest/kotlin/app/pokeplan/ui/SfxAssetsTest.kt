package app.pokeplan.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SfxAssetsTest {
    @Test
    fun everySfxHasItsFile() {
        val roots = listOf("../assets", "assets").map(::File).first { it.isDirectory }
        val dirs = listOf(File(roots, "sfx"), File(roots, "sky-sfx"))
        for (s in Sfx.entries) {
            assertTrue(dirs.any { File(it, "${s.file}.ogg").let { f -> f.isFile && f.length() > 500 } }, "${s.name} needs ${s.file}.ogg in assets/sfx or assets/sky-sfx")
        }
    }
}
