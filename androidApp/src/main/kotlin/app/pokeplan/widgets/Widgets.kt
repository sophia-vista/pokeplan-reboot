package app.pokeplan.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
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
import androidx.glance.appwidget.AndroidRemoteViews
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
import app.pokeplan.R
import app.pokeplan.MainActivity
import app.pokeplan.app.BuddyReaction
import app.pokeplan.app.BuddyScene
import app.pokeplan.app.GameStore
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

/**
 * The buddy's reaction after a tap or an event, kept for a few seconds so the widget can show it: one emoji above its
 * head, like a game emote. Outside these moments the buddy only idles.
 */
private object BuddyTalk {
    private const val FILE = "buddy_widget"
    private const val SHOWN_MILLIS = 4_000L

    fun show(context: Context, emoji: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("emoji", emoji).putLong("until", System.currentTimeMillis() + SHOWN_MILLIS).apply()
    }

    /** The emoji to show now, or null once the moment has passed. */
    fun current(context: Context): String? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() > prefs.getLong("until", 0)) return null
        return prefs.getString("emoji", null)
    }

    /** The last event the widget reacted to, so one event is reacted to once. */
    fun celebrated(context: Context): Long = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("celebrated", 0)
    fun celebrate(context: Context, at: Long) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("celebrated", at).apply()

    suspend fun settleLater(context: Context) {
        delay(SHOWN_MILLIS + 300)
        updateWidgets(context)
    }
}

/** Called when the app is left: an event that just happened (an egg hatched, a payout) gets a few seconds of reaction. */
suspend fun announceBuddyEvent(context: Context) {
    val store = widgetStore(context)
    val event = store.buddyEvent()
    if (event != null && event.atMillis > BuddyTalk.celebrated(context) && !store.data.settings.reduceMotion) {
        BuddyTalk.celebrate(context, event.atMillis)
        BuddyTalk.show(context, event.event.emoji)
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    } else {
        updateWidgets(context)
    }
}

private fun berryOf(store: GameStore): String? =
    store.data.player.inventory.counts.entries.firstOrNull { (id, n) -> n > 0 && Items.category(id) == ItemCategory.BERRY }?.key

/** A tap on the buddy: the same petting as in the app, with a heart and the cry. */
class PetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = widgetStore(context)
        store.pet()
        // The callback stays alive while the hearts show (settleLater), long enough for the cry to play out.
        store.data.player.buddy?.takeIf { store.data.settings.sound }?.let { AndroidCryPlayer(context).play(it.speciesId) }
        BuddyTalk.show(context, BuddyReaction.PET)
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
        BuddyTalk.show(context, BuddyReaction.FEED)
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    }
}

private data class BuddyState(
    val name: String?, val level: Int, val progress: Float, val reaction: String?, val description: String,
    val scene: Bitmap?, val still: Bitmap?, val idle: RemoteViews?, val berryName: String?, val berryIcon: Bitmap?,
)

class BuddyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy
        val moment = BuddyTalk.current(context)
        val berry = berryOf(store)
        val d = density(context)
        val night = BuddyScene.isNight(store.now.hour)
        val calm = store.data.settings.reduceMotion || store.data.focus != null
        // The idle animation plays by itself in a flipper; a pet or an event swaps in one still picture with its effect.
        val animated = if (buddy != null && moment == null && !calm) WidgetArt.idleFrames(context, buddy.form.id, buddy.isShiny) else null
        val idle = animated?.let { frames ->
            RemoteViews(context.packageName, R.layout.widget_sprite).also { flipper ->
                frames.forEach { bitmap ->
                    flipper.addView(R.id.sprite_flipper, RemoteViews(context.packageName, R.layout.widget_sprite_frame).also { it.setImageViewBitmap(R.id.sprite_frame, bitmap) })
                }
                flipper.setContentDescription(R.id.sprite_flipper, "${buddy?.nickname}, level ${buddy?.level}")
            }
        }
        val state = BuddyState(
            name = buddy?.nickname, level = buddy?.level ?: 1, progress = buddy?.let { store.levelProgress(it) } ?: 0f,
            reaction = moment, description = "${buddy?.nickname}, level ${buddy?.level}",
            scene = buddy?.let { WidgetArt.scene(context, BuddyScene.of(it.form.types), night, (420 * d).toInt(), (200 * d).toInt(), 20 * d) },
            still = if (idle == null) buddy?.let { WidgetArt.still(context, it.form.id, it.isShiny, lifted = moment != null) } else null,
            idle = idle, berryName = berry?.let { Items.get(it)?.name }, berryIcon = berry?.let { WidgetArt.item(context, it, 2) },
        )
        provideContent { BuddyContent(context, state) }
    }
}

class BuddyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BuddyWidget()
}

/** The buddy in its scene. Nothing else is loud: a small level chip in the corner, a berry if there is one, and an emoji reaction only for a real moment. */
@Composable
private fun BuddyContent(context: Context, s: BuddyState) {
    val height = LocalSize.current.height
    val box = (height.value * 0.72f).dp
    Box(GlanceModifier.fillMaxSize().cornerRadius(20.dp).clickable(rippleOverride = R.drawable.no_ripple, onClick = openApp(context))) {
        if (s.scene != null) Image(ImageProvider(s.scene), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(GlanceModifier.fillMaxSize().background(Color(0xFF8FCB7F))) {}
        if (s.name == null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Open PokéPlan to start", GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 8.dp), style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp))
            }
            return@Box
        }
        Box(GlanceModifier.fillMaxSize().padding(bottom = 6.dp), contentAlignment = Alignment.BottomCenter) {
            Box(GlanceModifier.size(box).clickable(rippleOverride = R.drawable.no_ripple, onClick = actionRunCallback<PetAction>())) {
                if (s.idle != null) AndroidRemoteViews(remoteViews = s.idle, modifier = GlanceModifier.fillMaxSize())
                else if (s.still != null) Image(ImageProvider(s.still), contentDescription = s.description, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        // Level and progress: small, bottom left.
        Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
            Column(
                GlanceModifier.background(Color(0x66000000)).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 5.dp)
                    .semantics { contentDescription = "${s.name}, level ${s.level}, ${(s.progress * 100).toInt()} percent to the next level" },
            ) {
                Text("${s.name}  Lv. ${s.level}", style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = s.progress, modifier = GlanceModifier.width(72.dp).height(3.dp),
                    color = ColorProvider(Color(0xFFFFCB05)), backgroundColor = ColorProvider(Color(0x55FFFFFF)),
                )
            }
        }
        if (s.berryIcon != null) Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomEnd) {
            Box(
                GlanceModifier.size(40.dp).background(Color(0xCCFFFFFF)).cornerRadius(20.dp)
                    .semantics { contentDescription = "Give ${s.name} a ${s.berryName ?: "berry"}" }.clickable(rippleOverride = R.drawable.no_ripple, onClick = actionRunCallback<FeedAction>()),
                contentAlignment = Alignment.Center,
            ) { Image(ImageProvider(s.berryIcon), contentDescription = null, modifier = GlanceModifier.size(28.dp)) }
        }
        // The reaction: one emoji above the buddy, only for a moment after a pet, a berry or an event.
        if (s.reaction != null) Box(GlanceModifier.fillMaxSize().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
            Text(s.reaction, style = TextStyle(fontSize = 28.sp))
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
