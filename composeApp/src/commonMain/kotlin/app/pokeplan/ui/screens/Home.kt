package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.Sprite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.theme.RegionLook
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.geometry.Offset
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.DialogueShape
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
 * Home is the world at a glance (docs/ecosystem.md, section 11): the trainer, every region with its lead and its one most
 * useful next step, then the day's streak, mini-quests and what happened lately. It is not Plan's dashboard: the planner,
 * focus and each lead's scene stay inside their region. Nothing here punishes a quiet day.
 */
@Composable
fun HomeScreen(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    val data = model.data
    val points = data.player.trainerPoints

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

            PlanRegionCard(model, go)
            if (data.settings.careModule) CareRegionCard(model, go)

            StreakCard(model)
            if (store.sees(Feature.MINI_QUESTS)) MiniQuestsCard(model, go)
            RecentCard(model, go)
        }
    }
}

/**
 * One region on Home: its lead idling in a window onto the region's scene ([RegionLook]), its name, how today stands there,
 * and below a line its one most useful next step ([step]). The region's colour runs down the card's edge. The top of the
 * card opens the region.
 */
@Composable
private fun RegionCard(
    name: String, icon: String, look: RegionLook, darkness: Float, lead: OwnedPokemon?, leadLine: String, status: String,
    still: Boolean, onOpen: () -> Unit, step: @Composable () -> Unit,
) {
    val shape = DialogueShape
    Column(
        Modifier.fillMaxWidth().dialogueBox().clip(shape)
            .drawBehind { drawRect(look.accent, Offset(8.dp.toPx(), 6.dp.toPx()), Size(3.dp.toPx(), size.height - 12.dp.toPx())) },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open $name", role = Role.Button, onClick = onOpen).padding(horizontal = Spacing.L, vertical = Spacing.M),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val window = RoundedCornerShape(Spacing.M)
            Box(Modifier.size(80.dp).clip(window).border(1.dp, Pixel.Border, window), contentAlignment = Alignment.BottomCenter) {
                SceneImage(look.scene, Modifier.matchParentSize(), darkness)
                if (lead != null) AnimatedSprite(lead.form.id, lead.isShiny, lead.nickname, size = 72.dp, animate = !still, ground = 4.dp)
                else ItemIcon(icon, size = 56.dp, description = null, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(leadLine, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2)
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = Pixel.InkSoft)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.S).height(1.dp).background(Pixel.Border))
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.M)) { step() }
    }
}

/** A next step: what it is in words, and the one button that does it. */
@Composable
private fun StepRow(text: String, action: String?, color: Color = Pixel.Green, lead: @Composable () -> Unit = {}, onClick: () -> Unit = {}) {
    // At large text sizes the words and the button do not fit side by side: the button goes under the words and nothing is cut off.
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                lead()
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            if (action != null) PixelButton(action, onClick, Modifier.fillMaxWidth(), color = color, compact = true)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        lead()
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 3)
        if (action != null) PixelButton(action, onClick, color = color, compact = true)
    }
}

private fun leadLine(p: OwnedPokemon) = "Lead: ${p.nickname}, Lv. ${p.level}"

/** Plan: its buddy, today's tasks, and the next one with Done (the picked one first), or the running focus session. */
@Composable
private fun PlanRegionCard(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy
    val open = store.openTodayTasks()
    val next = open.firstOrNull { it.id == data.pickedTaskId } ?: open.firstOrNull()
    val doneToday = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }
    val total = doneToday + open.size
    RegionCard(
        Module.PLAN.label, Module.PLAN.icon, Module.PLAN.look, BuddyScene.darkness(store.now.hour, store.now.minute), buddy, buddy?.let(::leadLine) ?: "No buddy yet",
        if (total == 0) "Tasks and focus" else "$doneToday of $total tasks done today",
        still = data.settings.reduceMotion, onOpen = { go(Place.PLAN) },
    ) {
        when {
            data.focus != null -> StepRow("An egg is incubating", "Open", Pixel.Blue) { go(Place.FOCUS) }
            next != null -> StepRow(next.title, "Done", lead = { RankBadge(next.rank) }) { model.completeTask(next.id) }
            doneToday > 0 -> StepRow("All done for today. Rest, or add one more if you feel like it.", null)
            else -> StepRow("Nothing planned for today", "Add a task", Pixel.Red) { model.addTaskRequested = true; go(Place.PLANNER) }
        }
    }
}

/** Care: its lead (or the Plan buddy helping until it has one), today's routines, and the next one with Done. */
@Composable
private fun CareRegionCard(model: AppModel, go: (Place) -> Unit) {
    model.careData // redraw when Care changes
    val care = model.care
    val player = model.data.player
    val lead = player.careLead
    val today = care.todayRoutines()
    val done = today.count { it.doneOn(care.today) }
    val next = today.firstOrNull { !it.doneOn(care.today) }
    val shown = lead ?: model.store.careBuddy
    RegionCard(
        Module.CARE.label, Module.CARE.icon, Module.CARE.look, BuddyScene.darkness(model.store.now.hour, model.store.now.minute), lead,
        lead?.let(::leadLine) ?: shown?.let { "No lead yet: ${it.nickname} helps out" } ?: "No lead yet",
        if (today.isEmpty()) "Routines and calm" else "$done of ${today.size} routines today",
        still = model.data.settings.reduceMotion, onOpen = { go(Place.CARE) },
    ) {
        val open = { section: CareSection -> go(Place.CARE); model.careSection = section }
        when {
            next != null -> StepRow(next.title, "Done") { model.effect(Sfx.SELECT); model.haptic(); care.completeRoutine(next.id) }
            today.isNotEmpty() -> StepRow("Routines done for today. A calm break is always there.", "Calm break", Pixel.Blue) { open(CareSection.CALM) }
            model.careData.routines.none { !it.archived } -> StepRow("Small things you do for yourself, like a glass of water.", "Add a routine", Pixel.Green) { open(CareSection.ROUTINES) }
            else -> StepRow("No routines today. A calm break is always there.", "Calm break", Pixel.Blue) { open(CareSection.CALM) }
        }
    }
}

/** The streak, which any region's effort keeps, and the rest day that pauses it. */
@Composable
private fun StreakCard(model: AppModel) {
    val streak = model.store.streak.current
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(if (streak == 0) "Do one thing today to start a streak." else "Streak: $streak day${if (streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
            RestDayRow(model)
        }
    }
}

/** How many things Recently lists; the rest stay in the log. */
private const val RECENT_SHOWN = 4

/**
 * What happened lately in any region, newest first: hatches with their sprite (a tap opens the Pokémon), other rewards with
 * their item. Rewards not looked at yet are in [RewardBox] above, so only seen ones are here.
 */
@Composable
private fun RecentCard(model: AppModel, go: (Place) -> Unit) {
    val store = model.store
    val entries = model.data.rewardLog.filter { it.seen }.takeLast(RECENT_SHOWN).reversed()
    if (entries.isEmpty()) return
    SectionLabel("Recently", icon = "oaks-letter")
    entries.forEach { entry ->
        val hatched = entry.rewards.filterIsInstance<Reward.Hatched>().firstOrNull()?.pokemon
        val owned = hatched?.let { h -> model.data.player.pokemon.firstOrNull { it.id == h.id } }
        ListRow(
            lead = {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (hatched != null) Sprite(hatched.form.id, hatched.isShiny, hatched.nickname, size = 48.dp)
                    else entry.rewards.firstNotNullOfOrNull { it.iconItem() }?.let { ItemIcon(it, size = 32.dp, description = null) }
                }
            },
            onClick = owned?.let { p -> { model.openPokemonId = p.id; go(Place.COLLECTION) } },
            onClickLabel = owned?.let { "Open ${it.nickname}" },
        ) {
            Text(if (hatched != null) "${hatched.nickname} hatched" else entry.source, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            val what = if (hatched != null) entry.source else entry.rewards.take(2).joinToString(", ") { it.describe() }
            Text(listOf(what, daysAgo(store.today, store.dayOf(entry.atMillis))).filter { it.isNotEmpty() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

private fun daysAgo(today: LocalDate, day: LocalDate): String = when (val n = day.daysUntil(today)) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$n days ago"
}

/**
 * The Plan buddy in Plan's town, on Plan's page: pet it, give it a berry, and it celebrates a finished task or a
 * hatch with its cry, its portrait and a hop. Nothing before the first buddy.
 */
@Composable
fun PlanBuddyStage(model: AppModel, onOpen: () -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy ?: return
    val berry = store.buddyBerry()
    BuddyStage(
        buddy = buddy, scene = Module.PLAN.look.scene, darkness = BuddyScene.darkness(store.now.hour, store.now.minute),
        progress = store.levelProgress(buddy), event = store.buddyEvent(),
        calm = data.settings.reduceMotion, resting = data.focus != null, berry = berry,
        onPet = { avoid -> store.pet(); model.cry(buddy.speciesId); BuddyReaction.pet(buddy, store.now.hour, avoid = avoid) },
        onFeed = { avoid -> berry?.let { store.useItem(it, buddy.id); model.effect(Sfx.HEAL) }; BuddyReaction.feed(buddy, avoid = avoid) },
        onOpen = onOpen, onHaptic = { model.haptic() }, onEvent = { model.cry(buddy.speciesId) },
        celebrated = model.celebratedAt, onCelebrated = { model.celebratedAt = it },
    )
}

/** The one recommended task (on Plan's page), with its actions. Completing is its own button; the rest of the card is just information. */
@Composable
internal fun NextUp(task: Task, onDone: () -> Unit, onFocus: (() -> Unit)?, onChoose: (() -> Unit)?, picked: Boolean = false) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text(if (picked) "Picked for you: +10% candies" else "Next up", style = MaterialTheme.typography.labelMedium, color = if (picked) Pixel.Green else Pixel.InkSoft)
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                RankBadge(task.rank)
                Text(listOf(task.rank.title, taskMeta(task)).filter { it.isNotEmpty() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
            PixelButton("Done", onDone, Modifier.fillMaxWidth(), Pixel.Green)
            if (onFocus != null || onChoose != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                if (onFocus != null) PixelButton("Focus on this", onFocus, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
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
            if (got.isEmpty()) Text("A berry and 2 Rare Candies each, more for all three.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
            .padding(horizontal = Spacing.M, vertical = Spacing.S)
            .semantics(mergeDescendants = true) { contentDescription = "Cloud save: $label" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pixel.Ink)
    }
}
