package app.pokeplan.app

import app.pokeplan.app.cloud.CloudClient
import app.pokeplan.app.cloud.CloudConfig
import app.pokeplan.app.cloud.CloudResult
import app.pokeplan.app.cloud.Session
import app.pokeplan.app.cloud.SocialClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialClientTest {
    private class Call(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private val session = Session("token", "refresh", 0, "me-1", "me@x.y")
    private val calls = mutableListOf<Call>()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, answer: (String) -> String): SocialClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
            val body = request.body.toByteArray().decodeToString()
            calls += Call(request.method.value, path, request.headers.entries().associate { it.key to it.value.first() }, body)
            respond(answer(path), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return SocialClient(CloudClient(CloudConfig("https://x.supabase.co", "anon"), HttpClient(engine)) { 0L })
    }

    @Test
    fun friendsAreReadFromTheFriendCardsFunction() = runTest {
        val social = client {
            """[{"user_id":"f1","trainer_name":"Bob","rank":3,"status":"accepted","incoming":false,"buddy_species":25,"buddy_form":"pikachu","buddy_shiny":true,"buddy_level":12},
                {"user_id":"f2","trainer_name":"Carol","rank":0,"status":"pending","incoming":true,"buddy_species":null,"buddy_form":null,"buddy_shiny":null,"buddy_level":null}]"""
        }
        val result = social.friends(session)
        val cards = (result as CloudResult.Ok).value
        assertEquals(2, cards.size)
        assertEquals("Bob", cards[0].trainerName)
        assertTrue(cards[0].accepted && cards[0].buddyShiny)
        assertEquals(25, cards[0].buddySpecies)
        assertTrue(!cards[1].accepted && cards[1].incoming)
        assertNull(cards[1].buddySpecies)

        val call = calls.single()
        assertEquals("POST", call.method)
        assertEquals("/rest/v1/rpc/friend_cards", call.path)
        assertEquals("platform", call.headers["Content-Profile"])
        assertEquals("platform", call.headers["Accept-Profile"])
        assertEquals("Bearer token", call.headers["Authorization"])
        assertEquals("anon", call.headers["apikey"])
    }

    @Test
    fun theLeaderboardIsParsed() = runTest {
        val social = client { """[{"user_id":"me-1","trainer_name":"Me","rank":1,"points":40,"is_me":true},{"user_id":"f1","trainer_name":"Bob","rank":3,"points":10,"is_me":false}]""" }
        val rows = (social.leaderboard(session) as CloudResult.Ok).value
        assertEquals(listOf("Me", "Bob"), rows.map { it.trainerName })
        assertEquals(40, rows[0].points)
        assertTrue(rows[0].isMe && !rows[1].isMe)
    }

    @Test
    fun theFriendCodeComesFromTheOwnProfileRow() = runTest {
        val social = client { """[{"friend_code":"ABCD1234"}]""" }
        assertEquals("ABCD1234", (social.myFriendCode(session) as CloudResult.Ok).value)
        val call = calls.single()
        assertEquals("GET", call.method)
        assertEquals("/rest/v1/profiles?select=friend_code&user_id=eq.me-1", call.path)
        assertEquals("platform", call.headers["Accept-Profile"])
        assertNull(call.headers["Content-Profile"], "reads do not set a content profile")
    }

    @Test
    fun unopenedGiftsAreListed() = runTest {
        val social = client { """[{"id":"g1","sender":"f1","item_id":"rare-candy","amount":3}]""" }
        val gifts = (social.gifts(session) as CloudResult.Ok).value
        assertEquals("rare-candy", gifts.single().itemId)
        assertEquals(3, gifts.single().amount)
        assertTrue("opened_at=is.null" in calls.single().path)
    }

    @Test
    fun actionsSendTheirArguments() = runTest {
        val social = client { "null" }
        assertIs<CloudResult.Ok<Unit>>(social.addFriend(session, " abcd1234 "))
        assertIs<CloudResult.Ok<Unit>>(social.respond(session, "f2", true))
        assertIs<CloudResult.Ok<Unit>>(social.sendGift(session, "f1"))
        assertIs<CloudResult.Ok<Unit>>(social.removeFriend(session, "f1"))
        assertEquals(listOf("add_friend", "respond_to_request", "send_gift", "remove_friend"), calls.map { it.path.substringAfterLast('/') })
        assertEquals("abcd1234", Json.parseToJsonElement(calls[0].body).jsonObject["p_code"]!!.jsonPrimitive.content)
        val respond = Json.parseToJsonElement(calls[1].body).jsonObject
        assertEquals("f2", respond["p_requester"]!!.jsonPrimitive.content)
        assertEquals("true", respond["p_accept"]!!.jsonPrimitive.content)
    }

    @Test
    fun openingAGiftReturnsWhatWasInIt() = runTest {
        val social = client { """{"item":"rare-candy","amount":3}""" }
        val gift = (social.openGift(session, "g1") as CloudResult.Ok).value
        assertEquals("rare-candy", gift.itemId)
        assertEquals(3, gift.amount)
    }

    @Test
    fun serverRefusalsKeepTheirMessage() = runTest {
        val social = client(HttpStatusCode.BadRequest) { """{"message":"no player has that friend code","code":"P0002"}""" }
        val result = social.addFriend(session, "NOPE")
        assertEquals("no player has that friend code", (result as CloudResult.Failure).message)
        assertEquals(400, result.status)
    }
}
