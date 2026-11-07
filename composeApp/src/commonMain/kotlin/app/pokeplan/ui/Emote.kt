package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel

/**
 * A small reaction picture, drawn from real assets (an item icon, or a Pixelarticons glyph tinted in a colour), where the app
 * once used an emoji. [item] is an ItemIcon id; otherwise [glyph] names a Pixelarticons glyph drawn in [tint].
 */
enum class Emote(val item: String? = null, val glyph: String? = null, private val tintOf: () -> androidx.compose.ui.graphics.Color = { Pixel.Pink }) {
    HEART(glyph = "heart"),
    LOVE(glyph = "heart", tintOf = { Pixel.Red }),
    PARTY(item = "star-piece"),
    YUM(item = "sweet-apple"),
    HAPPY(glyph = "sun", tintOf = { Pixel.Amber }),
    CALM(item = "soothe-bell"),
    MUSIC(glyph = "music", tintOf = { Pixel.Blue }),
    SPARK(item = "shiny-stone"),
    ;

    val tint get() = tintOf()
}

/** [emote] at [size]. */
@Composable
fun EmoteIcon(emote: Emote, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    if (emote.item != null) ItemIcon(emote.item, modifier, size, description = null)
    else Glyph(emote.glyph!!, emote.tint, modifier, size)
}
