package app.pokeplan.ui.screens

import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun QuestsView(model: AppModel) {
    val store = model.store
    val data = model.data
    val active = store.activeQuests
    val available = store.availableQuests
    val done = data.quests.count { it.value.completedAtMillis != null }
    val locked = Quests.all.filter { it.id !in data.quests && Quests.lockedReason(it, data) != null }.sortedBy { it.unlockRank }.take(3)
    // A quest shows its Pokémon, so the Pokédex counts it as seen (locked ones stay grey shapes).
    val questSpecies = (active + available.take(12)).map { it.speciesId }
    LaunchedEffect(questSpecies) { store.markSeen(questSpecies) }

    // World's jobs are World's own (a module owns its quests and its story); the shared list only says where they stand.
    val world = data.world
    if (app.pokeplan.world.Missions.boardOpen(world)) TextBox {
        Text(
            "World jobs: ${world.missions.size} taken, ${world.missions.count { it.done }} done and ready to report. They are on the mission board, in Modules, World, Missions.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (active.isEmpty() && available.isEmpty()) {
        EmptyState("No quests yet", "Quests open as your trainer rank grows.", icon = "oaks-letter", action = "Start a focus session" to { model.open(Place.FOCUS, true) })
    }
    if (active.isNotEmpty()) SectionLabel("In progress")
    active.forEach { q -> QuestCard(model, q) }
    if (available.isNotEmpty()) SectionLabel("Available")
    available.take(12).forEach { q -> QuestCard(model, q) }
    if (available.size > 12) TextBox { Text("${available.size - 12} more are open. Finish one to see the next.", style = MaterialTheme.typography.bodyMedium) }
    if (locked.isNotEmpty()) {
        SectionLabel("Coming up")
        locked.forEach { q ->
            ListRow(lead = { Sprite(Dex.defaultForm(q.speciesId).id, false, "Locked", size = 56.dp, silhouette = Pixel.Shade) }) {
                Text(q.title, style = MaterialTheme.typography.bodyLarge)
                Text(Quests.lockedReason(q, data) ?: "", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    if (done > 0) Text("$done quest${if (done == 1) "" else "s"} completed", style = MaterialTheme.typography.bodyLarge, color = Pixel.Ink)
}

@Composable
private fun QuestCard(model: AppModel, quest: Quest) {
    val store = model.store
    val state = model.data.quests[quest.id]
    val ready = store.questReady(quest)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Sprite(Dex.defaultForm(quest.speciesId).id, false, Dex.species(quest.speciesId).name, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(Dex.species(quest.speciesId).name, style = MaterialTheme.typography.titleMedium)
                    Text(quest.title, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (quest.longTerm) Text("Long-term", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            }
            Text(quest.story, style = MaterialTheme.typography.bodyMedium)
            store.questProgress(quest).forEach { (req, now) ->
                val shown = now.coerceAtMost(req.target)
                Text("${req.text}: $shown of ${req.target}", style = MaterialTheme.typography.bodyMedium)
                PixelBar(shown.toFloat() / req.target, color = if (shown >= req.target) Pixel.Green else Pixel.Blue, label = "${req.text}: $shown of ${req.target}")
            }
            when {
                state == null -> PixelButton("Take on this quest", { store.acceptQuest(quest.id) }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = store.activeQuests.size < Quests.MAX_ACTIVE, compact = true)
                ready -> PixelButton("Welcome ${Dex.species(quest.speciesId).name}", { store.claimQuest(quest.id) }, Modifier.fillMaxWidth(), Pixel.Green)
            }
        }
    }
}

// ----- Bag -----------------------------------------------------------------------------------------------------------
