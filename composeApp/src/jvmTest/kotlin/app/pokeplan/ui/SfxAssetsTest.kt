package app.pokeplan.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every sound effect in the enum has its file, and none is empty (a missing one would be silent in the app). */
class SfxAssetsTest {
    @Test
    fun everySfxHasItsFile() {
        // The build bundles assets/sfx and the Explorers of Sky jingles (assets/sky-sfx) together, so a sound may be in either.
        val roots = listOf("../assets", "assets").map(::File).first { it.isDirectory }
        val dirs = listOf(File(roots, "sfx"), File(roots, "sky-sfx"))
        for (s in Sfx.entries) {
            assertTrue(dirs.any { File(it, "${s.file}.ogg").let { f -> f.isFile && f.length() > 500 } }, "${s.name} needs ${s.file}.ogg in assets/sfx or assets/sky-sfx")
        }
    }
}
