package app.pokeplan.ui.theme

import androidx.compose.ui.graphics.Color
import app.pokeplan.app.BuddyScene

/**
 * A region's own look, the first of the three visual layers (docs/ecosystem.md section 11): the real scene its lead stands
 * in, and its colour. A region's page, its sections' headers and its card on Home and in Modules wear it; the lists, forms
 * and settings inside stay in the plain look, and game moments (hatch, evolution) keep theirs.
 */
enum class RegionLook(val scene: String) {
    /** A town: brick streets and Plan's red. */
    PLAN(BuddyScene.PLAN),

    /** A meadow: grass and Care's green. */
    CARE(BuddyScene.CARE),

    /** The World game (docs/world.md): a forest to begin in, and a deep teal. */
    WORLD("forest"),

    /** The Heal module (docs/pokelife MODULES/HEAL.md): an open route, and a clear blue. A utility surface: the scene is only a quiet backdrop. */
    HEAL(BuddyScene.HEAL),
    ;

    /** The header bar on the region's pages, under white text. */
    val header: Color get() = when (this) {
        PLAN -> Pixel.Red
        CARE -> Pixel.Meadow
        WORLD -> Pixel.Teal
        HEAL -> Pixel.Blue
    }

    /** The region's accent: its menu card tint and the edge of its card on Home. */
    val accent: Color get() = when (this) {
        PLAN -> Pixel.Red
        CARE -> Pixel.Green
        WORLD -> Pixel.Teal
        HEAL -> Pixel.Blue
    }
}
