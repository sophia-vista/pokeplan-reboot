package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
/**
 * The way to plan a task on the calendar without dragging (Plan usability, Phase 3): tap the task in "To plan", then tap an hour.
 * Done with real pointer events. The drag stays, for anyone who likes it; this is for anyone who cannot or would rather not.
 */
class DayPlanTapTest {
    @Test
    fun tapATaskThenAnHour() {
        val clock = object : Clock { override fun nowMillis() = 1_760_000_000_000L }
        val store = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25); it.markRewardsSeen() }
        store.addTask("Reply to mail", app.pokeplan.plan.Rank.E, planned = store.today)
        val model = AppModel(store)
        val scene = ImageComposeScene(width = 540, height = 2400, density = Density(1.5f, 1f)) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.plan.PlanView(model, app.pokeplan.ui.screens.PlanSection.CALENDAR, {}, {}, {}) } }
        }
        var nanos = 0L
        fun settle(n: Int = 30) = repeat(n) { scene.render(nanos); nanos += 50_000_000; Thread.sleep(8) }
        fun shot(name: String) = File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        fun tap(x: Float, y: Float) { scene.sendPointerEvent(PointerEventType.Press, Offset(x, y)); settle(2); scene.sendPointerEvent(PointerEventType.Release, Offset(x, y)); settle() }
        settle()
        shot("daytap-0")
        tap(180f, 604f)
        shot("daytap-1")
        // With a task picked, its panel sits above "To plan" (with its how-to line, shown until a task has a time) and the hours start
        // under both: 8:00 is at about y 1046 and every hour is 96 px, so 14:00 starts at about 1622.
        tap(300f, 1640f)
        shot("daytap-2")
        scene.close()
        val picked = store.data.tasks.first { it.title == "Finish your first task" }
        kotlin.test.assertEquals(14, picked.plannedTime?.hour, "tapping the task, then the 14:00 hour, plans it for the afternoon (no dragging)")
        kotlin.test.assertEquals(store.today, picked.planned)
    }
}
