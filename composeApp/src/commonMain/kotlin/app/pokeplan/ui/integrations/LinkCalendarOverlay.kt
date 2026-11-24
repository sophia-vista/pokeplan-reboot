package app.pokeplan.ui.integrations

import androidx.compose.ui.graphics.toArgb
import app.pokeplan.app.integrations.LinkInPlan
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.CalendarOverlay
import app.pokeplan.ui.Place
import app.pokeplan.ui.link.LinkSection
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.theme.ModuleAccent
import kotlinx.datetime.LocalDate

/**
 * Draws LINK dates and due follow-ups in the PLAN calendar. The integration lives here, outside both modules: PLAN only asks
 * its overlays for read-only entries, and LINK only keeps its own records. Off when the player turns it off, when LINK is
 * locked, and never on the launcher (the launcher reads the phone's calendars only).
 */
class LinkCalendarOverlay(private val model: AppModel) : CalendarOverlay {
    private val locked: Boolean get() = Module.LINK.name in model.data.settings.lockedModules

    override fun events(from: LocalDate, to: LocalDate): List<CalendarEvent> {
        val settings = model.integrationData
        if (locked || (!settings.linkDatesInCalendar && !settings.linkFollowUpsInCalendar)) return emptyList()
        val color = ModuleAccent.LINK.accent.toArgb().toLong() and 0xFFFFFFL
        return LinkInPlan.entries(model.linkData, from, to, model.store.today, settings.linkDatesInCalendar, settings.linkFollowUpsInCalendar, color)
    }

    override fun owns(eventId: String): Boolean = LinkInPlan.owns(eventId)

    override fun open(eventId: String) {
        val target = LinkInPlan.target(model.linkData, eventId) ?: return
        model.open(Place.LINK, focusTab = false)
        if (target.personId != null) {
            model.linkPersonTab = 0
            model.linkPersonId = target.personId
            model.linkSection = LinkSection.PEOPLE
        } else model.linkSection = if (target.followUp) LinkSection.FOLLOW_UPS else LinkSection.DATES
    }
}
