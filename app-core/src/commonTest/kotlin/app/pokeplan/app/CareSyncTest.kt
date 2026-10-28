package app.pokeplan.app

import app.pokeplan.app.cloud.CareSync
import app.pokeplan.app.cloud.CloudClient
import app.pokeplan.app.cloud.CloudConfig
import app.pokeplan.app.cloud.SyncManager
import app.pokeplan.app.cloud.SyncStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Care's opt-in cloud copy against a pretend reward function: off by default, uploads, adoption, conflicts, turning off. */
class CareSyncTest {
    private class Server {
        var careVersion = 0
        var care: JsonObject? = null
        var planPushes = 0
        var offline = false
        val kinds = mutableListOf<String>()

        fun engine() = MockEngine { request ->
            val path = request.url.encodedPath
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            if (offline) throw RuntimeException("no network")
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path.startsWith("/auth/v1/token") ->
                    respond("""{"access_token":"t","refresh_token":"r","expires_in":3600,"user":{"id":"u1","email":"a@b.c"}}""", headers = json)
                path == "/functions/v1/reward" -> when (val kind = body["kind"]!!.jsonPrimitive.content.also { kinds += it }) {
                    "pull" -> respond("""{"version":0,"data":null}""", headers = json)
                    "push" -> { planPushes++; respond("""{"version":$planPushes}""", headers = json) }
                    "tickets" -> respond("""{"tickets":[],"refused":[]}""", headers = json)
                    "care-pull" -> respond("""{"version":$careVersion,"data":${care?.toString() ?: "null"}}""", headers = json)
                    "care-delete" -> { val had = care != null; care = null; careVersion = 0; respond("""{"deleted":$had}""", headers = json) }
                    "care-push" -> {
                        val expected = body["expectedVersion"]!!.jsonPrimitive.content.toInt()
                        if (expected != careVersion) respond("""{"error":"changed","version":$careVersion}""", HttpStatusCode.Conflict, json)
                        else { care = body["data"]!!.jsonObject; careVersion++; respond("""{"version":$careVersion}""", headers = json) }
                    }
                    else -> error("unexpected $kind")
                }
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
    }

    private class Device(server: Server) {
        val clock = object : Clock { override fun nowMillis() = 1_800_000_000_000L }
        val game = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(1)).also { it.start("Sophia", 25) }
        val care = CareStore(MemoryStorage(), clock, game, TimeZone.UTC, rng = Random(2))
        val client = CloudClient(CloudConfig("https://x.supabase.co", "anon"), HttpClient(server.engine())) { clock.nowMillis() }
        val sync = SyncManager(game, client)
        val careSync = CareSync(care, client) { sync.session() }
    }

    @Test
    fun offByDefaultNothingLeavesTheDevice() = runTest {
        val server = Server()
        val d = Device(server)
        d.care.addRoutine("Drink water")
        assertNull(d.sync.signIn("a@b.c", "pw"))
        d.careSync.sync()
        assertFalse(d.care.data.settings.cloudSync)
        assertTrue(server.kinds.none { it.startsWith("care-") })
        assertNull(server.care)
    }

    @Test
    fun turningItOnNeedsAnAccount() = runTest {
        val d = Device(Server())
        assertNotNull(d.careSync.turnOn())
        assertFalse(d.care.data.settings.cloudSync)
    }

    @Test
    fun turningItOnUploadsAndChangesFollow() = runTest {
        val server = Server()
        val d = Device(server)
        val routine = d.care.addRoutine("Drink water")
        d.care.write("A private note")
        d.sync.signIn("a@b.c", "pw")
        assertNull(d.careSync.turnOn())
        assertEquals(1, server.careVersion)
        val routines = server.care!!["routines"]!!.jsonArray
        assertEquals("Drink water", routines.single().jsonObject["title"]!!.jsonPrimitive.content)
        assertFalse("player" in server.care!!, "no game state in Care's copy")
        assertEquals("null", server.care!!["cloud"].toString(), "the device's cloud state stays on the device")
        assertEquals(0, server.care!!["visitorSeed"]!!.jsonPrimitive.content.toInt(), "visitors are seeded per device")
        assertFalse(d.care.data.cloud!!.unsynced)

        d.care.completeRoutine(routine.id)
        assertTrue(d.care.data.cloud!!.unsynced)
        d.careSync.sync()
        assertEquals(2, server.careVersion)
        assertFalse(d.care.data.cloud!!.unsynced)
        assertEquals(SyncStatus.Idle, d.careSync.status.value)
        assertTrue(server.kinds.count { it == "care-push" } == 2, "Care's copy goes apart from the Plan save")
    }

    @Test
    fun aNewDeviceTakesTheCloudCopy() = runTest {
        val server = Server()
        val first = Device(server)
        first.care.addRoutine("Stretch")
        first.sync.signIn("a@b.c", "pw")
        first.careSync.turnOn()

        val second = Device(server)
        second.sync.signIn("a@b.c", "pw")
        assertNull(second.careSync.turnOn())
        assertEquals("Stretch", second.care.data.routines.single().title)
        assertTrue(second.care.data.settings.cloudSync)
        assertEquals(1, second.care.data.cloud!!.version)
        assertEquals(1, server.careVersion, "taking the copy uploads nothing")
    }

    @Test
    fun whenBothChangedThePlayerChooses() = runTest {
        val server = Server()
        val first = Device(server)
        first.care.addRoutine("Stretch")
        first.sync.signIn("a@b.c", "pw")
        first.careSync.turnOn()

        val second = Device(server)
        second.care.addRoutine("Walk")
        second.sync.signIn("a@b.c", "pw")
        second.careSync.turnOn()
        assertIs<SyncStatus.Conflict>(second.careSync.status.value)
        assertEquals("Stretch", server.care!!["routines"]!!.jsonArray.single().jsonObject["title"]!!.jsonPrimitive.content)

        second.careSync.resolve(keepThisDevice = true)
        assertEquals(SyncStatus.Idle, second.careSync.status.value)
        assertEquals("Walk", server.care!!["routines"]!!.jsonArray.single().jsonObject["title"]!!.jsonPrimitive.content)

        first.care.addRoutine("Read")
        first.careSync.sync()
        assertIs<SyncStatus.Conflict>(first.careSync.status.value)
        first.careSync.resolve(keepThisDevice = false)
        assertEquals(listOf("Walk"), first.care.data.routines.map { it.title })
        assertTrue(first.care.data.settings.cloudSync)
    }

    @Test
    fun offlineKeepsTheChangeForLater() = runTest {
        val server = Server()
        val d = Device(server)
        d.sync.signIn("a@b.c", "pw")
        d.careSync.turnOn()
        d.care.addRoutine("Wind down")
        server.offline = true
        d.careSync.sync()
        assertIs<SyncStatus.Offline>(d.careSync.status.value)
        assertTrue(d.care.data.cloud!!.unsynced)
        server.offline = false
        d.careSync.sync()
        assertFalse(d.care.data.cloud!!.unsynced)
        assertEquals("Wind down", server.care!!["routines"]!!.jsonArray.single().jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun turningItOffDeletesTheCopy() = runTest {
        val server = Server()
        val d = Device(server)
        d.care.addRoutine("Stretch")
        d.sync.signIn("a@b.c", "pw")
        d.careSync.turnOn()
        assertNotNull(server.care)
        assertNull(d.careSync.turnOff())
        assertNull(server.care)
        assertFalse(d.care.data.settings.cloudSync)
        assertNull(d.care.data.cloud)
        assertEquals("Stretch", d.care.data.routines.single().title, "this device keeps everything")

        server.offline = true
        d.careSync.turnOn()
        assertNotNull(d.careSync.turnOff(), "offline, the copy cannot be deleted, so the switch stays on")
        assertTrue(d.care.data.settings.cloudSync)
    }
}
