package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.pokeplan.ui.screens.HomeLive
import app.pokeplan.ui.screens.HomeLiveAction
import app.pokeplan.ui.screens.HomeLiveCard
import app.pokeplan.ui.screens.HomeOpportunities
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/** Home's time-sensitive strip: the selection rules and the rendered states that matter. */
@OptIn(ExperimentalComposeUiApi::class)
class HomeTest {
    private fun render(name: String, fontScale: Float, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 540, height = 620, density = Density(1.5f, fontScale)) { content() }
        repeat(20) { scene.render(it * 50_000_000L) }
        val bytes = scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)
        scene.close()
    }

    private val several = HomeLive(
        reportFloors = 3,
        floorDue = true,
        floorDueWhere = "Tiny Woods",
        jobsReady = 2,
        finds = 1,
        outbreaks = 1,
        dreamers = 2,
        safariVisits = 1,
        companionCandies = 3,
        companionTitle = "Book club",
    )

    @Test
    fun nothingIsLiveMeansNothingIsShown() {
        assertEquals(emptyList(), HomeOpportunities.select(HomeLive()))
        render("home-live-empty", 1f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { HomeLiveCard(HomeOpportunities.select(HomeLive())) {} } } }
    }

    @Test
    fun liveThingsAreOneLineEachAndTheMostUrgentComeFirst() {
        val lines = HomeOpportunities.select(several)
        assertEquals(HomeOpportunities.SHOWN, lines.size, "the strip stays a strip")
        assertEquals(listOf(5, 5, 4, 3), lines.map { it.urgency }, "most urgent first")
        assertEquals(HomeLiveAction.WORLD_REPORT, lines.first().kind)
        assertTrue(lines.none { it.text.isBlank() || it.action.isBlank() })
        assertTrue(lines.map { it.id }.toSet().size == lines.size, "one line per thing")
        val only = HomeOpportunities.select(HomeLive(companionCandies = 2, companionTitle = "Book club"))
        assertEquals("Book club: 2 candies waiting", only.single().text)
        assertEquals("1 job ready to report", HomeOpportunities.select(HomeLive(jobsReady = 1)).single().text)
    }

    @Test
    fun theStripRendersLiveAtNormalAndLargeText() {
        val lines = HomeOpportunities.select(several)
        render("home-live", 1f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { HomeLiveCard(lines) {} } } }
        render("home-live-xl", 1.3f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { HomeLiveCard(lines) {} } } }
        assertTrue(File("build/screenshots/home-live.png").length() > 1_000, "the strip drew")
        assertTrue(File("build/screenshots/home-live-xl.png").length() > 1_000, "the strip drew at fontScale 1.3")
    }
}
