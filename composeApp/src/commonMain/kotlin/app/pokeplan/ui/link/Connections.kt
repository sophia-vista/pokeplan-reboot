package app.pokeplan.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Person
import app.pokeplan.link.PersonConnection
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.ChoiceChip
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing

private val accent get() = ModuleAccent.LINK.accent
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/**
 * A person's connections (REP-084, LINK.md 2.2): the people the player recorded as family, friends, colleagues or in their own word.
 * Each row opens that person's profile; **Change** edits the word or removes the connection (with Undo). The same record shows on the
 * other person's profile. Nothing here is inferred, counted or scored.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonConnections(model: AppModel, p: Person) {
    val link = model.link
    val rows = link.connectionsOf(p.id)
    var adding by remember(p.id) { mutableStateOf(false) }
    var editing by remember(p.id) { mutableStateOf<String?>(null) }
    FieldLabel("Connections")
    rows.forEach { (c, other) ->
        if (editing == c.id) ConnectionEditor(model, c, LinkRules.displayName(other)) { editing = null }
        else RecordRow(
            LinkRules.displayName(other), c.label,
            trail = { PixelButton("Change", { editing = c.id }, color = Pixel.InkSoft, compact = true) },
            onClick = { openPerson(model, other.id) }, onClickLabel = "Open the profile",
        )
    }
    if (adding) ConnectionAdder(model, p) { adding = false }
    else if (model.linkData.people.size > 1) PixelButton("Connect to someone", { adding = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionAdder(model: AppModel, p: Person, onDone: () -> Unit) {
    var other by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PersonPicker(model.linkData.people.filter { it.id != p.id }, other) { other = it; problem = null }
            LabelField(label, { label = it; problem = null })
            problem?.let { Report(model, it, NoticeTone.WARNING) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Connect", {
                    val target = other
                    if (target == null) problem = "Choose a person."
                    else {
                        val result = model.link.addConnection(p.id, target, label)
                        if (result.record != null) onDone() else problem = result.refused
                    }
                }, Modifier.weight(1f), accent, compact = true)
                PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
private fun ConnectionEditor(model: AppModel, c: PersonConnection, otherName: String, onDone: () -> Unit) {
    var label by remember(c.id) { mutableStateOf(c.label) }
    var problem by remember(c.id) { mutableStateOf<String?>(null) }
    FormBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel(otherName)
            LabelField(label, { label = it; problem = null })
            problem?.let { Report(model, it, NoticeTone.WARNING) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Save", {
                    val result = model.link.updateConnection(c.id, label)
                    if (result.record != null) onDone() else problem = result.refused
                }, Modifier.weight(1f), accent, compact = true)
                PixelButton("Remove", {
                    val before = model.linkData
                    model.link.removeConnection(c.id); onDone()
                    model.offerUndo("Removed a connection") { model.link.restore(before) }
                }, Modifier.weight(1f), Pixel.Danger, compact = true)
                PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

/** The connection's word: the player's own, with a few common ones to tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelField(label: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(label, { onChange(it.take(LinkRules.CONNECTION_LABEL_MAX)) }, Modifier.fillMaxWidth(), label = "How they are connected", singleLine = true, keyboardOptions = sentenceKeys)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            LinkRules.CONNECTION_SUGGESTIONS.forEach { w -> ChoiceChip(w, label.equals(w, ignoreCase = true), { onChange(w) }, key = accent) }
        }
    }
}
