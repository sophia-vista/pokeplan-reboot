package app.pokeplan.ui.pokemon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun MartView(model: AppModel) {
    val player = model.data.player
    TextBox { Text("${player.coins} Poké", style = MaterialTheme.typography.titleMedium) }
    listOf("Candies" to Shop.stock.filter { Items.category(it.first) == ItemCategory.CANDY }, "Berries" to Shop.stock.filter { Items.category(it.first) != ItemCategory.CANDY })
        .forEach { (title, offers) ->
            SectionLabel(title)
            TextBox {
                Column {
                    offers.forEachIndexed { index, (id, price) ->
                        if (index > 0) HorizontalDivider(color = Pixel.Border)
                        MartRow(model, id, price)
                    }
                }
            }
        }
}

@Composable
private fun MartRow(model: AppModel, id: String, price: Int) {
    val store = model.store
    val have = model.data.player.inventory[id]
    val room = (if (Items.category(id) == ItemCategory.CANDY) Inventory.MAX_CANDIES else Inventory.MAX_STACK) - have
    val afford = minOf(model.data.player.coins / price, room).coerceAtLeast(0)
    val buy = { n: Int -> if (store.martBuy(id, n) > 0) model.effect(if (Items.category(id) == ItemCategory.CANDY) Sfx.CANDY else Sfx.CONFIRM) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            ItemIcon(id, size = Sizes.IconL)
            Column(Modifier.weight(1f)) {
                Text(Items.name(id), style = MaterialTheme.typography.bodyLarge)
                Text("In the Bag: $have", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Text("$price Poké", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            listOf(1, 10).forEach { n -> PixelButton("Buy $n", { buy(n) }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = afford >= n) }
            PixelButton(if (afford > 1) "Buy $afford" else "Buy max", { buy(afford) }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = afford > 1 && afford != 10)
        }
    }
}
