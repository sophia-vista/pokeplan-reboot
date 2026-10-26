package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.model.CandyType
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
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate

/** The newest rewards nobody has looked at yet, with an OK button. Shows nothing when there are none. */
@Composable
fun RewardBox(model: AppModel) {
    val fresh = model.data.rewardLog.filter { !it.seen }.takeLast(3)
    if (fresh.isEmpty()) return
    // A hatched Pokémon calls out once, when its box first shows.
    val hatched = fresh.flatMap { e -> e.rewards.filterIsInstance<Reward.Hatched>().map { e.atMillis to it.pokemon.speciesId } }.lastOrNull()
    LaunchedEffect(hatched?.first) { hatched?.let { model.cry(it.second) } }
    GameBox(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fresh.forEach { entry ->
                Text(entry.source, style = MaterialTheme.typography.titleMedium)
                entry.rewards.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r is Reward.Hatched) AnimatedSprite(r.pokemon.form.id, r.pokemon.isShiny, r.pokemon.nickname, size = 72.dp, animate = !model.data.settings.reduceMotion, ground = 4.dp)
                        else r.iconItem()?.let { ItemIcon(it, size = 32.dp) }
                        Text(r.describe(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            PixelButton("OK", { model.store.markRewardsSeen() }, Modifier.padding(top = 8.dp), Pixel.Green)
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
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portrait(buddy.form.id, buddy.isShiny, buddy.nickname, emotion = emotion, size = 64.dp)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** Tasks just ticked off, still inside the 10 second window where Undo cancels the reward. */
@Composable
fun UndoRows(model: AppModel) {
    model.data.pending.forEach { p ->
        TextBox(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Done: ${p.done.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                PixelButton("Undo", { model.store.undoCompletion(p.original.id) }, color = Pixel.Red)
            }
        }
    }
}

internal val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "19 Oct" */
fun LocalDate.dayMonth(): String = "$dayOfMonth ${monthNames[monthNumber - 1]}"

/** "Mon 19 Oct" */
fun LocalDate.short(): String = "${dayNames[dayOfWeek.ordinal]} $dayOfMonth ${monthNames[monthNumber - 1]}"

/** The bag item whose real icon stands for a reward. */
fun Reward.iconItem(): String? = when (this) {
    is Reward.Candies -> if (type == CandyType.SUPER) "super-candy" else "rare-candy"
    is Reward.Coins -> "amulet-coin"
    is Reward.ItemDrop -> itemId
    is Reward.Evs -> "protein"
    is Reward.Hatched -> null
}
