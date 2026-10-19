package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.app.AppData
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Tab
import app.pokeplan.ui.theme.NamePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.StatChip
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ListRow
import kotlin.random.Random

@Composable
fun HomeScreen(model: AppModel, go: (Tab) -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy
    var picked by remember { mutableStateOf<Task?>(null) }
    val points = data.player.trainerPoints

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = data.trainerName, subtitle = TrainerRank.nameFor(points),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip("${data.player.coins}", { ItemIcon("amulet-coin", size = 28.dp) })
                    StatChip("${data.player.inventory[Items.RARE_CANDY]}", { ItemIcon("rare-candy", size = 28.dp) })
                }
            },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RankProgress(points)

            if (buddy != null) {
                // The buddy stands on the wallpaper with a text box above it and a name plate below.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextBox(Modifier.widthIn(max = 280.dp)) {
                        Text(buddyLine(data), style = MaterialTheme.typography.bodyLarge)
                    }
                    Box(Modifier.height(170.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.padding(bottom = 14.dp).size(150.dp, 26.dp)
                                .drawBehind { drawOval(Color(0x40000000), Offset.Zero, Size(size.width, size.height)) },
                        )
                        Sprite(buddy.form.id, buddy.isShiny, buddy.nickname, Modifier.offset(y = 28.dp), size = 192.dp)
                    }
                    NamePlate(buddy.nickname, "Lv. ${buddy.level}")
                }
            }

            StreakChip(store.streak.current)
            RewardBox(model)
            UndoRows(model)

            SectionLabel("Today")
            val tasks = store.todayTasks()
            val pendingIds = data.pending.map { it.original.id }.toSet()
            val shown = tasks.filter { it.id !in pendingIds }
            if (shown.isEmpty() && data.pending.isEmpty()) {
                TextBox { Text("Nothing planned. Add a task in Planner, or start a focus session.", style = MaterialTheme.typography.bodyLarge) }
            }
            shown.forEach { t ->
                TaskRow(t, onDone = { store.completeTask(t.id) }, highlighted = picked?.id == t.id)
            }
            picked?.let {
                TextBox(accent = Pixel.Yellow) { Text("How about: ${it.title}?", style = MaterialTheme.typography.bodyLarge) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PixelButton("Pick for me", {
                    picked = PickForMe.choose(shown, Random)?.task
                }, Modifier.weight(1f), Pixel.Blue, enabled = shown.isNotEmpty())
                PixelButton("Start focus", { go(Tab.FOCUS) }, Modifier.weight(1f), Pixel.Red)
            }
        }
    }
}

/** A task as one compact row: rank badge, title and details, a Done key. */
@Composable
fun TaskRow(task: Task, onDone: () -> Unit, highlighted: Boolean = false) {
    ListRow(
        lead = { RankBadge(task.rank) },
        trail = { PixelButton("Done", onDone, color = Pixel.Green, compact = true) },
    ) {
        Text(task.title, style = MaterialTheme.typography.bodyLarge, color = if (highlighted) Pixel.Red else Pixel.Ink, maxLines = 2)
        val meta = taskMeta(task)
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** Progress to the next trainer rank, drawn like an HP bar. */
@Composable
private fun RankProgress(points: Int) {
    val rank = TrainerRank.rankFor(points)
    val low = TrainerRank.thresholds[rank]
    val high = TrainerRank.thresholds.getOrNull(rank + 1)
    val next = TrainerRank.pointsToNext(points)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PixelBar(if (high == null) 1f else (points - low).toFloat() / (high - low), color = Pixel.Blue)
        Box(Modifier.background(Pixel.Panel, app.pokeplan.ui.theme.NotchShape(2f)).padding(horizontal = 8.dp)) {
            Text(
                if (next == null) "Top rank" else "$next points to ${TrainerRank.names[rank + 1]}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StreakChip(days: Int) {
    Box(Modifier.background(Pixel.Panel, app.pokeplan.ui.theme.NotchShape(2f)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(
            if (days == 0) "Do one thing today to start a streak" else "Streak: $days day${if (days == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** What the buddy says, by what has happened today. */
private fun buddyLine(data: AppData): String = when {
    data.focus != null -> "I'm cheering you on!"
    data.rewardLog.isEmpty() -> "Ready when you are."
    else -> "Nice work today!"
}
