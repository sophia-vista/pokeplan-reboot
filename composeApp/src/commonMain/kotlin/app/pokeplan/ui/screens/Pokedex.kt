package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.PixelShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.Glyph
import app.pokeplan.app.BuddyScene
import app.pokeplan.app.DexState
import app.pokeplan.app.FormDex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.SpeciesInfo
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.spriteCredit
import app.pokeplan.ui.portraitCredit
import app.pokeplan.ui.SceneStage
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.Spacing

/** The National Dex split by generation, with the national numbers each generation covers. */
private val generations = listOf(
    "Gen 1" to 1..151, "Gen 2" to 152..251, "Gen 3" to 252..386, "Gen 4" to 387..493, "Gen 5" to 494..649,
    "Gen 6" to 650..721, "Gen 7" to 722..809, "Gen 8" to 810..905, "Gen 9" to 906..1025,
)

internal enum class DexFilter(val label: String) { ALL("All"), CAUGHT("Caught"), MISSING("Not caught") }

/** Which list the Pokédex shows, kept by the Pokémon tab so opening an entry and coming back leaves it as it was. */
internal data class DexView(val region: Region = Region.PLAN, val national: Boolean = false, val generation: Int = 0, val filter: DexFilter = DexFilter.ALL, val search: String = "") {
    /** (number shown, species): own order from 1 for a region, the National Dex number for the National tab. */
    fun rows(): List<Pair<Int, SpeciesInfo>> =
        if (national) Dex.species.filter { it.id in generations[generation].second }.map { it.id to it }
        else Dex.regionDex(region).mapIndexed { i, s -> (i + 1) to s }

    val title: String get() = if (national) "National Dex, ${generations[generation].first}" else region.label
}

/** "#001", the games' three digits (four past 999). */
private fun dexNumber(n: Int) = "#" + n.toString().padStart(3, '0')

/**
 * The Pokédex. A region's list is numbered from 1 in its own order (the Plan Region starts at 1 too); the National Dex tab
 * uses the real National Dex numbers, a generation at a time. Tapping a species found so far opens its entry.
 */
@Composable
internal fun PokedexView(model: AppModel, view: DexView, change: (DexView) -> Unit, open: (Int) -> Unit) {
    val player = model.data.player
    val regions = TrainerRank.unlockedRegions(player.trainerPoints)
    val rows = remember(view.region, view.national, view.generation) { view.rows() }
    val states = rows.associate { (_, s) -> s.id to DexState.of(model.data, s.id) }
    val caught = states.values.count { it.caught }
    val seen = states.values.count { it == DexState.SEEN }
    val shiny = rows.count { (_, sp) -> sp.id in model.data.shinySpecies }
    val (formsHad, formsAll) = remember(model.data, rows) {
        val forms = rows.flatMap { (_, sp) -> Dex.formsOf(sp.id).filter { FormDex.counts(it) } }
        forms.count { FormDex.caught(model.data, it) } to forms.size
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        regions.forEach { r -> TabButton(r.label, !view.national && r == view.region, { change(view.copy(national = false, region = r)) }, Modifier.padding(0.dp)) }
        TabButton("National", view.national, { change(view.copy(national = true)) }, Modifier.padding(0.dp))
    }
    if (view.national) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        generations.forEachIndexed { i, (label, _) -> TabButton(label, i == view.generation, { change(view.copy(generation = i)) }, Modifier.padding(0.dp)) }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text("${view.title}: $caught of ${rows.size} caught", style = MaterialTheme.typography.titleMedium)
            PixelBar(caught.toFloat() / rows.size.coerceAtLeast(1), color = if (caught == rows.size) Pixel.Warning else Pixel.Red)
            // One quiet line for the rest, so the card stays a number and a bar.
            val more = listOfNotNull(
                "$shiny shiny".takeIf { shiny > 0 }, "Forms $formsHad of $formsAll", "$seen more seen".takeIf { seen > 0 },
            )
            Text(more.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    PixelField(
        view.search, { change(view.copy(search = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = "Search by name or number",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        DexFilter.entries.forEach { f -> TabButton(f.label, f == view.filter, { change(view.copy(filter = f)) }, Modifier.padding(0.dp)) }
    }
    val text = view.search.trim().lowercase().removePrefix("#")
    val shown = rows.filter { (number, s) ->
        val state = states.getValue(s.id)
        val byFilter = when (view.filter) {
            DexFilter.ALL -> true
            DexFilter.CAUGHT -> state.caught
            DexFilter.MISSING -> !state.caught
        }
        // A species not found yet matches by number only: its name would give it away.
        byFilter && (text.isEmpty() || dexNumber(number).removePrefix("#").contains(text) || (state != DexState.UNKNOWN && s.name.lowercase().contains(text)))
    }
    if (shown.isEmpty()) {
        when {
            text.isNotEmpty() -> EmptyState("Nothing matches", "Try another name or number, or clear the search.", action = "Clear the search" to { change(view.copy(search = "")) })
            view.filter == DexFilter.CAUGHT -> EmptyState("None caught here yet", "Hatch eggs in this region to fill it in.", action = "Start a focus session" to { model.open(Place.FOCUS, true) })
            else -> EmptyState("All caught!", "Every Pokémon in this list is yours.", action = "Show them all" to { change(view.copy(filter = DexFilter.entries.first())) })
        }
    } else {
        // An index, not a wall of cards: number, sprite, name and one quiet line per row, with a hairline between rows.
        val shape = PixelShape
        Column(Modifier.fillMaxWidth().clip(shape).background(Pixel.Panel, shape).border(Sizes.Hairline, Pixel.Border, shape)) {
            shown.forEachIndexed { i, (number, s) ->
                val state = states.getValue(s.id)
                // A seen species is named; one not found yet stays a mystery, so its name would give it away.
                val name = if (state == DexState.UNKNOWN) "???" else s.name
                val isShiny = s.id in model.data.shinySpecies
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Pixel.Border))
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                        .quietClickable(enabled = state != DexState.UNKNOWN, onClickLabel = "Open entry") { open(s.id) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Number $number, ${if (state == DexState.UNKNOWN) "Unknown" else s.name}, ${state.label}" + if (isShiny) ", shiny found" else ""
                        }
                        .padding(horizontal = Spacing.S, vertical = Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Text(dexNumber(number), Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                    // Species not caught yet show as a grey shape: the real sprite, tinted.
                    Sprite(Dex.defaultForm(s.id).id, false, "", size = Spacing.Target, silhouette = if (state.caught) null else Pixel.Shade)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (state == DexState.UNKNOWN) Pixel.InkSoft else Pixel.Ink)
                        // Words, not just the sprite's colour, tell the states apart.
                        Text(if (state == DexState.UNKNOWN) "Not found yet" else state.label, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1)
                    }
                    if (isShiny) Glyph("star", Pixel.Warning, size = 18.dp, description = "Shiny")
                    if (state == DexState.OWNED) ItemIcon("poke-ball", size = 20.dp, description = null)
                }
            }
        }
    }
}

/** "0.7 m" from decimetres, "6.9 kg" from hectograms: the games keep both in tenths. */
private fun tenths(value: Int, unit: String) = "${value / 10}.${value % 10} $unit"

/**
 * One species' Pokédex page: its category, size and entry, types, abilities, base stats and evolution line, with Previous
 * and Next through the list it was opened from (skipping species not found yet). Seen species show only their shape.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DexEntryScreen(model: AppModel, rows: List<Pair<Int, SpeciesInfo>>, id: Int, open: (Int) -> Unit, back: () -> Unit) {
    BackHandler(enabled = true, onBack = back)
    val s = Dex.species(id)
    // The form shown in the scene (one the player has had, picked under Forms) and whether forms show as shinies; the
    // shiny switch stays on through Next, the picked form does not.
    var picked by remember(id) { mutableStateOf<Int?>(null) }
    var shinyView by remember { mutableStateOf(false) }
    val form = picked?.let { p -> Dex.formsOf(id).firstOrNull { it.id == p } } ?: Dex.defaultForm(id)
    val shiny = shinyView && FormDex.shiny(model.data, form)
    val state = DexState.of(model.data, id)
    val number = rows.firstOrNull { it.second.id == id }?.first ?: id
    val found = rows.map { it.second.id }.filter { DexState.of(model.data, it) != DexState.UNKNOWN }
    val at = found.indexOf(id)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${dexNumber(number)} ${s.name}", subtitle = if (state.caught) s.genus else state.label, icon = "great-ball") {
            PixelButton("Back", back, color = Pixel.InkSoft, compact = true)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A caught species stands in a scene for its type (the buddy's scenes on Home); a seen one keeps the plain wallpaper,
            // so the scene does not give its type away.
            if (state.caught) SceneStage(BuddyScene.of(form.types), 232.dp) {
                AnimatedSprite(
                    form.id, shiny, form.name,
                    Modifier.align(Alignment.TopCenter).padding(top = Spacing.M).quietClickable(onClickLabel = "Hear its cry") { model.cry(id) },
                    size = 160.dp, animate = !model.data.settings.reduceMotion,
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            } else Stage("bw08", 232.dp) {
                Sprite(form.id, false, s.name, Modifier.align(Alignment.Center), size = 160.dp, silhouette = Pixel.Shade)
            }
            if (at >= 0 && found.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelIconButton("arrow-left", "Previous", { open(found[at - 1]) }, Pixel.InkSoft, enabled = at > 0)
                PixelIconButton("arrow-right", "Next", { open(found[at + 1]) }, Pixel.InkSoft, enabled = at < found.size - 1)
            }

            if (state.caught) {
                FormsBox(model, id, form.id, shinyView, { shinyView = it }) { picked = it }
                CaughtEntry(model, s, form, open)
            }
            else TextBox { Text("Seen, not caught yet. Its entry opens when one is yours.", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/**
 * Every form of a species, as the form dex: the ones the player has had in colour, the rest as black shapes. Regular and
 * Shiny switch the whole row; tapping a form the player has had shows it in the scene above. Forms that cannot be had
 * in PokéPlan yet (Gigantamax and other special forms) are shown faded and are not counted.
 */
@Composable
private fun FormsBox(model: AppModel, speciesId: Int, shownId: Int, shiny: Boolean, setShiny: (Boolean) -> Unit, pick: (Int) -> Unit) {
    val forms = Dex.formsOf(speciesId)
    val counted = forms.filter { FormDex.counts(it) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(if (forms.size == 1) "Form" else "Forms", style = MaterialTheme.typography.titleMedium)
            Text(
                "${counted.count { FormDex.caught(model.data, it) }} of ${counted.size} caught, " +
                    "${counted.count { FormDex.shiny(model.data, it) }} of ${counted.size} shiny",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TabButton("Regular", !shiny, { setShiny(false) }, Modifier.padding(0.dp))
                TabButton("Shiny", shiny, { setShiny(true) }, Modifier.padding(0.dp))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                forms.forEach { f ->
                    val caught = FormDex.caught(model.data, f)
                    val found = if (shiny) FormDex.shiny(model.data, f) else caught
                    val canHave = FormDex.counts(f)
                    val name = if (caught) f.name else "???"
                    val shape = PixelShape
                    Column(
                        Modifier.size(width = 92.dp, height = 104.dp).clip(shape).background(Pixel.Panel)
                            .border(Sizes.Line, if (f.id == shownId && caught) Pixel.Red else Pixel.Border, shape)
                            .quietClickable(enabled = caught, onClickLabel = "Show this form") { pick(f.id) }
                            .alpha(if (canHave) 1f else 0.45f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = (if (caught) f.name else "Unknown form") + ", " +
                                    (if (!canHave) "not in PokéLife yet" else if (found) "caught" else "not caught yet") + if (shiny) ", shiny" else ""
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // A form not had yet is its real sprite in black: the shape only.
                        Sprite(f.id, shiny && found, name, size = 64.dp, silhouette = if (found) null else Color.Black)
                        Text(
                            name, Modifier.padding(horizontal = Spacing.XS),
                            style = MaterialTheme.typography.labelMedium, color = if (found) Pixel.Ink else Pixel.InkSoft,
                            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (counted.size < forms.size) Text(
                "Faded forms (Gigantamax and other special forms) can't be found in PokéLife yet and don't count.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

/** What a caught species' entry tells: the games' Pokédex page plus its base stats and how its line evolves. */
@Composable
private fun CaughtEntry(model: AppModel, s: SpeciesInfo, form: FormInfo, open: (Int) -> Unit) {
    val id = s.id
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(s.genus, style = MaterialTheme.typography.titleMedium)
            Text("Height ${tenths(s.heightDm, "m")}, weight ${tenths(s.weightHg, "kg")}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(Dex.entry(id), style = MaterialTheme.typography.bodyLarge)
            val owned = model.data.player.pokemon.count { it.speciesId == id }
            Text(
                if (owned > 0) "You have $owned in your party and PC." else "Caught before; none in your party or PC now.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            if (id in model.data.shinySpecies) Text("You have found a shiny one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Abilities")
            Text(
                (form.abilities + listOfNotNull(form.hiddenAbility?.let { "$it (hidden)" })).joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
            )
            PixelTitle("Base stats")
            val base = form.stats
            listOf(base.hp, base.attack, base.defense, base.spAttack, base.spDefense, base.speed).forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(statNames[i], Modifier.size(width = 84.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) { PixelBar(v / 200f, color = if (i == 0) Pixel.Green else Pixel.Tone) }
                    Text("$v", Modifier.size(width = 44.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                }
            }
            Text("Total ${base.total}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
    val line = Dex.lineEvolutions(id)
    if (line.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Evolution")
            line.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    LineMember(model, e.from, current = e.from == id, open)
                    // How it evolves sits on the arrow between the two, like a label on a signpost.
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▸", style = MaterialTheme.typography.titleMedium, color = Pixel.InkSoft)
                        Text(EvolutionRules.howTo(e), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center)
                    }
                    LineMember(model, e.to, current = e.to == id, open)
                }
            }
        }
    }
    val credit by produceState<String?>(null, form.id) {
        // The SpriteCollab licence (CC BY-NC 4.0) asks for credit to the artists of the sprite and the portrait.
        val sprite = spriteCredit(form.id)
        val portrait = portraitCredit(form.id)
        value = when {
            sprite != null && sprite == portrait -> "Sprite and portrait by $sprite"
            else -> listOfNotNull(sprite?.let { "Sprite by $it" }, portrait?.let { "portrait by $it" }).joinToString(", ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }
        }
    }
    credit?.let { CreditLine(it) }
}

/** A species in an evolution line: its sprite and name, a grey shape and ??? until found, tap to open its entry. */
@Composable
private fun LineMember(model: AppModel, speciesId: Int, current: Boolean, open: (Int) -> Unit) {
    val state = DexState.of(model.data, speciesId)
    val name = if (state == DexState.UNKNOWN) "???" else Dex.species(speciesId).name
    Column(
        Modifier.size(width = 92.dp, height = 76.dp).clip(PixelShape)
            .background(if (current) Pixel.Shade else Pixel.Panel)
            .quietClickable(enabled = !current && state != DexState.UNKNOWN, onClickLabel = "Open entry") { open(speciesId) }
            .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(Dex.defaultForm(speciesId).id, false, name, size = 52.dp, silhouette = if (state.caught) null else Pixel.Shade)
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** The credit SpriteCollab's licence asks for: small and faint, there to be found rather than read on every visit. */
@Composable
private fun CreditLine(credit: String) {
    Text(
        "$credit, PMD SpriteCollab (CC\u00A0BY\u2060-\u2060NC\u00A04.0)", Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
        color = Pixel.InkSoft, textAlign = TextAlign.Center,
    )
}

// ----- Guardians -----------------------------------------------------------------------------------------------------
