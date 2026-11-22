package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.app.ReactionIcon
import app.pokeplan.ui.theme.Pixel

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

@Composable
fun EmoteIcon(emote: Emote, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    if (emote.item != null) ItemIcon(emote.item, modifier, size, description = null)
    else Glyph(emote.glyph!!, emote.tint, modifier, size)
}

@Composable
fun ReactionMark(icon: ReactionIcon, modifier: Modifier = Modifier, size: Dp = 40.dp, animate: Boolean = true) {
    val kind = icon.status()
    if (kind != null) {
        val pixel = (size.value * androidx.compose.ui.platform.LocalDensity.current.density / kind.cell).toInt().coerceAtLeast(1)
        StatusIcon(kind, pixel, modifier, loop = kind !in PLAYS_ONCE, animate = animate)
    } else EmoteIcon(icon.emote()!!, modifier, size)
}

private val PLAYS_ONCE = setOf(StatusKind.ALERT, StatusKind.QUESTION, StatusKind.TWINKLE)

fun ReactionIcon.status(): StatusKind? = when (this) {
    ReactionIcon.TWINKLE -> StatusKind.TWINKLE; ReactionIcon.ALERT -> StatusKind.ALERT; ReactionIcon.QUESTION -> StatusKind.QUESTION
    ReactionIcon.SLEEP -> StatusKind.SLEEP; ReactionIcon.FLAME -> StatusKind.FLAME; ReactionIcon.ZAP -> StatusKind.ZAP
    ReactionIcon.ICE -> StatusKind.ICE; ReactionIcon.FIST -> StatusKind.FIST; ReactionIcon.SHIELD -> StatusKind.SHIELD; ReactionIcon.DROP -> StatusKind.SWEAT
    else -> null
}

fun ReactionIcon.emote(): Emote? = when (this) {
    ReactionIcon.HEART -> Emote.HEART; ReactionIcon.NOTE -> Emote.MUSIC; ReactionIcon.SUN -> Emote.HAPPY; ReactionIcon.MOON -> Emote.MOON
    ReactionIcon.STAR -> Emote.PARTY; ReactionIcon.APPLE -> Emote.YUM
    else -> null
}
