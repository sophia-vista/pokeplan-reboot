package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.app.ReactionIcon
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
    MOON(glyph = "moon", tintOf = { Pixel.Blue }),
    ;

    val tint get() = tintOf()
}

/** [emote] at [size]. */
@Composable
fun EmoteIcon(emote: Emote, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    if (emote.item != null) ItemIcon(emote.item, modifier, size, description = null)
    else Glyph(emote.glyph!!, emote.tint, modifier, size)
}

/**
 * A [ReactionIcon] at [size]: the Mystery Dungeon status icon where the games have one (looping for the flame, the ice and the like,
 * once for the "!", the "?" and the sparkle), else a real item icon or glyph. [animate] off shows one still frame (Reduce motion).
 */
@Composable
fun ReactionMark(icon: ReactionIcon, modifier: Modifier = Modifier, size: Dp = 40.dp, animate: Boolean = true) {
    val kind = icon.status()
    if (kind != null) {
        val pixel = (size.value * androidx.compose.ui.platform.LocalDensity.current.density / kind.cell).toInt().coerceAtLeast(1)
        StatusIcon(kind, pixel, modifier, loop = kind !in PLAYS_ONCE, animate = animate)
    } else EmoteIcon(icon.emote()!!, modifier, size)
}

private val PLAYS_ONCE = setOf(StatusKind.ALERT, StatusKind.QUESTION, StatusKind.TWINKLE)

/** The status icon a reaction is drawn with, or null for one drawn from an item icon or a glyph. */
fun ReactionIcon.status(): StatusKind? = when (this) {
    ReactionIcon.TWINKLE -> StatusKind.TWINKLE; ReactionIcon.ALERT -> StatusKind.ALERT; ReactionIcon.QUESTION -> StatusKind.QUESTION
    ReactionIcon.SLEEP -> StatusKind.SLEEP; ReactionIcon.FLAME -> StatusKind.FLAME; ReactionIcon.ZAP -> StatusKind.ZAP
    ReactionIcon.ICE -> StatusKind.ICE; ReactionIcon.FIST -> StatusKind.FIST; ReactionIcon.SHIELD -> StatusKind.SHIELD; ReactionIcon.DROP -> StatusKind.SWEAT
    else -> null
}

/** The item icon or glyph a reaction without a status icon is drawn with. */
fun ReactionIcon.emote(): Emote? = when (this) {
    ReactionIcon.HEART -> Emote.HEART; ReactionIcon.NOTE -> Emote.MUSIC; ReactionIcon.SUN -> Emote.HAPPY; ReactionIcon.MOON -> Emote.MOON
    ReactionIcon.STAR -> Emote.PARTY; ReactionIcon.APPLE -> Emote.YUM
    else -> null
}
