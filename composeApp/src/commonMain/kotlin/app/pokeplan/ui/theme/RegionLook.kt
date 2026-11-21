package app.pokeplan.ui.theme

import androidx.compose.ui.graphics.Color
import app.pokeplan.app.BuddyScene

/**
 * One accent for each of the eight modules, in the order of the Modules menu: Gear first, World last, and the six between
 * running down a quiet rainbow (docs/pokelife/FEATURES/DESIGN_SYSTEM.md, "Chrome and module accents"). [header] is the bar on the
 * module's pages (the same in both palettes, white text on it, 4.5:1 or better); [accent] tints its frames, menu card and primary
 * action, and is brighter in the dark palette so a frame stays visible on a dark panel. An accent never stands for success,
 * warning, error or money.
 */
enum class ModuleAccent(val header: Color, private val bright: Color) {
    GEAR(Color(0xFFB83A4B), Color(0xFFF0707F)),
    PLAN(Color(0xFFB35309), Color(0xFFEE9040)),
    MINT(Color(0xFF8A6700), Color(0xFFD4A82A)),
    BASE(Color(0xFF4D7C0F), Color(0xFF8DB83A)),
    CARE(Color(0xFF1E7F52), Color(0xFF34B07A)),
    HEAL(Color(0xFF0F766E), Color(0xFF2DB5A8)),
    LINK(Color(0xFF2C6CCC), Color(0xFF5B96EE)),
    WORLD(Color(0xFF6D3FD6), Color(0xFF9B7BF0)),
    ;

    /** The accent in the palette in use. */
    val accent: Color get() = if (Pixel.dark) bright else header
}

/**
 * A region's own look, the first of the three visual layers (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md): the real scene its lead stands
 * in, and its colour. A region's page, its sections' headers and its card on Home and in Modules wear it; the lists, forms
 * and settings inside stay in the plain look, and game moments (hatch, evolution) keep theirs.
 */
enum class RegionLook(val scene: String, private val module: ModuleAccent) {
    /** A town: brick streets and Plan's orange. */
    PLAN(BuddyScene.PLAN, ModuleAccent.PLAN),

    /** A meadow: grass and Care's green. */
    CARE(BuddyScene.CARE, ModuleAccent.CARE),

    /** The World game (docs/pokelife/MODULES/WORLD.md section 14): a forest to begin in, and violet at the end of the rainbow. */
    WORLD("forest", ModuleAccent.WORLD),

    /** The Heal module (docs/pokelife MODULES/HEAL.md): an open route, and teal. A utility surface: the scene is only a quiet backdrop. */
    HEAL(BuddyScene.HEAL, ModuleAccent.HEAL),

    /** The Mint module (docs/pokelife MODULES/MINT.md): a quiet cave of ore, and ochre. A utility surface: the scene is only a backdrop and never sits behind a figure. */
    MINT(BuddyScene.MINT, ModuleAccent.MINT),

    /** The Base module (docs/pokelife MODULES/BASE.md): the town, and olive. A utility surface: the scene is only a quiet backdrop. */
    BASE(BuddyScene.BASE, ModuleAccent.BASE),

    /** The Link module (docs/pokelife MODULES/LINK.md): a route for the postcard trail, and blue. A utility surface: the scene is only a quiet backdrop. */
    LINK(BuddyScene.LINK, ModuleAccent.LINK),
    ;

    /** The header bar on the region's pages, under white text. */
    val header: Color get() = module.header

    /** The region's accent: its menu card tint and the edge of its card on Home. */
    val accent: Color get() = module.accent
}

/** Gives everything inside [content] the module's tone: dialogue frames, keys and chips read it, so a screen never names its own colour. */
@androidx.compose.runtime.Composable
fun ModuleTone(accent: ModuleAccent, content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(accent.accent), content = content)
