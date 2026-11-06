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
            HelpItem("What is PokéLife?", "A Pokémon world for real life, made of modules that are each their own region: Plan (PokéPlan: a planner and focus timer) and Care (routines and calm breaks). Doing real things looks after your Pokémon. Finish a focus session to hatch an egg; finish tasks, habits and goals to earn candies and items that level up and evolve your Pokémon. There are no battles."),
            HelpItem("What is the buddy?", "One Pokémon from your party that stays with you on Home and in the widget. Pet it, feed it a berry, and it gets friendlier. Its type and nature give small bonuses to the rewards you earn."),
            HelpItem("What is Care?", "A module under Modules for looking after yourself, inspired by journalling and DBT: small routines to tick off, a calm break to breathe with your buddy, a daily diary card, DBT skills with short how-tos, and a journal. Routines, calm breaks and practised skills earn friendship and berries; diary cards and journal entries never earn anything and stay on this device. It is a reminder, not therapy. Turn Care off in Me, Settings, Modules."),
            HelpItem("How do I rank up?", "Every reward you earn also adds trainer points. New ranks open new things: Super Candies and evolution, new regions, Guardians and more quests."),
        ),
    ),
    HelpTopic(
        "Focus and eggs",
        listOf(
            HelpItem("How do I hatch an egg?", "Start a focus session in Modules, Plan, Focus, and stay in the app until it ends. A longer session gives better odds of a rarer egg."),
            HelpItem("What happens if I leave the app?", "You have a 10 second grace period to come back. After that the egg is lost and the session does not count for your streak. You get emergency pauses for the times you really must leave; start one before you go."),
            HelpItem("What is Deep Focus?", "The phone has to stay still while the timer runs: picking it up or moving it fails the session. In return the egg odds go up one step."),
        ),
    ),
    HelpTopic(
        "Tasks and rewards",
        listOf(
            HelpItem("What do the ranks E to A mean?", "How hard you find the task. You choose it. A harder rank pays more, and A pays the most. Urgency comes from the date, not the rank."),
            HelpItem("Why did my task not pay?", "A task has to exist for a short while before it pays, longer for harder ranks, so a big task cannot be added and ticked in the same second. Finishing it sooner still completes it, just without a reward."),
            HelpItem("How do I plan my day by the hour?", "Open Planner, Calendar. Tasks without a time wait at the top: hold one and drag it onto an hour (with a mouse, just drag), or tap it and then tap an hour. Drag a task on the hours to move it, or its lower edge to change how long it takes; everything snaps to 15 minutes. Tap a task on the hours to nudge it, change its length or take its time away. Week shows seven days side by side, and you can drag tasks between them."),
            HelpItem("Can I add events that aren't tasks?", "Yes: New event on Planner, Calendar. Name it, tap an hour (or use -15 and +15) and Add. Events are for appointments and blocks of time; they never pay and can't be ticked off. Drag and resize them like tasks, or tap one to rename it, change its length or delete it. They stay in PokéPlan and aren't added to your phone's calendar."),
            HelpItem("Can I undo a task?", "For 10 seconds after you finish it. After that the reward is paid and it is final. A task finished from a widget is paid the next time you open the app."),
            HelpItem("How do I get a copy of my data, or delete it?", "Me, then Settings, then Your data. Export my data saves everything PokéLife keeps as a JSON file. Erase this device starts over."),
            HelpItem("How do reminders work?", "Open a task with a day and pick up to three times under Remind me: at the time, 10 minutes, an hour or a day before. They count from the time you planned it, or its deadline, or 9:00 on its day. A reminder waits during quiet hours (Settings), a focus session and rest days. Tap Done on the notification to finish the task, Snooze to hear again in an hour, or Tomorrow for 9:00 the next day. Set a time under At on the task to be reminded at that time."),
            HelpItem("What are Rare Candies and Super Candies?", "Every finished task gives Rare Candies, and sometimes a Super Candy on top. Rare Candies level a Pokémon up; higher levels need more of them, as in the games. A Super Candy evolves it once it reaches its evolution level from the games (Charmander at 16); some Pokémon need a stone, a happy buddy or a time of day instead."),
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
            HelpItem("How do I turn sound, vibration or animations off?", "Me, then Settings: the sliders turn cries and effects down, Vibration turns it off, and Reduce motion stops the animations."),
            HelpItem("Is my progress saved?", "Yes, on this phone, in its own database, and a backup copy is kept once a day. There are no accounts and nothing is sent anywhere. Export my data under Settings, Your data makes a copy you can keep."),
        ),
    ),
)

@Composable
internal fun HelpView() {
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
                Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium, color = Pixel.InkSoft, modifier = Modifier.padding(start = Spacing.S))
            }
            if (open) Text(item.answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
