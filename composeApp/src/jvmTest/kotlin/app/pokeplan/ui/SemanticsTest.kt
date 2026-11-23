package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The semantics/accessibility gate (docs/pokelife/ARCHITECTURE/TEST_STRATEGY.md 13): every interactive control on every
 * main screen must carry a label a screen reader can announce, and an icon-only control (a label with no visible text)
 * must be at least the 48 dp touch target. Screens are rendered at the normal scale and at fontScale 1.3, so the large
 * text case is walked too.
 *
 * The walk reads the Compose semantics tree of the whole app (`ImageComposeScene.semanticsOwners`), not pixels: it can
 * prove a label and a touch target exist; it cannot prove TalkBack's spoken order or that a custom control is pleasant
 * to use. Those stay in the on-device checklist (`tools/ui-check/device-accessibility.md`).
 */
private fun SemanticsNode.semanticsTree(): List<SemanticsNode> = listOf(this) + children.flatMap { it.semanticsTree() }

@OptIn(ExperimentalComposeUiApi::class)
class SemanticsTest {
    private val clock = object : Clock { override fun nowMillis() = 1_760_000_000_000L }
    private val density = 1.5f

    private fun storage(): MemoryStorage {
        val store = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25); it.markRewardsSeen() }
        return MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
    }

    private class ComposeSceneRoot(val scene: ImageComposeScene, val density: Float, val fontScale: Float) {
        val nodes: List<SemanticsNode> = scene.semanticsOwners.flatMap { owner -> owner.rootSemanticsNode.semanticsTree() }
    }

    private val SemanticsNode.isInteractive: Boolean
        get() = config.contains(SemanticsActions.OnClick) ||
            config.contains(SemanticsProperties.Role) ||
            config.contains(SemanticsActions.SetProgress)

    /** What a screen reader would get for this control: its own or a nearby descendant's words. */
    private fun SemanticsNode.label(depth: Int = 0): String {
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val own = (description + text).joinToString(" ").trim()
        if (own.isNotEmpty() || depth >= 3) return own
        return children.joinToString(" ") { it.label(depth + 1) }.trim()
    }

    private fun places() = listOf(
        Place.HOME, Place.PLAN, Place.CARE, Place.HEAL, Place.MINT, Place.BASE, Place.LINK, Place.WORLD, Place.GEAR, Place.ME, Place.COLLECTION,
    )

    @Test
    fun everyMainScreenHasLabelledControlsAtNormalAndLargeText() {
        // Rendering one place per scene keeps the walk honest (a screen that was never drawn has no semantics).
        for (fontScale in listOf(1f, 1.3f)) {
            for (place in places()) {
                val problems = renderPlace(place, fontScale)
                if (problems.isNotEmpty()) {
                    fail("screen-reader labels missing at fontScale $fontScale on $place (${problems.size}):\n" + problems.take(12).joinToString("\n"))
                }
            }
        }
    }

    @Test
    fun iconOnlyControlsAreAtLeastTheTouchTarget() {
        val minimum = Spacing.Target.value * density // 48 dp in px at the test density
        for (place in places()) {
            val small = mutableListOf<String>()
            sceneFor(place, 1f) { root ->
                root.nodes.filter { it.isInteractive }.forEach { node ->
                    val label = node.label()
                    val hasVisibleText = node.config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty()
                    // Only controls that are actually laid out on the screen: a merged child or a popup node with no
                    // size is not a touch target by itself, and a progress bar (SetProgress only) is not one either.
                    val clickable = node.config.contains(SemanticsActions.OnClick)
                    val bounds = node.touchBoundsInRoot
                    if (clickable && !hasVisibleText && label.isNotEmpty() && bounds.width > 1f && bounds.height > 1f) {
                        val side = minOf(bounds.width, bounds.height)
                        if (side + 1f < minimum) small += "$label is ${(side / density).roundToInt()} dp"
                    }
                }
            }
            if (small.isNotEmpty()) fail("icon-only controls below 48 dp on $place:\n" + small.take(12).joinToString("\n"))
        }
    }

    private fun renderPlace(place: Place, fontScale: Float): List<String> {
        val problems = mutableListOf<String>()
        sceneFor(place, fontScale) { root ->
            val interactive = root.nodes.filter { it.isInteractive }
            if (interactive.isEmpty()) problems += "$place rendered no interactive control at all"
            interactive.forEach { node ->
                if (node.label().isBlank()) problems += "$place: a ${node.config.getOrNull(SemanticsProperties.Role) ?: "control"} has no text, content description or labelled child"
            }
        }
        return problems
    }

    /** Renders one place directly (the app's own start route), so `/` and module screens are the real screens. */
    private fun sceneFor(place: Place, fontScale: Float, block: (ComposeSceneRoot) -> Unit) {
        val store = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25); it.markRewardsSeen() }
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        val scene = ImageComposeScene(width = 540, height = 1080, density = Density(density, fontScale)) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { App(storage, focusTab = true, start = place) } }
        }
        try {
            var nanos = 0L
            repeat(60) { scene.render(nanos); nanos += 50_000_000 }
            block(ComposeSceneRoot(scene, density, fontScale))
        } finally {
            scene.close()
        }
    }
}
