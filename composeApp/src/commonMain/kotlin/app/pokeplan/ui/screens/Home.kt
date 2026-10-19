package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.pokeplan.app.AppData
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Tab
import app.pokeplan.ui.describe
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import kotlin.random.Random

@Composable
fun HomeScreen(model: AppModel, go: (Tab) -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy
    var picked by remember { mutableStateOf<Task?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(data.trainerName, style = MaterialTheme.typography.titleMedium)
                Text(TrainerRank.nameFor(data.player.trainerPoints), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Text(
                "${data.player.coins} coins   ${data.player.inventory[Items.RARE_CANDY]} candies",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }

        if (buddy != null) {
            TextBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sprite(buddy.form.id, buddy.isShiny, buddy.nickname, size = 120.dp)
                    Column {
                        Text(buddy.nickname, style = MaterialTheme.typography.titleMedium)
                        Text("Lv. ${buddy.level}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        Text(buddyLine(data), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        TextBox(Modifier.fillMaxWidth()) {
            val s = store.streak
            Text(
                if (s.current == 0) "Do one thing today to start a streak." else "Streak: ${s.current} day${if (s.current == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        data.rewardLog.filter { !it.seen }.takeLast(3).takeIf { it.isNotEmpty() }?.let { fresh ->
            TextBox(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    fresh.forEach { entry ->
                        Text(entry.source, style = MaterialTheme.typography.titleMedium)
                        entry.rewards.forEach { Text(it.describe(), style = MaterialTheme.typography.bodyLarge) }
                    }
                    PixelButton("OK", { store.markRewardsSeen() }, Modifier.padding(top = 8.dp), Pixel.Green)
                }
            }
        }

        Text("Today", style = MaterialTheme.typography.titleMedium)
        val tasks = store.todayTasks()
        val pendingIds = data.pending.map { it.original.id }.toSet()
        if (tasks.isEmpty() && data.pending.isEmpty()) {
            TextBox(Modifier.fillMaxWidth()) { Text("Nothing planned. Add a task in Planner, or just start a focus session.", style = MaterialTheme.typography.bodyLarge) }
        }
        tasks.forEach { t ->
            TaskRow(t, highlighted = picked?.id == t.id, onDone = { store.completeTask(t.id) })
        }
        data.pending.forEach { p ->
            TextBox(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Done: ${p.done.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Undo", { store.undoCompletion(p.original.id) }, color = Pixel.Red)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("Pick for me", {
                picked = PickForMe.choose(tasks.filter { it.id !in pendingIds }, Random)?.task
            }, Modifier.weight(1f), Pixel.Blue, enabled = tasks.isNotEmpty())
            PixelButton("Start focus", { go(Tab.FOCUS) }, Modifier.weight(1f), Pixel.Red)
        }
        picked?.let {
            TextBox(Modifier.fillMaxWidth()) { Text("How about: ${it.title}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun TaskRow(task: Task, highlighted: Boolean, onDone: () -> Unit) {
    TextBox(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, color = if (highlighted) Pixel.Red else Pixel.Ink)
                Text("Rank ${task.rank.letter}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            PixelButton("Done", onDone, color = Pixel.Green)
        }
    }
}

/** What the buddy says, by what has happened today. */
private fun buddyLine(data: AppData): String {
    val done = data.rewardLog.size
    return when {
        data.focus != null -> "I'm cheering you on!"
        done == 0 -> "Ready when you are."
        else -> "Nice work today!"
    }
}
