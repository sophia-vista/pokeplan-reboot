package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.PixelShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.hasAction
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.pixelFont

/** The most a party holds, as in the games. */
private const val PARTY_SIZE = 6

/** One perk line of a party panel: its name and its total now. */
private class PerkLine(val label: String, val percent: Double)

/** What a party's perks say before the rank that shows them: every party's perks open together, and they already count. */
internal val PERKS_LOCKED = "Open at rank ${TrainerRank.names[Feature.PERKS.step]}. Your party is already growing them."

/** Plan's party in its module: its lead first, and its perks once the rank has opened them. */
@Composable
fun PlanPartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val store = model.store
    val player = model.data.player
    val opened = store.sees(Feature.PERKS)
    val totals = store.perks
    PartyPanel(
        model, player.party, player.planLead?.id, lead = "Lead", perksTitle = "Party perks",
        perks = if (opened) PerkBucket.entries.filter { totals[it] > 0 }.map { PerkLine(it.label(), totals[it]) } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            player.planLead == null -> "Put a Pokémon in Plan's party to get its perks."
            else -> null
        },
        empty = "Hatch an egg with a focus session. It joins your party here.",
        onOpen = onOpen,
    )
}

/** Care's party in its module: its lead first, and its three perks (which only change what Care pays) once the rank opens them, as Plan's. */
@Composable
fun CarePartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val player = model.data.player
    val party = player.partyOf(PartyIds.CARE)
    val opened = model.store.sees(Feature.PERKS)
    val totals = CarePerks.total(player.careLead, party)
    PartyPanel(
        model, party, player.careLead?.id, lead = "Lead", perksTitle = "Care perks",
        perks = if (opened) CareBucket.entries.mapNotNull { b -> totals[b]?.takeIf { it > 0 }?.let { PerkLine(b.label, it) } } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            party.isEmpty() || totals.values.any { it > 0 } -> null
            else -> "None yet: Normal, Fairy, Water, Grass, Bug, Fighting, Flying, Psychic and Ghost types bring them."
        },
        empty = "Open a Pokémon and choose \"To Care party\". Until then Care has no lead to warm.",
        onOpen = onOpen,
    )
}

/** Heal's party in its module: its lead first, and its two perks (which only change what Field Research pays) once the rank opens them, as Care's. */
@Composable
fun HealPartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val player = model.data.player
    val party = player.partyOf(PartyIds.HEAL)
    val opened = model.store.sees(Feature.PERKS)
    val totals = HealPerks.total(player.healLead, party)
    PartyPanel(
        model, party, player.healLead?.id, lead = "Lead", perksTitle = "Heal perks",
        perks = if (opened) HealBucket.entries.mapNotNull { b -> totals[b]?.takeIf { it > 0 }?.let { PerkLine(b.label, it) } } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            party.isEmpty() || totals.values.any { it > 0 } -> null
            else -> "None yet: Fighting, Normal, Fire, Electric, Flying, Dragon, Water, Grass, Ground, Ice, Bug and Fairy types bring them."
        },
        empty = "Open a Pokémon and choose \"To Heal party\", or try Field Research above. Heal works fully with an empty party.",
        onOpen = onOpen,
    )
}

/** Mint's party in its module: its lead first, and its two perks (which only change what Ledger Research pays) once the rank opens them, as Care's. */
@Composable
fun MintPartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val player = model.data.player
    val party = player.partyOf(PartyIds.MINT)
    val opened = model.store.sees(Feature.PERKS)
    val totals = MintPerks.total(player.mintLead, party)
    PartyPanel(
        model, party, player.mintLead?.id, lead = "Lead", perksTitle = "Mint perks",
        perks = if (opened) MintBucket.entries.mapNotNull { b -> totals[b]?.takeIf { it > 0 }?.let { PerkLine(b.label, it) } } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            party.isEmpty() || totals.values.any { it > 0 } -> null
            else -> "None yet: Steel, Rock, Ground, Normal, Electric, Psychic, Ghost, Dark, Poison, Fire, Fairy and Dragon types bring them."
        },
        empty = "Open a Pokémon and choose \"To Mint party\", or try the tally above. Mint works fully with an empty party.",
        onOpen = onOpen,
    )
}

/**
 * A module's whole party, out and evenly spaced like the party screen of the games: two rows of three equal slots, the
 * lead first, outlined and named by its role, the free ones a faint Poké Ball. Tapping one plays its cry and a hop; holding it opens its page.
 * Below, the party's perks, laid out the same in every module so the two read alike.
 */
@Composable
private fun PartyPanel(
    model: AppModel, members: List<OwnedPokemon>, leadId: String?, lead: String,
    perksTitle: String, perks: List<PerkLine>, perksNote: String?, empty: String, onOpen: (String) -> Unit,
) {
    val ordered = members.sortedByDescending { it.id == leadId }.take(PARTY_SIZE)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Party", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("${ordered.size} of $PARTY_SIZE", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            }
            PartyGrid(model, ordered, leadId, lead, onOpen)
            if (ordered.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyMedium)
            else {
                HorizontalDivider(color = Pixel.Border)
                Text(perksTitle, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                perks.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(pct(line.percent), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Pixel.Green)
                    }
                }
                if (perksNote != null) Text(perksNote, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The party's six places, two rows of three equal slots, the lead first and named by its role, free ones a faint Poké Ball. With [tapOpens] a tap opens the
 * Pokémon (and a long press plays its cry), as on the Pokémon tab; otherwise a tap plays the cry and a long press opens it, as inside a module.
 */
@Composable
internal fun PartyGrid(model: AppModel, members: List<OwnedPokemon>, leadId: String?, lead: String, onOpen: (String) -> Unit, tapOpens: Boolean = false) {
    val ordered = members.sortedByDescending { it.id == leadId }.take(PARTY_SIZE)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        (0 until PARTY_SIZE).chunked(3).forEach { row ->
            // Every slot in a row is as tall as the tallest, so a long name never puts the row out of line.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                row.forEach { i ->
                    val p = ordered.getOrNull(i)
                    val slot = Modifier.weight(1f).fillMaxHeight()
                    if (p == null) EmptySlot(slot) else PartySlot(model, p, if (p.id == leadId) lead else null, slot, onOpen, tapOpens)
                }
            }
        }
    }
}

private val slotShape = PixelShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartySlot(model: AppModel, p: OwnedPokemon, role: String?, modifier: Modifier, onOpen: (String) -> Unit, tapOpens: Boolean = false) {
    val calm = model.data.settings.reduceMotion
    var hops by remember(p.id) { mutableIntStateOf(0) }
    var action by remember(p.id) { mutableStateOf(SpriteAction.IDLE) }
    val lift = remember(p.id) { Animatable(0f) }
    LaunchedEffect(hops) {
        // Its own Hop animation where SpriteCollab has one; a small hop in code for the rest; none with Reduce motion.
        if (hops == 0 || calm || hasAction(p.form.id, p.isShiny, SpriteAction.HOP)) return@LaunchedEffect
        lift.animateTo(-10f, tween(110))
        lift.animateTo(0f, tween(150))
    }
    val leads = role != null
    // The lead wears the module's own colour inside a module page (its frame tone), the brand red elsewhere.
    val leadColor = app.pokeplan.ui.theme.LocalFrameTone.current?.band ?: Pixel.Red
    Box(
        modifier
            .background(if (leads) leadColor.copy(alpha = 0.10f) else Pixel.Shade.copy(alpha = 0.6f), slotShape)
            .let { if (leads) it.border(2.dp, leadColor, slotShape) else it }
            .combinedClickable(
                role = Role.Button, onClickLabel = if (tapOpens) "Open ${p.nickname}" else "Hear ${p.nickname}", onLongClickLabel = if (tapOpens) "Hear ${p.nickname}" else "Open ${p.nickname}",
                onLongClick = { model.haptic(); if (tapOpens) hear(model, p, calm) { action = SpriteAction.HOP; hops++ } else onOpen(p.id) },
            ) {
                model.haptic()
                if (tapOpens) onOpen(p.id) else hear(model, p, calm) { action = SpriteAction.HOP; hops++ }
            }
            .padding(vertical = Spacing.S, horizontal = Spacing.XS)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(role, p.nickname, "level ${p.level}", if (p.isShiny) "shiny" else null).joinToString(", ")
            },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedSprite(
                p.form.id, p.isShiny, p.nickname, Modifier.offset { IntOffset(0, lift.value.dp.roundToPx()) },
                size = 56.dp, animate = !calm, ground = 4.dp,
                action = action, actionKey = hops, onActionDone = { action = SpriteAction.IDLE },
            )
            Text(
                p.nickname, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            // The lead's level is on the stage just above, so its slot names its role there instead.
            if (role != null) Text(role, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = leadColor)
            else Text(
                "Lv. ${p.level}" + if (p.isShiny) " · Shiny" else "", style = MaterialTheme.typography.labelMedium,
                color = if (p.isShiny) Pixel.Warning else Pixel.InkSoft,
            )
        }
    }
}

/** A Pokémon's cry and, unless motion is reduced, its hop. */
private inline fun hear(model: AppModel, p: OwnedPokemon, calm: Boolean, hop: () -> Unit) {
    model.cry(p.speciesId)
    if (!calm) hop()
}

/**
 * A free place in the party: a faint Poké Ball, the way the games leave an empty slot. It is laid out like a filled slot
 * (a sprite's room and two lines), so a row of free places is as tall as a full one at any font size.
 */
@Composable
private fun EmptySlot(modifier: Modifier) {
    Box(
        modifier.background(Pixel.Shade.copy(alpha = 0.3f), slotShape).semantics(mergeDescendants = true) { contentDescription = "Free place" },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(vertical = Spacing.S).alpha(0f)) {
            Box(Modifier.size(56.dp))
            Text(" ", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Text(" ", style = MaterialTheme.typography.labelMedium)
        }
        ItemIcon("poke-ball", Modifier.alpha(0.22f), size = 32.dp, description = null)
    }
}

/** Home's party in its module: its lead first, and its two perks (which only change what Habitat Discovery pays) once the rank opens them, as Mint's. */
@Composable
fun HomePartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val player = model.data.player
    val party = player.partyOf(PartyIds.HOME)
    val opened = model.store.sees(Feature.PERKS)
    val totals = HomePerks.total(player.homeLead, party)
    PartyPanel(
        model, party, player.homeLead?.id, lead = "Lead", perksTitle = "Home perks",
        perks = if (opened) HomeBucket.entries.mapNotNull { b -> totals[b]?.takeIf { it > 0 }?.let { PerkLine(b.label, it) } } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            party.isEmpty() || totals.values.any { it > 0 } -> null
            else -> "None yet: Grass, Normal, Bug, Ground, Fire, Water, Fairy, Flying, Ice, Electric, Psychic and Ghost types bring them."
        },
        empty = "Open a Pokémon and choose \"To Home party\", or try the tidy above. Home works fully with an empty party.",
        onOpen = onOpen,
    )
}

/** Link's party in its module: its lead first, and its two perks (which only change what Postcard Trail pays) once the rank opens them, as Home's. */
@Composable
fun LinkPartyPanel(model: AppModel, onOpen: (String) -> Unit) {
    val player = model.data.player
    val party = player.partyOf(PartyIds.LINK)
    val opened = model.store.sees(Feature.PERKS)
    val totals = LinkPerks.total(player.linkLead, party)
    PartyPanel(
        model, party, player.linkLead?.id, lead = "Lead", perksTitle = "Link perks",
        perks = if (opened) LinkBucket.entries.mapNotNull { b -> totals[b]?.takeIf { it > 0 }?.let { PerkLine(b.label, it) } } else emptyList(),
        perksNote = when {
            !opened -> PERKS_LOCKED
            party.isEmpty() || totals.values.any { it > 0 } -> null
            else -> "None yet: Normal, Flying, Water, Grass, Bug, Ground, Fairy, Fighting, Electric, Psychic, Fire and Dragon types bring them."
        },
        empty = "Open a Pokémon and choose \"To Link party\", or try the postcard above. Link works fully with an empty party.",
        onOpen = onOpen,
    )
}
