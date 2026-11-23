package app.pokeplan.ui.link

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Person
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlin.random.Random

private val tint: Color @Composable get() = ModuleAccent.LINK.accent

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonCard(p: Person, onPicture: () -> Unit) {
    TextBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Avatar(p.avatar, 88.dp, "Change the picture", onPicture)
                PixelButton(if (p.avatar == 0) "Choose" else "Change", onPicture, color = Pixel.InkSoft, compact = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(LinkRules.displayName(p), style = MaterialTheme.typography.titleMedium, color = Pixel.Ink)
                if (p.preferred.isNotBlank() && !p.preferred.equals(p.name, ignoreCase = true)) Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                val work = listOf(p.role, p.organization).filter { it.isNotBlank() }.joinToString(" · ")
                if (work.isNotBlank()) Text(work, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                val tags = listOfNotNull("Me".takeIf { p.isMe }, p.category.takeIf { it.isNotBlank() }, "Pinned".takeIf { p.favorite })
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) { tags.forEach { Tag(it) } }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = Pixel.Ink,
        modifier = Modifier.background(tint.copy(alpha = 0.22f), PixelShape).border(2.dp, tint, PixelShape).padding(horizontal = Spacing.S, vertical = Spacing.XS),
    )
}

@Composable
internal fun Avatar(species: Int, size: Dp, description: String, onClick: (() -> Unit)? = null) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier
    if (species > 0) Portrait(Dex.defaultForm(species).id, false, Dex.species(species).name, Modifier.then(click), size = size)
    else Box(Modifier.size(size).then(click).border(2.dp, Pixel.Outline), contentAlignment = Alignment.Center) { ItemIcon("friend-ball", size = size / 2, description = null) }
}

private const val PICK_PAGE = 36

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonPicture(model: AppModel, p: Person, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf(Random.nextInt()) }
    var mine by remember { mutableStateOf(false) }
    val owned = remember(mine) { (model.data.player.caught + model.data.player.pokemon.map { it.speciesId }).distinct().sorted() }
    val random = remember(seed) { Dex.species.map { it.id }.shuffled(Random(seed)) }
    val shown = if (query.isNotBlank()) Dex.species.filter { it.name.contains(query.trim(), ignoreCase = true) }.map { it.id }
        else if (mine && owned.isNotEmpty()) owned else random
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Choose a picture")
            PixelField(query, { query = it.take(24) }, Modifier.fillMaxWidth(), placeholder = "Search Pokémon", singleLine = true)
            if (query.isBlank()) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("Shuffle", { mine = false; seed = Random.nextInt() }, Modifier.weight(1f), tint, compact = true)
                if (owned.isNotEmpty()) PixelButton(if (mine) "Random" else "My Pokémon", { mine = !mine }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (shown.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                shown.take(PICK_PAGE).forEach { id ->
                    Avatar(id, 64.dp, "Use ${Dex.species(id).name}", onClick = { model.link.setAvatar(p.id, id); model.haptic(); onDone() })
                }
            }
            if (query.isNotBlank() && shown.size > PICK_PAGE) Text("Search to narrow the ${shown.size} Pokémon.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (p.avatar != 0) PixelButton("No picture", { model.link.setAvatar(p.id, 0); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Back", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SafariPanel(model: AppModel, p: Person) {
    val link = model.link
    model.linkData
    val pool = link.safariPool(p.id)
    if (pool.isEmpty()) return
    val day = link.safariToday(p.id)
    val caught = model.data.player.caught
    var asking by remember(p.id) { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Friend Safari")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                pool.forEach { o ->
                    val name = Dex.species(o.speciesId).name
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        if (o.speciesId in caught) Avatar(o.speciesId, 64.dp, name) else Avatar(0, 64.dp, "Not met yet")
                        Text(if (o.speciesId in caught) name else "?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
            }
            if (day.visited) Text("Come back tomorrow.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else if (day.left <= 0) Text("Today's Safari visits are used.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else if (!asking) PixelButton("Visit", { asking = true }, Modifier.fillMaxWidth(), tint, compact = true)
            else {
                Text("Use one of today's ${day.left} visits? It catches one Pokémon.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("Not now", { asking = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Visit now", { asking = false; link.visitSafari(p.id)?.let { model.haptic() } }, Modifier.weight(1f), tint, compact = true)
                }
            }
        }
    }
}

@Composable
internal fun PersonTile(p: Person, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.10f), PixelShape).border(2.dp, Pixel.Outline, PixelShape)
            .clickable(role = Role.Button, onClickLabel = "Open their screen", onClick = onClick).padding(Spacing.S),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Avatar(p.avatar, 48.dp, "Picture")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(LinkRules.displayName(p) + if (p.favorite) " ★" else "", style = MaterialTheme.typography.titleMedium, color = Pixel.Ink, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1)
        }
    }
}
