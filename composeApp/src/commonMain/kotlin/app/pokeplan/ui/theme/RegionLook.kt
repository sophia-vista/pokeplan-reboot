package app.pokeplan.ui.theme

import androidx.compose.ui.graphics.Color
import app.pokeplan.app.BuddyScene

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

    val accent: Color get() = if (Pixel.dark) bright else header
}

enum class RegionLook(val scene: String, private val module: ModuleAccent) {
    PLAN(BuddyScene.PLAN, ModuleAccent.PLAN),

    CARE(BuddyScene.CARE, ModuleAccent.CARE),

    WORLD("forest", ModuleAccent.WORLD),

    HEAL(BuddyScene.HEAL, ModuleAccent.HEAL),

    MINT(BuddyScene.MINT, ModuleAccent.MINT),

    BASE(BuddyScene.BASE, ModuleAccent.BASE),

    LINK(BuddyScene.LINK, ModuleAccent.LINK),
    ;

    val header: Color get() = module.header

    val accent: Color get() = module.accent
}

@androidx.compose.runtime.Composable
fun ModuleTone(accent: ModuleAccent, content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalFrameTone provides frameToneOf(accent.accent), content = content)
