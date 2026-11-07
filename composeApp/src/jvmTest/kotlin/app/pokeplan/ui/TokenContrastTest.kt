package app.pokeplan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.pokeplan.ui.theme.KeyState
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.keyLook
import kotlin.test.Test
import kotlin.test.assertTrue

/** The contrast the design system promises (WCAG AA for text), checked on the tokens themselves in both palettes. */
class TokenContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    private fun both(check: (dark: Boolean) -> Unit) { for (d in listOf(false, true)) { Pixel.dark = d; try { check(d) } finally { Pixel.dark = false } } }

    @Test
    fun moduleHeadersCarryWhiteText() {
        ModuleAccent.entries.forEach { assertTrue(ratio(Color.White, it.header) >= 4.5f, "${it.name} header ${ratio(Color.White, it.header)}") }
        assertTrue(ratio(Color.White, Pixel.Chrome) >= 4.5f)
    }

    @Test
    fun lettersOnFilledKeysReadInBothPalettes() = both { dark ->
        listOf(Pixel.Blue, Pixel.Green, Pixel.Red, Pixel.Warning, Pixel.Danger, Pixel.Yellow, Pixel.Teal, Pixel.Violet, Pixel.Amber).forEach { key ->
            val look = keyLook(key, KeyState.Pressed)
            assertTrue(ratio(look.ink, look.face) >= 4.5f, "key ${key.value} (dark=$dark): ${ratio(look.ink, look.face)}")
        }
    }

    @Test
    fun textTokensReadOnTheirPanels() = both { dark ->
        listOf(Pixel.Ink, Pixel.InkSoft).forEach { assertTrue(ratio(it, Pixel.Panel) >= 4.5f && ratio(it, Pixel.Paper) >= 4.5f, "dark=$dark") }
    }

    @Test
    fun accentsStayVisibleOnTheirPanel() = both { dark ->
        ModuleAccent.entries.forEach { assertTrue(ratio(it.accent, Pixel.Panel) >= 2.4f, "${it.name} dark=$dark ${ratio(it.accent, Pixel.Panel)}") }
    }
}
