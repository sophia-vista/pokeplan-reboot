package app.pokeplan.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every sound effect in the enum has its file, and none is empty (a missing one would be silent in the app). */
class SfxAssetsTest {
    @Test
    fun everySfxHasItsFile() {
        val dir = listOf("../assets/sfx", "assets/sfx").map(::File).first { it.isDirectory }
        for (s in Sfx.entries) {
            val f = File(dir, "${s.file}.ogg")
            assertTrue(f.isFile && f.length() > 500, "${s.name} needs assets/sfx/${s.file}.ogg")
        }
    }
}
