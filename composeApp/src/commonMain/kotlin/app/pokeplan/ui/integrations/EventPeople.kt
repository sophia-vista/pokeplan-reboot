package app.pokeplan.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.app.integrations.IntegrationStore
import app.pokeplan.link.LinkRules
import app.pokeplan.plan.PlanEvent
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Place
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private const val SUGGESTIONS = 5
private const val UPCOMING_DAYS = 90
private const val EVENTS_SHOWN = 4

/** Whether the people link is on offer at all: LINK is not locked and holds someone besides the player. */
internal fun linkPeopleAvailable(model: AppModel): Boolean =
    Module.LINK.name !in model.data.settings.lockedModules && model.linkData.people.any { !it.isMe }

/** The people tied to one PLAN event (its series), as far as they still exist in LINK. */
internal fun peopleOfEvent(model: AppModel, eventId: String): List<String> {
    val live = model.linkData.people.map { it.id }.toSet()
    return model.integrationData.eventPeople.firstOrNull { it.eventId == PlanEvent.seriesOf(eventId) }?.personIds.orEmpty().filter { it in live }
}

/**
 * Tie LINK people to a PLAN event: chosen people are keys that remove them, and a search field adds more. Used by the new-event
 * form (through [onChange] into the draft) and by the editor of an existing event. Shows nothing when there is nobody to pick.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventPeoplePicker(model: AppModel, selected: List<String>, onChange: (List<String>) -> Unit) {
    val data = model.linkData
    if (!linkPeopleAvailable(model) && selected.isEmpty()) return
    var query by remember { mutableStateOf("") }
    val accent = ModuleAccent.LINK.accent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("People")
        if (selected.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            selected.forEach { id ->
                val person = data.people.firstOrNull { it.id == id } ?: return@forEach
                PixelButton("${LinkRules.displayName(person)}  ×", { onChange(selected - id) }, color = accent, compact = true)
            }
        }
        if (selected.size < IntegrationStore.MAX_PEOPLE) {
            PixelField(query, { query = it }, Modifier.fillMaxWidth(), label = "Add a person", singleLine = true)
            if (query.isNotBlank()) {
                val matches = LinkRules.directory(data, query).filter { !it.isMe && it.id !in selected }.take(SUGGESTIONS)
                if (matches.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    matches.forEach { p -> PixelButton(LinkRules.displayName(p), { onChange(selected + p.id); query = "" }, color = Pixel.InkSoft, compact = true) }
                }
            }
        }
    }
}

/** The editor of an existing event: the people tied to it, saved as they change. */
@Composable
internal fun SavedEventPeople(model: AppModel, eventId: String) {
    val series = PlanEvent.seriesOf(eventId)
    EventPeoplePicker(model, peopleOfEvent(model, series)) { model.integrations.setPeople(series, it) }
}

/** A person's page: the next events they are tied to, each opening the PLAN calendar. Nothing when none are. */
@Composable
internal fun PersonEvents(model: AppModel, personId: String) {
    val today = model.store.today
    val linked = model.integrationData.eventPeople.filter { personId in it.personIds }.map { it.eventId }.toSet()
    if (linked.isEmpty()) return
    val upcoming = model.data.events.filter { it.id in linked }
        .flatMap { e -> e.occurrences(today, today.plus(DatePeriod(days = UPCOMING_DAYS))).take(1) }
        .sortedWith(compareBy({ it.day }, { it.startMinute }))
        .take(EVENTS_SHOWN)
    if (upcoming.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("Events")
        upcoming.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                PixelButton(whenText(e, today), { model.open(Place.PLAN, focusTab = false); model.planSection = PlanSection.CALENDAR }, color = ModuleAccent.PLAN.accent, compact = true)
            }
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun whenText(e: PlanEvent, today: LocalDate): String = when {
    e.day == today && !e.allDay -> "Today ${clock(e)}"
    e.day == today -> "Today"
    e.allDay -> "${e.day.dayOfMonth} ${MONTHS[e.day.monthNumber - 1]}"
    else -> "${e.day.dayOfMonth} ${MONTHS[e.day.monthNumber - 1]} ${clock(e)}"
}

private fun clock(e: PlanEvent): String = "${e.start.hour.toString().padStart(2, '0')}:${e.start.minute.toString().padStart(2, '0')}"

/** Settings: what LINK may show in the PLAN calendar. Both are read-only entries, drawn in the app only. */
@Composable
internal fun LinkInCalendarCard(model: AppModel) {
    if (Module.LINK.name in model.data.settings.lockedModules) return
    val settings = model.integrationData
    app.pokeplan.ui.theme.TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            app.pokeplan.ui.theme.PixelTitle("LINK in the calendar")
            app.pokeplan.ui.theme.ToggleRow("Dates", settings.linkDatesInCalendar) { model.integrations.setLinkDates(it) }
            app.pokeplan.ui.theme.ToggleRow("Follow-ups", settings.linkFollowUpsInCalendar) { model.integrations.setLinkFollowUps(it) }
        }
    }
}
