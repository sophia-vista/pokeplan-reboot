package app.pokeplan.ui

import app.pokeplan.ui.world.SkyBarks
import app.pokeplan.ui.world.SkyJobs
import app.pokeplan.ui.world.dungeonEntrance
import app.pokeplan.world.Dungeons
import app.pokeplan.world.Missions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkyVoiceTest {
    private val root = File("..").canonicalFile
    private val gradle = File(root, "composeApp/build.gradle.kts").readText()

    private val game: Set<String> by lazy {
        File(root, "rip/sky/text/strings.tsv").readLines().drop(1).mapNotNull { it.split('\t').getOrNull(2) }
            .map { it.replace("\\n", " ").replace(Regex("\\s+"), " ").trim() }.toSet()
    }

    private fun skipWithoutRip() = File(root, "rip/sky/text/strings.tsv").exists()

    @Test
    fun barksAreTheGamesLinesAndAPokemonAlwaysSaysTheSame() {
        assertTrue(SkyBarks.HEALTHY.size > 100 && SkyBarks.LEVEL_UP.size > 50)
        for (id in listOf(1, 25, 133, 150, 1010)) {
            assertEquals(SkyBarks.healthy(id), SkyBarks.healthy(id))
            assertTrue(SkyBarks.healthy(id) in SkyBarks.HEALTHY)
            assertTrue(SkyBarks.levelUp(id) in SkyBarks.LEVEL_UP)
        }
        assertTrue((1..40).map { SkyBarks.healthy(it) }.toSet().size > 10)
        if (skipWithoutRip()) assertEquals(emptyList(), (SkyBarks.HEALTHY + SkyBarks.LEVEL_UP).filter { it !in game })
    }

    @Test
    fun helpCallsAreTheGamesAndStablePerJob() {
        if (skipWithoutRip()) assertEquals(emptyList(), SkyJobs.HELP_CALLS.filter { it !in game })
        val rescues = Missions.all.filter { it.rescued != null }
        assertTrue(rescues.isNotEmpty())
        rescues.forEach { assertEquals(SkyJobs.helpCall(it), SkyJobs.helpCall(it)); assertTrue(SkyJobs.helpCall(it) in SkyJobs.HELP_CALLS) }
        assertTrue(rescues.map { SkyJobs.helpCall(it) }.toSet().size > 1)
    }

    @Test
    fun everyEntrancePictureIsBundledAndExists() {
        val listedRescue = Regex("\"([A-Z0-9_]+)\"").findAll(Regex("val storyRescueMaps = arrayOf\\((.*?)\\)\\.map", RegexOption.DOT_MATCHES_ALL).find(gradle)!!.groupValues[1]).map { it.groupValues[1] }.toSet()
        val listedSky = Regex("\"([A-Z0-9_]+)\"").findAll(Regex("val storySkyMaps = arrayOf\\((.*?)\\)\\.map", RegexOption.DOT_MATCHES_ALL).find(gradle)!!.groupValues[1]).map { it.groupValues[1] }.toSet()
        val shown = Dungeons.all.mapNotNull { d -> dungeonEntrance(d.id)?.let { d.id to it } }
        assertTrue(shown.size >= 6)
        for ((id, map) in shown) {
            val sky = Regex("map:sky-[a-z][0-9]{2}p[0-9]{2}[a-z]?").matches(map)
            val code = (if (sky) map.removePrefix("map:sky-") else map.removePrefix("map:")).uppercase().replace('-', '_')
            assertTrue(code in (if (sky) listedSky else listedRescue), "$id: $map is not bundled")
            assertTrue(File(root, "assets/${if (sky) "sky-maps" else "rescue-maps"}/$code.png").exists(), "$id: $map is missing")
        }
        assertNotNull(dungeonEntrance("tiny-woods"))
    }
}
