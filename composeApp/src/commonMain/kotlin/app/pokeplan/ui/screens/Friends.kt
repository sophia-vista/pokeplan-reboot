package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.app.cloud.CloudResult
import app.pokeplan.app.cloud.FriendCard
import app.pokeplan.app.cloud.LeaderRow
import app.pokeplan.app.cloud.ReceivedGift
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.launch

/** Friends, this week's leaderboard and daily gifts. Needs an account; everything is read from the cloud when the screen opens. */
@Composable
fun FriendsView(model: AppModel) {
    val sync = model.sync
    val social = model.social
    if (sync == null || social == null || model.data.account == null) {
        TextBox { Text("Sign in under Settings to add friends, trade gifts and see the weekly board.", style = MaterialTheme.typography.bodyLarge) }
        return
    }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var friends by remember { mutableStateOf<List<FriendCard>>(emptyList()) }
    var board by remember { mutableStateOf<List<LeaderRow>>(emptyList()) }
    var gifts by remember { mutableStateOf<List<ReceivedGift>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var friendCode by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        val session = sync.session()
        if (session == null) { message = "Please sign in again."; loading = false; return }
        (social.myFriendCode(session) as? CloudResult.Ok)?.let { code = it.value }
        when (val r = social.friends(session)) { is CloudResult.Ok -> friends = r.value; is CloudResult.Failure -> message = r.message }
        // A friend's buddy counts as seen in the Pokédex.
        model.store.markSeen(friends.filter { it.accepted }.mapNotNull { f -> f.buddyForm?.let { Dex.form(it)?.speciesId } })
        (social.leaderboard(session) as? CloudResult.Ok)?.let { board = it.value }
        (social.gifts(session) as? CloudResult.Ok)?.let { gifts = it.value }
        loading = false
    }

    /** Runs a friend action, then reloads. A refusal's message is shown as it came (it is written to be read). */
    fun act(done: String? = null, call: suspend (app.pokeplan.app.cloud.Session) -> CloudResult<*>) {
        scope.launch {
            val session = sync.session() ?: return@launch
            message = when (val r = call(session)) { is CloudResult.Ok -> done; is CloudResult.Failure -> r.message }
            refresh()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    TextBox {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text("Your friend code", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(code ?: "...", style = MaterialTheme.typography.headlineMedium)
        }
    }
    TextBox {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(
                friendCode, { friendCode = it.uppercase().take(12) }, Modifier.weight(1f), singleLine = true,
                placeholder = "A friend's code",
            )
            PixelIconButton("plus", "Add", { val c = friendCode; friendCode = ""; act("Request sent.") { social.addFriend(it, c) } }, Pixel.InkSoft, enabled = friendCode.isNotBlank())
        }
    }
    message?.let { TextBox { Text(it, style = MaterialTheme.typography.bodyLarge) } }

    if (gifts.isNotEmpty()) {
        SectionLabel("Gifts for you")
        gifts.forEach { g ->
            ListRow(
                lead = { ItemIcon(g.itemId, size = 40.dp) },
                trail = {
                    PixelButton("Open", {
                        scope.launch {
                            // Save this device's changes first so the cloud and this device are in step before the gift lands.
                            sync.sync()
                            val session = sync.session() ?: return@launch
                            message = when (val r = social.openGift(session, g.id)) {
                                is CloudResult.Ok -> "You got ${r.value.amount} ${Items.name(r.value.itemId)}."
                                is CloudResult.Failure -> r.message
                            }
                            sync.sync()
                            refresh()
                        }
                    }, color = Pixel.Green, compact = true)
                },
            ) { Text("${g.amount} ${Items.name(g.itemId)}", style = MaterialTheme.typography.bodyLarge) }
        }
    }

    val requests = friends.filter { !it.accepted && it.incoming }
    if (requests.isNotEmpty()) {
        SectionLabel("Requests")
        requests.forEach { f ->
            ListRow(trail = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("Accept", { act { social.respond(it, f.userId, true) } }, color = Pixel.Green, compact = true)
                    PixelButton("Decline", { act { social.respond(it, f.userId, false) } }, color = Pixel.InkSoft, compact = true)
                }
            }) { Text(f.trainerName, style = MaterialTheme.typography.bodyLarge); Text("wants to be friends", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        }
    }

    SectionLabel("Friends")
    val accepted = friends.filter { it.accepted }
    if (accepted.isEmpty() && !loading) EmptyState("No friends yet", "Share your code or add a friend's to see their buddy and the weekly board.")
    // Removing a friend asks once, in place: it sat right beside Gift, where a slip of the thumb is easy.
    var removing by remember { mutableStateOf<String?>(null) }
    accepted.forEach { f ->
        ListRow(
            lead = {
                val form = f.buddyForm?.let { Dex.form(it) }
                if (form != null) Sprite(form.id, f.buddyShiny, f.trainerName, size = 56.dp) else ItemIcon("friend-ball", size = 40.dp)
            },
            trail = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (removing == f.userId) {
                        PixelIconButton("trash", "Remove", { removing = null; act { social.removeFriend(it, f.userId) } }, Pixel.Danger)
                        PixelButton("Keep", { removing = null }, color = Pixel.InkSoft, compact = true)
                    } else {
                        PixelButton("Gift", { act("Gift sent!") { social.sendGift(it, f.userId) } }, color = Pixel.Blue, compact = true)
                        PixelIconButton("trash", "Remove", { removing = f.userId }, Pixel.InkSoft)
                    }
                }
            },
        ) {
            Text(f.trainerName, style = MaterialTheme.typography.bodyLarge)
            Text(TrainerRank.names[f.rank.coerceIn(0, TrainerRank.names.lastIndex)] + (f.buddyLevel?.let { ", buddy Lv. $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }

    SectionLabel("This week")
    board.forEachIndexed { i, row ->
        ListRow(lead = { Text("${i + 1}", style = MaterialTheme.typography.titleMedium) }, trail = { Text("${row.points}", style = MaterialTheme.typography.titleMedium) }) {
            Text(row.trainerName + if (row.isMe) " (you)" else "", style = MaterialTheme.typography.bodyLarge, color = if (row.isMe) Pixel.Red else Pixel.Ink)
        }
    }
    PixelButton("Refresh", { scope.launch { refresh() } }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = !loading, compact = true)
}
