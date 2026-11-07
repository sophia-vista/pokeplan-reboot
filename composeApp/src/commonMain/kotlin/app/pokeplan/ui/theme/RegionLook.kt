package app.pokeplan.ui.theme

import androidx.compose.ui.graphics.Color
import app.pokeplan.app.BuddyScene

/**
 * One accent for each of the eight modules (docs/pokelife/FEATURES/DESIGN_SYSTEM.md, "Chrome and module accents"). [header] is the
 * bar on the module's pages (the same in both palettes, white text on it, 4.5:1 or better); [accent] tints its frames, menu cards
 * and primary action, and is brighter in the dark palette so a frame stays visible on a dark panel. HEAL, MINT, HOME and LINK are
 * reserved before they are built. An accent never stands for success, warning, error or money.
 */
enum class ModuleAccent(val header: Color, private val bright: Color) {
    PLAN(Color(0xFF6D3FD6), Color(0xFF9B7BF0)),
    CARE(Color(0xFF1E7F52), Color(0xFF34B07A)),
    WORLD(Color(0xFF0F766E), Color(0xFF2DB5A8)),
    GEAR(Color(0xFF2C6CCC), Color(0xFF5B96EE)),
    HEAL(Color(0xFFBE185D), Color(0xFFEC5C9B)),
    MINT(Color(0xFFB45309), Color(0xFFE08A2E)),
    HOME(Color(0xFF4D7C0F), Color(0xFF8DB83A)),
    LINK(Color(0xFF0E7490), Color(0xFF38B2D0)),
    ;

    /** The accent in the palette in use. */
    val accent: Color get() = if (Pixel.dark) bright else header
}

/**
 * A region's own look, the first of the three visual layers (docs/ecosystem.md section 11): the real scene its lead stands
 * in, and its colour. A region's page, its sections' headers and its card on Home and in Modules wear it; the lists, forms
 * and settings inside stay in the plain look, and game moments (hatch, evolution) keep theirs.
 */
enum class RegionLook(val scene: String, private val module: ModuleAccent) {
    /** A town: brick streets and Plan's violet. */
    PLAN(BuddyScene.PLAN, ModuleAccent.PLAN),

    /** A meadow: grass and Care's green. */
    CARE(BuddyScene.CARE, ModuleAccent.CARE),

    /** The World game (docs/world.md): a forest to begin in, and a deep teal. */
    WORLD("forest", ModuleAccent.WORLD),
    ;

    /** The header bar on the region's pages, under white text. */
    val header: Color get() = module.header

    /** The region's accent: its menu card tint and the edge of its card on Home. */
    val accent: Color get() = module.accent
}
