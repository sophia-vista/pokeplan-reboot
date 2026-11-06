package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.MemoryStorage
import app.pokeplan.ui.screens.CareSection
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.ui.screens.WorldSection
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Back chains inside the modules, driven by real taps on the real Back button (the owner's open item): from a section Back
 * goes to its menu, from the menu Back goes to Modules, and nothing in between is skipped or loops. The screens are rendered in
 * an offscreen scene and the header's Back button (at the top left) is pressed with pointer events.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class BackChainTest {
    private val clock = object : Clock { override fun nowMillis() = 1_760_000_000_000L }
    private fun model() = AppModel(GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25); it.markRewardsSeen() })

    /** The screen in an offscreen scene; [block] drives it. */
    private fun scene(content: @Composable () -> Unit, block: (Scene) -> Unit) {
        val scene = ImageComposeScene(width = 540, height = 1080, density = Density(1.5f, 1f)) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { content() } }
        }
        try { block(Scene(scene)) } finally { scene.close() }
    }

    private class Scene(val scene: ImageComposeScene) {
        private var nanos = 0L
        fun settle(frames: Int = 40) = repeat(frames) { scene.render(nanos); nanos += 50_000_000; Thread.sleep(8) }
        /** Presses the Back button of the header: top left of the screen. */
        fun back() {
            val at = Offset(90f, 58f)
            scene.sendPointerEvent(PointerEventType.Press, at); settle(2)
            scene.sendPointerEvent(PointerEventType.Release, at); settle()
        }
        /** The header strip as pixels, to tell one screen from another without reading its text. */
        fun header(): List<Int> {
            val image = org.jetbrains.skia.Image.makeFromEncoded(scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
            return (0 until 120).flatMap { y -> (0 until 540 step 6).map { x -> bitmap.getColor(x, y) } }
        }
    }

    @Test
    fun planSectionsGoBackToThePlanMenuThenToModules() {
        for (section in PlanSection.entries.filter { it != PlanSection.PLANNER && it != PlanSection.FOCUS }) {
            val m = model().also { it.module = Module.PLAN; it.planSection = section }
            scene({ app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }) { s ->
                s.settle()
                s.back()
                assertNull(m.planSection, "Back from Plan's ${section.label} lands on Plan's menu")
                assertEquals(Module.PLAN, m.module)
                s.back()
                assertNull(m.module, "and Back from Plan's menu lands on Modules")
            }
        }
    }

    @Test
    fun theFocusScreenGoesBackToThePlanMenu() {
        val m = model().also { it.module = Module.PLAN; it.planSection = PlanSection.FOCUS }
        scene({ app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }) { s -> s.settle(); s.back(); assertNull(m.planSection); assertEquals(Module.PLAN, m.module) }
    }

    @Test
    fun aPlannerViewGoesBackToThePlannerMenuThenToPlan() {
        // "Add task" opens the Planner on Today (a view); Back steps out of the view, then out of the Planner.
        val m = model().also { it.module = Module.PLAN; it.planSection = PlanSection.PLANNER; it.addTaskRequested = true }
        scene({ app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }) { s ->
            s.settle()
            s.back()
            assertEquals(PlanSection.PLANNER, m.planSection, "the first Back closes the view and shows the Planner's menu")
            s.back()
            assertNull(m.planSection, "the second Back leaves the Planner for Plan's menu")
            assertEquals(Module.PLAN, m.module)
        }
    }

    @Test
    fun careSectionsGoBackToTheCareMenuThenToModules() {
        for (section in CareSection.entries) {
            val m = model().also { it.module = Module.CARE; it.careSection = section }
            scene({ app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }) { s ->
                s.settle()
                s.back()
                assertNull(m.careSection, "Back from Care's ${section.label} lands on Care's menu")
                assertEquals(Module.CARE, m.module)
                s.back()
                assertNull(m.module, "and Back from Care's menu lands on Modules")
            }
        }
    }

    @Test
    fun worldSectionsGoBackToTheWorldMenuThenToModules() {
        for (section in WorldSection.entries) {
            val m = model().also { it.module = Module.WORLD; it.worldSection = section }
            scene({ app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }) { s ->
                s.settle()
                s.back()
                assertNull(m.worldSection, "Back from World's ${section.label} lands on World's menu")
                assertEquals(Module.WORLD, m.module)
                s.back()
                assertNull(m.module, "and Back from World's menu lands on Modules")
            }
        }
    }

    @Test
    fun pokemonSectionsGoBackToThePokemonMenu() {
        val menu = model().let { m -> var h: List<Int> = emptyList(); scene({ app.pokeplan.ui.screens.CollectionScreen(m, startSection = -1) }) { s -> s.settle(); h = s.header() }; h }
        for (i in 0..3) {
            val m = model()
            scene({ app.pokeplan.ui.screens.CollectionScreen(m, startSection = i) }) { s ->
                s.settle()
                val inside = s.header()
                assertTrue(inside != menu, "section $i shows its own header")
                s.back()
                assertEquals(menu, s.header(), "Back from Pokémon section $i shows the Pokémon menu")
            }
        }
    }

    @Test
    fun meSectionsGoBackToTheMeMenu() {
        val menu = model().let { m -> var h: List<Int> = emptyList(); scene({ app.pokeplan.ui.screens.MeScreen(m, startSection = -1) }) { s -> s.settle(); h = s.header() }; h }
        for (i in 0..1) {
            val m = model()
            scene({ app.pokeplan.ui.screens.MeScreen(m, startSection = i) }) { s ->
                s.settle()
                assertTrue(s.header() != menu, "section $i shows its own header")
                s.back()
                assertEquals(menu, s.header(), "Back from Me section $i shows the Me menu")
            }
        }
    }
}
