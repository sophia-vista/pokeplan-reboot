package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.Place
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.ItemUse
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.StatCalculator
import app.pokeplan.core.reward.Feature
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun BagView(model: AppModel) {
    val store = model.store
    val player = model.data.player
    var open by remember { mutableStateOf<String?>(null) }
    val items = player.inventory.counts.entries.filter { it.value > 0 }.sortedWith(compareBy({ Items.category(it.key)?.ordinal ?: 99 }, { it.key }))
    if (store.sees(Feature.COINS)) TextBox {
        // The shop: coins buy Exp. Candies.
        val each = Shop.COINS_PER_RARE_CANDY
        val afford = player.coins / each
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("${player.coins} Poké", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                ItemIcon(Items.RARE_CANDY, size = Sizes.Icon)
                Text("Exp. Candy: $each Poké each", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                listOf(1, 10).forEach { n ->
                    PixelButton("Buy $n", { if (store.buyRareCandies(n) > 0) model.effect(Sfx.HEAL) }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = afford >= n)
                }
                PixelButton("Buy $afford", { if (store.buyRareCandies(afford) > 0) model.effect(Sfx.HEAL) }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = afford > 10)
            }
        }
    }
    if (items.isEmpty()) EmptyState("The bag is empty", "Finish tasks and focus sessions to find candies, berries and more.", icon = "seal-bag", sleeper = true, action = "Start a focus session" to { model.open(Place.FOCUS, true) })
    items.groupBy { Items.category(it.key) }.forEach { (category, group) ->
        SectionLabel(category.bagLabel())
        TextBox {
            Column {
                group.forEachIndexed { index, (id, n) ->
                    val holdable = Items.holdable(id) && store.sees(Feature.HELD_ITEMS)
                    val training = Items.category(id) == ItemCategory.TRAINING || Items.category(id) == ItemCategory.TERA
                    val usable = holdable || ((!training || store.sees(Feature.TRAINING_ITEMS)) && player.party.any { store.canUse(id, it) })
                    val toggle = { open = if (open == id) null else id }
                    if (index > 0) HorizontalDivider(color = Pixel.Border)
                    // A usable item says so with its own Use button, so it does not look the same as one that only sits in the bag.
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target)
                            .let { if (usable) it.clickable(onClickLabel = "Use ${Items.name(id)}", role = Role.Button, onClick = toggle) else it }
                            .padding(vertical = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                    ) {
                        ItemIcon(id, size = Sizes.IconL)
                        Text(Items.name(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("x$n", style = MaterialTheme.typography.titleMedium)
                        if (usable) PixelButton(if (open == id) "Close" else if (holdable) "Give" else "Use", toggle, color = Pixel.InkSoft, compact = true)
                    }
                    if (open == id) Box(Modifier.padding(bottom = Spacing.S)) { UseItemBox(model, id, holdable) { open = null } }
                }
            }
        }
    }
}

/**
 * Using one bag item: who gets it, and for a Bottle Cap or Nature Mint which stat or nature. Held items are given to
 * hold (what the Pokémon held before goes back to the bag).
 */
@Composable
private fun UseItemBox(model: AppModel, id: String, holdable: Boolean, close: () -> Unit) {
    val store = model.store
    val party = model.data.player.party
    var target by remember(id) { mutableStateOf<OwnedPokemon?>(null) }
    fun done(ok: Boolean) {
        if (ok) model.effect(Sfx.HEAL)
        target = null
        if (!store.data.player.inventory.has(id)) close()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        val who = target
        when {
            who != null && id == Items.BOTTLE_CAP -> {
                Text("Which stat should ${who.nickname} max out?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Stat.entries.filter { who.ivs[it] < StatCalculator.MAX_IV }.forEach { st ->
                        PixelButton(st.label, { done(store.useItem(ItemUse.BottleCap(st), who.id)) }, color = Pixel.Tone, compact = true)
                    }
                }
            }
            who != null && id == Items.NATURE_MINT -> {
                Text("Which nature for ${who.nickname}?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Nature.entries.filter { it != who.nature }.forEach { n ->
                        val detail = if (n.raised == null) "" else " (+${n.raised!!.label} -${n.lowered!!.label})"
                        PixelButton(n.name.lowercase().replaceFirstChar { it.uppercase() } + detail, { done(store.useItem(ItemUse.NatureMint(n), who.id)) }, color = Pixel.Tone, compact = true)
                    }
                }
            }
            else -> {
                Text(if (holdable) "Give to hold:" else "Use on:", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                party.filter { holdable || store.canUse(id, it) }.forEach { p ->
                    val label = if (holdable && p.heldItem != null) "${p.nickname} (holds ${Items.name(p.heldItem!!)})" else p.nickname
                    PixelButton(label, {
                        when {
                            holdable -> done(store.holdItem(p.id, id))
                            id == Items.BOTTLE_CAP || id == Items.NATURE_MINT -> target = p
                            else -> done(store.useItem(id, p.id))
                        }
                    }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                }
            }
        }
    }
}

private fun ItemCategory?.bagLabel(): String = when (this) {
    ItemCategory.CANDY -> "Candies"
    ItemCategory.VITAMIN -> "Vitamins"
    ItemCategory.TRAINING -> "Training"
    ItemCategory.EVOLUTION -> "Evolution items"
    ItemCategory.HELD -> "Held items"
    ItemCategory.BERRY -> "Berries"
    ItemCategory.LURE -> "Lures"
    ItemCategory.TERA -> "Tera Shards"
    ItemCategory.FORM -> "Forms"
    ItemCategory.KEY -> "Key items"
    null -> "Other"
}

// ----- One Pokémon ---------------------------------------------------------------------------------------------------
