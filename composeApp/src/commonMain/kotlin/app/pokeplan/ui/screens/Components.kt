package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.pokeplan.ui.rememberInForeground
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.describe
import app.pokeplan.ui.theme.GameBox
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate

/** The newest rewards nobody has looked at yet, with an OK button. Shows nothing when there are none. Home is the only place that draws it: a module page never shows another module's payout (a World Pokémon in Care). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RewardBox(model: AppModel) {
    val fresh = model.data.rewardLog.filter { !it.seen }.takeLast(3)
    if (fresh.isEmpty()) return
    GameBox(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            fresh.forEach { entry ->
                Text(entry.source.removePrefix("Task: "), style = MaterialTheme.typography.titleMedium)
                // A new Pokémon keeps its own row; everything else runs together as small icon-and-words pieces, so a payout is two lines, not a list.
                entry.rewards.filterIsInstance<Reward.Hatched>().forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        AnimatedSprite(r.pokemon.form.id, r.pokemon.isShiny, r.pokemon.nickname, size = Sizes.SpriteM, animate = !model.data.settings.reduceMotion, ground = 4.dp)
                        Text(r.describe(entry.source), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    entry.rewards.filter { it !is Reward.Hatched }.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            r.iconItem()?.let { ItemIcon(it, size = 24.dp) }
                            Text(r.describe(entry.source), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            PixelButton("OK", { model.store.markRewardsSeen() }, Modifier.padding(top = Spacing.S), Pixel.Green)
        }
    }
}

/**
 * A text box with the buddy's portrait at its start, the way the Mystery Dungeon games put a face beside a line of text.
 * The face only listens and cheers: the words are the app's, never the buddy talking. Without a buddy it is a plain text box.
 */
@Composable
fun SpeakerBox(model: AppModel, emotion: Emotion, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val buddy = model.data.player.buddy
    TextBox(modifier.fillMaxWidth()) {
        if (buddy == null) content()
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Portrait(buddy.form.id, buddy.isShiny, buddy.nickname, emotion = emotion, size = 64.dp)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** One thing just deleted that the undo bar can bring back: its [label] ("Deleted: Rent") and what [undo] does. */
class UndoItem(val label: String, val undo: () -> Unit)

/**
 * Tasks just ticked off, still inside the 10 second window where Undo cancels the reward, and a task just deleted. Shown in
 * one bar pinned above the tabs, near the thumb that tapped Done, on every tab; it floats over the page, so nothing on the
 * page moves when it comes and goes.
 */
@Composable
fun UndoBar(model: AppModel, modifier: Modifier = Modifier) {
    val deleted = model.deletedTask
    val other = model.undoItem
    var otherAt by remember { mutableLongStateOf(model.store.nowMillis) }
    LaunchedEffect(other) {
        if (other != null) {
            otherAt = model.store.nowMillis
            delay(UNDO_SHOWN_MILLIS)
            if (model.undoItem === other) model.undoItem = null
        }
    }
    // A deleted task can be brought back for as long as a finished one can be undone.
    var deletedAt by remember { mutableLongStateOf(model.store.nowMillis) }
    LaunchedEffect(deleted?.id) {
        if (deleted != null) {
            deletedAt = model.store.nowMillis
            delay(UNDO_SHOWN_MILLIS)
            model.deletedTask = null
        }
    }
    // How much of the time left to undo is left, for the bar under each row; it follows the clock ten times a second, and only while something can be undone.
    var now by remember { mutableLongStateOf(model.store.nowMillis) }
    val foreground = rememberInForeground()
    val anyRows = model.data.pending.isNotEmpty() || deleted != null || other != null
    LaunchedEffect(anyRows, foreground) { while (anyRows && foreground) { now = model.store.nowMillis; delay(UNDO_TICK_MILLIS) } }
    fun left(untilMillis: Long) = ((untilMillis - now).toFloat() / UNDO_SHOWN_MILLIS).coerceIn(0f, 1f)
    val rows: List<Triple<String, () -> Unit, Float>> =
        model.data.pending.takeLast(2).map { p -> Triple("Done: ${p.done.title}", { model.store.undoCompletion(p.original.id); Unit }, left(p.finalAtMillis)) } +
            listOfNotNull(deleted?.let { t -> Triple("Deleted: ${t.title}", { model.store.restoreTask(t.id); model.deletedTask = null }, left(deletedAt + UNDO_SHOWN_MILLIS)) }) +
            listOfNotNull(other?.let { o -> Triple(o.label, { o.undo(); model.undoItem = null }, left(otherAt + UNDO_SHOWN_MILLIS)) })
    if (rows.isEmpty()) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S)
            .background(Pixel.Ink, PixelShape)
            .padding(horizontal = Spacing.L, vertical = Spacing.XS)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        rows.forEach { (label, undo, fraction) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Pixel.OnInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PixelIconButton("undo", "Undo", undo, Pixel.Tone)
            }
            // The time left to undo, draining to the right; it carries no words, the row already says what can be undone.
            Box(Modifier.fillMaxWidth().height(Spacing.XS).background(Pixel.InkSoft.copy(alpha = 0.4f))) {
                Box(Modifier.fillMaxWidth(fraction).height(Spacing.XS).background(Pixel.Tone))
            }
        }
    }
}

private const val UNDO_SHOWN_MILLIS = 10_000L
private const val UNDO_TICK_MILLIS = 100L

internal val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "19 Oct" */
fun LocalDate.dayMonth(): String = "$dayOfMonth ${monthNames[monthNumber - 1]}"

/** "Mon 19 Oct" */
fun LocalDate.short(): String = "${dayNames[dayOfWeek.ordinal]} $dayOfMonth ${monthNames[monthNumber - 1]}"

/** "Mon 19 Oct 2026": the year too, for a date that was typed and is read back. */
fun LocalDate.long(): String = "${short()} $year"

/** The bag item whose real icon stands for a reward. */
fun Reward.iconItem(): String? = when (this) {
    is Reward.Candies -> if (type == CandyType.SUPER) "super-candy" else "rare-candy"
    is Reward.Coins -> "amulet-coin"
    is Reward.Poke -> "heart-scale"
    is Reward.ItemDrop -> itemId
    is Reward.Evs -> "protein"
    is Reward.Friendship -> "friend-ball"
    is Reward.Hatched -> null
}
