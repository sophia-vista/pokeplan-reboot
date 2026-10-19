package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import app.pokeplan.core.pokemon.ItemCategory
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyLines
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.PixelBackground
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Tab
import app.pokeplan.ui.theme.ExpBar
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.NamePlate
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
import kotlin.random.Random

/**
 * Home helps decide what to do next: a slim header with the rank, the buddy, one recommended task with its actions, how
 * today is going, and shortcuts. Nothing here punishes a quiet day.
 */
@Composable
fun HomeScreen(model: AppModel, go: (Tab) -> Unit) {
    val store = model.store
    val data = model.data
    val buddy = data.player.buddy
    val points = data.player.trainerPoints
    var chosen by remember { mutableStateOf<String?>(null) }

    val pendingIds = data.pending.map { it.original.id }.toSet()
    val open = store.todayTasks().filter { it.id !in pendingIds }
    val next = open.firstOrNull { it.id == chosen } ?: open.firstOrNull()
    val doneToday = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }
    val total = doneToday + open.size

    Column(Modifier.fillMaxSize().background(Pixel.Paper)) {
        ScreenHeader(
            title = data.trainerName,
            subtitle = "Rank: ${TrainerRank.nameFor(points)}",
            icon = "town-map",
            footer = { RankProgress(points) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    StatChip("${data.player.coins}", { ItemIcon("amulet-coin", size = 28.dp, description = "Coins") })
                    StatChip("${data.player.inventory[Items.RARE_CANDY]}", { ItemIcon("rare-candy", size = 28.dp, description = "Rare Candies") })
                }
            },
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            RewardBox(model)
            UndoRows(model)

            if (buddy != null) {
                val berry = data.player.inventory.counts.keys.firstOrNull { Items.category(it) == ItemCategory.BERRY && data.player.inventory.has(it) }
                Companion(
                    formId = buddy.form.id, shiny = buddy.isShiny, name = buddy.nickname, detail = "Lv. ${buddy.level}",
                    line = store.buddyMood().line, calm = data.settings.reduceMotion,
                    onPet = { store.pet().also { model.cry(buddy.speciesId) } },
                    berry = berry,
                    onFeed = { berry?.let { store.useItem(it, buddy.id) } },
                )
            }

            when {
                next != null -> NextUp(
                    task = next,
                    onDone = { store.completeTask(next.id) },
                    onFocus = { model.focusTaskId = next.id; go(Tab.FOCUS) },
                    onChoose = if (open.size > 1) ({ chosen = PickForMe.choose(open.filter { it.id != next.id }, Random)?.task?.id }) else null,
                )
                doneToday > 0 -> TextBox(accent = Pixel.Green) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text("All done for today", style = MaterialTheme.typography.titleSmall)
                        Text("You finished $doneToday task${if (doneToday == 1) "" else "s"} today. Rest, or start a focus session.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text("Nothing planned for today", style = MaterialTheme.typography.titleSmall)
                        Text("Add one small task to get going, or just start a focus session.", style = MaterialTheme.typography.bodyLarge)
                        PixelButton("Add a task", { go(Tab.PLANNER) }, Modifier.fillMaxWidth(), Pixel.Red)
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
                }
            }

            val more = open.filter { it.id != next?.id }.take(4)
            if (more.isNotEmpty()) {
                SectionLabel("Also today", icon = "oaks-parcel")
                more.forEach { t -> TaskRow(t, onDone = { store.completeTask(t.id) }) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                PixelButton("Planner", { go(Tab.PLANNER) }, Modifier.weight(1f), Pixel.InkSoft)
                PixelButton("Start focus", { model.focusTaskId = null; go(Tab.FOCUS) }, Modifier.weight(1f), Pixel.InkSoft)
            }
        }
    }
}

/**
 * The buddy standing on a wallpaper banner (the one place a texture is used on Home), with a name plate.
 * Tap it to pet it: it hops, hearts float up and it says something. The first few pets a day also raise friendship;
 * after that it is just for fun. A berry from the bag can be given from the button.
 */
@Composable
private fun Companion(
    formId: Int, shiny: Boolean, name: String, detail: String, line: String, calm: Boolean,
    onPet: () -> Int, berry: String?, onFeed: () -> Unit,
) {
    var said by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableStateOf(0) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        if (!calm) {
            hop.animateTo(-18f, tween(110))
            hop.animateTo(0f, tween(150))
        }
    }
    LaunchedEffect(said) {
        if (said != null) {
            delay(2_400)
            said = null
        }
    }
    val shape = RoundedCornerShape(Spacing.CardRadius)
    PixelBackground("bw01", Modifier.fillMaxWidth().height(216.dp).clip(shape).border(1.dp, Pixel.Border, shape)) {
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp).size(132.dp, 22.dp)
                .drawBehind { drawOval(Color(0x33000000), Offset.Zero, Size(size.width, size.height)) },
        )
        Hearts(taps, calm, Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp))
        AnimatedSprite(
            formId, shiny, name,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp).offset { IntOffset(0, hop.value.dp.roundToPx()) }
                .clickable(onClickLabel = "Pet $name", role = Role.Button) {
                    onPet()
                    taps++
                    said = BuddyLines.pet()
                },
            size = 144.dp, animate = !calm,
        )
        NamePlate(name, detail, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.S))
        Text(
            said ?: line, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S).background(Pixel.Panel, RoundedCornerShape(50)).padding(horizontal = Spacing.M, vertical = 6.dp),
        )
        if (berry != null) Box(
            Modifier.align(Alignment.BottomEnd).padding(Spacing.S).size(48.dp).background(Pixel.Panel, CircleShape)
                .border(1.dp, Pixel.Border, CircleShape)
                .clickable(onClickLabel = "Give $name a ${(Items.get(berry)?.name ?: berry)}", role = Role.Button) {
                    onFeed()
                    taps++
                    said = BuddyLines.feed()
                },
            contentAlignment = Alignment.Center,
        ) { ItemIcon(berry, size = 32.dp, description = (Items.get(berry)?.name ?: berry)) }
    }
}

/** A few hearts rise from the buddy and fade out after each pet. */
@Composable
private fun Hearts(taps: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        rise.snapTo(0f)
        rise.animateTo(1f, tween(if (calm) 400 else 900))
    }
    if (rise.value >= 1f) return
    Box(modifier.size(96.dp, 80.dp).graphicsLayer { alpha = 1f - rise.value }) {
        listOf(0.dp to 0f, 40.dp to 0.25f, 72.dp to 0.1f).forEach { (x, delayShare) ->
            val t = ((rise.value - delayShare) / (1f - delayShare)).coerceIn(0f, 1f)
            Heart(Modifier.offset(x = x, y = 60.dp - (if (calm) 0.dp else 60.dp * t)).size(22.dp))
        }
    }
}

@Composable
private fun Heart(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2, h * 0.92f)
            cubicTo(-w * 0.15f, h * 0.45f, w * 0.15f, -h * 0.1f, w / 2, h * 0.28f)
            cubicTo(w * 0.85f, -h * 0.1f, w * 1.15f, h * 0.45f, w / 2, h * 0.92f)
            close()
        }
        drawPath(path, Pixel.Red)
    }
}

/** The one recommended task, with its two actions. Completing is its own button; the rest of the card is just information. */
@Composable
private fun NextUp(task: Task, onDone: () -> Unit, onFocus: () -> Unit, onChoose: (() -> Unit)?) {
    TextBox(accent = Pixel.Red) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text("Next up", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                RankBadge(task.rank)
                Text(listOf(task.rank.title, taskMeta(task)).filter { it.isNotEmpty() }.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
            }
            PixelButton("Done", onDone, Modifier.fillMaxWidth(), Pixel.Green)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                PixelButton("Focus on this", onFocus, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                if (onChoose != null) PixelButton("Pick another", onChoose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
        Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
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
