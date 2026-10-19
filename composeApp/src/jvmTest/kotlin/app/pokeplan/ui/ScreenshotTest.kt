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
import kotlin.test.assertTrue

/**
 * Draws screens offscreen and writes PNGs to composeApp/build/screenshots, so they can be looked at without a device.
 * The assertion only checks that something was drawn.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private fun render(name: String, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = 540, height = 1080, density = Density(1.5f)) { content() }
        var nanos = 0L
        // Sprites load asynchronously, so draw a few frames with a short wait between them.
        repeat(60) {
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
    fun planner() {
        val store = started()
        store.addTask("Write the report", Rank.B, planned = store.today, estimateMinutes = 45)
        store.addTask("Reply to mail", Rank.E, planned = store.today)
        val model = AppModel(store)
        store.addSubtask(store.data.tasks.last().id, "Open the draft")
        render("planner") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model) } }
        }
        render("planner-today") {
            PixelTheme { Box(Modifier.fillMaxSize().background(Pixel.Paper)) { PlannerScreen(model, startView = 0) } }
        }
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

    private fun storeWithTeam(): GameStore {
        val store = started()
        repeat(4) {
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

    private fun collection(name: String, section: Int, open: Boolean = false) {
        val store = storeWithTeam()
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
    fun collectionDex() = collection("collection-dex", 2)

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
    fun meSettings() = me("me-settings", 3)

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
