package app.pokeplan.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pokeplan.MainActivity
import app.pokeplan.widgetStore
import app.pokeplan.plan.Task

private val red = Color(0xFFE8412F)
private val ink = Color(0xFF1C1F2B)
private val soft = Color(0xFF6A7088)
private val white = Color(0xFFFFFFFF)
private val green = Color(0xFF2FBF71)

private fun openApp(context: Context, tab: String? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply { tab?.let { putExtra(MainActivity.EXTRA_TAB, it) } },
)

private val TaskIdKey = ActionParameters.Key<String>("taskId")

/** A tap on a task's tick: finishes it. The reward is granted the next time the app opens (the undo window has passed by then). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val store = widgetStore(context)
        if (store.data.tasks.any { it.id == id && it.isOpen }) store.completeTask(id)
        TodayWidget().updateAll(context)
        BuddyWidget().updateAll(context)
    }
}

// ----- Today's tasks --------------------------------------------------------------------------------------------------

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val pending = store.data.pending.map { it.original.id }.toSet()
        val tasks = if (store.data.onboarded) store.todayTasks().filter { it.id !in pending }.take(8) else emptyList()
        provideContent { TodayContent(context, tasks, store.data.trainerName) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(context: Context, tasks: List<Task>, name: String) {
    Column(GlanceModifier.fillMaxSize().background(white).cornerRadius(20.dp).padding(12.dp).clickable(openApp(context, "PLANNER"))) {
        Text("Today", style = TextStyle(color = ColorProvider(red), fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) {
            Text(
                if (name.isEmpty()) "Open PokéPlan to start" else "Nothing planned. Enjoy the calm.",
                style = TextStyle(color = ColorProvider(soft), fontSize = 14.sp),
            )
        } else {
            LazyColumn {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { t ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.size(28.dp).background(green).cornerRadius(14.dp)
                                .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓", style = TextStyle(color = ColorProvider(white), fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                        Spacer(GlanceModifier.width(10.dp))
                        Text(t.title, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp), maxLines = 2)
                    }
                }
            }
        }
    }
}

// ----- Buddy and streak -----------------------------------------------------------------------------------------------

class BuddyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = widgetStore(context)
        val buddy = store.data.player.buddy
        val sprite = buddy?.let { spriteBitmap(context, it.form.id, it.isShiny) }
        val streak = store.streak.current
        provideContent { BuddyContent(context, buddy?.nickname, buddy?.level, sprite, streak) }
    }
}

class BuddyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BuddyWidget()
}

@Composable
private fun BuddyContent(context: Context, name: String?, level: Int?, sprite: Bitmap?, streak: Int) {
    Column(
        GlanceModifier.fillMaxSize().background(white).cornerRadius(20.dp).padding(8.dp).clickable(openApp(context)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sprite != null) Image(ImageProvider(sprite), contentDescription = name, modifier = GlanceModifier.size(88.dp))
        Text(name ?: "PokéPlan", style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        if (level != null) Text("Lv. $level  ·  streak $streak", style = TextStyle(color = ColorProvider(soft), fontSize = 12.sp))
    }
}

// ----- Start focus ----------------------------------------------------------------------------------------------------

class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                GlanceModifier.fillMaxSize().background(red).cornerRadius(20.dp).clickable(openApp(context, "FOCUS")),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Start focus", style = TextStyle(color = ColorProvider(white), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text("Hatch an egg", style = TextStyle(color = ColorProvider(white), fontSize = 12.sp))
                }
            }
        }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

/**
 * The real sprite for a form, read from the app's resources and enlarged by a whole number without smoothing, so the
 * pixels stay crisp in the widget.
 */
private fun spriteBitmap(context: Context, formId: Int, shiny: Boolean): Bitmap? {
    val name = if (shiny) "$formId-shiny" else "$formId"
    return try {
        context.assets.open("composeResources/pokeplan.composeapp.generated.resources/files/sprites/$name.png").use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream)
            Bitmap.createScaledBitmap(bitmap, bitmap.width * 3, bitmap.height * 3, false)
        }
    } catch (e: Exception) {
        null
    }
}
