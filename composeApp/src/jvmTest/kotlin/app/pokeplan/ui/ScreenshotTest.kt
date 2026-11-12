package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import app.pokeplan.ui.launcher.limits
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.pokeplan.ui.theme.Spacing
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.MemoryStorage
import app.pokeplan.plan.Rank
import app.pokeplan.ui.screens.PlannerScreen
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import io.ktor.client.engine.mock.respond
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlin.test.assertTrue

/**
 * Draws screens offscreen and writes PNGs to composeApp/build/screenshots, so they can be looked at without a device.
 * The assertion only checks that something was drawn.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private fun render(name: String, frames: Int = 60, fontScale: Float = 1f, height: Int = 1080, width: Int = 540, density: Float = 1.5f, minBytes: Int = 5_000, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(density, fontScale)) { content() }
        var nanos = 0L
        // Sprites load asynchronously, so draw a few frames with a short wait between them.
        repeat(frames) {
            scene.render(nanos)
            nanos += 50_000_000
            Thread.sleep(20)
        }
        val bytes = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)
        assertTrue(bytes.size > minBytes, "$name drew almost nothing")
        scene.close()
    }

    private fun env(model: AppModel, prefs: app.pokeplan.ui.launcher.LauncherPrefs = app.pokeplan.ui.launcher.LauncherPrefs(), editing: Boolean = false) = app.pokeplan.ui.launcher.WidgetEnv(
        model, fakeApps, prefs, fakeHost, null, 2, app.pokeplan.ui.launcher.TodayState(model.store.today), app.pokeplan.ui.launcher.BuddyMood(), false, editing, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )

    private val clock = object : Clock {
        var millis = System.currentTimeMillis()
        override fun nowMillis() = millis
    }

    // The opening story ("A New Explorer") covers Home until it is done, so a screenshot of a real screen marks it done first.
    private fun started(): GameStore = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25); it.worldEpisodeDone(app.pokeplan.world.WorldMoments.INTRO_KEY) }

    @Test
    fun story() {
        val model = AppModel(started())
        val episode = app.pokeplan.world.story.StoryCatalog.all().first()
        // Each scene alone, a few seconds in: its portraits, its stage and its dungeon walk.
        episode.scenes.forEach { scene ->
            render("story-${scene.id}", frames = 90) { PixelTheme { app.pokeplan.ui.screens.StoryPlayer(model, episode.copy(scenes = listOf(scene)), {}) } }
        }
        // A cinematic (a legendary's appearance) and a choice.
        listOf("kanto-5-cave", "kanto-4-camp", "intro-mew-1", "intro-mew-3", "intro-mew-5", "rescue-1-square", "rescue-4-absol", "explorers-1-guild", "explorers-6-hidden-land", "explorers-post-106-arrival").forEach { id ->
            val scene = app.pokeplan.world.story.StoryCatalog.scene(id)!!
            render("story-$id", frames = 120) { PixelTheme { app.pokeplan.ui.screens.StoryPlayer(model, episode.copy(scenes = listOf(scene)), {}) } }
        }
    }

    @Test
    fun pokegear() {
        val store = started()
        val helper = store.grantPokemon(137, "test")
        store.assignGearHelper(app.pokeplan.core.pokemon.GearTool.CALCULATOR, helper.id)
        store.setPickerOptions(listOf("Pizza", "Sushi", "Tacos"))
        store.saveGearRates(app.pokeplan.app.GearRates("2026-10-30", mapOf("USD" to 1.0, "EUR" to 0.86, "JPY" to 152.3, "PHP" to 58.1, "GBP" to 0.75)))
        // Something kept in each tool so its screen shows more than the empty state.
        store.gearFavorite(app.pokeplan.core.pokemon.GearTool.CALCULATOR, true)
        store.gearUsed(app.pokeplan.core.pokemon.GearTool.UNITS)
        store.changeGear { it.copy(calcHistoryOn = true, calcHistory = listOf("2 + 3 × 4 = 14", "200 + 10% = 220"), pickerWeights = mapOf("Sushi" to 3), pickerHistoryOn = true, pickerHistory = listOf("Tacos"), diceHistory = listOf("2d6+3: 4 + 5 + 3 = 12"), unitFavorites = listOf("length:mi>km")) }
        store.timerStart(store.timerAdd("Tea", 180_000)!!.id)
        store.timerAdd("Eggs", 600_000)
        store.stopwatchStart(); millisNow(5_000); store.stopwatchLap(); millisNow(7_300); store.stopwatchLap(); millisNow(2_000)
        val model = AppModel(store)
        render("gear-menu") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.GearScreen(model) {} } } }
        app.pokeplan.core.pokemon.GearTool.entries.forEach { tool ->
            render("gear-${tool.name.lowercase()}", height = 1700) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.GearScreen(model, start = tool) {} } } }
            // The same at 130% text: nothing may be clipped or pushed out of its box.
            render("gear-xl-${tool.name.lowercase()}", height = 1700, fontScale = 1.3f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.GearScreen(model, start = tool) {} } } }
        }
    }

    private fun millisNow(plus: Long) { clock.millis += plus }

    /** Every reaction the buddy can show, and the new faces, in both palettes (a form with a derived shiny beside its regular one). */
    @Test
    fun reactionsAndFaces() {
        for (dark in listOf(false, true)) {
            render("reactions-${if (dark) "dark" else "light"}", width = 540, height = 420, frames = 20) {
                PixelTheme(dark = dark) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Pixel.Paper).padding(Spacing.M), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.S)) {
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.S)) {
                            app.pokeplan.app.ReactionIcon.entries.forEach { ReactionMark(it, size = 28.dp) }
                        }
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.S)) {
                            Emotion.entries.forEach { Portrait(1, false, it.name, emotion = it, size = 56.dp, framed = false) }
                        }
                        // 58 (Growlithe) has no SpriteCollab shiny: the second one is derived from its still sprites.
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.S)) {
                            listOf(false, true).forEach { shiny -> AnimatedSprite(58, shiny, "growlithe", size = 96.dp); Portrait(58, shiny, "growlithe", size = 96.dp) }
                            listOf(SpriteAction.IDLE_SIDE, SpriteAction.IDLE_UP, SpriteAction.POSE).forEach { AnimatedSprite(25, false, "pikachu", size = 96.dp, action = it) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun buttons() {
        listOf(false, true).forEach { dark ->
            render("buttons-${if (dark) "dark" else "light"}") {
                PixelTheme(dark) {
                    Box(Modifier.fillMaxSize().background(Pixel.Paper).padding(16.dp)) {
                        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            listOf("Start" to Pixel.Blue, "Done" to Pixel.Green, "Brand" to Pixel.Red, "Delete" to Pixel.Danger, "Cancel" to Pixel.InkSoft, "Gold" to Pixel.Yellow).forEach { (label, color) ->
                                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    app.pokeplan.ui.theme.PixelButton(label, {}, color = color)
                                    app.pokeplan.ui.theme.PixelButton(label, {}, color = color, compact = true)
                                    app.pokeplan.ui.theme.PixelButton("Off", {}, color = color, enabled = false, compact = true)
                                    app.pokeplan.ui.theme.PixelButton(label, {}, color = color, loading = true, compact = true)
                                }
                            }
                            app.pokeplan.ui.theme.PixelButton("Full width button", {}, Modifier.padding(0.dp), Pixel.Blue)
                        }
                    }
                }
            }
        }
    }

    /** A dungeon town graded by each weather's colour ramp, with the weather's own layer over it (WeatherLayer, WeatherGrade). */
    @Test
    fun weatherTown() {
        val weathers = listOf(
            app.pokeplan.ui.launcher.Weather.CLEAR, app.pokeplan.ui.launcher.Weather.RAIN, app.pokeplan.ui.launcher.Weather.SNOW,
            app.pokeplan.ui.launcher.Weather.BLIZZARD, app.pokeplan.ui.launcher.Weather.SANDSTORM, app.pokeplan.ui.launcher.Weather.CLOUDS,
        )
        weathers.forEach { w ->
            render("weather-town-${w.name.lowercase()}", width = 360, height = 540, density = 1f, frames = 70) {
                Box(Modifier.fillMaxSize()) {
                    app.pokeplan.ui.launcher.TownBackdrop("lush-prairie", Modifier.fillMaxSize(), weather = w)
                    app.pokeplan.ui.launcher.WeatherLayer(w, 2, dungeon = true, calm = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** The open task card is a sheet of three tabs (When, Steps, Edit), in both palettes. */
    @Test
    fun taskCardSheet() {
        val store = started()
        val task = store.addTask("Write the report", planned = store.today)
        store.addSubtask(task.id, "Outline it")
        val model = AppModel(store)
        listOf(false, true).forEach { dark ->
            app.pokeplan.ui.screens.CardTab.entries.forEach { tab ->
                render("task-card-${tab.name.lowercase()}-${if (dark) "dark" else "light"}", height = 1400, frames = 8) {
                    PixelTheme(dark) {
                        Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                            app.pokeplan.ui.screens.TaskCard(model, store.data.tasks.first { it.id == task.id }, store.today, expanded = true, toggle = {}, onDeleted = {}, startTab = tab)
                        }
                    }
                }
            }
        }
    }

    /** The exact-entry fields (typed date, time, length, moment), in both palettes. */
    @Test
    fun exactFields() {
        val store = started()
        listOf(false, true).forEach { dark ->
            render("exact-fields-${if (dark) "dark" else "light"}", height = 1500, frames = 8) {
                PixelTheme(dark) {
                    Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            var day by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlinx.datetime.LocalDate?>(store.today) }
                            var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlinx.datetime.LocalTime?>(kotlinx.datetime.LocalTime(9, 30)) }
                            var minutes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(90) }
                            var at by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(store.nowMillis - 3_600_000L) }
                            app.pokeplan.ui.screens.PixelDateTimeField(day, time, store.today, Pixel.Blue, { d, t -> day = d; time = t }, label = "Deadline", noDayLabel = "No deadline")
                            app.pokeplan.ui.screens.PixelDurationField(minutes, { minutes = it }, Pixel.Blue, label = "Estimate", noneLabel = "No estimate", presets = listOf(15, 30, 45, 60, 90))
                            app.pokeplan.ui.screens.PixelMomentField(at, { at = it }, store.nowMillis, Pixel.Blue, label = "It ended")
                        }
                    }
                }
            }
        }
    }

    /** The Kanto layer's whole dungeon list, tall: each card's title, types and tiles must belong to the same dungeon. */
    @Test
    fun worldMapKantoList() {
        val store = started()
        val model = AppModel(store)
        model.worldSection = app.pokeplan.ui.screens.WorldSection.MAP
        model.worldLayer = app.pokeplan.world.WorldLayer.KANTO
        render("world-map-kanto-list", height = 9000, frames = 40) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
    }

    @Test
    fun world() {
        val store = started()
        val team = listOf(store.data.player.pokemon.first().id, store.grantPokemon(25, "test").id, store.grantPokemon(74, "test").id)
        store.worldAssign(1, team)
        store.worldStart(1, "tiny-woods")
        clock.millis += 9L * 60 * 60 * 1000
        store.worldSettle()
        store.worldRecruit(0)
        val model = AppModel(store)
        render("world-menu") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        app.pokeplan.ui.screens.WorldSection.entries.forEach { section ->
            model.worldSection = section
            render("world-${section.name.lowercase()}") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        }
        // The Kanto layer of the map: its own picture, story and legendary quests.
        model.worldSection = app.pokeplan.ui.screens.WorldSection.MAP
        model.worldLayer = app.pokeplan.world.WorldLayer.KANTO
        render("world-map-kanto") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        render("world-xl-map-kanto", fontScale = 1.3f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        model.worldLayer = app.pokeplan.world.WorldLayer.JOHTO
        render("world-map-johto") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        model.worldLayer = app.pokeplan.world.WorldLayer.HOENN
        render("world-map-hoenn") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        model.worldLayer = app.pokeplan.world.WorldLayer.SINNOH
        render("world-map-sinnoh") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        model.worldLayer = app.pokeplan.world.WorldLayer.RESCUE
        // The same screens with the system text at 130%: nothing may be clipped or pushed out of its box.
        app.pokeplan.ui.screens.WorldSection.entries.forEach { section ->
            model.worldSection = section
            render("world-xl-${section.name.lowercase()}", fontScale = 1.3f) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        }
    }

    /** The mission board once it is open: a held job, a finished one, the available jobs with where and how often, and the ones that cannot be taken, with why. */
    @Test
    fun worldMissionBoard() {
        val base = started()
        val world = app.pokeplan.world.WorldData(
            xp = 400, clears = mapOf("tiny-woods" to 1),
            missions = listOf(app.pokeplan.world.MissionProgress("explore-woods-3"), app.pokeplan.world.MissionProgress("gather-10", 10, true)),
            missionsDone = setOf("rescue-vulpix"),
        )
        val store = GameStore(MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), base.data.copy(world = world))), clock)
        val model = AppModel(store)
        model.worldSection = app.pokeplan.ui.screens.WorldSection.MISSIONS
        for ((name, scale) in listOf("world-board" to 1f, "world-board-xl" to 1.3f)) {
            render(name, height = 1700, fontScale = scale) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.WorldScreen(model) } } }
        }
    }

    /** The recruiting scene: the question, then the welcome. */
    @Test
    fun worldRecruit() {
        val store = started()
        val mon = store.data.player.pokemon.first()
        val find = app.pokeplan.world.Find(37, "tiny-woods", 4, 0L)
        render("world-recruit-ask") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.RecruitCard(find, null, {}, {}, {}) } } }
        render("world-recruit-joined") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.RecruitCard(null, mon, {}, {}, {}) } } }
    }

    @Test
    fun onboarding() = render("onboarding") { App(MemoryStorage(), focusTab = true) }

    @Test
    fun home() {
        val store = started()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("home") { App(storage, focusTab = true) }
    }

    /** A task just ticked off: the undo bar floats above the tabs, where the thumb is. */
    @Test
    fun homeUndo() {
        val store = started()
        store.completeTask(store.data.tasks.single().id)
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("home-undo") { App(storage, focusTab = true) }
    }

    @Test
    fun homeWithRewards() {
        val store = started()
        store.completeTask(store.data.tasks.single().id)
        clock.millis += 11_000
        store.tick()
        store.grant("rare-candy", 12)
        store.grant("berry-sweet", 2)
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("home-rewards") { App(storage, focusTab = true) }
    }

    @Test
    fun planShinyBuddy() {
        val store = started()
        val data = store.data.copy(player = store.data.player.copy(pokemon = store.data.player.pokemon.map { it.copy(isShiny = true) }))
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), data.copy(lastSeenVersion = app.pokeplan.ui.AppVersion.NAME)))
        render("home-buddy-shiny", frames = 47) { App(storage, focusTab = true, start = Place.HOME) }
    }

    @Test
    fun planTorchicAfterAnEvent() {
        val store = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 255) }
        store.completeTask(store.data.tasks.single().id)
        clock.millis += 11_000
        store.tick()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        // Stop early: a reaction lasts about two seconds of animation time, so it is on screen at frame 30.
        render("home-buddy-event", frames = 30) { App(storage, focusTab = true, start = Place.HOME) }
    }

    @Test
    fun planBuddyAsleepDuringFocus() {
        val store = started()
        store.startFocus(25, deepFocus = false)
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("home-buddy-asleep") { App(storage, focusTab = true, start = Place.HOME) }
    }

    @Test
    fun largeText() {
        val store = started()
        store.addTask("Write the report for the quarterly review", Rank.B, planned = store.today, estimateMinutes = 45)
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("large-text-home", fontScale = 1.8f) { App(storage, focusTab = true) }
        val model = AppModel(store)
        render("large-text-planner", fontScale = 1.8f) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 0) } }
        }
    }

    @Test
    fun dayBackgrounds() {
        render("day-backgrounds", frames = 3) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                listOf(7 to 0, 12 to 0, 20 to 0, 23 to 30).forEach { (h, m) ->
                    Box(Modifier.weight(1f).fillMaxSize()) { DayBackground(h, m) }
                }
            }
        }
    }

    @Test
    fun planner() {
        val store = started()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.addTask("Reply to mail", Rank.E, planned = store.today)
        store.addSubtask(store.data.tasks.last().id, "Open the draft")
        store.addTask("Water the plants", Rank.D, planned = store.today.minus(kotlinx.datetime.DatePeriod(days = 2)))
        store.addTask("Read a chapter", Rank.D, planned = store.today)
        store.setPlannedTime(store.data.tasks.last().id, kotlinx.datetime.LocalTime(21, 0))
        store.addTask("Book the dentist", Rank.C, planned = store.today.plus(kotlinx.datetime.DatePeriod(days = 1)), estimateMinutes = 15)
        store.addTask("Pay the rent", Rank.B, planned = store.today.plus(kotlinx.datetime.DatePeriod(days = 3)))
        store.addTask("Buy stamps", Rank.E, planned = store.today.plus(kotlinx.datetime.DatePeriod(days = 3)))
        val groceries = store.addList("Groceries", app.pokeplan.plan.TaskList.COLORS[2], "sweet-apple")!!
        store.addList("Thesis", app.pokeplan.plan.TaskList.COLORS[5], "journal")
        store.addTask("Oat milk", Rank.E, listId = groceries.id)
        store.moveToList(store.data.tasks.first { it.title == "Buy stamps" }.id, groceries.id)
        store.addTask("Call the bank", Rank.C)
        store.completeTask(store.data.tasks.first().id)
        clock.millis += 11_000
        store.tick()
        store.markRewardsSeen()
        val model = AppModel(store)
        render("planner", height = 1500) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model) } }
        }
        render("planner-today") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 0) } }
        }
        render("planner-soon") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 3) } }
        }
        render("planner-inbox") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 2) } }
        }
        render("planner-done") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 5) } }
        }
        // A repeating task shows "repeats weekly"; All tasks lists the open ones with the search and filter chips above.
        store.makeRepeating(store.data.tasks.first { it.title == "Pay the rent" }.id, app.pokeplan.plan.Repeat.Weekly())
        render("planner-all", height = 1700) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 6) } }
        }
        render("planner-habits") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 4) } }
        }
    }

    @Test
    fun plannerDay() {
        val model = plannerDayModel()
        render("planner-day", height = 2400) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 1) } }
        }
    }

    @Test
    fun plannerWeek() {
        val model = plannerDayModel()
        val store = model.store
        val gym = store.addTask("Gym", Rank.C, planned = store.today.plus(kotlinx.datetime.DatePeriod(days = 2)), estimateMinutes = 60, category = app.pokeplan.plan.Category.HEALTH)
        store.setPlannedTime(gym.id, kotlinx.datetime.LocalTime(17, 0))
        val study = store.addTask("Study for the quiz", Rank.B, planned = store.today.plus(kotlinx.datetime.DatePeriod(days = 4)), estimateMinutes = 120, category = app.pokeplan.plan.Category.SCHOOL)
        store.setPlannedTime(study.id, kotlinx.datetime.LocalTime(14, 0))
        val colors = app.pokeplan.plan.TaskList.COLORS
        store.addAllDayEvent("Condo", store.today.plus(kotlinx.datetime.DatePeriod(days = 1)), store.today.plus(kotlinx.datetime.DatePeriod(days = 5)), colors[0])
        store.addAllDayEvent("RTO", store.today.plus(kotlinx.datetime.DatePeriod(days = 2)), color = colors[3])
        render("planner-week", height = 2400) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 1, startWeek = true) } }
        }
    }

    @Test
    fun plannerMonth() {
        val store = started()
        val colors = app.pokeplan.plan.TaskList.COLORS
        val start = store.today
        fun day(n: Int) = start.plus(kotlinx.datetime.DatePeriod(days = n))
        store.addAllDayEvent("Home", day(-3), day(-1), colors[0])
        store.addAllDayEvent("Condo", day(0), day(4), colors[0])
        store.addAllDayEvent("Home", day(5), day(9), colors[0])
        listOf(0, 1, 2, 3).forEach { store.addAllDayEvent("RTO", day(it), color = colors[3]) }
        listOf(8, 9, 10).forEach { store.addAllDayEvent("WFH", day(it), color = colors[3]) }
        store.addEvent("The Odyssey", day(4), kotlinx.datetime.LocalTime(19, 0), 150, colors[1])
        store.addEvent("Gym", day(-20), kotlinx.datetime.LocalTime(18, 0), color = colors[2], repeat = app.pokeplan.plan.EventRepeat.WEEKLY)
        store.addTask("Pay the rent", Rank.B, planned = day(2))
        store.addTask("Book the dentist", Rank.C, planned = day(0))
        val model = AppModel(store)
        render("planner-month", height = 1700) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { app.pokeplan.ui.screens.DayView(model, emptySet(), startMode = 2) }
                }
            }
        }
    }

    @Test
    fun plannerRepeatingEvent() {
        val store = started()
        val gym = store.addEvent("Gym", store.today, kotlinx.datetime.LocalTime(18, 0), color = app.pokeplan.plan.TaskList.COLORS[2], repeat = app.pokeplan.plan.EventRepeat.WEEKLY)
        store.endEventOn(gym.id, store.today.plus(kotlinx.datetime.DatePeriod(days = 63)))
        val model = AppModel(store)
        val shown = store.data.events.single().occurrences(store.today, store.today).single()
        render("planner-repeating-event", height = 1200) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { app.pokeplan.ui.screens.EventPanel(model, shown, week = false, {}) {} }
                }
            }
        }
    }

    /** A day with timed tasks, one beside a calendar event, an untimed one, an inbox one and an all-day event. */
    private fun plannerDayModel(): AppModel {
        val store = started()
        val report = store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 90, category = app.pokeplan.plan.Category.WORK)
        store.setPlannedTime(report.id, kotlinx.datetime.LocalTime(9, 0))
        val call = store.addTask("Call the dentist", Rank.E, planned = store.today, category = app.pokeplan.plan.Category.HEALTH)
        store.setPlannedTime(call.id, kotlinx.datetime.LocalTime(10, 0))
        val run = store.addTask("Evening run", Rank.C, planned = store.today, estimateMinutes = 45, category = app.pokeplan.plan.Category.HEALTH)
        store.setPlannedTime(run.id, kotlinx.datetime.LocalTime(18, 30))
        store.addTask("Reply to mail", Rank.E, planned = store.today)
        store.addTask("Buy a birthday card", Rank.E)
        store.addEvent("Piano lesson", store.today, kotlinx.datetime.LocalTime(16, 0), 45)
        // Two calendar events from the phone (one beside the report) and an all-day one.
        store.setShownCalendars(setOf(1L))
        val day = store.today
        fun at(h: Int, m: Int) = kotlinx.datetime.LocalDateTime(day, kotlinx.datetime.LocalTime(h, m))
        val calendars = object : CalendarSource {
            override fun permitted() = true
            override fun requestPermission(done: (Boolean) -> Unit) = done(true)
            override fun calendars() = listOf(app.pokeplan.plan.DeviceCalendar(1, "Work", "sophia@example.com"))
            override fun events(calendarIds: Set<Long>, from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate) = listOf(
                app.pokeplan.plan.CalendarEvent("standup", 1, "Team standup", at(9, 30), at(10, 0), color = 0xFF3F51B5),
                app.pokeplan.plan.CalendarEvent("lunch", 1, "Lunch with Sam", at(12, 0), at(13, 0), color = 0xFF0B8043),
                app.pokeplan.plan.CalendarEvent("bday", 1, "Mum's birthday", at(0, 0), kotlinx.datetime.LocalDateTime(day.plus(kotlinx.datetime.DatePeriod(days = 1)), kotlinx.datetime.LocalTime(0, 0)), allDay = true),
            )
        }
        return AppModel(store, calendars = calendars)
    }

    @Test
    fun focusSetup() {
        val model = AppModel(started())
        render("focus-setup") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.FocusScreen(model) } } }
    }

    @Test
    fun focusRunning() {
        val store = started()
        store.startFocus(25, deepFocus = true)
        val model = AppModel(store)
        render("focus-running") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.FocusScreen(model) } } }
    }

    private fun storeWithTeam(hatched: Int = 4): GameStore {
        val store = started()
        repeat(hatched) {
            store.startFocus(25, deepFocus = false)
            clock.millis += 25 * 60_000L + 1
            store.tickFocus()
        }
        store.grant("rare-candy", 30)
        store.grant("protein", 2)
        store.grant("berry-sweet", 3)
        store.feedRareCandy(store.data.player.buddyId!!, 12)
        return store
    }

    private fun collection(name: String, section: Int, open: Boolean = false, hatched: Int = 4, fontScale: Float = 1f, height: Int = 1080, setup: (GameStore) -> Unit = {}) {
        val store = storeWithTeam(hatched).also(setup)
        val model = AppModel(store)
        render(name, fontScale = fontScale, height = height) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                    app.pokeplan.ui.screens.CollectionScreen(model, section, if (open) store.data.player.buddyId else null)
                }
            }
        }
    }

    @Test
    fun collectionParty() = collection("collection-party", 0)

    /** Both regions' parties on one page: Plan's, then Care's with its lead first. */
    @Test
    fun collectionPartyRegions() = collection("collection-party-regions", 0, hatched = 6) { store ->
        store.changeSettings { it.copy(careModule = true) }
        val plan = store.data.player.party.filter { it.id != store.data.player.buddyId }
        plan.take(2).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.CARE) }
        store.setCareLead(plan[1].id)
    }

    @Test
    fun pokedexEntry() {
        val store = storeWithTeam()
        store.grantPokemon(5, "test")
        val model = AppModel(store)
        val rows = app.pokeplan.core.dex.Dex.regionDex(app.pokeplan.core.dex.Region.KANTO).mapIndexed { i, s -> (i + 1) to s }
        render("pokedex-entry", height = 2200) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.DexEntryScreen(model, rows, 5, {}, {}) } }
        }
    }

    @Test
    fun pokedexForms() {
        val store = storeWithTeam()
        store.grantPokemon(3, "test")
        val model = AppModel(store)
        val rows = app.pokeplan.core.dex.Dex.regionDex(app.pokeplan.core.dex.Region.KANTO).mapIndexed { i, s -> (i + 1) to s }
        render("pokedex-forms", height = 1400) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.DexEntryScreen(model, rows, 3, {}, {}) } }
        }
    }

    private fun cutscene(name: String, frames: Int, still: Boolean, scene: (GameStore) -> app.pokeplan.ui.screens.Cutscene) {
        val store = storeWithTeam(hatched = 1)
        store.changeSettings { it.copy(reduceMotion = still) }
        store.markRewardsSeen()
        val model = AppModel(store)
        model.cutscene = scene(store)
        render(name, frames = frames) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.CutsceneHost(model) } }
        }
    }

    @Test
    fun hatchEgg() = cutscene("hatch-egg", frames = 3, still = false) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last(), fromTask = false)
    }

    @Test
    fun hatchShiny() = cutscene("hatch-shiny", frames = 30, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last().copy(isShiny = true), fromTask = true)
    }

    /** Each module's own way of a new Pokémon arriving (owner, 8 November 2026): a friend walks over Care's meadow, one is found on Heal's route, one is catalogued in Mint's cave. */
    @Test
    fun arrivalCare() = cutscene("arrival-care", frames = 60, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last(), fromTask = false, how = app.pokeplan.app.Origin.How.CARE_EGG)
    }

    /** A visitor's egg (owner, 8 November 2026): first the visitor leaves it in the meadow, then it hatches at once. [still] shows the end of the first part. */
    private fun visitorEgg(name: String, still: Boolean, frames: Int) {
        val base = storeWithTeam(hatched = 1)
        val gift = base.data.player.pokemon.last()
        val origin = app.pokeplan.app.Origin(
            app.pokeplan.app.Origin.How.BEFRIENDED, app.pokeplan.core.dex.Region.CARE,
            visitor = app.pokeplan.app.VisitorOrigin(speciesId = 25, visitorId = "v-test", epochDay = 20_000L, note = "Befriended on a sunny day."),
        )
        val withOrigin = base.data.copy(origins = base.data.origins + (gift.id to origin))
        val store = GameStore(MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), withOrigin)), clock)
        store.changeSettings { it.copy(reduceMotion = still) }
        store.markRewardsSeen()
        val model = AppModel(store)
        model.cutscene = app.pokeplan.ui.screens.Cutscene.Hatch(gift, fromTask = false, how = app.pokeplan.app.Origin.How.BEFRIENDED)
        render(name, frames = frames) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.CutsceneHost(model) } }
        }
    }

    @Test
    fun visitorLeavesAnEgg() = visitorEgg("visitor-leaves-egg", still = true, frames = 30)

    @Test
    fun visitorLeavesAnEggMoving() = visitorEgg("visitor-leaves-egg-moving", still = false, frames = 170)

    @Test
    fun arrivalHeal() = cutscene("arrival-heal", frames = 60, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last(), fromTask = false, how = app.pokeplan.app.Origin.How.FIELD_EGG)
    }

    @Test
    fun arrivalMint() = cutscene("arrival-mint", frames = 60, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last().copy(isShiny = true), fromTask = false, how = app.pokeplan.app.Origin.How.LEDGER_EGG)
    }

    @Test
    fun evolution() = cutscene("evolution", frames = 30, still = true) {
        val before = it.grantPokemon(4, "test")
        app.pokeplan.ui.screens.Cutscene.Evolve(before, before.evolvedInto(5, "charmeleon"))
    }

    /** The reported bug: a sprite that stays on screen while its Pokémon evolves (the buddy on Home) went blank, because it kept the old form's sheet. */
    @Test
    fun aSpriteStaysAfterItsPokemonEvolvesInPlace() {
        var form by androidx.compose.runtime.mutableIntStateOf(4)
        val scene = ImageComposeScene(width = 300, height = 300, density = Density(1f, 1f)) { PixelTheme { app.pokeplan.ui.AnimatedSprite(form, false, "x", size = 200.dp, animate = false) } }
        var nanos = 0L
        fun draw(frames: Int, name: String): Int {
            repeat(frames) { scene.render(nanos); nanos += 50_000_000; Thread.sleep(20) }
            val bytes = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)
            return bytes.size
        }
        val before = draw(60, "evolve-in-place-before")
        form = 5
        val after = draw(60, "evolve-in-place-after")
        scene.close()
        assertTrue(before > 800 && after > 800, "the sprite is drawn before ($before) and after ($after) the form changes")
    }

    /** The wrong-picture bug (a map card with another dungeon's tiles): an image composable whose key changes in place must draw what a fresh one draws. */
    @Test
    fun aTileBoxChangedInPlaceDrawsWhatAFreshOneDraws() {
        fun frames(content: @Composable () -> Unit, change: (() -> Unit)?): ByteArray {
            val scene = ImageComposeScene(width = 300, height = 300, density = Density(1f, 1f)) { PixelTheme { content() } }
            var nanos = 0L
            fun draw(n: Int): ByteArray { repeat(n) { scene.render(nanos); nanos += 50_000_000; Thread.sleep(20) }; return scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes }
            draw(60)
            change?.invoke()
            val out = draw(60)
            scene.close()
            return out
        }
        fun box(id: String) = @Composable { app.pokeplan.ui.screens.TileBox(id, emptyList(), animate = false, scrolls = false) { Text("x") } }
        var dungeon by androidx.compose.runtime.mutableStateOf("mt-ember")
        val changed = frames({ box(dungeon)() }, { dungeon = "sky-pillar" })
        val fresh = frames(box("sky-pillar"), null)
        assertTrue(changed.contentEquals(fresh), "a card whose dungeon changed is drawn in the new dungeon's tiles, not the old one's")
    }

    /** The animated evolution, run to its end: the evolved Pokémon must be on the stage (owner: "after evolution, sprite disappears"). */
    @Test
    fun evolutionAnimatedEnds() = cutscene("evolution-end", frames = 360, still = false) {
        val before = it.grantPokemon(4, "test")
        app.pokeplan.ui.screens.Cutscene.Evolve(before, before.evolvedInto(5, "charmeleon"))
    }

    /** Whole app places at the system's largest text (130%): looked at for clipped or overlapping text. */
    @Test
    fun largeTextPlaces() {
        listOf("home" to Place.HOME, "plan" to Place.PLAN, "planner" to Place.PLANNER, "focus" to Place.FOCUS, "care" to Place.CARE, "pokemon" to Place.COLLECTION, "me" to Place.ME, "world" to Place.WORLD).forEach { (name, place) ->
            val store = storeWithTeam()
            store.addTask("Write the report for the quarterly meeting with a long title that wraps", Rank.B, planned = store.today, estimateMinutes = 45)
            store.addTask("Reply to mail", Rank.E, planned = store.today)
            store.markRewardsSeen()
            store.markVersionSeen(AppVersion.NAME)
            val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
            render("xl-$name", fontScale = 1.3f) { App(storage, focusTab = true, start = place) }
        }
    }

    /** A brand-new player with nothing yet: every landing page must say what it is and what to do first. */
    @Test
    fun emptyStates() {
        listOf("home" to Place.HOME, "planner" to Place.PLANNER, "focus" to Place.FOCUS, "care" to Place.CARE, "pokemon" to Place.COLLECTION, "world" to Place.WORLD, "me" to Place.ME).forEach { (name, place) ->
            val store = started()
            store.markRewardsSeen()
            store.markVersionSeen(AppVersion.NAME)
            val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
            render("empty-$name") { App(storage, focusTab = true, start = place) }
        }
    }

    private fun dark(name: String, place: Place) {
        val store = storeWithTeam()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.addTask("Reply to mail", Rank.E, planned = store.today)
        store.changeSettings { it.copy(theme = app.pokeplan.app.ThemeMode.DARK) }
        // Unseen hatches would play their scene over the screen.
        store.markRewardsSeen()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render(name) { App(storage, focusTab = true, start = place) }
    }

    /** The shared date picker open on a day in the past, and the peso sign and the zero with its bar (design review, 11 November 2026). */
    @Test
    fun datePickerAndNumbers() {
        val today = kotlinx.datetime.LocalDate(2026, 11, 11)
        listOf(false, true).forEach { dark ->
            render("date-picker-${if (dark) "dark" else "light"}", height = 900) {
                PixelTheme(dark) {
                    Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            app.pokeplan.ui.screens.PixelDatePicker(kotlinx.datetime.LocalDate(2026, 11, 3), today, app.pokeplan.ui.theme.ModuleAccent.MINT.accent, {}, initiallyOpen = true)
                            androidx.compose.material3.Text("\u20B11,250.50   60 min   308   1,000.00", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                            androidx.compose.material3.Text("\u20B11,250.50   60 min   308", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    /** Home with something waiting in Mint, Home, Link and Heal, and a module page with the compact lead (design review batch 2). */
    @Test
    fun homeAlsoToday() {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.markVersionSeen(AppVersion.NAME)
        val model = AppModel(store)
        val mint = model.mint
        mint.addAccount("Daily", app.pokeplan.mint.AccountType.BANK, "PHP", 1_000_000)
        mint.addBill("Internet", "PHP", app.pokeplan.mint.Frequency.MONTHLY, store.today, 149_900)
        model.heal.logDrink(250)
        model.home.addChore("Take out the bins")
        model.link.addFollowUp("Reply to Ana", due = store.today)
        listOf(false, true).forEach { dark ->
            render("home-also-today-${if (dark) "dark" else "light"}", height = 2300) {
                PixelTheme(dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.HomeScreen(model) {} } }
            }
        }
        render("plan-compact-lead", height = 1100) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { model.module = app.pokeplan.ui.screens.Module.PLAN; model.planSection = null; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } }
        }
    }

    @Test
    fun darkHome() = dark("dark-home", Place.HOME)

    @Test
    fun darkPlanner() = dark("dark-planner", Place.PLANNER)

    @Test
    fun darkFocus() = dark("dark-focus", Place.FOCUS)

    @Test
    fun darkPokemon() = dark("dark-pokemon", Place.COLLECTION)

    @Test
    fun darkMe() = dark("dark-me", Place.ME)

    @Test
    fun darkCare() = dark("dark-care", Place.CARE)

    @Test
    fun darkWorld() = dark("dark-world", Place.WORLD)

    @Test
    fun darkGear() = dark("dark-gear", Place.GEAR)

    @Test
    fun darkPlan() = dark("dark-plan", Place.PLAN)

    /** Every module's page opens on its lead in its own scene , light and dark. */
    @Test
    fun moduleLeadStages() {
        val store = storeWithTeam(hatched = 7)
        store.markRewardsSeen(); store.markVersionSeen(AppVersion.NAME)
        val others = store.data.player.pokemon.filter { it.id != store.data.player.buddyId }
        val ids = app.pokeplan.core.pokemon.PartyIds
        listOf(ids.PLAN, ids.HEAL, ids.MINT, ids.HOME, ids.LINK).forEachIndexed { i, party -> others.getOrNull(i)?.let { store.setInParty(it.id, true, party) } }
        others.getOrNull(5)?.let { p -> store.worldAssign(0, listOf(p.id)) }
        val model = AppModel(store)
        for (dark in listOf(false, true)) {
            val tag = if (dark) "dark" else "light"
            for (module in listOf(app.pokeplan.ui.screens.Module.PLAN, app.pokeplan.ui.screens.Module.HEAL, app.pokeplan.ui.screens.Module.MINT, app.pokeplan.ui.screens.Module.HOME, app.pokeplan.ui.screens.Module.LINK, app.pokeplan.ui.screens.Module.WORLD)) {
                model.module = module
                render("lead-$tag-${module.name.lowercase()}", height = 1500) { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
            }
        }
        model.module = null
    }

    /** The module pages in both palettes side by side by name, so each accent can be checked on the light and the dark panel. */
    @Test
    fun moduleAccentsBothPalettes() {
        val store = storeWithTeam()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.markRewardsSeen(); store.markVersionSeen(AppVersion.NAME)
        val model = AppModel(store)
        model.care.addRoutine("Drink a glass of water", kotlinx.datetime.LocalTime(9, 0))
        for (dark in listOf(false, true)) {
            val tag = if (dark) "dark" else "light"
            fun shot(name: String, content: @Composable () -> Unit) = render("accent-$tag-$name") { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { content() } } }
            shot("home") { app.pokeplan.ui.screens.HomeScreen(model) {} }
            shot("modules") { model.module = null; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }
            shot("plan") { model.module = app.pokeplan.ui.screens.Module.PLAN; model.planSection = null; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }
            shot("care") { model.module = app.pokeplan.ui.screens.Module.CARE; model.careSection = null; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }
            shot("world") { app.pokeplan.ui.screens.WorldScreen(model) }
            shot("planner") { model.module = app.pokeplan.ui.screens.Module.PLAN; model.planSection = app.pokeplan.ui.screens.PlanSection.PLANNER; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }
            shot("heal") { model.module = app.pokeplan.ui.screens.Module.HEAL; model.healSection = null; app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }
            shot("gear") { app.pokeplan.ui.screens.GearScreen(model) {} }
        }
        model.module = null; model.planSection = null
    }

    @Test
    fun collectionPc() = collection("collection-pc", 1, hatched = 14)

    /** More than one box of Pokémon: names under the sprites and the Previous / Next bar. */
    @Test
    fun collectionPcBoxes() = collection("collection-pc-boxes", 1, hatched = 40)

    @Test
    fun collectionDex() = collection("collection-dex", 2) { s ->
        s.markSeen(app.pokeplan.core.dex.Dex.regionDex(app.pokeplan.core.dex.Region.PLAN).take(2).map { it.id })
    }

    /** The Pokédex as an index: number, sprite, name and a quiet line per row, with the search box. */
    @Test
    fun collectionDexIndex() = collection("collection-dex-index", 2, hatched = 3) { s ->
        s.markSeen(app.pokeplan.core.dex.Dex.regionDex(app.pokeplan.core.dex.Region.PLAN).take(2).map { it.id })
    }

    @Test
    fun collectionBag() = collection("collection-bag", 3)

    @Test
    fun pokemonDetail() = collection("pokemon-detail", 0, open = true)

    /** The whole detail page at a rank that shows stats, the IV judge and held items, at [fontScale]. */
    private fun pokemonDetailFull(name: String, fontScale: Float, height: Int) {
        val base = storeWithTeam()
        val text = GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), base.data.copy(player = base.data.player.copy(trainerPoints = 700)))
        val store = GameStore(MemoryStorage(text), clock)
        val model = AppModel(store)
        render(name, fontScale = fontScale, height = height) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                    app.pokeplan.ui.screens.PokemonDetail(model, store.data.player.party.last()) {}
                }
            }
        }
    }

    @Test
    fun pokemonDetailWhole() = pokemonDetailFull("pokemon-detail-whole", 1f, 3700)

    @Test
    fun pokemonDetailLargeText() = pokemonDetailFull("pokemon-detail-large-text", 1.8f, 4600)

    private fun me(name: String, section: Int) = meStore(name) { model ->
        app.pokeplan.ui.screens.MeScreen(model, section)
    }

    /** The Modules tab with [module] open on [plan] (null for the menus). */
    private fun modules(name: String, module: app.pokeplan.ui.screens.Module?, plan: app.pokeplan.ui.screens.PlanSection? = null) =
        meStore(name, prepare = { it.module = module; it.planSection = plan }) { model -> app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} }

    private fun meStore(name: String, prepare: (AppModel) -> Unit = {}, height: Int = 1080, fontScale: Float = 1f, content: @Composable (AppModel) -> Unit) {
        val store = storeWithTeam()
        store.addGoal("Learn guitar").also { g ->
            store.addMilestone(g.id, "Buy a guitar")
            store.addMilestone(g.id, "Learn three chords")
        }
        val t = store.addTask("Write the report", Rank.B, planned = store.today)
        clock.millis += 11 * 60_000L
        store.completeTask(t.id)
        clock.millis += 11_000
        store.tick()
        val model = AppModel(store).also(prepare)
        render(name, fontScale = fontScale, height = height) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { content(model) } }
        }
    }

    @Test
    fun me() = me("me", -1)

    @Test
    fun modulesMenu() = modules("modules", null)

    @Test
    fun planMenu() = modules("plan", app.pokeplan.ui.screens.Module.PLAN)

    /** Plan's whole page, for comparing with Care's: the buddy's scene, the party with its perks, today and the sections. */
    @Test
    fun planMenuFull() = meStore("plan-full", prepare = { it.module = app.pokeplan.ui.screens.Module.PLAN; it.store.markRewardsSeen() }, height = 3200) { model ->
        app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {}
    }

    @Test
    fun planGrowth() = modules("plan-growth", app.pokeplan.ui.screens.Module.PLAN, app.pokeplan.ui.screens.PlanSection.GROWTH)

    @Test
    fun planGoals() = modules("plan-goals", app.pokeplan.ui.screens.Module.PLAN, app.pokeplan.ui.screens.PlanSection.GOALS)

    @Test
    fun planReview() = modules("plan-review", app.pokeplan.ui.screens.Module.PLAN, app.pokeplan.ui.screens.PlanSection.REVIEW)

    /** The Care module, with a few routines (one done), a skill practised, a diary card and a journal entry, open on [section] (null for its menu). */
    private fun care(name: String, section: app.pokeplan.ui.screens.CareSection?, height: Int = 1080, careParty: Int = 0, visiting: Boolean = false, fontScale: Float = 1f) {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(careParty).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.CARE) }
        val model = AppModel(store)
        val care = model.care
        val water = care.addRoutine("Drink a glass of water", kotlinx.datetime.LocalTime(9, 0))
        care.addRoutine("Stretch for five minutes")
        care.addRoutine("Wind down without the phone", kotlinx.datetime.LocalTime(22, 0))
        care.completeRoutine(water.id)
        care.practiseSkill("stop")
        care.saveDiary(mapOf(app.pokeplan.care.Emotion.JOY to 3, app.pokeplan.care.Emotion.FEAR to 2), skills = setOf("stop"), note = "A calm morning")
        care.write("Tea with a friend after work.", tags = setOf("People"))
        model.module = app.pokeplan.ui.screens.Module.CARE
        model.careSection = section
        if (visiting) care.visitors().first().let { v -> care.startVisit(v.speciesId); care.visitAct(app.pokeplan.care.CareAction.WORDS) }
        render(name, fontScale = fontScale, height = height) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
    }

    /** The Heal module with a little logged (an activity, drinks, a night, a weight and a goal), Field Research on, open on [section] (null for its menu). */
    private fun heal(name: String, section: app.pokeplan.ui.screens.HealSection?, height: Int = 1500, healParty: Int = 0, fontScale: Float = 1f, empty: Boolean = false, dark: Boolean = false) {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(healParty).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.HEAL) }
        val model = AppModel(store)
        if (!empty) {
            val heal = model.heal
            heal.setFieldResearch(true)
            heal.logActivity(app.pokeplan.heal.ActivityType.WALK, 35, note = "Along the river")
            heal.logDrink(250); heal.logDrink(330)
            heal.logSleep(clock.millis - 9 * 3_600_000L, clock.millis - 2 * 3_600_000L)
            heal.logMeasurement(app.pokeplan.heal.MeasureType.WEIGHT, 68.4, "kg")
            heal.addGoal(app.pokeplan.heal.GoalKind.WEEKLY_ACTIVE_MINUTES, 150)
            heal.setHydrationTarget(2000)
            store.markRewardsSeen()
        }
        model.module = app.pokeplan.ui.screens.Module.HEAL
        model.healSection = section
        render(name, fontScale = fontScale, height = height) { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
    }

    @Test
    fun healMenu() = heal("heal-menu", null, height = 2400, healParty = 3)

    @Test
    fun healMenuEmptyShowsTheTutorialAndNoScore() = heal("heal-menu-empty", null, height = 2000, empty = true)

    @Test
    fun healMenuDark() = heal("heal-menu-dark", null, height = 2400, healParty = 3, dark = true)

    @Test
    fun healActivity() = heal("heal-activity", app.pokeplan.ui.screens.HealSection.ACTIVITY, height = 2000)

    /** Every Heal section at the system's text at 130%: nothing clipped or pushed out of its box. */
    @Test
    fun largeTextHeal() {
        heal("heal-xl-menu", null, height = 4200, healParty = 3, fontScale = 1.3f)
        app.pokeplan.ui.screens.HealSection.entries.forEach { heal("heal-xl-${it.name.lowercase()}", it, height = 2400, fontScale = 1.3f) }
    }

    /** The Mint module with a little recorded (two accounts, a card, spending, a budget, a bill and a reconciliation), open on [section] (null for its menu). Synthetic amounts only. */
    private fun mint(name: String, section: app.pokeplan.ui.screens.MintSection?, height: Int = 1500, mintParty: Int = 0, fontScale: Float = 1f, empty: Boolean = false, dark: Boolean = false, hide: Boolean = false, settings: ((AppModel) -> Unit)? = null, gate: Boolean = false) {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(mintParty).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.MINT) }
        val model = AppModel(store, gate = if (gate) object : app.pokeplan.ui.DeviceGate { override fun available() = true; override fun authenticate(onResult: (Boolean) -> Unit) {}; override fun leftAt() = 0L } else null)
        if (!empty) {
            val mint = model.mint
            val bank = mint.addAccount("Daily account", app.pokeplan.mint.AccountType.BANK, "PHP", 4_250_000, suffix = "4821", group = "Daily spending").record!!
            val wallet = mint.addAccount("E-wallet", app.pokeplan.mint.AccountType.EWALLET, "PHP", 350_000).record!!
            val card = mint.addAccount("Credit card", app.pokeplan.mint.AccountType.CREDIT_CARD, "PHP", 0).record!!
            mint.record(app.pokeplan.mint.TxType.INCOME, 3_500_000, bank.id, categoryId = "cat-salary", payee = "Salary")
            mint.record(app.pokeplan.mint.TxType.EXPENSE, 184_550, bank.id, categoryId = "cat-food", payee = "Wet market")
            mint.record(app.pokeplan.mint.TxType.EXPENSE, 62_000, wallet.id, categoryId = "cat-transport", payee = "Commute")
            mint.record(app.pokeplan.mint.TxType.EXPENSE, 320_000, card.id, categoryId = "cat-shopping", payee = "Shoes")
            mint.record(app.pokeplan.mint.TxType.TRANSFER, 500_000, bank.id, toAccountId = wallet.id)
            mint.addBudget(800_000, "PHP", app.pokeplan.mint.BudgetPeriod.MONTHLY, "cat-food")
            mint.addBudget(2_000_000, "PHP", app.pokeplan.mint.BudgetPeriod.MONTHLY, null)
            mint.addBill("Internet", "PHP", app.pokeplan.mint.Frequency.MONTHLY, model.store.today, 149_900)
            mint.addBill("Electricity", "PHP", app.pokeplan.mint.Frequency.MONTHLY, model.store.today, null)
            if (hide) mint.setHideValues(true)
            store.markRewardsSeen()
        }
        settings?.invoke(model)
        model.module = app.pokeplan.ui.screens.Module.MINT
        model.mintSection = section
        render(name, fontScale = fontScale, height = height) { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
    }

    @Test
    fun arrivalHome() = cutscene("arrival-home", frames = 60, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last(), fromTask = false, how = app.pokeplan.app.Origin.How.HABITAT_EGG)
    }

    /** The Home module with a little recorded (chores, supplies, an appliance with a warranty and a service, a note), open on [section] (null for its menu). Synthetic text only. */
    private fun household(name: String, section: app.pokeplan.ui.screens.HouseholdSection?, height: Int = 1500, homeParty: Int = 0, fontScale: Float = 1f, empty: Boolean = false, dark: Boolean = false) {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(homeParty).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.HOME) }
        val model = AppModel(store)
        if (!empty) {
            val home = model.home
            val today = model.store.today
            val kitchen = home.data.areas.first { it.name == "Kitchen" }.id
            home.addChore("Wash the dishes", app.pokeplan.home.Recurrence(app.pokeplan.home.RepeatKind.DAILY), today, areaId = kitchen)
            home.addChore("Take out the bins", app.pokeplan.home.Recurrence(app.pokeplan.home.RepeatKind.WEEKLY), today.plus(-3, DateTimeUnit.DAY))
            home.addChore("Descale the kettle")
            home.addSupply("Dish soap", "bottles", 1000, 1000, areaId = kitchen)
            home.addSupply("Rice", "kg", 4500, 2000)
            val fridge = home.addAsset("Fridge", brand = "Coolco", model = "C-200", areaId = kitchen).record!!
            home.addWarranty(fridge.id, provider = "Coolco", expiryDay = today.plus(20, DateTimeUnit.DAY))
            home.addSchedule("Clean the filter", today.plus(-2, DateTimeUnit.DAY), app.pokeplan.home.Recurrence(app.pokeplan.home.RepeatKind.MONTHLY), assetId = fridge.id)
            home.addReference("Router settings", "Reset button is behind the shelf.")
            store.markRewardsSeen()
        }
        model.module = app.pokeplan.ui.screens.Module.HOME
        model.homeSection = section
        render(name, fontScale = fontScale, height = height) { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
    }

    @Test
    fun householdMenu() = household("home-menu", null, height = 2600, homeParty = 3)

    @Test
    fun householdMenuEmptyShowsTheTutorial() = household("home-menu-empty", null, height = 2000, empty = true)

    @Test
    fun householdMenuDark() = household("home-menu-dark", null, height = 2600, homeParty = 3, dark = true)

    /** Every Home section at the system's text at 130%: nothing clipped or pushed out of its box. */
    @Test
    fun largeTextHousehold() {
        app.pokeplan.ui.screens.HouseholdSection.entries.forEach { household("home-xl-${it.name.lowercase()}", it, height = 2800, fontScale = 1.3f) }
    }

    @Test
    fun arrivalLink() = cutscene("arrival-link", frames = 60, still = true) {
        app.pokeplan.ui.screens.Cutscene.Hatch(it.data.player.pokemon.last(), fromTask = false, how = app.pokeplan.app.Origin.How.POSTCARD_EGG)
    }

    /** The Link module with a little recorded (two people, a note, two dates, follow-ups), open on [section] (null for its menu). Synthetic text only. */
    private fun link(name: String, section: app.pokeplan.ui.screens.LinkSection?, height: Int = 1500, linkParty: Int = 0, fontScale: Float = 1f, empty: Boolean = false, dark: Boolean = false, prepare: (AppModel) -> Unit = {}) {
        val store = storeWithTeam()
        store.markRewardsSeen()
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(linkParty).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.LINK) }
        val model = AppModel(store)
        if (!empty) {
            val link = model.link
            val today = model.store.today
            val ana = link.addPerson("Ana Reyes", organization = "Choir", category = "Friend", tags = listOf("choir"), favorite = true).record!!
            val ben = link.addPerson("Ben Cruz", preferred = "Benny", category = "Family").record!!
            link.addNote(ana.id, "Prefers texts in the evening.")
            link.addDate(app.pokeplan.link.DateKind.BIRTHDAY, "Birthday", today.plus(6, DateTimeUnit.DAY).monthNumber, today.plus(6, DateTimeUnit.DAY).dayOfMonth, 1994, personId = ana.id)
            link.addDate(app.pokeplan.link.DateKind.ANNIVERSARY, "Anniversary", 2, 29, personId = ben.id)
            link.addFollowUp("Send the choir photos", ana.id, today.plus(-2, DateTimeUnit.DAY))
            link.addFollowUp("Ask about the visit", ben.id)
            link.complete(link.addFollowUp("Return the book").record!!.id)
            store.markRewardsSeen()
        }
        prepare(model)
        model.module = app.pokeplan.ui.screens.Module.LINK
        model.linkSection = section
        render(name, fontScale = fontScale, height = height) { PixelTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.ModulesScreen(model, focusTab = true) {} } } }
    }

    @Test
    fun linkMenu() = link("link-menu", null, height = 2600, linkParty = 3)

    @Test
    fun linkMenuEmptyShowsTheTutorial() = link("link-menu-empty", null, height = 2000, empty = true)

    @Test
    fun linkMenuDark() = link("link-menu-dark", null, height = 2600, linkParty = 3, dark = true)

    /** Link's reminders switched on: the Settings card, and a date and an open follow-up with their own reminder choice (light and dark). */
    @Test
    fun linkReminders() {
        val on: (AppModel) -> Unit = { m ->
            m.link.setReminders(true)
            m.link.data.dates.firstOrNull()?.let { m.link.setDateRemind(it.id, 3) }
            m.link.data.followUps.firstOrNull { it.due != null }?.let { m.link.setFollowUpRemind(it.id, true) }
        }
        link("link-settings-reminders", app.pokeplan.ui.screens.LinkSection.SETTINGS, height = 2600, prepare = on)
        link("link-settings-reminders-dark", app.pokeplan.ui.screens.LinkSection.SETTINGS, height = 2600, dark = true, prepare = on)
    }

    /** Every Link section at the system's text at 130%: nothing clipped or pushed out of its box. */
    @Test
    fun largeTextLink() {
        app.pokeplan.ui.screens.LinkSection.entries.forEach { link("link-xl-${it.name.lowercase()}", it, height = 2800, fontScale = 1.3f) }
    }

    @Test
    fun mintMenu() = mint("mint-menu", null, height = 2400, mintParty = 3)

    @Test
    fun mintMenuEmptyShowsTheTutorial() = mint("mint-menu-empty", null, height = 2000, empty = true)

    @Test
    fun mintMenuDark() = mint("mint-menu-dark", null, height = 2400, mintParty = 3, dark = true)

    @Test
    fun mintMenuWithAmountsHidden() = mint("mint-menu-hidden", null, height = 2400, mintParty = 1, hide = true)

    @Test
    fun mintActivityAndAccounts() {
        mint("mint-activity", app.pokeplan.ui.screens.MintSection.ACTIVITY, height = 2800)
        mint("mint-accounts", app.pokeplan.ui.screens.MintSection.ACCOUNTS, height = 2200)
    }

    /** The optional access gate (locked card) and Mint's settings with bill reminders on and the gate offered. */
    @Test
    fun mintImportAndHealConnections() {
        mint("mint-import", app.pokeplan.ui.screens.MintSection.IMPORT, height = 3000)
        mint("mint-import-dark", app.pokeplan.ui.screens.MintSection.IMPORT, height = 3000, dark = true)
        heal("heal-connections", app.pokeplan.ui.screens.HealSection.CONNECTIONS, height = 2200)
    }

    @Test
    fun mintLockedAndSettings() {
        mint("mint-locked", null, height = 900, gate = true, settings = { it.mint.setGate(true) })
        mint("mint-settings", app.pokeplan.ui.screens.MintSection.SETTINGS, height = 3200, gate = true, settings = { it.mint.setBillReminders(true) })
        mint("mint-bills-reminders", app.pokeplan.ui.screens.MintSection.BILLS, height = 2600, settings = { it.mint.setBillReminders(true) })
    }

    /** An expedition box at several points of its scroll: a bay's rim must close at the bottom and the frame corner stays whole (owner: "this L seems to be missing something"). */
    @Test
    fun tileBoxCorners() {
        listOf(2, 120, 260, 420, 560, 700).forEach { frames ->
            render("tilebox-$frames", frames = frames, height = 380) {
                PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper).padding(16.dp)) {
                    app.pokeplan.ui.screens.TileBox("mt-ember", listOf(app.pokeplan.ui.screens.TileWalker(25, false, "Pikachu")), animate = true, scrolls = true) { Text("Team 2") }
                } }
            }
        }
    }

    /** An account being edited: every detail, the currency fixed once it has records. */
    @Test
    fun mintAccountEdit() {
        val store = storeWithTeam()
        val model = AppModel(store)
        val account = model.mint.addAccount("Daily account", app.pokeplan.mint.AccountType.BANK, "PHP", 4_250_000, suffix = "4821", group = "Daily spending").record!!
        val fresh = model.mint.addAccount("Spare wallet", app.pokeplan.mint.AccountType.CASH, "PHP", 10_000).record!!
        model.mint.record(app.pokeplan.mint.TxType.EXPENSE, 184_550, account.id, categoryId = "cat-food", payee = "Wet market")
        render("mint-account-edit", height = 1900) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { androidx.compose.foundation.layout.Column { app.pokeplan.ui.screens.AccountOptions(model, account, startEditing = true) {}; app.pokeplan.ui.screens.AccountOptions(model, fresh, startEditing = true) {} } } }
        }
    }

    /** Every Mint section at the system's text at 130%: nothing clipped or pushed out of its box. */
    @Test
    fun largeTextMint() {
        mint("mint-xl-menu", null, height = 4200, mintParty = 3, fontScale = 1.3f)
        app.pokeplan.ui.screens.MintSection.entries.forEach { mint("mint-xl-${it.name.lowercase()}", it, height = 2800, fontScale = 1.3f) }
    }

    /** Home as the world overview: each region with its lead and next step, the streak, and what happened recently. */
    @Test
    fun homeRegions() {
        val store = storeWithTeam()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.markRewardsSeen()
        store.markVersionSeen(AppVersion.NAME)
        store.data.player.pokemon.filter { it.id != store.data.player.buddyId }.take(2).forEach { store.setInParty(it.id, true, app.pokeplan.core.pokemon.PartyIds.CARE) }
        val model = AppModel(store)
        val water = model.care.addRoutine("Drink a glass of water", kotlinx.datetime.LocalTime(9, 0))
        model.care.addRoutine("Stretch for five minutes")
        model.care.completeRoutine(water.id)
        store.markRewardsSeen()
        render("home-regions", height = 1700) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.HomeScreen(model) {} } } }
    }

    /** The routine editor, at the system's largest text: nothing clipped. */
    @Test
    fun careRoutineEditor() {
        val model = AppModel(storeWithTeam())
        val r = model.care.addRoutine("Drink a glass of water", kotlinx.datetime.LocalTime(9, 0), setOf(kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.DayOfWeek.FRIDAY))
        render("care-routine-edit-xl", fontScale = 1.3f, height = 1500) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { app.pokeplan.ui.screens.RoutineOptions(model, model.careData.routines.single { it.id == r.id }, startEditing = true) {} } } } }
    }

    /** Every Plan section, every Care section and the Pokémon tab at the system's text at 130%: nothing clipped or pushed out of its box. */
    @Test
    fun largeTextModules() {
        app.pokeplan.ui.screens.PlanSection.entries.filter { it != app.pokeplan.ui.screens.PlanSection.PLANNER && it != app.pokeplan.ui.screens.PlanSection.FOCUS }.forEach { sec ->
            meStore("plan-xl-${sec.name.lowercase()}", prepare = { it.module = app.pokeplan.ui.screens.Module.PLAN; it.planSection = sec }, height = 1500, fontScale = 1.3f) { m -> app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }
        }
        meStore("plan-xl-menu", prepare = { it.module = app.pokeplan.ui.screens.Module.PLAN; it.store.markRewardsSeen() }, height = 3600, fontScale = 1.3f) { m -> app.pokeplan.ui.screens.ModulesScreen(m, focusTab = true) {} }
        care("care-xl-menu", null, height = 3800, careParty = 3, fontScale = 1.3f)
        app.pokeplan.ui.screens.CareSection.entries.forEach { care("care-xl-${it.name.lowercase()}", it, height = 1500, fontScale = 1.3f) }
        (0..3).forEach { collection("collection-xl-$it", it, hatched = 6, fontScale = 1.3f, height = 1500) }
        collection("collection-xl-menu", -1, fontScale = 1.3f)
    }

    /** Every control in every state, light and dark and at 130% text: the design system on one page (docs/pokelife/FEATURES/DESIGN_SYSTEM.md section 5.6). */
    @Test
    fun componentGallery() {
        for ((suffix, dark, scale) in listOf(Triple("", false, 1f), Triple("-dark", true, 1f), Triple("-xl", false, 1.3f))) {
            render("components$suffix", height = 3300, fontScale = scale) { PixelTheme(dark) { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { Gallery() } } }
        }
    }

    @Composable
    private fun Gallery() {
        val A = androidx.compose.foundation.layout.Arrangement
        val states = listOf("Rest" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Rest), "Hover" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Hovered),
            "Press" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Pressed), "Focus" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Rest, focused = true),
            "Off" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Disabled), "Load" to app.pokeplan.ui.theme.KeyPreview(app.pokeplan.ui.theme.KeyState.Loading))
        androidx.compose.foundation.layout.Column(Modifier.padding(Spacing.L), verticalArrangement = A.spacedBy(Spacing.S)) {
            for ((name, color) in listOf("Blue" to Pixel.Blue, "Green" to Pixel.Green, "Red" to Pixel.Danger, "Yellow" to Pixel.Yellow, "Neutral" to Pixel.InkSoft)) {
                Text(name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = A.spacedBy(Spacing.XS)) {
                    states.forEach { (label, pv) -> app.pokeplan.ui.theme.PixelButton(label, {}, color = color, preview = pv, enabled = pv.state != app.pokeplan.ui.theme.KeyState.Disabled) }
                }
            }
            Text("Compact", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = A.spacedBy(Spacing.S)) {
                app.pokeplan.ui.theme.PixelButton("Add", {}, compact = true); app.pokeplan.ui.theme.PixelButton("Add", {}, compact = true, enabled = false)
                app.pokeplan.ui.theme.PixelButton("Back", {}, color = Pixel.InkSoft, compact = true)
            }
            Text("Tabs and chips (selected, hover, press, focus, off)", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = A.spacedBy(Spacing.S)) {
                app.pokeplan.ui.screens.TabButton("Open", false, {}); app.pokeplan.ui.screens.TabButton("Done", true, {}); app.pokeplan.ui.screens.TabButton("Any", false, {}, enabled = false)
                app.pokeplan.ui.screens.ArrowButton("‹", "Back", true) {}; app.pokeplan.ui.screens.ArrowButton("›", "Next", false) {}
            }
            app.pokeplan.ui.screens.CategoryChips(app.pokeplan.plan.Category.entries[1]) {}
            app.pokeplan.ui.screens.Segmented(listOf("Day", "Week", "Month"), 1) {}
            Text("Rows", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            app.pokeplan.ui.theme.ChoiceRow(true, {}) { Text("Chosen") }
            app.pokeplan.ui.theme.ChoiceRow(false, {}) { Text("Not chosen") }
            app.pokeplan.ui.theme.ToggleRow("A setting", true, detail = "On") {}
            app.pokeplan.ui.theme.ToggleRow("A setting", false) {}
            app.pokeplan.ui.theme.ListRow(onClick = {}) { Text("A list row") }
            Text("Notices and chips", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            app.pokeplan.ui.theme.NoticeTone.entries.forEach { app.pokeplan.ui.theme.Notice("A ${it.name.lowercase()} notice.", tone = it) }
            app.pokeplan.ui.theme.StatChip("12", { Text("★") })
            Text("Text fields (rest, placeholder, error, off, read-only)", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            app.pokeplan.ui.theme.PixelField("Some text", {}, Modifier.fillMaxWidth(), singleLine = true, label = "Title", helper = "Up to 60 letters")
            app.pokeplan.ui.theme.PixelField("", {}, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Add a task")
            app.pokeplan.ui.theme.PixelField("", {}, Modifier.fillMaxWidth(), singleLine = true, label = "Title", error = "Give it a name")
            app.pokeplan.ui.theme.PixelField("Locked", {}, Modifier.fillMaxWidth(), singleLine = true, label = "Title", enabled = false)
            app.pokeplan.ui.theme.PixelField("Read only", {}, Modifier.fillMaxWidth(), singleLine = true, readOnly = true)
            app.pokeplan.ui.theme.PixelBar(0.6f)
        }
    }

    /** Every landing page on a brand-new save (the starter and nothing else): the first-use and empty states. */
    @Test
    fun firstUse() {
        fun page(name: String, prepare: (AppModel) -> Unit = {}, content: @Composable (AppModel) -> Unit) {
            val model = AppModel(started()).also(prepare)
            render("first-$name", height = 1500) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { content(model) } } }
        }
        page("home") { app.pokeplan.ui.screens.HomeScreen(it) {} }
        page("modules") { app.pokeplan.ui.screens.ModulesScreen(it, focusTab = true) {} }
        page("plan") { it.module = app.pokeplan.ui.screens.Module.PLAN; app.pokeplan.ui.screens.ModulesScreen(it, focusTab = true) {} }
        app.pokeplan.ui.screens.PlanSection.entries.filter { it != app.pokeplan.ui.screens.PlanSection.PLANNER && it != app.pokeplan.ui.screens.PlanSection.SETTINGS }.forEach { sec ->
            page("plan-${sec.name.lowercase()}") { it.module = app.pokeplan.ui.screens.Module.PLAN; it.planSection = sec; app.pokeplan.ui.screens.ModulesScreen(it, focusTab = true) {} }
        }
        for (v in 0..5) page("planner-$v") { app.pokeplan.ui.screens.PlannerScreen(it, startView = v) }
        page("care") { it.module = app.pokeplan.ui.screens.Module.CARE; app.pokeplan.ui.screens.ModulesScreen(it, focusTab = true) {} }
        app.pokeplan.ui.screens.CareSection.entries.forEach { sec ->
            page("care-${sec.name.lowercase()}") { it.module = app.pokeplan.ui.screens.Module.CARE; it.careSection = sec; app.pokeplan.ui.screens.ModulesScreen(it, focusTab = true) {} }
        }
        app.pokeplan.ui.screens.WorldSection.entries.filter { it != app.pokeplan.ui.screens.WorldSection.SETTINGS }.forEach { sec ->
            page("world-${sec.name.lowercase()}") { it.worldSection = sec; app.pokeplan.ui.screens.WorldScreen(it) }
        }
        for (i in 0..3) page("pokemon-$i") { app.pokeplan.ui.screens.CollectionScreen(it, i) }
        page("pokemon-quests") { app.pokeplan.ui.screens.CollectionScreen(it, -1) }
        page("me") { app.pokeplan.ui.screens.MeScreen(it, -1) }
    }

    @Test
    fun careMenu() = care("care", null)

    /** Care's whole page with three in Care's party, for comparing with Plan's. */
    @Test
    fun careMenuFull() = care("care-full", null, height = 3600, careParty = 3)

    /** Care with a visit on: the visitor up close in the meadow, its bid, and the kinds of care left. */
    @Test
    fun careVisit() = care("care-visit", null, height = 3800, careParty = 3, visiting = true)

    @Test
    fun careRoutines() = care("care-routines", app.pokeplan.ui.screens.CareSection.ROUTINES)

    @Test
    fun careCalm() = care("care-calm", app.pokeplan.ui.screens.CareSection.CALM)

    @Test
    fun careDiary() = care("care-diary", app.pokeplan.ui.screens.CareSection.DIARY)

    @Test
    fun careSkills() = care("care-skills", app.pokeplan.ui.screens.CareSection.SKILLS)

    @Test
    fun careJournal() = care("care-journal", app.pokeplan.ui.screens.CareSection.JOURNAL)

    @Test
    fun careTrends() = care("care-trends", app.pokeplan.ui.screens.CareSection.TRENDS)

    @Test
    fun careMeadow() = care("care-meadow", app.pokeplan.ui.screens.CareSection.MEADOW, careParty = 2)

    @Test
    fun carePlay() = care("care-play", app.pokeplan.ui.screens.CareSection.PLAY, careParty = 2)

    @Test
    fun meSettings() = me("me-settings", 0)

    @Test
    fun meHelp() = me("me-help", 1)

    /** A module's Guardian slot under its party, with the six-module switcher above it (the owner's phone showed the old tabs cut to "Pl..." and "Ca..."). */
    @Test
    fun collectionGuardians() = collection("collection-guardians", 0, hatched = 6) { store ->
        val ids = listOf("uxie", "mew", "celebi").map { name -> store.grantPokemon(app.pokeplan.core.dex.Dex.species.first { it.identifier == name }.id, "Quest").id }
        store.slotGuardian(ids[0], app.pokeplan.core.pokemon.PartyIds.PLAN)
    }

    @Test
    fun onboardingStarters() {
        val store = GameStore(MemoryStorage(), clock)
        render("onboarding-starters") {
            PixelTheme { PixelBackground("bw12", Modifier.fillMaxSize()) { app.pokeplan.ui.screens.OnboardingScreen(store, startStep = 3) } }
        }
    }

    @Test
    fun collectionMenu() = collection("collection-menu", -1)

    @Test
    fun collectionQuests() {
        val base = started()
        val text = GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), base.data.copy(player = base.data.player.copy(trainerPoints = 3_300)))
        val store = GameStore(MemoryStorage(text), clock)
        store.acceptQuest("azelf")
        store.acceptQuest("uxie")
        val model = AppModel(store)
        render("collection-quests") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.CollectionScreen(model, 5) } }
        }
    }

    @Test
    fun meMenu() = me("me-menu", -1)

    private val fakeApps = listOf(
        "Calendar" to app.pokeplan.ui.launcher.AppCategory.PRODUCTIVITY, "Camera" to app.pokeplan.ui.launcher.AppCategory.OTHER, "Chess" to app.pokeplan.ui.launcher.AppCategory.GAMES,
        "Clock" to app.pokeplan.ui.launcher.AppCategory.OTHER, "Maps" to app.pokeplan.ui.launcher.AppCategory.TRAVEL, "Messages" to app.pokeplan.ui.launcher.AppCategory.SOCIAL,
        "Music" to app.pokeplan.ui.launcher.AppCategory.MEDIA, "News" to app.pokeplan.ui.launcher.AppCategory.NEWS, "Notes" to app.pokeplan.ui.launcher.AppCategory.PRODUCTIVITY,
        "Phone" to app.pokeplan.ui.launcher.AppCategory.OTHER, "Photos" to app.pokeplan.ui.launcher.AppCategory.MEDIA, "Settings" to app.pokeplan.ui.launcher.AppCategory.OTHER,
        "Tasks" to app.pokeplan.ui.launcher.AppCategory.PRODUCTIVITY, "Weather" to app.pokeplan.ui.launcher.AppCategory.OTHER,
    ).map { (name, c) -> app.pokeplan.ui.launcher.LauncherApp("pkg.$name", name, c) }

    private val fakeHost = object : app.pokeplan.ui.launcher.LauncherHost {
        override suspend fun icon(key: String) = null
        override fun open(key: String) {}
        override fun appInfo(key: String) {}
        override fun uninstall(key: String) {}
        override fun openHomeSettings() {}
        override fun openPokemon(id: String) {}
        override fun openApp(place: Place) {}
    }

    @Test
    fun launcherSettingsAndEmoji() {
        val model = AppModel(started())
        render("launcher-settings") { PixelTheme { app.pokeplan.ui.launcher.LauncherSettings(model, app.pokeplan.ui.launcher.LauncherPrefs(), {}, false, 2, 2, fakeHost, {}, {}, {}, {}, {}) } }
        render("launcher-emoji", height = 700) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Night).padding(16.dp)) {
                    androidx.compose.foundation.layout.Column {
                        app.pokeplan.ui.theme.PmdText("\uD83C\uDFE0Home", 2)
                        app.pokeplan.ui.theme.PmdText("\uD83D\uDCBB WFH", 2)
                        app.pokeplan.ui.theme.PmdText("Run \uD83C\uDFC3 today \u2764\uFE0F", 2)
                    }
                }
            }
        }
    }

    @Test
    fun launcherHome() {
        val store = started()
        listOf(1, 4, 7, 39, 52, 54, 94, 133, 143, 150, 151, 25, 175, 183).forEach { store.grantPokemon(it, "test") }
        val model = AppModel(store)
        val prefs = app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages").pin("pkg.Camera")
        render("launcher-home") { app.pokeplan.ui.launcher.LauncherScreen(model, fakeApps, prefs, {}, isDefault = false, phone = fakeHost) }
    }

    /** The launcher's town at 8:39 pm over a dungeon's tiles: the night tint must reach the right edge (the owner's phone showed a lighter strip there). */
    @Test
    fun launcherNight() {
        val store = started()
        listOf(1, 4, 7, 39, 52, 54, 94, 133, 143, 150, 151, 25, 175, 183).forEach { store.grantPokemon(it, "test") }
        val zone = java.util.TimeZone.getDefault()
        val cal = java.util.Calendar.getInstance(zone).apply { set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 39) }
        clock.millis = cal.timeInMillis
        val model = AppModel(store)
        val prefs = app.pokeplan.ui.launcher.LauncherPrefs(scene = "dark-hill").pin("pkg.Phone").pin("pkg.Messages").pin("pkg.Camera")
        render("launcher-night", height = 1170) { app.pokeplan.ui.launcher.LauncherScreen(model, fakeApps, prefs, {}, isDefault = false, phone = fakeHost) }
    }

    /** The launcher's home page on the widget grid (the preview), beside the stacked one: the same widgets, in cells. */
    @Test
    fun launcherGrid() {
        val store = started()
        listOf(1, 4, 7, 39, 52, 54, 94, 133, 143, 150, 151, 25, 175, 183).forEach { store.grantPokemon(it, "test") }
        val model = AppModel(store)
        val prefs = app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages").pin("pkg.Camera")
        render("launcher-stacked", height = 1170) { app.pokeplan.ui.launcher.LauncherScreen(model, fakeApps, prefs.withGridMode(false), {}, isDefault = true, phone = fakeHost) }
        render("launcher-grid", height = 1170) { app.pokeplan.ui.launcher.LauncherScreen(model, fakeApps, prefs.withGridMode(true), {}, isDefault = true, phone = fakeHost) }
    }

    /** Editing the widget grid: the frames, handles and X on each widget, and a real drag that moves one. */
    @Test
    fun launcherGridEdit() {
        val store = started()
        listOf(1, 4, 7, 39, 52).forEach { store.grantPokemon(it, "test") }
        val model = AppModel(store)
        var prefs by androidx.compose.runtime.mutableStateOf(app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages").withGridMode(true))
        val env = {
            app.pokeplan.ui.launcher.WidgetEnv(
                model, fakeApps, prefs, fakeHost, null, 3, app.pokeplan.ui.launcher.TodayState(store.today), app.pokeplan.ui.launcher.BuddyMood(), false, true,
                { prefs = it }, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        val scene = ImageComposeScene(width = 540, height = 1170, density = Density(1.5f, 1f)) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Night)) { app.pokeplan.ui.launcher.GridHomePage(env(), null) } }
        }
        fun frames(n: Int = 20) = repeat(n) { scene.render(it * 50_000_000L); Thread.sleep(10) }
        frames()
        File("build/screenshots/launcher-grid-edit.png").apply { parentFile.mkdirs() }.writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        val before = prefs.gridWidgets.first { it.kind == app.pokeplan.ui.launcher.WidgetKind.NAV }
        // Drag the shortcuts widget two rows down and one column right.
        val rowPx = app.pokeplan.ui.launcher.WidgetGrid.ROW_DP * 1.5f
        val from = androidx.compose.ui.geometry.Offset(150f, 420f)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, from)
        frames(2)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, from + androidx.compose.ui.geometry.Offset(0f, rowPx))
        frames(2)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, from + androidx.compose.ui.geometry.Offset(0f, rowPx * 2))
        frames(2)
        File("build/screenshots/launcher-grid-drag.png").writeBytes(scene.render(3_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, from + androidx.compose.ui.geometry.Offset(0f, rowPx * 2))
        frames(4)
        File("build/screenshots/launcher-grid-dropped.png").writeBytes(scene.render(4_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        val moved = prefs.gridWidgets.first { it.kind == app.pokeplan.ui.launcher.WidgetKind.NAV }
        kotlin.test.assertEquals(before.row + 2, moved.row, "the drag moved the shortcuts two rows down")
        // Pull its corner square one row down: the widget is one row taller (its kind allows 4).
        val corner = androidx.compose.ui.geometry.Offset(474f, 24f + (moved.bottom * rowPx) - 42f)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, corner)
        frames(2)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, corner + androidx.compose.ui.geometry.Offset(0f, rowPx))
        frames(2)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, corner + androidx.compose.ui.geometry.Offset(0f, rowPx))
        frames(4)
        File("build/screenshots/launcher-grid-resized.png").writeBytes(scene.render(5_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        val resized = prefs.gridWidgets.first { it.kind == app.pokeplan.ui.launcher.WidgetKind.NAV }
        kotlin.test.assertEquals(moved.h + 1, resized.h, "the corner pulled it one row taller")
        kotlin.test.assertEquals(moved.row, resized.row)
        scene.close()
    }

    /** Every widget kind at its smallest and at half width, on the grid: what clips and what fits. */
    @Test
    fun launcherGridSizes() {
        val store = started()
        listOf(1, 4, 7, 39, 52).forEach { store.grantPokemon(it, "test") }
        store.addTask("Write the report", Rank.B, planned = store.today)
        val model = AppModel(store)
        val kinds = listOf(app.pokeplan.ui.launcher.WidgetKind.CLOCK, app.pokeplan.ui.launcher.WidgetKind.BUDDY, app.pokeplan.ui.launcher.WidgetKind.NAV, app.pokeplan.ui.launcher.WidgetKind.PINNED, app.pokeplan.ui.launcher.WidgetKind.ACTIONS, app.pokeplan.ui.launcher.WidgetKind.CALENDAR, app.pokeplan.ui.launcher.WidgetKind.AGENDA, app.pokeplan.ui.launcher.WidgetKind.FORECAST)
        for ((name, wide, tall) in listOf(Triple("min", false, false), Triple("half", true, false))) {
            var row = 0
            val grid = kinds.flatMap { k ->
                val l = k.limits()
                val w = if (wide) l.minW.coerceAtLeast(2) else l.minW
                val h = l.minH
                listOf(app.pokeplan.ui.launcher.GridWidget(app.pokeplan.ui.launcher.WidgetPage.HOME, k, "", 0, row, w, h)).also { row += h }
            }
            val prefs = app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages").pin("pkg.Camera").pin("pkg.Maps").withGridMode(true).withGrid(grid)
            val env = app.pokeplan.ui.launcher.WidgetEnv(model, fakeApps, prefs, fakeHost, null, 3, app.pokeplan.ui.launcher.TodayState(store.today), app.pokeplan.ui.launcher.BuddyMood(), false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            render("grid-sizes-$name", height = 1500) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Night)) { app.pokeplan.ui.launcher.GridHomePage(env, null) } } }
        }
    }

    /** Every widget kind at each size its limits allow (2x2, 4x2, 2x3, 4x3, 4x4 ...), one image per size, to look for clipped or off-centre text. */
    @Test
    fun launcherGridSizeMatrix() {
        val store = started()
        listOf(1, 4, 7, 39, 52).forEach { store.grantPokemon(it, "test") }
        store.addTask("Write the report", Rank.B, planned = store.today)
        val model = AppModel(store)
        val kinds = listOf(
            app.pokeplan.ui.launcher.WidgetKind.CLOCK, app.pokeplan.ui.launcher.WidgetKind.STATUS, app.pokeplan.ui.launcher.WidgetKind.BUDDY,
            app.pokeplan.ui.launcher.WidgetKind.NAV, app.pokeplan.ui.launcher.WidgetKind.PINNED, app.pokeplan.ui.launcher.WidgetKind.OFTEN,
            app.pokeplan.ui.launcher.WidgetKind.ACTIONS, app.pokeplan.ui.launcher.WidgetKind.CALENDAR, app.pokeplan.ui.launcher.WidgetKind.AGENDA,
            app.pokeplan.ui.launcher.WidgetKind.FORECAST,
        )
        val sizes = listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 4 to 2, 2 to 3, 4 to 3, 4 to 4, 4 to 6, 4 to 8, 4 to 10)
        for (k in kinds) for ((w, h) in sizes) {
            val l = k.limits()
            if (w !in l.minW..l.maxW || h !in l.minH..l.maxH) continue
            val grid = listOf(app.pokeplan.ui.launcher.GridWidget(app.pokeplan.ui.launcher.WidgetPage.HOME, k, "", 0, 0, w, h))
            val prefs = app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages").pin("pkg.Camera").pin("pkg.Maps").withGridMode(true).withGrid(grid)
            val env = app.pokeplan.ui.launcher.WidgetEnv(model, fakeApps, prefs, fakeHost, null, 3, app.pokeplan.ui.launcher.TodayState(store.today), app.pokeplan.ui.launcher.BuddyMood(), false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            val rows = app.pokeplan.ui.launcher.WidgetGrid.ROW_DP * h + 16
            render("gridsize-${k.name.lowercase()}-${w}x$h", frames = 8, height = (rows * 1.5f).toInt(), minBytes = 500) { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Night)) { app.pokeplan.ui.launcher.GridHomePage(env, null) } } }
        }
    }

    /** Holding a dragged widget at the right side of the screen hops it to the next page (and the page goes there). */
    @Test
    fun launcherGridHop() {
        val store = started()
        val model = AppModel(store)
        var prefs by androidx.compose.runtime.mutableStateOf(app.pokeplan.ui.launcher.LauncherPrefs().withGridMode(true))
        var wentTo = -1
        val scene = ImageComposeScene(width = 540, height = 1170, density = Density(1.5f, 1f)) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Night)) {
                    app.pokeplan.ui.launcher.GridHomePage(
                        app.pokeplan.ui.launcher.WidgetEnv(
                            model, fakeApps, prefs, fakeHost, null, 3, app.pokeplan.ui.launcher.TodayState(store.today), app.pokeplan.ui.launcher.BuddyMood(), false, true,
                            { prefs = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, pages = prefs.pages, goToPage = { wentTo = it },
                        ), null,
                    )
                }
            }
        }
        fun frames(n: Int = 10, pause: Long = 10) = repeat(n) { scene.render(it * 50_000_000L); Thread.sleep(pause) }
        frames()
        val nav = prefs.gridWidgets.first { it.kind == app.pokeplan.ui.launcher.WidgetKind.NAV }
        val ptr = androidx.compose.ui.input.pointer.PointerEventType
        val from = androidx.compose.ui.geometry.Offset(150f, 24f + nav.row * app.pokeplan.ui.launcher.WidgetGrid.ROW_DP * 1.5f + 60f)
        scene.sendPointerEvent(ptr.Press, from); frames(2)
        scene.sendPointerEvent(ptr.Move, from + androidx.compose.ui.geometry.Offset(150f, 0f)); frames(2)
        // Into the right-hand edge zone, then hold (the hop waits half a second).
        scene.sendPointerEvent(ptr.Move, androidx.compose.ui.geometry.Offset(525f, from.y)); frames(2)
        for (i in 0 until 12) { scene.sendPointerEvent(ptr.Move, androidx.compose.ui.geometry.Offset(526f - i % 2, from.y)); frames(1, 100) }
        scene.close()
        kotlin.test.assertEquals(app.pokeplan.ui.launcher.WidgetPage.TODAY, prefs.gridWidgets.first { it.kind == app.pokeplan.ui.launcher.WidgetKind.NAV }.page, "held at the right side, the shortcuts hopped to Today")
        kotlin.test.assertEquals(1, wentTo, "and the launcher went to that page")
    }

    /** The floating edit bar of the widget grid, with every button showing, at normal and large text. */
    @Test
    fun launcherGridEditBar() {
        val store = started()
        val model = AppModel(store)
        val prefs = app.pokeplan.ui.launcher.LauncherPrefs().withPages(3)
        // On Home the bar offers a page at the left or the right end; on an empty page of the user's own it offers to remove it.
        for ((name, scale, shown) in listOf(Triple("launcher-edit-bar", 1f, app.pokeplan.ui.launcher.WidgetPage.PAGE3), Triple("launcher-edit-bar-xl", 1.3f, app.pokeplan.ui.launcher.WidgetPage.PAGE3), Triple("launcher-edit-bar-home", 1f, app.pokeplan.ui.launcher.WidgetPage.HOME), Triple("launcher-edit-bar-home-xl", 1.3f, app.pokeplan.ui.launcher.WidgetPage.HOME))) {
            val env = app.pokeplan.ui.launcher.WidgetEnv(model, fakeApps, prefs, fakeHost, null, 3, app.pokeplan.ui.launcher.TodayState(store.today), app.pokeplan.ui.launcher.BuddyMood(), false, true, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, pages = 3)
            render(name, height = 900, fontScale = scale) {
                PixelTheme {
                    Box(Modifier.fillMaxSize().background(Pixel.Night), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                        app.pokeplan.ui.launcher.GridHomePage(env, null)
                        app.pokeplan.ui.launcher.GridEditBar(env, shown)
                    }
                }
            }
        }
    }

    @Test
    fun launcherList() {
        val many = fakeApps + ('D'..'L').flatMap { c -> listOf(1, 2).map { app.pokeplan.ui.launcher.LauncherApp("pkg.$c$it", "$c app $it") } }
        render("launcher-list") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.AppListPage(many, app.pokeplan.ui.launcher.LauncherPrefs(), fakeHost, {}, {}) } }
        }
        for (town in listOf("lush-prairie", "drenched-bluff", "dark-hill")) {
            val store = started()
            listOf(1, 4, 7, 39, 52, 54, 94, 133, 143, 150, 151, 25, 175, 183).forEach { store.grantPokemon(it, "test") }
            val prefs = app.pokeplan.ui.launcher.LauncherPrefs().withScene(town).pin("pkg.Phone").pin("pkg.Messages")
            render("launcher-town-$town") { app.pokeplan.ui.launcher.LauncherScreen(AppModel(store), fakeApps, prefs, {}, isDefault = true, phone = fakeHost) }
        }
        val planned = started()
        planned.addTask("Write the report", Rank.B, planned = planned.today, estimateMinutes = 45)
        planned.addTask("Reply to mail", Rank.E, planned = planned.today)
        planned.addEvent("Gym", planned.today, kotlinx.datetime.LocalTime(18, 30))
        render("launcher-today") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.TodayPage(env(AppModel(planned))) } } }
        app.pokeplan.ui.launcher.Weather.entries.filter { it != app.pokeplan.ui.launcher.Weather.CLEAR && it != app.pokeplan.ui.launcher.Weather.LIVE }.forEach { weather ->
            val sky = started().also { st -> listOf(1, 4, 25, 133, 150).forEach { st.grantPokemon(it, "test") } }
            render("launcher-weather-${weather.name.lowercase()}") {
                app.pokeplan.ui.launcher.LauncherScreen(AppModel(sky), fakeApps, app.pokeplan.ui.launcher.LauncherPrefs().copy(weather = weather), {}, isDefault = true, phone = fakeHost)
            }
        }
        // A phone's size and density (2.625), where a box's width is not a multiple of the pixel scale.
        val phone = started().also { st -> listOf(1, 4, 25, 133, 150).forEach { st.grantPokemon(it, "test") } }
        render("launcher-phone", width = 923, height = 1990, density = 2.625f) {
            app.pokeplan.ui.launcher.LauncherScreen(AppModel(phone), fakeApps, app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone").pin("pkg.Messages"), {}, isDefault = true, phone = fakeHost)
        }
        val days = listOf("2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04").mapIndexed { n, d -> app.pokeplan.ui.launcher.DayForecast(d, listOf(61, 3, 0, 71, 95)[n], 31.0 - n, 25.0) }
        val sample = app.pokeplan.ui.launcher.Forecast("Manila, Philippines", 29.6, 61, days, 0L)
        val withFolder = app.pokeplan.ui.launcher.LauncherPrefs().addFolder("Work").addToFolder("Work", "pkg.Phone").addToFolder("Work", "pkg.Messages").addFolder("Play")
        render("launcher-forecast") { PixelTheme { app.pokeplan.ui.launcher.ForecastPage(sample, app.pokeplan.ui.launcher.LauncherPrefs(), 2, {}, {}) } }
        render("launcher-folders") { PixelTheme { app.pokeplan.ui.launcher.FoldersPage(fakeApps, withFolder, {}, fakeHost, null, {}, {}, 2) } }
        render("launcher-folder") { PixelTheme { app.pokeplan.ui.launcher.FoldersPage(fakeApps, withFolder, {}, fakeHost, "Work", {}, {}, 2) } }
        val folderWidgets = app.pokeplan.ui.launcher.LauncherPrefs().addFolderWidget("Work").addToFolder("Work", "pkg.Phone").addToFolder("Work", "pkg.Messages").addFolderWidget("Play")
        render("launcher-widgets") { PixelTheme { app.pokeplan.ui.launcher.WidgetsPage(folderWidgets, {}, fakeHost, 2, {}) } }
        render("launcher-phone-folders", width = 923, height = 1990, density = 2.625f) {
            app.pokeplan.ui.launcher.LauncherScreen(AppModel(phone), fakeApps, folderWidgets, {}, isDefault = true, phone = fakeHost)
        }
        render("launcher-editing") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Night)) { app.pokeplan.ui.launcher.TodayPage(env(AppModel(planned), app.pokeplan.ui.launcher.LauncherPrefs().resizeWidget(app.pokeplan.ui.launcher.WidgetSlot(app.pokeplan.ui.launcher.WidgetArea.TODAY, app.pokeplan.ui.launcher.WidgetKind.CALENDAR), app.pokeplan.ui.launcher.WidgetSize.SMALL), editing = true)) } } }
        val moody = app.pokeplan.ui.launcher.BuddyMood().also { m -> m.meet(phone.data.player.pokemon.last(), app.pokeplan.ui.Emotion.SURPRISED, app.pokeplan.ui.Emotion.HAPPY) }
        render("launcher-buddy") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Night)) { androidx.compose.foundation.layout.Column(Modifier.padding(20.dp)) {
            app.pokeplan.ui.launcher.LauncherWidget(app.pokeplan.ui.launcher.WidgetSlot(app.pokeplan.ui.launcher.WidgetArea.HOME_TOP, app.pokeplan.ui.launcher.WidgetKind.BUDDY), env(AppModel(phone)).let { e -> app.pokeplan.ui.launcher.WidgetEnv(e.model, e.apps, e.prefs, e.host, null, 2, e.today, moody, false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) })
        } } } }
        render("launcher-towns") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.TownPicker("meadow", {}, {}) } } }
        render("launcher-pokemon-menu") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.PokemonMenu(started().data.player.buddy!!, false, 2, {}, {}, {}) } } }
        render("launcher-settings") { PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.LauncherSettings(AppModel(started()), app.pokeplan.ui.launcher.LauncherPrefs().pin("pkg.Phone"), {}, false, 2, 2, fakeHost, {}, {}, {}, {}, {}) } } }
        render("launcher-sheet") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.launcher.AppSheet(fakeApps[2], app.pokeplan.ui.launcher.LauncherPrefs(), {}, {}, fakeHost) } }
        }
    }

    @Test
    fun appWithTownBackground() {
        val store = started()
        store.changeSettings { it.copy(appBackground = "lush-prairie") }
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("app-town-background") { App(storage, focusTab = true) }
    }

    /** Tablet and desktop widths: the page stays a phone-width column, and from 840 dp the tabs move to a rail. */
    @Test
    fun wideWindows() {
        val store = started()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("wide-home-medium", width = 900, height = 700, density = 1f) { App(storage, focusTab = true) }
        render("wide-home-expanded", width = 1280, height = 800, density = 1f) { App(storage, focusTab = true) }
    }
}
