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

/**
 * A person's profile card (LINK.md 2.6): the Pokémon the user chose as their picture in a round pixel frame, their whole name (it wraps, never clipped), what
 * they are called, and small tags for their relationship word, pinned and the Me card. Tapping the picture opens [PersonPicture]. Cosmetic and private.
 */
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

/** A small tag: a word in a tinted pixel plate. */
@Composable
private fun Tag(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = Pixel.Ink,
        modifier = Modifier.background(tint.copy(alpha = 0.22f), PixelShape).border(2.dp, tint, PixelShape).padding(horizontal = Spacing.S, vertical = Spacing.XS),
    )
}

/**
 * The picture of Pokémon [species] in the app's ordinary portrait frame (0 for none: a Friend Ball in the same plain frame). [onClick] makes it a button.
 */
@Composable
internal fun Avatar(species: Int, size: Dp, description: String, onClick: (() -> Unit)? = null) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier
    if (species > 0) Portrait(Dex.defaultForm(species).id, false, Dex.species(species).name, Modifier.then(click), size = size)
    else Box(Modifier.size(size).then(click).border(2.dp, Pixel.Outline), contentAlignment = Alignment.Center) { ItemIcon("friend-ball", size = size / 2, description = null) }
}

/** The most Pokémon the picker shows at once; a search narrows them. */
private const val PICK_PAGE = 60

/**
 * The picture chooser: the Pokémon the player has caught first, or a search of every species by name. Choosing one sets it and goes back; "No picture" clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonPicture(model: AppModel, p: Person, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val owned = (model.data.player.caught + model.data.player.pokemon.map { it.speciesId }).sorted()
    val shown = if (query.isBlank()) owned.ifEmpty { listOf(1, 4, 7, 25, 133, 143) } else Dex.species.filter { it.name.contains(query.trim(), ignoreCase = true) }.map { it.id }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Choose a picture")
            PixelField(query, { query = it.take(24) }, Modifier.fillMaxWidth(), placeholder = "Search Pokémon", singleLine = true)
            if (shown.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                shown.take(PICK_PAGE).forEach { id ->
                    Avatar(id, 64.dp, "Use ${Dex.species(id).name}", onClick = { model.link.setAvatar(p.id, id); model.haptic(); onDone() })
                }
            }
            if (shown.size > PICK_PAGE) Text("Search to narrow the ${shown.size} Pokémon.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (p.avatar != 0) PixelButton("No picture", { model.link.setAvatar(p.id, 0); onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Back", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

/**
 * A person's Friend Safari (LINK.md 9.5): the Pokémon their opaque key decides, and a free visit once a day that catches one of them. It pays only the
 * Pokémon, never touches a record, and says nothing about the person. A caught species shows in full, one not caught yet as a Pokéball.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SafariPanel(model: AppModel, p: Person) {
    val link = model.link
    model.linkData // the save changes, so the visit state is read again
    val pool = link.safariPool(p.id)
    if (pool.isEmpty()) return
    val day = link.safariToday(p.id)
    val caught = model.data.player.caught
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
            else PixelButton("Visit", { link.visitSafari(p.id)?.let { model.haptic() } }, Modifier.fillMaxWidth(), tint, compact = true)
        }
    }
}
