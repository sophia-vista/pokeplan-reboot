package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.AbilityPerks
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.HeldItems
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.CareBucket
import app.pokeplan.core.pokemon.HealBucket
import app.pokeplan.core.pokemon.HealPerks
import app.pokeplan.core.pokemon.HomeBucket
import app.pokeplan.core.pokemon.HomePerks
import app.pokeplan.core.pokemon.LinkBucket
import app.pokeplan.core.pokemon.LinkPerks
import app.pokeplan.core.pokemon.MintBucket
import app.pokeplan.core.pokemon.MintPerks
import app.pokeplan.core.pokemon.CarePerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.PerkCondition
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.reward.Feature
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import kotlin.math.roundToInt

internal fun PerkBucket.label() = when (this) {
    PerkBucket.EGG_LUCK -> "Egg luck"
    PerkBucket.CANDY_YIELD -> "Candy yield"
    PerkBucket.SUPER_CANDY_CHANCE -> "Super Candy chance"
    PerkBucket.ITEM_FINDER -> "Item finder"
    PerkBucket.SHINY_HUNTER -> "Shiny hunter"
    PerkBucket.STREAK_KEEPER -> "Streak keeper"
    PerkBucket.COINS -> "Poké"
}

/** What a bucket does, in one line, so a percentage means something. */
private fun PerkBucket.effect() = when (this) {
    PerkBucket.EGG_LUCK -> "rarer eggs"
    PerkBucket.CANDY_YIELD -> "more Exp. Candies from tasks"
    PerkBucket.SUPER_CANDY_CHANCE -> "more Super Candies"
    PerkBucket.ITEM_FINDER -> "more item drops"
    PerkBucket.SHINY_HUNTER -> "better shiny odds"
    PerkBucket.STREAK_KEEPER -> "at ${Perks.STREAK_KEEPER_THRESHOLD.roundToInt()}%, a second missed day a week is forgiven"
    PerkBucket.COINS -> "more Poké"
}

private fun PerkCondition.label(): String? = when (this) {
    PerkCondition.ALWAYS -> null
    PerkCondition.FIRST_TASK_BEFORE_9AM -> "first task of the day, before 9am"
    PerkCondition.SHORT_SESSION -> "focus sessions of 5 to 20 minutes"
    PerkCondition.TOP_RANK_TASK -> "A-rank tasks"
}

private fun PerkSource.Kind.label() = when (this) {
    PerkSource.Kind.BUDDY_TYPE -> "buddy"
    PerkSource.Kind.ABILITY -> "ability"
    PerkSource.Kind.HELD_ITEM -> "held item"
    PerkSource.Kind.ASSIST -> "party assist"
    PerkSource.Kind.GUARDIAN -> "Guardian"
}

/** "+0.4%" under 10 (early perks are small, and should still read as something), "+12%" above. */
internal fun pct(v: Double): String = if (v < 10) "+${(v * 10).roundToInt() / 10.0}%".replace(".0%", "%") else "+${v.roundToInt()}%"

/** One bucket's total as a row: its name, a bar out of [cap] and the percent. Both parties' perk cards use it. */
@Composable
private fun PerkRow(label: String, value: Double, cap: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) { PixelBar((value / cap).toFloat().coerceIn(0f, 1f), color = Pixel.Green, label = "$label ${pct(value)}") }
        Text(pct(value), Modifier.size(width = 48.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/**
 * The party's perks, bucket by bucket: the total that applies now and, opened, where every part of it comes from (the
 * buddy's types and ability, its held item, the rest of the party, and Guardians, which sit outside the cap). Before the
 * rank that opens them it says when they will show; they already count.
 */
@Composable
fun PartyPerksCard(model: AppModel) {
    val store = model.store
    val totals = store.perks
    val sources = store.perkSources
    val buddy = model.data.player.planLead
    var open by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Party perks")
            if (!store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (buddy == null && sources.isEmpty()) {
                Text("Choose a buddy to get its perks.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            PerkBucket.entries.filter { totals[it] > 0 }.forEach { b -> PerkRow(b.label(), totals[b], Perks.BUCKET_CAP + Guardians.BUCKET_CAP) }
            if (totals.keepsFailedEgg) Text("Magic Guard: once a week a failed session of 5 minutes or more still hatches its egg.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            if (totals.extraForgivenDays > 0) Text("Streak keeper: a second missed day a week is forgiven.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            PixelButton(if (open) "Hide where they come from" else "Where they come from", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (open) {
                val byBucket = sources.groupBy { it.bucket }
                PerkBucket.entries.forEach { bucket ->
                    val parts = byBucket[bucket] ?: return@forEach
                    Text("${bucket.label()}: ${bucket.effect()}", style = MaterialTheme.typography.bodyLarge)
                    parts.forEach { s ->
                        val whenText = s.condition.label()?.let { ", on $it" } ?: ""
                        Text("  ${pct(s.percent)} ${s.from} (${s.kind.label()}$whenText)", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
                // Help: perks are capped per bucket (buddy, held item and party; Guardians on top) and only change what the module pays.
            }
        }
    }
}

/**
 * Care's party perks, laid out like [PartyPerksCard]: each bucket's total from the lead and the rest of Care's party and,
 * opened, where every part comes from. Like Plan's, they show from the rank that opens party perks.
 */
@Composable
fun CarePerksCard(model: AppModel) {
    val player = model.data.player
    val lead = player.careLead
    val party = player.partyOf(PartyIds.CARE)
    val totals = CarePerks.total(lead, party, model.data.player.guardianOf(app.pokeplan.core.pokemon.PartyIds.CARE))
    var open by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Care perks")
            if (!model.store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (lead == null) {
                return@Column
            }
            val shown = CareBucket.entries.filter { (totals[it] ?: 0.0) > 0 }
            shown.forEach { b -> PerkRow(b.label, totals[b] ?: 0.0, Perks.BUCKET_CAP) }
            PixelButton(if (open) "Hide where they come from" else "Where they come from", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (open) {
                val assists = party.filter { it.id != lead.id }.take(5)
                CareBucket.entries.forEach { bucket ->
                    val parts = buildList {
                        CarePerks.typePerks(lead)[bucket]?.let { add("  ${pct(it)} ${lead.nickname} (lead)") }
                        assists.forEach { a -> CarePerks.typePerks(a)[bucket]?.let { add("  ${pct(it * Perks.ASSIST_SHARE)} ${a.nickname} (party assist)") } }
                    }
                    if (parts.isEmpty()) return@forEach
                    Text("${bucket.label}: ${bucket.effect}", style = MaterialTheme.typography.bodyLarge)
                    parts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                }
            }
        }
    }
}

/** Heal's party perks, laid out like [CarePerksCard]. They only change what Field Research pays; nothing in HEAL's records depends on them. */
@Composable
fun HealPerksCard(model: AppModel) {
    val player = model.data.player
    val lead = player.healLead
    val party = player.partyOf(PartyIds.HEAL)
    val totals = HealPerks.total(lead, party, model.data.player.guardianOf(app.pokeplan.core.pokemon.PartyIds.HEAL))
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Heal perks")
            if (!model.store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (lead == null) {
                return@Column
            }
            val shown = HealBucket.entries.filter { (totals[it] ?: 0.0) > 0 }
            shown.forEach { b -> PerkRow(b.label, totals[b] ?: 0.0, Perks.BUCKET_CAP) }
        }
    }
}

/** Mint's party perks, laid out like [CarePerksCard]. They only change what Ledger Research pays; nothing in MINT's records depends on them. */
@Composable
fun MintPerksCard(model: AppModel) {
    val player = model.data.player
    val lead = player.mintLead
    val party = player.partyOf(PartyIds.MINT)
    val totals = MintPerks.total(lead, party, model.data.player.guardianOf(app.pokeplan.core.pokemon.PartyIds.MINT))
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Mint perks")
            if (!model.store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (lead == null) {
                return@Column
            }
            val shown = MintBucket.entries.filter { (totals[it] ?: 0.0) > 0 }
            shown.forEach { b -> PerkRow(b.label, totals[b] ?: 0.0, Perks.BUCKET_CAP) }
        }
    }
}

/** Home's party perks, laid out like [MintPerksCard]. They only change what Habitat Discovery pays; nothing in HOME's records depends on them. */
@Composable
fun HomePerksCard(model: AppModel) {
    val player = model.data.player
    val lead = player.homeLead
    val party = player.partyOf(PartyIds.HOME)
    val totals = HomePerks.total(lead, party, model.data.player.guardianOf(app.pokeplan.core.pokemon.PartyIds.HOME))
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Home perks")
            if (!model.store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (lead == null) {
                return@Column
            }
            val shown = HomeBucket.entries.filter { (totals[it] ?: 0.0) > 0 }
            shown.forEach { b -> PerkRow(b.label, totals[b] ?: 0.0, Perks.BUCKET_CAP) }
        }
    }
}

/** Link's party perks, laid out like [LinkPerksCard]. They only change what Postcard Trail pays; nothing in LINK's records depends on them. */
@Composable
fun LinkPerksCard(model: AppModel) {
    val player = model.data.player
    val lead = player.linkLead
    val party = player.partyOf(PartyIds.LINK)
    val totals = LinkPerks.total(lead, party, model.data.player.guardianOf(app.pokeplan.core.pokemon.PartyIds.LINK))
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Link perks")
            if (!model.store.sees(Feature.PERKS)) {
                Text(PERKS_LOCKED, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (lead == null) {
                return@Column
            }
            val shown = LinkBucket.entries.filter { (totals[it] ?: 0.0) > 0 }
            shown.forEach { b -> PerkRow(b.label, totals[b] ?: 0.0, Perks.BUCKET_CAP) }
        }
    }
}

/** One Pokémon's own perks (what it gives as the buddy, and as an assist) and its held item, with Give and Take. */
@Composable
fun PokemonPerksBox(model: AppModel, p: OwnedPokemon) {
    val store = model.store
    val bag = model.data.player.inventory
    var choosing by remember(p.id) { mutableStateOf(false) }
    val type = Perks.typePerks(p)
    val ability = AbilityPerks.table[p.ability]
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Perks")
            val rows = buildList {
                type.forEach { (b, v) -> if (v > 0) add(b.label() to pct(v)) }
                ability?.bucket?.let { if (ability.percent > 0) add("${it.label()}, ${p.ability}${ability.condition.label()?.let { c -> ", on $c" } ?: ""}" to pct(ability.percent)) }
                if (ability?.keepsFailedEgg == true) add("Magic Guard" to "Keeps a failed egg once a week")
                p.heldItem?.let { HeldItems.perks[it] }?.let { if (it.percent > 0) add("${it.bucket.label()}, ${Items.name(p.heldItem!!)}" to pct(it.percent)) }
                type.forEach { (b, v) -> if (v * Perks.ASSIST_SHARE > 0) add("${b.label()}, in a party" to pct(v * Perks.ASSIST_SHARE)) }
            }
            rows.forEach { (name, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }

            val held = p.heldItem
            ListRow(lead = { if (held != null) ItemIcon(held, size = Sizes.Icon) else ItemIcon("seal-bag", size = Sizes.Icon, description = null) }, trail = {
                if (held != null) PixelButton("Take", { store.takeItem(p.id) }, color = Pixel.InkSoft, compact = true)
                else PixelButton(if (choosing) "Close" else "Give", { choosing = !choosing }, color = Pixel.Tone, compact = true)
            }) {
                Text(if (held != null) "Holding ${Items.name(held)}" else "Holds nothing", style = MaterialTheme.typography.bodyLarge)
            }
            if (held != null) PixelButton(if (choosing) "Close" else "Swap", { choosing = !choosing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (choosing) {
                val holdable = bag.counts.filter { it.value > 0 && Items.holdable(it.key) }.keys.sorted()
                holdable.forEach { id ->
                    val perk = HeldItems.perks[id]?.let { " (${pct(it.percent)} ${it.bucket.label()})" } ?: ""
                    ListRow(lead = { ItemIcon(id, size = Sizes.Icon) }, onClick = { if (store.holdItem(p.id, id)) choosing = false }, onClickLabel = "Give ${Items.name(id)}") {
                        Text("${Items.name(id)}$perk", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
