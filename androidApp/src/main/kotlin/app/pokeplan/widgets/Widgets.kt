package app.pokeplan.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentSize
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
import app.pokeplan.care.Routine
import app.pokeplan.core.pokemon.Items
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.ui.Place
import app.pokeplan.ui.screens.clock
import app.pokeplan.careStore
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
private val green = Color(0xFF1E7F52)

// Widget text uses two sizes: 15 sp for a widget's title and the tick, 13 sp for everything else (the 28 sp emoji is a picture).

private fun rankColor(rank: Rank) = when (rank) {
    Rank.E -> Color(0xFF666B7E)
    Rank.D -> Color(0xFF1E7F52)
    Rank.C -> Color(0xFF2C6CCC)
    Rank.B -> Color(0xFF946000)
    Rank.A -> Color(0xFFB42332)
}

private fun openApp(context: Context, tab: String? = null, pokemon: String? = null, addTask: Boolean = false) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        tab?.let { putExtra(MainActivity.EXTRA_TAB, it) }
        pokemon?.let { putExtra(MainActivity.EXTRA_POKEMON, it) }
        if (addTask) putExtra(MainActivity.EXTRA_ADD_TASK, true)
    },
)

/** The 44 dp strip on top of the Today and Care widgets: the header picture, or a flat [fallback] colour without one, with [content] laid over it. */
@Composable
private fun WidgetHeader(header: Bitmap?, fallback: Color, onClick: Action, content: @Composable RowScope.() -> Unit) {
    Box(GlanceModifier.fillMaxWidth().height(44.dp).clickable(onClick)) {
        if (header != null) Image(ImageProvider(header), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(GlanceModifier.fillMaxSize().background(fallback)) {}
        Row(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** The round tick at the end of a task or routine row: green and tappable, or grey and inert once [done]. */
@Composable
private fun TickButton(description: String, done: Boolean = false, onClick: Action? = null) {
    val button = GlanceModifier.size(36.dp).background(if (done) shade else green).cornerRadius(18.dp).semantics { contentDescription = description }
    Box(if (onClick != null) button.clickable(onClick) else button, contentAlignment = Alignment.Center) {
        Text("✓", style = TextStyle(color = ColorProvider(if (done) green else Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

/** Brings every widget up to date; called when the app is left and after a tap on a widget. */
suspend fun updateWidgets(context: Context) {
    for (widget in listOf(TodayWidget(), BuddyWidget(), FocusWidget(), CareWidget())) {
        val stamp = System.currentTimeMillis()
        for (id in GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass)) {
            updateAppWidgetState(context, id) { it[RefreshKey] = stamp }
        }
        widget.updateAll(context)
    }
}

private val RefreshKey = longPreferencesKey("refresh")

/**
 * Shows what [load] gives and loads it again on every [updateWidgets]. Glance runs provideGlance once per session (about
 * 45 seconds); an update to a widget whose session is still open only recomposes. Values worked out in provideGlance then
 * never change, which froze the buddy on its still picture after a pet. So [updateWidgets] stamps each widget's Glance
 * state, and a new stamp loads the data again inside the composition.
 */
private suspend fun <T> GlanceAppWidget.provideFresh(context: Context, id: GlanceId, load: suspend () -> T, content: @Composable (T) -> Unit): Nothing {
    val first = load()
    val firstStamp = getAppWidgetState<Preferences>(context, id)[RefreshKey]
    provideContent {
        val stamp = currentState(RefreshKey)
        var value by remember { mutableStateOf(first) }
        LaunchedEffect(stamp) { if (stamp != firstStamp) value = load() }
        content(value)
    }
}

private val TaskIdKey = ActionParameters.Key<String>("taskId")

private fun density(context: Context) = context.resources.displayMetrics.density

/** The buddy's scene (Plan's town, dimmed at dusk) as a [heightDp] strip for the other widgets, or null before there is a buddy. */
private fun buddyScene(context: Context, store: GameStore, heightDp: Int, roundBottom: Boolean = true): Bitmap? {
    val buddy = store.data.player.buddy ?: return null
    val d = density(context)
    return WidgetArt.scene(
        context, BuddyScene.PLAN, BuddyScene.darkness(store.now.hour, store.now.minute),
        (420 * d).toInt(), (heightDp * d).toInt(), 20 * d, roundBottom,
    )
}

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
    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideFresh(context, id, { load(context) }) { (state, header) -> TodayContent(context, state, header) }

    private suspend fun load(context: Context): Pair<TodayState, Bitmap?> {
        val store = widgetStore(context)
        val open = if (store.data.onboarded) store.openTodayTasks() else emptyList()
        val done = if (store.data.onboarded) store.doneToday() else 0
        val d = density(context)
        val header = buddyScene(context, store, 44, roundBottom = false)
            ?: WidgetArt.wallpaper(context, "bw07", (420 * d).toInt(), (44 * d).toInt(), 20 * d, d, roundBottom = false)
        return TodayState(open.take(8), done, done + open.size, store.data.trainerName) to header
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(context: Context, state: TodayState, header: Bitmap?) {
    Column(GlanceModifier.fillMaxSize().background(paper).cornerRadius(20.dp)) {
        WidgetHeader(header, red, openApp(context, Place.PLANNER.name)) {
            Text(
                "Today", GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 4.dp),
                style = TextStyle(color = ColorProvider(red), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "+ Add", GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "Add a task" }
                    .clickable(rippleOverride = R.drawable.no_ripple, onClick = openApp(context, Place.PLANNER.name, addTask = true)),
                style = TextStyle(color = ColorProvider(green), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            if (state.total > 0) Text(
                "${state.done} of ${state.total} done", GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
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
                        state.name.isEmpty() -> "Open PokéLife to start"
                        state.done > 0 -> "All done for today"
                        else -> "Nothing planned. Tap to add a task."
                    },
                    GlanceModifier.clickable(openApp(context, Place.PLANNER.name)),
                    style = TextStyle(color = ColorProvider(soft), fontSize = 13.sp),
                )
            } else {
                LazyColumn {
                    items(state.tasks, itemId = { it.id.hashCode().toLong() }) { t ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(26.dp).background(rankColor(t.rank)).cornerRadius(13.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    t.rank.letter.toString(),
                                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                            Spacer(GlanceModifier.width(10.dp))
                            Text(
                                t.title, GlanceModifier.defaultWeight().clickable(openApp(context, Place.PLANNER.name)),
                                style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp), maxLines = 2,
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            TickButton("Finish ${t.title}", onClick = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to t.id)))
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

    /** The last reaction shown, so a pet never repeats it straight away. */
    fun last(context: Context): String? = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("emoji", null)

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

    /** Milliseconds until the current reaction ends, 0 if none. */
    fun remaining(context: Context): Long =
        (context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("until", 0) - System.currentTimeMillis()).coerceAtLeast(0)

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


/** A tap on the buddy: the same petting as in the app, with a heart and the cry. */
class PetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = widgetStore(context)
        store.pet()
        if (store.data.settings.haptics) app.pokeplan.AndroidHaptics(context).tick()
        // The callback stays alive while the hearts show (settleLater), long enough for the cry to play out.
        store.data.player.buddy?.takeIf { store.data.settings.effectsLevel > 0f }?.let { AndroidCryPlayer(context).play(it.speciesId, store.data.settings.effectsLevel) }
        BuddyTalk.show(context, store.data.player.buddy?.let { BuddyReaction.pet(it, store.now.hour, avoid = BuddyTalk.last(context)) } ?: "❤️")
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    }
}

/** The berry button: gives the buddy a berry from the bag. */
class FeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy ?: return
        val berry = store.buddyBerry() ?: return
        store.useItem(berry, buddy.id)
        BuddyTalk.show(context, BuddyReaction.feed(buddy, avoid = BuddyTalk.last(context)))
        updateWidgets(context)
        BuddyTalk.settleLater(context)
    }
}

private data class BuddyState(
    val name: String?, val buddyId: String?, val level: Int, val progress: Float, val reaction: String?, val description: String,
    val scene: Bitmap?, val still: Bitmap?, val idle: RemoteViews?, val berryName: String?, val berryIcon: Bitmap?,
)

class BuddyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideFresh(context, id, { load(context) }) { BuddyContent(context, it) }

    private suspend fun load(context: Context): BuddyState {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy
        val moment = BuddyTalk.current(context)
        // An alarm refreshes the widget when the reaction ends, to take the emoji away.
        if (moment != null) scheduleWidgetRefresh(context, BuddyTalk.remaining(context) + 300)
        val berry = store.buddyBerry()
        val d = density(context)
        val darkness = BuddyScene.darkness(store.now.hour, store.now.minute)
        val focusing = store.data.focus != null
        // The idle animation plays by itself in a flipper and keeps playing under a reaction. During a focus session the
        // buddy sleeps (its SpriteCollab Sleep animation; one still picture for a form without it); Reduce motion shows
        // one still picture.
        val sleep = if (buddy != null && focusing) WidgetArt.sleepFrames(context, buddy.form.id, buddy.isShiny) else null
        val animated = when {
            buddy == null || store.data.settings.reduceMotion -> null
            focusing -> sleep
            else -> WidgetArt.idleFrames(context, buddy.form.id, buddy.isShiny)
        }
        val idle = animated?.let { frames ->
            RemoteViews(context.packageName, R.layout.widget_sprite).also { flipper ->
                frames.forEach { bitmap ->
                    flipper.addView(R.id.sprite_flipper, RemoteViews(context.packageName, R.layout.widget_sprite_frame).also { it.setImageViewBitmap(R.id.sprite_frame, bitmap) })
                }
                flipper.setContentDescription(R.id.sprite_flipper, "${buddy?.nickname}, level ${buddy?.level}")
            }
        }
        return BuddyState(
            name = buddy?.nickname, buddyId = buddy?.id, level = buddy?.level ?: 1, progress = buddy?.let { store.levelProgress(it) } ?: 0f,
            reaction = moment, description = "${buddy?.nickname}, level ${buddy?.level}",
            scene = buddy?.let { WidgetArt.scene(context, BuddyScene.PLAN, darkness, (420 * d).toInt(), (200 * d).toInt(), 20 * d) },
            still = if (idle == null) buddy?.let { sleep?.first() ?: WidgetArt.still(context, it.form.id, it.isShiny) } else null,
            idle = idle, berryName = berry?.let { Items.get(it)?.name }, berryIcon = berry?.let { WidgetArt.item(context, it, 2) },
        )
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
                Text("Open PokéLife to start", GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 8.dp), style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp))
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
                GlanceModifier.background(Color(0x66000000)).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 5.dp)
                    .clickable(rippleOverride = R.drawable.no_ripple, onClick = openApp(context, "COLLECTION", s.buddyId))
                    .semantics { contentDescription = "${s.name}, level ${s.level}, ${(s.progress * 100).toInt()} percent to the next level" },
            ) {
                Text("${s.name}  Lv. ${s.level}", style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium))
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

private data class FocusState(
    val running: Boolean, val title: String, val detail: String, val wallpaper: Bitmap?, val egg: Bitmap?,
    /** A ticking countdown to the end of the session, built as a Chronometer so the system keeps it right. */
    val countdown: RemoteViews? = null,
)

class FocusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 55.dp), DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideFresh(context, id, { load(context) }) { FocusContent(context, it) }

    private suspend fun load(context: Context): FocusState {
        val store = widgetStore(context)
        val session = store.data.focus
        val backdrop = buddyScene(context, store, 200)
            ?: WidgetArt.wallpaper(context, "bw11", (420 * density(context)).toInt(), (200 * density(context)).toInt(), 20 * density(context), density(context))
        return if (session != null && !session.isFinished) {
            // The session ends this long after it started, plus time spent paused; shown as a clock time so it never goes stale.
            val end = session.startedAtMillis + session.durationMillis + session.pausedMillis
            val paused = session.phase is Phase.Paused
            FocusState(
                running = true,
                title = if (paused) "Paused" else "Focusing",
                detail = if (paused) "Open the app to continue" else "Egg hatches around ${clock(end)}",
                countdown = if (paused) null else RemoteViews(context.packageName, R.layout.widget_countdown).also {
                    it.setChronometerCountDown(R.id.countdown, true)
                    it.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + (end - store.nowMillis).coerceAtLeast(0), null, true)
                },
                wallpaper = backdrop,
                egg = WidgetArt.item(context, "mystery-egg", 3),
            )
        } else {
            FocusState(
                running = false, title = "Start focus", detail = "${store.data.settings.defaultFocusMinutes} min, hatch an egg",
                wallpaper = backdrop,
                egg = WidgetArt.item(context, "mystery-egg", 3),
            )
        }
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
    Box(GlanceModifier.fillMaxSize().cornerRadius(20.dp).clickable(openApp(context, Place.FOCUS.name))) {
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
                if (s.countdown != null) AndroidRemoteViews(remoteViews = s.countdown, modifier = GlanceModifier.wrapContentSize())
                Text(
                    s.detail, GlanceModifier.pill().padding(horizontal = 8.dp, vertical = 3.dp),
                    style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp), maxLines = 2,
                )
            }
        }
    }
}

// ----- Care: today's routines -----------------------------------------------------------------------------------------

private val RoutineIdKey = ActionParameters.Key<String>("routineId")

/** A tap on a routine's tick: ticks it off for today and pays it, exactly as on the Care screen (into the shared save). */
class CompleteRoutineAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[RoutineIdKey] ?: return
        val care = careStore(context)
        if (care.todayRoutines().any { it.id == id && !it.doneOn(care.today) }) care.completeRoutine(id)
        updateWidgets(context)
    }
}

private data class CareState(val on: Boolean, val started: Boolean, val routines: List<Routine>, val done: Int, val diarySaved: Boolean, val today: kotlinx.datetime.LocalDate)

/** Care's routines for today with a tick, and whether today's diary card is saved (what the launcher's home page will show too). */
class CareWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideFresh(context, id, { load(context) }) { (state, header) -> CareContent(context, state, header) }

    private suspend fun load(context: Context): Pair<CareState, Bitmap?> {
        val game = widgetStore(context)
        val care = careStore(context, game)
        val today = care.todayRoutines()
        val d = density(context)
        val dark = BuddyScene.darkness(game.now.hour, game.now.minute)
        val header = WidgetArt.scene(context, BuddyScene.CARE, dark, (420 * d).toInt(), (44 * d).toInt(), 20 * d, roundBottom = false)
        val state = CareState(
            on = game.data.settings.careModule, started = game.data.onboarded,
            // Not done first, so the ticks stay at the top; then the ones done today.
            routines = today.sortedBy { it.doneOn(care.today) }.take(8),
            done = today.count { it.doneOn(care.today) }, diarySaved = care.diaryToday() != null, today = care.today,
        )
        return state to header
    }
}

class CareWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CareWidget()
}

@Composable
private fun CareContent(context: Context, state: CareState, header: Bitmap?) {
    val open = openApp(context, Place.CARE.name)
    Column(GlanceModifier.fillMaxSize().background(paper).cornerRadius(20.dp)) {
        WidgetHeader(header, green, open) {
            Text(
                "Care", GlanceModifier.pill().padding(horizontal = 12.dp, vertical = 4.dp),
                style = TextStyle(color = ColorProvider(green), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (state.on && state.routines.isNotEmpty()) Text(
                "${state.done} of ${state.routines.size} done", GlanceModifier.pill().padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!state.on || !state.started) {
                Text(
                    if (!state.started) "Open PokéLife to start" else "Care is switched off. Turn it on in Me, Settings, Modules.",
                    GlanceModifier.clickable(openApp(context, if (state.started) Place.ME.name else null)),
                    style = TextStyle(color = ColorProvider(soft), fontSize = 13.sp),
                )
                return@Column
            }
            Text(
                if (state.diarySaved) "Diary card saved today" else "Diary card: how did today feel?",
                GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).clickable(open),
                style = TextStyle(color = ColorProvider(soft), fontSize = 13.sp),
            )
            if (state.routines.isEmpty()) {
                Text("No routines today. Tap to add one.", GlanceModifier.clickable(open), style = TextStyle(color = ColorProvider(soft), fontSize = 13.sp))
            } else LazyColumn {
                items(state.routines, itemId = { it.id.hashCode().toLong() }) { r ->
                    val done = r.doneOn(state.today)
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = r.icon?.let { WidgetArt.item(context, it, 1) }
                        if (icon != null) {
                            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(26.dp))
                            Spacer(GlanceModifier.width(10.dp))
                        }
                        Text(
                            r.title + (r.time?.let { "  ${clock(it)}" } ?: ""),
                            GlanceModifier.defaultWeight().clickable(open),
                            style = TextStyle(color = ColorProvider(if (done) soft else ink), fontSize = 13.sp), maxLines = 2,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        if (done) TickButton("${r.title}, done today", done = true)
                        else TickButton("Done: ${r.title}", onClick = actionRunCallback<CompleteRoutineAction>(actionParametersOf(RoutineIdKey to r.id)))
                    }
                }
            }
        }
    }
}
