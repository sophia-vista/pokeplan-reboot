package app.pokeplan.app

import app.pokeplan.app.cloud.CloudClient
import app.pokeplan.app.cloud.CloudConfig
import app.pokeplan.app.cloud.SyncManager
import app.pokeplan.app.cloud.SyncStatus
import app.pokeplan.core.reward.RewardRules
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cloud sync against a pretend Supabase: sign-in, first upload, adoption, conflicts and going offline. */
class SyncManagerTest {
    private class Server {
        var version = 0
        var saved: JsonObject? = null
        var pushedRules: String? = null
        var offline = false
        var refuseSignIn = false
        var deleted = false
        val requests = mutableListOf<Pair<String, String>>()
        val ticketReports = mutableListOf<List<String>>()
        val ticketDevices = mutableListOf<String>()
        val spentTickets = mutableSetOf<String>()

        fun engine() = MockEngine { request ->
            val path = request.url.encodedPath + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            requests += request.method.value to path
            if (offline) throw RuntimeException("no network")
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path.startsWith("/auth/v1/token?grant_type=password") ->
                    if (refuseSignIn) respond("""{"error_description":"Invalid login credentials"}""", HttpStatusCode.BadRequest, json)
                    else respond(session(body["email"]!!.jsonPrimitive.content), headers = json)
                path.startsWith("/auth/v1/token?grant_type=refresh_token") -> respond(session("a@b.c", token = "refreshed"), headers = json)
                path.startsWith("/auth/v1/signup") -> respond("""{"id":"u1","email":"${body["email"]!!.jsonPrimitive.content}"}""", headers = json)
                path == "/functions/v1/reward" -> when (body["kind"]!!.jsonPrimitive.content) {
                    "pull" -> respond(
                        """{"version":$version,"data":${saved?.toString() ?: "null"}}""", headers = json,
                    )
                    "tickets" -> {
                        ticketDevices += body["device"]!!.jsonPrimitive.content
                        val spent = body["spent"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
                        ticketReports += spent
                        spentTickets += spent
                        val stock = listOf("s1", "s2", "s3").filter { it !in spentTickets }
                            .joinToString(",", "[", "]") { "{\"id\":\"$it\",\"seed\":${it.drop(1)},\"issuedAtMillis\":0}" }
                        respond("{\"tickets\":$stock,\"refused\":[]}", headers = json)
                    }
                    "delete-account" -> {
                        deleted = body["confirm"]!!.jsonPrimitive.content == "DELETE"
                        saved = null
                        respond("""{"deleted":true}""", headers = json)
                    }
                    else -> {
                        val expected = body["expectedVersion"]!!.jsonPrimitive.content.toInt()
                        if (expected != version) respond("""{"error":"the save changed somewhere else","version":$version}""", HttpStatusCode.Conflict, json)
                        else {
                            saved = body["data"]!!.jsonObject
                            pushedRules = body["rulesVersion"]?.jsonPrimitive?.content
                            version++
                            respond("""{"version":$version}""", headers = json)
                        }
                    }
                }
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }

        private fun session(email: String, token: String = "token") =
            """{"access_token":"$token","refresh_token":"r-$token","expires_in":3600,"user":{"id":"u1","email":"$email"}}"""
    }

    private class Device(server: Server, startMillis: Long = 1_000_000L) {
        class TestClock(var millis: Long) : Clock {
            override fun nowMillis() = millis
        }
        val clock = TestClock(startMillis)
        val storage = MemoryStorage()
        val store = GameStore(storage, clock, TimeZone.UTC, Random(1))
        val sync = SyncManager(store, CloudClient(CloudConfig("https://x.supabase.co", "anon"), HttpClient(server.engine())) { clock.millis })
    }

    @Test
    fun aGuestWhoSignsInUploadsTheirProgress() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        assertNull(d.sync.signIn("a@b.c", "pw"))
        assertEquals(1, server.version)
        assertNotNull(server.saved)
        assertEquals("Sophia", server.saved!!["trainerName"]!!.jsonPrimitive.content)
        assertFalse(d.store.data.account!!.unsynced)
        assertEquals(SyncStatus.Idle, d.sync.status.value)
        assertFalse("account" in server.saved!!, "the account never leaves the device")
        assertEquals(RewardRules.VERSION.toString(), server.pushedRules, "the push says which rules the app ran")
    }

    @Test
    fun changesAreUploadedOnTheNextSync() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        d.store.addTask("Buy milk")
        assertTrue(d.store.data.account!!.unsynced, "a change marks the account as unsynced")
        d.sync.sync()
        assertEquals(2, server.version)
        assertFalse(d.store.data.account!!.unsynced)
    }

    @Test
    fun syncFillsTheEggTicketsAndReportsTheSpentOnes() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        assertEquals(listOf("s1", "s2", "s3"), d.store.data.eggTickets.stock.map { it.id })
        assertEquals(listOf(emptyList<String>()), server.ticketReports)

        d.store.startFocus(25, deepFocus = false)
        d.clock.millis += 25 * 60_000L + 1
        d.store.tickFocus()
        assertEquals(listOf("s1"), d.store.data.eggTickets.spent.map { it.id })
        d.sync.sync()
        assertEquals(listOf("s1"), server.ticketReports.last(), "the spent ticket is reported")
        assertTrue(d.store.data.eggTickets.spent.isEmpty(), "and forgotten once the server has it")
        assertEquals(listOf("s2", "s3"), d.store.data.eggTickets.stock.map { it.id })
        assertEquals(1, server.ticketDevices.toSet().size, "the device keeps its id")

        server.offline = true
        d.sync.sync()
        assertIs<SyncStatus.Offline>(d.sync.status.value)
        assertEquals(listOf("s2", "s3"), d.store.data.eggTickets.stock.map { it.id }, "offline, the stock stays as it was")
    }

    @Test
    fun aNewDeviceAdoptsTheCloudSave() = runTest {
        val server = Server()
        val phone = Device(server)
        phone.store.start("Sophia", 25)
        phone.store.addTask("Buy milk")
        phone.sync.signIn("a@b.c", "pw")

        val desktop = Device(server) // never played on
        assertNull(desktop.sync.signIn("a@b.c", "pw"))
        assertEquals("Sophia", desktop.store.data.trainerName)
        assertEquals(listOf("Buy milk"), desktop.store.data.tasks.map { it.title }.filter { it == "Buy milk" })
        assertEquals(SyncStatus.Idle, desktop.sync.status.value)
    }

    @Test
    fun whenBothSidesChangedThePlayerChooses() = runTest {
        val server = Server()
        val phone = Device(server)
        phone.store.start("Phone", 25)
        phone.sync.signIn("a@b.c", "pw")

        val desktop = Device(server)
        desktop.store.start("Desktop", 4)
        desktop.sync.signIn("a@b.c", "pw")
        val conflict = assertIs<SyncStatus.Conflict>(desktop.sync.status.value)
        assertEquals(1, conflict.serverVersion)

        desktop.sync.resolve(keepThisDevice = false)
        assertEquals("Phone", desktop.store.data.trainerName)
        assertEquals(SyncStatus.Idle, desktop.sync.status.value)
    }

    @Test
    fun keepingThisDeviceOverwritesTheCloud() = runTest {
        val server = Server()
        val phone = Device(server)
        phone.store.start("Phone", 25)
        phone.sync.signIn("a@b.c", "pw")
        val desktop = Device(server)
        desktop.store.start("Desktop", 4)
        desktop.sync.signIn("a@b.c", "pw")
        desktop.sync.resolve(keepThisDevice = true)
        assertEquals("Desktop", server.saved!!["trainerName"]!!.jsonPrimitive.content)
        assertEquals(SyncStatus.Idle, desktop.sync.status.value)
    }

    @Test
    fun aStalePushBecomesAConflict() = runTest {
        val server = Server()
        val phone = Device(server)
        phone.store.start("Phone", 25)
        phone.sync.signIn("a@b.c", "pw")
        val desktop = Device(server)
        desktop.sync.signIn("a@b.c", "pw") // adopts version 1
        phone.store.addTask("From the phone")
        phone.sync.sync() // version 2
        desktop.store.addTask("From the desktop")
        desktop.sync.sync()
        assertIs<SyncStatus.Conflict>(desktop.sync.status.value)
    }

    @Test
    fun offlineKeepsTheChangesForLater() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        d.store.addTask("Buy milk")
        server.offline = true
        d.sync.sync()
        assertIs<SyncStatus.Offline>(d.sync.status.value)
        assertTrue(d.store.data.account!!.unsynced)
        server.offline = false
        d.sync.sync()
        assertEquals(SyncStatus.Idle, d.sync.status.value)
        assertFalse(d.store.data.account!!.unsynced)
    }

    @Test
    fun aWrongPasswordIsReportedAndLeavesTheDeviceSignedOut() = runTest {
        val server = Server().apply { refuseSignIn = true }
        val d = Device(server)
        assertEquals("Invalid login credentials", d.sync.signIn("a@b.c", "wrong"))
        assertNull(d.store.data.account)
        assertEquals(SyncStatus.SignedOut, d.sync.status.value)
    }

    @Test
    fun signingUpAsksToConfirmTheEmail() = runTest {
        val d = Device(Server())
        assertEquals("confirm", d.sync.signUp("new@b.c", "pw"))
        assertNull(d.store.data.account)
    }

    @Test
    fun anExpiringSessionIsRefreshedFirst() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        d.clock.millis += 4_000_000L // past the one hour the session lasted
        d.sync.sync()
        assertTrue(server.requests.any { it.second.contains("grant_type=refresh_token") })
        assertEquals("refreshed", d.store.data.account!!.accessToken)
    }

    @Test
    fun signingOutKeepsTheLocalProgress() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        d.sync.signOut()
        assertNull(d.store.data.account)
        assertEquals("Sophia", d.store.data.trainerName)
        assertEquals(SyncStatus.SignedOut, d.sync.status.value)
    }

    @Test
    fun deletingTheAccountClearsTheServerAndKeepsThisDeviceAsAGuest() = runTest {
        val server = Server()
        val d = Device(server)
        d.store.start("Sophia", 25)
        d.sync.signIn("a@b.c", "pw")
        assertNotNull(server.saved)
        assertNull(d.sync.deleteAccount())
        assertTrue(server.deleted)
        assertNull(server.saved)
        assertNull(d.store.data.account)
        assertEquals(SyncStatus.SignedOut, d.sync.status.value)
        assertEquals("Sophia", d.store.data.trainerName, "the device keeps its progress")
    }
}
