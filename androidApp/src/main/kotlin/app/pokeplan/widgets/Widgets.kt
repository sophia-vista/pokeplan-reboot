package app.pokeplan.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pokeplan.AndroidCryPlayer
import app.pokeplan.MainActivity
import app.pokeplan.app.BuddyLines
import app.pokeplan.app.BuddyMood
import app.pokeplan.app.GameStore
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.widgetStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val red = Color(0xFFE84436)
private val paper = Color(0xFFF7F5F0)
private val panel = Color(0xFFFFFFFF)
private val ink = Color(0xFF252637)
private val soft = Color(0xFF666B7E)
private val shade = Color(0xFFE9E9EF)
private val green = Color(0xFF218858)

private fun rankColor(rank: Rank) = when (rank) {
    Rank.E -> Color(0xFF666B7E)
    Rank.D -> Color(0xFF218858)
    Rank.C -> Color(0xFF3478DB)
    Rank.B -> Color(0xFF946000)
    Rank.A -> Color(0xFFB42332)
}

private fun openApp(context: Context, tab: String? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply { tab?.let { putExtra(MainActivity.EXTRA_TAB, it) } },
)

/** Brings every widget up to date; called when the app is left and after a tap on a widget. */
suspend fun updateWidgets(context: Context) {
    TodayWidget().updateAll(context)
    BuddyWidget().updateAll(context)
    FocusWidget().updateAll(context)
}

private val TaskIdKey = ActionParameters.Key<String>("taskId")

private fun density(context: Context) = context.resources.displayMetrics.density

/** The soft pill the names and counts sit on, so text stays readable over a wallpaper. */
private fun GlanceModifier.pill(color: Color = panel) = background(color).cornerRadius(14.dp)

// ----- Today's tasks --------------------------------------------------------------------------------------------------

/** A tap on a task's tick: finishes it. The reward is granted the next time the app opens (the undo window has passed by then). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val store = widgetStore(context)
        if (store.data.tasks.any { it.id == id && it.isOpen }) store.completeTask(id)
        updateWidgets(context)
    }
}

private data class TodayState(val tasks: List<Task>, val done: Int, val total: Int, val name: String)

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val pending = store.data.pending.map { it.original.id }.toSet()
        val open = if (store.data.onboarded) store.todayTasks().filter { it.id !in pending && it.isOpen } else emptyList()
        val done = if (store.data.onboarded) store.doneToday() else 0
        val d = density(context)
        val header = WidgetArt.wallpaper(context, "bw07", (420 * d).toInt(), (44 * d).toInt(), 20 * d, d, roundBottom = false)
        val state = TodayState(open.take(8), done, done + open.size, store.data.trainerName)
        provideContent { TodayContent(context, state, header) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(context: Context, state: TodayState, header: Bitmap?) {
    Column(GlanceModifier.fillMaxSize().background(paper).cornerRadius(20.dp)) {
        Box(GlanceModifier.fillMaxWidth().height(44.dp).clickable(openApp(context, "PLANNER"))) {
            if (header != null) Image(ImageProvider(header), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(GlanceModifier.fillMaxSize().background(red)) {}
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today", GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 4.dp),
                    style = TextStyle(color = ColorProvider(red), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (state.total > 0) Text(
                    "${state.done} of ${state.total} done", GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp),
                    style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = state.done.toFloat() / state.total,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = ColorProvider(green), backgroundColor = ColorProvider(shade),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            if (state.tasks.isEmpty()) {
                Text(
                    when {
                        state.name.isEmpty() -> "Open PokéPlan to start"
                        state.done > 0 -> "All done for today. Rest, or start a focus session."
                        else -> "Nothing planned. Tap to add one small thing."
                    },
                    GlanceModifier.clickable(openApp(context, "PLANNER")),
                    style = TextStyle(color = ColorProvider(soft), fontSize = 14.sp),
                )
            } else {
                LazyColumn {
                    items(state.tasks, itemId = { it.id.hashCode().toLong() }) { t ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(26.dp).background(rankColor(t.rank)).cornerRadius(13.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    t.rank.letter.toString(),
                                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                            Spacer(GlanceModifier.width(10.dp))
                            Text(
                                t.title, GlanceModifier.defaultWeight().clickable(openApp(context, "PLANNER")),
                                style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp), maxLines = 2,
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Box(
                                GlanceModifier.size(36.dp).background(green).cornerRadius(18.dp)
                                    .semantics { contentDescription = "Finish ${t.title}" }
                                    .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold)) }
                        }
                    }
                }
            }
        }
    }
}

// ----- Buddy ----------------------------------------------------------------------------------------------------------

/** What the buddy just said after a tap on the widget, kept for a few seconds so the widget can show it. */
private object BuddyTalk {
    private const val FILE = "buddy_widget"
    private const val SHOWN_MILLIS = 4_000L

    fun say(context: Context, text: String, petted: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("say", text).putBoolean("petted", petted).putLong("until", System.currentTimeMillis() + SHOWN_MILLIS).apply()
    }

    /** The line to show now and whether the buddy is still in its petted pose, or null once the moment has passed. */
    fun current(context: Context): Pair<String, Boolean>? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val text = prefs.getString("say", null) ?: return null
        if (System.currentTimeMillis() > prefs.getLong("until", 0)) return null
        return text to prefs.getBoolean("petted", false)
    }

    suspend fun settleLater(context: Context) {
        delay(SHOWN_MILLIS + 300)
        updateWidgets(context)
    }
}

private fun berryOf(store: GameStore): String? =
    store.data.player.inventory.counts.entries.firstOrNull { (id, n) -> n > 0 && Items.category(id) == ItemCategory.BERRY }?.key

/** A tap on the buddy or the Pet button: the same petting as in the app, with a hop, hearts and a line. */
class PetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = widgetStore(context)
        store.pet()
        // The callback stays alive for the speech bubble's few seconds (settleLater), long enough for the cry to play out.
        store.data.player.buddy?.takeIf { store.data.settings.sound }?.let { AndroidCryPlayer(context).play(it.speciesId) }
        val full = (store.data.player.buddy?.friendship ?: 0) >= FriendshipRules.MAX
        BuddyTalk.say(context, if (full) BuddyLines.FRIENDSHIP_FULL else BuddyLines.pet(), petted = true)
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    }
}

/** The berry button: gives the buddy a berry from the bag. */
class FeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy ?: return
        val berry = berryOf(store) ?: return
        store.useItem(berry, buddy.id)
        BuddyTalk.say(context, BuddyLines.feed(), petted = true)
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    }
}

private data class BuddyState(
    val name: String?, val level: Int?, val friendship: Int, val streak: Int, val line: String,
    val berryName: String?, val stage: Bitmap?, val wallpaper: Bitmap?, val berryIcon: Bitmap?, val heart: Bitmap,
)

class BuddyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy
        val talk = BuddyTalk.current(context)
        val berry = berryOf(store)
        val d = density(context)
        val state = BuddyState(
            name = buddy?.nickname, level = buddy?.level, friendship = buddy?.friendship ?: 0, streak = store.streak.current,
            line = talk?.first ?: if (buddy == null) "Open PokéPlan to start" else store.buddyMood().line,
            berryName = berry?.let { Items.get(it)?.name },
            stage = buddy?.let { WidgetArt.buddyStage(context, it.form.id, it.isShiny, petted = talk?.second == true) },
            wallpaper = WidgetArt.wallpaper(context, "bw01", (420 * d).toInt(), (200 * d).toInt(), 20 * d, d),
            berryIcon = berry?.let { WidgetArt.item(context, it, 2) },
            heart = WidgetArt.heart(40),
        )
        provideContent { BuddyContent(context, state) }
    }
}

class BuddyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BuddyWidget()
}

@Composable
private fun BuddyContent(context: Context, s: BuddyState) {
    val wide = LocalSize.current.width >= 240.dp
    val pet = actionRunCallback<PetAction>()
    Box(GlanceModifier.fillMaxSize().cornerRadius(20.dp).clickable(openApp(context))) {
        if (s.wallpaper != null) Image(ImageProvider(s.wallpaper), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(GlanceModifier.fillMaxSize().background(Color(0xFF8FCB7F))) {}
        if (s.name == null || s.stage == null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.line, GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 8.dp), style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp))
            }
            return@Box
        }
        if (wide) {
            Row(GlanceModifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(s.stage), contentDescription = "Pet ${s.name}",
                    modifier = GlanceModifier.fillMaxHeight().width(120.dp).clickable(pet), contentScale = ContentScale.Fit,
                )
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.line, GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 5.dp),
                        style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp), maxLines = 2,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Text("  Lv. ${s.level}", style = TextStyle(color = ColorProvider(soft), fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            GlanceModifier.pill().padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics { contentDescription = "Friendship ${s.friendship} of ${FriendshipRules.MAX}" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(ImageProvider(s.heart), contentDescription = null, modifier = GlanceModifier.size(14.dp))
                            Text(" ${s.friendship}", style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp))
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            "Streak ${s.streak}", GlanceModifier.pill().padding(horizontal = 8.dp, vertical = 4.dp),
                            style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        if (s.berryIcon != null) Box(
                            GlanceModifier.size(40.dp).pill().semantics { contentDescription = "Give ${s.name} a ${s.berryName ?: "berry"}" }
                                .clickable(actionRunCallback<FeedAction>()),
                            contentAlignment = Alignment.Center,
                        ) { Image(ImageProvider(s.berryIcon), contentDescription = null, modifier = GlanceModifier.size(30.dp)) }
                    }
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    ImageProvider(s.stage), contentDescription = "Pet ${s.name}",
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth().clickable(pet), contentScale = ContentScale.Fit,
                )
                Row(GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text("  Lv. ${s.level}", style = TextStyle(color = ColorProvider(soft), fontSize = 11.sp))
                }
            }
        }
    }
}

// ----- Start focus ----------------------------------------------------------------------------------------------------

private data class FocusState(val running: Boolean, val title: String, val detail: String, val wallpaper: Bitmap?, val egg: Bitmap?)

class FocusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 55.dp), DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val session = store.data.focus
        val d = density(context)
        val state = if (session != null && !session.isFinished) {
            // The session ends this long after it started, plus time spent paused; shown as a clock time so it never goes stale.
            val end = session.startedAtMillis + session.durationMillis + session.pausedMillis
            val paused = session.phase is Phase.Paused
            FocusState(
                running = true,
                title = if (paused) "Paused" else "Focusing",
                detail = if (paused) "Open the app to continue" else "Egg hatches around ${clock(end)}",
                wallpaper = WidgetArt.wallpaper(context, "bw11", (420 * d).toInt(), (200 * d).toInt(), 20 * d, d),
                egg = WidgetArt.item(context, "mystery-egg", 3),
            )
        } else {
            FocusState(
                running = false, title = "Start focus", detail = "${store.data.settings.defaultFocusMinutes} min, hatch an egg",
                wallpaper = WidgetArt.wallpaper(context, "bw11", (420 * d).toInt(), (200 * d).toInt(), 20 * d, d),
                egg = WidgetArt.item(context, "mystery-egg", 3),
            )
        }
        provideContent { FocusContent(context, state) }
    }

    private fun clock(millis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

@Composable
private fun FocusContent(context: Context, s: FocusState) {
    val tall = LocalSize.current.height >= 100.dp
    Box(GlanceModifier.fillMaxSize().cornerRadius(20.dp).clickable(openApp(context, "FOCUS"))) {
        if (s.wallpaper != null) Image(ImageProvider(s.wallpaper), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(GlanceModifier.fillMaxSize().background(red)) {}
        Row(GlanceModifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.egg != null && tall) {
                Image(ImageProvider(s.egg), contentDescription = null, modifier = GlanceModifier.size(64.dp))
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title, GlanceModifier.background(red).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    s.detail, GlanceModifier.pill().padding(horizontal = 8.dp, vertical = 3.dp),
                    style = TextStyle(color = ColorProvider(ink), fontSize = 11.sp), maxLines = 2,
                )
            }
        }
    }
}
