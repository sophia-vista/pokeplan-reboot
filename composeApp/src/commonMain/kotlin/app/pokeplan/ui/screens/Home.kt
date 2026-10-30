package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import app.pokeplan.core.pokemon.ItemCategory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import app.pokeplan.ui.WhatsNew
import app.pokeplan.ui.AppVersion
import app.pokeplan.app.cloud.SyncStatus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.MiniQuest
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.describe
import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.ExpBar
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.StatChip
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.title

/**
 * Home helps decide what to do next: a slim header with the rank, the buddy, one recommended task with its actions, how
 * today is going, and shortcuts. Nothing here punishes a quiet day.
 */
@Composable
fun HomeScreen(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy
    val points = data.player.trainerPoints

    val pendingIds = data.pending.map { it.original.id }.toSet()
    val open = store.todayTasks().filter { it.id !in pendingIds }
    val next = open.firstOrNull { it.id == data.pickedTaskId } ?: open.firstOrNull()
    val doneToday = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }
    val total = doneToday + open.size

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = data.trainerName,
            subtitle = "Rank: ${TrainerRank.nameFor(points)}",
            icon = "town-map",
            footer = { RankProgress(points) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    SyncChip(model) { go(Place.ME) }
                    if (store.sees(Feature.COINS)) StatChip("${data.player.coins}", { ItemIcon("amulet-coin", size = 28.dp, description = "Coins") })
                    StatChip("${data.player.inventory[Items.RARE_CANDY]}", { ItemIcon("rare-candy", size = 28.dp, description = "Rare Candies") })
                }
            },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            WhatsNewCard(model)
            RankUpCard(model)
            WelcomeBackCard(model)
            RewardBox(model)

            PlanBuddyStage(model) { buddy?.let { model.openPokemonId = it.id }; go(Place.COLLECTION) }

            when {
                next != null -> NextUp(
                    task = next,
                    onDone = { model.completeTask(next.id) },
                    onFocus = { model.focusTaskId = next.id; go(Place.FOCUS) },
                    onChoose = if (open.size > 1 || data.pickedTaskId != next.id) ({ store.pickForMe() }) else null,
                    picked = next.id == data.pickedTaskId,
                )
                // The progress card below already counts what was done, so this says only that the day is clear.
                doneToday > 0 -> TextBox { Text("All done for today", style = MaterialTheme.typography.titleSmall) }
                else -> TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text("Nothing planned for today", style = MaterialTheme.typography.titleSmall)
                        PixelButton("Add a task", { go(Place.PLANNER) }, Modifier.fillMaxWidth(), Pixel.Red)
                    }
                }
            }

            if (total > 0) TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    val label = "$doneToday of $total tasks done today"
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    PixelBar(doneToday.toFloat() / total, color = Pixel.Green, label = label)
                    val streak = store.streak.current
                    Text(if (streak == 0) "Do one thing today to start a streak." else "Streak: $streak day${if (streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                    RestDayRow(model)
                }
            }
            if (store.sees(Feature.MINI_QUESTS)) MiniQuestsCard(model, go)

            val more = open.filter { it.id != next?.id }.take(4)
            if (more.isNotEmpty()) {
                SectionLabel("Also today", icon = "oaks-parcel")
                more.forEach { t -> TaskRow(t, onDone = { model.completeTask(t.id) }) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                // With nothing planned, the empty card's "Add a task" already goes to the Planner.
                if (total > 0) PixelButton("Planner", { go(Place.PLANNER) }, Modifier.weight(1f), Pixel.InkSoft)
                PixelButton("Start focus", { model.focusTaskId = null; go(Place.FOCUS) }, Modifier.weight(1f), Pixel.InkSoft)
            }
        }
    }
}

/**
 * The Plan buddy in its type's scene, on Home and in Plan: pet it, give it a berry, and it celebrates a finished task or a
 * hatch with its cry, its portrait and a hop. Nothing before the first buddy.
 */
@Composable
fun PlanBuddyStage(model: AppModel, onOpen: () -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy ?: return
    val berry = data.player.inventory.counts.keys.sorted().firstOrNull { Items.category(it) == ItemCategory.BERRY && data.player.inventory.has(it) }
    BuddyStage(
        buddy = buddy, scene = BuddyScene.of(buddy.form.types), darkness = BuddyScene.darkness(store.now.hour, store.now.minute),
        progress = store.levelProgress(buddy), event = store.buddyEvent(),
        calm = data.settings.reduceMotion, resting = data.focus != null, berry = berry,
        onPet = { avoid -> store.pet(); model.cry(buddy.speciesId); BuddyReaction.pet(buddy, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> berry?.let { store.useItem(it, buddy.id); model.effect(Sfx.HEAL) }; BuddyReaction.feed(buddy, avoid = avoid) },
        onOpen = onOpen, onHaptic = { model.haptic() }, onEvent = { model.cry(buddy.speciesId) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
    )
}

/** The one recommended task, with its two actions. Completing is its own button; the rest of the card is just information. */
@Composable
private fun NextUp(task: Task, onDone: () -> Unit, onFocus: () -> Unit, onChoose: (() -> Unit)?, picked: Boolean = false) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text(if (picked) "Picked for you: +10% candies" else "Next up", style = MaterialTheme.typography.labelMedium, color = if (picked) Pixel.Green else Pixel.InkSoft)
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                RankBadge(task.rank)
                Text(listOf(task.rank.title, taskMeta(task)).filter { it.isNotEmpty() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
            PixelButton("Done", onDone, Modifier.fillMaxWidth(), Pixel.Green)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                PixelButton("Focus on this", onFocus, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                if (onChoose != null) PixelButton(if (picked) "Pick another" else "Pick for me", onChoose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

/** A task as one compact row: rank badge, title and details, a Done key. */
@Composable
fun TaskRow(task: Task, onDone: () -> Unit) {
    ListRow(
        lead = { RankBadge(task.rank) },
        trail = { PixelButton("Done", onDone, color = Pixel.Green, compact = true) },
    ) {
        Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
        val meta = taskMeta(task)
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Progress to the next trainer rank, as an EXP bar like the games', with the words beside it. */
@Composable
private fun RankProgress(points: Int) {
    val rank = TrainerRank.rankFor(points)
    val low = TrainerRank.thresholds[rank]
    val high = TrainerRank.thresholds.getOrNull(rank + 1)
    val next = TrainerRank.pointsToNext(points)
    val label = if (next == null) "Top rank" else "$next points to ${TrainerRank.names[rank + 1]}"
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        ExpBar(if (high == null) 1f else (points - low).toFloat() / (high - low), label, color = Pixel.Yellow)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
    }
}

/** A rest day pauses the streak without using the week's free miss; a day that already counted needs none. */
@Composable
private fun RestDayRow(model: AppModel) {
    val store = model.store
    val day = store.streakDay
    if (day in model.data.activeDays) return
    val resting = day in model.data.restDays
    var refused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            if (resting) "Today is a rest day. Rest well." else if (refused) "No rest days left this month." else "Need a break? Rest days keep your streak.",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
        PixelButton(if (resting) "Undo" else "Rest today", { refused = !store.toggleRestDay(day) }, color = Pixel.InkSoft, compact = true)
    }
}

/** Three small actions a day, each a berry (honour system). "Open the task you've been avoiding" starts 5 minutes on it. */
@Composable
private fun MiniQuestsCard(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    // Read the save through Compose state: the store itself is not observable, so without this the card never redraws
    // after Done and the button looks dead.
    model.data
    val quests = store.miniQuests()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Mini-quests", style = MaterialTheme.typography.titleSmall)
            quests.forEach { q ->
                val done = store.miniQuestDone(q.id)
                val avoided = q.kind == MiniQuest.Kind.AVOIDED_TASK
                val oldest = if (avoided) store.oldestOpenTask() else null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(q.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (done) Pixel.InkSoft else Color.Unspecified)
                    when {
                        done -> Text("Done", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                        avoided && oldest != null && model.data.focus == null -> PixelButton("Start 5 min", {
                            store.completeMiniQuest(q.id)
                            store.startFocus(FocusRules.justFiveMinutes().minutesRoundedUp, deepFocus = false, taskId = oldest.id)
                            go(Place.FOCUS)
                        }, color = Pixel.Blue, compact = true)
                        else -> PixelButton("Done", { if (store.completeMiniQuest(q.id)) model.effect(Sfx.HEAL) }, color = Pixel.Green, compact = true)
                    }
                }
            }
            val got = store.miniQuestRewards()
            if (got.isEmpty()) Text("A berry each, and one more for all three.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else got.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    r.iconItem()?.let { ItemIcon(it, size = 24.dp) }
                    Text("You got ${r.describe()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                }
            }
            if (store.canRerollMiniQuests()) PixelButton("Different ones", { store.rerollMiniQuests() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}

/** After a week or more away: a gift and a fresh start, never a scolding. */
@Composable
private fun WelcomeBackCard(model: AppModel) {
    model.data // observe the save (see MiniQuestsCard)
    if (!model.store.welcomeBackDue()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Welcome back!", style = MaterialTheme.typography.titleSmall)
            Text("It's good to see you. Here's a little something to start again with.", style = MaterialTheme.typography.bodyLarge)
            PixelButton("Open the gift", { model.store.claimWelcomeBack(); model.effect(Sfx.HEAL) }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

/** After a rank up: the new rank and what it opened, once. Nothing when there is nothing new. */
@Composable
private fun RankUpCard(model: AppModel) {
    val opened = model.store.newlyOpened()
    if (opened.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Rank up: ${TrainerRank.nameFor(model.data.player.trainerPoints)}!", style = MaterialTheme.typography.titleSmall)
            Text("New for you:", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            opened.forEach { Text("•  ${it.label}", style = MaterialTheme.typography.bodyLarge) }
            PixelButton("Got it", { model.store.markRankSeen() }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

/** After an update: what is new in this version, once. Nothing when there is no entry or it has been seen. */
@Composable
private fun WhatsNewCard(model: AppModel) {
    val version = AppVersion.NAME
    val lines = WhatsNew.forVersion(version) ?: return
    if (model.data.lastSeenVersion == version) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("What's new in $version", style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
            PixelButton("Got it", { model.store.markVersionSeen(version) }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

/** Where the cloud save stands, as a small dot and word. Only shown when signed in; tapping it opens Settings. */
@Composable
private fun SyncChip(model: AppModel, open: () -> Unit) {
    val account = model.data.account ?: return
    val (label, color) = when (val status = model.syncStatus) {
        is SyncStatus.Syncing -> "Syncing" to Pixel.Blue
        is SyncStatus.Offline -> "Offline" to Pixel.Warning
        is SyncStatus.Conflict -> "Choose a save" to Pixel.Danger
        is SyncStatus.Refused -> "Not saved" to Pixel.Danger
        else -> if (account.unsynced) "Waiting" to Pixel.Warning else "Synced" to Pixel.Green
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Pixel.Panel)
            .clickable(onClickLabel = "Open the account settings", role = Role.Button, onClick = open)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Cloud save: $label" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pixel.Ink)
    }
}
