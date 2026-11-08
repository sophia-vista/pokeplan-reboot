package app.pokeplan.ui

import app.pokeplan.world.story.StoryCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every picture a story scene is set on exists in `assets/` and is in the lists `composeApp/build.gradle.kts` bundles (a missing one would draw a plain green box). */
class StoryMapsAssetTest {
    private val root = File("..").canonicalFile
    private val gradle = File(root, "composeApp/build.gradle.kts").readText()

    private fun listed(name: String): Set<String> =
        Regex("val $name = arrayOf\\((.*?)\\)\\.map", RegexOption.DOT_MATCHES_ALL).find(gradle)!!.groupValues[1].let { Regex("\"([A-Z0-9_]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() }

    @Test
    fun everySceneBackgroundIsBundled() {
        val rescue = listed("storyRescueMaps")
        val sky = listed("storySkyMaps")
        val missing = mutableListOf<String>()
        for (scene in StoryCatalog.all().flatMap { it.scenes }) {
            val bg = scene.background
            when {
                scene.dungeonId != null -> {}
                bg.startsWith("map:sky-") -> {
                    val code = bg.removePrefix("map:sky-").uppercase()
                    if (code !in sky || !File(root, "assets/sky-maps/$code.png").exists()) missing += "${scene.id}: $bg"
                }
                bg.startsWith("map:") -> {
                    val id = bg.removePrefix("map:").uppercase().replace('-', '_')
                    if (id !in rescue || !File(root, "assets/rescue-maps/$id.png").exists()) missing += "${scene.id}: $bg"
                }
                bg.startsWith("fa-") -> if (!File(root, "assets/rescue-maps/FRIEND_AREA_${bg.removePrefix("fa-").uppercase().replace('-', '_')}.png").exists()) missing += "${scene.id}: $bg"
                else -> if (!File(root, "assets/scenes/$bg.png").exists()) missing += "${scene.id}: $bg"
            }
        }
        assertEquals(emptyList(), missing)
        assertTrue(rescue.isNotEmpty() && sky.isNotEmpty())
    }
}
