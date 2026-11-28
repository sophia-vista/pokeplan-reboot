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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.pokeplan.link.LinkRules
import app.pokeplan.link.ProfileKit
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.screens.ChoiceChip
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

private val accent get() = ModuleAccent.LINK.accent

/**
 * The slambook questions per relationship category (REP-085, LINK.md 2.6), in LINK's Settings. Pick a set (the general one or a
 * category), then show, hide, reorder or add questions; **Back to the starting set** undoes the changes. A person's slambook offers
 * the set for their category. Removing a question here never deletes an answer, and changing one set changes no other.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionSetsBox(model: AppModel) {
    val link = model.link
    val sets = listOf(ProfileKit.GENERAL) + LinkRules.categories(model.linkData)
    var chosen by remember { mutableStateOf(ProfileKit.GENERAL) }
    var own by remember { mutableStateOf("") }
    val shown = if (chosen in sets) chosen else ProfileKit.GENERAL
    val questions = link.questionsFor(shown)
    val customised = ProfileKit.isCustomised(model.linkData.settings, shown)
    val hidden = ProfileKit.defaultQuestions(shown).plus(ProfileKit.questions).distinct().filter { q -> questions.none { it.equals(q, ignoreCase = true) } }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Slambook questions")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                sets.forEach { c -> ChoiceChip(if (c == ProfileKit.GENERAL) "General" else c, shown == c, { chosen = c }, key = accent) }
            }
            questions.forEachIndexed { i, q ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(q, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Up", { link.setQuestions(shown, questions.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, Modifier.weight(1f), Pixel.InkSoft, enabled = i > 0, compact = true)
                        PixelButton("Down", { link.setQuestions(shown, questions.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, Modifier.weight(1f), Pixel.InkSoft, enabled = i < questions.lastIndex, compact = true)
                        PixelButton("Hide", { link.setQuestions(shown, questions.filterIndexed { j, _ -> j != i }) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                FieldLabel("Hidden")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    hidden.forEach { q -> PixelButton(q, { link.setQuestions(shown, questions + q) }, color = accent, compact = true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                PixelField(own, { own = it.take(ProfileKit.TEXT_MAX) }, Modifier.weight(1f), label = "Your own question", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                PixelButton("Add", { link.setQuestions(shown, questions + own.trim()); own = "" }, color = accent, enabled = own.isNotBlank() && questions.none { it.equals(own.trim(), ignoreCase = true) })
            }
            if (customised) PixelButton("Back to the starting set", { link.resetQuestions(shown) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
