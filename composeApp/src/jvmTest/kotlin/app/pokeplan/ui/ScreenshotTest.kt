package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
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
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlin.test.assertTrue

/**
 * Draws screens offscreen and writes PNGs to composeApp/build/screenshots, so they can be looked at without a device.
 * The assertion only checks that something was drawn.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private fun render(name: String, frames: Int = 60, fontScale: Float = 1f, height: Int = 1080, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 540, height = height, density = Density(1.5f, fontScale)) { content() }
        var nanos = 0L
        // Sprites load asynchronously, so draw a few frames with a short wait between them.
        repeat(frames) {
            scene.render(nanos)
            nanos += 50_000_000
            Thread.sleep(20)
        }
        val bytes = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)
        assertTrue(bytes.size > 5_000, "$name drew almost nothing")
        scene.close()
    }

    private val clock = object : Clock {
        var millis = System.currentTimeMillis()
        override fun nowMillis() = millis
    }

    private fun started(): GameStore = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25) }

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
    fun homeShinyBuddy() {
        val store = started()
        val data = store.data.copy(player = store.data.player.copy(pokemon = store.data.player.pokemon.map { it.copy(isShiny = true) }))
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), data.copy(lastSeenVersion = app.pokeplan.ui.AppVersion.NAME)))
        render("home-shiny", frames = 47) { App(storage, focusTab = true) }
    }

    @Test
    fun homeTorchicAfterAnEvent() {
        val store = GameStore(MemoryStorage(), clock).also { it.start("Sophia", 255) }
        store.completeTask(store.data.tasks.single().id)
        clock.millis += 11_000
        store.tick()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        // Stop early: a reaction lasts about two seconds of animation time, so it is on screen at frame 30.
        render("home-torchic-event", frames = 30) { App(storage, focusTab = true) }
    }

    @Test
    fun homeBuddyAsleepDuringFocus() {
        val store = started()
        store.startFocus(25, deepFocus = false)
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render("home-focus-asleep") { App(storage, focusTab = true) }
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
        render("planner-week", height = 2400) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 1, startWeek = true) } }
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

    private fun collection(name: String, section: Int, open: Boolean = false, hatched: Int = 4, setup: (GameStore) -> Unit = {}) {
        val store = storeWithTeam(hatched).also(setup)
        val model = AppModel(store)
        render(name) {
            PixelTheme {
                Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                    app.pokeplan.ui.screens.CollectionScreen(model, section, if (open) store.data.player.buddyId else null)
                }
            }
        }
    }

    @Test
    fun collectionParty() = collection("collection-party", 0)

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

    @Test
    fun evolution() = cutscene("evolution", frames = 30, still = true) {
        val before = it.grantPokemon(4, "test")
        app.pokeplan.ui.screens.Cutscene.Evolve(before, before.evolvedInto(5, "charmeleon"))
    }

    private fun dark(name: String, tab: Tab) {
        val store = storeWithTeam()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.addTask("Reply to mail", Rank.E, planned = store.today)
        store.changeSettings { it.copy(theme = app.pokeplan.app.ThemeMode.DARK) }
        // Unseen hatches would play their scene over the screen.
        store.markRewardsSeen()
        val storage = MemoryStorage(GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), store.data))
        render(name) { App(storage, focusTab = true, startTab = tab) }
    }

    @Test
    fun darkHome() = dark("dark-home", Tab.HOME)

    @Test
    fun darkPlanner() = dark("dark-planner", Tab.PLANNER)

    @Test
    fun darkFocus() = dark("dark-focus", Tab.FOCUS)

    @Test
    fun darkPokemon() = dark("dark-pokemon", Tab.COLLECTION)

    @Test
    fun darkMe() = dark("dark-me", Tab.ME)

    @Test
    fun collectionPc() = collection("collection-pc", 1, hatched = 14)

    @Test
    fun collectionDex() = collection("collection-dex", 2) { s ->
        s.markSeen(app.pokeplan.core.dex.Dex.regionDex(app.pokeplan.core.dex.Region.HOME).take(2).map { it.id })
    }

    @Test
    fun collectionBag() = collection("collection-bag", 3)

    @Test
    fun pokemonDetail() = collection("pokemon-detail", 0, open = true)

    private fun me(name: String, section: Int) {
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
        val model = AppModel(store)
        render(name) {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.MeScreen(model, section) } }
        }
    }

    @Test
    fun meGrowth() = me("me-growth", 0)

    @Test
    fun meGoals() = me("me-goals", 1)

    @Test
    fun meReview() = me("me-review", 2)

    @Test
    fun meSettings() = me("me-settings", 4)

    @Test
    fun meHelp() = me("me-help", 5)

    @Test
    fun collectionGuardians() {
        val base = started()
        val text = GameStore.json.encodeToString(app.pokeplan.app.AppData.serializer(), base.data.copy(player = base.data.player.copy(trainerPoints = 3_300)))
        val store = GameStore(MemoryStorage(text), clock)
        val ids = listOf("uxie", "mew", "celebi").map { name ->
            store.grantPokemon(app.pokeplan.core.dex.Dex.species.first { it.identifier == name }.id, "Quest").id
        }
        store.slotGuardian(ids[0])
        val model = AppModel(store)
        render("collection-guardians") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.CollectionScreen(model, 4) } }
        }
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

    @Test
    fun friends() {
        val store = storeWithTeam()
        store.signedIn("sophia@example.com", "me-1", "token", "refresh", Long.MAX_VALUE)
        val engine = io.ktor.client.engine.mock.MockEngine { request ->
            val path = request.url.encodedPath
            val answer = when {
                path.endsWith("/profiles") -> """[{"friend_code":"7F3A91C2"}]"""
                path.endsWith("/friend_cards") ->
                    """[{"user_id":"f1","trainer_name":"Bob","rank":3,"status":"accepted","incoming":false,"buddy_species":6,"buddy_form":"charizard","buddy_shiny":false,"buddy_level":32},
                        {"user_id":"f2","trainer_name":"Carol","rank":1,"status":"pending","incoming":true}]"""
                path.endsWith("/weekly_leaderboard") ->
                    """[{"user_id":"f1","trainer_name":"Bob","rank":3,"points":140,"is_me":false},{"user_id":"me-1","trainer_name":"Sophia","rank":1,"points":96,"is_me":true}]"""
                path.endsWith("/gifts") -> """[{"id":"g1","sender":"f1","item_id":"rare-candy","amount":3}]"""
                else -> "{}"
            }
            respond(answer, io.ktor.http.HttpStatusCode.OK, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }
        val client = app.pokeplan.app.cloud.CloudClient(app.pokeplan.app.cloud.CloudConfig("https://x.supabase.co", "anon"), io.ktor.client.HttpClient(engine)) { clock.millis }
        val model = AppModel(store, null, app.pokeplan.app.cloud.SyncManager(store, client), app.pokeplan.app.cloud.SocialClient(client))
        render("friends") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.SectionScaffold("Friends", null, {}) { app.pokeplan.ui.screens.FriendsView(model) } } }
        }
    }
}
