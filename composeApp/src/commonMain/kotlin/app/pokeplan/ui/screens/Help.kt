package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/** One question and its answer. */
internal data class HelpItem(val question: String, val answer: String)

/** A group of questions under one heading. */
internal data class HelpTopic(val title: String, val items: List<HelpItem>)

/**
 * The help text, written from docs/design.md in plain words. Numbers that are still being tuned are left out, so the text
 * does not go stale when a reward changes.
 */
internal val helpTopics = listOf(
    HelpTopic(
        "The basics",
        listOf(
            HelpItem("What is PokéPlan?", "A planner and focus timer where doing real things looks after your Pokémon. Finish a focus session to hatch an egg; finish tasks, habits and goals to earn candies and items that level up and evolve your Pokémon. There are no battles."),
            HelpItem("What is the buddy?", "One Pokémon from your party that stays with you on Home and in the widget. Pet it, feed it a berry, and it gets friendlier. Its type and nature give small bonuses to the rewards you earn."),
            HelpItem("How do I rank up?", "Every reward you earn also adds trainer points. New ranks open new things: Super Candies and evolution, new regions, Guardians and more quests."),
        ),
    ),
    HelpTopic(
        "Focus and eggs",
        listOf(
            HelpItem("How do I hatch an egg?", "Start a focus session on the Focus tab and stay in the app until it ends. A longer session gives better odds of a rarer egg."),
            HelpItem("What happens if I leave the app?", "You have a 10 second grace period to come back. After that the egg is lost and the session does not count for your streak. You get emergency pauses for the times you really must leave; start one before you go."),
            HelpItem("What is Deep Focus?", "The phone has to stay still while the timer runs: picking it up or moving it fails the session. In return the egg odds go up one step."),
        ),
    ),
    HelpTopic(
        "Tasks and rewards",
        listOf(
            HelpItem("What do the ranks E to A mean?", "How hard you find the task. You choose it. A harder rank pays more, and A pays the most. Urgency comes from the date, not the rank."),
            HelpItem("Why did my task not pay?", "A task has to exist for a short while before it pays, longer for harder ranks, so a big task cannot be added and ticked in the same second. Finishing it sooner still completes it, just without a reward."),
            HelpItem("Can I undo a task?", "For 10 seconds after you finish it. After that the reward is paid and it is final. A task finished from a widget is paid the next time you open the app."),
            HelpItem("What are Rare Candies and Super Candies?", "A Rare Candy levels a Pokémon up. A Super Candy evolves it once it is ready; some Pokémon need a stone, a happy buddy or a time of day instead."),
        ),
    ),
    HelpTopic(
        "Pokémon",
        listOf(
            HelpItem("Where do new Pokémon go?", "Into your party until it has six, then to the PC boxes. Use Sort and filters in the PC to find one."),
            HelpItem("How do I keep a Pokémon safe?", "Star it in its details. A starred Pokémon cannot be transferred by mistake."),
            HelpItem("What is a shiny?", "A rare colour variant. It plays like any other Pokémon; the Pokédex has a Shiny tab for the ones you have."),
            HelpItem("What are Guardians and quests?", "Legendary and mythical Pokémon are not found in eggs. Each has a quest, and once you have one it can sit in a Guardian slot for a bigger bonus."),
        ),
    ),
    HelpTopic(
        "Widgets, sound and your data",
        listOf(
            HelpItem("Which widgets are there?", "Today shows your tasks for the day with a tick and a quick add. Buddy shows your buddy in its place: tap it to pet, or use the berry button. Focus starts a session and shows the countdown while one runs."),
            HelpItem("The widget did not update.", "Open the app once; it brings the widgets up to date. If a widget still looks stale, remove it and add it again."),
            HelpItem("How do I turn sound, vibration or animations off?", "Me, then Settings: the sliders turn cries and effects down, Haptics turns off vibration, and Reduce motion stops the animations."),
            HelpItem("Is my progress saved?", "Yes, on this phone. Sign in under Settings, Account to back it up and use it on another device. Sign-in is optional."),
        ),
    ),
)

@Composable
internal fun HelpView() {
    Text("Tap a question to read the answer.", style = MaterialTheme.typography.bodyMedium)
    helpTopics.forEach { topic ->
        SectionLabel(topic.title)
        topic.items.forEach { HelpRow(it) }
    }
}

@Composable
private fun HelpRow(item: HelpItem) {
    var open by remember(item.question) { mutableStateOf(false) }
    TextBox(
        Modifier.clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.question, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium, color = Pixel.InkSoft, modifier = Modifier.padding(start = 8.dp))
            }
            if (open) Text(item.answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
