package app.pokeplan.app.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A friend, or a pending request (see [incoming]), as `platform.friend_cards()` returns it. */
data class FriendCard(
    val userId: String,
    val trainerName: String,
    val rank: Int,
    val accepted: Boolean,
    /** A pending request somebody sent to the player. */
    val incoming: Boolean,
    val buddySpecies: Int?,
    val buddyForm: String?,
    val buddyShiny: Boolean,
    val buddyLevel: Int?,
)

/** One line of this week's leaderboard among the player and their friends. */
data class LeaderRow(val userId: String, val trainerName: String, val rank: Int, val points: Int, val isMe: Boolean)

/** A gift sent to the player and not opened yet. */
data class ReceivedGift(val id: String, val senderId: String, val itemId: String, val amount: Int)

/** Friends, the weekly leaderboard and daily gifts, over the `platform` functions in supabase/migrations. */
class SocialClient(private val cloud: CloudClient) {
    suspend fun myFriendCode(session: Session): CloudResult<String> =
        when (val r = cloud.rows(session, "profiles", "select=friend_code&user_id=eq.${session.userId}")) {
            is CloudResult.Ok -> r.value.jsonArray.firstOrNull()?.jsonObject?.get("friend_code")?.jsonPrimitive?.contentOrNull
                ?.let { CloudResult.Ok(it) } ?: CloudResult.Failure("No profile yet.")
            is CloudResult.Failure -> r
        }

    suspend fun friends(session: Session): CloudResult<List<FriendCard>> =
        when (val r = cloud.rpc(session, "friend_cards")) {
            is CloudResult.Ok -> CloudResult.Ok(r.value.rowObjects().map { o ->
                FriendCard(
                    o.string("user_id"), o.string("trainer_name"), o.int("rank") ?: 0, o.string("status") == "accepted",
                    o.bool("incoming"), o.int("buddy_species"), o.stringOrNull("buddy_form"), o.bool("buddy_shiny"), o.int("buddy_level"),
                )
            })
            is CloudResult.Failure -> r
        }

    suspend fun leaderboard(session: Session): CloudResult<List<LeaderRow>> =
        when (val r = cloud.rpc(session, "weekly_leaderboard")) {
            is CloudResult.Ok -> CloudResult.Ok(r.value.rowObjects().map { o ->
                LeaderRow(o.string("user_id"), o.string("trainer_name"), o.int("rank") ?: 0, o.int("points") ?: 0, o.bool("is_me"))
            })
            is CloudResult.Failure -> r
        }

    suspend fun gifts(session: Session): CloudResult<List<ReceivedGift>> =
        when (val r = cloud.rows(session, "gifts", "select=id,sender,item_id,amount&recipient=eq.${session.userId}&opened_at=is.null&order=created_at")) {
            is CloudResult.Ok -> CloudResult.Ok(r.value.rowObjects().map { o ->
                ReceivedGift(o.string("id"), o.string("sender"), o.string("item_id"), o.int("amount") ?: 1)
            })
            is CloudResult.Failure -> r
        }

    suspend fun addFriend(session: Session, code: String): CloudResult<Unit> =
        unit(cloud.rpc(session, "add_friend", buildJsonObject { put("p_code", code.trim()) }))

    suspend fun respond(session: Session, requester: String, accept: Boolean): CloudResult<Unit> =
        unit(cloud.rpc(session, "respond_to_request", buildJsonObject { put("p_requester", requester); put("p_accept", accept) }))

    suspend fun removeFriend(session: Session, friend: String): CloudResult<Unit> =
        unit(cloud.rpc(session, "remove_friend", buildJsonObject { put("p_friend", friend) }))

    suspend fun sendGift(session: Session, friend: String): CloudResult<Unit> =
        unit(cloud.rpc(session, "send_gift", buildJsonObject { put("p_recipient", friend) }))

    /** Opens a gift: its items land in the player's bag on the server (the next sync brings them to this device). */
    suspend fun openGift(session: Session, giftId: String): CloudResult<ReceivedGift> =
        when (val r = cloud.rpc(session, "open_gift", buildJsonObject { put("p_gift", giftId) })) {
            is CloudResult.Ok -> {
                val o = r.value.jsonObject
                CloudResult.Ok(ReceivedGift(giftId, "", o.string("item"), o.int("amount") ?: 1))
            }
            is CloudResult.Failure -> r
        }

    private fun unit(r: CloudResult<JsonElement>): CloudResult<Unit> = when (r) {
        is CloudResult.Ok -> CloudResult.Ok(Unit)
        is CloudResult.Failure -> r
    }

    private fun JsonElement.rowObjects(): List<JsonObject> = (this as? JsonArray)?.map { it.jsonObject } ?: emptyList()
    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull ?: ""
    private fun JsonObject.stringOrNull(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
}
